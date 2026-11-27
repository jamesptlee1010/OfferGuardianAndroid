# OfferGuardian v0.9.14 — GitHub build guide

The source now monitors only Uber Driver while the Uber Driver app is foreground. Idle Uber polling remains at approximately 1.7 seconds; bounded fast re-reads remain for active offer verification.

Extract the ZIP and upload its contents to the root of your repository, preserving `.github` and other folders. The GitHub workflow expands `tests_bundle.zip` automatically, runs host-side checks, and builds an installable debug APK.

**Delete obsolete Waze source files and older nested projects from your existing GitHub repository.** The latest source is versionName `0.9.14`, versionCode `22`.

Export History before uninstalling a previously installed APK. After installing, begin with TEST ONLY and test actual Uber Driver foreground behaviour.
