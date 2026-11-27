# v3.2.0 verification

- Existing host-side GitHub workflow regression shell steps executed locally with Java 21, including Java rules/OCR and SQLite history tests. See count below.
- 18 new own-screen probe assertions pass (focus, shell identity, caller, coordinates, and actual touch event verification).
- All 41 Java files parsed with javac compiler syntax parser; this is not Android compilation or API type checking.
- Manifest XML and workflow YAML parsed, source inventory matched, simulated stale file detection verified.
- Rules, preferences, offer history store, detector and live accessibility service preserved from v3.1.0 (service version banner only).
- Android SDK/Gradle and device not available here. Maven dependency resolution, AIDL compilation, merged manifest, APK signing, Shizuku service binding, real focus format, and actual touch delivery still require CI/device testing. Shizuku API used from official documented 13.1.5. No live Shizuku injection into Uber is implemented.

Host workflow steps passed: 31 (includes inventory and test bundle expansion).
