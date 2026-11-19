# Offer Guardian v0.3.0 — Complete Android source project

**Full replacement project, not a patch or APK.** This ZIP contains the entire Android application and its GitHub Actions APK workflow. The ZIP has one project root (`app/`, `tests/`, `settings.gradle`, `.github/`). No private Uber map screenshots, customer names, or address information are bundled.

## What changed

- No “Learn the Decline button” step. That failed because Uber's X did not expose a usable Android click event.
- Screenshot recognition of the grey rounded-square X **plus the real offer-card horizontal border** works with both green-bordered Accept offers and black-bordered Match offers.
- The app reads the large **$ payout inside the same card** using on-device ML Kit OCR. An unrelated dollar amount elsewhere on screen is never a reason to decline.
- The screenshot poll runs around every 1.7 seconds while Uber Driver is foreground, rather than depending solely on Uber emitting Android accessibility events.
- Live diagnostics explain whether Accessibility is connected, screenshot capture succeeded, the X was recognised, price OCR succeeded, and the offer was above/below the threshold. Use “Copy diagnostics for troubleshooting.”
- The threshold is adjustable (default A$30). **Offers below A$30** qualify for the decline rule; **A$30 or above** trigger an optional sound/vibration alert and are always left for manual decision.
- TEST ONLY defaults to ON; auto-decline defaults to OFF after updating. **No tap occurs until both are deliberately changed.** A dispatched gesture is not confirmation that Uber processed a decline.

## Install with GitHub (no Android Studio required)

1. Extract the ZIP on your computer.
2. In your GitHub `OfferGuardianAndroid` repository, upload the **CONTENTS** of this ZIP to the **repository root**. That means `app/`, `tests/`, `build.gradle`, `settings.gradle`, `.github/workflows/build-apk.yml`, etc. Do **not** upload another enclosing `OfferGuardianAndroid/` folder.
3. If old nested project copies exist, delete the older ones later. The new workflow chooses a complete **v0.3.0** source tree, even if older versions are still present.
4. Commit; go to **Actions → Build Offer Guardian APK**. Wait for a green check.
5. Open the completed run → **Artifacts → OfferGuardian-v0.3.0-installable-APK**. Download the artifact ZIP, extract `OfferGuardian-v0.3.0-debug.apk` and install it on Android.
6. Confirm **Settings → Apps → Offer Guardian → App info** says **0.3.0**. Debug APKs built in different GitHub runs may be signed with different keys. If the phone rejects the update, uninstall the old app first and install the new one, then re-enable the Accessibility service and monitoring.

## Safe first test

1. When **parked**, enable Offer Guardian Android Accessibility service and **Monitor Uber Driver offers**. Keep **Detect grey X** enabled.
2. Set the threshold to A$30 and leave **TEST ONLY** ON. Do not enable auto-decline yet.
3. Open Uber Driver and wait for an offer. The service will try to capture its screen while Uber is foreground.
4. Return to Offer Guardian → **Live diagnostics → Refresh diagnostics**. If recognition works, it should say e.g. `TEST ONLY: A$11.47 < A$30.00. X at (579,752) ... No tap.`
5. If the app still appears inactive, press **Copy diagnostics for troubleshooting** and paste the copied text into ChatGPT. It will explain whether the service wasn't running, Android blocked screenshot capture, the card X wasn't recognised or OCR couldn't find the price.
6. Only consider enabling actual tapping after several test-only recognitions match the visible offers exactly. It is experimental and may violate Uber's automation rules or affect account status.

## Offline checks performed

- `OfferDetectorStandaloneTest`: 20 cases, including Match button and $30.59 threshold behaviour.
- `VisualDeclineDetectorSyntheticTest`: green-border and black-border offers, plus rejection without card border.
- `OfferScreenEvaluatorStandaloneTest`: 11 geometry and payout tests.
- Local detection against 12 user-provided real offer screenshots: 12 X positions found correctly; two unrelated app screenshots rejected. **Those photographs are not included in the project**.

**Limitations:** No Android SDK/Gradle was available for an end-to-end APK compilation here. Real-time OCR, Android screenshots, live gesture dispatch, and whether Uber actually declines an offer remain unverified on the phone. An action can fail if Uber changes its UI, Android blocks capture or Android disables the Accessibility service. No Internet permission or Uber credentials are required.
