package com.volumewake

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(VolumeWakeAccessibilityService.TAG, "WakeActivity onCreate")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        overridePendingTransition(0, 0)

        window.decorView.postDelayed({
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finishAndRemoveTask()
        }, FINISH_DELAY_MS)
    }

    companion object {
        // Long enough for the display to come up, short enough that the user
        // never lands on this activity after unlocking.
        private const val FINISH_DELAY_MS = 200L
    }
}
