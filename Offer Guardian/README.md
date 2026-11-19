# Offer Guardian v0.2.1 — complete Android project

**This is the full replacement source project, not a patch and not an APK.** Every file needed for the Android Gradle project and GitHub Actions workflow is present. The ZIP has **no extra enclosing folder**; `app/`, `tests/`, `build.gradle`, `settings.gradle`, and `.github/workflows/build-apk.yml` are at the ZIP root.

## Important changes from v0.1.0

- Visual X recognition is added for the two provided Australian Uber Eats offer layouts. The offer card moves vertically; the app locates the X relative to the card border.
- Visual detection can run in **TEST ONLY — locate X without tapping**, the default. Do this first on real offers, while parked.
- Enabling visual X recognition clears the legacy stuck “Learning armed” state.
- For offers below the configured threshold (default AUD 30.00), the app attempts a decline **only if** monitoring, visual recognition, automatic decline, and actual tapping are all enabled and the screen passes validation.
- At or above the threshold, the driver remains in control and can receive a sound/vibration alert. The app never auto-accepts.
- The GitHub build checks that the selected Android source and final APK are **v0.2.1**, avoiding accidental v0.1.0 builds from duplicated folders.

## Build using GitHub Actions

1. Extract this ZIP on Windows or another computer.
2. Open the **contents** of the extracted folder. Upload all of them, including `.github/`, into the **root** of your GitHub repository. Do not upload a new `OfferGuardianAndroid/` folder, or the ZIP itself.
3. Commit changes, then go to **Actions → Build Offer Guardian APK**. If it doesn't start automatically, choose **Run workflow**.
4. Open a successful run (green check). Under **Artifacts**, download `OfferGuardian-installable-APK`. Extract it to obtain `OfferGuardian-debug.apk`.
5. Install the APK on your Android phone. If Android reports that it cannot update the existing app, the new APK may have a different GitHub debug signing key; uninstall the previous Offer Guardian first (this resets its app settings and Accessibility permission), then install the new APK.
6. In **Settings → Apps → Offer Guardian → App info**, confirm **version 0.2.1**. If it still shows 0.1.0, you are still installing an old build.

See `GITHUB_BUILD_GUIDE.md` for precise steps and `REPLACE_EXISTING_GITHUB_PROJECT.md` for help with an existing nested-folder repository.

## Initial on-phone testing

1. Set your minimum payout to AUD 30.00 and enable monitoring / Android Accessibility permission.
2. Switch on **Recognise X visually from Uber screenshots**.
3. **Leave TEST ONLY enabled** and leave automatic decline off until you've verified multiple real offers. With test-only active, the app locates X but deliberately sends no tap.
4. Check **Last recognised offer**. A line like `Visual TEST passed: X at …; NO TAP` means visual recognition succeeded; `could not verify X` means no tap was attempted. The *gesture sent* state does not prove Uber processed a decline.
5. Automatic tapping is experimental and could affect Uber account standing. Do not configure or use the controls while driving. The app may fail or Uber may change its layout without warning.

## Tested here

- `OfferDetectorStandaloneTest`: 14 cases passed.
- `VisualDeclineDetectorSyntheticTest`: passed.
- `VisualDeclineDetectorStandaloneTest`: matched two supplied Uber offer screenshots at `(579,737)` and `(579,651)` and correctly rejected a non-Uber settings screenshot.
- **Not yet verified:** a complete GitHub Android APK build, on-phone OCR integration, and real-world successful automatic declines.

## Data / permissions

The app is a prototype using a local Android Accessibility service. Screenshot text recognition uses bundled on-device ML Kit. The app requests no Internet permission and is not affiliated with Uber. It never needs your Uber login. Android may require allowing restricted Accessibility settings when installing an APK outside Google Play.
