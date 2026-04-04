package com.szecloud.wakebridge;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class WakeActivity extends Activity {
    private static final String TAG = "WakeBridge";
    private static final int DEFAULT_HOLD_MS = 5000;
    private static final int MAX_HOLD_MS = 60000;
    private static final String URI_PARAM_HOLD_MS = "hold_ms";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WakeCoordinator.cancelWakeArtifacts(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.TRANSPARENT);

        TextView hint = new TextView(this);
        hint.setText("WakeBridge 正在保持亮屏");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(16);
        hint.setPadding(36, 20, 36, 20);
        hint.setBackgroundColor(Color.argb(170, 17, 24, 39));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hintParams.topMargin = 72;
        root.addView(hint, hintParams);
        setContentView(root);

        maybeRequestDismissKeyguard();

        int holdMs = sanitizeHoldMs(getIntent());
        String source = getIntent() != null
            ? getIntent().getStringExtra(WakeCoordinator.EXTRA_SOURCE)
            : "unknown";
        WakeDebugStore.append(this, "WakeActivity 已启动，source=" + source + "，holdMs=" + holdMs);
        Log.i(TAG, "wake requested, holdMs=" + holdMs + ", source=" + source);

        new Handler(Looper.getMainLooper()).postDelayed(this::finishSafely, holdMs);
    }

    private void maybeRequestDismissKeyguard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        if (keyguardManager == null) {
            return;
        }

        try {
            keyguardManager.requestDismissKeyguard(this, null);
        } catch (RuntimeException e) {
            Log.w(TAG, "dismiss keyguard failed", e);
        }
    }

    private int sanitizeHoldMs(Intent intent) {
        int holdMs = DEFAULT_HOLD_MS;
        if (intent != null) {
            holdMs = intent.getIntExtra(WakeCoordinator.EXTRA_HOLD_MS, DEFAULT_HOLD_MS);
            Uri data = intent.getData();
            if (data != null) {
                String uriHoldMs = data.getQueryParameter(URI_PARAM_HOLD_MS);
                if (uriHoldMs != null) {
                    try {
                        holdMs = Integer.parseInt(uriHoldMs);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "invalid hold_ms in uri: " + uriHoldMs, e);
                    }
                }
            }
        }

        if (holdMs < 1000) {
            return 1000;
        }
        return Math.min(holdMs, MAX_HOLD_MS);
    }

    private void finishSafely() {
        WakeDebugStore.append(this, "WakeActivity 即将结束");
        finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        }
    }
}
