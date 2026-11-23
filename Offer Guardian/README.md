# OfferGuardian v0.9.8 — Independent Delivery-Specific Rules

**Complete GitHub-ready Android source ZIP; not an APK.** Built directly on the full v0.9.7 project. Previous Faster Waze, History Filters, History Sorting, OCR corrections, notifications and TEST ONLY functionality remain in place.

## New Rules Builder (v0.9.8)

Open **Offer Rules Builder** and choose **1 Delivery**, **2 Deliveries**, or **3 Deliveries**. Each category saves its **own** enabled switch, minimum total payout, minimum estimated hourly rate, minimum A$/km, optional maximum journey distance and **ANY (OR) / ALL (AND)** combination. Each criterion has its own checkbox; unchecked criteria do not apply. **Save all three delivery rule sets** activates tiered rules together.

Example configuration (not preinstalled):

| Header | Rule | Outcome for a lower offer |
| --- | --- | --- |
| `Delivery` | Solo payment ≥ A$25 | Decline below A$25 |
| `Delivery (2)` | Double payment ≥ A$35 AND hourly ≥ A$45 | Decline when verified criteria fail |
| `Pack & Deliver (3)` | Triple payment ≥ A$50 AND hourly ≥ A$55 | Decline when verified criteria fail |

The selected category is **exactly** the confirmed number in Uber's green heading; the previous global minimum-deliveries switch is **ignored** when tiered rules are active. No matching category is silently substituted. Count 0/unreadable, disabled categories and **4 or more deliveries** are manual review (no tap). When a rate needed by the active criteria is missing, the app waits for a verified reading rather than inventing a rate.

**Payout-only decline:** When the selected tier has only a minimum payout rule, or an ALL (AND) tier has a minimum payout that already fails, the decision is independent of missing duration. The app still requires two agreeing images for payout, delivery count, pickup, card and X and retains the guarded Waze confirmation. Such count/payout-only history records have **Unknown hourly rate** rather than a made-up number. The $5/11-min and 61-/67-min fixes remain.

**Upgrade behaviour:** Installing v0.9.8 does **not** activate tiered rules or modify your existing rules. On first opening, the three categories are prefilled with the previous payment/rate thresholds to give you a starting point. Configure each category, then press **Save all three delivery rule sets** to switch the app to the new logic. Legacy preferences remain stored as a fallback; after activating tiers, you can use **Restore previous global rules** in the Rules Builder to switch back explicitly. Save changes before returning to Uber Driver. If a save occurs while an offer is visible, the monitor invalidates any earlier cached decision and re-verifies it.

## History and navigation (unchanged)

**Offer History** retains Advanced Filters by min/max payment, verified hourly rate, A$/km and distance, as well as sorting newest first, highest/lowest payment and highest/lowest verified hourly rate. Unknown/unverified rates are not treated as verified hourly values. These are display-only controls and **do not affect automatic offer decisions**.

Faster Waze uses guarded screenshots and two-image offer confirmation. Android may rate-limit screenshots. The app does not automatically accept offers. Auto-decline is only attempted if monitoring, visual recognition and auto-decline are enabled, **TEST ONLY is off**, and all required visual and contextual safety checks pass. Verification failures mean no tap.

## Build through GitHub

1. Extract the ZIP and upload the **contents**, not the ZIP itself, into your existing GitHub repository root. Preserve `.github/workflows/build-apk.yml` and all folder structure.
2. Remove obsolete nested old project copies and older workflows. GitHub browser uploads are limited to 100 files per operation; this package is under that limit.
3. Open **Actions → Build Offer Guardian APK**, run the workflow and download the debug APK from the successful build's artifacts.
4. Export Offer History **before uninstalling** any installed version: uninstall erases local data, and GitHub debug signing certificates can change between runs, preventing in-place upgrades.
5. On the phone, enable Accessibility and monitoring. Configure your three tiers and save. **Keep TEST ONLY enabled** while testing `Delivery`, `Delivery (2)`, `Pack & Deliver (3)`, incomplete count OCR and Waze day/night offers. Read Live Diagnostics before permitting automated declines.

**Verification limits:** Pure-Java and SQLite host tests passed. UI syntax was checked with local Android API stubs. This container does not have the complete Android SDK/Gradle toolchain, so it did **not** compile a real APK or validate OCR, gesture timing, layout or successful auto-declines on the Nubia Focus 5G. It is a private tool not affiliated with Uber or Waze; automation may affect third-party account standing. No network permission is requested by the app.
