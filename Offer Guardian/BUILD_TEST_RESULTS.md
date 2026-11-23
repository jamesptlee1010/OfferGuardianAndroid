# OfferGuardian v0.9.11 — test results

## Source changes
- Added SQLite v3 `delivery_count` with conservative migration from v1/v2.
- New offers persist their recognized numeric delivery count instead of depending on generic type labels.
- History quick filter uses `delivery_count` for All / 1 / 2 / 3.
- History offer cards display recovered counts and normalize generic labels when a count is known.
- New filtered-match percentage card computes matching items divided by all offers in selected date/time window.
- CSV includes delivery-count field (UNKNOWN for ambiguous historical offers).

## Verified in this environment
- **24 of 24 available host-side GitHub workflow checks passed**, split into two groups to stay within execution limits.
- SQL v1-to-v3 migration verified with ambiguous historical values retained as unknown.
- Numeric delivery filtering and combined hourly-rate/time-window share calculations verified using SQLite.
- Existing standalone OCR, historical filters, Waze, tiered rules, and overlay regressions passed.
- GitHub APK workflow retained, but **Android APK compilation and on-device testing were not possible here** (Android SDK/Gradle unavailable).
