package com.questhelper.tkkeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Intent svc = new Intent(context, KeeperService.class).putExtra(KeeperService.EXTRA_BOOT, true);
        context.startForegroundService(svc);
    }
}
