# Offer Guardian — Android prototype (Australia)

**Project status:** Android Studio source code, v0.1.0. The payout parser has been executed and tested on the host, but **the app has not been compiled into an APK or tested against a real Uber Eats request**. It is not affiliated with Uber.

## What it is designed to do

- Read the visible **Uber Driver** app (`com.ubercab.driver`) using Android Accessibility on Android 8.0+.
- When Android's accessibility text has no readable offer, use an **on-device ML Kit OCR fallback** on Android 11+ to recognise the screenshot. The screenshot is processed locally; no data is uploaded and the app requests no Internet permission.
- If an offer is **less than your minimum** (A$30.00 by default), attempt to press the calibrated **Decline** button — **only if you expressly switch on automatic decline and approve a captured decline control**.
- If an offer is **equal to or greater than your minimum**, play a notification sound, vibrate, and create a local notification. **The final Accept decision stays yours.**
- Show the last recognised offer and the attempted action.
- Default **monitoring OFF** and **auto-decline OFF** for safety. Sound alerts default ON (once monitoring has been enabled).

## Build on your Windows computer

1. Install **Android Studio** from https://developer.android.com/studio .
2. In Android Studio's **Tools → SDK Manager**, install **Android SDK Platform 35** (and SDK Build-Tools).
3. Extract this folder to a normal location such as `C:\Users\You\Desktop\OfferGuardianAndroid`.
4. Double-click **`BUILD_APK_WINDOWS.bat`**. Alternatively, open PowerShell in the folder and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\Build-APK.ps1
   ```

   The script uses your installed Android SDK and downloads official Gradle 8.9 and dependencies from their standard repositories. Requires an Internet connection on your Windows PC and Android Studio's bundled Java installation. The script may need several minutes and network access.
5. When successful, you'll find **`OfferGuardian-debug.apk`** in that folder. Copy it onto your Android phone and open it to install. Android may ask you to permit **Install unknown apps** for your file manager.

Alternatively, open this folder as a Gradle project in Android Studio, configure **Gradle 8.9 / JDK 17**, sync and run `:app:assembleDebug`. The APK goes to `app/build/outputs/apk/debug/app-debug.apk`.

**Important:** This source package intentionally does **not** include an APK or a Gradle wrapper JAR. The APK must be compiled on a machine with Android's build toolchain. Do not mistake a `.zip` for an installable Android app.

## Set up on the phone

1. Install the APK. Open **Offer Guardian** and set the minimum to `30.00`.
2. Enable **Monitor Uber Driver offers**.
3. Open **Accessibility settings**, locate **Offer Guardian – Uber screen monitor**, and grant accessibility access. You can stop monitoring in Offer Guardian at any time.
4. On Android 13+, if the system says **Restricted setting**, go to **Settings → Apps → Offer Guardian → three-dot menu → Allow restricted settings** (if your phone offers that option), then revisit Accessibility settings. This grants sensitive UI access, so only do it if you trust the locally built application.
5. Press **Test example offers** (simulation) and **Test alert sound** before using Uber.
6. For real automatic declines: **Arm learning**. When you get a real Uber Eats request that *you intend to decline*, manually press **Decline / X** in Uber Driver once. Return to Offer Guardian. Check the captured ID/label and press **Approve captured Decline control** only if you definitely declined. Then turn on **Auto-decline offers below minimum**.
7. If Uber's control is *not* exposed through Android accessibility, **Allow coordinate tap fallback** is available as an **extra explicit opt-in**. It only works with the same screen size/layout as calibration, and it is significantly less safe.
8. Test with the app foreground, observe real offers, and check the last-action panel. If it fails to detect offers or declines, turn off auto-decline rather than trusting it.

## Important limitations and account safety

- This is a **best-effort prototype**, **not a verified functional Uber integration**. The Uber Driver UI can change across phone models, app releases, and Australian regions. It may hide content from accessibility and screenshots, or it may fail to generate a click event when you manually decline.
- Offer identification requires an **Accept** control and exactly one distinct readable dollar amount on screen. A blank payout, multiple dollar amounts, or uncertain payout means **no automatic action**. Some genuine offers will be skipped intentionally.
- **If only a visual X is present**, a manual one-time calibration plus explicitly enabled coordinate fallback may be needed; tapping may fail or hit an unintended control if Uber changes layouts.
- The app **does not** intercept Uber's network traffic, use an Uber API, use your credentials, or automate acceptance.
- On some Android versions, Uber's own notification sound may already have played **before** this app attempts to decline. This app **cannot suppress Uber's ringtone**.
- It is designed to inspect Uber's visible screen while **Uber Driver is foreground**. If Android routes incoming offers to a system notification, lock-screen UI, or another window, detection may not work.
- Android's silent mode, battery optimisation, restrictions, and screen changes may prevent alerts or declines. Phone battery settings may need adjustment. Do not operate the UI or configure rules while driving.
- This app is **not approved by Uber**, and automated interactions could have account or platform-policy consequences. Uber's Australian guidelines also say repeated consecutive declines may result in temporary offline status. Use at your own risk.
- If you want to avoid account-risky automation, leave **Auto-decline OFF** and use alerts/diagnostics only.
- The source code is intended for personal testing; Play Store publication involves additional policies and consent requirements for Accessibility Services.

## Data handling

- **Permissions:** Accessibility service, notification (optional), vibration.
- **No Internet permission.** Local ML Kit OCR does not require screenshots to be transmitted.
- **Storage:** Android private app preferences only, including threshold, calibration and last amount/action. Raw screenshots and OCR text are not saved.
- **Scope:** Uber Driver package only (`com.ubercab.driver`).

## Testing performed so far

The pure-Java `OfferDetector` has 14 host-executable test cases in `tests/OfferDetectorStandaloneTest.java`, covering dollar formats, thresholds, missing Accept, ambiguous money amounts and malformed input. Run from any JDK using:

```bash
javac -d tests/out app/src/main/java/com/personal/offerguardian/OfferDetector.java tests/OfferDetectorStandaloneTest.java
java -cp tests/out com.personal.offerguardian.OfferDetectorStandaloneTest
```

Android Studio compilation, screenshot OCR, accessibility control selection, Australian Uber UI calibration, notifications, and real declinations **still require on-device testing**. The software should not be relied on unattended until those tests pass.
