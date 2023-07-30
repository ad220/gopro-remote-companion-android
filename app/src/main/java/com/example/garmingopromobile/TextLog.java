package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.w3c.dom.Text;

public class TextLog {

    private static String content;
    @SuppressLint("StaticFieldLeak")
    static TextView textView;
    @SuppressLint("StaticFieldLeak")
    static ScrollView scrollView;
    @SuppressLint("StaticFieldLeak")
    static Activity activity;


    static void bind(TextView textView, ScrollView scrollView, Activity activity ) {
        TextLog.textView = textView;
        TextLog.scrollView = scrollView;
        TextLog.activity = activity;
    }

    static void logInfo() {logInfo("");}

    static void logInfo(Object text) {
        if (text == null) text = "";
        TextLog.content = TextLog.content + text + "\n";

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(TextLog.content);
                scrollView.post(() -> {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                });
            }
        });

        System.out.println(text);
    }
}
