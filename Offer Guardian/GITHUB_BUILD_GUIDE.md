# GitHub APK build — Offer Guardian v0.9.6

1. In your existing GitHub repository, replace old code with the **contents** of the ZIP (not a new nested folder). Keep `.github/workflows/build-apk.yml` at the repository root.
2. If there is an obsolete `.github/workflows/android.yml` that tries `chmod +x gradlew`, remove it.
3. Open GitHub **Actions → Build Offer Guardian APK**, run or select the commit build.
4. Check the tests and compile steps. A successful build publishes **OfferGuardian-v0.9.6-installable-APK** under Artifacts.
5. Download the artifact ZIP, extract `OfferGuardian-v0.9.6-debug.apk`, then install on Android.

The workflow looks for a complete 0.9.6 project even if previous versions remain nested, installs Gradle (not a missing `gradlew`) and confirms the APK version. Android compilation has not been verified locally.

**Before uninstalling an old build:** Export Offer History to CSV or history will be deleted. Update-in-place requires the same debug signing key, which separate GitHub Actions runs may not share.


### New History summary
Build includes an isolated host-side `OfferHistoryStatsStandaloneTest` and ensures the app version is **0.9.6**. The on-device date-range selector and search apply to all averages. Select Accepted to see accepted-only averages. Install as an update if Android signing permits; uninstalling erases on-device history, so export CSV first if you must uninstall.

## v0.9.6 feature checks

The workflow compiles and runs host-side tests for the overlay rule gate and the conservative two-frame accepted-job evidence matcher. The full APK build is the final step; those host tests are not a substitute for testing on your phone.

### New in v0.9.6: minimum deliveries

The Offer Rules Builder contains a separate mandatory minimum-deliveries switch and options from one through four. A green offer badge with a readable parenthesised number supplies the delivery count; an entirely readable badge without brackets supplies one. Count failures require two matching screenshots of the payout, count, card position and pickup merchant. They can be rejected even if total time is unreadable, with hourly rate saved as Unknown. If count is unreadable, decline is withheld. The workflow runs **51 new regression assertions** before compiling the APK.

### Preserved from v0.9.5: Faster Waze / decline recovery
The GitHub job now runs host-side regression tests for the **fast Waze confirmation**, ensuring a single screenshot never authorises a tap and that changes in price, X position, merchant, age, confidence or offer identity prevent it. It also retains tests for both X templates and trusted Uber/Waze window contexts. Real user screenshots are NOT packaged or uploaded to GitHub. An APK can be installed after the job succeeds; test both Waze and day/night modes with TEST ONLY before enabling auto-declines.

### v0.9.5 OCR reliability verification (preserved)

Test the actual `1 hr 7 min (9.8 km)` layout, Waze displayed over Uber, short/long orders, day/night, and delayed loading. First screen reading is provisional; a second matching reading and plausibility check are required before an action. Legacy rows remain intact but hourly and km values are labelled UNVERIFIED. As Android debug signing keys may change across GitHub runs, **export history before attempting any uninstall**.

## Added in v0.9.4 — Advanced History Filters (preserved)

History → **Filters** (beside the period selector) allows min/max payment, hourly, A$/km and kilometres, with **ALL (AND)** or **ANY (OR)**. These are history-only preferences, saved on the phone. They never change Active Rules or the accessibility service. The summary cards and visible list are filtered together. **Export all CSV** always exports the complete database.

The workflow now checks `OfferHistoryFiltersStandaloneTest` (45 assertions) and executes production filter SQL against SQLite (9 scenarios). The build still requires the Android SDK provided by GitHub Actions. No production APK was compiled here.
