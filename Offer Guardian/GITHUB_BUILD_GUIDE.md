# OfferGuardian v0.9.15 — GitHub build guide

The source monitors only Uber Driver when Uber Driver is foreground. It retains ~1.7 s idle scanning and 430 ms bounded offer verification retries. The 8-second watchdog monitors the existing pipeline and adds no new screenshot polling loop.

1. Extract `OfferGuardian_v0.9.15_Uber_Only_Watchdog_Source.zip` on your computer.
2. Upload its *extracted contents* to the repository root, preserving `.github/workflows/build-apk.yml`, `app/`, `tests_bundle.zip`, `settings.gradle`, and `build.gradle`.
3. Remove outdated nested complete copies of earlier projects and any obsolete Waze source from your existing repository; GitHub browser upload does not remove old files. Keep only one active build workflow.
4. Open **Actions → Build Offer Guardian APK → Run workflow**.
5. Download the APK artifact. Export History before uninstalling; installing an APK signed with a different debug key may require an uninstall.
6. Begin with **TEST ONLY**, check Monitor Health/diagnostics and test on real Uber Driver offer screens while safely parked.

Version: `versionName 0.9.15`, `versionCode 23`. The workflow runs 26 host-side checks including scanner timeout/callback safety, then compiles Android using its SDK. Android APK compilation and prolonged phone testing were not available locally.
