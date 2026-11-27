# 3.1.0 — Touch diagnostics

Based on the clean v3.0.0 Uber-only source, not the installed build whose logs still mention DoorDash.

* Separate self-owned touch test target with DOWN/UP/CANCEL/CLICK counters, raw coordinates, hold times, dispatch and callback status, and copyable report.
* Select original native-first behavior, native-only, or 100/200/300 ms fixed gesture. Default remains original. Strict verified offer, foreground, target, timing and retry conditions retained. The app removes its own overlay before attempting a decline.
* Retain financial parsing, rates, history, alerts, and settings. No additional third-party apps monitored.
* Local vector icon in manifest, version code 34, matching GitHub workflow and test bundle.
* Device behavior not yet verified. A successful callback is not proof of a registered touch or a decline.
