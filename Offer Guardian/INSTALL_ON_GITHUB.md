# Install OfferGuardian v0.9.12 via GitHub

1. Download **OfferGuardian_v0.9.12_Weekday_Time_Filters_LongPress_Delete_GitHub_Source.zip** and extract it on your computer.
2. Upload **the extracted contents**, including the hidden `.github/` folder and `tests_bundle.zip`, to the root of your GitHub repository. Do **not** upload the outer ZIP as the source file: GitHub does not automatically extract a source ZIP. The extracted upload has 66 files, so it stays under the GitHub browser's 100-file limit.
3. Open **Actions → Build Offer Guardian APK → Run workflow**. The workflow automatically unpacks `tests_bundle.zip`, runs the existing regression tests and then compiles the Android APK.
4. Download the **OfferGuardian-v0.9.12-installable-APK** artifact from the completed workflow, extract it and install the APK. Export existing Offer History first if you might uninstall the previous app; installing over the same package/signing key should retain the on-device database.
5. Test new History features on your Android phone: tap a weekday, combine several time periods, then press-and-hold a History offer and cancel its deletion to confirm the prompt.

## New History controls
- Weekdays: All, Monday through Sunday (single-select; tap selected day again to show all weekdays).
- Time periods: All, Morning 6–11 am, Lunch 11 am–2 pm, Afternoon 2–5 pm, Evening 5–9:30 pm, Night 9:30 pm–6 am (multi-select).
- Each offer is assigned to the calendar weekday and local time when it was received, even for overnight hours. A Monday search on Tuesday requires selecting Last 7 Days, Last 30 Days or All Dates instead of Today.
- Press and hold any offer in History to delete that **one offer** after confirmation. This cannot be undone.

These controls affect only History. Live rules, automatic declines and Waze behaviour are unchanged.
