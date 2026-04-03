package com.shizy.wakebridge;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public final class WakeCoordinator {
    private static final String TAG = "WakeBridge";
    public static final String EXTRA_HOLD_MS = "hold_ms";
    public static final String EXTRA_SOURCE = "source";
    public static final String CHANNEL_ID = "wakebridge_wake";
    public static final int NOTIFICATION_ID = 1001;
    private static final int WAKE_REQUEST_CODE = 1002;

    private WakeCoordinator() {
    }

    public static int clampHoldMs(int holdMs) {
        if (holdMs < 1000) {
            return 1000;
        }
        return Math.min(holdMs, 60000);
    }

    public static void dispatchWake(Context context, int requestedHoldMs, String source) {
        int holdMs = clampHoldMs(requestedHoldMs);
        WakeDebugStore.append(context, "收到唤醒请求，source=" + source + "，holdMs=" + holdMs);

        acquireWakeLock(context, holdMs);
        scheduleAlarmWake(context, holdMs, source);
        postFullScreenNotification(context, holdMs, source);
        startWakeActivityDirectly(context, holdMs, source);
    }

    public static Intent buildWakeActivityIntent(Context context, int holdMs, String source) {
        Intent intent = new Intent(context, WakeActivity.class);
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        intent.putExtra(EXTRA_HOLD_MS, clampHoldMs(holdMs));
        intent.putExtra(EXTRA_SOURCE, source);
        return intent;
    }

    public static PendingIntent buildWakeActivityPendingIntent(
        Context context,
        int holdMs,
        String source,
        int flags
    ) {
        return PendingIntent.getActivity(
            context,
            WAKE_REQUEST_CODE,
            buildWakeActivityIntent(context, holdMs, source),
            flags
        );
    }

    public static void cancelWakeArtifacts(Context context) {
        try {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "cancel notification failed", e);
        }

        try {
            AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
            if (alarmManager == null) {
                return;
            }
            PendingIntent pendingIntent = buildWakeActivityPendingIntent(
                context,
                1000,
                "cancel",
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "cancel alarm failed", e);
        }
    }

    @SuppressLint({"WakelockTimeout", "InvalidWakeLockTag"})
    private static void acquireWakeLock(Context context, int holdMs) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                WakeDebugStore.append(context, "PowerManager 不可用，无法申请 WakeLock");
                return;
            }

            int timeoutMs = Math.max(holdMs, 3000);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE,
                "WakeBridge:receiver-wakelock"
            );
            wakeLock.acquire(timeoutMs);
            WakeDebugStore.append(context, "WakeLock 已申请，timeoutMs=" + timeoutMs);
        } catch (RuntimeException e) {
            WakeDebugStore.append(context, "WakeLock 申请失败: " + e.getClass().getSimpleName());
            Log.e(TAG, "failed to acquire wake lock", e);
        }
    }

    private static void scheduleAlarmWake(Context context, int holdMs, String source) {
        try {
            AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
            if (alarmManager == null) {
                WakeDebugStore.append(context, "AlarmManager 不可用，跳过 AlarmClock 兜底");
                return;
            }

            PendingIntent operation = buildWakeActivityPendingIntent(
                context,
                holdMs,
                source + ":alarm",
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            long triggerAtMillis = System.currentTimeMillis() + 800L;
            PendingIntent showIntent = PendingIntent.getActivity(
                context,
                WAKE_REQUEST_CODE + 1,
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent);
            alarmManager.setAlarmClock(info, operation);
            WakeDebugStore.append(context, "AlarmClock 已计划，triggerAt=" + triggerAtMillis);
        } catch (RuntimeException e) {
            WakeDebugStore.append(context, "AlarmClock 计划失败: " + e.getClass().getSimpleName());
            Log.e(TAG, "failed to schedule alarm wake", e);
        }
    }

    private static void postFullScreenNotification(Context context, int holdMs, String source) {
        try {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager == null) {
                WakeDebugStore.append(context, "NotificationManager 不可用，跳过通知兜底");
                return;
            }

            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "WakeBridge Wake",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("WakeBridge full-screen wake notifications");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);

            PendingIntent fullScreenIntent = buildWakeActivityPendingIntent(
                context,
                holdMs,
                source + ":fsi",
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("WakeBridge")
                .setContentText("正在尝试点亮屏幕")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenIntent, true)
                .build();

            notificationManager.notify(NOTIFICATION_ID, notification);
            WakeDebugStore.append(context, "全屏通知已发送");
        } catch (RuntimeException e) {
            WakeDebugStore.append(context, "全屏通知发送失败: " + e.getClass().getSimpleName());
            Log.e(TAG, "failed to post full-screen notification", e);
        }
    }

    private static void startWakeActivityDirectly(Context context, int holdMs, String source) {
        try {
            context.startActivity(buildWakeActivityIntent(context, holdMs, source + ":direct"));
            WakeDebugStore.append(context, "直接拉起 WakeActivity 已提交");
        } catch (RuntimeException e) {
            WakeDebugStore.append(context, "直接拉起 WakeActivity 失败: " + e.getClass().getSimpleName());
            Log.e(TAG, "failed to launch WakeActivity from background", e);
        }
    }
}
