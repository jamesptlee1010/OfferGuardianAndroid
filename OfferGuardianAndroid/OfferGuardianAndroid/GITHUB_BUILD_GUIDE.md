# Build Offer Guardian entirely on GitHub (no Android Studio)

This project now includes `.github/workflows/build-apk.yml`. This workflow uses GitHub's hosted computers to compile the Android source into an **installable debug APK**.

## First-time setup

1. Sign in at https://github.com and choose **New repository**. Name it, for example, `OfferGuardianAndroid` and choose **Private** if preferred.
2. Download and **extract** the `OfferGuardian_GitHub_Ready_v0.1.0.zip` package locally. Open the inner `OfferGuardianAndroid` project folder.
3. In the new GitHub repository, select **Add file → Upload files**. Upload the contents of the extracted `OfferGuardianAndroid` folder, keeping the `.github/workflows/build-apk.yml` path intact. Do **not** upload just the ZIP file: GitHub does not turn a ZIP uploaded into a repo into project source files.
4. Choose **Commit changes**. GitHub Actions should run automatically on the push. Otherwise choose the repo **Actions** tab → **Build Offer Guardian APK** → **Run workflow**.
5. Open the workflow run. A **green check** indicates the build succeeded. Expand the run and select **OfferGuardian-installable-APK** in **Artifacts**.
6. GitHub downloads an artifact ZIP. **Extract that ZIP on your Android phone** to find `app-debug.apk`; tap that APK to install. Android may ask you to allow installation from your Files app/browser.
7. When installed, open Offer Guardian, turn on monitoring and separately give it accessibility permission. Start with **Auto-decline OFF** to test real offers safely.

If the workflow does not show up, make sure `.github/workflows/build-apk.yml` is in the root of your repository (not within an extra `OfferGuardianAndroid` directory) and is committed on its default branch.

## Build workflow details

- Android Gradle plugin: `8.7.3` (project's version)
- Gradle `8.9`, Java `17`, Android SDK platform `35`
- Produces `app/build/outputs/apk/debug/app-debug.apk`
- Creates an artifact called `OfferGuardian-installable-APK` after a successful build
- Does not need private signing secrets because Android debug builds are automatically signed with a debug key; suitable for private sideloaded testing but **not** Play Store distribution

## Limitations and safety

The build workflow has been prepared, but it has **not** been executed on GitHub or validated by installing the app. APK compilation success does not establish that Uber Eats offers will be recognised or that decline automation works in Australia. The offer-reading accessibility logic and decline calibration need real-device checks; Uber may change its UI or restrict automation. Read the main `README.md` first, and leave automatic decline off until verified. Never assume the app can suppress Uber Driver's own notification tone. Only configure it while parked.

This app is **not affiliated with Uber**. Accessibility control of another app can create account and policy risks. The project requires no Uber login and requests no Internet permission.
