# v0.9.8 — Delivery-specific rules (from v0.9.7)

- Three independent 1-, 2- and 3-delivery rule sets, selected by verified offer heading count.
- Per-category enabled switch, minimum total payment, minimum estimated hourly rate, minimum A$/km, maximum distance and ANY/ALL combination.
- Persist each tier's settings separately. Tier mode remains inactive until Save; retain legacy preferences without running the global minimum-deliveries gate in tier mode; a confirmed restore button can reactivate the old configuration.
- Unknown/unreadable counts, 4+ deliveries and inactive tiers are manual review (no automatic decline). Require matching count on the two independently captured frames.
- For a price-only tier, or a price-failing ALL tier, independently verify payout/count/merchant/X twice and allow decline without waiting on duration OCR; save history with Unknown hourly rate.
- Retain Waze's extra independent verification, latest screenshot X gestures, TEST ONLY and auto-decline switches. Reset cached decisions whenever tier settings are saved.
- Main dashboard and History show the selected tier criteria instead of out-of-date global rules.
- Extend existing rule engine with optional maximum distance. Keep existing v0.9.7 history filters and sorting isolated.
- VersionCode 17, versionName 0.9.8; update the existing GitHub Actions APK build and tests.
- Host-side tests passed; full Android compile/device testing not performed here.
