# Replace existing OfferGuardian source with v0.9.15

Extract the v0.9.15 ZIP and upload its **contents** to the root of your existing GitHub repository, preserving paths and hidden `.github` files. Replace matching existing files. GitHub does **not** extract a source ZIP uploaded as one file and does **not** delete files no longer present in the new release.

Delete obsolete `WazeFastVerifier.java` if present and remove old nested full copies of the app and duplicate workflows. Keep one project containing `settings.gradle`, `app/build.gradle`, `app/src`, `tests_bundle.zip` and `.github/workflows/build-apk.yml`.

The included workflow extracts the bundled tests, performs host-side checks and builds the Android debug APK. **Export History before uninstalling an existing APK**; debug signing keys may differ. Start with **TEST ONLY** and verify the heartbeat stays current during a prolonged monitoring session.
