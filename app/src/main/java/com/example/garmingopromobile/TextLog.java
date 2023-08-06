package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.w3c.dom.Text;

public class TextLog {
    static private String content;
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

    static void logInfo() {logInfo("");}

    static void logInfo(Object text) {
        if (text == null) text = "";
        TextLog.content = TextLog.content + text + "\n";

        if (logToUI) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(TextLog.content);
                    scrollView.post(() -> {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    });
                }
            });
        }

        System.out.println(text);
    }

    static public void activateUI() {
        logToUI = true;
    }

    static public void deactivateUI() {
        logToUI = false;
    }
}
