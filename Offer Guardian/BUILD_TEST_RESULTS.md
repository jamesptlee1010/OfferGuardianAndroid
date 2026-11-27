# 3.1.0 host-side verification

The GitHub workflow's host-side regression/check steps (including the new touch method tests) were executed locally using JDK 21. All 28 steps passed; TouchMethodStandaloneTest passed 24 assertions. The workflow itself uses Java 17 for its Android build.

All 38 production Java files passed javac parser syntax checks; all Android XML files parsed successfully. Compared to the v3.0.0 source archive, only MainActivity and OfferAccessibilityService were changed among existing Java classes; the two new classes are TouchMethod and TouchDiagnosticsActivity. Financial recognition/rules/history source files were unchanged.

Limitations: no Android SDK/Gradle build available in this environment. No Android APK compilation, signing, installation, physical or simulated touch testing on a phone, or actual Uber decline verification has been performed. The self-owned diagnostic button must be tested on both devices. A blocked test or a completed gesture without a target click is not a successful touch test.
