# Offer Guardian v0.4.0 — Complete GitHub-ready Android project

This is a **full replacement project**, not a patch or a compiled APK. The ZIP has **one project root** (containing `app/`, `tests/`, `.github/`, and Gradle files). It contains no Uber order screenshots or customer data.

## What's new: Uber can be quiet, Offer Guardian can ring

- When an Uber Driver offer is **below your adjustable minimum** (default A$30), the existing test-only/auto-decline rule applies. The Offer Guardian **high-value audio never plays** for below-threshold offers.
- When an offer is **A$30 or above**, Offer Guardian plays an **independent, offline alert WAV**, optionally vibrates, and leaves Uber Driver untouched for your decision.
- Select **Bright chime** or **Triple beep**, adjust **tone volume 0–100%**, repeat it **1–3 times**, enable/disable vibration.
- Select **Alarm volume** (default) or **Media volume**, useful if Android's Uber notification categories have been silenced. **Tone volume in the app cannot override the system volume.** Test both streams with the provided large **TEST high-value ring** button. Use **Stop test sound** if desired.
- A high-value offer makes one configured alert sequence; scanning the same still-visible offer does not keep ringing. Once the card disappears for two scans, the next offer can alert, including an identically priced offer.
- The local notification for a high-value offer has **no notification-channel sound**; audio is played directly by the app without depending on Uber's notification category or Android notification permission.
- Existing Accept/Match screenshot detection, threshold, monitoring, fail-closed auto-decline, diagnostics, and automatic-accept prohibition are retained. New version **0.4.0**.

## IMPORTANT: Silencing Uber is a separate manual Android setting

Offer Guardian **cannot change Uber Driver's own audio settings** or guarantee that all Uber sounds stop. On your phone, try Uber Driver's own sound options and/or **Android Settings → Apps → Uber Driver → Notifications → relevant offer category → Silent**. Leave Android's general sound ON; do **not** mute the whole phone or activate Do Not Disturb. If Uber plays sound internally instead of using a notification category, Android may not offer a reliable way to mute only that sound. Offer Guardian uses the **Alarm** or **Media** output stream, chosen in settings. Turn up that stream in Android sound settings and press **TEST high-value ring** to verify real output, including over Bluetooth.

## Setup with GitHub Actions

1. Extract the ZIP and upload its **contents**, not an enclosing folder, to the root of the GitHub repository. The correct root has `app/`, `tests/`, `.github/workflows/build-apk.yml`, `settings.gradle`, etc.
2. The workflow deliberately selects **only v0.4.0** project sources so older nested copies cannot be built accidentally. Delete stale workflows such as `.github/workflows/android.yml` to avoid duplicate failing jobs.
3. Commit → **Actions → Build Offer Guardian APK** → successful run → **Artifacts → OfferGuardian-v0.4.0-installable-APK** → download ZIP → extract APK → install on Android.
4. If APK signing key differs, Android may refuse to update over the older version. Only uninstall the previous version if needed, then re-enable Accessibility and reconfigure settings. **Uninstalling clears local settings.**
5. Confirm Android Settings → Apps → Offer Guardian → **version 0.4.0**.

## On-phone test

1. Verify Offer Guardian Accessibility and Monitor Uber Driver are enabled. Set A$30 minimum.
2. Leave **TEST ONLY** ON initially to avoid accidental taps. **High-value audio still plays in test-only mode** because the app never automatically accepts high-value requests.
3. Open Offer Guardian → **High-value offer sound**. Use alarm volume and test the ring, then try media mode if necessary. Adjust volume, tone, repeats and vibration.
4. Silence only Uber Driver notification sound if Android offers that control; verify regular Uber offers remain visible. Some Uber app-generated sounds may remain.
5. With Uber Driver foreground and while safely parked, test an offer under $30: no Offer Guardian high alert. Test an offer at/above $30: app should ring without pressing Uber's buttons. Open Live diagnostics for confirmation.
6. If Android is silent, first use **TEST high-value ring** in Offer Guardian itself. If even the test is silent, raise the selected system Alarm/Media volume, check Do Not Disturb and Bluetooth, then try the other output channel.

## Constraints and safety

- Built-in sounds are original, generated in the project; **no internet permission** or remote service.
- Sound starting in software doesn't guarantee the device's output was audible: Android's DND, system sound levels and audio routing apply.
- No way for this app to universally silence Uber's internal sounds; it can only supply its own independent alert.
- Screenshot matching and gesture automation are experimental and can have Uber account consequences. High-value offers are **never** accepted automatically. Only enable auto-decline after confirming accurate diagnostics for multiple real offers.
- Full Android build not compiled locally (Android SDK and Gradle are not installed here). The included GitHub Actions workflow compiles a debuggable APK and verifies its version.

## Code tests

- `OfferDetectorStandaloneTest` (Accept and Match payouts)
- `VisualDeclineDetectorSyntheticTest` (green and black borders)
- `OfferScreenEvaluatorStandaloneTest` (price belongs to matched card)
- Tone sanity checks (valid original WAV assets)

- `OfferAlertRulesStandaloneTest` (15 checks: quiet under $30, audible at $30 and above, disabled/invalid offers stay quiet)
