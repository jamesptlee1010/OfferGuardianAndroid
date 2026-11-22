package com.personal.offerguardian;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Private on-device Uber screen monitor. It does not use Uber APIs or credentials.
 * Detection path: trusted foreground (Uber Driver or Waze) -> fresh screenshot ->
 * automatic light/dark offer-card border + matching X -> OCR payout INSIDE the
 * same card -> existing rules. Waze auto-declines require two stable observations
 * AND verified Uber offer text; ambiguous screens never receive a gesture.
 * No legacy click-event learning, fixed tap coordinates, or automatic accepting.
 */
public final class OfferAccessibilityService extends AccessibilityService {
    private static final String UBER = OfferSurfacePolicy.UBER;
    private static final String WAZE = OfferSurfacePolicy.WAZE;
    // Faster Waze polling; second safety frame is deliberately scheduled shortly
    // after OCR, rather than waiting for another ordinary polling cycle.
    private static final long UBER_POLL_MS=1700;
    private static final long WAZE_POLL_MS=700;
    private static final long UBER_MIN_CAPTURE_MS=1450;
    private static final long WAZE_MIN_CAPTURE_MS=650;
    // Android limits repeated Accessibility screenshots (~333 ms in AOSP).
    // Give it a margin to avoid screenshot rate-limit failures.
    private static final long WAZE_CONFIRM_MIN_CAPTURE_MS=430;
    private static final long REPEAT_GUARD_MS = 90000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable monitorLoop = new Runnable() {
        @Override public void run() {
            try {
                SharedPreferences prefs=Prefs.of(OfferAccessibilityService.this);
                if (prefs.getBoolean(Prefs.MONITOR,false) &&
                        !prefs.getBoolean(Prefs.LEARNING,false)) {
                    if (!prefs.getBoolean(Prefs.VISUAL_X,false)) {
                        hideOverlay();clearPendingAcceptance();clearReadVerification();
                        status("Visual recognition is OFF. Turn on grey-X recognition in the app.");
                    } else if (isSupportedForeground()) {
                        scanIfDue();
                    } else {
                        hideOverlay();clearPendingAcceptance();clearReadVerification();
                    }
                } else {
                    hideOverlay();clearPendingAcceptance();clearReadVerification();
                }
            } catch (Exception e) {
                status("Monitor error: " + e.getClass().getSimpleName());
            } finally {
                handler.postDelayed(this, isWazeForeground() ? WAZE_POLL_MS : UBER_POLL_MS);
            }
        }
    };

    private TextRecognizer recognizer;
    private int[] template;
    private int[] darkTemplate;
    private final WazeFastVerifier wazeVerifier=new WazeFastVerifier();
    private final OfferReadVerifier readVerifier=new OfferReadVerifier();
    private final OfferReadVerifier correctionVerifier=new OfferReadVerifier();
    private long scanStartMs=0;
    private final Runnable fastVerifyRunnable=()->{
        if(isSupportedForeground() && (readVerifier.isPending() ||
                correctionVerifier.isPending() || (isWazeForeground()&&wazeVerifier.isPending())))
            scanIfDue();
    };
    private void scheduleFastVerify() {
        handler.removeCallbacks(fastVerifyRunnable);
        if(!(readVerifier.isPending() || correctionVerifier.isPending() ||
                (isWazeForeground()&&wazeVerifier.isPending())))return;
        long sinceCapture=SystemClock.elapsedRealtime()-lastCaptureAt;
        handler.postDelayed(fastVerifyRunnable,
                Math.max(15,WAZE_CONFIRM_MIN_CAPTURE_MS-sinceCapture+15));
    }
    private void resetWazeVerification() {
        wazeVerifier.reset();handler.removeCallbacks(fastVerifyRunnable);
    }
    private long lastCaptureAt=0;
    private boolean capturing=false;
    private double lastAmount=Double.NaN;
    private long lastDecisionAt=0;
    private int lastX=-1,lastY=-1;
    private int noOfferFrames=0;
    private long lastHistoryId=-1;
    // Avoid a disk read/write on the fast second frame when history is complete.
    private int cachedMinutes=0,cachedMetres=0;
    private String cachedPickup="",cachedDestination="";
    private OfferRuleEngine.Outcome lastOutcome=null;
    private boolean reviewAlerted=false;
    private OfferOverlay liveOverlay;
    private long pendingAcceptanceId=-1, pendingAcceptanceSince=0;
    private String pendingPickup="";
    private boolean autoDeclineSent=false;
    private final OfferAcceptanceEvidence.StableConfirmation acceptanceEvidence=
            new OfferAcceptanceEvidence.StableConfirmation();

    private void hideOverlay() {if(liveOverlay!=null)liveOverlay.hide();}
    private void clearReadVerification() {
        readVerifier.reset();correctionVerifier.reset();
        resetWazeVerification();
    }
    private void clearPendingAcceptance() {
        pendingAcceptanceId=-1;pendingAcceptanceSince=0;pendingPickup="";
        acceptanceEvidence.reset();
    }
    private void armPendingAcceptance() {
        if(lastHistoryId<=0 || autoDeclineSent || !isUberForeground() ||
                !Prefs.of(this).getBoolean(Prefs.ACCEPT_TRACKING,true))return;
        try(OfferHistoryStore store=new OfferHistoryStore(this)) {
            OfferHistoryStore.Item item=store.byId(lastHistoryId);
            if(item!=null && item.pickup!=null && !item.pickup.trim().isEmpty() &&
                    OfferHistoryStore.UNKNOWN.equals(item.outcome)) {
                pendingAcceptanceId=lastHistoryId;
                pendingAcceptanceSince=SystemClock.elapsedRealtime();
                pendingPickup=item.pickup;
                acceptanceEvidence.reset();
                status("Offer card disappeared. Checking for confirmed pickup assignment (not assuming acceptance).");
            }
        } catch(Exception e) {status("Could not start acceptance check: "+e.getClass().getSimpleName());}
    }

    private void examinePickupAfterOffer(Bitmap screenshot) {
        final long id=pendingAcceptanceId;
        if(id<=0 || !Prefs.of(this).getBoolean(Prefs.ACCEPT_TRACKING,true) ||
                SystemClock.elapsedRealtime()-pendingAcceptanceSince>30000) {
            clearPendingAcceptance();screenshot.recycle();capturing=false;return;
        }
        if(recognizer==null) recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(screenshot,0))
            .addOnSuccessListener(text -> {
                if(id!=pendingAcceptanceId || !isUberForeground())return;
                boolean found=OfferAcceptanceEvidence.confirmsActivePickup(text.getText(),pendingPickup);
                if(acceptanceEvidence.observe(found)) {
                    try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                        if(store.markAcceptedIfUnknown(id))
                            status("Accepted job CONFIRMED: same merchant and active pickup screen seen twice. Offer History updated.");
                        else status("Active pickup seen, but offer outcome was already set; no overwrite.");
                    } catch(Exception e) {status("Acceptance history update failed: "+e.getClass().getSimpleName());}
                    clearPendingAcceptance();
                } else if(found)status("Pickup assignment evidence seen once; awaiting second confirmation.");
            })
            .addOnFailureListener(e->status("Acceptance confirmation OCR failed: "+e.getClass().getSimpleName()))
            .addOnCompleteListener(task->{screenshot.recycle();capturing=false;});
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        status("Accessibility connected. Auto day/night recognition for Uber and Waze is active.");
        handler.removeCallbacks(monitorLoop);
        handler.post(monitorLoop);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event==null)return;
        OfferSurfacePolicy.Host host=OfferSurfacePolicy.classify(
                event.getPackageName()==null?null:event.getPackageName().toString());
        if(!OfferSurfacePolicy.shouldScan(host)) {
            if(!isSupportedForeground())hideOverlay();
            return;
        }
        if(!Prefs.of(this).getBoolean(Prefs.MONITOR,false)||
                !Prefs.of(this).getBoolean(Prefs.VISUAL_X,false))return;
        // Waze's navigation updates may not fire Uber events, so the periodic
        // screenshot poll remains the primary detection path.
        if(isSupportedForeground())scanIfDue();
    }

    private OfferSurfacePolicy.Host foregroundHost() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null)return OfferSurfacePolicy.Host.OTHER;
        return OfferSurfacePolicy.classify(root.getPackageName().toString());
    }
    private boolean isSupportedForeground() {
        return OfferSurfacePolicy.shouldScan(foregroundHost());
    }
    private boolean isUberForeground() {
        return foregroundHost()==OfferSurfacePolicy.Host.UBER;
    }
    private boolean isWazeForeground() {
        return foregroundHost()==OfferSurfacePolicy.Host.WAZE;
    }

    private void scanIfDue() {
        if (Build.VERSION.SDK_INT<30) {
            status("Android 11+ required for screen screenshots.");return;
        }
        long now=SystemClock.elapsedRealtime();
        boolean confirmationPending=readVerifier.isPending()||correctionVerifier.isPending()||
                (isWazeForeground()&&wazeVerifier.isPending());
        long minInterval=confirmationPending?WAZE_CONFIRM_MIN_CAPTURE_MS:
                (isWazeForeground()?WAZE_MIN_CAPTURE_MS:UBER_MIN_CAPTURE_MS);
        if(capturing || now-lastCaptureAt<minInterval)return;
        lastCaptureAt=now;
        scanStartMs=now;
        capturing=true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback() {
                @Override public void onFailure(int errorCode) {
                    capturing=false;
                    if(errorCode==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && wazeVerifier.isPending()) {
                        handler.removeCallbacks(fastVerifyRunnable);
                        handler.postDelayed(fastVerifyRunnable,500);
                        status("Screenshot rate-limited: safely retrying Waze confirmation; no tap.");
                    } else status("Screenshot permission / capture failed (Android code " +errorCode+").");
                }
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer hardware=result.getHardwareBuffer();
                    if (hardware==null) {
                        capturing=false;status("Screenshot returned no image buffer.");return;
                    }
                    Bitmap copy=null;
                    try {
                        Bitmap buffer=Bitmap.wrapHardwareBuffer(hardware,result.getColorSpace());
                        if (buffer!=null) copy=buffer.copy(Bitmap.Config.ARGB_8888,false);
                    } catch (Exception e) {
                        status("Could not read screenshot: "+e.getClass().getSimpleName());
                    } finally {hardware.close();}
                    if(copy==null) {capturing=false;return;}
                    processScreenshot(copy, scanStartMs, SystemClock.elapsedRealtime());
                }
            });
        } catch(Exception e) {
            capturing=false;
            status("Screenshot exception: "+e.getClass().getSimpleName());
        }
    }

    private void processScreenshot(Bitmap source,long scanStarted,long screenshotReady) {
        if (!isSupportedForeground() || !Prefs.of(this).getBoolean(Prefs.MONITOR,false)) {
            hideOverlay();clearPendingAcceptance();
            source.recycle();capturing=false;
            status("Uber/Waze no longer foreground. No action.");return;
        }
        int w=source.getWidth(),h=source.getHeight();
        if(w<350 || h<700 || Math.abs(w/(double)h-
                VisualDeclineDetector.WIDTH/(double)VisualDeclineDetector.HEIGHT)>.018) {
            hideOverlay();source.recycle();capturing=false;
            status("Screen aspect ratio changed; no tap ("+w+"x"+h+").");return;
        }
        try {
            if(template==null) {
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inScaled=false; // Always load exactly 41x41 on high-density phones.
                Bitmap ref=BitmapFactory.decodeResource(getResources(),R.raw.decline_x_reference,options);
                if(ref==null || ref.getWidth()!=41 || ref.getHeight()!=41)
                    throw new IllegalStateException("X template must be 41x41");
                template=new int[ref.getWidth()*ref.getHeight()];
                ref.getPixels(template,0,ref.getWidth(),0,0,ref.getWidth(),ref.getHeight());
                ref.recycle();
                Bitmap darkRef=BitmapFactory.decodeResource(getResources(),
                        R.raw.decline_x_reference_dark,options);
                if(darkRef==null||darkRef.getWidth()!=41||darkRef.getHeight()!=41)
                    throw new IllegalStateException("Dark X template must be 41x41");
                darkTemplate=new int[41*41];
                darkRef.getPixels(darkTemplate,0,41,0,0,41,41);
                darkRef.recycle();
            }
            Bitmap resized=Bitmap.createScaledBitmap(source,VisualDeclineDetector.WIDTH,
                    VisualDeclineDetector.HEIGHT,true);
            int[] pixels=new int[VisualDeclineDetector.WIDTH*VisualDeclineDetector.HEIGHT];
            resized.getPixels(pixels,0,VisualDeclineDetector.WIDTH,0,0,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT);
            if(resized!=source) resized.recycle();
            VisualDeclineDetector.Match visual=VisualDeclineDetector.locate(pixels,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT,
                    template,darkTemplate,41,41);
            final long visualReady=SystemClock.elapsedRealtime();
            if(!visual.found) {
                hideOverlay();clearReadVerification();
                ++noOfferFrames;
                // Losing the offer card does NOT mean Accept was pressed.
                // We only mark Accepted after the next screen confirms the SAME
                // merchant is now assigned as an active pickup, twice.
                if(noOfferFrames==1)armPendingAcceptance();
                if(noOfferFrames>=2) {
                    lastAmount=Double.NaN;
                    lastX=-1;lastY=-1;
                    lastHistoryId=-1;
                    cachedMinutes=0;cachedMetres=0;
                    cachedPickup="";cachedDestination="";
                    lastOutcome=null;
                    clearReadVerification();
                    reviewAlerted=false;
                    autoDeclineSent=false;
                }
                if(pendingAcceptanceId>0) {
                    examinePickupAfterOffer(source);
                } else {
                    source.recycle();capturing=false;
                    status("No supported offer card; overlay hidden. No action.");
                }
                return;
            }
            noOfferFrames=0;
            clearPendingAcceptance();
            status(String.format(Locale.US,"%s offer on %s: X (%d,%d), %.0f%%; reading payout...",
                    visual.theme,foregroundHost(),visual.x,visual.y,visual.confidence*100));
            if(recognizer==null) recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            // OCR only the verified Uber offer card, not Waze's navigation labels
            // or the map above it. Preserve absolute pixel offsets for parsing.
            final int left=Math.max(0,Math.round(20f*w/VisualDeclineDetector.WIDTH));
            final int top=Math.max(0,Math.round(
                    Math.max(0,visual.cardTop-28)*h/(float)VisualDeclineDetector.HEIGHT));
            final int right=Math.min(w,Math.round(674f*w/VisualDeclineDetector.WIDTH));
            final int bottom=Math.min(h,Math.round(1470f*h/VisualDeclineDetector.HEIGHT));
            final Bitmap frame=source;
            final Bitmap ocrImage;
            if(right-left>150 && bottom-top>190)
                ocrImage=Bitmap.createBitmap(frame,left,top,right-left,bottom-top);
            else ocrImage=frame;
            recognizer.process(InputImage.fromBitmap(ocrImage,0))
                .addOnSuccessListener(text -> {
                    try {onOcrReady(text,visual,w,h,left,top,scanStarted,screenshotReady,visualReady);} catch(Exception e) {
                        status("Price-reading exception: "+e.getClass().getSimpleName());
                    }
                })
                .addOnFailureListener(err ->
                        status("Screenshot OK, X found, but OCR failed: "+err.getClass().getSimpleName()))
                .addOnCompleteListener(task -> {
                    if(ocrImage!=frame)ocrImage.recycle();
                    frame.recycle();capturing=false;
                    if(wazeVerifier.isPending() || readVerifier.isPending() ||
                    correctionVerifier.isPending())scheduleFastVerify();
                });
        } catch(Exception e) {
            source.recycle();capturing=false;
            status("X detection exception: "+e.getClass().getSimpleName());
        }
    }

    private void onOcrReady(Text text,VisualDeclineDetector.Match visual,int width,int height,
                            int offsetX,int offsetY,long scanStarted,
                            long screenshotReady,long visualReady) {
        if(!isSupportedForeground()) {status("Uber/Waze left foreground while reading; no action.");return;}
        SharedPreferences prefs=Prefs.of(this);
        if(!prefs.getBoolean(Prefs.MONITOR,false)) return;
        List<OfferScreenEvaluator.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:text.getTextBlocks()) {
            for(Text.Line line:block.getLines()) {
                Rect box=line.getBoundingBox();
                if(box==null) continue;
                // ML Kit bounding boxes use the original screenshot's pixel dimensions.
                lines.add(new OfferScreenEvaluator.Line(line.getText(),
                    Math.round((box.left+offsetX)*VisualDeclineDetector.WIDTH/(float)width),
                    Math.round((box.top+offsetY)*VisualDeclineDetector.HEIGHT/(float)height),
                    Math.round((box.right+offsetX)*VisualDeclineDetector.WIDTH/(float)width),
                    Math.round((box.bottom+offsetY)*VisualDeclineDetector.HEIGHT/(float)height)));
            }
        }
        final String timing=OfferScanTiming.summary(scanStarted,screenshotReady,
                visualReady,SystemClock.elapsedRealtime());
        OfferScreenEvaluator.Assessment a=OfferScreenEvaluator.assess(visual,lines);
        if(!a.valid) {hideOverlay();clearReadVerification();
            status("X found, but " + a.reason + ". No tap. " + timing);return;}
        double payout=a.payout;
        long now=SystemClock.elapsedRealtime();
        // New offers are saved once. Including merchant identity prevents a
        // different offer with the same price and X location from bypassing
        // the first/second-frame verification requirement.
        OfferDetailsParser.Details parsed=OfferDetailsParser.parse(visual.y,lines);
        boolean sameOffer=lastHistoryId>0 && !Double.isNaN(lastAmount) &&
                Math.abs(lastAmount-payout)<.005 &&
                now-lastDecisionAt<REPEAT_GUARD_MS &&
                Math.abs(visual.x-lastX)<3 && Math.abs(visual.y-lastY)<3 &&
                cachedPickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim()
                    .equals(parsed.pickup.toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9]+"," ").trim());
        int minutes=parsed.minutes, metres=parsed.metres;
        boolean metricsWereCorrected=false;
        if(!sameOffer) {
            OfferReadVerifier.Result verified=readVerifier.observe(payout,parsed,visual.x,visual.y,now);
            if(verified.state!=OfferReadVerifier.State.CONFIRMED) {
                hideOverlay();
                if(verified.state==OfferReadVerifier.State.CHANGED ||
                        verified.state==OfferReadVerifier.State.UNVERIFIED)resetWazeVerification();
                // Seed Waze's existing two-screen safety verification on the FIRST
                // full reading, so the fast SECOND frame completes BOTH checks.
                if(isWazeForeground() &&
                        (verified.state==OfferReadVerifier.State.WAITING ||
                         verified.state==OfferReadVerifier.State.CHANGED) &&
                        prefs.getBoolean(Prefs.AUTO_DECLINE,false) &&
                        !prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
                    OfferRuleEngine.Decision preview=OfferRuleEngine.evaluate(
                            Prefs.rules(prefs),payout,minutes,metres);
                    if(preview.outcome==OfferRuleEngine.Outcome.DECLINE) {
                        wazeVerifier.observe(payout,visual.x,visual.y,visual.cardTop,
                                visual.confidence,
                                OfferScreenEvaluator.hasOfferIdentity(visual,lines),
                                parsed.pickup,now);
                    }
                }
                status(String.format(Locale.US,
                        "A$%.2f — %s; %s. No decision/tap. Raw time: %s | raw payout: %s | %s",
                        payout,verified.state,verified.reason,
                        parsed.rawDuration,a.rawPayout,timing));
                return;
            }
        } else {
            OfferExtractionGuard.Result safe=OfferExtractionGuard.check(parsed);
            if(safe.valid && cachedMinutes>0 && cachedMetres>0 &&
                    (cachedMinutes!=minutes || cachedMetres!=metres ||
                     correctionVerifier.isPending())) {
                // A later OCR read contradicts the stored metrics. Withhold
                // overlay/gesture until TWO matching new frames support a correction.
                hideOverlay();lastOutcome=OfferRuleEngine.Outcome.REVIEW;
                OfferReadVerifier.Result revision=correctionVerifier.observe(
                        payout,parsed,visual.x,visual.y,now);
                if(revision.state!=OfferReadVerifier.State.CONFIRMED) {
                    status("Existing offer metrics changed: " + revision.reason +
                            ". Manual review until confirmed; no tap. " + timing);
                    return;
                }
                try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                    store.correctVerified(lastHistoryId,parsed,a.rawPayout);
                } catch(Exception e) {status("Correction save failed: "+e.getClass().getSimpleName());}
                cachedMinutes=minutes;cachedMetres=metres;
                lastOutcome=null;
                metricsWereCorrected=true;
                resetWazeVerification();
            } else if(!safe.valid) {
                // Never replace two verified measurements with an incomplete frame.
                hideOverlay();lastOutcome=OfferRuleEngine.Outcome.REVIEW;
                status("Ignoring incomplete repeat OCR: "+safe.reason+". No new tap.");
                return;
            } else correctionVerifier.reset();
        }
        try {
            if(sameOffer) {
                // Most second Waze frames need NO SQLite work. Only backfill fields
                // if the first screenshot missed details and the new one supplies them.
                boolean missingFilled=(cachedMinutes<=0&&parsed.minutes>0) ||
                        (cachedMetres<=0&&parsed.metres>0) ||
                        (cachedPickup.isEmpty()&&!parsed.pickup.isEmpty()) ||
                        (cachedDestination.isEmpty()&&!parsed.destination.isEmpty());
                if(missingFilled) {
                    try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                        store.fillMissing(lastHistoryId,parsed);
                    }
                }
                if(cachedMinutes<=0)cachedMinutes=parsed.minutes;
                if(cachedMetres<=0)cachedMetres=parsed.metres;
                if(cachedPickup.isEmpty())cachedPickup=parsed.pickup;
                if(cachedDestination.isEmpty())cachedDestination=parsed.destination;
                minutes=cachedMinutes;metres=cachedMetres;
            } else {
                try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                    lastHistoryId=store.record((int)Math.round(payout*100),parsed,a.rawPayout);
                }
                cachedMinutes=minutes;cachedMetres=metres;
                cachedPickup=parsed.pickup;cachedDestination=parsed.destination;
            }
        } catch(Exception e) {
            hideOverlay();lastHistoryId=-1;
            status("History save failed: "+e.getClass().getSimpleName()+". Manual review; no tap.");
            return;
        }
        // When an OCR frame lacks time or distance, retry on the same visible
        // card. Do not lock it into the repeat guard until a final decision.
        if(sameOffer && !metricsWereCorrected &&
                lastOutcome!=OfferRuleEngine.Outcome.REVIEW &&
                !(isWazeForeground() && lastOutcome==OfferRuleEngine.Outcome.DECLINE &&
                        !autoDeclineSent)) {
            // Same visible offer: do not ring again or tap repeatedly. Keep the
            // already qualified overlay present, and respect any new OFF toggle.
            if(lastOutcome==OfferRuleEngine.Outcome.SHOW) {
                OfferRuleEngine.Decision repeat=OfferRuleEngine.evaluate(
                        Prefs.rules(prefs),payout,minutes,metres);
                if(OfferOverlayPolicy.shouldShow(repeat,
                        prefs.getBoolean(Prefs.OVERLAY_ENABLED,true),
                        prefs.getBoolean(Prefs.MONITOR,false),isSupportedForeground(),true)) {
                    if(liveOverlay==null)liveOverlay=new OfferOverlay(this);
                    liveOverlay.show(repeat.hourly,repeat.perKm);
                } else hideOverlay();
            } else hideOverlay();
            return;
        }
        if(!sameOffer) {
            lastAmount=payout;lastDecisionAt=now;lastX=visual.x;lastY=visual.y;
            lastOutcome=null;reviewAlerted=false;autoDeclineSent=false;
            // Do not clear a first-frame Waze verification here: the matching
            // second frame is still needed by the Waze decline policy below.
        }
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,(float)payout)
                .putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        OfferRuleEngine.Decision decision=OfferRuleEngine.evaluate(Prefs.rules(prefs),payout,minutes,metres);
        String rates=String.format(Locale.US," (time: %s; distance: %s; A$/hr: %s; A$/km: %s)",
                minutes>0?minutes+" min":"unknown",
                metres>0?String.format(Locale.US,"%.1f km",metres/1000.0):"unknown",
                Double.isNaN(decision.hourly)?"unknown":String.format(Locale.US,"%.2f",decision.hourly),
                Double.isNaN(decision.perKm)?"unknown":String.format(Locale.US,"%.2f",decision.perKm));
        if(decision.outcome==OfferRuleEngine.Outcome.REVIEW) {
            hideOverlay();
            lastOutcome=OfferRuleEngine.Outcome.REVIEW;
            if(!reviewAlerted && prefs.getBoolean(Prefs.ALERT,true)) {
                reviewAlerted=true;
                OfferAlert.signal(this,payout);
            }
            status(String.format(Locale.US,
                    "A$%.2f — MANUAL REVIEW, no tap. %s%s",payout,decision.reason,rates));
            return;
        }
        lastOutcome=decision.outcome;
        if(decision.outcome==OfferRuleEngine.Outcome.SHOW) {
            boolean canShow=OfferOverlayPolicy.shouldShow(decision,
                    prefs.getBoolean(Prefs.OVERLAY_ENABLED,true),
                    prefs.getBoolean(Prefs.MONITOR,false),isSupportedForeground(),true);
            boolean visible=false;
            if(canShow) {
                if(liveOverlay==null)liveOverlay=new OfferOverlay(this);
                visible=liveOverlay.show(decision.hourly,decision.perKm);
            } else hideOverlay();
            final String overlayStatus=visible ? "overlay shown" :
                    canShow ? "overlay could not display" : "overlay disabled";
            if(prefs.getBoolean(Prefs.ALERT,true)) {
                boolean started=OfferAlert.signal(this,payout);
                status(String.format(Locale.US,
                        "A$%.2f — QUALIFIES; %s. %s; %s; no tap.%s",
                        payout,decision.reason,overlayStatus,
                        started?"Independent alert started":"Sound could not start",rates+" | "+timing));
            } else status(String.format(Locale.US,
                    "A$%.2f — QUALIFIES; %s. %s; alerts off; no tap.%s",
                    payout,decision.reason,overlayStatus,rates+" | "+timing));
            return;
        }
        hideOverlay();
        if(prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status(String.format(Locale.US,"TEST ONLY: A$%.2f — FAILS rules; %s. X at (%d,%d), %.0f%%. No tap.%s",
                    payout,decision.reason,visual.x,visual.y,visual.confidence*100,rates+" | "+timing));
            return;
        }
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)) {
            status(String.format(Locale.US,"A$%.2f — FAILS rules; %s. Auto-decline OFF. No tap.%s",
                    payout,decision.reason,rates+" | "+timing));
            return;
        }
        if(!prefs.getBoolean(Prefs.VISUAL_X,true) || !isSupportedForeground()) {
            status("Visual monitoring or Uber/Waze foreground not confirmed; no tap.");return;
        }
        OfferSurfacePolicy.Host host=foregroundHost();
        boolean reliableOfferText=OfferScreenEvaluator.hasOfferIdentity(visual,lines);
        String verifyTiming="";
        if(host==OfferSurfacePolicy.Host.WAZE) {
            long firstEvidenceAt=wazeVerifier.firstAtMs();
            if(!wazeVerifier.observe(payout,visual.x,visual.y,visual.cardTop,
                    visual.confidence,reliableOfferText,parsed.pickup,now)) {
                status(String.format(Locale.US,
                    "A$%.2f — Waze: %s; merchant %s, text %s, X %.0f%%. No tap yet. %s",
                    payout,wazeVerifier.isPending()?"fast verification queued":"verification unavailable (manual review)",
                    parsed.pickup.isEmpty()?"unreadable":parsed.pickup,
                    reliableOfferText?"verified":"not verified",
                    visual.confidence*100,timing));
                return;
            }
            if(firstEvidenceAt>0)verifyTiming=" | Waze confirmation "+
                    (now-firstEvidenceAt)+"ms";
        } else resetWazeVerification();
        if(!OfferSurfacePolicy.allowDecline(host,true,reliableOfferText,
                host==OfferSurfacePolicy.Host.WAZE?2:1,visual.confidence)) {
            status("Offer verification failed; no tap. " + timing);return;
        }
        if(SystemClock.elapsedRealtime()-lastCaptureAt>1900) {
            status("OCR took too long; screenshot is stale, so no tap.");return;
        }
        // The only gesture target comes from the freshly verified X on THIS screenshot.
        float x=visual.x*width/(float)VisualDeclineDetector.WIDTH;
        float y=visual.y*height/(float)VisualDeclineDetector.HEIGHT;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,90)).build();
        final long declinedHistoryId=lastHistoryId;
        final String finalTiming=timing+verifyTiming;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                status(String.format(Locale.US,
                        "A$%.2f: X gesture completed. Uber decline NOT independently confirmed. %s",
                        payout,finalTiming));
            }
            @Override public void onCancelled(GestureDescription gesture) {
                status(String.format(Locale.US,
                        "A$%.2f: X gesture cancelled; decline not confirmed. %s",
                        payout,finalTiming));
            }
        },null);
        if(accepted) {
            autoDeclineSent=true;
            try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                store.markDeclineAttempt(declinedHistoryId);
            } catch(Exception ignored) { /* Gesture status remains in diagnostics. */ }
        }
        status(String.format(Locale.US,accepted?
                "A$%.2f: X tap dispatched (%s, %s); Uber decline unconfirmed. %s":
                "A$%.2f: Android rejected X gesture; no tap. (%s, %s). %s",
                payout,host,visual.theme,finalTiming));
    }

    private void status(String msg) {
        SharedPreferences p=Prefs.of(this);
        String old=p.getString(Prefs.LIVE_STATUS,"");
        long time=System.currentTimeMillis();
        SharedPreferences.Editor edit=p.edit().putString(Prefs.LIVE_STATUS,msg)
                .putLong(Prefs.LIVE_TIME,time);
        if(!msg.equals(old)) {
            String stamp=DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(time));
            String previous=p.getString(Prefs.DIAG_HISTORY,"");
            String[] entries=previous.split("\n");
            StringBuilder b=new StringBuilder(stamp).append(" | ").append(msg);
            for(int i=0;i<Math.min(entries.length,12);i++) {
                if(!entries[i].isEmpty()) b.append('\n').append(entries[i]);
            }
            edit.putString(Prefs.DIAG_HISTORY,b.toString());
        }
        edit.apply();
    }

    @Override public void onInterrupt() {
        hideOverlay();clearPendingAcceptance();clearReadVerification();
        status("Accessibility interrupted. Monitoring paused.");
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(monitorLoop);
        hideOverlay();clearPendingAcceptance();clearReadVerification();
        if(recognizer!=null) recognizer.close();
        status("Accessibility service stopped. Re-enable if monitoring is needed.");
        super.onDestroy();
    }
}
