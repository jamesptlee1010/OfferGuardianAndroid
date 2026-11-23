# OfferGuardian v0.9.9 — GitHub APK Build Guide

1. Extract `OfferGuardian_v0.9.9_Time_Filter_Faster_Price_Hourly_Source.zip`.
2. Upload the **extracted files**, preserving folders, to the **root** of your GitHub repository. The web uploader does not extract ZIP files. `.github/workflows/build-apk.yml` must remain in that exact folder.
3. Remove old nested copies and obsolete workflows so GitHub selects the current source. Check `app/build.gradle` has `versionName '0.9.9'` and `versionCode 18`.
4. Open **Actions → Build Offer Guardian APK → Run workflow** (or allow push-triggered build).
5. Once the action finishes green, download the `OfferGuardian-v0.9.9-installable-APK` artifact and install `OfferGuardian-v0.9.9-debug.apk` inside it.

The workflow runs host-side regression checks, sets up Java 17, Android SDK 35 and Gradle 8.9, compiles the APK, checks its version, and publishes an installable artifact. No APK has been compiled locally.

**Before uninstalling any previous version, export Offer History to CSV.** Debug builds may have different signing certificates and might require uninstalling, which erases local settings/history.

After installation, check your three delivery-specific payout/hourly settings. In History, open **Filters** and use the optional **Time of day** section. Start with TEST ONLY before enabling automatic declines, especially when Waze is foreground.
