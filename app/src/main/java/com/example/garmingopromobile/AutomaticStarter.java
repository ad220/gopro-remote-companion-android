package com.example.garmingopromobile;

import android.app.Activity;
import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.Preference;

public class AutomaticStarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) || intent.getAction().equals(Intent.ACTION_APP_ERROR)) {
            SharedPreferences pref = context.getSharedPreferences("savedPrefs", Context.MODE_PRIVATE);
            if (pref.getBoolean("backgroundToggle", false)) {
                Intent serviceIntent = new Intent(context, BackgroundService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
