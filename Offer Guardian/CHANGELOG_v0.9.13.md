# OfferGuardian v0.9.13 — Import Offer History

- Added **Import History** beside Export CSV on the Offer History screen.
- Accepts legacy OfferGuardian 11-column CSVs, intermediate OCR-diagnostic CSVs, and modern 20-column exports with delivery counts.
- Reads quoted CSV cells correctly, including commas, doubled quotes, embedded line breaks and optional UTF-8 BOMs.
- Merges into the existing on-device History database without deleting or changing existing records.
- Duplicate protection compares offer timestamp to one-second precision (CSV export timestamps do not contain milliseconds), payout, pickup and destination; type is an additional identity check for rows missing both addresses.
- Preserves the saved offer date and time (at CSV's one-second precision), payout, duration, distance, action, recorded outcome, and supported OCR metadata.
- Restores verified delivery counts where the export provides them explicitly. Generic legacy `Pack & Deliver`/`Delivery (multiple)` remains unknown instead of guessing.
- Historical rates lacking independent OCR-verification fields are marked `LEGACY_UNVERIFIED`; implausible legacy hourly values are never promoted to verified statistics.
- A malformed file is rolled back as one transaction. Invalid individual records are skipped, with a result summary of imported, duplicate and invalid rows.
- Runs parsing/import on a worker thread and refreshes the Offer History screen after successful import. Newly imported older records can be viewed with `All dates` automatically selected.
- Preserves v0.9.12's weekday and shift filtering, long-press delete, matching-rate statistics, tiered decline rules, Faster Waze, and all other functionality.
