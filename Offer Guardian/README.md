# Offer Guardian 3.2.0 — Uber Eats only

Native Android utility for reading Uber Driver offer cards locally. This rebuild uses the dedicated Uber-only v0.9.15 scanner architecture with selected v2.6.1 financial, OCR, visual detection and history improvements. No DoorDash service, notification listener, application switching, floating quick controls, navigation or GPS integration. No automatic acceptance.

## Installation / updating from a dual-platform release

1. Export Offer History as a backup before upgrading.
2. Extract the complete source ZIP to a new folder. In GitHub, **replace the entire old source project**, including `app/` and `.github/workflows/`. Do not simply overlay files: removed Java classes in a v2.x tree must be deleted, or Gradle will try to compile them. Keep any unrelated repositories/files.
3. Run the **Build Offer Guardian APK** workflow. It selects a project with versionName `3.2.0`, expands `tests_bundle.zip`, runs host-side regressions, compiles and verifies the APK. Download the artifact `OfferGuardian-v3.2.0-installable-APK` only after a green build.
4. Install the resulting APK over the existing installation only if Android accepts the same signing certificate. The app uses the existing application ID and SQLite schema, so an in-place upgrade preserves existing history and preferences. If Android requires uninstalling, export history first; uninstalling erases private app data. Re-import the CSV after reinstalling.
5. In Offer Guardian enable the Uber-only accessibility service and monitoring. Existing auto-decline and TEST ONLY preferences are preserved on upgrade. Fresh installs require explicit opt-in to automatic declines. Check TEST ONLY first while safely parked. No notification listener access is necessary.

## Existing Touch Diagnostics (introduced in 3.1.0)

- Open **Touch Diagnostics** on the home screen. Manually press the green X target first. Check that DOWN, UP and CLICK are recorded.
- With the accessibility service enabled, run native click, 100 ms, 200 ms and 300 ms tests separately. Do not touch the screen during a test. A native action can register a click without DOWN/UP. A gesture passes only when the target receives exactly one DOWN, UP and CLICK without cancellation; Android's callback alone is not proof of a click.
- Tests are restricted to the app's own visible diagnostic target and refuse unknown or covering windows. The accessibility event filter now includes Offer Guardian itself; the live screenshot scanner remains Uber-only. If a test says BLOCKED, this is inconclusive about touch delivery.
- Copy the report from each phone. The report is temporary in this screen; copy before leaving if needed.
- Live Uber decline method is saved separately from financial rules. Default is the original native-first method with 90/140/200 ms gestures. Select native-only or a fixed 100/200/300 ms gesture to compare methods. Native-only never falls back; no exposed X means no action. The app removes its own overlay before rechecking the Uber target. Other apps' overlays are not removed.
- Test while parked; use the same settings and offer type when comparing phones. Existing fresh-screen, rule and window safeguards and retry budget remain. There is no automatic acceptance. A positive result on the diagnostic button does not prove Uber will respond.
- The local vector launcher icon avoids the old framework icon reference. This is a source build, not a locally compiled APK.

## Shizuku diagnostic integration (3.2.0)

1. Start Shizuku 13.5 in ADB mode on your phone. Open Offer Guardian → Shizuku connection & touch test.
2. Tap Authorise Shizuku and approve only this trusted app, then return to the diagnostics screen.
3. Tap Connect diagnostic service. Check UID 2000 (ADB), permission granted and diagnostic service connected. The remote input permission indicator is informational, not proof of delivered touches.
4. Manually press the green X once; then tap Test one Shizuku tap on green X. Remain on this screen and do not touch the phone during the test. A pass requires exactly one DOWN, UP and CLICK and zero CANCEL events. If focus verification fails, the result is inconclusive.
5. Copy diagnostic report. Compare with the existing Touch Diagnostics accessibility tests if needed. The test is local to this app and has no live Uber Shizuku tapping mode. Keep existing monitoring off during testing. Restore any changed system security settings and disable debugging when no longer needed.

Use a fresh GitHub repository with the included workflow; upload the contents of this ZIP, not the ZIP itself. A source inventory check now fails early if obsolete files are still present. `tests_bundle.zip` is expanded by CI, so do not upload the expanded tests separately. If the workflow is missing, create `.github/workflows/build-apk.yml` from the included file. Same package name means updating in place requires the same APK signing certificate; back up history before uninstalling.

## What remains

- Automatic light/dark offer-card recognition for Exclusive, Trip Radar and Pack & Deliver; financial rules, sound and upper-right hourly / kilometre overlay.
- Fresh installs have all three delivery tiers enabled with A$30/hr and price disabled. Existing rules are not silently overwritten; use the Apply $30/hr to all three delivery tiers button to reset them deliberately.
- Two independent matching reads for automatic decline, verified native Uber target, bounded per-offer retries, direct accessibility click when an exact decline control is exposed, otherwise a fresh verified X gesture. No taps while another foreground app is active.
- Financial display may precede strict history confirmation. An ambiguous read never authorizes a decline. A gesture callback is not platform confirmation; outcome remains unknown until confirmed or manually marked.
- Single background visual worker, reused pixel buffer, targeted X scan, bounded OCR backlog, screenshot watchdog, alternate contrast/scale payout recognition and context-specific duration fixes. Capture/ML Kit performance and real gestures require live testing.
- Existing database, history filters, manual outcomes, CSV import/export, duplicate detection and approval-based cleanup. Historical records from earlier versions (including previous platforms) are preserved; no new non-Uber offers are recorded. A 60-second history deduplication heuristic can confuse genuinely identical offers; review before deleting.

See BUILD_TEST_RESULTS.md for actual host-side checks and limitations.
