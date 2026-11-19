# Replace the previous GitHub sources

Earlier uploads produced duplicate paths like `OfferGuardianAndroid/OfferGuardianAndroid/app/`. Prefer one complete source tree at your repository root:

- `.github/workflows/build-apk.yml`
- `app/`
- `tests/`
- `build.gradle`
- `settings.gradle`
- `gradle.properties`

If you cannot remove the old nested folders immediately, the build workflow will **only select a complete 0.3.0 project**, selecting the shallowest matching copy. Old `android.yml` workflows may still fail on missing `gradlew`; remove any obsolete workflow after the new build is succeeding.

Do not commit user-provided Uber offer screenshots: the screenshots can expose current locations, routes, addresses and business activity.
