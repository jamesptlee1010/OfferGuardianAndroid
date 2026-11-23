# OfferGuardian v0.9.9 — Faster Price/Hourly Rules + Offer History Time Windows

Based on v0.9.8 (independent solo/double/triple rules; Faster Waze; advanced History filters and sorting).

## History: filter offers by time received

- Added **Time of day** controls to **Offer History → Filters** with on/off switch and native 12-hour From/To time pickers. Example: 5:00 pm to 8:00 pm.
- Uses the phone's local time when each offer was received, for every day of the currently selected date range. Both endpoints are included. Ranges crossing midnight, e.g. 10:00 pm to 2:00 am, are supported.
- Applied consistently to the offer list, high-to-low sorting and all statistics; composed with existing min/max filters, search, accepted-only and date range. Time is an additional AND restriction regardless of the metric filter's AND/OR choice.
- Time-filter state persists in History-only SharedPreferences and never changes live Uber/Waze decisions. **Clear all** resets it.
- No history database migration or destructive changes.

## Live decisions: price/hourly only

- Delivery count still selects one of three separately configured tiers; each tier now supports **minimum payment** and **minimum hourly rate**, plus an independent AND/OR choice.
- Removed distance and A$/km settings from tiered-rule UI and deactivated old distance-related preference flags when building live rule configurations. Historical saved values remain in preferences but do not participate in live decisions. Previously km-only configurations therefore require a valid payment or hourly condition before automatic decisions resume.
- Full-offer OCR verification no longer **requires** kilometres to be readable or matching on both images. Merchant, payout, estimated duration and card/X verification remain required for an hourly decision. The duration extractor also has a time-row fallback when the kilometre label is missing.
- Existing safe hour-bound, insufficient-count and Waze verification paths retained. **No automatic acceptance** added.
- Fast price-only/known-price-failure declines continue without needing duration OCR. If two confirmation frames also agree on a complete, plausible duration, History now retains it as VERIFIED rather than always discarding it; if time is inconsistent, History correctly shows Unknown.
- Distance and A$/km remain optional display data in History and overlays when available, not live rules. Removing the distance dependency may improve responsiveness in some cases; no latency improvement was measured on a real phone.

## Verification

- 22 existing host-side test/check steps passed, plus a new v0.9.9 step: 18 time-window Java assertions, 3 SQLite time/date scenarios, 22 simplified-decision/duration assertions.
- All regression tests for $5, hour-plus offers, tier rules, Faster Waze, History stats/filtering/sorting and v1→v2 history migration passed.
- Android APK compilation and on-device OCR/gestures are **not verified in this environment**. Run the included GitHub Actions workflow and test in TEST ONLY mode before allowing automated declines.
