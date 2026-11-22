# Offer Guardian v0.9.6 — Mandatory Minimum Deliveries + Faster Waze + History Filters

**Full GitHub-ready source project, not a compiled APK.** This version updates the original v0.9.1 Faster Waze source and retains the working day/night/Waze recognition, Concept B rules, history, conditional overlay and sound alerts.

## What's new in v0.9.6 — mandatory minimum deliveries

Set **Minimum deliveries** to 1, 2, 3, or 4 (disabled by default on update). The count is read exclusively from the green Uber card heading: a complete unbracketed Delivery/Pack & Deliver means one, `Delivery (2)` means two, and `Pack & Deliver (3)` means three. If the count is unreadable or a suffix is truncated, it is **UNKNOWN**, never assumed single.

When enabled, this is a **mandatory condition separate from ANY (OR) / ALL (AND)** on monetary and hourly rules. A lower-count offer can be declined after two stable readings of the count, payout, merchant and card; it does not require accurate OCR duration. When duration is unreadable, History retains **UNKNOWN hourly rate**. Count checks also apply to Waze without bypassing the extra Waze safety screen. The feature preserves the improvements below from v0.9.5.

## Preserved from v0.9.5 — low-value and long-duration decline recovery

- **$5/11-minute order:** Uber displays low whole-dollar amounts as `$5`, without `.00`. Recognise **unambiguous single-digit whole-dollar** amounts `$3`–`$9`; keep rejecting potentially truncated multi-digit amounts like `$29`, and require two matching OCR frames before any rule/gesture.
- **61- and 67-minute orders:** preserve the existing exact `1 hr 1 min` -> **61 minutes** and `1 hr 7 min` -> **67 minutes** logic. Confirm both readings, then apply the same live rules as short orders.
- **Incomplete hour components:** if OCR recognises only `1 hr`, it cannot be used as an exact duration. If the extracted merchant, payout, distance and hour bound agree on two images, and *even the maximum possible hourly rate* fails the driver's active rules, the app can safely request a decline. Otherwise it waits for better OCR. This rule works for both ANY (OR) and ALL (AND), with no assumptions about missing minutes.
- **History integrity:** safely declined hour-only offers are saved with `HOUR_BOUND`, exact minutes **unknown (0 internally)** and hourly rate **Unknown**. A subsequent pair of complete matching readings can upgrade the same row to `CORRECTED`. Existing History filters exclude incomplete rates from rate conditions, even though the safe lower-bound decision may allow a decline.
- **Fast OCR recovery:** incomplete/changed readings trigger up to five early retries before reverting to normal polling; Android screenshot rate-limit conditions back off. Retry status is included in on-phone Live Diagnostics.
- **No repeated automatic taps:** all paths still require verified card/X position, merchant and two fresh images. Waze retains its additional offer-identity and independent two-frame check. TEST ONLY and Auto-decline switches remain authoritative.
- **Regression coverage:** new tests for the actual `$5/11 min`, `$27.10/61 min`, `$29.66/67 min` scenarios, bounds that cannot safely decline, retry limits, and Waze/test-mode preservation.

**Important:** This source has passed host-side unit/integration tests, but has not been compiled into an Android APK or validated on the user's phone. Build through GitHub Actions, test with TEST ONLY first, and inspect Live Diagnostics for unverified or missed offers.

## What's new in v0.9.4 — Advanced History Filters

- Added a **Filters** button beside the History period selector; tap to set independent **minimum and maximum** boundaries for payment (A$), estimated routed hourly earnings (A$/hr), payment per kilometre (A$/km), and estimated distance (km).
- Choose **ALL (AND)** to require each selected metric to qualify, or **ANY (OR)** to match at least one selected metric. Minimum and maximum limits on a *single metric* always form an inclusive range together.
- All filters apply consistently to the list and every on-screen KPI, including matching offer counts, average payment, average estimated hourly earnings and A$/km. Search, date period, Accepted and Highest tabs still apply.
- Missing/unverified OCR-derived hourly, per-kilometre or distance values never qualify for those conditions. In an ANY query, a legacy offer may still match a **payment-only** condition, with rates displayed as **Unknown**.
- Filter settings persist locally, independent of **live Active Rules** and the auto-decline service. **Clear all** resets filters without touching saved Uber offer history. Existing **Export all CSV** continues to export full history, regardless of filters.
- v0.9.4 added display filters; v0.9.5 added recovery for low-payout and hour-plus declines. Both are preserved in v0.9.6.
- Added 45 pure-Java assertions and nine SQLite integration filter-query scenarios, included in GitHub Actions.

## Previous v0.9.3 — safer OCR / hourly rates

- Built from the ORIGINAL v0.9.1 Faster Waze source (not the v0.9.2 intermediate fix).
- Full duration extraction: `1 hr 7 min` becomes 67 minutes, including OCR split across adjacent lines.
- OCR validation flags physically implausible delivery times and shopping durations. Unknown/inconsistent readings do not trigger an automated decision. No software can reconstruct an hour that OCR never recognised in otherwise plausible text; such cases may require manual review.
- A first valid reading is **provisional**. Two matching screen observations are required before recording a verified offer, displaying a qualifying rate, or sending an automatic decline. A changed payout, time, distance, merchant or card position restarts verification.
- Waze retains fast second-frame scheduling (~430 ms minimum from previous screenshot start). This is a target, not a guarantee of elapsed real-device response.
- SQL history upgraded in place: new rows retain raw payout, duration, distance, parsed hours and minutes, total minutes, two-frame count and validation status; older rows are retained, explicitly marked `LEGACY_UNVERIFIED` and excluded from hourly / per-km average calculations rather than presented as verified rates.
- Offers without two consistent, plausible OCR readings stay in live Diagnostics for manual review and may not be saved in the verified offer history. This prioritises safe actions over logging every transient card.
- TEST ONLY should remain on until verified on the target Nubia / Waze / Uber setup. Live timing and full Android APK compilation have NOT been tested in this container.

## Previous v0.9.1 Faster Waze design — preserved

- **Faster Waze polling:** while Waze is foreground, the screenshot polling loop runs approximately every 700 ms rather than waiting for the prior 1,700 ms cycle (actual device scheduling varies).
- **Quick second check:** when an offer fails the selected rules, a second *fresh* screenshot is scheduled as soon as the prior OCR completes and Android's screenshot interval permits (at least 430 ms between capture requests). The safety requirement for two observations remains.
- **Verify it really is the same offer:** both screenshots must match on payout, offer-card/X coordinates, OCR delivery labels **and the pickup merchant**. Unreadable or changing information means **no automatic tap**.
- **Less OCR work:** screen text recognition now crops to the verified Uber offer card, avoiding OCR on Waze directions and most of the map.
- **Fewer repeated database operations:** complete offer details are cached while the same visible card is being verified. New offers still enter Offer History.
- **Useful timing diagnostics:** Live Diagnostics reports screen capture, X detection, OCR, scan time and Waze confirmation delay, in milliseconds. These are *measured on your phone* only after installing the app; no live performance claim has been verified here.
- **Screenshot-rate-limit handling:** when Android blocks an overly frequent capture, the app backs off rather than skipping the two-frame safety requirement.

## Existing functionality retained

- **Automatic day/night recognition** on each incoming offer: the X detector examines the actual offer card and chooses between a light-template or dark-template, with a fallback when appearance is uncertain. There is no day/night switch in the app.
- **Waze navigation support:** the accessibility monitor receives Uber Driver and Waze window events. It will scan for a valid Uber offer card on either screen, instead of assuming Uber must own the foreground.
- **Match and Accept card support** with green, black or light borders, using both X glyph styles. The app does not tap the Accept/Match button itself.
- **Extra Waze safety:** an automated X gesture from a Waze foreground screen requires the same offer to be seen twice, an X match confidence of at least 94%, OCR evidence of a delivery offer and its estimated earnings, and a matching pickup merchant. If a check fails, the offer is left for manual review.
- **Live diagnostics** now identify day/night matching and the current foreground host (Uber or Waze). No manual mode choice.
- Existing payout, estimated $/hour and $/km calculations, ANY/ALL rules, conditional small overlay, sound alerts, offer history and conservative accepted-offer tracking remain available.

## How the app processes an offer

1. Uber or Waze is visible on the screen. The accessibility service captures a screenshot, without sending it anywhere.
2. The detector verifies an offer-card border and matching X, automatically deciding which light/dark glyph fits the card.
3. On-device OCR extracts the price within the same card and reads time, distance and merchant. Missing or implausible values lead to manual review.
4. A second matching OCR frame is required to confirm the extracted price, total duration, distance, merchant and card before a rule decision.
5. The saved ANY/ALL rules decide whether to show the overlay + alert or allow an optional decline.
6. If information is uncertain, **do not tap**. When Waze is in front, retain its offer-identity and two-frame confirmation safeguards.
7. The offer disappears => overlay disappears. A successful tap gesture does not prove Uber declined the offer. Accepted status is only recorded when separately confirmed in Uber; otherwise history keeps Unknown.

## Safety and platform limitations

- Android 11+ and Accessibility permission required. The screen's 686x1536 aspect ratio is currently expected; different displays fail closed.
- This is a private, unverified Android app, not affiliated with Uber or Waze. Behaviour can break as Uber's UI changes, and automated interaction may be restricted by platform rules.
- The fast second capture is not guaranteed to arrive in a fixed time: Android rate-limits accessibility screenshots and OCR speed varies by phone. If verification fails, there is **no automatic tap**.
- Waze's *background* does not need to be recognised—the visible Uber card does. Some devices may expose overlays with different accessibility package identities or prevent screenshots; check Live Diagnostics if needed.
- Some display changes or screenshots may be recognised only in testing; **offline tests are not a substitute for live-device testing**. Try TEST ONLY with offers in all three contexts before turning on automatic declines.
- In the Android Settings accessibility screen, the service name is now **Offer Guardian – Uber/Waze offer monitor**. Recheck the service after updating.
- The app collects no credentials, has no internet permission, and retains data on the phone. Export the offer history before uninstalling/reinstalling.

## Install

Use `.github/workflows/build-apk.yml` in a GitHub repository and run **Build Offer Guardian APK**, then download the artifact `OfferGuardian-v0.9.6-installable-APK`. See `GITHUB_BUILD_GUIDE.md`.
