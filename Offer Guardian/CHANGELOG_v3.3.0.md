# v3.3.0 — experimental live Shizuku input

Opt-in Touch Diagnostics mode. Existing native and gesture modes and own-screen tests retained. Existing rules, OCR, database, and X detection unchanged. Live mode uses a separate Shizuku user service with shell identity, owning caller, current Uber foreground, bounds and freshness guards. No root mode or arbitrary command API. No fallback to other input methods. On unknown command result no further attempts for the same offer. Live Android compilation and real-device validation pending; platform compatibility and dismissal not guaranteed.
