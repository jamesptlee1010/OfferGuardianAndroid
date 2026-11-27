# GitHub build guide — Offer Guardian 2.6.1
1. Extract `OfferGuardian_v2.6.1_Stability_Source.zip`.
2. Replace the repository root with the extracted contents. Include hidden `.github/workflows/build-apk.yml`. The small `tests_bundle.zip` is automatically expanded by Actions; do not expand it before browser upload.
3. Remove redundant old source copies and workflows. Do not delete any signing configuration used for your installed app.
4. In GitHub select Actions → Build Offer Guardian APK → Run workflow. The workflow runs host regression checks and compiles the debug APK with the Android SDK.
5. Download the `OfferGuardian-v2.6.1-installable-APK` artifact, extract the APK, and install. Export History first; different debug signing keys may prevent an in-place upgrade.
6. Follow README test-only and live test instructions before enabling automatic declines. Android compilation and device testing are not yet verified locally.
