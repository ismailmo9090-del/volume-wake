package com.volumewake

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

object SelfHeal {
    private const val JOB_ID = 7481

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, VolumeWakeAccessibilityService::class.java).flattenToString()
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.any { it.equals(expected, ignoreCase = true) } == true
    }

    fun enableAccessibility(context: Context): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(VolumeWakeAccessibilityService.TAG, "self-heal skipped: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        val component = ComponentName(context, VolumeWakeAccessibilityService::class.java).flattenToString()
        val cr = context.contentResolver
        val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val services = current.split(':').filter { it.isNotBlank() }.toMutableSet()
        if (services.add(component)) {
            Settings.Secure.putString(
                cr,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":")
            )
        }
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        val ok = isAccessibilityEnabled(context)
        Log.d(VolumeWakeAccessibilityService.TAG, "self-heal accessibility ok=$ok")
        return ok
    }

    fun scheduleWatchdog(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJobService::class.java))
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .build()
        runCatching { scheduler.schedule(job) }
    }
}
