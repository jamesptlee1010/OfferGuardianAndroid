# Offer Guardian v0.9.6 — host-side test results (22 November 2026)

Base ZIP: `OfferGuardian_v0.9.5_AutoDecline_Recovery_Faster_Waze_History_Filters_Source.zip`.

## Completed locally

- **19 of 19 available host-side workflow checks passed** when the Java, SQL, grep and static validation steps from `.github/workflows/build-apk.yml` were run against this source tree. Cloud SDK/Gradle setup and APK compilation were not available here.
- **51 new mandatory delivery-count assertions passed:** single, double, triple, 4+, split OCR badges, missing/incomplete parentheses, ignoring address/item counts, mandatory override of ANY and ALL modes, unknown count safety, full-duration and count-only decisions, and two independent reading confirmation.
- Existing **47 v0.9.5 safety assertions**, **28 OCR/duration assertions**, **45 advanced History filter assertions**, **32 price/rate engine assertions**, and other existing host-side suites passed.
- SQLite v1→v2 migration and 9 History SQL filtering scenarios passed. The v0.9.6 count-only extraction uses the existing SQLite schema; rates for `COUNT_ONLY` remain unverified/unknown.
- All workflow steps continue to require a verified X/card, readable offer identity, the separate Waze checks, and TEST ONLY / auto-decline switch permissions before attempting an X tap.

## Still requires verification

- **Android SDK/Gradle compilation** (`:app:assembleDebug`) on GitHub Actions. Neither Android SDK nor Gradle was available locally, so no APK is included in this package.
- **Nubia Focus 5G real-device OCR and accessibility gestures** for bare single delivery badges, `Delivery (2)`, `Pack & Deliver (3)`, split OCR, Waze overlay, daytime and night mode. Synthetic tests do not guarantee that every actual Uber layout is read accurately.
- No gesture success should be interpreted as a confirmed Uber rejection; diagnostics distinguish dispatched actions from confirmed outcomes.
