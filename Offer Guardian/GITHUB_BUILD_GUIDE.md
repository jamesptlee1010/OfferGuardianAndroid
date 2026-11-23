# OfferGuardian v0.9.8 — Build APK in GitHub

1. Extract `OfferGuardian_v0.9.8_Tiered_Delivery_Rules_Faster_Waze_Source.zip`.
2. Open your existing GitHub repository and upload all extracted **contents to the repository root**, preserving the `.github/workflows/build-apk.yml` location. GitHub does not extract ZIPs uploaded as a single file.
3. Delete obsolete old workflows (`.github/workflows/android.yml` etc.) and remove previous nested project folders if necessary. Ensure `app/build.gradle` says `versionName '0.9.8'` and the root `settings.gradle` is present.
4. Commit; open **Actions → Build Offer Guardian APK → Run workflow** (or use the commit-triggered build).
5. Once green, download the **OfferGuardian-v0.9.8-installable-APK** artifact, extract the ZIP, and install **OfferGuardian-v0.9.8-debug.apk**.

The workflow installs Java, Android SDK 35 and Gradle, runs 22 host-side regression checks, compiles `:app:assembleDebug`, and inspects the APK version. An Android APK has not been compiled locally. An older debug APK may use a different signing key; in-place update may not be possible. **Export History to CSV before uninstalling**, since uninstallation deletes saved data and settings.

Once installed, use Offer Rules Builder to set and save 1-, 2- and 3-delivery tiers, then verify with TEST ONLY, especially over Waze and on any ambiguous green heading.
