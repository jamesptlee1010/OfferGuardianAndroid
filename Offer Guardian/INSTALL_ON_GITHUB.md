# Offer Guardian v0.9.12 — GitHub APK build

1. Export your existing Offer History to CSV on your phone. Save it in Downloads or another location outside app storage.
2. Extract the v0.9.12 source ZIP and upload the extracted contents to the root of your existing GitHub repository, preserving folder structure and replacing existing files. Keep `.github/workflows/build-apk.yml` in its exact location. Remove obsolete nested builds.
3. Check `app/build.gradle` says `versionName '0.9.12'` and `versionCode 20`.
4. Run **Actions → Build Offer Guardian APK → Run workflow**. The workflow runs regression tests, configures Java 17 / Android SDK 35 / Gradle 8.9 and compiles the APK.
5. Download `OfferGuardian-v0.9.12-installable-APK` when the action is green. Install its `OfferGuardian-v0.9.12-debug.apk`. If debug signing differs, an uninstall may be necessary; keep the CSV backup first.
6. If needed, open Offer History → Import CSV and select your backup. Check the imported counts and outcomes. Settings are not in the history CSV.
7. Performance settings → Low Resource Mode is on by default. Keep TEST ONLY enabled and verify that Uber Driver offers are reliably detected before enabling automated decline. Switch Low Resource Mode off to restore Waze monitoring.

This archive is source code, not a compiled APK. No Android SDK is available in the preparation environment, so Android build and real-device performance remain unverified.
