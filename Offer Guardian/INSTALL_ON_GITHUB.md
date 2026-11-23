# Installing OfferGuardian v0.9.9 from GitHub

Extract the source ZIP first. Upload its extracted files to your GitHub repository at the root, keeping the `.github/workflows/build-apk.yml` file and `app/` and `tests/` folders intact. Remove obsolete older source copies if needed. You cannot build directly by uploading only a ZIP into the repository.

Run **Actions → Build Offer Guardian APK** and download the `OfferGuardian-v0.9.9-installable-APK` artifact. Install the contained `OfferGuardian-v0.9.9-debug.apk` on your Android phone. Export old History before uninstalling older debug versions. On the phone, enable Accessibility and monitoring as required; use TEST ONLY to confirm correct behaviour before live auto-decline.

The new time window is in Offer History → Filters and is for **display only**. Live rules use payment and/or hourly rate, independently for solo, double and triple deliveries. Distance no longer affects live decisions.
