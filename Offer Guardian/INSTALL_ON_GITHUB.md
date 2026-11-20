# Install Offer Guardian v0.9.0 through GitHub Actions

1. Extract **OfferGuardian_FULL_GitHub_v0.9.0_Auto_Day_Night_Waze.zip** on your computer.
2. Open your existing `OfferGuardianAndroid` GitHub repository, **Code** tab.
3. Upload the *contents* of the extracted project (not another enclosing folder) into the root of your repository, replacing files with the same names. Preserve `.github/workflows/build-apk.yml`.
4. Delete any obsolete `.github/workflows/android.yml` or other older workflow if it still exists, so only one APK build runs.
5. Commit changes; open **Actions** > **Build Offer Guardian APK**. The workflow selects a complete source tree with `versionName '0.9.0'` even if older nested versions remain.
6. After a green build, download `OfferGuardian-v0.9.0-installable-APK` from **Artifacts**, unzip it and install `OfferGuardian-v0.9.0-debug.apk` on your Android phone.
7. In Android Settings, check Accessibility is enabled for **Offer Guardian – Uber/Waze offer monitor**. Enable Monitoring and visual X recognition in Offer Guardian.
8. Start in **TEST ONLY** mode. Try one normal daytime Uber offer, one nighttime offer, and one offer while navigating with Waze. In **Live Diagnostics**, confirm it recognises the theme, price and X, but does not tap. Only enable automatic decline after testing.

**Important:** Export Offer History before uninstalling a prior version; uninstalling erases locally stored history. Upgrading without uninstalling may fail if the previous debug APK was signed with a different key.

**Security:** This app needs accessibility screenshot access. No internet permission and no screenshots are uploaded. It is not an official Uber or Waze app. Automatic decline may have platform/account consequences.
