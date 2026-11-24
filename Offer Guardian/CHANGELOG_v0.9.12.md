# Offer Guardian v0.9.12 — History weekday/period filters and per-offer deletion

- Added one-tap weekday selection (Monday through Sunday) plus All. One weekday active; a second tap clears it.
- Added independent, multi-select Morning (06:00–11:00), Lunch (11:00–14:00), Afternoon (14:00–17:00), Evening (17:00–21:30) and Night (21:30–06:00) chips.
- Uses local phone time and precise non-overlapping half-open boundaries. Overnight offers use the actual received date and weekday.
- Applies the new filters to History lists, summary statistics, and the filtering percentage denominator; supports existing date/time/advanced filters/sort/delivery-count filters.
- Press and hold a historical offer for a single-record delete action with confirmation. History statistics refresh on successful deletion.
- Remembers weekday and shift selections locally between visits. No changes to automatic-decline logic.
- Includes host-side schedule tests and SQLite boundary/integration tests, plus bundled regression tests for GitHub uploads under 100 files.
