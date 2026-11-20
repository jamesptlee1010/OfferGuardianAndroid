# GitHub APK build — Offer Guardian v0.9.0

1. In your existing GitHub repository, replace old code with the **contents** of the ZIP (not a new nested folder). Keep `.github/workflows/build-apk.yml` at the repository root.
2. If there is an obsolete `.github/workflows/android.yml` that tries `chmod +x gradlew`, remove it.
3. Open GitHub **Actions → Build Offer Guardian APK**, run or select the commit build.
4. Check the tests and compile steps. A successful build publishes **OfferGuardian-v0.9.0-installable-APK** under Artifacts.
5. Download the artifact ZIP, extract `OfferGuardian-v0.9.0-debug.apk`, then install on Android.

The workflow looks for a complete 0.9.0 project even if previous versions remain nested, installs Gradle (not a missing `gradlew`) and confirms the APK version. Android compilation has not been verified locally.

**Before uninstalling an old build:** Export Offer History to CSV or history will be deleted. Update-in-place requires the same debug signing key, which separate GitHub Actions runs may not share.


### New History summary
Build includes an isolated host-side `OfferHistoryStatsStandaloneTest` and ensures the app version is **0.9.0**. The on-device date-range selector and search apply to all averages. Select Accepted to see accepted-only averages. Install as an update if Android signing permits; uninstalling erases on-device history, so export CSV first if you must uninstall.

## v0.9.0 feature checks

The workflow compiles and runs host-side tests for the overlay rule gate and the conservative two-frame accepted-job evidence matcher. The full APK build is the final step; those host tests are not a substitute for testing on your phone.

### New in v0.9.0
The GitHub job now runs host-side regression tests for both X templates and trusted Uber/Waze window contexts. Real user screenshots are NOT packaged or uploaded to GitHub. An APK can be installed after the job succeeds; test both Waze and day/night modes with TEST ONLY before enabling auto-declines.
