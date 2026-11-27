# OfferGuardian v0.9.14 — Uber Driver only

This is complete Android source based on v0.9.13. **Waze-specific runtime functionality is removed.** It monitors only Uber Driver in the foreground: no Waze package visibility, no Waze accessibility events, no 700 ms navigation polling, no Waze verification helper and no Waze-specific UI controls.

Uber Driver monitoring retains its 1,700 ms polling and bounded fast re-reads during active offer verification. This does **not** introduce a Low Resource Mode. CPU benefits depend on how often Waze was previously foreground.

Existing solo/double/triple delivery rules, OCR hour/minute parsing, History import/export, weekday/time filters, historical delivery-count filter, rate sorting and long-press deletion remain.

**Offers over Waze will no longer be processed. Keep Uber Driver foreground for automatic declines.** This ZIP contains source code, not a compiled or phone-tested APK. Start with TEST ONLY.

See `INSTALL_ON_GITHUB.md`, `GITHUB_BUILD_GUIDE.md` and `CHANGELOG_v0.9.14.md`. Older changelogs document functionality that has since been removed.
