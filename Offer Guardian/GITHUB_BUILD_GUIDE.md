# Full project — GitHub APK build (v0.2.1)

1. Download `OfferGuardian_FULL_GitHub_v0.2.1.zip` and extract it.
2. Do **not** upload the ZIP file. From the extracted folder, drag **its contents** (`.github`, `app`, `tests`, `build.gradle`, `settings.gradle`, etc.) into **Add file → Upload files** on your GitHub repository's **Code** tab. This project has **no outer folder** in the ZIP.
3. Commit your changes to branch `main`.
4. GitHub's root workflow must be at `.github/workflows/build-apk.yml`, **not** inside `OfferGuardianAndroid/`.
5. Delete the old `.github/workflows/android.yml` workflow if it exists and is still producing `chmod +x gradlew` errors; this project does not contain `gradlew`.
6. Open **Actions → Build Offer Guardian APK**. The build can also be started with **Run workflow**.
7. The workflow uses Java 17, Android SDK 35, Gradle 8.9 and the Android Gradle Plugin 8.7.3. It checks the v0.2.1 source **and** the APK's version, tests payout parsing and visual X detection, then publishes the installable `OfferGuardian-debug.apk` inside an artifact called `OfferGuardian-installable-APK`.
8. On your phone, download the artifact ZIP, extract it, and tap the APK. If upgrading an existing GitHub debug build gives an install-signature conflict, uninstall the old version first. Re-enable permissions and re-enter preferences as needed.
9. Verify `Settings → Apps → Offer Guardian → App info` says version **0.2.1**.

This project's Android APK has **not** been compiled and tested within ChatGPT's environment. A green GitHub Actions build is required before using the produced APK. A successful compilation does not establish that real Uber Driver declines will work. Use TEST ONLY initially.
