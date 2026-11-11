package com.questhelper.tkkeeper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dadb.AdbKeyPair;
import dadb.Dadb;

/**
 * Runs on the headset and uses ADB (127.0.0.1:5555, opened by ADBActivator) to:
 *  1. after boot, restart Quest ToolKit if its built-in ADB connection failed (it tries before the port is open);
 *  2. bring Quest ToolKit back to the front when another app is closed.
 */
public class KeeperService extends Service {
    static final String TAG = "TKKeeper";
    static final String TOOLKIT = "com.sdh.metavision";
    static final String EXTRA_BOOT = "boot";
    static final String ACTION_RESTART_TOOLKIT = "com.questhelper.tkkeeper.RESTART_TOOLKIT";
    static final String PREFS = "keeper";
    static final String PREF_STATUS = "status";
    static final String PREF_WATCH = "watch";

    private static final int ADB_PORT = 5555;
    // ADBActivator restarts adbd (tcpip 5555) more than once right after boot; wait until it settles.
    private static final long STABLE_MS = 12_000;
    private static final long TICK_MS = 2_000;
    private static final String CHANNEL = "keeper";

    // Packages that are part of the OS shell, or are ToolKit's own helpers; they never count as "another app".
    private static final String[] NON_USER_PREFIXES = {
            "com.oculus.", "com.meta.", "com.facebook.", "android", "com.android.", "horizonos.",
            "com.qualcomm.", "com.sdh.", "com.questhelper."
    };
    private static final Pattern TASK_LINE =
            Pattern.compile("taskId=\\d+: ([^/\\s]+)/\\S+ .*?visible=(true|false)");

    private volatile boolean running;
    private volatile boolean bootFlow;
    private volatile boolean restartRequested;
    private Thread worker;
    private Dadb adb;
    private long connectedSince;
    private boolean userAppWasVisible;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification("시작 중"));
        if (intent != null) {
            if (intent.getBooleanExtra(EXTRA_BOOT, false)) bootFlow = true;
            if (ACTION_RESTART_TOOLKIT.equals(intent.getAction())) restartRequested = true;
        }
        if (worker == null) {
            running = true;
            worker = new Thread(this::loop, "keeper");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        closeAdb();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loop() {
        while (running) {
            try {
                if (!portOpen()) {
                    closeAdb();
                    setStatus("5555 포트 대기 중 (Activator가 열 때까지)");
                    sleep(TICK_MS);
                    continue;
                }
                if (adb == null) connect();

                if (bootFlow) {
                    if (System.currentTimeMillis() - connectedSince < STABLE_MS) {
                        shell("echo ok"); // detect an adbd restart early
                        sleep(TICK_MS);
                        continue;
                    }
                    bootFlow = false;
                    ensureToolkitConnected();
                }
                if (restartRequested) {
                    restartRequested = false;
                    restartToolkit();
                }
                if (prefs().getBoolean(PREF_WATCH, true)) watchTick();
                sleep(TICK_MS);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                Log.w(TAG, "loop error", e);
                closeAdb();
                setStatus("ADB 끊김, 다시 연결 시도: " + e.getClass().getSimpleName());
                try {
                    sleep(3_000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void connect() throws IOException {
        setStatus("ADB 연결 중… 처음이면 헤드셋에서 '항상 허용'을 눌러 주세요");
        // Generous socket timeout: the first connection blocks until the user accepts the debugging prompt.
        adb = Dadb.Companion.create("127.0.0.1", ADB_PORT, keyPair(), 3_000, 90_000, false);
        String r = shell("echo ok");
        if (!r.contains("ok")) throw new IOException("unexpected shell output: " + r);
        connectedSince = System.currentTimeMillis();
        setStatus("ADB 연결됨");
        Log.i(TAG, "adb connected");
    }

    /** After boot: ToolKit only connects at startup, so restart it until it holds a connection to 5555. */
    private void ensureToolkitConnected() throws IOException, InterruptedException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (toolkitHasAdb()) {
                setStatus("ToolKit ADB 연결 확인됨");
                return;
            }
            setStatus("ToolKit ADB 미연결 → 재시작 (" + attempt + "/3)");
            restartToolkit();
            sleep(15_000);
        }
        setStatus(toolkitHasAdb() ? "ToolKit ADB 연결 확인됨" : "ToolKit 재시작 3회 후에도 미연결");
    }

    /** True if ToolKit's uid owns an ESTABLISHED TCP connection to port 5555 (0x15B3). */
    private boolean toolkitHasAdb() throws IOException {
        int uid;
        try {
            uid = getPackageManager().getApplicationInfo(TOOLKIT, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        String table = shell("cat /proc/net/tcp /proc/net/tcp6");
        for (String line : table.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length > 7 && f[2].endsWith(":15B3") && "01".equals(f[3]) && String.valueOf(uid).equals(f[7])) {
                return true;
            }
        }
        return false;
    }

    private void restartToolkit() throws IOException, InterruptedException {
        String component = toolkitComponent();
        if (component == null) {
            setStatus("Quest ToolKit이 설치돼 있지 않아요");
            return;
        }
        Log.i(TAG, "restarting toolkit");
        shell("am force-stop " + TOOLKIT);
        sleep(1_500);
        shell("am start -n " + component);
    }

    /** Bring ToolKit to the front when the last visible third-party app goes away. */
    private void watchTick() throws IOException {
        if (!shell("dumpsys power | grep -m1 mWakefulness=").contains("Awake")) {
            userAppWasVisible = false; // headset asleep: don't treat hidden apps as "closed"
            return;
        }
        boolean userAppVisible = false;
        boolean toolkitVisible = false;
        String visibleApp = null;
        for (String line : shell("am stack list").split("\n")) {
            Matcher m = TASK_LINE.matcher(line);
            if (!m.find() || !"true".equals(m.group(2))) continue;
            String pkg = m.group(1);
            if (TOOLKIT.equals(pkg)) toolkitVisible = true;
            else if (isUserApp(pkg)) {
                userAppVisible = true;
                visibleApp = pkg;
            }
        }
        if (userAppWasVisible && !userAppVisible && !toolkitVisible) {
            String component = toolkitComponent();
            if (component != null) {
                Log.i(TAG, "app closed, bringing toolkit to front");
                shell("am start -n " + component);
                setStatus("다른 앱 종료 감지 → ToolKit 다시 띄움");
            }
        } else if (userAppVisible && !userAppWasVisible) {
            setStatus("실행 중인 앱: " + visibleApp);
        }
        userAppWasVisible = userAppVisible;
    }

    private static boolean isUserApp(String pkg) {
        for (String p : NON_USER_PREFIXES) if (pkg.startsWith(p)) return false;
        return true;
    }

    private String toolkitComponent() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TOOLKIT);
        ComponentName c = launch == null ? null : launch.getComponent();
        return c == null ? null : c.flattenToShortString();
    }

    private String shell(String cmd) throws IOException {
        if (adb == null) throw new IOException("not connected");
        return adb.shell(cmd).getAllOutput();
    }

    private AdbKeyPair keyPair() {
        File priv = new File(getFilesDir(), "adbkey");
        File pub = new File(getFilesDir(), "adbkey.pub");
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub);
        return AdbKeyPair.read(priv, pub);
    }

    private static boolean portOpen() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", ADB_PORT), 1_000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void closeAdb() {
        if (adb != null) {
            try {
                adb.close();
            } catch (Exception ignored) {
            }
            adb = null;
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void setStatus(String s) {
        if (s.equals(prefs().getString(PREF_STATUS, null))) return;
        Log.i(TAG, s);
        prefs().edit().putString(PREF_STATUS, s).apply();
        getSystemService(NotificationManager.class).notify(1, buildNotification(s));
    }

    private Notification buildNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "ToolKit Keeper", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("ToolKit Keeper")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private static void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
