package com.volumewake

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeWakeAccessibilityService : AccessibilityService() {
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var mediaSession: MediaSession
    private var lastWakeAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastMusicVolume = -1

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "screen action=${intent.action}")
            updateSession(!powerManager.isInteractive)
        }
    }

    // The system broadcasts the volume change as soon as AudioService commits it,
    // which is earlier than the Settings provider notification. Whichever arrives
    // first wins; wakeIfNeeded() debounces the duplicate.
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != VOLUME_CHANGED_ACTION) return
            val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
            if (stream != AudioManager.STREAM_MUSIC) return
            if (!VolumeWakePreferences.isEnabled(this@VolumeWakeAccessibilityService)) return
            Log.d(TAG, "volume broadcast music interactive=${powerManager.isInteractive}")
            if (!powerManager.isInteractive) wakeIfNeeded()
        }
    }

    // One UI swallows screen-off volume keys unless an active media session
    // exists, and it does not deliver remote VolumeProvider callbacks. Keeping a
    // local session lets the system adjust STREAM_MUSIC; this observer turns
    // that adjustment into a screen wake.
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!VolumeWakePreferences.isEnabled(this@VolumeWakeAccessibilityService)) return
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val changed = current != lastMusicVolume
            lastMusicVolume = current
            val key = uri?.lastPathSegment
            Log.d(
                TAG,
                "settings change key=$key music=$current changed=$changed interactive=${powerManager.isInteractive}"
            )
            if (!powerManager.isInteractive && (changed || key?.startsWith("volume_") == true)) {
                wakeIfNeeded()
            }
        }
    }

    private val preferencesListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled") {
            if (VolumeWakePreferences.isEnabled(this)) {
                updateSession(!powerManager.isInteractive)
            } else {
                mediaSession.isActive = false
                releaseWakeLock()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(AudioManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        lastMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        mediaSession = MediaSession(this, "VolumeWake")
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }

        val volumeFilter = IntentFilter(VOLUME_CHANGED_ACTION)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(volumeChangeReceiver, volumeFilter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(volumeChangeReceiver, volumeFilter)
            }
        }.onFailure { Log.w(TAG, "volume broadcast unavailable", it) }
        getSharedPreferences("volume_wake", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferencesListener)

        updateSession(!powerManager.isInteractive)
        Log.d(TAG, "service connected music=$lastMusicVolume")
    }

    private fun updateSession(screenOff: Boolean) {
        if (!::mediaSession.isInitialized) return
        val enabled = VolumeWakePreferences.isEnabled(this)
        mediaSession.isActive = enabled
        if (!enabled) {
            mediaSession.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_NONE, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .build()
            )
            return
        }
        mediaSession.setPlaybackToLocal(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .setState(
                    if (screenOff) PlaybackState.STATE_PLAYING else PlaybackState.STATE_NONE,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (screenOff) 1.0f else 0f
                )
                .build()
        )
        Log.d(TAG, "session updated screenOff=$screenOff active=${mediaSession.isActive}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyEvent key=${event.keyCode} action=${event.action} interactive=${powerManager.isInteractive}")
        if (!VolumeWakePreferences.isEnabled(this)) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        // Returning false lets Android perform its normal volume adjustment.
        if (event.action == KeyEvent.ACTION_DOWN && !powerManager.isInteractive) {
            wakeIfNeeded()
        }
        return false
    }

    private fun wakeIfNeeded() {
        Log.d(TAG, "wakeIfNeeded interactive=${powerManager.isInteractive}")
        if (powerManager.isInteractive) return
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < DEBOUNCE_MS) return
        lastWakeAt = now
        if (wakeLock == null) {
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "VolumeWake:button"
            ).apply { setReferenceCounted(false) }
        }
        @Suppress("DEPRECATION")
        wakeLock?.acquire(WAKE_LOCK_MS)
        runCatching {
            startActivity(
                Intent(this, WakeActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
            )
            Log.d(TAG, "WakeActivity launch requested")
        }.onFailure { Log.e(TAG, "WakeActivity launch failed", it) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        releaseWakeLock()
        runCatching { contentResolver.unregisterContentObserver(volumeObserver) }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(volumeChangeReceiver) }
        getSharedPreferences("volume_wake", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferencesListener)
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    companion object {
        const val TAG = "VolumeWake"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val DEBOUNCE_MS = 60L
        private const val WAKE_LOCK_MS = 1_000L
    }
}
