# OfferGuardian v0.9.11 — Verified delivery-count History + Match rate

## Fixes
- New SQLite `delivery_count` integer field (v3 schema) stores a confirmed 1/2/3/4+ number from Uber's green delivery heading; 0 means unknown.
- The quick History filter now matches this numeric field instead of generic offer type strings, so `Delivery (2)` and `Pack & Deliver (2)` are both counted as two deliveries, and similarly for triples.
- Migration from v1/v2 recovers historical counts only from explicit retained evidence (e.g., `Delivery (2)` or a `COUNT_ONLY` diagnostic of a confirmed count); ambiguous old records stay unknown and only appear under All.
- Card delivery-count badges and CSV export use the same numeric stored value.

## New History performance tile
- When a price/rate/quick delivery filter or search is active, show a match percentage and `X of Y offers`.
- Numerator: matching offers after the active filters, search, and current History tab.
- Denominator: all recorded offers in the selected date range and, if enabled, the selected time-of-day window, before other filters.
- Unknown/unverified rates do **not** qualify for an hourly threshold but still count toward the total received.
- Empty selected windows display `—` rather than divide by zero.

## Unchanged
- Live solo/double/triple decision rules, OCR confirmation, Faster Waze and auto-decline logic are untouched.
- Existing filters, sort modes, time windows and History records remain intact.

## Tests
- Numeric Java quick-filter regressions.
- SQLite v1->v3 migration, cautious backfill, combination filtering and match-rate denominator scenarios.
- Existing host-side workflow regressions.
