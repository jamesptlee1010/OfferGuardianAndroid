# Replace existing GitHub project — v0.9.8

Extract the source ZIP. Upload the **top-level contents**: `.github/`, `app/`, `tests/`, `build.gradle`, `settings.gradle`, `gradle.properties`, and documentation files. Do not create a nested `OfferGuardianAndroid/OfferGuardianAndroid` folder. The GitHub workflow locates a complete v0.9.8 project and rejects old version names; remove old nested versions and obsolete workflows to avoid mistakes.

GitHub's browser upload limit is 100 files at once, and this package contains fewer than 100 files. Include hidden `.github` when uploading. If an old app was installed with a different debug key, export your full History before any required uninstall.
