# OfferGuardian v0.9.14 — Local verification

- Base: v0.9.13 Import History source ZIP.
- Removed `com.waze` Android package queries, accessibility events, and UI references.
- Removed navigation-specific scanning, WazeFastVerifier, its dedicated test, and Waze-only gesture verification paths.
- Retained Uber Driver monitoring (1700 ms normal polling) and bounded 430 ms active-offer OCR retries.
- All 25 GitHub workflow host-side check steps passed locally with updated tests.
- Confirmed the navigation package classifies as OTHER, never as an allowed scan/decline host.
- Preserved History import/export, filters, sorting, tiered rules and existing OCR checks.
- Android SDK/Gradle are unavailable locally: Android APK compilation and phone testing remain outstanding.

## Important GitHub upgrade step

Browser uploads do not delete old files. If your existing repository still has `WazeFastVerifier.java`, delete that file separately. The v0.9.14 archive itself does not include it.
