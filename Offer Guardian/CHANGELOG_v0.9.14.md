# OfferGuardian v0.9.14 — Uber-only monitoring

## Removed
- `com.waze` from Android package visibility and accessibility service event subscriptions.
- Navigation-specific 700 ms polling and Waze-only capture thresholds.
- WazeFastVerifier, its dedicated test and all Waze-only decline confirmation branches.
- Waze monitoring references in current Settings and accessibility labels.

## Preserved
- Uber Driver-only 1.7-second monitoring, bounded OCR verification retries, offer rules and guarded decline gestures.
- History import/export, sorting/filtering, weekday and shift filters, rate statistics, matching percentages and individual deletion.

When Waze or another app is foreground, no screenshots or auto-declines are performed. Potential reduction of CPU use has not been measured. Android compilation and phone verification are outstanding.
