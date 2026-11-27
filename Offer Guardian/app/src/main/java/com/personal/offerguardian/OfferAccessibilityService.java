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
import android.view.accessibility.AccessibilityWindowInfo;

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
 * Detection path: Uber Driver foreground -> fresh screenshot -> automatic
 * light/dark offer-card border + matching X -> OCR payout INSIDE the same
 * card -> existing rules. Ambiguous screens never receive a gesture.
 * No legacy click-event learning, fixed tap coordinates, or automatic accepting.
 */
public final class OfferAccessibilityService extends AccessibilityService {
    private static OfferAccessibilityService connected;
    static OfferAccessibilityService connectedService(){return connected;}
    private static final String UBER = OfferSurfacePolicy.UBER;
    // One Uber-only idle polling interval; fast retries run only during
    // active offer verification, never while another app is foreground.
    private static final long UBER_POLL_MS=900;
    private static final long UBER_MIN_CAPTURE_MS=650;
    // Android limits repeated Accessibility screenshots (~333 ms in AOSP).
    // Give it a margin for bounded, offer-specific confirmation retries.
    private static final long FAST_CONFIRM_MIN_CAPTURE_MS=430;
    private static final long REPEAT_GUARD_MS = 90000;
    // Long enough for normal ML Kit and screenshot work, short enough to recover
    // when Android silently drops a screenshot/OCR completion callback.
    private static final long CAPTURE_TIMEOUT_MS = 8000;
    private static final long HEARTBEAT_MS = 45000;
    private final OfferScanWatchdog watchdog = new OfferScanWatchdog();
    private long lastHeartbeatAt = 0;
    private int consecutiveCaptureErrors = 0;
    private long nextCaptureAfter = 0;
    private int screenshotCount = 0;
    private String lastForegroundReport = "";
    private long lastForegroundReportAt = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable monitorLoop = new Runnable() {
        @Override public void run() {
            try {
                final long now = SystemClock.elapsedRealtime();
                recoverStalledCapture(now);
                SharedPreferences prefs=Prefs.of(OfferAccessibilityService.this);
                if (prefs.getBoolean(Prefs.MONITOR,false) &&
                        !prefs.getBoolean(Prefs.LEARNING,false)) {
                    if (!prefs.getBoolean(Prefs.VISUAL_X,false)) {
                        hideOverlay();clearPendingAcceptance();clearReadVerification();
                        status("Visual recognition is OFF. Turn on grey-X recognition in the app.");
                    } else if (isSupportedForeground()) {
                        reportHeartbeat(now, "Uber foreground; monitoring active");
                        scanIfDue();
                    } else {
                        hideOverlay();clearPendingAcceptance();clearReadVerification();
                        reportForegroundUnavailable(now);
                        reportHeartbeat(now, "Uber not confirmed; waiting for supported foreground");
                    }
                } else {
                    hideOverlay();clearPendingAcceptance();clearReadVerification();
                    reportHeartbeat(now, !prefs.getBoolean(Prefs.MONITOR,false) ?
                            "Monitoring switched OFF" : "Learning mode enabled; no scan");
                }
            } catch (Exception e) {
                status("Monitor error: " + e.getClass().getSimpleName());
            } finally {
                handler.postDelayed(this, UBER_POLL_MS);
            }
        }
    };

    private TextRecognizer recognizer;
    private final java.util.concurrent.ExecutorService visualWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean visualBusy=false,gestureBusy=false;
    private int ocrInFlight=0;
    private int[] reusablePixels;
    private int recentCardTop=-1;
    private final UberDeclineRetry retries=new UberDeclineRetry();
    private String lastAlertKey="";
    private long lastAlertAt=0;
    private int payoutFailures=0;
    private long gestureGeneration=0;
    private boolean alertOnce(double payout,OfferDetailsParser.Details d){
        String key=Math.round(payout*100)+"/"+d.minutes+"/"+d.metres+"/"+d.deliveryCount+"/"+d.type;
        long now=SystemClock.elapsedRealtime();
        if(key.equals(lastAlertKey)&&now-lastAlertAt<60000)return false;
        lastAlertKey=key;lastAlertAt=now;return OfferAlert.signal(this,payout);
    }
    private com.google.android.gms.tasks.Task<Text> submitOcr(Bitmap bitmap){
        ocrInFlight++;
        try{return recognizer.process(InputImage.fromBitmap(bitmap,0));}
        catch(RuntimeException e){ocrInFlight--;throw e;}
    }
    private int[] template;
    private int[] darkTemplate;
    private final OfferReadVerifier readVerifier=new OfferReadVerifier();
    private final OfferReadVerifier correctionVerifier=new OfferReadVerifier();
    private final OfferBoundVerifier hourBoundVerifier=new OfferBoundVerifier();
    private final OfferCountVerifier countVerifier=new OfferCountVerifier();
    private final OfferRetryBudget retryBudget=new OfferRetryBudget();
    private boolean fastRetryPending=false;
    private long scanStartMs=0;
    private final Runnable fastVerifyRunnable=()->{
        if(isSupportedForeground() && (readVerifier.isPending() ||
                correctionVerifier.isPending() || hourBoundVerifier.isPending() ||
                countVerifier.isPending() ||
                fastRetryPending))
            scanIfDue();
    };
    private void scheduleFastVerify() {
        handler.removeCallbacks(fastVerifyRunnable);
        if(!(readVerifier.isPending() || correctionVerifier.isPending() ||
                hourBoundVerifier.isPending() || countVerifier.isPending() || fastRetryPending))return;
        long sinceCapture=SystemClock.elapsedRealtime()-lastCaptureAt;
        handler.postDelayed(fastVerifyRunnable,
                Math.max(15,FAST_CONFIRM_MIN_CAPTURE_MS-sinceCapture+15));
    }
    private long lastCaptureAt=0;
    // The watchdog owns the in-flight token; there is no unbounded capturing flag.
    private double lastAmount=Double.NaN;
    private long lastDecisionAt=0;
    private int lastX=-1,lastY=-1;
    private int noOfferFrames=0;
    private long lastHistoryId=-1;
    // Avoid a disk read/write on the fast second frame when history is complete.
    private int cachedMinutes=0,cachedMetres=0,cachedDeliveryCount=0;
    private String cachedPickup="",cachedDestination="";
    private OfferRuleEngine.Outcome lastOutcome=null;
    private boolean reviewAlerted=false;
    private OfferOverlay liveOverlay;
    private long pendingAcceptanceId=-1, pendingAcceptanceSince=0;
    private String pendingPickup="";
    private boolean autoDeclineSent=false;
    private boolean confirmedHourBound=false;
    private boolean confirmedCountDecline=false;
    private long lastRulesGeneration=-1;
    private int confirmedLowerHours=0;
    private final OfferAcceptanceEvidence.StableConfirmation acceptanceEvidence=
            new OfferAcceptanceEvidence.StableConfirmation();

    private void hideOverlay() {if(liveOverlay!=null)liveOverlay.hide();}
    private void clearReadVerification() {
        readVerifier.reset();correctionVerifier.reset();hourBoundVerifier.reset();countVerifier.reset();
        fastRetryPending=false;retryBudget.reset();
        handler.removeCallbacks(fastVerifyRunnable);
    }
    private void clearPendingAcceptance() {
        pendingAcceptanceId=-1;pendingAcceptanceSince=0;pendingPickup="";
        acceptanceEvidence.reset();
    }
    private void armPendingAcceptance() {
        if(lastHistoryId<=0 || autoDeclineSent || retries.attempts()>0 || !isUberForeground() ||
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

    private void reportHeartbeat(long now, String detail) {
        if(now - lastHeartbeatAt < HEARTBEAT_MS) return;
        lastHeartbeatAt = now;
        Prefs.of(this).edit()
                .putLong(Prefs.HEALTH_HEARTBEAT_TIME,System.currentTimeMillis())
                .putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,true)
                .putInt(Prefs.HEALTH_RECOVERIES,watchdog.recoveries())
                .putInt(Prefs.HEALTH_SCREENSHOTS,screenshotCount)
                .putString(Prefs.HEALTH_STATE,detail)
                .apply();
        long last = watchdog.lastFinishedAt();
        String lastAge = last == 0 ? "none yet" : (Math.max(0L, now-last)/1000)+"s ago";
        status("Monitor heartbeat: "+detail+"; screenshots="+screenshotCount+
                "; last finished="+lastAge+"; busy="+watchdog.busy()+
                "; watchdog recoveries="+watchdog.recoveries());
    }

    private void reportForegroundUnavailable(long now) {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        String description=root==null ? "no active window root" :
                root.getPackageName()==null ? "active window has no package" :
                "foreground="+root.getPackageName();
        // The device can report a null root intermittently. Do not guess that
        // Uber is foreground and never scan/tap based only on a stale event.
        if(!description.equals(lastForegroundReport) ||
                now-lastForegroundReportAt >= HEARTBEAT_MS) {
            lastForegroundReport=description;
            lastForegroundReportAt=now;
            status("Scanner waiting: Uber Driver not confirmed ("+description+"). No tap.");
        }
    }

    private boolean ownsFreshCapture(long token) {
        final long now=SystemClock.elapsedRealtime();
        if(watchdog.owns(token) && watchdog.overdue(now,CAPTURE_TIMEOUT_MS))
            recoverStalledCapture(now);
        return watchdog.safeToProcess(token,now,CAPTURE_TIMEOUT_MS);
    }

    private void finishCapture(long token) {
        watchdog.finish(token,SystemClock.elapsedRealtime());
    }

    private void recordCaptureFailure(long token, String message) {
        if(!watchdog.owns(token)) return;
        finishCapture(token);
        consecutiveCaptureErrors++;
        // Recover automatically but avoid hammering a device that repeatedly
        // refuses screenshot access. Normal scanning resumes after the backoff.
        if(consecutiveCaptureErrors >= 3) {
            nextCaptureAfter=SystemClock.elapsedRealtime()+
                    Math.min(8000L,1000L*consecutiveCaptureErrors);
        }
        clearReadVerification();
        status(message+"; failures="+consecutiveCaptureErrors+
                "; auto-retry "+(consecutiveCaptureErrors>=3 ? "after backoff" : "on next scan"));
    }

    private void recoverStalledCapture(long now) {
        if(!watchdog.overdue(now,CAPTURE_TIMEOUT_MS)) return;
        final long elapsed=watchdog.elapsed(now);
        final long expired=watchdog.recover(now,CAPTURE_TIMEOUT_MS);
        if(expired==0) return;
        // Invalidate all partial evidence: two NEW, matching fresh frames are
        // required after recovery before any decline gesture is considered.
        clearReadVerification();
        clearPendingAcceptance();
        hideOverlay();
        consecutiveCaptureErrors++;
        nextCaptureAfter=now+Math.min(5000L,1000L*consecutiveCaptureErrors);
        Prefs.of(this).edit().putInt(Prefs.HEALTH_RECOVERIES,watchdog.recoveries()).apply();
        status("SCANNER WATCHDOG RECOVERED stalled capture #"+expired+
                " after "+elapsed+"ms. Old OCR/tap discarded; fresh verification required."+
                " Recoveries="+watchdog.recoveries());
    }

    private void examinePickupAfterOffer(Bitmap screenshot,long token) {
        final long id=pendingAcceptanceId;
        if(id<=0 || !Prefs.of(this).getBoolean(Prefs.ACCEPT_TRACKING,true) ||
                SystemClock.elapsedRealtime()-pendingAcceptanceSince>30000) {
            clearPendingAcceptance();screenshot.recycle();finishCapture(token);return;
        }
        if(recognizer==null) recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        submitOcr(screenshot)
            .addOnSuccessListener(text -> {
                if(!ownsFreshCapture(token) || id!=pendingAcceptanceId || !isUberForeground())return;
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
            .addOnFailureListener(e->{if(ownsFreshCapture(token))
                status("Acceptance confirmation OCR failed: "+e.getClass().getSimpleName());})
            .addOnCompleteListener(task->{ocrInFlight=Math.max(0,ocrInFlight-1);screenshot.recycle();finishCapture(token);});
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected=this;
        watchdog.invalidate();
        consecutiveCaptureErrors=0;nextCaptureAfter=0;lastHeartbeatAt=0;
        Prefs.of(this).edit().putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,true)
                .putLong(Prefs.HEALTH_HEARTBEAT_TIME,System.currentTimeMillis())
                .putString(Prefs.HEALTH_STATE,"Accessibility connected")
                .putInt(Prefs.HEALTH_RECOVERIES,0).apply();
        status("Accessibility connected. v3.2.0 Touch Diagnostics | Uber Driver-only monitoring + scanner watchdog active.");
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
        // Uber accessibility events supplement the throttled screenshot poll.
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

    private void scanIfDue() {
        if (Build.VERSION.SDK_INT<30) {
            status("Android 11+ required for screen screenshots.");return;
        }
        long now=SystemClock.elapsedRealtime();
        boolean confirmationPending=readVerifier.isPending()||correctionVerifier.isPending()||
                hourBoundVerifier.isPending()||countVerifier.isPending()||fastRetryPending;
        long minInterval=confirmationPending?FAST_CONFIRM_MIN_CAPTURE_MS:UBER_MIN_CAPTURE_MS;
        recoverStalledCapture(now);
        if(ocrInFlight>=2 && !watchdog.busy()){status("OCR backlog: waiting for outstanding reads; restart accessibility if this persists.");return;}
        if(watchdog.busy() || visualBusy || gestureBusy || now<nextCaptureAfter ||
                now-lastCaptureAt<minInterval)return;
        fastRetryPending=false;
        lastCaptureAt=now;
        scanStartMs=now;
        final long token=watchdog.begin(now);
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback() {
                @Override public void onFailure(int errorCode) {
                    if(!ownsFreshCapture(token))return;
                    recordCaptureFailure(token,"Screenshot permission / capture failed (Android code " +errorCode+")");
                    // A new scan will retry after the Android screenshot rate limit.
                    // Never reuse half-completed verification from the failed capture.
                }
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer hardware=result.getHardwareBuffer();
                    if(!ownsFreshCapture(token)) {
                        if(hardware!=null)hardware.close();
                        return;
                    }
                    if (hardware==null) {
                        recordCaptureFailure(token,"Screenshot returned no image buffer");return;
                    }
                    Bitmap copy=null;
                    try {
                        Bitmap buffer=Bitmap.wrapHardwareBuffer(hardware,result.getColorSpace());
                        if (buffer!=null) {try{copy=buffer.copy(Bitmap.Config.ARGB_8888,false);}finally{buffer.recycle();}}
                    } catch (Exception e) {
                        status("Could not read screenshot: "+e.getClass().getSimpleName());
                    } finally {hardware.close();}
                    if(copy==null) {
                        recordCaptureFailure(token,"Could not copy screenshot buffer");return;
                    }
                    // Successful screenshot capture. A later OCR timeout still
                    // triggers watchdog recovery if a listener never completes.
                    consecutiveCaptureErrors=0;
                    nextCaptureAfter=0;
                    screenshotCount++;
                    processScreenshot(copy, now, SystemClock.elapsedRealtime(),token);
                }
            });
        } catch(Exception e) {
            recordCaptureFailure(token,"Screenshot exception: "+e.getClass().getSimpleName());
        }
    }

    private void processScreenshot(Bitmap source,long scanStarted,long screenshotReady,long token) {
        if(!ownsFreshCapture(token)) {source.recycle();return;}
        if (!isSupportedForeground() || !Prefs.of(this).getBoolean(Prefs.MONITOR,false)) {
            hideOverlay();clearPendingAcceptance();
            source.recycle();finishCapture(token);
            status("Uber Driver no longer foreground. No action.");return;
        }
        int w=source.getWidth(),h=source.getHeight();
        if(w<350 || h<700 || Math.abs(w/(double)h-
                VisualDeclineDetector.WIDTH/(double)VisualDeclineDetector.HEIGHT)>.018) {
            hideOverlay();source.recycle();finishCapture(token);
            status("Screen aspect ratio changed; no tap ("+w+"x"+h+").");return;
        }
        visualBusy=true;
        final int priorTop=recentCardTop;
        try {visualWorker.execute(()->{
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
            if(reusablePixels==null)reusablePixels=new int[VisualDeclineDetector.WIDTH*VisualDeclineDetector.HEIGHT];
            int[] pixels=reusablePixels;
            resized.getPixels(pixels,0,VisualDeclineDetector.WIDTH,0,0,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT);
            if(resized!=source) resized.recycle();
            VisualDeclineDetector.Match visual=VisualDeclineDetector.locateNear(pixels,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT,
                    template,darkTemplate,41,41,priorTop);
            handler.post(()->{visualBusy=false;processVisual(source,scanStarted,screenshotReady,token,visual,w,h);});
            } catch(Exception e){handler.post(()->{visualBusy=false;source.recycle();finishCapture(token);status("Visual worker failed: "+e.getClass().getSimpleName());});}
        });}catch(RuntimeException e){visualBusy=false;source.recycle();finishCapture(token);}
    }
    private void processVisual(Bitmap source,long scanStarted,long screenshotReady,long token,VisualDeclineDetector.Match visual,int w,int h){
        if(!ownsFreshCapture(token)||!isSupportedForeground()){source.recycle();finishCapture(token);return;}
        try {
            if(visual.found)recentCardTop=visual.cardTop;else recentCardTop=-1;
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
                    autoDeclineSent=false;retries.reset();
                    confirmedHourBound=false;confirmedLowerHours=0;
                }
                if(pendingAcceptanceId>0) {
                    examinePickupAfterOffer(source,token);
                } else {
                    source.recycle();finishCapture(token);
                    status("No supported offer card; overlay hidden. No action.");
                }
                return;
            }
            noOfferFrames=0;
            clearPendingAcceptance();
            status(String.format(Locale.US,"%s offer on %s: X (%d,%d), %.0f%%; reading payout...",
                    visual.theme,foregroundHost(),visual.x,visual.y,visual.confidence*100));
            if(recognizer==null) recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            // OCR only the verified Uber offer card. Preserve absolute pixel offsets.
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
            final Bitmap adjusted;
            if(payoutFailures>0&&payoutFailures%2==1){
                adjusted=Bitmap.createBitmap(ocrImage.getWidth(),ocrImage.getHeight(),Bitmap.Config.ARGB_8888);
                android.graphics.ColorMatrix contrast=new android.graphics.ColorMatrix(new float[]{1.5f,0,0,0,-64,0,1.5f,0,0,-64,0,0,1.5f,0,-64,0,0,0,1,0});
                android.graphics.Paint paint=new android.graphics.Paint();paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(contrast));
                new android.graphics.Canvas(adjusted).drawBitmap(ocrImage,0,0,paint);
            }else if(payoutFailures>=2&&ocrImage.getWidth()<=1000&&ocrImage.getHeight()<=1600){
                adjusted=Bitmap.createScaledBitmap(ocrImage,Math.round(ocrImage.getWidth()*1.25f),Math.round(ocrImage.getHeight()*1.25f),true);
            }else adjusted=ocrImage;
            final float ocrScaleX=adjusted.getWidth()/(float)ocrImage.getWidth(),ocrScaleY=adjusted.getHeight()/(float)ocrImage.getHeight();
            final com.google.android.gms.tasks.Task<Text> submitted;
            try{submitted=submitOcr(adjusted);}catch(RuntimeException e){if(adjusted!=ocrImage)adjusted.recycle();if(ocrImage!=frame)ocrImage.recycle();throw e;}
            submitted
                .addOnSuccessListener(text -> {
                    if(!ownsFreshCapture(token)) return;
                    try {onOcrReady(text,visual,w,h,ocrImage==frame?0:left,ocrImage==frame?0:top,ocrScaleX,ocrScaleY,scanStarted,screenshotReady,visualReady);} catch(Exception e) {
                        status("Price-reading exception: "+e.getClass().getSimpleName());
                    }
                })
                .addOnFailureListener(err -> {
                    if(ownsFreshCapture(token))
                        status("Screenshot OK, X found, but OCR failed: "+err.getClass().getSimpleName());
                })
                .addOnCompleteListener(task -> {
                    ocrInFlight=Math.max(0,ocrInFlight-1);if(adjusted!=ocrImage)adjusted.recycle();
                    if(ocrImage!=frame)ocrImage.recycle();
                    frame.recycle();
                    boolean wasActive=watchdog.owns(token);
                    finishCapture(token);
                    if(wasActive && (readVerifier.isPending() ||
                    correctionVerifier.isPending() || hourBoundVerifier.isPending() ||
                countVerifier.isPending() ||
                    fastRetryPending))scheduleFastVerify();
                });
        } catch(Exception e) {
            source.recycle();finishCapture(token);
            status("X detection exception: "+e.getClass().getSimpleName());
        }
    }

    private void onOcrReady(Text text,VisualDeclineDetector.Match visual,int width,int height,
                            int offsetX,int offsetY,float ocrScaleX,float ocrScaleY,long scanStarted,
                            long screenshotReady,long visualReady) {
        if(!isSupportedForeground()) {status("Uber Driver left foreground while reading; no action.");return;}
        SharedPreferences prefs=Prefs.of(this);
        if(!prefs.getBoolean(Prefs.MONITOR,false)) return;
        List<OfferScreenEvaluator.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:text.getTextBlocks()) {
            for(Text.Line line:block.getLines()) {
                Rect box=line.getBoundingBox();
                if(box==null) continue;
                // ML Kit bounding boxes use the original screenshot's pixel dimensions.
                lines.add(new OfferScreenEvaluator.Line(line.getText(),
                    Math.round((box.left/ocrScaleX+offsetX)*VisualDeclineDetector.WIDTH/(float)width),
                    Math.round((box.top/ocrScaleY+offsetY)*VisualDeclineDetector.HEIGHT/(float)height),
                    Math.round((box.right/ocrScaleX+offsetX)*VisualDeclineDetector.WIDTH/(float)width),
                    Math.round((box.bottom/ocrScaleY+offsetY)*VisualDeclineDetector.HEIGHT/(float)height)));
            }
        }
        final String timing=OfferScanTiming.summary(scanStarted,screenshotReady,
                visualReady,SystemClock.elapsedRealtime());
        OfferScreenEvaluator.Assessment a=OfferScreenEvaluator.assess(visual,lines);
        if(!a.valid) {
            payoutFailures++;hideOverlay();
            requestFastRetry(visual);
            status("X found, but " + a.reason +
                    ". " + retryStatus() + ". No tap. " + timing);return;
        }
        payoutFailures=0;
        double payout=a.payout;
        long now=SystemClock.elapsedRealtime();
        // New offers are saved once. Including merchant identity prevents a
        // different offer with the same price and X location from bypassing
        // the first/second-frame verification requirement.
        OfferDetailsParser.Details parsed=OfferDetailsParser.parse(visual.y,lines);
        long generation=prefs.getLong(Prefs.TIERED_GENERATION,0);
        if(lastRulesGeneration>=0 && lastRulesGeneration!=generation) {
            // A save during an active offer must invalidate the old category
            // verdict; never re-use a cached SHOW or DECLINE from old settings.
            clearReadVerification();hideOverlay();lastHistoryId=-1;
            lastOutcome=null;lastAmount=Double.NaN;autoDeclineSent=false;
            cachedMinutes=0;cachedMetres=0;cachedDeliveryCount=0;
            cachedPickup="";cachedDestination="";
            confirmedHourBound=false;confirmedCountDecline=false;
        }
        lastRulesGeneration=generation;
        boolean tieredMode=Prefs.tieredEnabled(prefs);
        TieredOfferRules tierRules=tieredMode?Prefs.tieredRules(prefs):null;
        // Selecting the category precedes every payout/rate decision. Never
        // infer an absent count as a solo, or let global minimum deliveries
        // override a tiered solo offer.
        if(tieredMode) {
            if(parsed.deliveryCount<=0) {
                hideOverlay();readVerifier.reset();countVerifier.reset();
                requestFastRetry(visual);
                status("Tiered rules: delivery count unreadable, retrying. No decline/tap. "+retryStatus());
                return;
            }
            if(parsed.deliveryCount>3 || !tierRules.enabled(parsed.deliveryCount)) {
                hideOverlay();clearReadVerification();
                status("Tiered rules: "+tierRules.evaluate(payout,parsed.minutes,parsed.metres,
                        parsed.deliveryCount).reason+". No tap. "+timing);
                return;
            }
            if(!tierRules.valid()) {
                hideOverlay();clearReadVerification();
                status("Tiered rules invalid; manual review, no tap.");return;
            }
        }
        OfferRuleEngine.Config activeRules=tieredMode?
                tierRules.configFor(parsed.deliveryCount):Prefs.rules(prefs);
        OfferRuleEngine.Decision diagnostic=OfferRuleEngine.evaluate(activeRules,payout,parsed.minutes,parsed.metres,parsed.deliveryCount);
        prefs.edit().putString("uber_last_rule_v30",OfferScreenEvaluator.offerKind(visual,lines)+" | A$"+payout+" | raw ["+a.rawPayout+" / "+parsed.rawDuration+"] | orders="+parsed.deliveryCount+" | "+diagnostic.outcome+" | "+diagnostic.reason).apply();
        boolean countRequired=tieredMode || activeRules.deliveriesOn;
        boolean sameOffer=lastHistoryId>0 && !Double.isNaN(lastAmount) &&
                Math.abs(lastAmount-payout)<.005 &&
                now-lastDecisionAt<REPEAT_GUARD_MS &&
                (cachedMinutes<=0||cachedMinutes==parsed.minutes) &&
                (cachedMetres<=0||cachedMetres==parsed.metres) &&
                Math.abs(visual.x-lastX)<3 && Math.abs(visual.y-lastY)<3 &&
                cachedPickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim()
                    .equals(parsed.pickup.toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9]+"," ").trim()) &&
                (!countRequired || cachedDeliveryCount==parsed.deliveryCount);
        int minutes=parsed.minutes, metres=parsed.metres;
        // A count restriction is an independent mandatory gate, so it can
        // establish a rejection even if journey duration is partially unreadable.
        // But a NEW offer requires two fresh matching card/header observations.
        if(!tieredMode && activeRules.deliveriesOn) {
            if(parsed.deliveryCount<=0) {
                countVerifier.reset();hideOverlay();requestFastRetry(visual);
                status("Delivery-count rule active but header unreadable; manual review, no tap. "+retryStatus());
                return;
            }
            if(parsed.deliveryCount<activeRules.minDeliveries) {
                hideOverlay();readVerifier.reset();hourBoundVerifier.reset();correctionVerifier.reset();
                if(sameOffer && confirmedCountDecline && lastOutcome==OfferRuleEngine.Outcome.DECLINE) {
                    if(!autoDeclineSent)performDecline(payout,parsed,visual,lines,width,height,now,
                            timing+" | confirmed delivery count "+parsed.deliveryCount);
                    return;
                }
                processDeliveryCountDecline(prefs,payout,parsed,a,visual,lines,width,height,now,timing);
                return;
            }
        }
        // A price-only tier (or an AND tier with a failed minimum price) can
        // conclusively fail even when duration OCR is absent. Verify the payout,
        // count, merchant and X on two fresh frames, then use the same guarded
        // gesture path; never pretend the missing hourly rate is known.
        if(tieredMode && tierRules.provenPriceFailure(parsed.deliveryCount,payout)) {
            hideOverlay();readVerifier.reset();hourBoundVerifier.reset();correctionVerifier.reset();
            if(sameOffer && confirmedCountDecline && lastOutcome==OfferRuleEngine.Outcome.DECLINE) {
                if(!autoDeclineSent)performDecline(payout,parsed,visual,lines,width,height,now,
                        timing+" | confirmed "+parsed.deliveryCount+"-delivery payout failure");
                return;
            }
            processTierPriceDecline(prefs,payout,parsed,a,visual,lines,width,height,now,
                    timing,activeRules.minPrice);
            return;
        }
        countVerifier.reset();
        boolean metricsWereCorrected=false;
        if(!sameOffer) {
            OfferReadVerifier.Result verified=readVerifier.observe(payout,parsed,visual.x,visual.y,now,countRequired);
            if(verified.state!=OfferReadVerifier.State.CONFIRMED) {
                OfferRuleEngine.Decision display=OfferRuleEngine.evaluate(activeRules,payout,parsed.minutes,parsed.metres,parsed.deliveryCount);
                if(display.outcome==OfferRuleEngine.Outcome.SHOW && OfferExtractionGuard.checkFinancial(parsed).valid && OfferScreenEvaluator.hasOfferIdentity(visual,lines)){
                    boolean shown=false;
                    if(prefs.getBoolean(Prefs.OVERLAY_ENABLED,true)){if(liveOverlay==null)liveOverlay=new OfferOverlay(this);shown=liveOverlay.show(display.hourly,display.perKm);}
                    if(prefs.getBoolean(Prefs.ALERT,true))alertOnce(payout,parsed);
                    status("Qualifies: "+display.reason+"; overlay "+(shown?"shown":"off/unavailable")+"; awaiting history verification.");
                    requestFastRetry(visual);return;
                }
                hideOverlay();
                OfferRuleEngine.Decision bounded=OfferSafeDecline.evaluate(activeRules,payout,parsed);
                if(bounded.outcome==OfferRuleEngine.Outcome.DECLINE) {
                    processBoundedDecline(prefs,payout,parsed,a,visual,lines,width,height,now,timing,bounded);
                    return;
                }
                hourBoundVerifier.reset();
                requestFastRetry(visual);
                status(String.format(Locale.US,
                        "A$%.2f — %s; %s. No decision/tap. Raw time: %s | raw payout: %s | %s",
                        payout,verified.state,verified.reason,
                        parsed.rawDuration,a.rawPayout,timing+" | "+retryStatus()));
                return;
            }
            retryBudget.reset();fastRetryPending=false;
            hourBoundVerifier.reset();
        } else {
            OfferExtractionGuard.Result safe=OfferExtractionGuard.check(parsed);
            if(confirmedHourBound && cachedMinutes<=0 &&
                    parsed.parsedHours==confirmedLowerHours &&
                    OfferSafeDecline.evaluate(activeRules,payout,parsed).outcome==
                            OfferRuleEngine.Outcome.DECLINE) {
                // Recheck this confirmed hour bound without assuming an exact duration.
                if(!autoDeclineSent)performDecline(payout,parsed,visual,lines,width,height,now,
                        timing+" | confirmed hour lower bound; exact minutes unknown");
                return;
            }
            if(cachedMinutes<=0 && safe.valid) {
                // Upgrade a lower bound to an EXACT history duration only after
                // two complete matching new images. Never fill a single OCR minute.
                OfferReadVerifier.Result correction=correctionVerifier.observe(
                        payout,parsed,visual.x,visual.y,now);
                if(correction.state!=OfferReadVerifier.State.CONFIRMED) {
                    requestFastRetry(visual);
                    status("Full minutes appeared after hour bound: " +correction.reason+
                            "; awaiting second exact reading; no new tap. "+retryStatus());
                    return;
                }
                try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                    store.correctVerified(lastHistoryId,parsed,a.rawPayout,
                            correctionVerifier.confirmedDistanceMetres());
                } catch(Exception e) {
                    status("Hour-bound history upgrade failed; no tap: "+e.getClass().getSimpleName());
                    return;
                }
                cachedMinutes=minutes;cachedMetres=metres;cachedDeliveryCount=parsed.deliveryCount;
                confirmedHourBound=false;confirmedLowerHours=0;confirmedCountDecline=false;
                metricsWereCorrected=true;lastOutcome=null;
            } else if(safe.valid && cachedMinutes>0 &&
                    (cachedMinutes!=minutes || correctionVerifier.isPending())) {
                // A later OCR read contradicts the stored metrics. Withhold
                // overlay/gesture until TWO matching new frames support a correction.
                hideOverlay();lastOutcome=OfferRuleEngine.Outcome.REVIEW;
                OfferReadVerifier.Result revision=correctionVerifier.observe(
                        payout,parsed,visual.x,visual.y,now);
                if(revision.state!=OfferReadVerifier.State.CONFIRMED) {
                    requestFastRetry(visual);
                    status("Existing offer metrics changed: " + revision.reason +
                            ". Manual review until confirmed; no tap. " + timing);
                    return;
                }
                try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                    store.correctVerified(lastHistoryId,parsed,a.rawPayout,
                            correctionVerifier.confirmedDistanceMetres());
                } catch(Exception e) {status("Correction save failed: "+e.getClass().getSimpleName());}
                cachedMinutes=minutes;cachedMetres=metres;
                lastOutcome=null;
                metricsWereCorrected=true;
            } else if(!safe.valid) {
                // Never replace two verified measurements with an incomplete frame.
                hideOverlay();lastOutcome=OfferRuleEngine.Outcome.REVIEW;
                requestFastRetry(visual);
                status("Ignoring incomplete repeat OCR: "+safe.reason+". "+retryStatus()+". No new tap.");
                return;
            } else correctionVerifier.reset();
        }
        try {
            if(sameOffer) {
                // Most repeat frames need NO SQLite work. Only backfill fields
                // if the first screenshot missed details and the new one supplies them.
                boolean missingFilled=(cachedMetres<=0&&parsed.metres>0) ||
                        (cachedPickup.isEmpty()&&!parsed.pickup.isEmpty()) ||
                        (cachedDestination.isEmpty()&&!parsed.destination.isEmpty());
                if(missingFilled) {
                    try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                        store.fillMissing(lastHistoryId,parsed);
                    }
                }
                // Never upgrade a count/price-only record on one unverified time frame.
                if(cachedMinutes>0)cachedMinutes=parsed.minutes;
                if(cachedDeliveryCount<=0)cachedDeliveryCount=parsed.deliveryCount;
                if(cachedMetres<=0)cachedMetres=parsed.metres;
                if(cachedPickup.isEmpty())cachedPickup=parsed.pickup;
                if(cachedDestination.isEmpty())cachedDestination=parsed.destination;
                minutes=cachedMinutes;metres=cachedMetres;
            } else {
                try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                    lastHistoryId=store.record((int)Math.round(payout*100),parsed,a.rawPayout,
                            readVerifier.confirmedDistanceMetres());
                }
                cachedMinutes=minutes;cachedMetres=metres;cachedDeliveryCount=parsed.deliveryCount;
                confirmedHourBound=false;confirmedLowerHours=0;confirmedCountDecline=false;
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
                !(lastOutcome==OfferRuleEngine.Outcome.DECLINE && !autoDeclineSent)) {
            // Same visible offer: do not ring again or tap repeatedly. Keep the
            // already qualified overlay present, and respect any new OFF toggle.
            if(lastOutcome==OfferRuleEngine.Outcome.SHOW) {
                OfferRuleEngine.Decision repeat=OfferRuleEngine.evaluate(
                        activeRules,payout,minutes,metres,parsed.deliveryCount);
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
        }
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,(float)payout)
                .putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        OfferRuleEngine.Decision decision=OfferRuleEngine.evaluate(activeRules,payout,minutes,metres,parsed.deliveryCount);
        if(tieredMode)decision=new OfferRuleEngine.Decision(decision.outcome,
                parsed.deliveryCount+"-delivery tier: "+decision.reason,
                decision.payout,decision.hourly,decision.perKm);
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
                boolean started=alertOnce(payout,parsed);
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
        performDecline(payout,parsed,visual,lines,width,height,now,timing);
    }

    /**
     * A verified mandatory delivery-count failure is enough to reject an offer
     * regardless of how much it pays or what estimated time the OCR can read.
     * The count and payout must agree across independent frames before any tap.
     */
    private void processDeliveryCountDecline(SharedPreferences prefs,double payout,
            OfferDetailsParser.Details parsed,OfferScreenEvaluator.Assessment assessment,
            VisualDeclineDetector.Match visual,List<OfferScreenEvaluator.Line> lines,
            int width,int height,long now,String timing) {
        boolean confirmed=countVerifier.observe(payout,parsed,visual.x,visual.y,now);
        if(!confirmed) {
            requestFastRetry(visual);
            status("Mandatory delivery count: "+parsed.deliveryCount+" < "+
                    Prefs.rules(prefs).minDeliveries+"; confirming payout, header and merchant " +
                    "on second image. No tap yet. "+retryStatus());
            return;
        }
        retryBudget.reset();fastRetryPending=false;
        lastAmount=payout;lastDecisionAt=now;lastX=visual.x;lastY=visual.y;
        cachedMinutes=countVerifier.hasConfirmedExactDuration()?parsed.minutes:0;
        cachedMetres=parsed.metres;cachedDeliveryCount=parsed.deliveryCount;
        cachedPickup=parsed.pickup;cachedDestination=parsed.destination;
        lastOutcome=OfferRuleEngine.Outcome.DECLINE;reviewAlerted=false;autoDeclineSent=false;
        confirmedCountDecline=true;confirmedHourBound=false;confirmedLowerHours=0;
        try(OfferHistoryStore store=new OfferHistoryStore(this)) {
            lastHistoryId=cachedMinutes>0?
                    store.record((int)Math.round(payout*100),parsed,assessment.rawPayout,
                            countVerifier.confirmedDistanceMetres()):
                    store.recordCountRule((int)Math.round(payout*100),parsed,assessment.rawPayout);
        } catch(Exception e) {
            lastHistoryId=-1;status("Confirmed count but history write failed; no tap: "+
                    e.getClass().getSimpleName());return;
        }
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,(float)payout)
                .putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        if(prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status("TEST ONLY: "+parsed.deliveryCount+" deliveries < minimum "+
                    Prefs.rules(prefs).minDeliveries+"; decline qualified; no tap. "+timing);
            return;
        }
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)) {
            status("Delivery-count decline qualified; auto-decline OFF; no tap. "+timing);
            return;
        }
        performDecline(payout,parsed,visual,lines,width,height,now,
                timing+" | mandatory delivery count "+parsed.deliveryCount);
    }

    /** A verified independent payout failure does not require duration OCR.
     * Unknown hourly rates are intentionally stored as COUNT_ONLY, never
     * misrepresented as verified. */
    private void processTierPriceDecline(SharedPreferences prefs,double payout,
            OfferDetailsParser.Details parsed,OfferScreenEvaluator.Assessment assessment,
            VisualDeclineDetector.Match visual,List<OfferScreenEvaluator.Line> lines,
            int width,int height,long now,String timing,double minPrice) {
        boolean confirmed=countVerifier.observe(payout,parsed,visual.x,visual.y,now);
        String reason=parsed.deliveryCount+"-delivery tier: A$"+
                String.format(Locale.US,"%.2f",payout)+" below minimum A$"+
                String.format(Locale.US,"%.2f",minPrice);
        if(!confirmed) {
            requestFastRetry(visual);
            status(reason+"; confirming payout, delivery count, merchant and X on second image. " +
                    "No tap yet. "+retryStatus());
            return;
        }
        retryBudget.reset();fastRetryPending=false;
        lastAmount=payout;lastDecisionAt=now;lastX=visual.x;lastY=visual.y;
        cachedMinutes=countVerifier.hasConfirmedExactDuration()?parsed.minutes:0;
        cachedMetres=parsed.metres;cachedDeliveryCount=parsed.deliveryCount;
        cachedPickup=parsed.pickup;cachedDestination=parsed.destination;
        lastOutcome=OfferRuleEngine.Outcome.DECLINE;reviewAlerted=false;autoDeclineSent=false;
        confirmedCountDecline=true;confirmedHourBound=false;confirmedLowerHours=0;
        try(OfferHistoryStore store=new OfferHistoryStore(this)) {
            lastHistoryId=cachedMinutes>0?
                    store.record((int)Math.round(payout*100),parsed,assessment.rawPayout,
                            countVerifier.confirmedDistanceMetres()):
                    store.recordCountRule((int)Math.round(payout*100),parsed,assessment.rawPayout);
        } catch(Exception e) {
            lastHistoryId=-1;status("Tier payout verified, but history write failed; no tap: "+
                    e.getClass().getSimpleName());return;
        }
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,(float)payout)
                .putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        if(prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status("TEST ONLY: "+reason+"; declined by rules, no tap. "+timing);return;
        }
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)) {
            status(reason+"; auto-decline OFF, no tap. "+timing);return;
        }
        performDecline(payout,parsed,visual,lines,width,height,now,timing+" | "+reason);
    }

    /** A bounded reading is not a known duration: history must show Unknown/hr. */
    private void processBoundedDecline(SharedPreferences prefs,double payout,
            OfferDetailsParser.Details parsed,OfferScreenEvaluator.Assessment assessment,
            VisualDeclineDetector.Match visual,List<OfferScreenEvaluator.Line> lines,
            int width,int height,long now,String timing,OfferRuleEngine.Decision bound) {
        boolean confirmed=hourBoundVerifier.observe(payout,parsed,visual.x,visual.y,now,
                Prefs.tieredEnabled(prefs) || Prefs.rules(prefs).deliveriesOn);
        if(!confirmed) {
            requestFastRetry(visual);
            status(String.format(Locale.US,
                    "A$%.2f: hour component read; confirming safe %d+ minute bound on a second image. %s; %s. No tap yet.",
                    payout,parsed.parsedHours*60,bound.reason,retryStatus()));
            return;
        }
        retryBudget.reset();fastRetryPending=false;
        lastAmount=payout;lastDecisionAt=now;lastX=visual.x;lastY=visual.y;
        cachedMinutes=countVerifier.hasConfirmedExactDuration()?parsed.minutes:0;
        cachedMetres=parsed.metres;cachedDeliveryCount=parsed.deliveryCount;
        confirmedCountDecline=false;
        cachedPickup=parsed.pickup;cachedDestination=parsed.destination;
        lastOutcome=OfferRuleEngine.Outcome.DECLINE;reviewAlerted=false;autoDeclineSent=false;
        confirmedHourBound=true;confirmedLowerHours=parsed.parsedHours;
        try(OfferHistoryStore store=new OfferHistoryStore(this)) {
            lastHistoryId=store.recordHourBound((int)Math.round(payout*100),parsed,assessment.rawPayout);
        } catch(Exception e) {
            lastHistoryId=-1;
            status("Confirmed duration bound, but history write failed; no tap: "+e.getClass().getSimpleName());
            return;
        }
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,(float)payout)
                .putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        if(prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status(String.format(Locale.US,
                    "TEST ONLY: A$%.2f, confirmed ≥%d min; safely fails all rules. No tap. %s",
                    payout,parsed.parsedHours*60,timing));
            return;
        }
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)) {
            status("Safe hour-bound decline established; auto-decline OFF, no tap. "+timing);
            return;
        }
        performDecline(payout,parsed,visual,lines,width,height,now,
                timing+" | confirmed minimum "+parsed.parsedHours*60+" min; exact minutes UNKNOWN");
    }

    /** Both full-duration and mathematically proven hour-bound offers use this guarded gesture path. */
    private void performDecline(double payout,OfferDetailsParser.Details parsed,
            VisualDeclineDetector.Match visual,List<OfferScreenEvaluator.Line> lines,
            int width,int height,long now,String timing) {
        SharedPreferences prefs=Prefs.of(this);
        String retryKey=Math.round(payout*100)+"/"+parsed.minutes+"/"+parsed.metres+"/"+parsed.deliveryCount+"/"+parsed.type;
        retries.observe(retryKey,now);
        if(gestureBusy||!retries.ready(now))return;
        if(!prefs.getBoolean(Prefs.MONITOR,false))return;
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)||prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status("Automatic decline disabled or TEST ONLY enabled. No tap. "+timing);return;
        }
        if(!prefs.getBoolean(Prefs.VISUAL_X,true) || !isSupportedForeground()) {
            status("Visual monitoring or Uber Driver foreground not confirmed; no tap.");return;
        }
        OfferSurfacePolicy.Host host=foregroundHost();
        boolean reliableOfferText=OfferScreenEvaluator.hasOfferIdentity(visual,lines);
        if(!reliableOfferText||!OfferSurfacePolicy.allowDecline(host,true,reliableOfferText,2,visual.confidence)) {
            status("Offer verification failed; no tap. " + timing);return;
        }
        if(SystemClock.elapsedRealtime()-lastCaptureAt>1900) {
            status("OCR took too long; screenshot is stale, so no tap.");return;
        }
        // The only gesture target comes from the freshly verified X on THIS screenshot.
        float x=visual.x*width/(float)VisualDeclineDetector.WIDTH;
        float y=visual.y*height/(float)VisualDeclineDetector.HEIGHT;
        // Remove our own non-touchable overlay before checking the target.
        hideOverlay();
        if(!targetIsUber((int)x,(int)y)){status("Target no longer in Uber window; no tap.");return;}
        int mode=TouchMethod.normalize(prefs.getInt(TouchMethod.KEY,0));
        AccessibilityNodeInfo direct=TouchMethod.tryNative(mode)?findDecline(getRootInActiveWindow(),(int)x,(int)y,0):null;
        if(!targetIsUber((int)x,(int)y)){status("Uber target changed during accessibility lookup; no tap.");return;}
        if(!TouchMethod.allowGesture(mode)&&direct==null){status("Native-only: no verified accessible X node; no tap.");return;}
        final int attempt=retries.attempt(now);
        status("Verified Uber target: "+x+","+y+"; method="+TouchMethod.LABELS[mode]+"; accessible decline node="+(direct!=null)+"; own overlay removed");
        if(direct!=null && direct.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
            autoDeclineSent=!retries.canAttempt();
            try(OfferHistoryStore store=new OfferHistoryStore(this)){store.markDeclineAttempt(lastHistoryId);}catch(Exception ignored){}
            status("Uber decline attempt "+attempt+"/3 via accessibility; checking dismissal.");return;
        }
        if(!TouchMethod.allowGesture(mode)){status("Native-only: ACTION_CLICK returned false; no gesture fallback.");return;}
        if(!targetIsUber((int)x,(int)y)||SystemClock.elapsedRealtime()-lastCaptureAt>1900){status("Target stale before gesture; no tap.");return;}
        final int duration=TouchMethod.duration(mode,attempt);
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
        final long declinedHistoryId=lastHistoryId;
        final String finalTiming=timing;
        gestureBusy=true;final long g=++gestureGeneration;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if(g!=gestureGeneration)return;gestureBusy=false;autoDeclineSent=!retries.canAttempt();
                status(String.format(Locale.US,
                        "A$%.2f: X gesture completed. Uber decline NOT independently confirmed. %s",
                        payout,finalTiming));
            }
            @Override public void onCancelled(GestureDescription gesture) {
                if(g!=gestureGeneration)return;gestureBusy=false;autoDeclineSent=!retries.canAttempt();
                status(String.format(Locale.US,
                        "A$%.2f: X gesture cancelled; decline not confirmed. %s",
                        payout,finalTiming));
            }
        },null);
        if(!accepted){gestureBusy=false;autoDeclineSent=!retries.canAttempt();}
        else handler.postDelayed(()->{if(gestureBusy&&g==gestureGeneration){gestureBusy=false;gestureGeneration++;autoDeclineSent=!retries.canAttempt();status("Gesture callback timed out; reverify.");}},2500);
        if(accepted) {
            autoDeclineSent=true;
            try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                store.markDeclineAttempt(declinedHistoryId);
            } catch(Exception ignored) { /* Gesture status remains in diagnostics. */ }
        }
        status(String.format(Locale.US,accepted?
                "A$%.2f: X tap dispatched (%s, %s); Uber decline unconfirmed. %s":
                "A$%.2f: Android rejected X gesture; no tap. (%s, %s). %s",
                payout,host,visual.theme,finalTiming+" | hold="+duration+"ms | attempt "+attempt+"/3 | physical target="+x+","+y+" screen="+width+"x"+height));
    }


    /** Explicit user-initiated test restricted to our own visible diagnostic button. */
    interface TouchResult { void report(String message); }
    void testOwnButton(int x,int y,int duration,boolean nativeClick,TouchResult result){
        if(gestureBusy){result.report("Busy: a gesture is still pending");return;}
        hideOverlay();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||!getPackageName().contentEquals(root.getPackageName()==null?"":root.getPackageName())){
            result.report("Blocked: diagnostic window is not active");return;
        }
        AccessibilityNodeInfo target=findDiagnosticButton(root,x,y,0);
        if(target==null){result.report("Blocked: diagnostic button not exposed at target");return;}
        List<AccessibilityWindowInfo> windows=getWindows();
        AccessibilityWindowInfo top=null;
        if(windows!=null)for(AccessibilityWindowInfo w:windows){Rect b=new Rect();w.getBoundsInScreen(b);if(b.contains(x,y)&&(top==null||w.getLayer()>top.getLayer()))top=w;}
        AccessibilityNodeInfo owner=top==null?null:top.getRoot();
        if(owner==null||!getPackageName().contentEquals(owner.getPackageName()==null?"":owner.getPackageName())){result.report("Blocked: another or unknown window covers target");return;}
        if(nativeClick){result.report("ACTION_CLICK returned "+target.performAction(AccessibilityNodeInfo.ACTION_CLICK));return;}
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
        gestureBusy=true;final long generation=++gestureGeneration;
        boolean sent=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){if(generation==gestureGeneration){gestureBusy=false;result.report("Android callback: completed");}}
            @Override public void onCancelled(GestureDescription g){if(generation==gestureGeneration){gestureBusy=false;result.report("Android callback: cancelled");}}
        },handler);
        result.report("dispatchGesture returned "+sent+"; "+duration+"ms at "+x+","+y);
        if(!sent)gestureBusy=false;
        else handler.postDelayed(()->{if(gestureBusy&&generation==gestureGeneration){gestureBusy=false;gestureGeneration++;result.report("Android callback timed out");}},2500);
    }
    private AccessibilityNodeInfo findDiagnosticButton(AccessibilityNodeInfo node,int x,int y,int depth){
        if(node==null||depth>18)return null;
        Rect b=new Rect();node.getBoundsInScreen(b);
        if(node.isVisibleToUser()&&node.isEnabled()&&node.isClickable()&&b.contains(x,y)&&"Touch test target".contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findDiagnosticButton(node.getChild(i),x,y,depth+1);if(found!=null)return found;}
        return null;
    }

    private boolean targetIsUber(int x,int y){
        if(!isUberForeground())return false;
        List<AccessibilityWindowInfo> windows=getWindows();
        if(windows==null||windows.isEmpty())return false;
        AccessibilityWindowInfo top=null;
        for(AccessibilityWindowInfo w:windows){Rect b=new Rect();w.getBoundsInScreen(b);if(b.contains(x,y)&&(top==null||w.getLayer()>top.getLayer()))top=w;}
        if(top==null)return false;
        AccessibilityNodeInfo r=top.getRoot();return r!=null&&r.getPackageName()!=null&&OfferSurfacePolicy.UBER.contentEquals(r.getPackageName());
    }
    private AccessibilityNodeInfo findDecline(AccessibilityNodeInfo node,int x,int y,int depth){
        if(node==null||depth>18)return null;
        Rect b=new Rect();node.getBoundsInScreen(b);
        if(!b.contains(x,y)||!node.isVisibleToUser())return null;
        String t=String.valueOf(node.getText()).trim().toLowerCase(Locale.ROOT),d=String.valueOf(node.getContentDescription()).trim().toLowerCase(Locale.ROOT);
        if(node.isEnabled()&&node.isClickable()&&b.width()<200&&b.height()<200&&(t.matches("x|close|dismiss|decline")||d.matches("x|close|dismiss|decline|decline offer|dismiss offer|close offer")))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=findDecline(node.getChild(i),x,y,depth+1);if(child!=null)return child;}
        return null;
    }
    private void requestFastRetry(VisualDeclineDetector.Match visual) {
        if(visual!=null && visual.found &&
                retryBudget.request(visual.x,visual.y,SystemClock.elapsedRealtime()))
            fastRetryPending=true;
    }
    private String retryStatus() {
        return fastRetryPending ?
                "fast re-read queued ("+retryBudget.used()+"/5)" :
                "waiting for next normal scan; retry budget used";
    }

    private void status(String msg) {
        SharedPreferences p=Prefs.of(this);
        String old=p.getString(Prefs.LIVE_STATUS,"");
        if(msg.equals(old))return;
        long time=System.currentTimeMillis();
        SharedPreferences.Editor edit=p.edit().putString(Prefs.LIVE_STATUS,msg)
                .putLong(Prefs.LIVE_TIME,time);
        if(!msg.equals(old)) {
            String stamp=DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(time));
            String previous=p.getString(Prefs.DIAG_HISTORY,"");
            String[] entries=previous.split("\n");
            StringBuilder b=new StringBuilder(stamp).append(" | ").append(msg);
            for(int i=0;i<Math.min(entries.length,60);i++) {
                if(!entries[i].isEmpty()) b.append('\n').append(entries[i]);
            }
            edit.putString(Prefs.DIAG_HISTORY,b.toString());
        }
        edit.apply();
    }

    @Override public void onInterrupt() {
        Prefs.of(this).edit().putString(Prefs.HEALTH_STATE,"Accessibility interrupted").apply();
        watchdog.invalidate();
        hideOverlay();clearPendingAcceptance();clearReadVerification();
        status("Accessibility interrupted. Monitoring paused; service reconnect may be required.");
    }
    @Override public boolean onUnbind(android.content.Intent intent) {
        if(connected==this)connected=null;
        Prefs.of(this).edit().putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,false)
                .putString(Prefs.HEALTH_STATE,"Accessibility unbound").apply();
        watchdog.invalidate();
        handler.removeCallbacks(monitorLoop);
        handler.removeCallbacks(fastVerifyRunnable);
        hideOverlay();clearPendingAcceptance();clearReadVerification();
        status("Accessibility unbound by Android; automatic restart is not permitted if disabled.");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        if(connected==this)connected=null;
        Prefs.of(this).edit().putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,false)
                .putString(Prefs.HEALTH_STATE,"Accessibility service stopped").apply();
        watchdog.invalidate();
        handler.removeCallbacks(monitorLoop);
        hideOverlay();clearPendingAcceptance();clearReadVerification();
        visualWorker.shutdown();
        if(recognizer!=null) recognizer.close();
        status("Accessibility service stopped. Re-enable if monitoring is needed.");
        super.onDestroy();
    }
}
