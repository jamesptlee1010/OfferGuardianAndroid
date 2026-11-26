# v2.6.0 validation
- 32/32 host-side workflow test steps passed (Java standalone tests, SQLite/Python checks, source checks).
- New UberVisualFallback test: 17 assertions passed; tests based on reported unexposed window 1648, X (579,708) and $12.55/28 minutes; rejects duplicate frames, stale frames, changed window/coordinates/financial key, insufficient confidence and wrong host.
- TieredPrefs: 38 assertions passed, including default hourly-on $30 and saved legacy preferences.
- Visual detector repeat search matched all four Uber screenshot targets (151–154); no Uber X detected on two DoorDash samples (155–156). Desktop repeat searches 4–12 ms in this sample versus 36–89 ms for full search; not a phone performance measurement.
- Java source syntax parsed: 43 files. Syntax parsing is not Android Java type checking or APK compilation.
- Full Android compilation and on-device interactions: not run; Android SDK/Gradle unavailable. Use bundled GitHub workflow. No claim of a live verified decline or launch.
