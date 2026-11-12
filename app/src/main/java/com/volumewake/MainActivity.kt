package com.volumewake

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var enabledSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SelfHeal.scheduleWatchdog(this)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(getColor(R.color.volume_wake_background))
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_volume_wake)
            contentDescription = getString(R.string.app_name)
            adjustViewBounds = true
        }
        root.addView(icon, LinearLayout.LayoutParams(-1, (64 * resources.displayMetrics.density).toInt()))

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(0xff202124.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val explanation = TextView(this).apply {
            text = "Wakes the screen with Volume Up or Volume Down when this phone's firmware delivers those buttons to VolumeWake."
            textSize = 16f
            setTextColor(0xff5f6368.toInt())
            setPadding(0, padding / 2, 0, padding)
        }
        root.addView(explanation, LinearLayout.LayoutParams(-1, -2))

        enabledSwitch = Switch(this).apply {
            text = getString(R.string.enable_volume_wake)
            textSize = 18f
            setOnCheckedChangeListener { _, checked ->
                if (checked && !isAccessibilityEnabled()) {
                    if (!SelfHeal.enableAccessibility(this@MainActivity)) {
                        isChecked = false
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
                VolumeWakePreferences.setEnabled(this@MainActivity, checked && isAccessibilityEnabled())
                updateStatus()
            }
        }
        root.addView(enabledSwitch, LinearLayout.LayoutParams(-1, -2))

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, padding / 2, 0, padding)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        val settings = TextView(this).apply {
            text = getString(R.string.open_accessibility_settings)
            textSize = 16f
            setTextColor(getColor(R.color.volume_wake_blue))
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(settings, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (VolumeWakePreferences.isEnabled(this) && !isAccessibilityEnabled()) {
            SelfHeal.enableAccessibility(this)
        }
        if (::enabledSwitch.isInitialized) {
            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = VolumeWakePreferences.isEnabled(this) && isAccessibilityEnabled()
            enabledSwitch.setOnCheckedChangeListener { _, checked ->
                if (checked && !isAccessibilityEnabled()) {
                    if (!SelfHeal.enableAccessibility(this)) {
                        enabledSwitch.isChecked = false
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
                VolumeWakePreferences.setEnabled(this, checked && isAccessibilityEnabled())
                updateStatus()
            }
            updateStatus()
        }
    }

    private fun updateStatus() {
        status.text = when {
            !isAccessibilityEnabled() -> "Setup required: enable VolumeWake button service in Accessibility settings."
            !VolumeWakePreferences.isEnabled(this) -> "Ready, but disabled."
            else -> "Enabled. Screen-off wake support depends on device firmware; test both volume buttons."
        }
    }

    private fun isAccessibilityEnabled(): Boolean = SelfHeal.isAccessibilityEnabled(this)
}
