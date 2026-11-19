# Full replacement (not a patch)

**Do not upload the containing `OfferGuardian_v0.4.0` directory or the ZIP itself into the repository.** Extract and upload the files within it to the GitHub repository root.

Root must contain: `.github/workflows/build-apk.yml`, `app/`, `tests/`, `build.gradle`, `settings.gradle`, `gradle.properties`, and this README. Older nested folders may coexist temporarily; the workflow selects v0.4.0 only. Delete old nested copies and old `android.yml` when practical to prevent confusion.

If the new APK cannot overwrite the previous debug-signed app due to different signatures, uninstall the old version, install the new APK, and reconfigure Accessibility/settings. The project is a complete source build, not an APK already compiled.
