# OfferGuardian v0.9.9 — Time-of-Day Offer History & Simplified Price/Hourly Rules

**Complete Android GitHub-ready source project, not a compiled APK.** Built on the v0.9.8 tiered-delivery version. Retains Faster Waze, independent 1/2/3-delivery rules, History filtering/sorting, diagnostics and existing safety gates.

## New in v0.9.9

### Time-of-day filters for Offer History

Open **Offer History → Filters**, enable **Only offers received between these times**, then choose a From and To time. For example, 5:00 pm–8:00 pm shows only offers received between 17:00 and 20:00, inclusive, on each selected date. Use the existing **Today / Last 7 days / Last 30 days / All dates** control to choose the date range separately. Overnight ranges (10:00 pm–2:00 am) work. Historical offers use the phone's local time zone, accounting for summer/winter offsets where relevant.

Time filtering applies to the offer list, search, sort and summary statistics, and combines with the existing Advanced History monetary filters. Time is always **AND** with other filters; the AND/OR switch still controls how the monetary filter groups combine. The selected time window is saved locally in History settings. Clear All resets the window. This affects **History display only**, never live auto-decline behaviour.

### Simpler live Rules Builder

For each of **1 delivery**, **2 deliveries**, and **3 deliveries**, you can set:

- Minimum total offer payment (AUD)
- Minimum estimated hourly rate (AUD/hour)
- ANY (OR) or ALL (AND) within that category

A verified delivery count selects only its matching tier. Unreadable count, disabled tier or 4+ deliveries stays manual review. **Distance and A$/km no longer affect live offer decisions**, and their controls are removed from the tiered editor. Prior saved distance preferences are left in storage but ignored by the live rules engine; if a category previously depended *only* on km/distance, configure a payout/hourly condition before relying on auto-decline.

The price-only fast path needs two agreeing payout/count/merchant/X frames; it does not wait for duration or distance. If both of those readings also contain matching complete estimated duration, the History entry keeps the confirmed time and displays its hourly rate. If duration is incomplete or inconsistent, it remains Unknown. For hourly decisions, a verified time is required, but the kilometre field is optional. Legacy 1-hour minimum-bound safety and Faster Waze confirmation remain intact.

**Rate calculations remain gross estimates from Uber's offer**, not actual completion or net pay. Distance and A$/km may still be displayed in History if captured; they are informational and do not affect live declines. The grey **Unknown** outcome badge means final acceptance/rejection wasn't confirmed and is distinct from **A$/hr Unknown**.

## Build & installation

1. Extract this ZIP on your computer; upload its *contents* to the root of your existing GitHub repository, preserving `.github/workflows/build-apk.yml` and the folder hierarchy. Uploading the ZIP as one file does not build it.
2. Remove obsolete nested copies of old source trees/workflows. Keep the new `app/build.gradle`, `tests` and workflow files together.
3. Open GitHub **Actions → Build Offer Guardian APK → Run workflow**. When the Android compile succeeds, download the `OfferGuardian-v0.9.9-installable-APK` artifact and install its debug APK.
4. **Export existing Offer History to CSV before uninstalling** a previous version; uninstalling deletes local history. Debug APK signing keys can change across GitHub runs, which may prevent an in-place update.
5. Check each delivery-tier setting and save. Keep **TEST ONLY** enabled while checking live OCR and Waze. Enable real auto-decline only after successful on-device testing.

## Tests & limits

Host-side Java/SQLite regression checks are included in the workflow. They cover the $5/11-minute offer, 61-/67-minute offers, unknown hours, 1/2/3 tier routing, History sorting/filtering, time windows, and Waze verification. The Android SDK/Gradle toolchain is not available in this workspace: APK compilation, UI rendering and real-device touch/OCR behaviour have not been certified. Automated Uber interactions may be subject to service terms; this is an independent private tool without network permissions.


## v0.9.10
- Offer History quick filter chips for All / 1 / 2 / 3 deliveries.
- Delivery-count filtering combines with date, search, advanced filters, time windows and sorting.

## v0.9.11 — History delivery counts + match percentage
- Store and filter on a verified numeric delivery count (1, 2, 3), not the generic offer type string.
- Recover only explicitly evidenced old counts; ambiguous records remain `Unknown count` under All.
- When a qualifying filter or search is active, show the percentage of matching offers over all offers in the chosen date/time window.
- The live Uber/Waze rule engine remains unchanged.
