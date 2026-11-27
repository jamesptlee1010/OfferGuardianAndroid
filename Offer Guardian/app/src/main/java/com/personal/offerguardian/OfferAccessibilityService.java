package com.personal.offerguardian;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.content.Intent;

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
 * Detection path: visible delivery window -> fresh screenshot -> automatic
 * light/dark offer-card border + matching X -> OCR payout INSIDE the same
 * card -> existing rules. Ambiguous screens never receive a gesture.
 * No legacy click-event learning, fixed tap coordinates, or automatic accepting.
 */
public final class OfferAccessibilityService extends AccessibilityService {
    private static final String UBER = OfferSurfacePolicy.UBER;
    // Shared polling; other apps are inspected only for a delivery-window event or fresh offer notification.
    private static final long UBER_POLL_MS=500;
    private static final long UBER_MIN_CAPTURE_MS=450;
    // Android limits repeated Accessibility screenshots (~333 ms in AOSP).
    // Give it a margin for bounded, offer-specific confirmation retries.
    private static final long FAST_CONFIRM_MIN_CAPTURE_MS=360;
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
                applyMonitoringMode(prefs);
                refreshQuickControls(prefs);
                if(dashLaunchVerifyAt>0){
                    if(foregroundHost()==OfferSurfacePolicy.Host.DOORDASH){dashLaunchVerifyAt=0;prefs.edit().putString(Prefs.DASH_NOTICE_DIAG,"DoorDash launch verified: Dasher foreground").apply();status("DoorDash launch verified: Dasher foreground.");}
                    else if(now>=dashLaunchVerifyAt){
                        dashLaunchVerifyAt=0;
                        if(dashLaunchUsedIntent&&prefs.getBoolean(Prefs.MONITOR,false)&&prefs.getBoolean(Prefs.AUTO_SWITCH,false)&&prefs.getBoolean(Prefs.DASH_ENABLED,true)&&!knownUberActive&&!knownDashActive&&!quickBusy&&!uberGestureBusy&&!dashGestureBusy&&!uberVisibleLast&&dashStage==0&&!watchdog.busy()&&now-lastCaptureAt<2500&&now-switchedAt<10000){
                            dashLaunchUsedIntent=false;
                            if(launchPackage(OfferSurfacePolicy.DOORDASH)){dashLaunchVerifyAt=now+3000;status("DoorDash notification launch unverified; direct app launch requested once.");}
                        }
                        if(dashLaunchVerifyAt==0){switchReturnPackage="";prefs.edit().putString(Prefs.DASH_NOTICE_DIAG,"DoorDash launch not verified; Android may have blocked switching").apply();status("DoorDash launch not verified; Android may have blocked opening. Tap notification manually.");}
                    }
                }
                if(quickBusy)return;
                if (prefs.getBoolean(Prefs.MONITOR,false) &&
                        !prefs.getBoolean(Prefs.LEARNING,false)) {
                    if (!prefs.getBoolean(Prefs.VISUAL_X,false)) {
                        hideOverlay();clearPendingAcceptance();clearReadVerification();resetDash();
                        status("Visual recognition is OFF. Turn on grey-X recognition in the app.");
                    } else if (isSupportedForeground()) {
                        reportHeartbeat(now, "Delivery monitor active: "+foregroundHost());
                        scanIfDue();
                    } else {
                        hideOverlay();clearPendingAcceptance();clearReadVerification();dashPrevious=null;dashConfirmPrevious=null;expireDashPending(now,"foreground changed");
                        reportForegroundUnavailable(now);
                        reportHeartbeat(now, "Waiting for supported delivery app");
                    }
                } else {
                    hideOverlay();clearPendingAcceptance();clearReadVerification();resetDash();
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

    private QuickControls quickControls;
    private boolean quickBusy=false,uberGestureBusy=false;
    private int quickGeneration=0;
    private long quickDeadline=0;
    private boolean quickOpenedMenu=false,quickPressedPause=false,quickPressedResume=false;
    private void refreshQuickControls(SharedPreferences p) {
        if(p.getBoolean(Prefs.DASH_ENABLED,true)&&p.getBoolean(Prefs.QUICK_CONTROLS,false)&&p.getBoolean(Prefs.MONITOR,false)&&!quickBusy){
            if(quickControls==null)quickControls=new QuickControls(this,this::quickAction);
            quickControls.show();
        }else if(quickControls!=null)quickControls.hide();
        if(quickBusy&&(!p.getBoolean(Prefs.DASH_ENABLED,true)||!p.getBoolean(Prefs.QUICK_CONTROLS,false)||!p.getBoolean(Prefs.MONITOR,false)))finishQuick("Quick controls disabled; action stopped.");
    }
    private void finishQuick(String message){quickGeneration++;quickBusy=false;status(message);}
    private void quickAction(String action){
        if(quickBusy||dashGestureBusy||uberGestureBusy||dashStage==1||dashStage==2){status("Quick controls: another action is in progress; try again.");return;}
        if(!Prefs.of(this).getBoolean(Prefs.MONITOR,false)||!Prefs.of(this).getBoolean(Prefs.QUICK_CONTROLS,false))return;
        switchReturnPackage="";Prefs.of(this).edit().remove(Prefs.DASH_NOTICE).apply();
        quickBusy=true;int generation=++quickGeneration;quickDeadline=SystemClock.elapsedRealtime()+9000;
        watchdog.invalidate();clearReadVerification();hideOverlay();if(quickControls!=null)quickControls.hide();
        if("D".equals(action)){boolean ok=launchPackage(OfferSurfacePolicy.DOORDASH);handler.postDelayed(()->{if(generation==quickGeneration)finishQuick(ok?"Quick D: DoorDash launch requested.":"Quick D: could not open DoorDash.");},600);return;}
        
        if("DR".equals(action)){
            quickPressedResume=false;
            if(foregroundHost()!=OfferSurfacePolicy.Host.DOORDASH&&!launchPackage(OfferSurfacePolicy.DOORDASH)){finishQuick("DR: DoorDash could not open.");return;}
            handler.postDelayed(()->quickResumeStep(generation),300);return;
        }
        if(!"DF".equals(action)){finishQuick("Unknown quick action; no tap.");return;}
        quickOpenedMenu=false;quickPressedPause=false;
        if(foregroundHost()!=OfferSurfacePolicy.Host.DOORDASH&&!launchPackage(OfferSurfacePolicy.DOORDASH)){finishQuick("DF: DoorDash could not open.");return;}
        handler.postDelayed(()->quickPauseStep(generation),300);
    }
    private boolean quickCurrent(int generation){return quickBusy&&generation==quickGeneration&&Prefs.of(this).getBoolean(Prefs.MONITOR,false)&&Prefs.of(this).getBoolean(Prefs.QUICK_CONTROLS,false);}
    private void quickPauseStep(int generation){
        if(!quickCurrent(generation))return;
        if(SystemClock.elapsedRealtime()>=quickDeadline){finishQuick("DF stopped: pause not verified. Check DoorDash manually.");return;}
        if(foregroundHost()!=OfferSurfacePolicy.Host.DOORDASH||quickUberOfferObscuresDash()){handler.postDelayed(()->quickPauseStep(generation),350);return;}
        AccessibilityNodeInfo root=getRootInActiveWindow();StringBuilder b=new StringBuilder();collectIdleText(root,b,0);
        QuickControlPolicy.Screen screen=QuickControlPolicy.screen(b.toString());
        if(screen==QuickControlPolicy.Screen.PAUSED){quickReturnUber(generation,"DF: DoorDash pause verified");return;}
        if(screen==QuickControlPolicy.Screen.IDLE&&!quickOpenedMenu){
            if(clickQuickLabel(root,OfferSurfacePolicy.DOORDASH,new String[]{"settings","current dash","dash settings","open settings"},true)){quickOpenedMenu=true;status("DF: opened current dash menu.");}
        }else if(screen==QuickControlPolicy.Screen.MENU&&!quickPressedPause){
            if(clickQuickLabel(root,OfferSurfacePolicy.DOORDASH,new String[]{"pause orders"},false)){quickPressedPause=true;status("DF: Pause orders pressed; verifying.");}
        }
        handler.postDelayed(()->quickPauseStep(generation),350);
    }
    private void quickResumeStep(int generation){
        if(!quickCurrent(generation))return;
        if(SystemClock.elapsedRealtime()>=quickDeadline){finishQuick("DR stopped: resume not verified. Check DoorDash manually.");return;}
        if(foregroundHost()!=OfferSurfacePolicy.Host.DOORDASH||quickUberOfferObscuresDash()){handler.postDelayed(()->quickResumeStep(generation),350);return;}
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null||!OfferSurfacePolicy.DOORDASH.contentEquals(root.getPackageName())){handler.postDelayed(()->quickResumeStep(generation),350);return;}
        StringBuilder b=new StringBuilder();collectQuickVisibleText(root,b,0);
        QuickControlPolicy.Screen screen=QuickControlPolicy.screen(b.toString());
        switch(QuickControlPolicy.resumeDecision(screen,quickPressedResume)){
            case ALREADY_ACTIVE:finishQuick("DR: DoorDash already active; no tap.");return;
            case VERIFIED:finishQuick("DR: DoorDash resumed; finding offers verified.");return;
            case CLICK:
                if(clickQuickLabel(root,OfferSurfacePolicy.DOORDASH,new String[]{"resume dash"},false)){quickPressedResume=true;status("DR: Resume dash pressed; verifying.");}
                break;
            default:break;
        }
        handler.postDelayed(()->quickResumeStep(generation),350);
    }
    private void collectQuickVisibleText(AccessibilityNodeInfo n,StringBuilder b,int depth){
        if(n==null||depth>20||b.length()>10000)return;
        if(n.isVisibleToUser()){if(n.getText()!=null)b.append(' ').append(n.getText());if(n.getContentDescription()!=null)b.append(' ').append(n.getContentDescription());}
        for(int i=0;i<n.getChildCount();i++)collectQuickVisibleText(n.getChild(i),b,depth+1);
    }
    private boolean clickQuickLabel(AccessibilityNodeInfo root,String pkg,String[] labels,boolean topLeft){
        AccessibilityNodeInfo node=findQuickLabel(root,labels,0);if(node==null)return false;
        Rect r=new Rect();node.getBoundsInScreen(r);int x=r.centerX(),y=r.centerY();
        if(topLeft){Rect bounds=new Rect();root.getBoundsInScreen(bounds);if(x>bounds.left+bounds.width()*.25||y>bounds.top+bounds.height()*.20)return false;}
        if(windowAt(x,y,pkg)<0)return false;
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    private AccessibilityNodeInfo findQuickLabel(AccessibilityNodeInfo node,String[] labels,int depth){
        if(node==null||depth>20)return null;
        String t=String.valueOf(node.getText()).trim().toLowerCase(Locale.ROOT),d=String.valueOf(node.getContentDescription()).trim().toLowerCase(Locale.ROOT);
        if(node.isVisibleToUser()&&node.isEnabled())for(String label:labels)if(t.equals(label)||d.equals(label)){
            AccessibilityNodeInfo click=node;for(int k=0;k<3&&click!=null;k++,click=click.getParent())if(click.isClickable()&&click.isEnabled()&&click.isVisibleToUser())return click;
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo n=findQuickLabel(node.getChild(i),labels,depth+1);if(n!=null)return n;}return null;
    }
    private boolean quickUberOfferObscuresDash(){
        List<AccessibilityWindowInfo> windows=getWindows();if(windows==null)return true;
        int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
        for(AccessibilityWindowInfo window:windows){AccessibilityNodeInfo root=window.getRoot();if(root==null||root.getPackageName()==null||!UBER.contentEquals(root.getPackageName()))continue;
            Rect r=new Rect();window.getBoundsInScreen(r);if(r.width()>width*.35&&r.height()>height*.22)return true;}
        return false;
    }
    private boolean clickUberBubble(){
        List<AccessibilityWindowInfo> windows=getWindows();if(windows==null)return false;
        int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
        for(AccessibilityWindowInfo window:windows){Rect r=new Rect();window.getBoundsInScreen(r);if(r.width()>width*.35||r.height()>height*.22||r.width()<10||r.height()<10)continue;
            AccessibilityNodeInfo node=window.getRoot();if(node==null||node.getPackageName()==null||!UBER.contentEquals(node.getPackageName()))continue;
            if(clickQuickLabel(node,UBER,new String[]{"uber","uber →","uber driver","open uber driver","return to uber"},false))return true;
        }return false;
    }
    private void quickReturnUber(int generation,String prefix){
        if(!quickCurrent(generation))return;
        if(isUberForeground()){finishQuick(prefix+"; Uber already open.");return;}
        boolean bubble=clickUberBubble();
        handler.postDelayed(()->{if(!quickCurrent(generation))return;if(isUberForeground()){finishQuick(prefix+"; Uber opened.");return;}
            if(!launchPackage(UBER)){finishQuick(prefix+"; could not open Uber automatically.");return;}
            handler.postDelayed(()->{if(quickCurrent(generation))finishQuick(prefix+(isUberForeground()?"; Uber opened.":"; Uber launch requested but not verified."));},700);
        },bubble?650:10);
    }
    private void stopQuick(){quickGeneration++;quickBusy=false;if(quickControls!=null)quickControls.hide();}
    private TextRecognizer recognizer;
    private int ocrInFlight=0, appliedMonitoringMode=-1;
    private int payoutReadFailures=0;
    private int payoutFailureX=-1,payoutFailureY=-1;
    private long lastOcrBackoffLog=0;
    private com.google.android.gms.tasks.Task<Text> processOcr(Bitmap image){
        ocrInFlight++;
        try{return recognizer.process(InputImage.fromBitmap(image,0));}
        catch(RuntimeException e){ocrInFlight--;throw e;}
    }
    private void ocrDone(){ocrInFlight=Math.max(0,ocrInFlight-1);}
    private void applyMonitoringMode(SharedPreferences p){
        int mode=(p.getBoolean(Prefs.UBER_ENABLED,true)?1:0)|(p.getBoolean(Prefs.DASH_ENABLED,true)?2:0);
        if(mode==appliedMonitoringMode)return;
        appliedMonitoringMode=mode;
        watchdog.invalidate();clearUberSession();clearPendingAcceptance();hideOverlay();resetDash();
        recentCardTop=-1;visualFallback.reset();uberVisibleLast=false;uberRetries.reset();uberReadOnlyShowKey="";lastUberWindowEvent=0;
        if((mode&1)==0){template=null;darkTemplate=null;reusablePixels=null;}
        if((mode&2)==0){dashLaunchVerifyAt=0;switchReturnPackage="";p.edit().remove(Prefs.DASH_NOTICE).apply();OfferNotificationListener.clearPending();}
        status("Monitoring mode: "+(mode==1?"Uber only":mode==2?"DoorDash only":mode==3?"Both":"Off")+"; processing reset.");
    }
    private int[] template;
    // Main-thread-only reuse avoids allocating a 4 MB pixel array on each capture.
    private int[] reusablePixels;
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
    private String uberReadOnlyShowKey="";
    private final UberDeclineRetry uberRetries=new UberDeclineRetry();
    private long uberGestureGeneration=0;
    private int uberAbsentFrames=0;
    private DoorDashOffer.Read dashProtectedRead;
    private long dashProtectedAt=0;
    private String dashProtectedRules="";
    private int dashMatchingReads=0;
    private DoorDashOffer.Read dashPrevious;
    private long dashPreviousAt,dashId=-1,dashStageAt,dashConfirmAt,dashDeadline;
    private DoorDashOffer.Read dashPendingRead;
    private int dashConfirmWindow=-1;
    private String dashKey="";
    private int dashStage=0,dashMissing=0,dashWindow=-1,dashOfferWindow=-1;
    private boolean dashAlerted=false,dashGestureBusy=false;
    private String switchReturnPackage="";
    private long lastUberWindowEvent=0;
    private final UberVisualFallback visualFallback=new UberVisualFallback();
    private int frameUnknownWindow=-1;
    private int recentCardTop=-1;
    private long recentCardAt=0;
    private boolean knownUberActive=false,knownDashActive=false;
    private int dashStep1Retries=0,dashStep2Retries=0;
    private long dashShowAt=0;
    private String dashShowKey="", lastDashDecision="";
    private long dashLaunchVerifyAt=0;
    private boolean dashLaunchUsedIntent=false;
    private long switchedAt=0,lastSwitchAttempt=0,dashGestureGeneration=0;
    private DoorDashOffer.Confirmation dashConfirmPrevious;
    private boolean uberVisibleLast=false;
    private int uberOfferWindow=-1;

    private boolean confirmedHourBound=false;
    private boolean confirmedCountDecline=false;
    private long lastRulesGeneration=-1;
    private int confirmedLowerHours=0;
    private final OfferAcceptanceEvidence.StableConfirmation acceptanceEvidence=
            new OfferAcceptanceEvidence.StableConfirmation();

    private void hideOverlay() {if(liveOverlay!=null)liveOverlay.hide();}
    private void clearReadVerification() {
        visualFallback.reset();readVerifier.reset();correctionVerifier.reset();hourBoundVerifier.reset();countVerifier.reset();
        fastRetryPending=false;retryBudget.reset();
        handler.removeCallbacks(fastVerifyRunnable);
    }
    private void clearPendingAcceptance() {
        pendingAcceptanceId=-1;pendingAcceptanceSince=0;pendingPickup="";
        acceptanceEvidence.reset();
    }
    private void armPendingAcceptance() {
        if(lastHistoryId<=0 || autoDeclineSent || lastOutcome==OfferRuleEngine.Outcome.DECLINE || !isUberForeground() ||
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
            status("Scanner waiting: supported delivery window not confirmed ("+description+"). No tap.");
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
        dashPrevious=null;dashConfirmPrevious=null;expireDashPending(now,"watchdog recovery");
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
        final com.google.android.gms.tasks.Task<Text> submitted;
        try{submitted=processOcr(screenshot);}catch(RuntimeException e){screenshot.recycle();finishCapture(token);status("Acceptance OCR submission failed: "+e.getClass().getSimpleName());return;}
        submitted.addOnSuccessListener(text -> {
                if(!ownsFreshCapture(token) || id!=pendingAcceptanceId || !isUberForeground())return;
                boolean found=OfferAcceptanceEvidence.confirmsActivePickup(text.getText(),pendingPickup);
                if(acceptanceEvidence.observe(found)) {
                    knownUberActive=true;
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
            .addOnCompleteListener(task->{ocrDone();screenshot.recycle();finishCapture(token);});
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        watchdog.invalidate();
        consecutiveCaptureErrors=0;nextCaptureAfter=0;lastHeartbeatAt=0;
        Prefs.of(this).edit().putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,true)
                .putLong(Prefs.HEALTH_HEARTBEAT_TIME,System.currentTimeMillis())
                .putString(Prefs.HEALTH_STATE,"Accessibility connected")
                .putInt(Prefs.HEALTH_RECOVERIES,0).apply();
        status("Accessibility connected. Uber + DoorDash monitoring and scanner watchdog active.");
        handler.removeCallbacks(monitorLoop);
        handler.post(monitorLoop);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event==null)return;
        OfferSurfacePolicy.Host host=OfferSurfacePolicy.classify(
                event.getPackageName()==null?null:event.getPackageName().toString());
        if(host==OfferSurfacePolicy.Host.UBER)lastUberWindowEvent=SystemClock.elapsedRealtime();
        if(!OfferSurfacePolicy.shouldScan(host)) {
            if(!isSupportedForeground())hideOverlay();
            return;
        }
        if(!Prefs.of(this).getBoolean(Prefs.MONITOR,false)||
                !Prefs.of(this).getBoolean(Prefs.VISUAL_X,false))return;
        if(host==OfferSurfacePolicy.Host.UBER&&!Prefs.of(this).getBoolean(Prefs.UBER_ENABLED,true))return;
        if(host==OfferSurfacePolicy.Host.DOORDASH&&!Prefs.of(this).getBoolean(Prefs.DASH_ENABLED,true))return;
        // Uber accessibility events supplement the throttled screenshot poll.
        if(host==OfferSurfacePolicy.Host.UBER&&foregroundHost()==host){
            StringBuilder text=new StringBuilder();collectIdleText(getRootInActiveWindow(),text,0);updateActiveDelivery(text.toString());
        }
        if(host==OfferSurfacePolicy.Host.DOORDASH&&dashStage==1&&tryDashAccessibilityConfirmation())return;
        if(isSupportedForeground())scanIfDue();
    }

    private OfferSurfacePolicy.Host foregroundHost() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null)return OfferSurfacePolicy.Host.OTHER;
        return OfferSurfacePolicy.classify(root.getPackageName().toString());
    }
    private String activePackage() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root==null||root.getPackageName()==null?"":root.getPackageName().toString();
    }
    private boolean largeUberWindow() {
        List<AccessibilityWindowInfo> windows=getWindows();if(windows==null)return false;
        int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
        for(AccessibilityWindowInfo window:windows){
            Rect bounds=new Rect();window.getBoundsInScreen(bounds);
            if(bounds.width()<width*.35||bounds.height()<height*.22)continue;
            AccessibilityNodeInfo root=window.getRoot();
            if(root!=null&&root.getPackageName()!=null&&UBER.contentEquals(root.getPackageName()))return true;
        }return false;
    }
    private boolean isSupportedForeground() {
        OfferSurfacePolicy.Host h=foregroundHost();
        SharedPreferences p=Prefs.of(this);long now=SystemClock.elapsedRealtime();
        return CrossPlatformPolicy.scan(h,p.getBoolean(Prefs.UBER_ENABLED,true),p.getBoolean(Prefs.DASH_ENABLED,true),
                largeUberWindow(),lastUberWindowEvent>0&&now-lastUberWindowEvent<6000,
                p.getBoolean(Prefs.AUTO_SWITCH,false)&&CrossPlatformPolicy.freshNotice(p.getLong(Prefs.DASH_NOTICE,0),now));
    }
    private boolean isUberForeground() {
        return foregroundHost()==OfferSurfacePolicy.Host.UBER;
    }

    private void scanIfDue() {
        if(quickBusy||Prefs.of(this).getBoolean(Prefs.LEARNING,false)||!Prefs.of(this).getBoolean(Prefs.MONITOR,false))return;
        if (Build.VERSION.SDK_INT<30) {
            status("Android 11+ required for screen screenshots.");return;
        }
        long now=SystemClock.elapsedRealtime();
        boolean confirmationPending=dashStage==1||dashStage==2||dashPrevious!=null||readVerifier.isPending()||correctionVerifier.isPending()||
                hourBoundVerifier.isPending()||countVerifier.isPending()||fastRetryPending;
        long minInterval=confirmationPending?FAST_CONFIRM_MIN_CAPTURE_MS:UBER_MIN_CAPTURE_MS;
        recoverStalledCapture(now);
        expireDashPending(now,"deadline");
        // An accessible confirmation does not need to wait for an unrelated OCR task.
        if(dashStage==1&&tryDashAccessibilityConfirmation())return;
        if(ocrInFlight>=2){if(now-lastOcrBackoffLog>10000){lastOcrBackoffLog=now;status("OCR backlog capped at two tasks; awaiting completion before another capture.");}return;}
        if(watchdog.busy() || dashGestureBusy || uberGestureBusy || now<nextCaptureAfter ||
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
            status("Delivery app no longer foreground. No action.");return;
        }
        int w=source.getWidth(),h=source.getHeight();
        if(w<350 || h<700 || h<=w) {
            hideOverlay();source.recycle();finishCapture(token);
            status("Screen aspect ratio changed; no tap ("+w+"x"+h+").");return;
        }
        // DoorDash-only mode bypasses the expensive Uber pixel matcher entirely.
        if(!Prefs.of(this).getBoolean(Prefs.UBER_ENABLED,true)){
            if(foregroundHost()==OfferSurfacePolicy.Host.DOORDASH&&Prefs.of(this).getBoolean(Prefs.DASH_ENABLED,true))processDashScreenshot(source,token,w,h);
            else{hideOverlay();maybeOpenDash(SystemClock.elapsedRealtime());source.recycle();finishCapture(token);}
            return;
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
            if(reusablePixels==null)reusablePixels=new int[VisualDeclineDetector.WIDTH*VisualDeclineDetector.HEIGHT];
            int[] pixels=reusablePixels;
            resized.getPixels(pixels,0,VisualDeclineDetector.WIDTH,0,0,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT);
            if(resized!=source) resized.recycle();
            int preferredTop=SystemClock.elapsedRealtime()-recentCardAt<4500?recentCardTop:-1;
            VisualDeclineDetector.Match visual=VisualDeclineDetector.locateNear(pixels,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT,
                    template,darkTemplate,41,41,preferredTop);
            if(visual.found){recentCardTop=visual.cardTop;recentCardAt=SystemClock.elapsedRealtime();}
            else{recentCardTop=-1;visualFallback.reset();}
            final long visualReady=SystemClock.elapsedRealtime();
            // A visually verified Uber card takes priority even when DoorDash is the active root.
            // If the geometry is too different from the known template, never tap it.
            final boolean uberGeometry=Math.abs(w/(double)h-VisualDeclineDetector.WIDTH/(double)VisualDeclineDetector.HEIGHT)<=.025;
            if(!visual.found || !uberGeometry) {
                if(++uberAbsentFrames>=2){
                    if(uberAbsentFrames==2&&uberRetries.attempts()>0)status("Uber offer card no longer visible after "+uberRetries.attempts()+" decline attempt(s); dismissal observed, platform outcome unconfirmed.");
                    uberRetries.reset();autoDeclineSent=false;uberReadOnlyShowKey="";
                }
                // No competing Uber card in this fresh frame. Only now consider opening DoorDash.
                if(!visual.found && maybeOpenDash(SystemClock.elapsedRealtime())){
                    clearUberSession();uberVisibleLast=false;source.recycle();finishCapture(token);return;
                }
                if(foregroundHost()==OfferSurfacePolicy.Host.DOORDASH && Prefs.of(this).getBoolean(Prefs.DASH_ENABLED,true)) {
                    if(uberVisibleLast){clearUberSession();uberVisibleLast=false;}
                    processDashScreenshot(source,token,w,h);return;
                }
                if(foregroundHost()!=OfferSurfacePolicy.Host.UBER){hideOverlay();clearUberSession();uberVisibleLast=false;source.recycle();finishCapture(token);return;}
                hideOverlay();clearReadVerification();
                ++noOfferFrames;
                // Losing the offer card does NOT mean Accept was pressed.
                // We only mark Accepted after the next screen confirms the SAME
                // merchant is now assigned as an active pickup, twice.
                if(noOfferFrames==1)armPendingAcceptance();
                if(noOfferFrames>=2) {
                    uberVisibleLast=false;
                    lastAmount=Double.NaN;
                    lastX=-1;lastY=-1;
                    lastHistoryId=-1;
                    cachedMinutes=0;cachedMetres=0;
                    cachedPickup="";cachedDestination="";
                    lastOutcome=null;
                    clearReadVerification();
                    reviewAlerted=false;
                    autoDeclineSent=false;
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
            if(!Prefs.of(this).getBoolean(Prefs.UBER_ENABLED,true)) {
                hideOverlay();source.recycle();finishCapture(token);return;
            }
            uberAbsentFrames=0;
            int owner=windowAt(visual.x*w/VisualDeclineDetector.WIDTH,visual.y*h/VisualDeclineDetector.HEIGHT,UBER);
            if(!uberVisibleLast){hideOverlay();clearUberSession();uberVisibleLast=true;dashPrevious=null;dashConfirmPrevious=null;}
            else if(owner!=uberOfferWindow)status("Uber X window changed "+uberOfferWindow+" -> "+owner+"; fresh target verification required, financial session retained.");
            uberOfferWindow=owner;
            // Remember the actual top unknown window at capture time, not a guessed package.
            frameUnknownWindow=owner<0?unknownUberTarget(visual.x*w/686,visual.y*h/1536):-1;
            // Financial recognition remains independent of the target ownership.
            if(owner<0){String diag=windowDiagnostic(visual.x*w/686,visual.y*h/1536);Prefs.of(this).edit().putString("uber_last_windows_v25",diag).apply();status("Uber card visible over "+foregroundHost()+"; X ownership unavailable: "+diag);}
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
            if(payoutReadFailures>0&&Math.abs(payoutFailureX-visual.x)<=4&&Math.abs(payoutFailureY-visual.y)<=6&&payoutReadFailures%2==1){
                adjusted=Bitmap.createBitmap(ocrImage.getWidth(),ocrImage.getHeight(),Bitmap.Config.ARGB_8888);
                android.graphics.ColorMatrix contrast=new android.graphics.ColorMatrix(new float[]{1.5f,0,0,0,-64,0,1.5f,0,0,-64,0,0,1.5f,0,-64,0,0,0,1,0});
                android.graphics.Paint paint=new android.graphics.Paint();paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(contrast));
                new android.graphics.Canvas(adjusted).drawBitmap(ocrImage,0,0,paint);
            }else if(payoutReadFailures>=2&&Math.abs(payoutFailureX-visual.x)<=4&&Math.abs(payoutFailureY-visual.y)<=6&&ocrImage.getWidth()<=1000&&ocrImage.getHeight()<=1600){
                adjusted=Bitmap.createScaledBitmap(ocrImage,Math.round(ocrImage.getWidth()*1.25f),Math.round(ocrImage.getHeight()*1.25f),true);
            }else adjusted=ocrImage;
            final float ocrScaleX=adjusted.getWidth()/(float)ocrImage.getWidth(),ocrScaleY=adjusted.getHeight()/(float)ocrImage.getHeight();
            final com.google.android.gms.tasks.Task<Text> submitted;
            try{submitted=processOcr(adjusted);}catch(RuntimeException e){if(adjusted!=ocrImage)adjusted.recycle();if(ocrImage!=frame)ocrImage.recycle();throw e;}
            submitted.addOnSuccessListener(text -> {
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
                    ocrDone();if(adjusted!=ocrImage)adjusted.recycle();
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
        if(!isSupportedForeground() || !Prefs.of(this).getBoolean(Prefs.UBER_ENABLED,true)) {status("Uber offer no longer on supported surface; no action.");return;}
        SharedPreferences prefs=Prefs.of(this);
        if(!prefs.getBoolean(Prefs.MONITOR,false)||!prefs.getBoolean(Prefs.VISUAL_X,false)||prefs.getBoolean(Prefs.LEARNING,false)) return;
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
        int currentOwner=windowAt(visual.x*width/VisualDeclineDetector.WIDTH,visual.y*height/VisualDeclineDetector.HEIGHT,UBER);
        if(currentOwner!=uberOfferWindow){
            status("Uber window changed during OCR: "+uberOfferWindow+" -> "+currentOwner+"; financial read allowed, tap requires fresh target.");
            // Never tap a newly changed window using this frame. Reacquire on next capture.
            uberOfferWindow=-1;
        }
        if((currentOwner<0||foregroundHost()!=OfferSurfacePolicy.Host.UBER)&&!OfferScreenEvaluator.hasOfferIdentity(visual,lines)){
            hideOverlay();requestFastRetry(visual);status("Uber overlay identity not readable; rechecking; no action.");return;
        }
        OfferScreenEvaluator.Assessment a=OfferScreenEvaluator.assess(visual,lines);
        if(!a.valid) {
            if(Math.abs(payoutFailureX-visual.x)>4||Math.abs(payoutFailureY-visual.y)>6)payoutReadFailures=0;
            payoutFailureX=visual.x;payoutFailureY=visual.y;payoutReadFailures++;
            hideOverlay();
            requestFastRetry(visual);
            status("X found, but " + a.reason +
                    ". " + retryStatus() + ". No tap. " + timing);return;
        }
        payoutReadFailures=0;
        double payout=a.payout;
        long now=SystemClock.elapsedRealtime();
        // New offers are saved once. Including merchant identity prevents a
        // different offer with the same price and X location from bypassing
        // the first/second-frame verification requirement.
        OfferDetailsParser.Details parsed=OfferDetailsParser.parse(visual.y,lines);
        String kind=OfferScreenEvaluator.offerKind(visual,lines);
        timingLogUber(kind,a,parsed,currentOwner,visual);
        int unknownNow=currentOwner<0?unknownUberTarget(visual.x*width/686,visual.y*height/1536):-1;
        boolean identity=OfferScreenEvaluator.hasOfferIdentity(visual,lines);
        if(frameUnknownWindow>=0&&unknownNow==frameUnknownWindow&&OfferExtractionGuard.checkFinancial(parsed).valid&&parsed.deliveryCount>0)
            visualFallback.observe(unknownNow,foregroundHost().name(),kind,uberFinancialKey(payout,parsed),visual.x,visual.y,visual.cardTop,visual.confidence,identity,lastCaptureAt,now);
        else visualFallback.reset();
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
        boolean countRequired=tieredMode || activeRules.deliveriesOn;
        boolean sameOffer=lastHistoryId>0 && !Double.isNaN(lastAmount) &&
                Math.abs(lastAmount-payout)<.005 &&
                now-lastDecisionAt<REPEAT_GUARD_MS &&
                Math.abs(visual.x-lastX)<3 && Math.abs(visual.y-lastY)<3 &&
                cachedPickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","")
                    .equals(parsed.pickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","")) &&
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
                // Financial display is informational: incomplete merchant OCR must
                // not prevent a qualifying alert, while a decline still needs the
                // stricter two-frame offer identity and target ownership checks.
                OfferRuleEngine.Decision display=OfferRuleEngine.evaluate(activeRules,payout,parsed.minutes,parsed.metres,parsed.deliveryCount);
                if(display.outcome==OfferRuleEngine.Outcome.SHOW&&OfferExtractionGuard.checkFinancial(parsed).valid&&OfferScreenEvaluator.hasOfferIdentity(visual,lines)){
                    boolean shown=false;
                    if(prefs.getBoolean(Prefs.OVERLAY_ENABLED,true)){if(liveOverlay==null)liveOverlay=new OfferOverlay(this);shown=liveOverlay.show(display.hourly,display.perKm);}else hideOverlay();
                    String key=uberFinancialKey(payout,parsed);
                    if(!key.equals(uberReadOnlyShowKey)&&prefs.getBoolean(Prefs.ALERT,true)){if(shouldAlert("Uber",key,parsed.destination,now))OfferAlert.signal(this,payout);uberReadOnlyShowKey=key;}
                    status("Uber "+kind+" qualifies; "+(shown?"rate overlay shown":"overlay off/unavailable")+"; awaiting strict history identity. No tap. "+display.reason);
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
                    (cachedMinutes!=minutes || (activeRules.kilometreOn||activeRules.maxDistanceOn)&&cachedMetres!=metres || correctionVerifier.isPending())) {
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
                boolean prior=uberFinancialKey(payout,parsed).equals(uberReadOnlyShowKey)||!shouldAlert("Uber",uberFinancialKey(payout,parsed),parsed.destination,now);
                boolean started=prior||OfferAlert.signal(this,payout);
                uberReadOnlyShowKey=uberFinancialKey(payout,parsed);
                status(String.format(Locale.US,
                        "A$%.2f — QUALIFIES; %s. %s; %s; no tap.%s",
                        payout,decision.reason,overlayStatus,
                        prior?"Repeat alert suppressed":started?"Independent alert started":"Sound could not start",rates+" | "+timing));
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
        if(uberGestureBusy||dashGestureBusy||quickBusy)return;
        if(autoDeclineSent) {status("Uber decline already in flight or retry limit reached.");return;}
        String retryKey=String.format(Locale.US,"%.2f/%d/%d/%d/%s/%s",payout,parsed.minutes,parsed.metres,parsed.deliveryCount,parsed.type,parsed.pickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
        uberRetries.observe(retryKey,now);
        if(!uberRetries.canAttempt()){autoDeclineSent=true;status("Uber decline retry limit reached; no further taps.");return;}
        OfferRuleEngine.Config effective=Prefs.tieredEnabled(prefs)?Prefs.tieredRules(prefs).configFor(parsed.deliveryCount):Prefs.rules(prefs);
        if(effective==null){status("Uber effective rule tier unavailable; no tap.");return;}
        OfferRuleEngine.Decision fresh=OfferRuleEngine.evaluate(effective,payout,parsed.minutes,parsed.metres,parsed.deliveryCount);
        boolean bound=confirmedHourBound&&OfferSafeDecline.evaluate(effective,payout,parsed).outcome==OfferRuleEngine.Outcome.DECLINE;
        boolean countOrPrice=confirmedCountDecline&&(effective.deliveriesOn&&parsed.deliveryCount>0&&parsed.deliveryCount<effective.minDeliveries||Prefs.tieredEnabled(prefs)&&Prefs.tieredRules(prefs).provenPriceFailure(parsed.deliveryCount,payout));
        if(fresh.outcome!=OfferRuleEngine.Outcome.DECLINE&&!bound&&!countOrPrice){status("Uber final rule check: "+fresh.outcome+"; no tap. "+fresh.reason);return;}
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)||prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status("Automatic decline disabled or TEST ONLY enabled. No tap. "+timing);return;
        }
        if(!prefs.getBoolean(Prefs.VISUAL_X,true) || !isSupportedForeground()) {
            status("Visual monitoring or Uber Driver foreground not confirmed; no tap.");return;
        }
        OfferSurfacePolicy.Host host=foregroundHost();
        boolean reliableOfferText=OfferScreenEvaluator.hasOfferIdentity(visual,lines);
        float x=visual.x*width/(float)VisualDeclineDetector.WIDTH;
        float y=visual.y*height/(float)VisualDeclineDetector.HEIGHT;
        int ownerNow=windowAt((int)x,(int)y,UBER);
        boolean owned=uberOfferWindow>=0&&ownerNow==uberOfferWindow&&CrossPlatformPolicy.allowUberDecline(host,true,reliableOfferText,visual.confidence);
        int unknownNow=ownerNow<0?unknownUberTarget((int)x,(int)y):-1;
        boolean visualVerified=ownerNow<0&&unknownNow==frameUnknownWindow&&
                visualFallback.ready(unknownNow,host.name(),OfferScreenEvaluator.offerKind(visual,lines),uberFinancialKey(payout,parsed),visual.x,visual.y,visual.cardTop,visual.confidence,reliableOfferText,lastCaptureAt,SystemClock.elapsedRealtime())&&fresh.outcome==OfferRuleEngine.Outcome.DECLINE;
        if(!prefs.getBoolean(Prefs.UBER_ENABLED,true)||(!owned&&!visualVerified)){
            status("Uber target not verified: owner="+ownerNow+" unknown="+unknownNow+" visualFrames="+visualFallback.frames()+"; no tap. "+timing);
            if(ownerNow<0&&unknownNow<0&&host!=OfferSurfacePolicy.Host.UBER&&reliableOfferText&&visual.confidence>=.94&&
                    prefs.getBoolean(Prefs.AUTO_SWITCH,false)&&!knownUberActive&&!knownDashActive&&dashStage==0&&uberRetries.canFallback()){
                uberRetries.fallback();status("Uber target unavailable; attempting native app fallback, no overlay tap.");
                if(!clickUberBubble())launchPackage(UBER);
            }
            requestFastRetry(visual);return;
        }
        if(SystemClock.elapsedRealtime()-lastCaptureAt>1900){status("Uber frame stale; reacquiring fresh X before retry.");requestFastRetry(visual);return;}
        if(visualVerified)status("Uber visual fallback VERIFIED: two matching financial frames; unexposed top window="+unknownNow+"; X="+visual.x+","+visual.y);
        if(!uberRetries.ready(now)){requestFastRetry(visual);return;}
        if(uberRetries.attempts()==2&&host!=OfferSurfacePolicy.Host.UBER&&prefs.getBoolean(Prefs.AUTO_SWITCH,false)&&!knownUberActive&&!knownDashActive&&dashStage==0&&uberRetries.canFallback()){
            uberRetries.fallback();status("Uber overlay remains after two attempts; trying native Uber before final attempt.");
            if(clickUberBubble()||launchPackage(UBER)){requestFastRetry(visual);return;}
        }
        int attempt=uberRetries.attempt(now);
        AccessibilityNodeInfo direct=owned?findUberDeclineNode(uberOfferWindow,(int)x,(int)y):null;
        if(direct!=null&&direct.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
            autoDeclineSent=true;try(OfferHistoryStore store=new OfferHistoryStore(this)){store.markDeclineAttempt(lastHistoryId);}catch(Exception ignored){}
            status("Uber "+OfferScreenEvaluator.offerKind(visual,lines)+" decline attempt "+attempt+"/3 via verified accessibility click; checking dismissal.");
            autoDeclineSent=!uberRetries.canAttempt();requestFastRetry(visual);return;
        }
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,UberDeclineRetry.holdMillis(attempt))).build();
        final long declinedHistoryId=lastHistoryId;
        final String finalTiming=timing;
        uberGestureBusy=true;
        final long gestureGeneration=++uberGestureGeneration;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if(gestureGeneration!=uberGestureGeneration)return;
                uberGestureBusy=false;autoDeclineSent=!uberRetries.canAttempt();requestFastRetry(visual);scheduleFastVerify();
                status(String.format(Locale.US,
                        "A$%.2f: X gesture completed. Uber decline NOT independently confirmed. %s",
                        payout,finalTiming));
            }
            @Override public void onCancelled(GestureDescription gesture) {
                if(gestureGeneration!=uberGestureGeneration)return;
                uberGestureBusy=false;autoDeclineSent=!uberRetries.canAttempt();requestFastRetry(visual);scheduleFastVerify();
                status(String.format(Locale.US,
                        "A$%.2f: X gesture cancelled; decline not confirmed. %s",
                        payout,finalTiming));
            }
        },null);
        if(!accepted){uberGestureBusy=false;autoDeclineSent=!uberRetries.canAttempt();requestFastRetry(visual);}
        else handler.postDelayed(()->{if(uberGestureBusy&&gestureGeneration==uberGestureGeneration){uberGestureBusy=false;uberGestureGeneration++;autoDeclineSent=!uberRetries.canAttempt();requestFastRetry(visual);scheduleFastVerify();status("Uber gesture callback timed out; fresh offer verification before retry.");}},2500);
        if(accepted) {
            autoDeclineSent=true;
            try(OfferHistoryStore store=new OfferHistoryStore(this)) {
                store.markDeclineAttempt(declinedHistoryId);
            } catch(Exception ignored) { /* Gesture status remains in diagnostics. */ }
        }
        status(String.format(Locale.US,accepted?
                "A$%.2f: X tap dispatched (%s, %s); Uber decline unconfirmed. %s":
                "A$%.2f: Android rejected X gesture; no tap. (%s, %s). %s",
                payout,host,visual.theme,finalTiming+" | attempt "+attempt+"/3"));
    }

    private void clearUberSession() {
        visualFallback.reset();clearReadVerification();clearPendingAcceptance();lastAmount=Double.NaN;lastHistoryId=-1;
        lastX=-1;lastY=-1;cachedMinutes=0;cachedMetres=0;cachedDeliveryCount=0;
        cachedPickup="";cachedDestination="";lastOutcome=null;reviewAlerted=false;autoDeclineSent=false;
        confirmedHourBound=false;confirmedCountDecline=false;confirmedLowerHours=0;noOfferFrames=0;
    }
    /** The topmost window at the exact point must be owned by the expected app.
     * Never use a stale package name or a coordinate alone as authority to click. */
    private int windowAt(int x,int y,String expected) {
        if(quickControls!=null&&quickControls.covers(x,y))return -1;
        List<AccessibilityWindowInfo> windows=getWindows();
        if(windows==null)return -1;
        windows=new ArrayList<>(windows);
        windows.sort((a,b)->Integer.compare(b.getLayer(),a.getLayer()));
        Rect r=new Rect();
        for(AccessibilityWindowInfo win:windows){
            win.getBoundsInScreen(r);if(!r.contains(x,y))continue;
            // On Android 11+ the region is more precise than an overlay's rectangular bounds.
            if(Build.VERSION.SDK_INT>=30){Region region=new Region();win.getRegionInScreen(region);if(!region.contains(x,y))continue;}
            AccessibilityNodeInfo node=win.getRoot();
            if(node==null){AccessibilityNodeInfo active=getRootInActiveWindow();if(active!=null&&active.getWindowId()==win.getId())node=active;}
            String pkg=node==null||node.getPackageName()==null?"":node.getPackageName().toString();
            if(getPackageName().equals(pkg)&&win.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)continue;
            return expected.equals(pkg)?win.getId():-1;
        }
        return -1;
    }
    private int unknownUberTarget(int x,int y){
        if(foregroundHost()!=OfferSurfacePolicy.Host.DOORDASH||quickBusy||dashGestureBusy||dashStage==1||dashStage==2||
                (quickControls!=null&&quickControls.covers(x,y)))return -1;
        List<AccessibilityWindowInfo> all=getWindows();if(all==null)return -1;
        all=new ArrayList<>(all);all.sort((a,b)->Integer.compare(b.getLayer(),a.getLayer()));
        boolean uberPresent=false,hostBelow=false;int top=-1,topLayer=Integer.MIN_VALUE;
        for(AccessibilityWindowInfo win:all){
            Rect r=new Rect();win.getBoundsInScreen(r);AccessibilityNodeInfo root=win.getRoot();
            String pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
            if(UBER.equals(pkg)&&!r.isEmpty())uberPresent=true;
            if(!r.contains(x,y))continue;
            // Underlying host bounds establish the layer relationship even when its visible region excludes the overlay.
            if(top>=0&&win.getLayer()<topLayer&&OfferSurfacePolicy.DOORDASH.equals(pkg)){hostBelow=true;continue;}
            if(Build.VERSION.SDK_INT>=30){Region region=new Region();win.getRegionInScreen(region);if(!region.contains(x,y))continue;}
            if(top<0){
                // A named window (including our own controls or system UI) at the target blocks visual fallback.
                if(!pkg.isEmpty()||win.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD||win.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)return -1;
                if(r.width()<getResources().getDisplayMetrics().widthPixels*.35||r.height()<getResources().getDisplayMetrics().heightPixels*.22)return -1;
                top=win.getId();topLayer=win.getLayer();
            }else if(win.getLayer()<topLayer&&OfferSurfacePolicy.DOORDASH.equals(pkg)){hostBelow=true;}
            else if(!pkg.isEmpty()&&!UBER.equals(pkg))return -1;
        }
        return uberPresent&&hostBelow?top:-1;
    }
    private String uberFinancialKey(double payout,OfferDetailsParser.Details d){return String.format(Locale.US,"%.2f/%d/%d/%d/%s",payout,d.minutes,d.metres,d.deliveryCount,d.type);}
    private void timingLogUber(String kind,OfferScreenEvaluator.Assessment a,OfferDetailsParser.Details d,int owner,VisualDeclineDetector.Match v){
        SharedPreferences pref=Prefs.of(this);
        OfferRuleEngine.Config config=Prefs.tieredEnabled(pref)?Prefs.tieredRules(pref).configFor(d.deliveryCount):Prefs.rules(pref);
        OfferRuleEngine.Decision result=OfferRuleEngine.evaluate(config,a.payout,d.minutes,d.metres,d.deliveryCount);
        String line=String.format(Locale.US,"Uber %s | host=%s owner=%d X=%d,%d %.1f%% | raw [%s / %s] | orders=%d | %s | %s",kind,foregroundHost(),owner,v.x,v.y,v.confidence*100,a.rawPayout,d.rawDuration,d.deliveryCount,result.outcome,result.reason);
        if(!line.equals(pref.getString("uber_last_rule_v25","")))pref.edit().putString("uber_last_rule_v25",line).apply();
    }
    private String windowDiagnostic(int x,int y){
        StringBuilder b=new StringBuilder();List<AccessibilityWindowInfo> windows=getWindows();if(windows==null)return "no windows";
        for(AccessibilityWindowInfo win:windows){Rect rect=new Rect();win.getBoundsInScreen(rect);AccessibilityNodeInfo root=win.getRoot();
            String pkg=root==null||root.getPackageName()==null?"unexposed":root.getPackageName().toString();
            b.append(pkg).append("#").append(win.getId()).append(" layer=").append(win.getLayer()).append(" type=").append(win.getType()).append(" bounds=").append(rect.toShortString()).append(rect.contains(x,y)?" covers-X; ":"; ");
            if(b.length()>600)break;
        }return b.toString();
    }
    private AccessibilityNodeInfo findUberDeclineNode(int id,int x,int y){
        List<AccessibilityWindowInfo> windows=getWindows();if(windows==null)return null;
        for(AccessibilityWindowInfo w:windows)if(w.getId()==id){AccessibilityNodeInfo root=w.getRoot();if(root!=null&&root.getPackageName()!=null&&UBER.contentEquals(root.getPackageName()))return uberDeclineNode(root,x,y,0);}
        return null;
    }
    private AccessibilityNodeInfo uberDeclineNode(AccessibilityNodeInfo n,int x,int y,int depth){
        if(n==null||depth>24||!n.isVisibleToUser())return null;
        Rect b=new Rect();n.getBoundsInScreen(b);
        String t=String.valueOf(n.getText()).trim().toLowerCase(Locale.ROOT), d=String.valueOf(n.getContentDescription()).trim().toLowerCase(Locale.ROOT);
        if(n.isEnabled()&&n.isClickable()&&n.getPackageName()!=null&&UBER.contentEquals(n.getPackageName())&&b.contains(x,y)&&b.width()<getResources().getDisplayMetrics().widthPixels*.28&&b.height()<getResources().getDisplayMetrics().heightPixels*.16&&
          (t.matches("x|close|decline|dismiss|decline offer|dismiss offer|decline request")||d.matches("x|close|decline|dismiss|decline offer|dismiss offer|decline request")))return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=uberDeclineNode(n.getChild(i),x,y,depth+1);if(found!=null)return found;}return null;
    }
    private boolean dashVisible(int width,int height) {
        return foregroundHost()==OfferSurfacePolicy.Host.DOORDASH &&
                windowAt(width/2,(int)(height*.72),OfferSurfacePolicy.DOORDASH)>=0;
    }
    private List<OfferScreenEvaluator.Line> dashLines(Text text,int w,int h) {
        List<OfferScreenEvaluator.Line> result=new ArrayList<>();
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line l:block.getLines()){
            Rect b=l.getBoundingBox();if(b!=null)result.add(new OfferScreenEvaluator.Line(l.getText(),
                    Math.round(b.left*686f/w),Math.round(b.top*1536f/h),Math.round(b.right*686f/w),Math.round(b.bottom*1536f/h)));
        }
        return result;
    }
    private final java.util.LinkedHashMap<String,Long> recentAlerts=new java.util.LinkedHashMap<>();
    private boolean shouldAlert(String platform,String key,String destination,long now){
        String dest=OfferHistoryDedup.canonical(destination);if(dest.length()<8)return true;
        String full=platform+":"+key+":"+dest;Long last=recentAlerts.get(full);
        if(last!=null&&now-last<60000)return false;
        recentAlerts.put(full,now);
        while(recentAlerts.size()>64)recentAlerts.remove(recentAlerts.keySet().iterator().next());
        return true;
    }
    private void expireDashPending(long now,String reason){
        if(DoorDashPendingPolicy.expired(dashStage,dashStageAt,dashDeadline,now)){
            dashStage=3;status("DoorDash pending decline expired: "+reason+"; no final tap.");
        }
    }
    private void resetDash() {
        if(dashStage==1||dashStage==2)status("DoorDash pending sequence reset (stage="+dashStage+"): monitoring/idle/session change.");
        dashPrevious=null;dashPreviousAt=0;dashConfirmPrevious=null;dashConfirmAt=0;dashId=-1;
        dashStage=0;dashStageAt=0;dashKey="";dashMissing=0;dashWindow=-1;dashOfferWindow=-1;dashAlerted=false;dashDeadline=0;dashPendingRead=null;dashConfirmWindow=-1;dashShowAt=0;dashShowKey="";lastDashDecision="";dashStep1Retries=0;dashStep2Retries=0;dashProtectedRead=null;dashProtectedAt=0;dashProtectedRules="";dashMatchingReads=0;
    }
    private void queueDashScan(){handler.postDelayed(()->{if(isSupportedForeground()&&Prefs.of(this).getBoolean(Prefs.MONITOR,false)){if(tryDashAccessibilityConfirmation())return;scanIfDue();}},365);}
    private void queueDashConfirmation(){handler.postDelayed(()->{if(dashStage==1&&!tryDashAccessibilityConfirmation())scanIfDue();},120);}
    // Use visible text from Dasher's own accessibility tree when exposed. No screenshot
    // OCR is needed on the common path; OCR remains the fallback for custom layouts.
    private void collectDashLines(AccessibilityNodeInfo n,List<OfferScreenEvaluator.Line> lines,int w,int h,int depth){
        if(n==null||depth>30||lines.size()>250||!n.isVisibleToUser())return;
        if(n.getPackageName()!=null&&!OfferSurfacePolicy.DOORDASH.contentEquals(n.getPackageName()))return;
        CharSequence raw=n.getText();if(raw==null||raw.length()==0)raw=n.getContentDescription();
        if(raw!=null&&raw.length()>0&&raw.length()<250){
            Rect b=new Rect();n.getBoundsInScreen(b);
            if(b.width()>0&&b.height()>0&&b.height()<h*.25){
                OfferScreenEvaluator.Line l=new OfferScreenEvaluator.Line(raw.toString(),Math.round(b.left*686f/w),Math.round(b.top*1536f/h),Math.round(b.right*686f/w),Math.round(b.bottom*1536f/h));
                boolean duplicate=false;for(OfferScreenEvaluator.Line old:lines)if(old.text.equals(l.text)&&Math.abs(old.top-l.top)<12&&Math.abs(old.left-l.left)<12){duplicate=true;break;}
                if(!duplicate)lines.add(l);
            }
        }
        for(int i=0;i<n.getChildCount();i++)collectDashLines(n.getChild(i),lines,w,h,depth+1);
    }
    private List<OfferScreenEvaluator.Line> dashAccessibilityLines(int w,int h){
        List<OfferScreenEvaluator.Line> result=new ArrayList<>();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null&&root.getPackageName()!=null&&OfferSurfacePolicy.DOORDASH.contentEquals(root.getPackageName()))collectDashLines(root,result,w,h,0);
        return result;
    }
    private boolean tryDashAccessibilityConfirmation(){
        if(dashStage!=1||dashGestureBusy||uberGestureBusy||quickBusy||largeUberWindow()||uberVisibleLast)return false;
        int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;
        if(!dashVisible(w,h))return false;
        List<OfferScreenEvaluator.Line> lines=dashAccessibilityLines(w,h);
        DoorDashOffer.Confirmation c=DoorDashOffer.confirmation(lines);
        if(c==null)return false;
        int id=windowAt(pointX(c.target,w),pointY(c.target,h),OfferSurfacePolicy.DOORDASH);
        if(!canConfirmDash(c,id,SystemClock.elapsedRealtime()))return false;
        AccessibilityNodeInfo node=findDeclineNode(getRootInActiveWindow(),"decline offer",pointX(c.target,w),pointY(c.target,h),0);
        if(node==null||windowAt(pointX(c.target,w),pointY(c.target,h),OfferSurfacePolicy.DOORDASH)!=id||!dashTapEnabled(Prefs.of(this))||!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return false;
        watchdog.invalidate();finishDashConfirmation(id,"accessibility");queueDashScan();return true;
    }
    private void processDashScreenshot(Bitmap source,long token,int w,int h) {
        if(!dashVisible(w,h)){hideOverlay();dashPrevious=null;dashConfirmPrevious=null;source.recycle();finishCapture(token);return;}
        List<OfferScreenEvaluator.Line> accessible=dashAccessibilityLines(w,h);
        DoorDashOffer.Read accessibleRead=DoorDashOffer.parse(accessible);
        boolean nearDecline=accessibleRead!=null&&dashDecision(accessibleRead).outcome==OfferRuleEngine.Outcome.DECLINE&&OfferDecisionProtection.nearHourly(dashEffectiveConfig(accessibleRead),dashDecision(accessibleRead));
        if(DoorDashOffer.confirmation(accessible)!=null||dashIdleText(accessible)||accessibleRead!=null&&!nearDecline&&
                (accessibleRead.target!=null&&accessibleRead.seconds>0||dashDecision(accessibleRead).outcome==OfferRuleEngine.Outcome.SHOW)){
            try{onDashRead(accessible,w,h,"accessibility");}finally{source.recycle();finishCapture(token);if(dashStage==1||dashStage==2||dashPrevious!=null)queueDashScan();}return;
        }
        if(recognizer==null)recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        // The offer and confirmation panels are in the lower part of the screen.
        // Retain the top-right decline area in a compact composite for stage 1.
        final int top=(int)(h*.35),head=(int)(h*.16);
        final Bitmap cropped=Bitmap.createBitmap(w,h-top+head,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(cropped);
        if(head>0)canvas.drawBitmap(source,new Rect(0,0,w,head),new Rect(0,0,w,head),null);
        canvas.drawBitmap(source,new Rect(0,top,w,h),new Rect(0,head,w,h-top+head),null);
        final com.google.android.gms.tasks.Task<Text> submitted;
        try{submitted=processOcr(cropped);}catch(RuntimeException e){cropped.recycle();source.recycle();finishCapture(token);status("DoorDash OCR submission failed: "+e.getClass().getSimpleName());return;}
        submitted.addOnSuccessListener(t->{if(!ownsFreshCapture(token))return;try{
                List<OfferScreenEvaluator.Line> lines=new ArrayList<>();
                for(Text.TextBlock block:t.getTextBlocks())for(Text.Line l:block.getLines()){
                    Rect b=l.getBoundingBox();if(b==null||b.top<head&&b.bottom>head)continue;
                    int offset=b.top>=head?top-head:0;
                    lines.add(new OfferScreenEvaluator.Line(l.getText(),Math.round(b.left*686f/w),Math.round((b.top+offset)*1536f/h),Math.round(b.right*686f/w),Math.round((b.bottom+offset)*1536f/h)));
                }
                DoorDashOffer.Read optical=DoorDashOffer.parse(lines);
                if(accessibleRead!=null&&optical!=null&&!accessibleRead.financialKey().equals(optical.financialKey())){
                    dashPrevious=null;status("DoorDash accessibility/OCR financial disagreement; no automatic decline. "+accessibleRead.financialKey()+" vs "+optical.financialKey());
                    if(dashDecision(accessibleRead).outcome==OfferRuleEngine.Outcome.SHOW)onDashRead(accessible,w,h,"accessibility conflict — qualifying");
                    else if(dashDecision(optical).outcome==OfferRuleEngine.Outcome.SHOW)onDashRead(lines,w,h,"OCR conflict — qualifying");
                    dashPrevious=null;return;
                }
                onDashRead(lines,w,h,accessibleRead!=null&&optical!=null?"OCR + accessibility agree":"cropped OCR");
            }catch(Exception e){status("DoorDash read exception: "+e.getClass().getSimpleName());}})
            .addOnFailureListener(e->{if(ownsFreshCapture(token)){dashPrevious=null;dashConfirmPrevious=null;status("DoorDash OCR failed: "+e.getClass().getSimpleName());}})
            .addOnCompleteListener(t->{ocrDone();cropped.recycle();source.recycle();boolean active=watchdog.owns(token);finishCapture(token);if(active&&(dashStage==1||dashStage==2||dashPrevious!=null))queueDashScan();});
    }
    private boolean dashTapEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(Prefs.MONITOR,false)&&prefs.getBoolean(Prefs.VISUAL_X,false)&&
                prefs.getBoolean(Prefs.DASH_ENABLED,true)&&prefs.getBoolean(Prefs.AUTO_DECLINE,false)&&
                !prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)&&!prefs.getBoolean(Prefs.DASH_DRY,true)&&!prefs.getBoolean(Prefs.LEARNING,false);
    }
    private int pointX(OfferScreenEvaluator.Line l,int w){return Math.round((l.left+l.right)*w/(2f*686));}
    private int pointY(OfferScreenEvaluator.Line l,int h){return Math.round((l.top+l.bottom)*h/(2f*1536));}
    private boolean canConfirmDash(DoorDashOffer.Confirmation c,int id,long now){
        if(!dashTapEnabled(Prefs.of(this))||dashPendingRead==null||dashDecision(dashPendingRead).outcome!=OfferRuleEngine.Outcome.DECLINE)return false;
        return DoorDashPendingPolicy.timerMatches(dashStage,id,dashWindow,c.seconds,dashStageAt,dashDeadline,now,dashStep2Retries);
    }
    private void finishDashConfirmation(int id,String source){
        dashStage=2;dashStageAt=SystemClock.elapsedRealtime();dashConfirmPrevious=null;dashWindow=id;
        status("DoorDash step 2 dispatched via "+source+"; waiting for dismissal (not independently confirmed).");
    }
    private void onDashRead(List<OfferScreenEvaluator.Line> lines,int w,int h,String source) {
        long now=SystemClock.elapsedRealtime();SharedPreferences prefs=Prefs.of(this);
        if(!dashVisible(w,h)||!prefs.getBoolean(Prefs.MONITOR,false)||!prefs.getBoolean(Prefs.DASH_ENABLED,true)){hideOverlay();dashPrevious=null;dashConfirmPrevious=null;return;}
        DoorDashOffer.Confirmation confirmation=DoorDashOffer.confirmation(lines);
        if(confirmation!=null){
            hideOverlay();dashPrevious=null;dashMissing=0;
            int id=windowAt(pointX(confirmation.target,w),pointY(confirmation.target,h),OfferSurfacePolicy.DOORDASH);
            if(!canConfirmDash(confirmation,id,now)){
                status("DoorDash confirmation blocked: stage="+dashStage+"; window="+id+"/"+dashWindow+"; timer="+confirmation.seconds+"; remaining="+(dashDeadline>0?Math.max(0,dashDeadline-now)+"ms":"no pending deadline")+". No tap.");return;
            }
            // One fresh, positively identified confirmation is sufficient AFTER a verified stage 1.
            if(tapDash(confirmation.target,w,h,id,2)){if(dashStage==2)dashStep2Retries++;finishDashConfirmation(id,source);}
            return;
        }
        DoorDashOffer.Read read=DoorDashOffer.parse(lines);
        // A non-idle DoorDash screen might be an active delivery. Require a fresh
        // positively idle Uber screen before allowing other-app notification switches.
        if(read==null){StringBuilder text=new StringBuilder();for(OfferScreenEvaluator.Line l:lines)text.append(l.text).append(' ');if(CrossPlatformPolicy.activeDelivery(text.toString()))knownDashActive=true;}
        if(read==null){
            if(now-dashShowAt>2200||dashIdleText(lines)){hideOverlay();dashShowKey="";}
            dashPrevious=null;dashConfirmPrevious=null;
            // A missing/obscured OCR frame is not proof of decline. Require a recognised idle screen twice.
            if(dashIdleText(lines)){
                knownDashActive=false;
                if(++dashMissing>=2&&(dashStage!=1||now-dashStageAt>2000)){
                    if(dashStage==2&&dashId>0){try(OfferHistoryStore store=new OfferHistoryStore(this)){store.markDashDismissed(dashId);}catch(Exception ignored){}status("DoorDash dialog dismissed; decline remains unconfirmed.");}
                    boolean shouldReturn=dashStage==2&&!switchReturnPackage.isEmpty()&&prefs.getBoolean(Prefs.AUTO_RETURN,true)&&prefs.getBoolean(Prefs.AUTO_SWITCH,false)&&now-switchedAt<90000;
                    String previous=switchReturnPackage;switchReturnPackage="";
                    resetDash();if(shouldReturn&&dashVisible(w,h)&&!largeUberWindow()){
                        if(launchPackage(previous))status("DoorDash dismissed; previous application launch requested.");
                        else status("DoorDash dismissed; could not return automatically. Switch manually.");
                    }
                }
            }else dashMissing=0;
            expireDashPending(now,"unreadable or idle");if(dashStage==2&&now-dashStageAt>12000){dashStage=3;status("DoorDash dismissal not confirmed; no repeat tap.");}
            return;
        }
        dashMissing=0;
        int window=windowAt(w/2,(int)(h*.72),OfferSurfacePolicy.DOORDASH);
        int targetWindow=read.target==null?-1:windowAt(pointX(read.target,w),pointY(read.target,h),OfferSurfacePolicy.DOORDASH);
        boolean stable=DoorDashOffer.agrees(dashPrevious,read,now-dashPreviousAt)&&dashOfferWindow==window&&window>=0;
        boolean newOffer=!dashKey.equals(read.key())||(dashPrevious!=null&&read.seconds>0&&dashPrevious.seconds>0&&read.seconds>dashPrevious.seconds+5);
        boolean verifiedNewAfterDispatch=dashStage==2&&stable&&dashPendingRead!=null&&
                !dashPendingRead.financialKey().equals(read.financialKey())&&read.actionIdentity&&read.seconds>=3&&now-dashStageAt>=1200;
        if(newOffer&&dashId>0&&DoorDashPendingPolicy.allowIdentityReset(dashStage,verifiedNewAfterDispatch)){
            status("DoorDash history/session reset: changed offer identity or timer (stage="+dashStage+").");
            DoorDashOffer.Read protectedBefore=dashProtectedRead;long protectedAt=dashProtectedAt;String protectedRules=dashProtectedRules;
            boolean sameProtected=OfferDecisionProtection.sameVisibleDash(protectedBefore,read,now-protectedAt);
            resetDash();stable=false;if(sameProtected){dashProtectedRead=protectedBefore;dashProtectedAt=protectedAt;dashProtectedRules=protectedRules;}
        }
        dashPrevious=read;dashPreviousAt=now;dashOfferWindow=window;dashMatchingReads=stable?dashMatchingReads+1:1;
        if(dashStage!=0){
            hideOverlay();
            if(dashStage==1&&(now-dashStageAt>12000||now>=dashDeadline))expireDashPending(now,"offer still visible");
            else if(dashStage==1&&dashStep1Retries<1&&now-dashStageAt>=1200&&stable&&dashPendingRead!=null&&dashPendingRead.key().equals(read.key())&&read.actionIdentity&&read.seconds>=3&&read.target!=null&&targetWindow==window&&window==dashWindow&&dashDecision(read).outcome==OfferRuleEngine.Outcome.DECLINE&&Math.abs(now+read.seconds*1000L-dashDeadline)<=4500){
                if(tapDash(read.target,w,h,window,1)){dashStep1Retries++;status("DoorDash step 1 retried once on same verified offer.");}
            }
            return;
        }
        OfferRuleEngine.Decision decision=dashDecision(read);
        String ruleSignature=OfferRuleEngine.summary(dashEffectiveConfig(read));
        if(decision.outcome==OfferRuleEngine.Outcome.DECLINE&&ruleSignature.equals(dashProtectedRules)&&OfferDecisionProtection.sameVisibleDash(dashProtectedRead,read,now-dashProtectedAt)){
            status("DoorDash previously qualified; conflicting read protected from decline. "+decision.reason);dashPrevious=null;return;
        }
        String rates=String.format(Locale.US,"DoorDash A$%.2f | %d min | %.1f km | A$%.2f/hr | A$%.2f/km",read.cents/100.0,read.minutes,read.metres/1000.0,read.cents*0.6/read.minutes,read.cents*10.0/read.metres);
        String explanation=rates+" | "+source+" raw ["+read.rawPrice+" / "+read.rawMetrics+"] | orders="+read.count+" | "+(prefs.getBoolean(Prefs.DASH_SEPARATE,false)?"DoorDash rules":"shared rules")+" | effective="+OfferRuleEngine.summary(dashEffectiveConfig(read))+" | "+decision.outcome+": "+decision.reason;
        if(!explanation.equals(lastDashDecision)){lastDashDecision=explanation;prefs.edit().putString(Prefs.DASH_LAST_RULE_DIAG,explanation).apply();status(explanation);}
        // Display valid qualifying rates immediately. A covered decline button or unreadable timer
        // is not a reason to suppress an informational overlay. Automatic rejection still needs two matching reads.
        if(decision.outcome==OfferRuleEngine.Outcome.SHOW){
            dashProtectedRead=read;dashProtectedAt=now;dashProtectedRules=ruleSignature;
            dashShowAt=now;
            if(prefs.getBoolean(Prefs.OVERLAY_ENABLED,true)){if(liveOverlay==null)liveOverlay=new OfferOverlay(this);if(!liveOverlay.show(decision.hourly,decision.perKm,true))status("DoorDash qualifies but financial overlay could not be attached.");else if(!read.financialKey().equals(dashShowKey))status("DoorDash qualifying rate overlay attached: "+rates);}else hideOverlay();
            if(!read.financialKey().equals(dashShowKey)&&prefs.getBoolean(Prefs.ALERT,true)&&shouldAlert("DoorDash",read.financialKey(),read.destination,now)){boolean sounded=OfferAlert.signal(this,read.cents/100.0);dashAlerted=true;status(rates+" | QUALIFIES; "+(sounded?"alert started":"alert failed")+"; "+ruleSignature);}
            dashShowKey=read.financialKey();
        }else{hideOverlay();dashShowKey="";}
        if(!stable){if(decision.outcome!=OfferRuleEngine.Outcome.SHOW)status(rates+" | waiting for second matching read; "+decision.reason);return;}
        if(dashId<=0){
            try(OfferHistoryStore store=new OfferHistoryStore(this)){dashId=store.record(read.cents,read.details(),read.rawPrice,read.metres);dashKey=read.key();}
            catch(Exception e){status("DoorDash history write failed; no tap.");return;}
        }
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,read.cents/100f).putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        if(decision.outcome==OfferRuleEngine.Outcome.SHOW)return;
        if(decision.outcome==OfferRuleEngine.Outcome.REVIEW||read.count==0){
            if(!dashAlerted&&prefs.getBoolean(Prefs.ALERT,true)){OfferAlert.signal(this,read.cents/100.0);dashAlerted=true;}
            status(rates+" | MANUAL REVIEW: "+decision.reason+"; no tap.");return;
        }
        if(!dashTapEnabled(prefs)){status(rates+" | FAILS rules; test mode or auto-decline off. "+decision.reason);return;}
        if(OfferDecisionProtection.nearHourly(dashEffectiveConfig(read),decision)&&dashMatchingReads<3&&!source.equals("OCR + accessibility agree")){
            status(rates+" | near threshold: third agreeing read required before decline.");return;
        }
        if(!read.actionIdentity||read.seconds<3||read.target==null||targetWindow!=window){status(rates+" | decline button/timer unavailable or covered; no tap.");return;}
        if(tapDash(read.target,w,h,window,1)){
            dashKey=read.key();dashWindow=window;dashStage=1;dashStageAt=now;dashConfirmPrevious=null;dashDeadline=now+read.seconds*1000L;dashPendingRead=read;dashConfirmWindow=-1;
            try(OfferHistoryStore store=new OfferHistoryStore(this)){store.markDeclineAttempt(dashId);}catch(Exception ignored){}
            status(rates+" | DoorDash step 1 dispatched. "+decision.reason+". Awaiting confirmation screen.");queueDashConfirmation();
        }
    }
    private OfferRuleEngine.Config dashEffectiveConfig(DoorDashOffer.Read read){
        SharedPreferences p=Prefs.of(this);if(p.getBoolean(Prefs.DASH_SEPARATE,false)&&!Prefs.tieredEnabled(Prefs.dashRules(this)))return null;
        SharedPreferences rules=Prefs.rulesFor(this,true);return Prefs.tieredEnabled(rules)?Prefs.tieredRules(rules).configFor(read.count):Prefs.rules(rules);
    }
    private OfferRuleEngine.Decision dashDecision(DoorDashOffer.Read read){
        SharedPreferences prefs=Prefs.of(this);
        if(prefs.getBoolean(Prefs.DASH_SEPARATE,false)&&!Prefs.tieredEnabled(Prefs.dashRules(this)))return new OfferRuleEngine.Decision(OfferRuleEngine.Outcome.REVIEW,"Separate DoorDash rules enabled but not saved; save tiers or use shared rules",read.cents/100.0,read.cents*.6/read.minutes,read.cents*10.0/read.metres);
        SharedPreferences rules=Prefs.rulesFor(this,true);
        return Prefs.tieredEnabled(rules)?Prefs.tieredRules(rules).evaluate(read.cents/100.0,read.minutes,read.metres,read.count):OfferRuleEngine.evaluate(Prefs.rules(rules),read.cents/100.0,read.minutes,read.metres,read.count);
    }
    private boolean tapDash(OfferScreenEvaluator.Line line,int w,int h,int window,int stage){
        SharedPreferences p=Prefs.of(this);int px=pointX(line,w),py=pointY(line,h);
        String label=line.text.trim().toLowerCase(Locale.ROOT);
        if(!dashTapEnabled(p)||dashGestureBusy||!dashVisible(w,h)||window<0||windowAt(px,py,OfferSurfacePolicy.DOORDASH)!=window||
                SystemClock.elapsedRealtime()-lastCaptureAt>2200||
                (stage==1&&!label.equals("decline"))||(stage==2&&!label.matches("decline offer(?:\\s+\\d{1,2}:\\d{2})?")))return false;
        // Coordinates come from OCR of this fresh, verified label, never a static point.
        // If accessibility exposes the same clickable button use its exact click action.
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null&&root.getPackageName()!=null&&OfferSurfacePolicy.DOORDASH.contentEquals(root.getPackageName())){
            String expected=stage==1?"decline":"decline offer";
            AccessibilityNodeInfo node=findDeclineNode(root,expected,px,py,0);
            if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){queueDashScan();return true;}
        }
        Path path=new Path();path.moveTo(px,py);
        GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,75)).build();
        dashGestureBusy=true;final long gestureId=++dashGestureGeneration;
        boolean dispatched=dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){if(gestureId!=dashGestureGeneration)return;dashGestureBusy=false;queueDashScan();}
            @Override public void onCancelled(GestureDescription g){if(gestureId!=dashGestureGeneration)return;dashGestureBusy=false;status("DoorDash gesture cancelled; fresh same-offer check before bounded retry.");queueDashScan();}
        },handler);
        if(!dispatched)dashGestureBusy=false;
        else handler.postDelayed(()->{if(dashGestureBusy&&gestureId==dashGestureGeneration){dashGestureBusy=false;dashGestureGeneration++;status("DoorDash gesture callback timed out; preserving session for fresh dialog verification.");queueDashConfirmation();}},3000);
        return dispatched;
    }
    private AccessibilityNodeInfo findDeclineNode(AccessibilityNodeInfo node,String expected,int px,int py,int depth){
        if(node==null||depth>25)return null;
        Rect rect=new Rect();node.getBoundsInScreen(rect);
        String s=String.valueOf(node.getText()).trim().toLowerCase(Locale.ROOT),d=String.valueOf(node.getContentDescription()).trim().toLowerCase(Locale.ROOT);
        if(node.isVisibleToUser()&&node.isEnabled()&&rect.contains(px,py)&&(s.equals(expected)||d.equals(expected)||expected.equals("decline offer")&&(s.matches("decline offer\\s+\\d{1,2}:\\d{2}")||d.matches("decline offer\\s+\\d{1,2}:\\d{2}")))){
            if(node.isClickable())return node;
            AccessibilityNodeInfo parent=node.getParent();
            if(parent!=null&&parent.isClickable()&&parent.isEnabled()&&parent.isVisibleToUser()&&parent.getPackageName()!=null&&OfferSurfacePolicy.DOORDASH.contentEquals(parent.getPackageName())){
                Rect bounds=new Rect();parent.getBoundsInScreen(bounds);if(bounds.contains(px,py)&&bounds.height()<getResources().getDisplayMetrics().heightPixels*.18)return parent;
            }
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findDeclineNode(node.getChild(i),expected,px,py,depth+1);if(found!=null)return found;}return null;
    }
    private boolean dashIdleText(List<OfferScreenEvaluator.Line> lines){
        StringBuilder b=new StringBuilder();for(OfferScreenEvaluator.Line l:lines)b.append(' ').append(l.text.toLowerCase(Locale.ROOT));String t=b.toString();
        return t.contains("this dash")&&(t.contains("looking for orders")||t.contains("finding offers")||t.contains("finding orders")||t.contains("waiting for orders"))&&
                !t.contains("customer drop-off")&&!t.contains("decline offer")&&!t.contains("confirm pickup")&&!t.contains("directions");
    }
    private void collectIdleText(AccessibilityNodeInfo n,StringBuilder b,int depth){if(n==null||depth>20||b.length()>10000)return;if(n.isVisibleToUser()){if(n.getText()!=null)b.append(' ').append(n.getText());if(n.getContentDescription()!=null)b.append(' ').append(n.getContentDescription());}for(int i=0;i<n.getChildCount();i++)collectIdleText(n.getChild(i),b,depth+1);}
    private void updateActiveDelivery(String text){
        if(CrossPlatformPolicy.activeDelivery(text))knownUberActive=true;
        else if(CrossPlatformPolicy.idleUber(text)||text.toLowerCase(Locale.ROOT).contains("you're offline")||text.toLowerCase(Locale.ROOT).contains("you are offline"))knownUberActive=false;
    }
    private boolean maybeOpenDash(long now){
        SharedPreferences p=Prefs.of(this);
        String previous=activePackage();OfferSurfacePolicy.Host host=OfferSurfacePolicy.classify(previous);
        if(host==OfferSurfacePolicy.Host.DOORDASH){p.edit().remove(Prefs.DASH_NOTICE).apply();return false;}
        if(host==OfferSurfacePolicy.Host.UBER&&!p.getBoolean(Prefs.UBER_ENABLED,true))return false;
        if(host==OfferSurfacePolicy.Host.UBER){StringBuilder b=new StringBuilder();collectIdleText(getRootInActiveWindow(),b,0);updateActiveDelivery(b.toString());}
        long notice=p.getLong(Prefs.DASH_NOTICE,0);
        if(!p.getBoolean(Prefs.AUTO_SWITCH,false)||!p.getBoolean(Prefs.DASH_ENABLED,true)||!p.getBoolean(Prefs.MONITOR,false)||!CrossPlatformPolicy.freshNotice(notice,now))return false;
        boolean busy=quickBusy||dashGestureBusy||uberGestureBusy||dashStage==1||dashStage==2||pendingAcceptanceId>0||largeUberWindow()&&host!=OfferSurfacePolicy.Host.UBER;
        if(previous.isEmpty()||getPackageName().equals(previous)||now-lastSwitchAttempt<2000)return false;
        if(!CrossPlatformPolicy.allowDashSwitch(host,p.getBoolean(Prefs.SWITCH_OTHER_IDLE,true),(knownUberActive||knownDashActive),busy)){
            status("DoorDash New Delivery! switch blocked: host="+host+"; active delivery evidence="+(knownUberActive||knownDashActive)+"; competing action="+busy+"; other-app switching="+p.getBoolean(Prefs.SWITCH_OTHER_IDLE,true));return false;
        }
        lastSwitchAttempt=now;p.edit().remove(Prefs.DASH_NOTICE).apply();
        status("DoorDash New Delivery! matched; requesting launch from "+previous);
        dashLaunchUsedIntent=OfferNotificationListener.openLatest(this,now);
        if(dashLaunchUsedIntent||launchPackage(OfferSurfacePolicy.DOORDASH)){
            p.edit().putString(Prefs.DASH_NOTICE_DIAG,"New Delivery! matched; DoorDash opening requested from "+previous).apply();
            switchReturnPackage="com.android.systemui".equals(previous)?"":previous;switchedAt=now;dashLaunchVerifyAt=now+4000;return true;
        }
        p.edit().putString(Prefs.DASH_NOTICE_DIAG,"Matched New Delivery! but launch failed").apply();status("DoorDash launch failed; Android may require tapping the notification.");return false;
    }
    private boolean launchPackage(String pkg){
        try{
            Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent!=null){intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);return true;}
            // Android 13+ can reopen a known previous package without broad package visibility.
            if(Build.VERSION.SDK_INT>=33){getPackageManager().getLaunchIntentSenderForPackage(pkg).sendIntent(this,0,null,null,null);return true;}
            return false;
        }catch(Exception e){status("Android prevented opening "+pkg+"; open manually.");return false;}
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
        long time=System.currentTimeMillis();
        SharedPreferences.Editor edit=p.edit().putString(Prefs.LIVE_STATUS,msg)
                .putLong(Prefs.LIVE_TIME,time);
        if(!msg.equals(old)) {
            String stamp=DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(time));
            String previous=p.getString(Prefs.DIAG_HISTORY,"");
            String[] entries=previous.split("\n");
            StringBuilder b=new StringBuilder(stamp).append(" | ").append(msg);
            for(int i=0;i<Math.min(entries.length,119);i++) {
                if(!entries[i].isEmpty()) b.append('\n').append(entries[i]);
            }
            edit.putString(Prefs.DIAG_HISTORY,b.toString());
        }
        edit.apply();
    }

    @Override public void onInterrupt() {
        Prefs.of(this).edit().putString(Prefs.HEALTH_STATE,"Accessibility interrupted").apply();
        watchdog.invalidate();
        stopQuick();
        resetDash();hideOverlay();clearPendingAcceptance();clearReadVerification();
        status("Accessibility interrupted. Monitoring paused; service reconnect may be required.");
    }
    @Override public boolean onUnbind(android.content.Intent intent) {
        Prefs.of(this).edit().putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,false)
                .putString(Prefs.HEALTH_STATE,"Accessibility unbound").apply();
        watchdog.invalidate();
        stopQuick();
        handler.removeCallbacks(monitorLoop);
        handler.removeCallbacks(fastVerifyRunnable);
        hideOverlay();clearPendingAcceptance();clearReadVerification();
        status("Accessibility unbound by Android; automatic restart is not permitted if disabled.");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        Prefs.of(this).edit().putBoolean(Prefs.HEALTH_SERVICE_ACTIVE,false)
                .putString(Prefs.HEALTH_STATE,"Accessibility service stopped").apply();
        watchdog.invalidate();
        stopQuick();
        handler.removeCallbacks(monitorLoop);
        resetDash();hideOverlay();clearPendingAcceptance();clearReadVerification();
        if(recognizer!=null) recognizer.close();
        status("Accessibility service stopped. Re-enable if monitoring is needed.");
        super.onDestroy();
    }
}
