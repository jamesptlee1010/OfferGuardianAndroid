# Offer Guardian 3.0.0 — Experimental scanner reset

Full Android source project based on v2.6.1. Existing local database, saved rules, history import/export, alert settings, draggable D/DF/DR dock and Uber-over-DoorDash visual fallback retained. No automatic acceptance or new permissions. Read `CHANGELOG_v3.0.0.md` and `BUILD_TEST_RESULTS.md` for details and limits.

## New in this version
- Preserve a verified pending DoorDash decline across ordinary OCR changes; prioritize its exact confirmation button and keep deadline/ownership checks.
- Space Uber retries, vary verified gesture hold, correct bounded hour/minute OCR lookalikes, and try alternate contrast/size on payout OCR failures.
- Select Uber Eats only, DoorDash only, both or neither in Platforms. Single-platform paths skip the disabled platform's work. The D/DF/DR dock is hidden in Uber-only mode. Shared OCR is still used; actual RAM savings depend on the device.
- Bound outstanding OCR work and reuse the pixel buffer to reduce allocation churn.
- History deduplication uses complete financial values, nearby timestamps, exact destination and similar pickup, never as permission to tap. Existing suspected duplicates require review and approval. Export history before deletion. Distinct offers with identical details within 60 seconds may be indistinguishable without a platform offer ID.
- $30/hour enabled by default for single, double and triple delivery tiers on both platforms. Saved custom rules remain unchanged; use Apply $30/hr to all six delivery tiers if desired.

## Build and install
1. Export offer history before upgrading. Keep the same debug signing key for in-place installation; if the key changes, uninstalling can delete local data.
2. Extract the ZIP into a fresh folder. Upload all contents including the hidden `.github/workflows/build-apk.yml`. Keep `tests_bundle.zip` zipped; CI expands it. Avoid multiple project copies.
3. Run Actions → Build Offer Guardian APK → Run workflow. It tests and builds using SDK 35, JDK 17, Gradle 8.9. Download `OfferGuardian-v3.0.0-installable-APK` if successful.
4. Test safely parked with automatic decline/test-only toggles checked. Verify the offer rate, target and result before enabling automatic gestures. Notification access remains required for DoorDash notification switching; use the app's shortcut if desired. Review delivery-platform agreements.

## Validation
Host tests and Java syntax parsing completed. Full Android SDK/Gradle compilation and phone testing not performed in this environment. Gesture dispatch/dismissal does not confirm the platform's recorded outcome. RAM savings and timing are not measured; a stalled OCR backlog may require restarting monitoring/accessibility if tasks never finish.
