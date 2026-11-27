# Install OfferGuardian v0.9.14 — Uber Driver only

1. Extract `OfferGuardian_v0.9.14_Uber_Only_Source.zip` on your computer.
2. Upload its contents to the root of your existing GitHub repository, preserving `.github/workflows/build-apk.yml`, `app/`, `tests_bundle.zip`, and the Gradle project files.
3. Delete any obsolete source files left in the existing repository, especially `app/src/main/java/com/personal/offerguardian/WazeFastVerifier.java`; merely uploading updated files does not delete old ones.
4. In **Actions**, run **Build Offer Guardian APK** and download the installable APK artifact.
5. Install over the existing app if signing permits. Export Offer History before uninstalling, because uninstalling clears local History.
6. Start in **TEST ONLY**. This version deliberately does **not** scan or decline offers when Waze or any other application is foreground.

This is the Android **source ZIP**, not a precompiled APK. Compile it in GitHub and verify it on your phone.
