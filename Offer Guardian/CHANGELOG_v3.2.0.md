# 3.2.0 — Shizuku diagnostics

- Adds the official Shizuku API/provider (13.1.5), explicit permission management, service status, ADB UID and remote input permission report.
- Adds an independent own-screen touch probe, with a manually initiated single tap, focused diagnostic-screen guard, and DOWN/UP/CLICK/CANCEL verification. No arbitrary commands, root path, or Shizuku input to Uber. The diagnostic service is released on leaving its screen. If focus cannot be verified, the test stops; a blocked result is inconclusive.
- The existing live Uber accessibility decline pipeline, OCR, rules, history and alerts are unchanged. This is a diagnostic step, not a live Shizuku decline implementation.
- GitHub workflow now rejects mixed/incomplete app/src/main file trees before compilation. Version code 35, same package and database.
