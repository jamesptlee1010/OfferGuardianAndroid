# Replace an earlier OfferGuardian source version with v0.9.9

Extract the v0.9.9 ZIP and upload the contents to the root of your existing GitHub repository, preserving paths. Replace existing files with matching paths and remove old nested complete projects and extra workflows so there is only one active build tree. GitHub won't extract a ZIP uploaded as a single file. The repo should contain `settings.gradle`, `app/build.gradle`, `app/src`, `tests`, and `.github/workflows/build-apk.yml` at the correct levels.

The included workflow searches for a complete v0.9.9 project, executes host-side tests, compiles an Android debug APK and uploads it as an artifact. Export History before uninstalling a previous APK; in-place installation may be blocked by different debug-signing keys. Use TEST ONLY before enabling live auto-decline.
