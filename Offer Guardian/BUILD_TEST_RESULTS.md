# OfferGuardian v0.9.8 — source validation (23 November 2026)

Base: `OfferGuardian_v0.9.7_History_Sorting_Minimum_Deliveries_Faster_Waze_Source.zip`, copied directly and modified in place. No Android project recreation.

## Passed locally

- **22 of 22 GitHub workflow host-side checks passed** (`.github/workflows/build-apk.yml` test and structural check steps), run sequentially. Excludes Android SDK/Gradle install, APK compile/badging and artifact upload steps because those Android tools were not installed in the environment.
- **35 new delivery-specific pure-Java assertions passed:** separate solo/double/triple thresholds, boundary values, no cross-tier leakage, no four-plus automatic decisions, disabled/unknown counts, ANY/ALL logic, max distance, A$/km, and mathematically provable price failure with missing duration OCR.
- **20 new preferences persistence assertions passed:** upgrade remains on old rules until Save; values copied from old rules as editable defaults; tier thresholds and combination modes saved independently; disabled tier and legacy fallback preserved; explicit restore retains saved tier drafts.
- **Existing 51 delivery-heading OCR/two-frame assertions passed** (bare Delivery, Delivery (2), Pack & Deliver (3), split OCR, missing/ambiguous header).
- **Existing 47 low-payout/hour-plus decline tests passed**, including `$5 / 11 min`, `$27.10 / 61 min`, `$29.66 / 67 min` and conservative hour lower bounds.
- **Existing 28 duration and OCR extraction assertions passed**; 32 pre-existing rules engine cases; 18 Waze scan timing checks; History sorting and filtering Java/SQLite scenarios; SQLite migration test; remaining host-side guards all pass.
- **OfferRulesActivity and Prefs Java compile checked with local Android API stubs** (syntax and method/type wiring). Stubs are test-only, not bundled with APK application code.
- **Source version**: Gradle versionName `0.9.8`, versionCode `17`; workflow and debug APK artifact names updated.

## Safety choices

- Upgrade does NOT turn tiered rules on until user presses Save all three delivery rule sets. Existing global rules retained and explicitly restorable.
- Selected count must match confirmed green offer heading. Two stable matching images are needed before declining, including price-only fast path. Waze also retains surface verification, with TEST ONLY/auto-decline controls respected.
- Unknown count, disabled tier, 4+ deliveries or ambiguous metrics -> no automatic tap.
- Pure payout failures can be decided without reading duration only if price is the sole condition (OR) or a failed mandatory condition in ALL. A verified count/payout-only decline has Unknown hourly rate in History, not a guessed rate.
- Changing rules invalidates cached readings/decisions to prevent use of old thresholds on a still-visible offer.

## Not verified here

- Full Android SDK/Gradle `:app:assembleDebug` compilation, APK signing and install on an Android phone.
- Device display layout, real Uber/Waze OCR accuracy, screenshot timing, decline gestures, TEST ONLY output, and preservation of installed phone settings/history under differing APK signatures.

**Before any uninstall, export Offer History to CSV.** Test all three delivery categories in TEST ONLY before enabling automatic declines.
