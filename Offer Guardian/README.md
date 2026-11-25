# OfferGuardian v0.9.15 — Uber-only monitoring reliability

Complete Android source for OfferGuardian, updated from v0.9.14. **Waze support remains removed.** Only Uber Driver in the foreground is supported for OCR and auto-decline.

### New in v0.9.15

The scanner no longer relies on a capture flag that can remain busy forever if an Android screenshot callback or ML Kit OCR completion is lost. An 8-second **scanner watchdog** invalidates stalled work, safely discards delayed results, clears incomplete verification, and allows the Uber-only scan loop to resume. Repeated Android screenshot failures receive a short bounded backoff.

The app now shows a **Monitor Health** heartbeat and recovery count in Live diagnostics and includes these details when diagnostics are copied. A stale or absent heartbeat can point to Android stopping/suspending the Accessibility service, which the app cannot forcibly restart.

The normal 1.7-second Uber scanning interval, bounded 430 ms verification retries, separate 1/2/3-delivery rules, OCR safeguards, History filters, CSV import/export, accepted-offer tracking, and live offer overlay are preserved. No Waze scanning is restored and no extra periodic screenshot work is added.

### Building and testing

Extract the ZIP, upload the source folders and files (including `.github` and `tests_bundle.zip`) into your GitHub repository, then run **Actions → Build Offer Guardian APK**. The workflow expands bundled host-side tests, runs them, and builds an Android debug APK.

This ZIP contains source, **not a compiled APK**. The scanner watchdog passed standalone tests, and 26 host-side workflow checks passed locally. Android compilation and prolonged real-phone testing remain outstanding. Start with **TEST ONLY** and verify on your device while safely parked.

See `INSTALL_ON_GITHUB.md`, `GITHUB_BUILD_GUIDE.md`, and `CHANGELOG_v0.9.15.md`.
