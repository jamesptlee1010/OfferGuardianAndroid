# Offer Guardian 2.5.0 — Dual-platform reliability

Upgrade from v2.4.0, with the same app ID and local history database. Existing thresholds, delivery tiers, settings and test-only switches are retained. No automatic acceptance. No new GPS or Internet permission.

## Changes
- Vertical D / DF / DR dock (U removed). Default right side, long-press 450 ms to drag the whole dock, save position, lock/reset available. Old horizontal position is reset for the new layout.
- D opens DoorDash; DF pauses the current Dash and returns to Uber (does not end the Dash); DR resumes a paused Dash. All require verified controls. Use only while safely parked.
- Uber overlay handler now classifies Exclusive and Trip Radar, preserves the financial session across window ID changes, logs ownership and rule details, and attempts verified accessibility clicks before gesture fallback. Up to three attempts per continuous visible card sequence, with a cooldown, fresh verification and a one-time optional native Uber fallback. If the owner cannot be verified, never tap the overlay. If fallback opens Uber, you may need to switch back manually. Visual absence is not proof of platform decline.
- DoorDash recognises Customer drop-off, Drop off N orders and Multiple drop-offs (N stops). Separate financial parsing from action identity. Qualifying display/alerts are independent of whether the decline control is readable. Conflicting accessibility/OCR readings prevent automated declines. Near-hourly-threshold declines need extra agreeing readings (or corroborating accessibility and OCR). Previously qualifying cards with matching timer/merchant continuity are protected against contradictory later readings. Existing AND/OR and tier rules are unchanged.
- DoorDash's two-stage decline remains guarded; shortened scan schedule and bounded verified retry on a cancelled gesture. New Delivery! notifications can use a fresh package-verified notification activity intent, with explicit app launch fallback. Switching must be enabled and may still be prevented by Android or active-delivery safeguards.
- Scanner targets: 500 ms normal poll, 450 ms minimum capture interval, 360 ms fast confirmation interval (rather than 300 ms to leave a margin above Android's approximately 333 ms screenshot limit). Actual OCR and interaction speeds vary.
- Copy diagnostics includes latest Uber rule reading, unresolved window packages/IDs/bounds, DoorDash effective rule reasoning and notification switching results. No screenshots saved or included in the source archive.

## Build and install
1. Export offer history before upgrading. Keep the same signing key for an in-place upgrade.
2. Extract the archive and upload all root contents to the existing GitHub project (including hidden `.github` workflow). Keep only one active version of the workflow. `tests_bundle.zip` is expanded by CI; do not manually upload its contents.
3. Run Actions → Build Offer Guardian APK → Run workflow. The workflow runs the host tests and builds an Android debug APK using SDK 35, JDK 17, Gradle 8.9. Download the v2.5.0 artifact if successful.
4. Test while safely parked. Enable accessibility, monitoring, both delivery platforms, and optional notification access/switching. Keep test-only switches on until actual readings and targets are verified. Review delivery-platform agreements before enabling automation.
5. If a $30/hr-only rule is intended, check that the relevant delivery tier enables hourly at $30 and that any other enabled rules/AND logic are intentional. No configured rule has been overridden by this upgrade.

## Validation and limitations
See BUILD_TEST_RESULTS.md and CHANGELOG_v2.5.0.md. The host-side tests do not prove real ML Kit OCR accuracy, Android window ownership, notification switching, drag gestures, or live declines. No Android SDK or Gradle was available locally, so APK compilation and live-device testing remain outstanding. Decline requests and observed dismissal do not independently confirm a platform-recorded decline; uncertain targets remain untouched.
