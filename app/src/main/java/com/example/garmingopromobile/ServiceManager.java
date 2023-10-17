package com.example.garmingopromobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Switch;

public class ServiceManager extends BroadcastReceiver {
    private static final String TAG = "ServiceManager";

    public static final String ACTION_SERVICE_STOP = "garmingopromobile.servicemanager.SERVICE_STOP";

    private static BackgroundService instance;
    private static Switch backgroundSwitch;

    public static void setInstance(BackgroundService _instance) {
        instance = _instance;
    }

    public static void setSwitch(Switch _backgroundSwitch) {
        backgroundSwitch = _backgroundSwitch;
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_SERVICE_STOP -> {
                if (TextLog.isUIActive()) {
                    instance.toggleBackground(false);
                    backgroundSwitch.setChecked(false);
                } else {
                    Log.v(TAG, "ServiceManager: Stopping service");
                    Intent serviceIntent = new Intent(context, BackgroundService.class);
                    context.stopService(serviceIntent);
                }
            }
            case Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_APP_ERROR -> {
                SharedPreferences pref = context.getSharedPreferences("savedPrefs", Context.MODE_PRIVATE);
                if (pref.getBoolean("backgroundToggle", false)) {
                    Intent serviceIntent2 = new Intent(context, BackgroundService.class);
                    context.startForegroundService(serviceIntent2);
                }
            }
            default -> Log.e(TAG, "ServiceManager: Unknown action");
        }
    }
}
