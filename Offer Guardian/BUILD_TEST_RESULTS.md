# OfferGuardian v0.9.12 — build and test results

## Changes implemented
- Single-select weekday chips (All / Monday–Sunday), with tap-again deselection.
- Multi-select and tappable All / Morning / Lunch / Afternoon / Evening / Night filters.
- Exact non-overlapping local time boundaries: 06:00, 11:00, 14:00, 17:00 and 21:30; Night spans midnight to 06:00.
- SQLite queries apply weekday and selected periods to the History offer list, summary statistics and match-percentage denominator while preserving existing date range, advanced filter, delivery-count filter and sort options.
- Filter preferences persist locally between visits; Clear History Filters resets them.
- Long-pressing a History offer opens a confirmation and deletes only that database record, refreshing all statistics.
- GitHub build workflow expands bundled regression tests to keep the browser-upload source archive under 100 files.

## Verified in this environment
- Existing 24 host-side regression/check steps passed, including OCR, Faster Waze, offer-duration, tiered rules, History filters, delivery count, statistics, and overlay tests.
- 1,480 assertions in OfferHistoryTimeWindowStandaloneTest (including exhaustive 1,440-minute no-gap/no-overlap schedule checks) passed.
- 14 SQLite time/filter scenarios passed, including Monday vs Tuesday, Sunday, all five shifts and overnight local-time boundaries.
- Java syntax parsing passed for OfferHistoryActivity, OfferHistoryStore, OfferHistoryTimeWindow and MainActivity.
- Release ZIP and bundled tests both passed archive integrity and source-tree checks; packaged workflow extracted test bundle successfully and reran the targeted tests.

## Not verified here
- Android APK compilation and on-device behaviour (Android SDK/Gradle are not available in this environment). Use GitHub Actions for APK compilation and test the History controls on your phone.
