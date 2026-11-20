# Offer Guardian v0.9.0 — automatic Uber / Waze / day / night mode

**Full GitHub-ready source project, not a compiled APK.** This version starts from v0.8.0 and retains the Concept B rules, history, conditional overlay and sound alerts.

## What's new

- **Automatic day/night recognition** on each incoming offer: the X detector examines the actual offer card and chooses between a light-template or dark-template, with a fallback when appearance is uncertain. There is no day/night switch in the app.
- **Waze navigation support:** the accessibility monitor receives Uber Driver and Waze window events. It will scan for a valid Uber offer card on either screen, instead of assuming Uber must own the foreground.
- **Match and Accept card support** with green, black or light borders, using both X glyph styles. The app does not tap the Accept/Match button itself.
- **Extra Waze safety:** an automated X gesture from a Waze foreground screen requires the same offer to be seen twice, an X match confidence of at least 94%, and OCR evidence of a delivery offer and its estimated earnings. If a check fails, the offer is left for manual review.
- **Live diagnostics** now identify day/night matching and the current foreground host (Uber or Waze). No manual mode choice.
- Existing payout, estimated $/hour and $/km calculations, ANY/ALL rules, conditional small overlay, sound alerts, offer history and conservative accepted-offer tracking remain available.

## How the app processes an offer

1. Uber or Waze is visible on the screen. The accessibility service captures a screenshot, without sending it anywhere.
2. The detector verifies an offer-card border and matching X, automatically deciding which light/dark glyph fits the card.
3. On-device OCR extracts the price within the same card, and when possible time, distance and pickup/destination.
4. The saved ANY/ALL rules decide whether to show the overlay + alert or allow an optional decline.
5. If information is uncertain, **do not tap**. When Waze is in front, require extra consecutive-screen confirmation and offer labels before a decline.
6. The offer disappears => overlay disappears. A successful tap gesture does not prove Uber declined the offer. Accepted status is only recorded when separately confirmed in Uber; otherwise history keeps Unknown.

## Safety and platform limitations

- Android 11+ and Accessibility permission required. The screen's 686x1536 aspect ratio is currently expected; different displays fail closed.
- This is a private, unverified Android app, not affiliated with Uber or Waze. Behaviour can break as Uber's UI changes, and automated interaction may be restricted by platform rules.
- Waze's *background* does not need to be recognised—the visible Uber card does. Some devices may expose overlays with different accessibility package identities or prevent screenshots; check Live Diagnostics if needed.
- Some display changes or screenshots may be recognised only in testing; **offline tests are not a substitute for live-device testing**. Try TEST ONLY with offers in all three contexts before turning on automatic declines.
- In the Android Settings accessibility screen, the service name is now **Offer Guardian – Uber/Waze offer monitor**. Recheck the service after updating.
- The app collects no credentials, has no internet permission, and retains data on the phone. Export the offer history before uninstalling/reinstalling.

## Install

Use `.github/workflows/build-apk.yml` in a GitHub repository and run **Build Offer Guardian APK**, then download the artifact `OfferGuardian-v0.9.0-installable-APK`. See `GITHUB_BUILD_GUIDE.md`.
