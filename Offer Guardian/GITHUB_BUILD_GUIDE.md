# Build Offer Guardian v0.4.0 without Android Studio

1. Upload the **contents** of this ZIP to the root of `https://github.com/jamesptlee1010/OfferGuardianAndroid`, preserving all folders. Your GitHub main directory should show `app/`, `.github/`, `tests/`, and `build.gradle` (not an additional `OfferGuardianAndroid/`).
2. Commit and open **Actions** → **Build Offer Guardian APK**. Ignore/delete any older `android.yml` build workflow.
3. Wait for a green tick. Open run → **Artifacts** → **OfferGuardian-v0.4.0-installable-APK**, download and extract.
4. Install the contained `.apk` on your Android. Check App info version **0.4.0**.
5. Enable Accessibility, set minimum A$30, then test the independent alert using Offer Guardian's **TEST high-value ring** button. Set the phone's ALARM volume up. To silence Uber notifications, change Uber Driver's notification categories separately.

This is source code; compiling the APK requires GitHub Actions. The APK build itself has not been run in this environment.
