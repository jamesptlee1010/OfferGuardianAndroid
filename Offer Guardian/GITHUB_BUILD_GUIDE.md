# GitHub build, v0.3.0

1. Extract `OfferGuardian_FULL_GitHub_v0.3.0.zip`.
2. Visit the root of your GitHub repository (`https://github.com/jamesptlee1010/OfferGuardianAndroid`).
3. Upload the folder **contents** (not the folder itself), preserving all paths, especially `.github/workflows/build-apk.yml`.
4. GitHub should run the `Build Offer Guardian APK` workflow after commit. You can also launch it from the Actions tab with `Run workflow`.
5. Successful artifacts: `OfferGuardian-v0.3.0-installable-APK` → inside it `OfferGuardian-v0.3.0-debug.apk`.
6. On phone, install the APK and check Android App info version **0.3.0**. Re-enable Accessibility if Android treats it as a new install.

The workflow deliberately searches for a complete v0.3.0 source tree, so it won't silently select the older 0.1.0 project when duplicate folders are still present. There should ideally be only one copy.

**If Actions fails:** Select the failed step in the new `Build Offer Guardian APK` workflow (not old `android.yml`) and share its actual error lines. Uploading only `.github/workflows/build-apk.yml` without the full Java source will not build v0.3.0.
