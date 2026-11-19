# Replacing the previous nested Offer Guardian GitHub source

Your previous GitHub repository included `.github/workflows` and one or more nested `OfferGuardianAndroid` source folders. This release is a **whole-project update** and is designed to be uploaded at the repository **root**.

## Existing repository

1. On a Windows PC, extract the full ZIP. Open the extracted folder to see `.github`, `app`, `tests`, `build.gradle`, etc.
2. On GitHub's **Code** page, click **Add file → Upload files** and drag **these files and folders**, not the extracted parent directory itself, into the upload window. Choose **Commit changes**.
3. After uploading, your **top-level repository view** must show `.github`, `app`, `tests`, `build.gradle` and `settings.gradle`. It is okay if an *old* `OfferGuardianAndroid/` folder also still exists temporarily: the workflow prefers the shallowest complete project and confirms its version is v0.2.1.
4. Check `.github/workflows/build-apk.yml` exists at repository root. Remove the old `android.yml` workflow to avoid two different builds.
5. Go to **Actions → Build Offer Guardian APK**. Check that the run logs print `Using project: .../OfferGuardianAndroid` (the repository root path) and `Confirmed Offer Guardian v0.2.1 with visual X recognition.`
6. After success, get the ZIP from **Artifacts** and extract `OfferGuardian-debug.apk`; this is the installable application, not the source ZIP.

## Fresh clean repository (alternative)

If the old duplicated folders are confusing, create a **new private** GitHub repository and upload the extracted ZIP contents there. The workflow file is already included. No separate YAML downloads or patches are needed.

**Important:** Uploading files alone does not delete old nested copies. They can be removed later after a successful build, but the version-check step prevents silently packaging v0.1.0 again.
