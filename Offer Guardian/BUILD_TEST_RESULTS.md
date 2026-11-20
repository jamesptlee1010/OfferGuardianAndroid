# Offer Guardian v0.9.1 — build and test report

- Full Android SDK / Gradle APK compile: **not performed here** (Android SDK/Gradle are not installed). Use the included GitHub Actions workflow to compile and download an APK. Do not assume installability until GitHub succeeds.
- Available 14 Java test suites **passed** in the container, including the new `WazeFastVerifierStandaloneTest` (18 assertions) for fast and safe confirmation.
- Checks cover payout, price-on-card geometry, day/night and Waze X matching, detail extraction, history/rate summaries, AND/OR rules, overlay gating, sounds, accepted-offer evidence, trusted app context and screenshot verification policy.
- The unchanged X detector also found the expected button in all six dark-mode screenshots and all six Waze screenshots used in local checks (plus four other daytime examples). **This does not test Android OCR timing or live taps.**
- The intended Waze capture interval is approx. 700 ms while idle, with a fast confirm at least 430 ms after the first capture, subject to Android restrictions and OCR completion.
- Live Diagnostics now provides `capture | X | OCR | scan` timings; successful Waze checks additionally show `Waze confirmation ...ms`. Device timing can only be measured after installing.
- No user screenshots, addresses or other personal data are bundled. Small X-reference templates remain in `res/raw`.
- First test with **TEST ONLY enabled**. Never tap if merchant, card, X, price, duration, distance or rule result is uncertain. Some confirmation failures may leave an offer for manual review rather than decline it.
