# Offer Guardian v0.9.5 — local build and test results

Base project: `OfferGuardian_v0.9.4_Advanced_History_Filters_Faster_Waze_Source.zip` from this conversation. This is an in-place extension, not a recreated app.

## Passed (22 November 2026)

- **18 GitHub Actions host-side test/check steps**, run from the production workflow (all passed).
- **17 Java standalone test suites** (plus the existing synthetic X detector test); original external-fixture-only detector test cannot run because the original screenshot fixtures were not bundled.
- **47 v0.9.5 incident/safety assertions** including the actual `$5 / 11 min` offer, Coles `$27.10 / 61 min`, `$29.66 / 67 min`, hour lower-bound confirmation, plausible vs unprovable declines, changed payouts, merchant consistency and retry limit.
- **28 earlier OCR/duration regression assertions** and **45 previous History filter regression assertions**.
- **9 live SQLite queries** from production History filter SQL, now including an `HOUR_BOUND` item (exact duration unknown) that must never qualify through hourly-rate or distance filters.
- **SQLite v1→v2 migration** preserved previous history and legacy-unverified flags.
- The GitHub Actions workflow is valid YAML and is updated to `v0.9.5` / `versionCode 14` / `versionName '0.9.5'` / Android artifact `OfferGuardian-v0.9.5-debug.apk`.
- History filter implementation is unchanged except an additional test case. The SQLite schema is still version 2 and requires no new migration.

## Not tested here

- **Full Android APK build:** Android SDK and Gradle are unavailable in this environment. The included `.github/workflows/build-apk.yml` performs `:app:assembleDebug` on GitHub and publishes an installable APK if compilation succeeds.
- **Real phone / live Uber and Waze OCR and gesture testing:** not available. Host-side synthetic screenshot tests cannot guarantee actual on-device results.
- Automatic X gesture dispatch is only an *attempt*; its completion does not independently confirm that Uber rejected an offer. The app continues to record this distinction in diagnostics/history.

## Acceptance checks on phone (TEST ONLY first)

1. Verify rules are **Price ≥ A$40**, **Hourly ≥ A$50/hr**, **ANY (OR)** if those are the intended rules; also verify **Auto-decline**, **Visual X** and **TEST ONLY** switches.
2. With TEST ONLY enabled, `$5` displayed with no decimal should be read as A$5.00 and show `FAILS rules` at an 11-minute duration.
3. A two-image complete `1 hr 1 min` order should show 61 minutes; `1 hr 7 min` should show 67 minutes.
4. If only `1 hr` is read and the known payout/rules conclusively fail, Live Diagnostics should report a confirmed **minimum duration**. History should show `HOUR_BOUND` and **Unknown** hourly, not a guessed 60-minute exact rate.
5. Unreliable payout, distance, merchant, missing hours, or criteria that could qualify should **not** dispatch a gesture.
6. With TEST ONLY disabled and auto-decline enabled, test real-world behaviour carefully; capture Live Diagnostics if the service withholds or fails to dispatch an X gesture.
7. Verify Waze fast verification still works, History Filters operate independently, and historical CSV export retains all rows.
