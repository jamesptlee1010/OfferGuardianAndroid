# v2.3.0 — Cross-platform offer handling
- Recognise verified Uber overlay offers on DoorDash and other foreground apps without requiring the native Uber screen. Separate scanning from tap ownership, revalidate window identity after OCR, and preserve read-only alert behavior when ownership is unavailable. Serialize gesture dispatch.
- Match Dasher package and New Delivery! notification text; ignore stale, duplicate and unrelated notifications. Evaluate switches only after fresh screenshots show no competing Uber offer. Existing idle-state protection remains; opt-in other-app switching requires recently verified idle Uber evidence. Test-only now permits switching without decline taps.
- Return to prior app when DoorDash idle screen is observed twice after decline attempt; recognise "Finding offers". Android activity launch may fail.
- Preserve v2.2 draggable quick controls, rules, preferences and History. No new GPS or auto-accept.
- Full source release, not an APK. Host checks passed; Android compilation and live interactions pending.
