package com.personal.offerguardian;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
    private TextView accessStatus, calibrationStatus, lastStatus;
    private Button confirmButton, discardButton, learnButton;
    private Switch monitorSwitch, autoSwitch, alertSwitch, coordinateSwitch;
    private EditText amountInput;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Prefs.of(this);
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
        root.addView(text("Uber Eats payout filter  ·  Australia  ·  Private prototype", 13, MUTED, false), lp(-2, 20));

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
            if (checked && !prefs.getBoolean(Prefs.CALIBRATED, false)) {
                new AlertDialog.Builder(this).setTitle("Calibration required")
                        .setMessage("Automatic decline is not active until you teach the app the actual Decline control and approve the capture. No unknown buttons will be tapped.")
                        .setPositiveButton("OK", null).show();
            }
            refresh();
        });
        alertSwitch.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(Prefs.ALERT, checked).apply());
        button(settings, "Test example offers ($14.50 and $36.00)", false, v -> testExamples());
        button(settings, "Test alert sound", false, v -> OfferAlert.signal(this, 36.00));

        LinearLayout access = card(root, "Android permission", "Open Android settings and enable the Offer Guardian accessibility service. Android may show a warning about sensitive screen access.");
        accessStatus = text("Checking service…", 14, MUTED, true);
        access.addView(accessStatus, lp(-2, 9));
        button(access, "Open Accessibility settings", false, v -> openAccessibility());
        button(access, "Open Uber Driver", false, v -> openUber());

        LinearLayout calibration = card(root, "Learn the Decline button", "Some Uber screens use an X icon instead of a labelled Decline button. This lets you teach the app that control using one real, manual decline.");
        calibrationStatus = text("No calibration saved", 13, MUTED, false);
        calibration.addView(calibrationStatus, lp(-2, 10));
        learnButton = button(calibration, "1. Arm learning (then decline one real offer)", true, v -> armLearning());
        confirmButton = button(calibration, "2. Approve captured Decline control", true, v -> approveCandidate());
        discardButton = button(calibration, "Discard capture", false, v -> {
            prefs.edit().putBoolean(Prefs.PENDING, false).putBoolean(Prefs.LEARNING, false).apply();
            refresh();
        });
        coordinateSwitch = toggle(calibration, "Allow coordinate tap fallback (higher risk)",
                prefs.getBoolean(Prefs.COORD_FALLBACK, false));
        coordinateSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) prefs.edit().putBoolean(Prefs.COORD_FALLBACK, false).apply();
            else if (!prefs.getBoolean(Prefs.COORD_FALLBACK, false)) {
                coordinateSwitch.setChecked(false);
                new AlertDialog.Builder(this).setTitle("Coordinate-based taps")
                        .setMessage("If Uber hides its button from Accessibility, this feature taps the exact position you calibrated. It can hit the wrong control if Uber changes its layout. Only activate after testing on your exact phone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("I understand — enable", (d, which) -> {
                            prefs.edit().putBoolean(Prefs.COORD_FALLBACK, true).apply();
                            coordinateSwitch.setChecked(true);
                        }).show();
            }
        });
        button(calibration, "Clear saved calibration", false, v -> {
            new AlertDialog.Builder(this).setTitle("Clear calibration?")
                    .setMessage("Automatic declines will stop until you calibrate again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (d, which) -> {
                        prefs.edit().putBoolean(Prefs.CALIBRATED, false).apply();
                        refresh();
                    }).show();
        });

        LinearLayout recent = card(root, "Last recognised offer", "Local diagnostic only. No personal data leaves the phone.");
        lastStatus = text("No offer detected yet.", 14, INK, false);
        recent.addView(lastStatus, lp(-2, 4));
        button(recent, "Refresh status", false, v -> refresh());

        LinearLayout note = card(root, "Important limits", null);
        note.addView(text("This prototype only works when Uber exposes a readable payout and an Accept control. If Uber draws those as images, this version safely skips the offer rather than guessing. Uber's own notification may still sound before a decline. This app never accepts an order. Automatic interactions may carry Uber account risk.",
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
        OfferDetector.Result high = OfferDetector.detect(Arrays.asList("Delivery", "A$36.00", "Accept"));
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

    private void armLearning() {
        if (!accessibilityEnabled()) {
            new AlertDialog.Builder(this).setTitle("Enable screen access first")
                    .setMessage("Enable Offer Guardian in Android Accessibility settings before learning the Decline control.")
                    .setPositiveButton("Open settings", (d, w) -> openAccessibility())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Learn the real Decline control")
                .setMessage("1. Choose a real Uber Eats offer that you genuinely want to decline.\n\n2. Tap Start learning, switch to Uber Driver, and manually tap its Decline / X button once.\n\n3. Return here to inspect and approve the captured control.\n\nWhile learning, automatic declines are suspended. If Uber does not emit an accessible click event, learning may not work.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start learning", (d, w) -> {
                    prefs.edit().putBoolean(Prefs.LEARNING, true).putBoolean(Prefs.PENDING, false).apply();
                    refresh();
                    openUber();
                }).show();
    }

    private void approveCandidate() {
        if (!prefs.getBoolean(Prefs.PENDING, false)) return;
        String id = prefs.getString(Prefs.PENDING_ID, "");
        String label = prefs.getString(Prefs.PENDING_LABEL, "");
        String detail = "Captured app control:\n\nID: " + (id.isEmpty() ? "(none)" : id) +
                "\nLabel: " + (label.isEmpty() ? "(none)" : label) +
                "\n\nOnly approve if the action you just performed was DECLINE, not ACCEPT. For unlabelled controls, coordinate fallback requires a separate opt-in.";
        new AlertDialog.Builder(this).setTitle("Was that definitely Decline?")
                .setMessage(detail)
                .setNegativeButton("No — discard", (d, which) -> {
                    prefs.edit().putBoolean(Prefs.PENDING, false).apply();
                    refresh();
                })
                .setPositiveButton("Yes — save Decline", (d, which) -> {
                    prefs.edit()
                            .putString(Prefs.CAL_ID, prefs.getString(Prefs.PENDING_ID, ""))
                            .putString(Prefs.CAL_LABEL, prefs.getString(Prefs.PENDING_LABEL, ""))
                            .putString(Prefs.CAL_CLASS, prefs.getString(Prefs.PENDING_CLASS, ""))
                            .putFloat(Prefs.CAL_X, prefs.getFloat(Prefs.PENDING_X, -1))
                            .putFloat(Prefs.CAL_Y, prefs.getFloat(Prefs.PENDING_Y, -1))
                            .putInt(Prefs.CAL_WIDTH, prefs.getInt(Prefs.PENDING_WIDTH, -1))
                            .putInt(Prefs.CAL_HEIGHT, prefs.getInt(Prefs.PENDING_HEIGHT, -1))
                            .putBoolean(Prefs.CALIBRATED, true)
                            .putBoolean(Prefs.PENDING, false)
                            .apply();
                    refresh();
                }).show();
    }

    private void refresh() {
        if (accessStatus == null) return;
        boolean access = accessibilityEnabled();
        accessStatus.setText(access ? "✓ Accessibility service ENABLED" : "! Accessibility service NOT ENABLED");
        accessStatus.setTextColor(access ? Color.rgb(22, 120, 79) : Color.rgb(181, 92, 32));
        boolean pending = prefs.getBoolean(Prefs.PENDING, false);
        boolean learning = prefs.getBoolean(Prefs.LEARNING, false);
        boolean calibrated = prefs.getBoolean(Prefs.CALIBRATED, false);
        String summary;
        if (pending) summary = "Captured a control. Check it, then approve or discard below.";
        else if (learning) summary = "Learning armed. Manually tap DECLINE on one real Uber offer.";
        else if (calibrated) summary = "✓ Decline control calibrated. Auto-decline: " +
                (prefs.getBoolean(Prefs.AUTO_DECLINE, false) ? "ON" : "OFF");
        else summary = "Not calibrated. Automatic decline cannot operate yet.";
        calibrationStatus.setText(summary);
        confirmButton.setVisibility(pending ? View.VISIBLE : View.GONE);
        discardButton.setVisibility(pending || learning ? View.VISIBLE : View.GONE);
        learnButton.setText(learning ? "Learning armed — go to Uber Driver" :
                "1. Arm learning (then decline one real offer)");
        long timestamp = prefs.getLong(Prefs.LAST_TIME, 0);
        if (timestamp == 0) lastStatus.setText("No offer detected yet.");
        else {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(timestamp));
            lastStatus.setText(String.format(Locale.US, "A$%.2f · %s\n%s",
                    prefs.getFloat(Prefs.LAST_AMOUNT, 0), time,
                    prefs.getString(Prefs.LAST_STATUS, "")));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (accessStatus != null) refresh();
    }
}
