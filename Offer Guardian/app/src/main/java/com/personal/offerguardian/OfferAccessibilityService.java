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
 * The detection path is always: Uber foreground -> fresh screenshot -> grey X
 * plus an actual offer-card border -> OCR payout INSIDE the same card -> rule.
 * No legacy click-event learning, hard-coded tap coordinates, or auto acceptance.
 */
public final class OfferAccessibilityService extends AccessibilityService {
    private static final String UBER = "com.ubercab.driver";
    private static final long PERIOD_MS = 1700;
    private static final long REPEAT_GUARD_MS = 25000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable monitorLoop = new Runnable() {
        @Override public void run() {
            try {
                SharedPreferences prefs=Prefs.of(OfferAccessibilityService.this);
                if (prefs.getBoolean(Prefs.MONITOR,false) &&
                        !prefs.getBoolean(Prefs.LEARNING,false)) {
                    if (!prefs.getBoolean(Prefs.VISUAL_X,false)) {
                        status("Visual recognition is OFF. Turn on grey-X recognition in the app.");
                    } else if (isUberForeground()) {
                        scanIfDue();
                    }
                }
            } catch (Exception e) {
                status("Monitor error: " + e.getClass().getSimpleName());
            } finally {
                handler.postDelayed(this, PERIOD_MS);
            }
        }
    };

    private TextRecognizer recognizer;
    private int[] template;
    private long lastCaptureAt=0;
    private boolean capturing=false;
    private double lastAmount=Double.NaN;
    private long lastDecisionAt=0;
    private int lastX=-1,lastY=-1;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        status("Accessibility connected. Waiting for Uber Driver offers.");
        handler.removeCallbacks(monitorLoop);
        handler.post(monitorLoop);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event==null || event.getPackageName()==null ||
                !UBER.contentEquals(event.getPackageName())) return;
        if (!Prefs.of(this).getBoolean(Prefs.MONITOR,false) ||
                !Prefs.of(this).getBoolean(Prefs.VISUAL_X,false)) return;
        // Some Uber map/offer redraws don't generate useful accessibility events.
        // This early trigger supplements the periodic screenshot poll.
        if (isUberForeground()) scanIfDue();
    }

    private boolean isUberForeground() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null && root.getPackageName()!=null &&
                UBER.contentEquals(root.getPackageName());
    }

    private void scanIfDue() {
        if (Build.VERSION.SDK_INT<30) {
            status("Android 11+ required for screen screenshots.");return;
        }
        long now=SystemClock.elapsedRealtime();
        if (capturing || now-lastCaptureAt<PERIOD_MS-200) return;
        lastCaptureAt=now;
        capturing=true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback() {
                @Override public void onFailure(int errorCode) {
                    capturing=false;
                    status("Screenshot permission / capture failed (Android code " +errorCode+").");
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
                    processScreenshot(copy);
                }
            });
        } catch(Exception e) {
            capturing=false;
            status("Screenshot exception: "+e.getClass().getSimpleName());
        }
    }

    private void processScreenshot(Bitmap source) {
        if (!isUberForeground() || !Prefs.of(this).getBoolean(Prefs.MONITOR,false)) {
            source.recycle();capturing=false;
            status("Uber left the foreground. No action.");return;
        }
        int w=source.getWidth(),h=source.getHeight();
        if(w<350 || h<700 || Math.abs(w/(double)h-
                VisualDeclineDetector.WIDTH/(double)VisualDeclineDetector.HEIGHT)>.018) {
            source.recycle();capturing=false;
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
            }
            Bitmap resized=Bitmap.createScaledBitmap(source,VisualDeclineDetector.WIDTH,
                    VisualDeclineDetector.HEIGHT,true);
            int[] pixels=new int[VisualDeclineDetector.WIDTH*VisualDeclineDetector.HEIGHT];
            resized.getPixels(pixels,0,VisualDeclineDetector.WIDTH,0,0,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT);
            if(resized!=source) resized.recycle();
            VisualDeclineDetector.Match visual=VisualDeclineDetector.locate(pixels,
                    VisualDeclineDetector.WIDTH,VisualDeclineDetector.HEIGHT,template,41,41);
            if(!visual.found) {
                source.recycle();capturing=false;
                status("Screenshot OK; no verified grey X on a green/black offer card. No tap.");return;
            }
            status(String.format(Locale.US,"Grey X found at (%d,%d), %.0f%%. Reading card payout...",
                    visual.x,visual.y,visual.confidence*100));
            if(recognizer==null) recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            final Bitmap frame=source;
            recognizer.process(InputImage.fromBitmap(frame,0))
                .addOnSuccessListener(text -> {
                    try {onOcrReady(text,visual,w,h);} catch(Exception e) {
                        status("Price-reading exception: "+e.getClass().getSimpleName());
                    }
                })
                .addOnFailureListener(err ->
                        status("Screenshot OK, X found, but OCR failed: "+err.getClass().getSimpleName()))
                .addOnCompleteListener(task -> {frame.recycle();capturing=false;});
        } catch(Exception e) {
            source.recycle();capturing=false;
            status("X detection exception: "+e.getClass().getSimpleName());
        }
    }

    private void onOcrReady(Text text,VisualDeclineDetector.Match visual,int width,int height) {
        if(!isUberForeground()) {status("Uber left foreground while reading; no action.");return;}
        SharedPreferences prefs=Prefs.of(this);
        if(!prefs.getBoolean(Prefs.MONITOR,false)) return;
        List<OfferScreenEvaluator.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:text.getTextBlocks()) {
            for(Text.Line line:block.getLines()) {
                Rect box=line.getBoundingBox();
                if(box==null) continue;
                // ML Kit bounding boxes use the original screenshot's pixel dimensions.
                lines.add(new OfferScreenEvaluator.Line(line.getText(),
                    Math.round(box.left*VisualDeclineDetector.WIDTH/(float)width),
                    Math.round(box.top*VisualDeclineDetector.HEIGHT/(float)height),
                    Math.round(box.right*VisualDeclineDetector.WIDTH/(float)width),
                    Math.round(box.bottom*VisualDeclineDetector.HEIGHT/(float)height)));
            }
        }
        OfferScreenEvaluator.Assessment a=OfferScreenEvaluator.assess(visual,lines);
        if(!a.valid) {status("X found, but " + a.reason + ". No tap.");return;}
        double payout=a.payout;
        double threshold=Prefs.threshold(prefs);
        long now=SystemClock.elapsedRealtime();
        // Guard repeated OCR frames of the same offer. The next genuinely different
        // payout is handled immediately; same payout re-arms after 25s.
        if(!Double.isNaN(lastAmount) && Math.abs(lastAmount-payout)<.005 &&
                now-lastDecisionAt<REPEAT_GUARD_MS &&
                Math.abs(visual.x-lastX)<3 && Math.abs(visual.y-lastY)<3) {
            status(String.format(Locale.US,
                    "A$%.2f: same offer already evaluated; suppressing repeat action.",payout));
            return;
        }
        lastAmount=payout;lastDecisionAt=now;lastX=visual.x;lastY=visual.y;
        prefs.edit().putFloat(Prefs.LAST_AMOUNT,(float)payout)
                .putLong(Prefs.LAST_TIME,System.currentTimeMillis()).apply();
        if(payout>=threshold) {
            if(prefs.getBoolean(Prefs.ALERT,true)) {
                OfferAlert.signal(this,payout);
                status(String.format(Locale.US,"A$%.2f >= A$%.2f. Alert sent; YOU decide. No tap.",payout,threshold));
            } else status(String.format(Locale.US,"A$%.2f >= A$%.2f. Alerts off; no tap.",payout,threshold));
            return;
        }
        if(prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
            status(String.format(Locale.US,"TEST ONLY: A$%.2f < A$%.2f. X at (%d,%d), %.0f%%. No tap.",
                    payout,threshold,visual.x,visual.y,visual.confidence*100));
            return;
        }
        if(!prefs.getBoolean(Prefs.AUTO_DECLINE,false)) {
            status(String.format(Locale.US,"A$%.2f below A$%.2f. Auto-decline OFF. No tap.",payout,threshold));
            return;
        }
        if(!prefs.getBoolean(Prefs.VISUAL_X,true) || !isUberForeground()) {
            status("Visual monitoring or Uber foreground not confirmed; no tap.");return;
        }
        if(SystemClock.elapsedRealtime()-lastCaptureAt>2500) {
            status("OCR took too long; screenshot is stale, so no tap.");return;
        }
        // The only gesture target comes from the freshly verified X on THIS screenshot.
        float x=visual.x*width/(float)VisualDeclineDetector.WIDTH;
        float y=visual.y*height/(float)VisualDeclineDetector.HEIGHT;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,90)).build();
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                status(String.format(Locale.US,"A$%.2f: X gesture completed. Uber decline NOT independently confirmed.",payout));
            }
            @Override public void onCancelled(GestureDescription gesture) {
                status(String.format(Locale.US,"A$%.2f: X gesture cancelled; decline not confirmed.",payout));
            }
        },null);
        status(String.format(Locale.US,accepted?"A$%.2f: X tap dispatched; awaiting Android response.":
                "A$%.2f: Android rejected X gesture; no tap.",payout));
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

    @Override public void onInterrupt() {status("Accessibility interrupted. Monitoring paused.");}
    @Override public void onDestroy() {
        handler.removeCallbacks(monitorLoop);
        if(recognizer!=null) recognizer.close();
        status("Accessibility service stopped. Re-enable if monitoring is needed.");
        super.onDestroy();
    }
}
