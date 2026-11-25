# 2.0.0 validation
Host-side Java and SQLite regression steps from `.github/workflows/build-apk.yml` completed successfully, including new DoorDash parser tests (six screenshot transcriptions and safety negatives). Actual screenshot pixel checks: 9/9 Uber cards identified by existing X detector; 8/8 DoorDash offers/confirmation screens rejected by Uber X detector. No ML Kit live OCR test, Android compilation, physical-device verification or official platform permission verification performed. APK is not included. Use the included workflow to compile and test.

## 2.1.0 quick controls
Six host Java screen-state tests passed (idle, menu, paused, offer, active delivery, unknown). Android SDK compilation and live accessibility/overlay testing not performed. Prior 2.0.0 results above are historical, not rerun for this revision.
