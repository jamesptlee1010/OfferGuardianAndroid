# GitHub APK build — Offer Guardian v0.7.0

1. In your existing GitHub repository, replace old code with the **contents** of the ZIP (not a new nested folder). Keep `.github/workflows/build-apk.yml` at the repository root.
2. If there is an obsolete `.github/workflows/android.yml` that tries `chmod +x gradlew`, remove it.
3. Open GitHub **Actions → Build Offer Guardian APK**, run or select the commit build.
4. Check the tests and compile steps. A successful build publishes **OfferGuardian-v0.7.0-installable-APK** under Artifacts.
5. Download the artifact ZIP, extract `OfferGuardian-v0.7.0-debug.apk`, then install on Android.

The workflow looks for a complete 0.7.0 project even if previous versions remain nested, installs Gradle (not a missing `gradlew`) and confirms the APK version. Android compilation has not been verified locally.

**Before uninstalling an old build:** Export Offer History to CSV or history will be deleted. Update-in-place requires the same debug signing key, which separate GitHub Actions runs may not share.


### New History summary
Build includes an isolated host-side `OfferHistoryStatsStandaloneTest` and ensures the app version is **0.7.0**. The on-device date-range selector and search apply to all averages. Select Accepted to see accepted-only averages. Install as an update if Android signing permits; uninstalling erases on-device history, so export CSV first if you must uninstall.
