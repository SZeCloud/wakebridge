package com.shizy.wakebridge;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class WakeDebugStore {
    private static final String PREFS_NAME = "wakebridge_debug";
    private static final String KEY_RECENT_EVENTS = "recent_events";
    private static final int MAX_CHARS = 4000;

    private WakeDebugStore() {
    }

    public static void append(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_RECENT_EVENTS, "");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date());
        String entry = timestamp + "  " + message;
        String merged = existing == null || existing.isEmpty() ? entry : entry + "\n" + existing;
        if (merged.length() > MAX_CHARS) {
            merged = merged.substring(0, MAX_CHARS);
        }
        prefs.edit().putString(KEY_RECENT_EVENTS, merged).apply();
    }

    public static String getRecentEvents(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_RECENT_EVENTS, "暂无事件记录");
    }
}
