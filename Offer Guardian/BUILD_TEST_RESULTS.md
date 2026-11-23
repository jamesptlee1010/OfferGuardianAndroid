# OfferGuardian v0.9.9 — Tests and verification

## Completed in this workspace

- Original v0.9.8 source ZIP extracted and updated (not rebuilt from scratch).
- The **22 existing** host-side workflow steps (Java tests, SQLite integration, screen geometry, day/night visuals, payout verification, offer-history migration, old duration regression, 1/2/3 tiers, Waze timing) passed.
- **New v0.9.9 test step passed**: 18 Java time-window assertions; 3 production-SQL SQLite scenarios; 17 payout/hourly/duration-recording assertions. In total **23 host-side test/check steps passed**.
- Time-of-day window includes endpoints, works overnight, and composes with date/monetary restrictions using local-device timestamps.
- New tests verify missing kilometres do not obstruct duration-based hourly decisions and that mismatched duration readings are not treated as verified.
- v1→v2 history migration smoke test passed; no new DB schema migration is necessary.

## Not verified here

- GitHub's Android `:app:assembleDebug` task has **not** run in this workspace: Android SDK and Gradle are unavailable. The APK has **not** been generated locally.
- No live Nubia/Waze test, screen rendering test or real automated-decline gesture was performed.
- Faster response is a reasonable goal of decoupling distance OCR, but no actual on-device latency reduction has been measured.

Build through the included GitHub Actions workflow and start with TEST ONLY enabled.
