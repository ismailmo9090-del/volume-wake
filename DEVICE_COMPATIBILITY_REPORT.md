# VolumeWake device compatibility report

**Report date:** 2026-11-12
**App version:** 1.2 (`com.volumewake.app`, versionCode 3)

## Verified result

| Device / Android | Screen-off volume delivery | Wake result | Status |
|---|---|---|---|
| Samsung Galaxy A03 (SM-A035F), Android 13, One UI | Accessibility key events **not** delivered; system adjusts `STREAM_MUSIC` when a local media session is active | **Display wakes** on Volume Up / Volume Down | Pass (wake verified on hardware) |
| Other devices | Unknown | Unknown | Requires testing |

## Evidence from the Galaxy A03

- Display-off state observed as `mWakefulness=Dozing` (always-on display) with
  `PowerManager.isInteractive=false`.
- With no active media session, screen-off volume presses produced no `Adjusting` log and no
  stream change — the system swallowed them.
- With a remote `VolumeProvider`, Samsung logged `Adjusting ... VolumeWake` but never invoked
  the app-side callback.
- With an active **local** media session (`STATE_PLAYING` while the display is off), the
  system changed `STREAM_MUSIC`; the settings observer and `VOLUME_CHANGED_ACTION` broadcast
  both fired.
- The app then acquired a wake lock and started the trampoline activity. PowerManager
  recorded `Waking up from Dozing (reason=application, com.volumewake.app)` — the wake is
  attributable to this app, not a physical power key.
- Repeated volume presses woke the device; a 6-second control period with no input stayed in
  Dozing (no spurious wakes).
- Measured app-side latency from the settings change to the wake request: ≈45 ms. The rest
  of the perceived delay is the display wake/AOD animation.

## Not verified

The following were not exercised on hardware and should not be assumed:

- Bluetooth audio or an active phone call owning media routing.
- Battery Saver and prolonged Doze.
- Devices other than the Galaxy A03.
- Self-heal via `WRITE_SECURE_SETTINGS` (requires an adb/root grant; normal installs do not
  have it).
- Update-in-place across different signing keys (not possible by Android design).

## Interpretation

A device "passes" only if, after the one-time accessibility enable, both volume buttons wake
the display while it is off and normal volume adjustment still works while it is on. The
accessibility state alone does not prove screen-off delivery; the Galaxy A03 needed the
local media session described in `README.md`.

If a device neither changes volume nor wakes, its firmware never exposes screen-off volume
presses to the media/accessibility layers. No rootless third-party app can change that input
policy; only firmware support, root-level input injection, or a system-signed privileged app
can.

## Reproduce

1. Install the release APK, enable the switch and the accessibility service.
2. Turn the screen off and wait for the always-on display.
3. Press Volume Up, then Volume Down; confirm the display wakes.
4. `adb logcat -s VolumeWake` should show `volume broadcast` or `settings change` followed by
   `wakeIfNeeded` and `WakeActivity onCreate`; `adb shell dumpsys power` should attribute the
   wake to `com.volumewake.app`.
