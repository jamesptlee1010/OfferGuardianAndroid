# Install OfferGuardian v0.9.15 — Uber-only scanner watchdog

1. Extract `OfferGuardian_v0.9.15_Uber_Only_Watchdog_Source.zip` on a computer.
2. Upload the **files and directories inside** the ZIP into the root of your GitHub repository. Preserve `.github`, `app`, `tests_bundle.zip` and Gradle files. Do not upload just the ZIP to GitHub as it is not expanded automatically.
3. Remove obsolete Waze source files and duplicate old-version project trees left behind by earlier uploads. In particular, delete `app/src/main/java/com/personal/offerguardian/WazeFastVerifier.java` if it still exists in the repository.
4. Run **Actions → Build Offer Guardian APK**; download its debug APK artifact.
5. Export your current Offer History before uninstalling an earlier build. Try to update in place; a different debug signing key may require uninstall/reinstall.
6. Initially enable **TEST ONLY**, monitor the app for longer than the earlier 10–15 minute failure, and use **Copy diagnostics for troubleshooting** if detection stops again.

The Monitor Health heartbeat helps distinguish a scanner stall from an Android-stopped Accessibility service. If Android disables Accessibility, it must be enabled by the user. This is source code, not a compiled APK.
