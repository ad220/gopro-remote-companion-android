package com.example.garmingopromobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ServiceManager extends BroadcastReceiver {

    public static final String ACTION_SERVICE_STOP = "garmingopromobile.servicemanager.SERVICE_STOP";
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_SERVICE_STOP -> {
                System.out.println("ServiceManager: Stopping service");
                Intent serviceIntent = new Intent(context, BackgroundService.class);
                context.stopService(serviceIntent);
            }
            case Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_APP_ERROR -> {
                SharedPreferences pref = context.getSharedPreferences("savedPrefs", Context.MODE_PRIVATE);
                if (pref.getBoolean("backgroundToggle", false)) {
                    Intent serviceIntent2 = new Intent(context, BackgroundService.class);
                    context.startForegroundService(serviceIntent2);
                }
            }
            default -> System.out.println("ServiceManager: Unknown action");
        }
    }
}
