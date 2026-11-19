# Replace the existing project with v0.7.1

Extract this ZIP. Upload these exact **top-level contents** to GitHub: `.github/`, `app/`, `tests/`, `build.gradle`, `settings.gradle`, `gradle.properties`, and docs. Never create `OfferGuardianAndroid/OfferGuardianAndroid` nested copies.

For a completely clean repository, remove older nested project directories and previous workflows after backing up any files you need. Keep only `.github/workflows/build-apk.yml`. The workflow is version locked to 0.7.1 to reduce the risk of compiling stale app versions.

Android data is stored on your device and cannot be recovered from a ZIP of the source code. Export your history before removing the installed app.
