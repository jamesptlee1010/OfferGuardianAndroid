# v2.5.1 — Compilation hotfix

- Fix undefined local `prefs` in `OfferAccessibilityService.quickAction`: obtain the same existing preferences via `Prefs.of(this)` before clearing the pending DoorDash notification.
- Update versionName to 2.5.1, versionCode to 30, UI and GitHub artifact labels.
- Retain all v2.5.0 functionality, settings, application ID and history schema.
- No Android APK compilation or live device validation performed locally.
