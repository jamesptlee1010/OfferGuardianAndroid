# Source validation — Offer Guardian v0.9.0

- Full Android Gradle compilation: **not available in this environment**; GitHub Actions will perform the APK build.
- Offline Java host tests passed: payout parsing, light/black-border X detection, offer OCR bounds, alert rules, offer detail extraction, AND/OR rule engine, hourly and km rates, history averages, overlay gating, conservative acceptance evidence, Uber/Waze foreground restrictions and Waze OCR offer identity.
- Synthetic tests passed for light card, black-border Match card, dark card, dark Match card, Waze-positioned card and an unrelated navigation screen.
- The updated visual matcher located the correct X in **all 6 user-supplied night-mode screenshots**, **all 6 user-supplied Waze screenshots**, and **4 additional earlier light-mode screenshots** tested locally. These image-only tests do not validate Android's screenshot permissions, OCR reliability or tapping on a real device.
- Real screenshots and location details were **not included** in this source package. The only new image asset is a small 41×41 dark-theme X-button template.
- Version locked in the workflow to **0.9.0**, with `versionCode 10` and one top-level project root.

## Live-device checks before auto-decline

1. Ensure the Accessibility service is enabled after upgrading.
2. With TEST ONLY enabled, wait for a light Uber offer, then a dark Uber offer, then a Waze navigation offer. Check that Live Diagnostics reports the right price, card X and detected light/dark mode.
3. Qualifying offers must show only the small `$ /hr` + `$ /km` overlay and optional alert.
4. Non-qualifying offers must show **No tap** while TEST ONLY is enabled.
5. If testing a Waze decline, the app additionally requires stable consecutive captures and OCR offer identity before dispatching a gesture. A dispatched gesture does not confirm that Uber declined.
