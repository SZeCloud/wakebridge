package com.shizy.wakebridge;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 100;
    private TextView statusView;
    private TextView eventsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        WakeDebugStore.append(this, "主界面已打开");
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            WakeDebugStore.append(this, "通知权限请求已返回");
            refreshStatus();
        }
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.parseColor("#10151C"));
        scrollView.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("WakeBridge");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("用于远程点亮屏幕。先看权限状态，再做自测。");
        subtitle.setTextColor(Color.parseColor("#C7D2E0"));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(15);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackgroundColor(Color.parseColor("#1F2937"));
        root.addView(statusView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildButton("请求通知权限", v -> requestNotificationPermission()));
        root.addView(buildButton("打开通知设置", v -> openNotificationSettings()));
        root.addView(buildButton("打开全屏通知设置", v -> openFullScreenIntentSettings()));
        root.addView(buildButton("测试亮屏 15 秒", v -> {
            WakeDebugStore.append(this, "手动点击测试亮屏");
            WakeCoordinator.dispatchWake(this, 15000, "manual_test");
            refreshStatus();
        }));
        root.addView(buildButton("刷新状态", v -> refreshStatus()));

        TextView eventsTitle = new TextView(this);
        eventsTitle.setText("最近事件");
        eventsTitle.setTextColor(Color.WHITE);
        eventsTitle.setTextSize(18);
        eventsTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(eventsTitle);

        eventsView = new TextView(this);
        eventsView.setTextColor(Color.parseColor("#D1D5DB"));
        eventsView.setTextSize(13);
        eventsView.setPadding(dp(12), dp(12), dp(12), dp(12));
        eventsView.setBackgroundColor(Color.parseColor("#111827"));
        eventsView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(eventsView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return scrollView;
    }

    private Button buildButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(12);
        button.setLayoutParams(params);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    private void refreshStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append("通知权限: ").append(isNotificationsEnabled() ? "已开启" : "未开启").append('\n');
        builder.append("全屏通知: ").append(getFullScreenIntentStatus()).append('\n');
        builder.append("系统版本: Android ").append(Build.VERSION.RELEASE)
            .append(" / SDK ").append(Build.VERSION.SDK_INT);
        statusView.setText(builder.toString());
        eventsView.setText(WakeDebugStore.getRecentEvents(this));
    }

    private boolean isNotificationsEnabled() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        }
        return notificationManager.areNotificationsEnabled();
    }

    private String getFullScreenIntentStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return "系统未单独限制";
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return "无法判断";
        }
        return notificationManager.canUseFullScreenIntent() ? "已允许" : "未允许";
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            WakeDebugStore.append(this, "当前系统无需动态申请通知权限");
            refreshStatus();
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            WakeDebugStore.append(this, "通知权限已处于允许状态");
            refreshStatus();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openFullScreenIntentSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(android.net.Uri.parse("package:" + getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
