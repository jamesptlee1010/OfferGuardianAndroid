# Offer Guardian v0.9.5 — Low-Value + Long-Duration Decline Recovery

Based on **v0.9.4 Advanced History Filters + Faster Waze**, not the older original v0.9.1.

## Fixed

1. **Uber whole-dollar payout OCR:** `$5` is valid on Uber; previous `OfferScreenEvaluator` erroneously demanded two decimal places. Permit a standalone `$3`–`$9` inside the verified offer payout area, while continuing to reject ambiguous truncated multi-digit payout text such as `$29`. Two consistent images are still mandatory.
2. **Long-duration partial OCR:** complete `1 hr 1 min` and `1 hr 7 min` readings continue to give exactly 61 and 67 min. When minute OCR fails but an hour-plus lower bound is clearly recognised, the new `OfferSafeDecline` checks whether the most favourable possible hourly rate still fails the active criteria. Never decline on an uncertain bound or when any selected OR condition can still pass.
3. **Bound confirmation:** `OfferBoundVerifier` confirms matching payout, hour lower bound, distance, merchant, and X location on two distinct screenshots. `OfferAccessibilityService` reuses the existing checked gesture dispatcher and Waze's independent card/identity verification.
4. **Targeted retry:** `OfferRetryBudget` schedules no more than five quick OCR verification retries per continuous failure, with standard polling afterwards; Android screenshot rate-limits still back off.
5. **Truthful history:** hour-bound declines are stored as `HOUR_BOUND` with unknown exact minutes and unknown hourly rate. The new database path retains the existing v2 schema; no reinstallation or history wipe is required. Two verified complete observations can later correct the record.
6. **Live diagnostics:** states whether the app is still verifying, using a proven hour bound, retrying OCR, test-only, auto-decline disabled, or failing X/Waze verification.

## Preserved

- v0.9.4 Advanced History Filters, database and full CSV export.
- v0.9.3 two-frame OCR validation and truthful history.
- v0.9.1 Faster Waze and light/dark detection, original rule builder, alerts and TEST ONLY switch.

## Verification

- 17 standalone Java suites (excluding an older optional detector fixture test whose external screenshots were not bundled).
- 47 new targeted regression assertions; 28 earlier duration regressions and 45 existing history-filter checks retained.
- SQLite migration and actual History Filter SQL integration tests passed.
- Full Android APK compilation/device recognition still require GitHub Actions and Android-device validation.
