# 3.0.0 validation

- 26 inherited host-side workflow steps passed (Java rules, visual synthetic detection, duration and verification, watchdog, CSV and SQLite history migration, filtering, sorting, delivery counts, overlays, accepted evidence, tier defaults, lifecycle checks).
- 40 new Uber-only regression assertions passed (financial decisions for the $18.03 Woolworths, $18.07 and $19.03 Coles examples; reset retry budget for a changed offer; cooldown and bounds; hour/minute OCR and punctuation; history deduplication and offer identity).
- Three supplied recent screenshot images normalised with PIL to 686x1536, then tested with Java detector: 185.jpg 98.78% (579,522); 186.jpg 98.51% (579,618); 187.jpg 96.03% (579,587). Repeat search matched all coordinates. This is an offline visual test only, not Android OCR or gesture test. Raw screenshots are not bundled.
- Java syntax parser passed 36 production source files. This is not an Android type-check or APK compile.
- Full Android SDK/Gradle compilation and live accessibility gestures, screenshot capture, RAM and battery measurements are not verified locally; GitHub workflow and parked device testing required. No promise of real-world accuracy or speed.
