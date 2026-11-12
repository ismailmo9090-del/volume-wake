# VolumeWake

Wake and unlock support for phones whose **power button is broken or hard to reach**: press
**Volume Up** or **Volume Down** while the screen is off and the display wakes up.

Small native Kotlin app, no root, no Xposed, no PC required after install.

## Verified device

| Device | Android | Result |
|---|---|---|
| Samsung Galaxy A03 (SM-A035F), One UI | 13 | **Working** — volume press while the screen is off wakes the display |

Other devices are untested. Screen-off volume routing is firmware-dependent; see
[Compatibility](#compatibility).

## Install (no adb needed)

1. Download `VolumeWake-v1.2.apk` from the [Releases](../../releases) page.
2. Open it on the phone and allow **Install unknown apps** for your browser/file manager when asked.
3. Open **VolumeWake** and turn on **Enable Volume Wake**.
4. The app opens Android Accessibility settings. Enable **VolumeWake button service**
   (Samsung: *Settings → Accessibility → Installed apps → VolumeWake*).
5. Return to the app. The switch should now stay on.
6. Lock the phone and press Volume Up or Volume Down — the screen should wake.

If Android refuses to enable the service with a *restricted setting* warning (Android 13+):
open **Settings → Apps → VolumeWake → ⋮ → Allow restricted settings**, then enable the
service again.

> Updating from an older debug/test build fails with a signature error. Uninstall the old
> copy first (this erases the app's saved switch state only).

## After reboot — what actually happens

Once you have enabled the accessibility service **one time**, Android keeps it enabled
across normal reboots, so volume-to-wake keeps working with no further action. The app also
registers a boot receiver and a 15-minute watchdog job that re-arm it if the system
disables the service.

**Honest limitation:** stock Android does not allow any app to silently grant itself
Accessibility access. There is no install-and-it-magically-enables-itself path on an
unmodified phone, and this app does not try to bypass that — doing so would be covertly
enabling a highly privileged permission. If the service is ever turned off, either turn it
back on in Accessibility settings, or (optionally) grant `WRITE_SECURE_SETTINGS` once via
adb/root and the self-heal code can restore it automatically:

```bash
adb shell pm grant com.volumewake.app android.permission.WRITE_SECURE_SETTINGS
```

That grant is optional. Normal installs do not need adb and do not need this permission
to survive reboots.

## Why a media session is involved

On Samsung/One UI, volume keys pressed while the display is off are consumed by the system's
media-key path before accessibility key events are delivered, so `onKeyEvent()` never sees
them. With no active media session, the system also swallows the press entirely.

The service therefore keeps a **local** `MediaSession` active while the display is off. The
system then applies the press to `STREAM_MUSIC`, which the app can observe in two places:

- a `ContentObserver` on `Settings.System.CONTENT_URI` (volume settings writes), and
- the system `android.media.VOLUME_CHANGED_ACTION` broadcast (usually the earliest signal).

When either fires while `PowerManager.isInteractive == false`, the app:

1. acquires a short `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` (1 s), and
2. starts a fully transparent `WakeActivity` with `showWhenLocked`/`turnScreenOn` that
   finishes and removes its task after 200 ms.

`WakeActivity` runs in its **own task affinity**, so finishing it never reveals the app's
main screen — after unlocking you land back on your home screen or previous app, not in
VolumeWake.

When the display is on, the media session is inactive and volume buttons behave normally.
Duplicate events are debounced at 60 ms.

## Response time

The wake request is issued within tens of milliseconds of the system committing the volume
change. What you notice on a phone is dominated by the display/AOD wake animation, which is
controlled by the firmware, not by this app. The app adds no polling, no timers and no
foreground service.

## Privacy and footprint

- No `INTERNET` permission, no analytics, no ads, no accounts, no network code.
- `allowBackup=false`; no data leaves the device.
- Accessibility service is used only for its key-event hook; accessibility events are ignored.
- Wake lock is held for at most 1 second and only after a detected volume press.

## Permissions

| Permission | Why |
|---|---|
| Accessibility service binding | Receive key events where firmware exposes them |
| `WAKE_LOCK` | Turn the display on for up to 1 s |
| `TURN_SCREEN_ON` | Let the trampoline activity wake the display |
| `MODIFY_AUDIO_SETTINGS` | Observe/normalize music-stream volume handling |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the service after reboot |
| `WRITE_SECURE_SETTINGS` | Optional self-heal only (normal installs cannot grant it; adb/root only) |

## Compatibility

| Platform | Assessment |
|---|---|
| Android 8–11 (API 26–30) | APIs exist. OEM screen-off volume routing untested. |
| Android 12–14 (API 31–34) | Untested on third-party firmware; Galaxy A03 / Android 13 verified. |
| Android 15 (API 35) | Build/target level. Untested on hardware. |

If volume keys change nothing and never wake the phone, that firmware does not expose
screen-off volume presses to apps at all. Without root or a privileged/system-signed
build, no third-party app can change that input policy. Battery saver, Doze, active calls
and Bluetooth routing can also affect the media session path and have not all been
characterized on every OEM.

## Build from source

Requirements: JDK 17 (the Kotlin compiler fails on JDK 25), Android SDK platform 35 and
build-tools 35.0.0.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
```

Release signing is optional and read from an untracked `keystore.properties`:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release task produces an unsigned APK. `keystore.properties`,
keystores and build outputs are gitignored. Published APKs are signed; future updates must
use the same signing key or Android will refuse to install over an existing copy.

## Project layout

```
app/src/main/java/com/volumewake/
  VolumeWakeAccessibilityService.kt  key hook, media session, observers, wake
  WakeActivity.kt                    transparent trampoline, own task
  MainActivity.kt                    switch + status
  SelfHeal.kt                        optional accessibility re-enable + watchdog
  WatchdogJobService.kt              15-minute periodic self-heal attempt
  BootReceiver.kt                    boot/update re-arm
  VolumeWakePreferences.kt           persisted switch
```
