package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

public class TextLog {
    static private final String TAG = "TextLog";
    static private String content =
        "===========================================================\n" +
        "             Garmin GoPro Remote end-user logs             \n" +
        "===========================================================\n";
    @SuppressLint("StaticFieldLeak")
    static private TextView textView;
    @SuppressLint("StaticFieldLeak")
    static private ScrollView scrollView;
    @SuppressLint("StaticFieldLeak")
    static private Activity activity;
    static private boolean logToUI;


    static void bind(TextView textView, ScrollView scrollView, Activity activity ) {
        TextLog.textView = textView;
        TextLog.scrollView = scrollView;
        TextLog.activity = activity;
    }

    static public void logInfo() {logInfo("");}

    static public void logInfo(Object text) {
        if (text == null) text = "";
        logToUI(text);
        if (!text.equals("")) Log.v(TAG, text.toString());
    }

    static public void logWarn() {logWarn("");}
    static public void logWarn(Object text) {
        if (text == null) text = "";
        logToUI(text);
        if (!text.equals("")) Log.w(TAG, text.toString());
    }

    static public void logError() {logError("");}

    static public void logError(Object text) {
        if (text == null) text = "";
        logToUI(text);
        if (!text.equals("")) Log.e(TAG, text.toString());
    }

    static private void logToUI(Object text) {
        TextLog.content = TextLog.content + text + "\n";

        if (logToUI) {
            activity.runOnUiThread(() -> {
                textView.setText(TextLog.content);
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            });
        }
    }

    static public void activateUI() {
        logToUI = true;
    }

    static public void deactivateUI() {
        logToUI = false;
    }

    static public boolean isUIActive() {
        return logToUI;
    }
}
