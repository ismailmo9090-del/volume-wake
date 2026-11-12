package com.volumewake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(VolumeWakeAccessibilityService.TAG, "boot/replace received: ${intent.action}")
        if (VolumeWakePreferences.isEnabled(context) && !SelfHeal.isAccessibilityEnabled(context)) {
            SelfHeal.enableAccessibility(context)
        }
        SelfHeal.scheduleWatchdog(context)
    }
}
