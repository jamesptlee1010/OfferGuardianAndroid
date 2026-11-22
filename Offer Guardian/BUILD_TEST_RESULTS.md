# Offer Guardian v0.9.3 — local source verification

Source base: ORIGINAL `OfferGuardian_FULL_GitHub_v0.9.1_Faster_Waze.zip`, freshly extracted. Changes were NOT made on top of the intermediate v0.9.2 ZIP.

## Completed offline checks

- **15 self-contained Java regression suites PASSED** (original functionality plus new 28-assertion duration, payout and frame-verification suite).
- **SQLite v1→v2 migration smoke test PASSED**, preserving old history and marking legacy rates unverified.
- **GitHub Actions YAML parsed successfully**, versionName 0.9.3 and dedicated new test steps checked.
- Tested full 67-minute result from `1 hr 7 min (9.8 km)` and gross estimated hourly rate `A$26.56/hr` on `A$29.66`.
- Tested split OCR, two-hour delivery, 45-minute delivery, unrealistic 7-minute 9.8km trip, full payout decimals, changing payout, changing merchant, stale reading and read recovery.
- Existing visual day/night/Waze synthetic tests and WazeFastVerifier tests PASSED.
- One optional `VisualDeclineDetectorStandaloneTest` expects three external real screenshot files that were never included in the original project; **not run**. Its self-contained synthetic replacements passed.

## Not verified here

- **No Android SDK or Gradle installed locally:** no full Android APK build performed. Only GitHub Actions can perform APK compile in the supplied workflow.
- **No real Nubia Focus 5G test:** screenshot rate-limiting, real ML Kit OCR, accessibility service uptime and Waze timing remain device-specific.
- OCR is inherently imperfect; no guard can reliably infer a missing hour when every OCR frame reports a plausible minute-only duration.

## Testing instructions

1. Upload the project to the root of your GitHub repository and run `Build Offer Guardian APK` in Actions.
2. Verify all test steps and the `:app:assembleDebug` step complete green before using the artifact.
3. Export existing offer history CSV before any uninstall. A different debug signing key may require uninstall to install the new build.
4. Install on the phone, leave `TEST ONLY` enabled, and check Uber and Waze offer cards including 1-hour-plus trips, shopping orders, and live diagnostic latency before enabling auto decline.
