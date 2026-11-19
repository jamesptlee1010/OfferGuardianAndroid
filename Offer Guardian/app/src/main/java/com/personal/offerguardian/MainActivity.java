package com.personal.offerguardian;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Self-contained Android UI, deliberately avoiding external dependencies. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(19, 31, 47);
    private static final int MUTED = Color.rgb(97, 112, 130);
    private static final int ACCENT = Color.rgb(18, 102, 179);
    private static final int BG = Color.rgb(245, 247, 250);
    private SharedPreferences prefs;
    private TextView accessStatus, lastStatus, historyStatus;
    private Switch monitorSwitch, autoSwitch, alertSwitch, visualSwitch, visualTestSwitch;
    private EditText amountInput;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Prefs.of(this);
        // The old learning state could permanently block monitoring. Migrate to
        // screenshot matching, with NO AUTOMATIC TAPS until explicitly tested.
        if (!prefs.getBoolean(Prefs.MIGRATED_V03,false)) {
            prefs.edit().putBoolean(Prefs.MIGRATED_V03,true)
                    .putBoolean(Prefs.VISUAL_X,true)
                    .putBoolean(Prefs.LEARNING,false)
                    .putBoolean(Prefs.PENDING,false)
                    .putBoolean(Prefs.AUTO_DECLINE,false)
                    .putBoolean(Prefs.VISUAL_DRY_RUN,true)
                    .apply();
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        requestNotificationsIfAppropriate();
        drawUi();
    }

    private void requestNotificationsIfAppropriate() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private int dp(int size) { return Math.round(getResources().getDisplayMetrics().density * size); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int height, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height < 0 ? height : dp(height));
        p.bottomMargin = dp(bottom);
        return p;
    }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(content);
        v.setTextColor(color);
        v.setTextSize(sp);
        v.setLineSpacing(dp(2), 1f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout card(LinearLayout parent, String heading, String description) {
        LinearLayout card = column();
        card.setBackground(bg(Color.WHITE, 16));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        parent.addView(card, lp(-2, 14));
        card.addView(text(heading, 18, INK, true), lp(-2, 6));
        if (description != null) card.addView(text(description, 13, MUTED, false), lp(-2, 12));
        return card;
    }

    private Button button(LinearLayout parent, String label, boolean filled, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(filled ? Color.WHITE : ACCENT);
        b.setBackgroundTintList(ColorStateList.valueOf(filled ? ACCENT : Color.rgb(232, 241, 251)));
        b.setOnClickListener(listener);
        parent.addView(b, lp(50, 8));
        return b;
    }

    private Switch toggle(LinearLayout parent, String title, boolean value) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(15);
        s.setTextColor(INK);
        s.setPadding(0, dp(7), 0, dp(7));
        s.setChecked(value);
        parent.addView(s, lp(-2, 6));
        return s;
    }

    private void drawUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        setContentView(scroll);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(27), dp(16), dp(18));
        scroll.addView(root);

        TextView title = text("OFFER GUARDIAN", 25, INK, true);
        root.addView(title, lp(-2, 3));
        root.addView(text("v0.3.0  ·  Accept + Match  ·  Australia", 13, MUTED, false), lp(-2, 20));

        LinearLayout settings = card(root, "Your delivery rule", "Minimum payout (AUD). Exactly A$30.00 counts as a high offer.");
        amountInput = new EditText(this);
        amountInput.setSingleLine(true);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setText(String.format(Locale.US, "%.2f", Prefs.threshold(prefs)));
        amountInput.setTextSize(26);
        amountInput.setTextColor(INK);
        settings.addView(amountInput, lp(56, 8));
        button(settings, "Save minimum payout", true, v -> saveThreshold());
        monitorSwitch = toggle(settings, "Monitor Uber Driver offers", prefs.getBoolean(Prefs.MONITOR, false));
        autoSwitch = toggle(settings, "Auto-decline offers BELOW the minimum", prefs.getBoolean(Prefs.AUTO_DECLINE, false));
        alertSwitch = toggle(settings, "Ring + vibrate for offers AT / ABOVE minimum", prefs.getBoolean(Prefs.ALERT, true));
        monitorSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(Prefs.MONITOR, checked).apply();
            if (checked && !accessibilityEnabled()) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable Accessibility Service")
                        .setMessage("Android requires you to enable 'Offer Guardian – Uber screen monitor' in Accessibility settings. The service reads Uber Driver's visible screen only. Without permission, filtering will not work.")
                        .setPositiveButton("Open settings", (d, which) -> openAccessibility())
                        .setNegativeButton("Later", null).show();
            }
            refresh();
        });
        autoSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(Prefs.AUTO_DECLINE, checked).apply();
            if (checked && !prefs.getBoolean(Prefs.VISUAL_X, false)) {
                autoSwitch.setChecked(false);
                new AlertDialog.Builder(this).setTitle("Visual screen recognition required")
                        .setMessage("Enable 'Detect grey X and price' first. No button learning is required.")
                        .setPositiveButton("OK", null).show();
            }
            refresh();
        });
        alertSwitch.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(Prefs.ALERT, checked).apply());
        button(settings, "Test rules (Delivery + Match)", false, v -> testExamples());
        button(settings, "Test alert sound", false, v -> OfferAlert.signal(this, 36.00));

        LinearLayout access = card(root, "Android permission", "Open Android settings and enable the Offer Guardian accessibility service. Android may show a warning about sensitive screen access.");
        accessStatus = text("Checking service…", 14, MUTED, true);
        access.addView(accessStatus, lp(-2, 9));
        button(access, "Open Accessibility settings", false, v -> openAccessibility());
        button(access, "Open Uber Driver", false, v -> openUber());

        LinearLayout visual = card(root, "Recognise Uber offers on screen",
                "No button learning required. Detects the grey-square X and either a green or black offer-card border, then reads the price below it.");
        visualSwitch = toggle(visual, "Detect grey X and price from Uber offer screenshots",
                prefs.getBoolean(Prefs.VISUAL_X, false));
        visualTestSwitch = toggle(visual, "TEST ONLY — locate X without tapping (recommended first)",
                prefs.getBoolean(Prefs.VISUAL_DRY_RUN, true));
        visualSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(Prefs.VISUAL_X, checked)
                    .putBoolean(Prefs.LEARNING, false).putBoolean(Prefs.PENDING, false)
                    .apply();
            if (checked) new AlertDialog.Builder(this)
                    .setTitle("Visual X detection enabled")
                    .setMessage("This version recognises Accept and Match cards with green or black borders. The old button-learning feature is removed. Keep TEST ONLY on and look at Live diagnostics after an offer appears.")
                    .setPositiveButton("OK", null).show();
            refresh();
        });
        visualTestSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && prefs.getBoolean(Prefs.VISUAL_DRY_RUN,true)) {
                visualTestSwitch.setChecked(true);
                new AlertDialog.Builder(this)
                        .setTitle("Enable real Uber X taps?")
                        .setMessage("Only do this after you have verified that TEST ONLY reports a correct X location on multiple real offers. A visual recognition error could tap the wrong part of the Uber screen. Using automation may affect your Uber account.")
                        .setNegativeButton("Keep TEST ONLY", null)
                        .setPositiveButton("I understand — allow taps", (d,w) -> {
                            prefs.edit().putBoolean(Prefs.VISUAL_DRY_RUN, false).apply();
                            visualTestSwitch.setChecked(false);
                        }).show();
            } else prefs.edit().putBoolean(Prefs.VISUAL_DRY_RUN, checked).apply();
        });
        visual.addView(text("Testing: keep TEST ONLY on. Open Uber Driver and let an offer appear while safely parked. Return here to read exactly which stage passed or failed. Automatic taps require Auto-decline ON and TEST ONLY OFF.",
                13, MUTED, false), lp(-2,0));

        LinearLayout legacy = card(root, "No learning step necessary", null);
        legacy.addView(text("The original click-event learning was removed because Uber did not expose your X taps to Android. This version searches every fresh offer screenshot for the grey X and reads the payout from the same card.",
                13,MUTED,false),lp(-2,0));

        LinearLayout recent = card(root, "Live diagnostics", "Shows why the app did or did not detect an Uber offer. No text from maps, customer addresses or stores is saved.");
        lastStatus = text("No offer detected yet.", 14, INK, false);
        recent.addView(lastStatus, lp(-2, 12));
        recent.addView(text("Recent detection steps",14,MUTED,true),lp(-2,6));
        historyStatus=text("No scan activity yet.",12,MUTED,false);
        recent.addView(historyStatus,lp(-2,8));
        button(recent, "Refresh diagnostics", false, v -> refresh());
        button(recent, "Copy diagnostics for troubleshooting", false, v -> {
            String report="Offer Guardian v0.3.0\n"+
                    prefs.getString(Prefs.DIAG_HISTORY,"No diagnostics yet.");
            ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(manager!=null) {
                manager.setPrimaryClip(ClipData.newPlainText("Offer Guardian diagnostic history",report));
                Toast.makeText(this,"Diagnostics copied",Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout note = card(root, "Important limits", null);
        note.addView(text("The app checks a fresh screenshot for a grey X, matching green/black card border, and a single price in that card. Any uncertain stage means NO TAP. Keep TEST ONLY on until results are verified. It never accepts orders. Automation can affect account standing.",
                13, MUTED, false), lp(-2, 0));
        root.addView(text("Independent personal utility; not affiliated with or endorsed by Uber.",
                11, MUTED, false), lp(-2, 0));
        refresh();
    }

    private void saveThreshold() {
        try {
            float value = Float.parseFloat(amountInput.getText().toString().trim());
            if (Float.isNaN(value) || value < 1 || value > 1000) throw new NumberFormatException();
            prefs.edit().putFloat(Prefs.THRESHOLD, Math.round(value * 100f) / 100f).apply();
            Toast.makeText(this, String.format(Locale.US, "Minimum set to A$%.2f", value), Toast.LENGTH_LONG).show();
        } catch (NumberFormatException e) {
            new AlertDialog.Builder(this).setTitle("Invalid minimum")
                    .setMessage("Enter an AUD amount between $1 and $1,000 (for example, 30.00).")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void testExamples() {
        OfferDetector.Result low = OfferDetector.detect(Arrays.asList("Delivery", "$14.50", "Accept"));
        OfferDetector.Result high = OfferDetector.detect(Arrays.asList("Pack & Deliver (3)", "A$30.59", "Match"));
        float limit = Prefs.threshold(prefs);
        String message = String.format(Locale.US,
                "A$%.2f: %s\n\nA$%.2f: %s\n\nThis only tests payout logic. It does not press any Uber Driver buttons.",
                low.payout, low.payout < limit ? "BELOW threshold" : "AT / ABOVE threshold",
                high.payout, high.payout < limit ? "BELOW threshold" : "AT / ABOVE threshold");
        new AlertDialog.Builder(this).setTitle("Rule simulation")
                .setMessage(message).setPositiveButton("OK", null).show();
    }

    private boolean accessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null &&
                    getPackageName().equals(info.getResolveInfo().serviceInfo.packageName) &&
                    OfferAccessibilityService.class.getName().equals(info.getResolveInfo().serviceInfo.name))
                return true;
        }
        return false;
    }

    private void openAccessibility() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openUber() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.ubercab.driver");
        if (intent == null) {
            Toast.makeText(this, "Uber Driver app is not installed or cannot be found.", Toast.LENGTH_LONG).show();
        } else startActivity(intent);
    }

    private void refresh() {
        if (accessStatus == null) return;
        boolean access = accessibilityEnabled();
        accessStatus.setText(access ? "✓ Accessibility service ENABLED" : "! Accessibility service NOT ENABLED");
        accessStatus.setTextColor(access ? Color.rgb(22, 120, 79) : Color.rgb(181, 92, 32));
        long timestamp = prefs.getLong(Prefs.LIVE_TIME, 0);
        if (timestamp == 0) lastStatus.setText("No live scan yet. Enable Monitoring and Android Accessibility; open Uber Driver.");
        else {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(new Date(timestamp));
            lastStatus.setText(time+"\n"+prefs.getString(Prefs.LIVE_STATUS,"No scan status"));
        }
        if(historyStatus!=null) historyStatus.setText(prefs.getString(Prefs.DIAG_HISTORY,"No scan activity yet."));
    }

    @Override protected void onResume() {
        super.onResume();
        if (accessStatus != null) refresh();
    }
}
