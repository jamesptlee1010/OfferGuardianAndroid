# OfferGuardian v0.9.15 — Uber-only Scanner Reliability

Built on v0.9.14 after a new Android phone successfully detected offers for roughly 10–15 minutes, then stopped until the Accessibility service was restarted.

## Monitoring reliability fixes
- Replace the indefinitely held `capturing` flag with `OfferScanWatchdog` tracking one in-flight screenshot/OCR pipeline by a unique token.
- Detect a screenshot or OCR request that has not completed within **8 seconds**; invalidate it, clear incomplete offer-verification evidence, and permit new scans after a brief bounded backoff.
- Guard **all** late screenshot/OCR success and failure callbacks with the capture token and timeout; discard stale OCR so it cannot initiate an old decline gesture or clear a newer capture's state.
- Release screenshot buffers when an expired screenshot callback arrives and recycle images when their OCR tasks eventually complete.
- Back off for repeated screenshot capture failures rather than aggressively retrying when Android refuses screenshots.
- Keep the original **Uber-only** 1,700 ms normal poll / 430 ms bounded offer-specific re-read, verified two-frame decision checks, rule engine, and gesture conditions unchanged.

## Diagnostics
- Add a heartbeat every ~45 seconds to the existing monitoring loop; it does **not** introduce additional screenshot scans or polling threads.
- Report Uber-not-foreground and unavailable Accessibility-root states instead of silently failing to scan.
- Record watchdog recovery counts, approximate successful screenshot count, and last heartbeat in shared preferences.
- Show Monitor Health in the main app and add health data to the Copy diagnostics report. A heartbeat older than 100 seconds is marked **stale**; this helps distinguish a stalled scanner from Android stopping or suspending the Accessibility service.
- Report Accessibility interruption, unbind and service shutdown in diagnostics. The app does **not** silently re-enable a service Android has disabled; that requires user action.

## Preserved
- v0.9.14 Uber-only mode (no Waze monitoring), existing rules, History import/export, day/time/delivery filters, history match-rate calculation, long-press delete, OCR and safety safeguards, and qualifying-offer overlay.

## Testing and limits
- Added host-side watchdog tests for timeout boundary, unique tokens, successful recovery, late callbacks and service invalidation.
- 26 host-side workflow checks passed locally, including the new watchdog test (18 assertions). No Android SDK or emulator is available in this environment, so Android APK compilation and an extended on-phone test are still required.
- **A stopped or disabled Android Accessibility service cannot be force-reenabled by this code.** If monitoring stops and the heartbeat is stale, inspect Android service/battery restrictions and share diagnostics.
