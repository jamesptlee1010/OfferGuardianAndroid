# Offer Guardian v0.9.7 — Offer History Sorting

Base: v0.9.6 Minimum Deliveries + Faster Waze source ZIP.

- Add saved History-only sort selection: Newest, Payment highest/lowest, Verified hourly highest/lowest.
- Show the Sort selector beside the existing date and Advanced Filters controls. The existing Highest tab selects payment descending; the dropdown can override it.
- Run sorting in the SQLite query *after* date/search/outcome/history filters and *before* the 35/70/100-record display limit. Stable timestamp and row-ID tie-breaks.
- Hourly sorting uses precise payout/minute ratios; only VERIFIED/CORRECTED readings with positive minute counts have usable rates. Unknown/unverified rates sort LAST in either direction, then newest-first.
- Keep all offer history records, calculated summary metrics, CSV export order, live auto-decline rules, hour-bounds, delivery-count requirement, Faster Waze and TEST ONLY behavior unchanged.
- Added pure Java sorting tests and real SQLite test cases; source project remains GitHub-ready, and an Android APK must be built and device-tested separately.
