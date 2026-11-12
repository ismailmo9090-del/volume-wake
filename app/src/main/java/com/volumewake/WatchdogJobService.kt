package com.volumewake

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

class WatchdogJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (VolumeWakePreferences.isEnabled(this) && !SelfHeal.isAccessibilityEnabled(this)) {
            Log.d(VolumeWakeAccessibilityService.TAG, "watchdog re-enabling accessibility")
            SelfHeal.enableAccessibility(this)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
