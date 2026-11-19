# GitHub APK build: Offer Guardian v0.5.0

## Where the files belong

Upload the **contents of this ZIP**, not a folder containing the ZIP, to the repository root. The root should look like:

```
.github/workflows/build-apk.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/personal/offerguardian/...
build.gradle
settings.gradle
tests/OfferHistoryStandaloneTest.java
README.md
```

If an older nested `OfferGuardianAndroid/OfferGuardianAndroid/` exists, do **not** upload another nested copy. The workflow searches for **version 0.5.0** and selects the shallowest complete version-0.5.0 tree, skipping older versions.

## Compile

1. Open `https://github.com/jamesptlee1010/OfferGuardianAndroid`.
2. On **Code**, choose **Add file → Upload files** and upload the extracted *contents* to the root. The hidden `.github` folder must remain at the root.
3. If an older `.github/workflows/android.yml` still exists, remove it to avoid a second failing build.
4. Commit. Then open **Actions → Build Offer Guardian APK**, select the newest successful run.
5. Under **Artifacts**, download **OfferGuardian-v0.5.0-installable-APK**.
6. Extract the downloaded artifact ZIP and transfer **OfferGuardian-v0.5.0-debug.apk** to the phone. Install and check that Android App info says **0.5.0**.

There is no `gradlew` requirement. The build uses installed Gradle 8.9 and SDK 35 on the GitHub runner.

**Important:** If Android says "App not installed", it may be because the old debug APK was signed with a different ephemeral GitHub debug key. Uninstalling the old app should be a **last resort**: it will delete its settings and stored offer history. Export Offer History first if you already have data; re-enable Accessibility after reinstalling. A persistent signing key is recommended for subsequent updates.

## Reliability

Source-only tests can check parsing, A$/km and output structure, but they do **not** replace a successful Android compile and live phone tests. The app keeps TEST ONLY enabled for new installations. Outcomes not confirmed by the user are marked Unknown; **recording a sent X gesture is not proof of a declined request**.
