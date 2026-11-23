# Offer Guardian v0.9.10

## New
- Added a quick Offer History delivery-count filter with tappable choices: All, 1, 2, or 3 deliveries.
- Wired the quick filter into history statistics, sorting, search, date ranges, advanced filters and time-of-day filtering.
- Added compact delivery-count badges on Offer History cards when the stored heading clearly identifies 1, 2 or 3 deliveries.

## Behaviour
- This filter is display-only and never changes live alerting, acceptance or decline rules.
- Bare `Delivery` / `Pack & Deliver` headings are treated as 1 delivery. `Delivery (2)` and `Delivery (3)` are treated as 2 and 3 deliveries respectively. Ambiguous headings such as `Delivery (multiple)` remain visible only under All.
