package com.szecloud.wakebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WakeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int holdMs = intent != null ? intent.getIntExtra(WakeCoordinator.EXTRA_HOLD_MS, 5000) : 5000;
        WakeCoordinator.dispatchWake(context, holdMs, "broadcast");
    }
}
