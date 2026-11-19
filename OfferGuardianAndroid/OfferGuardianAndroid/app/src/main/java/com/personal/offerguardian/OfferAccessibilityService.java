package com.personal.offerguardian;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.Text;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works only with the public Android accessibility API and ONLY in the Uber Driver app.
 * It does not intercept traffic, call a private Uber API, or attempt to auto-accept orders.
 */
public final class OfferAccessibilityService extends AccessibilityService {
    private static final String UBER_PACKAGE = "com.ubercab.driver";
    private static final long SCAN_INTERVAL_MS = 350;
    private long lastScanAt = 0;
    private long lastOcrAt = 0;
    private boolean ocrInProgress = false;
    private TextRecognizer textRecognizer;
    private long lastValidOfferAt = 0;
    private double lastHandledPayout = Double.NaN;
    private long lastHandledAt = 0;

    private static final class Snapshot {
        final Set<String> labels = new LinkedHashSet<>();
        final List<AccessibilityNodeInfo> nodes = new ArrayList<>();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null ||
                !UBER_PACKAGE.contentEquals(event.getPackageName())) return;
        SharedPreferences prefs = Prefs.of(this);
        if (prefs.getBoolean(Prefs.LEARNING, false)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) captureClickedNode(event, prefs);
            return; // No automatic actions while learning the manual Decline control.
        }
        if (!prefs.getBoolean(Prefs.MONITOR, false) ||
                prefs.getBoolean(Prefs.PENDING, false)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastScanAt < SCAN_INTERVAL_MS) return;
        lastScanAt = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !UBER_PACKAGE.contentEquals(root.getPackageName())) return;
        Snapshot snapshot = new Snapshot();
        collect(root, snapshot, 0);
        OfferDetector.Result result = OfferDetector.detect(new ArrayList<>(snapshot.labels));
        if (!result.valid) {
            // Fallback when Uber renders the payout/Accept label as graphics.
            // Android screenshots are used only while the Uber Driver app is foreground.
            if (Build.VERSION.SDK_INT >= 30 && now - lastOcrAt >= 1200 && !ocrInProgress) {
                runOcrOnUberScreen();
            }
            // Keep recent payout cached to avoid repeated alerts from OCR redraws.
            // A new payout is handled immediately; the same payout is rearmed after 20 seconds.
            return;
        }
        handleOffer(result, snapshot.nodes, prefs);
    }

    private void handleOffer(OfferDetector.Result result,
                             List<AccessibilityNodeInfo> currentNodes, SharedPreferences prefs) {
        long now = SystemClock.elapsedRealtime();
        lastValidOfferAt = now;
        // Ignore frequent redraws / countdown changes for the same visible offer.
        if (!Double.isNaN(lastHandledPayout) &&
                Math.abs(result.payout - lastHandledPayout) < 0.005 &&
                now - lastHandledAt < 20000) return;
        lastHandledPayout = result.payout;
        lastHandledAt = now;

        float threshold = Prefs.threshold(prefs);
        if (result.payout >= threshold) {
            if (prefs.getBoolean(Prefs.ALERT, true)) {
                OfferAlert.signal(this, result.payout);
                updateStatus(prefs, result.payout, "High offer — sounded alert. Manual decision required.");
            } else updateStatus(prefs, result.payout, "High offer — alerts disabled. Manual decision required.");
        } else if (!prefs.getBoolean(Prefs.AUTO_DECLINE, false)) {
            updateStatus(prefs, result.payout, "Below minimum — automatic decline is off.");
        } else if (!prefs.getBoolean(Prefs.CALIBRATED, false)) {
            updateStatus(prefs, result.payout, "Below minimum — calibrate Decline control first.");
        } else {
            String resultMessage = clickCalibratedDecline(currentNodes, prefs);
            updateStatus(prefs, result.payout, resultMessage);
        }
    }

    /** Android 11+ screenshots are processed locally by bundled ML Kit OCR. */
    private void runOcrOnUberScreen() {
        ocrInProgress = true;
        lastOcrAt = SystemClock.elapsedRealtime();
        if (textRecognizer == null) {
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        if (Build.VERSION.SDK_INT < 30) { ocrInProgress = false; return; }
        try {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onFailure(int errorCode) {
                ocrInProgress = false;
            }

            @Override public void onSuccess(ScreenshotResult screenshot) {
                HardwareBuffer buffer = screenshot.getHardwareBuffer();
                if (buffer == null) { ocrInProgress = false; return; }
                Bitmap bitmap = null;
                try {
                    Bitmap hwBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                    if (hwBitmap != null) bitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Exception ignored) { }
                finally { buffer.close(); }
                if (bitmap == null) { ocrInProgress = false; return; }
                final Bitmap frame = bitmap;
                textRecognizer.process(InputImage.fromBitmap(frame, 0))
                        .addOnSuccessListener(result -> {
                            List<String> words = new ArrayList<>();
                            for (Text.TextBlock block : result.getTextBlocks()) {
                                words.add(block.getText());
                                for (Text.Line line : block.getLines()) words.add(line.getText());
                            }
                            // OCR findings are not persisted or transmitted.
                            OfferDetector.Result detected = OfferDetector.detect(words);
                            if (!detected.valid) return;
                            AccessibilityNodeInfo root = getRootInActiveWindow();
                            if (root == null || root.getPackageName() == null ||
                                    !UBER_PACKAGE.contentEquals(root.getPackageName())) return;
                            SharedPreferences prefs = Prefs.of(OfferAccessibilityService.this);
                            if (!prefs.getBoolean(Prefs.MONITOR, false) ||
                                    prefs.getBoolean(Prefs.LEARNING, false) ||
                                    prefs.getBoolean(Prefs.PENDING, false)) return;
                            Snapshot live = new Snapshot();
                            collect(root, live, 0);
                            handleOffer(detected, live.nodes, prefs);
                        })
                        .addOnCompleteListener(task -> {
                            frame.recycle();
                            ocrInProgress = false;
                        });
            }
        });
        } catch (Exception denied) {
            ocrInProgress = false;
        }
    }

    private void updateStatus(SharedPreferences prefs, double amount, String status) {
        prefs.edit().putFloat(Prefs.LAST_AMOUNT, (float) amount)
                .putString(Prefs.LAST_STATUS, status)
                .putLong(Prefs.LAST_TIME, System.currentTimeMillis()).apply();
    }

    private void collect(AccessibilityNodeInfo node, Snapshot snapshot, int depth) {
        if (node == null || depth > 22 || snapshot.nodes.size() >= 300) return;
        if (!node.isVisibleToUser()) return;
        snapshot.nodes.add(node);
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) snapshot.labels.add(text.toString());
        if (desc != null && desc.length() > 0) snapshot.labels.add(desc.toString());
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), snapshot, depth + 1);
        }
    }

    /** The user must manually press Decline once, then confirm the captured control in our UI. */
    private void captureClickedNode(AccessibilityEvent event, SharedPreferences prefs) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        Rect bounds = new Rect();
        source.getBoundsInScreen(bounds);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (bounds.isEmpty() || dm.widthPixels <= 0 || dm.heightPixels <= 0) return;
        String id = safe(source.getViewIdResourceName());
        String label = safe(source.getContentDescription());
        if (label.isEmpty()) label = safe(source.getText());
        prefs.edit()
                .putBoolean(Prefs.PENDING, true)
                .putBoolean(Prefs.LEARNING, false)
                .putString(Prefs.PENDING_ID, id)
                .putString(Prefs.PENDING_LABEL, label)
                .putString(Prefs.PENDING_CLASS, safe(source.getClassName()))
                .putFloat(Prefs.PENDING_X, (float) bounds.centerX() / dm.widthPixels)
                .putFloat(Prefs.PENDING_Y, (float) bounds.centerY() / dm.heightPixels)
                .putInt(Prefs.PENDING_WIDTH, dm.widthPixels)
                .putInt(Prefs.PENDING_HEIGHT, dm.heightPixels)
                .apply();
    }

    private static String safe(Object s) { return s == null ? "" : s.toString().trim(); }

    private String clickCalibratedDecline(List<AccessibilityNodeInfo> nodes, SharedPreferences prefs) {
        String id = prefs.getString(Prefs.CAL_ID, "");
        String label = prefs.getString(Prefs.CAL_LABEL, "");
        String calClass = prefs.getString(Prefs.CAL_CLASS, "");
        AccessibilityNodeInfo matched = findUnique(nodes, id, true, calClass);
        if (matched == null && !label.isEmpty()) matched = findUnique(nodes, label, false, calClass);
        if (matched != null) {
            AccessibilityNodeInfo target = matched;
            for (int i = 0; i < 7 && target != null; i++, target = target.getParent()) {
                if (target.isClickable()) {
                    boolean sent = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return sent ? "Below minimum — Decline click sent." :
                            "Below minimum — accessibility click was rejected by Uber.";
                }
            }
        }
        if (prefs.getBoolean(Prefs.COORD_FALLBACK, false)) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (dm.widthPixels != prefs.getInt(Prefs.CAL_WIDTH, -1) ||
                    dm.heightPixels != prefs.getInt(Prefs.CAL_HEIGHT, -1))
                return "Below minimum — screen size changed; tap blocked.";
            float x = prefs.getFloat(Prefs.CAL_X, -1) * dm.widthPixels;
            float y = prefs.getFloat(Prefs.CAL_Y, -1) * dm.heightPixels;
            if (x > 0 && y > 0 && x < dm.widthPixels && y < dm.heightPixels) {
                Path path = new Path();
                path.moveTo(x, y);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, 90)).build();
                boolean sent = dispatchGesture(gesture, null, null);
                return sent ? "Below minimum — calibrated screen tap sent (verify result)." :
                        "Below minimum — calibrated screen tap was rejected.";
            }
        }
        return "Below minimum — decline control not found; no tap performed.";
    }

    /** IDs take priority; all matches must be unique to avoid clicking random controls. */
    private AccessibilityNodeInfo findUnique(List<AccessibilityNodeInfo> nodes,
                                              String key, boolean byId, String calClass) {
        if (key == null || key.isEmpty()) return null;
        AccessibilityNodeInfo found = null;
        for (AccessibilityNodeInfo node : nodes) {
            if (!node.isVisibleToUser()) continue;
            String value = byId ? safe(node.getViewIdResourceName()) :
                    safe(node.getContentDescription()).isEmpty() ? safe(node.getText()) :
                            safe(node.getContentDescription());
            if (!key.equals(value)) continue;
            if (!calClass.isEmpty() && !calClass.equals(safe(node.getClassName()))) continue;
            if (found != null) return null; // ambiguous -> skip safely
            found = node;
        }
        return found;
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        super.onDestroy();
        if (textRecognizer != null) textRecognizer.close();
    }
}
