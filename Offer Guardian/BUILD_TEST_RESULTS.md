# v2.6.1 validation

- 32 existing host-side workflow checks passed, including prior parser/rule, SQLite filter simulations, notification policy, visual fallback, and retry checks. Existing retry timing test adjusted for the intentional 1500 ms cooldown.
- New Stability261Test: 38 assertions passed (user's time OCR variants, near-identical merchant examples, matching financial identity, separated offers, pending DoorDash stage/timer safety and gesture hold durations). Added as the 33rd host workflow check. This tests pure policy, not live UI interactions or Android SQLite merge integration.
- Java 17 syntax parse: 45 source files passed. This does not perform Android symbol resolution or compile the APK.
- VersionName 2.6.1, versionCode 32; workflow selects only matching project and checks APK badging.
- No local Android SDK or Gradle 8.9; APK compilation and runtime device testing not performed. No claim that watchdog stalls or decline effectiveness are fully resolved.
- Existing history is not modified by installing the source. Export before updating/cleanup.
