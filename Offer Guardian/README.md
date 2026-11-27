# Offer Guardian 3.0.0 — Uber Eats only

Native Android utility for reading Uber Driver offer cards locally. This rebuild uses the dedicated Uber-only v0.9.15 scanner architecture with selected v2.6.1 financial, OCR, visual detection and history improvements. No DoorDash service, notification listener, application switching, floating quick controls, navigation or GPS integration. No automatic acceptance.

## Installation / updating from a dual-platform release

1. Export Offer History as a backup before upgrading.
2. Extract the complete source ZIP to a new folder. In GitHub, **replace the entire old source project**, including `app/` and `.github/workflows/`. Do not simply overlay files: removed Java classes in a v2.x tree must be deleted, or Gradle will try to compile them. Keep any unrelated repositories/files.
3. Run the **Build Android APK** workflow. It selects a project with versionName `3.0.0`, expands `tests_bundle.zip`, runs host-side regressions, compiles and verifies the APK. Download the artifact `OfferGuardian-v3.0.0-installable-APK` only after a green build.
4. Install the resulting APK over the existing installation only if Android accepts the same signing certificate. The app uses the existing application ID and SQLite schema, so an in-place upgrade preserves existing history and preferences. If Android requires uninstalling, export history first; uninstalling erases private app data. Re-import the CSV after reinstalling.
5. In Offer Guardian enable the Uber-only accessibility service and monitoring. Existing auto-decline and TEST ONLY preferences are preserved on upgrade. Fresh installs require explicit opt-in to automatic declines. Check TEST ONLY first while safely parked. No notification listener access is necessary.

## What remains

- Automatic light/dark offer-card recognition for Exclusive, Trip Radar and Pack & Deliver; financial rules, sound and upper-right hourly / kilometre overlay.
- Fresh installs have all three delivery tiers enabled with A$30/hr and price disabled. Existing rules are not silently overwritten; use the Apply $30/hr to all three delivery tiers button to reset them deliberately.
- Two independent matching reads for automatic decline, verified native Uber target, bounded per-offer retries, direct accessibility click when an exact decline control is exposed, otherwise a fresh verified X gesture. No taps while another foreground app is active.
- Financial display may precede strict history confirmation. An ambiguous read never authorizes a decline. A gesture callback is not platform confirmation; outcome remains unknown until confirmed or manually marked.
- Single background visual worker, reused pixel buffer, targeted X scan, bounded OCR backlog, screenshot watchdog, alternate contrast/scale payout recognition and context-specific duration fixes. Capture/ML Kit performance and real gestures require live testing.
- Existing database, history filters, manual outcomes, CSV import/export, duplicate detection and approval-based cleanup. Historical records from earlier versions (including previous platforms) are preserved; no new non-Uber offers are recorded. A 60-second history deduplication heuristic can confuse genuinely identical offers; review before deleting.

See BUILD_TEST_RESULTS.md for actual host-side checks and limitations.
