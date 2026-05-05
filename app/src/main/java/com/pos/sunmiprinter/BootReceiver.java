package com.pos.sunmiprinter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 開機自動啟動 PrintService
 * 監聽 BOOT_COMPLETED / QUICKBOOT_POWERON（部分 Android 裝置）
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            startPrintService(context);
        }
    }

    private void startPrintService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, PrintService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "PrintService started on boot");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start PrintService", e);
        }
    }
}
