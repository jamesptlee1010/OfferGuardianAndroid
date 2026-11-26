# 2.0.0 validation
Host-side Java and SQLite regression steps from `.github/workflows/build-apk.yml` completed successfully, including new DoorDash parser tests (six screenshot transcriptions and safety negatives). Actual screenshot pixel checks: 9/9 Uber cards identified by existing X detector; 8/8 DoorDash offers/confirmation screens rejected by Uber X detector. No ML Kit live OCR test, Android compilation, physical-device verification or official platform permission verification performed. APK is not included. Use the included workflow to compile and test.

## 2.1.0 quick controls
Six host Java screen-state tests passed (idle, menu, paused, offer, active delivery, unknown). Android SDK compilation and live accessibility/overlay testing not performed. Prior 2.0.0 results above are historical, not rerun for this revision.

## 2.2.0 drag and resume
16 host screen/DR checks and 14 pure Java drag geometry/tap-suppression checks passed. These are not Android touch event, accessibility, live OCR or overlay integration tests. No Android SDK or Gradle available locally; APK compilation and physical device validation outstanding. Previous results above are historical.

## 2.3.0 cross-platform offers
29 host-side workflow regression/check steps completed successfully (Java, SQLite and source checks), including 28 new cross-platform policy assertions and 4 new transcribed offer-text fixtures. Actual supplied screenshots decoded and rescaled to 686x1536: Uber X recognition matched 4/4 new Uber cards (including Pack & Deliver and heavy delivery), rejected 1/1 DoorDash offer screenshot. These test the visual detector and pure Java text parsing separately, not live ML Kit or Android accessibility ownership. Android SDK/Gradle unavailable locally; no APK compilation or physical-device test. No claim of confirmed live declines or guaranteed automatic app launches.
