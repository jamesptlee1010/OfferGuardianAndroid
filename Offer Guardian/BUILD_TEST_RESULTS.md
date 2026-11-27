# v3.0.0 experimental source build
- Existing screen evaluator: 11 tests passed.
- Existing watchdog: 18 assertions passed.
- Seven supplied images: X detection passed on all seven after WebP-to-JPEG conversion and normalization to 686x1536. Manually transcribed OCR line fixtures passed price validity, offer identity, duration and count checks. This is not a live ML Kit OCR test.
- Android compilation and live Oppo/Nubia gesture delivery not tested: Android SDK/Gradle unavailable in this environment.
- This is a conservative reset of capture scheduling and gesture coordinate validation, not a complete replacement of the existing OCR/financial/session engine. DoorDash shares the serialized capture scheduler. A permanently missing Android callback now requires restarting the accessibility service instead of launching overlapping requests.
