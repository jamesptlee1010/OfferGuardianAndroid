# Offer Guardian v0.7.1 — Java build fix

The v0.7.0 full source incorrectly contained two `onResume()` method definitions in `OfferHistoryActivity.java` (line 236 and line 321). GitHub correctly failed its Java compile. This build deletes only the redundant second method, preserving the existing refresh behaviour.

- Version is now 0.7.1 (versionCode 8).
- Workflow checks for a duplicate `onResume()` before compiling the APK.
- All existing v0.7.0 statistics, rules, sound alerts, Offer History, and visual detection remain unchanged.
- The ZIP contains the source project at the archive root, including the `.github` folder. Do not upload another enclosing folder.

**Build instructions:** Extract the ZIP, upload its *contents* to your GitHub repository root (replacing corresponding files), commit, then open Actions → Build Offer Guardian APK. When the build finishes successfully, download the `OfferGuardian-v0.7.1-installable-APK` artifact. A complete Android APK build has not been verified here.

**Important:** If Android requires uninstalling an older build due to signing differences, export Offer History first; uninstalling clears the locally stored data.
