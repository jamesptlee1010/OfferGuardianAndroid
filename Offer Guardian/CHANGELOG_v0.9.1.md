# v0.9.1 — Waze latency optimisation

This is a complete source build updating v0.9.0, not a patch.

1. Waze screen polling reduced from ~1,700 ms to ~700 ms while navigation is foreground.
2. Follow-up capture scheduled independently once the first failing offer is recognised, with a safe 430 ms minimum screenshot spacing (Android imposes its own limits).
3. Introduced `WazeFastVerifier` to require two *fresh* captures with matching payout, merchant, X, card border and verified Uber delivery text.
4. Cropped OCR to the validated offer-card region and preserved bounding-box offsets for payment, time, distance and route parsing.
5. Avoided unnecessary SQLite reads/writes on the same offer when the original offer details are complete.
6. Added per-stage timing diagnostics and a rate-limit-safe retry path. No guarantee of sub-second decline or compatibility on every device.
7. Preserved all existing rules, audio alerts, overlay, history and day/night support.

**Safety:** Waze still requires two screen confirmations. Use TEST ONLY to validate on your phone before enabling automatic decline. Automatic interaction with Uber may be subject to Uber's policies.
