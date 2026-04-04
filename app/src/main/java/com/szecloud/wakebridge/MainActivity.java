package com.szecloud.wakebridge;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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
        subtitle.setText("用于远程点亮屏幕。先确认系统策略，再做自测。");
        subtitle.setTextColor(Color.parseColor("#C7D2E0"));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        TextView prereqView = new TextView(this);
        prereqView.setText(
            "关键前提\n"
                + "1. 需要开启自启动\n"
                + "2. 需要把电量策略设为无限制\n"
                + "3. 通知、全屏通知、锁屏显示等权限尽量开齐\n\n"
                + "说明：自启动和 MIUI 的“无限制”通常不能由 App 直接代配，只能跳转系统设置页并提示手动确认。"
        );
        prereqView.setTextColor(Color.parseColor("#FDE68A"));
        prereqView.setTextSize(14);
        prereqView.setPadding(dp(12), dp(12), dp(12), dp(12));
        prereqView.setBackgroundColor(Color.parseColor("#3F2D12"));
        root.addView(prereqView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(15);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackgroundColor(Color.parseColor("#1F2937"));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(12);
        root.addView(statusView, statusParams);

        root.addView(buildButton("请求通知权限", v -> requestNotificationPermission()));
        root.addView(buildButton("打开通知设置", v -> openNotificationSettings()));
        root.addView(buildButton("打开全屏通知设置", v -> openFullScreenIntentSettings()));
        root.addView(buildButton("申请忽略系统电池优化", v -> requestIgnoreBatteryOptimizations()));
        root.addView(buildButton("打开系统电池优化设置", v -> openBatteryOptimizationSettings()));
        root.addView(buildButton("打开应用详情页", v -> openApplicationDetailsSettings()));
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
        eventsView.setLineSpacing(0f, 1.1f);

        ScrollView eventsScrollView = new ScrollView(this);
        eventsScrollView.setFillViewport(true);
        eventsScrollView.setBackgroundColor(Color.parseColor("#111827"));
        eventsScrollView.addView(eventsView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams eventsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(220)
        );
        eventsScrollView.setLayoutParams(eventsParams);
        root.addView(eventsScrollView);

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
        builder.append("系统电池优化: ").append(isIgnoringBatteryOptimizations() ? "已忽略" : "未忽略").append('\n');
        builder.append("自启动: 无法自动检测，请手动确认已开启").append('\n');
        builder.append("MIUI 电量无限制: 无法自动检测，请手动确认已设置").append('\n');
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

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager == null) {
            return false;
        }
        return powerManager.isIgnoringBatteryOptimizations(getPackageName());
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

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            WakeDebugStore.append(this, "当前系统无需申请忽略电池优化");
            refreshStatus();
            return;
        }
        if (isIgnoringBatteryOptimizations()) {
            WakeDebugStore.append(this, "系统电池优化已处于忽略状态");
            refreshStatus();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            WakeDebugStore.append(this, "已尝试拉起系统忽略电池优化授权页");
        } catch (RuntimeException e) {
            WakeDebugStore.append(this, "无法直接申请忽略电池优化，改为打开系统电池设置");
            openBatteryOptimizationSettings();
        }
    }

    private void openBatteryOptimizationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        } else {
            intent = new Intent(Settings.ACTION_SETTINGS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            WakeDebugStore.append(this, "已打开系统电池优化设置页");
        } catch (RuntimeException e) {
            WakeDebugStore.append(this, "无法打开系统电池优化设置，改为打开应用详情页");
            openApplicationDetailsSettings();
        }
    }

    private void openFullScreenIntentSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:" + getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                Uri.parse("package:" + getPackageName())
            );
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openApplicationDetailsSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + getPackageName()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
