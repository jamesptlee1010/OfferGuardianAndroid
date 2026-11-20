# Offer Guardian v0.8.0 — qualifying overlay & cautious accepted-offer tracking

**Full GitHub-ready Android source**. Builds a debug APK via `.github/workflows/build-apk.yml`.

## What's new in v0.8.0

- Tiny **top-right black floating badge** shows **estimated A$/hr** (green) and **A$/km** (white). No lightning bolt, separator, or other icons. It is non-touchable.
- **Only displays when the existing three-rule engine returns SHOW** for the current verified Uber offer. Supports price, hourly, per-kilometre, ANY (OR), and ALL (AND) combinations.
- Hidden for declined/failed rules, unknown data requiring manual review, missing/expired offer cards, disabled monitoring, and when Uber Driver leaves the foreground. If a qualifying offer has no time/distance (e.g., qualifies by payout alone), the relevant rate is displayed as a dash rather than invented.
- Optional **automatic Accepted tracking**. Offer Guardian waits for the incoming offer card to disappear, then looks for the SAME pickup merchant on an active pickup/navigation confirmation screen in TWO separate screenshots. It marks Accepted only with this evidence. A tap on Accept or Match alone is NOT proof of assignment. If no strong evidence is observed, the entry remains Unknown, and manual correction remains available.
- Two separate on/off switches in the Offer Guardian settings. Both are initially enabled for this upgrade, and existing threshold and history data are preserved.
- The overlay uses the already enabled accessibility service’s `TYPE_ACCESSIBILITY_OVERLAY`, so no second floating-window permission is necessary. It is non-touchable and cannot press Uber buttons.

## Existing functionality retained

- Visual verification of Uber Driver offer cards, payouts and the grey-square X; independent sound alerts; adjustable ANY/ALL criteria; optional automatic declines; TEST ONLY safeguard; local history, accepted/highest tabs, pickup and destination, hourly and kilometre rates, averages, CSV export, and diagnostic history.

## Important limitations

- The rates are **estimates**, calculated from the offered payout / Uber’s listed minutes and kilometres; they are not actual earnings.
- The app cannot guarantee it can detect all Uber pickup-confirmation screens. Matching and different layouts are particularly uncertain. Where evidence is insufficient, entries stay **Unknown**, never assumed Accepted.
- No real Uber button is automatically accepted. Automatic declining is unchanged and remains opt-in (and test-only should be verified first). Automation may affect your Uber account.
- The program was tested using **host Java tests**. It was not compiled into an APK or tested against live Uber screens in this environment. GitHub Actions must build the APK, and live behaviour needs testing when safely stationary.
- If a GitHub debug APK uses a different signing key than your installed version, Android may demand a reinstall; **export your Offer History CSV before uninstalling**, because uninstalling removes local app data.

## Build

1. Upload the **contents** of this ZIP to the **repository root**, not inside an additional `OfferGuardianAndroid` folder. Remove any obsolete `.github/workflows/android.yml` so only the intended build runs.
2. GitHub → Actions → **Build Offer Guardian APK** → latest successful run → Artifacts.
3. Download and extract `OfferGuardian-v0.8.0-installable-APK`; install the `.apk` on your Android phone.
4. Verify Android App Info version **0.8.0**. Enable the Offer Guardian Accessibility Service, monitoring, and screenshot recognition.
5. Test **with automatic declines OFF / TEST ONLY ON first**. Confirm that only qualifying offers show the two-line overlay and that it disappears when the offer closes. Check History for accepted outcomes after a confirmed Uber active-pickup screen.

## Privacy

All detection runs on-device. No Uber credentials or remote servers. The app stores a local history of payouts, offer times, pickup/dropoff labels and the inferred/confirmed status; screenshots are processed transiently and not retained.

---

# Offer Guardian v0.8.0 — Concept B Rules & History

**Complete Android Studio / GitHub Actions project**, based on v0.5.0. This ZIP contains source and a GitHub workflow, **not a precompiled APK**. No Uber API or login is used.

## New: Offer Rules Builder

Open **Offer Filtering Rules → Open Rules Builder** in the app. Add or remove any of these conditions:

1. **Minimum total payout** — e.g. A$30 per offer.
2. **Minimum gross hourly estimate** — payout × 60 ÷ advertised minutes, e.g. A$40/hr.
3. **Minimum gross payout per kilometre** — payout ÷ advertised kilometres, e.g. A$3/km.

Select **ANY (OR)** to alert for an offer meeting *at least one* enabled condition, or **ALL (AND)** to require *every* enabled condition. Exactly equal values pass. For example, with ANY, a A$20 order estimated at 27 minutes qualifies because its estimated A$44.44/hr meets an A$40/hr requirement even if the A$30 payment requirement fails. With ALL it fails.

The new Rules page follows the approved **Concept B light/green design** and includes editable controls, Remove/Add Rule, save settings, and three updating sample offers. Newly upgraded apps preserve the previous $30 payment rule alone; hourly and kilometre conditions are disabled until added. All rules save to the same private Android preferences.

### Runtime safety behavior

- **SHOW:** a qualifying request plays the existing independent Offer Guardian alert (when alerts are enabled) and is left for manual acceptance/decline.
- **DECLINE:** an offer failing selected criteria can use the existing verified visual X gesture, but **only** if auto-decline is on and **TEST ONLY is off**.
- **REVIEW:** if the payout, OCR minutes, or kilometres needed for an enabled rule are unavailable, the app does **not** auto-decline. It reports the missing fields and alerts for manual review if alerts are enabled. Subsequent OCR frames can fill missing time/distance, triggering re-evaluation without duplicate history records.
- Screen recognition still requires an active Uber Driver foreground, a fresh screenshot, a verified grey X, the offer-card border, and payout OCR tied to that same card. There is no automatic acceptance and no blind coordinate taps.
- A completed Android tap is not proof Uber actually declined: the history records a decline attempt, while the outcome remains Unknown until confirmed.

## History upgrades

The existing **All Offers / Accepted / Highest** tabs retain pickup, approximate destination, estimated distance and local timestamps, with **estimated A$/hour alongside A$/km** in offer rows, offer details and CSV exports. The history page also shows current active-rule settings and links to the Rules Builder.

All payouts, hourly rates, and per-kilometre rates are estimates from Uber offer-card data, **not final earnings or net profit**. Missing time/distance shows N/A. Outcome detection remains conservative: manual acceptance or decline may need to be marked explicitly.

## Keeping your existing data

Install as an **update to the existing app** when possible. Normal updates with the same applicationId and signing certificate preserve history/settings. GitHub Actions debug keys may differ across builds, so Android might require uninstalling older builds; **uninstalling erases SQLite history**. Export existing Offer History to CSV *before* uninstalling. CSV export is not an automatic restore/import.

## Installation using GitHub

Upload the **contents** of this ZIP to the repository root (`app/`, `.github/`, `build.gradle`, etc.), replacing the older source rather than nesting another `OfferGuardianAndroid/` folder. Commit changes, run **Build Offer Guardian APK** in GitHub Actions, and download `OfferGuardian-v0.8.0-installable-APK` when the build succeeds. The workflow selects an actual `0.8.0` source tree, runs pure-Java tests, compiles using Java 17 / Gradle 8.9 / SDK 35, and checks the resulting APK version.

After installing, verify the app says **0.8.0**. Start with **TEST ONLY enabled** and verify monitoring diagnostics for a few real offers before allowing real auto-declines. Test both an OR rule that rescues a below-price but high-hourly offer and an AND rule. You may need to re-enable Accessibility if the app was uninstalled.

**Limitations:** ML Kit offline OCR is fallible; app needs Android 11+ for Accessibility screenshot capture and the Uber Driver screen in the foreground. Exact pickup/delivery addresses cannot always be read from the incoming offer. Some Uber internal notification audio cannot be silenced by this app. Automated declinations can affect Uber account standing. No Internet permission, no hidden data transfers, no guarantee of compatibility with future Uber screens.

## New in v0.8.0 — History average summaries

The approved Concept B green/white Offer History now displays **Average Offer**, **Average A$/hour**, and **Average A$/km**.

- **All Offers:** Total, Accepted, Declined, Highest Offer, then all three averages.
- **Accepted:** Accepted count and all three averages calculated **only from offers confirmed Accepted**, not all Uber offers.
- **Highest:** Highest offer and averages of offers in the current search/date filter; offers are ranked by gross payout.
- **Period picker:** Today, Last 7 days, Last 30 days, or All dates. Search filters both the list and the summary.
- Averages include **all** matching stored offers, not merely the 35–100 rows currently rendered.
- Average offer is an arithmetic mean of all matching payouts. Average hourly rate is an arithmetic mean of per-offer gross hourly estimates (payout / advertised time × 60); average $/km is the arithmetic mean of per-offer payout / advertised kilometres. Entries missing time/distance are **excluded only from their respective rate average**. If no qualifying offers exist, show a dash, never $0.00.
- Summary values update after the user confirms or changes an offer's outcome. All data stays in the original database; **no migration or history wipe**.

**Build:** GitHub Actions publishes `OfferGuardian-v0.8.0-installable-APK` only after payout, rate, filter-rule and summary tests pass and the Android APK has compiled. The source was not compiled to an APK in the current environment.
