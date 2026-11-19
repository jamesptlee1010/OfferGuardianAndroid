# Offer Guardian v0.5.0 — Concept B Offer History

A complete Android Studio / GitHub Actions project updated from **Offer Guardian v0.4.0**. This is **source code**, not a compiled APK. The live X detector, screen monitoring, optional automatic-decline logic and independent high-offer alerts from v0.4.0 are retained.

## New in v0.5.0

Open **Offer History · Concept B** from the top of the main app.

- **All offers:** chronological, scrollable records of Uber Driver requests detected while the Accessibility service monitors the Uber screen.
- **Accepted:** only requests confirmed as accepted in Offer Guardian.
- **Highest:** offers ranked by their advertised estimated payout.
- **Per-offer details:** date and local time, advertised AUD payout, Delivery/Pack & Deliver, *pickup displayed on offer*, *approximate destination displayed on offer*, estimated trip duration, estimated kilometres and the calculated rate **A$/km = estimated payout ÷ estimated kilometres**.
- **Today / all dates**, pickup/destination search, **CSV export**, clear with confirmation, and 35-row incremental display.
- **Outcome selector:** tap any offer to mark **Accepted**, **Declined**, **Expired**, or **Unknown**.
- **Automatic recording:** new recognised offer cards are stored in a private local SQLite database even in TEST ONLY mode. Consecutive OCR frames of the same visible card are deduplicated. No network permissions or background servers are used.

## Important accuracy limitations

- Uber Driver does **not reliably provide a manual acceptance/decline event** to Accessibility. The app must not guess an outcome from the card simply disappearing. A verified payout and card result is stored with **Unknown** outcome until a user updates it. An automated X gesture is tracked internally as **DECLINE_REQUESTED**, not claimed as a confirmed decline. The accepted-only view shows records that have actually been marked Accepted.
- The pickup and dropoff labels are captured from **the offer card's visible OCR text**. In some layouts text may be incomplete, truncated, or simply not readable. Missing details display **Not readable on offer** rather than fabricated addresses.
- **A$/km** is based on Uber's *estimated* payout and *advertised* distance, not the final payout, kilometres actually driven, vehicle costs or net earnings. N/A appears when the distance is missing or zero.
- The original screenshot-based detection is calibrated for the phone and Uber layouts previously supplied. If the app cannot verify the grey X, border and payout, **no automated action occurs and no offer is logged**. Monitoring must be enabled, Accessibility connected, and Uber Driver in the foreground.
- Same-payout, same-position offers arriving very close together without a card-free interval might be grouped; the recorder is conservative about duplicates. Review the history for completeness.
- **Historical offers from before installing v0.5.0 cannot be recovered** by the app. It starts logging new detected offers from installation onward.
- **Data persists across normal app closes** and same-signature APK upgrades, but **uninstalling the app erases local history**. Export a CSV before uninstalling. GitHub-generated debug APKs may have changing signing keys, so future debug builds might require an uninstall.

## First use

1. Install the v0.5.0 debug APK produced by GitHub Actions and confirm **Settings → Apps → Offer Guardian → App info → version 0.5.0**.
2. Open Offer Guardian. Check Android Accessibility is enabled and **Monitor Uber Driver offers** is on. Preserve your existing high-value alerts and $30 minimum.
3. **Keep TEST ONLY on** until you've checked recognition on actual offers. Saving history does not require active auto-decline.
4. When safely parked, allow a new offer to appear in Uber Driver. Return to Offer Guardian → **Open Offer History** to see it.
5. Tap a record to verify pickup, destination, estimated time, distance and A$/km. If you know you accepted or declined, tap **Set outcome** and select the result.
6. Use **Today / All dates**, **Accepted / Highest**, Search, and **Export CSV** as needed.

## Build

See `GITHUB_BUILD_GUIDE.md`. The `.github/workflows/build-apk.yml` workflow installs Android SDK 35, Gradle 8.9 and Java 17 on GitHub Actions (no `gradlew` required), runs pure-Java tests, builds an APK, and verifies it reports `versionName='0.5.0'`.

## Local history/privacy

Uses `SQLiteOpenHelper` on the phone, no account login, and no INTERNET permission. Location labels are captured only from visible offer cards. Exported CSVs contain locations and must be handled carefully. No guarantee that Uber permits automation, and repetitive auto-declines may affect the user's account. The application is independent of Uber.
