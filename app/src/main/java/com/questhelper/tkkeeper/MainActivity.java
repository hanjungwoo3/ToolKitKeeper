package com.questhelper.tkkeeper;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            status.setText(prefs().getString(KeeperService.PREF_STATUS, "대기 중"));
            handler.postDelayed(this, 1_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("ToolKit Keeper");
        title.setTextSize(24);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 24, 0, 24);
        root.addView(status);

        Switch watch = new Switch(this);
        watch.setText("다른 앱을 닫으면 Quest ToolKit 다시 띄우기");
        watch.setChecked(prefs().getBoolean(KeeperService.PREF_WATCH, true));
        watch.setOnCheckedChangeListener((b, on) -> prefs().edit().putBoolean(KeeperService.PREF_WATCH, on).apply());
        root.addView(watch);

        Button restart = new Button(this);
        restart.setText("Quest ToolKit 지금 재시작");
        restart.setOnClickListener(v -> startForegroundService(
                new Intent(this, KeeperService.class).setAction(KeeperService.ACTION_RESTART_TOOLKIT)));
        root.addView(restart);

        TextView help = new TextView(this);
        help.setPadding(0, 24, 0, 0);
        help.setText("처음 한 번은 헤드셋에 'USB 디버깅 허용' 창이 뜨면 '항상 허용'을 선택하세요.\n"
                + "이후에는 부팅할 때마다 자동으로 동작합니다. (ADBActivator가 5555 포트를 열어야 합니다)");
        root.addView(help);

        setContentView(root);
        startForegroundService(new Intent(this, KeeperService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(KeeperService.PREFS, MODE_PRIVATE);
    }
}
