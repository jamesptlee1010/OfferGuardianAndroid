# v3.3.1 validation

- 30 local workflow host-side verification/test steps passed (source inventory plus all 29 test steps).
- Live focus guard: 23 assertions, including supplied Oppo Android 16 focus output and missing/null/foreign/ambiguous focus rejection.
- Uber-only retry/OCR/history suite: 44 assertions, including blocked-request cooldown without consuming dispatch budget.
- 27 touch-method and 18 own-screen Shizuku assertions passed.
- All 44 Java sources parsed successfully with JDK Java 17 syntax rules. Manifest parsed; workflow YAML parsed.
- Existing financial/OCR/history classes are byte-identical to v3.3.0; version labels, connection-service versions and focused input checks changed.
- Full Android compilation and actual Shizuku offer dismissal are not verified locally. Explicitly protected, stale, unknown or non-Uber targets remain blocked. Use only when safely parked.
