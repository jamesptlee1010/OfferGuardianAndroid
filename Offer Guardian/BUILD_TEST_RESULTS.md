# OfferGuardian v0.9.15 — Local verification

- Base: `OfferGuardian_v0.9.14_Uber_Only_Source.zip` (latest prior project).
- Added `OfferScanWatchdog.java` and a standalone host test with **18 assertions passed** covering timeouts, fresh tokens, late callback rejection and service invalidation.
- Replaced the indefinite screenshot/OCR `capturing` flag with capture tokens; late task completion cannot reset the busy state of a newer scan. A capture older than **8,000 ms** is invalidated; partial offer verification is discarded before scanning resumes.
- Added a periodic (~45 second) monitor heartbeat, monitor health UI, watchdog recovery counts and Android foreground/accessibility state diagnostics; no additional polling service or screenshot loop.
- **All 26/26 available host-side GitHub workflow checks passed locally**, including previous history, import, OCR, delivery-count, rules, SQLite, overlay and Uber-only scanning checks.
- GitHub workflow updated for `versionName 0.9.15`, `versionCode 23` and new watchdog test; APK metadata validation now expects 0.9.15.
- **Android APK compilation was not performed locally:** the Android SDK/Gradle are unavailable in this environment. Test the GitHub-built APK on the phone for longer than the former 10–15 minute failure period.
- Caveat: Android may suspend or disable Accessibility independently; an app cannot silently re-enable a disabled service. A stale heartbeat helps distinguish this from a scanner timeout.
