# Offer Guardian v0.9.3 — verified duration and payout OCR

Starting point: **original v0.9.1 Faster Waze** source archive. The earlier v0.9.2 draft is NOT used as a source dependency.

## Resolved

1. **Total duration:** The existing parser extracted the first `7 min` and ignored `1 hr`. It now parses hours and minutes together and normalises 1 hr 7 min to 67 minutes. Split adjacent OCR lines are combined using the distance line as an anchor.
2. **Plausibility safeguards:** Extraction with missing units, missing distance/merchant, too-high implied average speeds, or implausibly short large shopping requests is classified `UNVERIFIED`. Values are not invented.
3. **Two-frame payout and details confirmation:** Screen price must have two decimals. Two fresh, matching OCR readings of price, time, distance, merchant and card position are required before any automated rule result is made actionable or history is logged as verified. Changing results require another confirmation.
4. **Fast Waze:** The v0.9.1 fast confirmation timer remains, and first-frame Waze identity confirmation is seeded early, allowing the second full reading to satisfy the pre-existing Waze 2-frame policy when the content remains stable. Not guaranteed on every device / frame.
5. **History audit:** SQLite v1→v2 migration preserves all existing history, marks older one-frame OCR rows `LEGACY_UNVERIFIED`, and adds `raw_payout`, `raw_duration`, `raw_distance`, `parsed_hours`, `parsed_minutes`, `verified_frames`, `extraction_status` and `extraction_note`. CSV export includes these fields. Old unverified rates are not presented as verified, and are excluded from averages.
6. **Corrected subsequent readings:** Two consistent subsequent readings can replace a stored duration/distance and mark the row `CORRECTED`. Conflicting frames withhold gestures until resolved.

## Confirmed regression example

Coles Kedron, 2026-11-22 12:30 (from screenshot and user's CSV): $29.66, 9.8 km, 38 items, 1 hr 7 min. Expected duration: **67 min**. Expected rate: **A$26.56/hr**, not A$254.23/hr. Distance rate remains **A$3.03/km**.

## Behavioural limitations

- OCR can still miss a whole `1 hr` in a plausible-looking `30 min (6 km)` read; if the missing hour is invisible in successive frames, the software cannot reliably reconstruct it. The plausibility rules catch a number of the most obvious errors, but **not all**.
- A first or unverified transient screen is not treated as a confirmed offer in history. The live diagnostics log contains the reason for manual review.
- Conservative checks can also lead to some legitimate orders requiring manual review. High-speed rural trips or large shopping requests with unexpectedly short estimates are examples.
- No APK build or on-device confirmation in this environment. Use the GitHub Actions workflow to compile, then validate with TEST ONLY before enabling auto-decline. If the app update cannot be installed in place due to a signing certificate change, export CSV history before uninstalling the previous build.
