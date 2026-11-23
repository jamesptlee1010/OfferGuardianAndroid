# Install OfferGuardian v0.9.8

Extract the source ZIP. Upload its contents—not the ZIP file—to the root of your existing GitHub repository, preserving `.github/workflows/build-apk.yml` and `app/`/`tests/`. Delete nested stale sources and obsolete workflows before building. At **Actions → Build Offer Guardian APK**, run the workflow. Download the **OfferGuardian-v0.9.8-installable-APK** artifact and install its debug APK on your phone.

**Before removing an existing version, export your Offer History CSV.** Uninstalling deletes locally saved history and rules; GitHub debug signing keys may change between versions.

Open Offer Rules Builder, select **1 Delivery**, enable the minimum payout and set your solo threshold, then configure separate **2 Deliveries** and **3 Deliveries** tabs. Each tab has independent payment/hourly/A$/km/maximum distance criteria and ANY/ALL. Press **Save all three delivery rule sets** to activate. The old mandatory minimum-deliveries setting does not override any of these. Count 4+, disabled tiers or unreadable headings are left for manual review.

Turn on monitoring and Accessibility as usual. Keep **TEST ONLY ON** until Live Diagnostics shows correct payout, delivery count, rate and expected decline for day, night and Waze. Only then consider enabling auto-decline and disabling TEST ONLY; device-level behaviour remains unverified.
