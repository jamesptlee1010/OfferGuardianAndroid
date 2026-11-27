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
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
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
    private static final int ACCENT = Color.rgb(10, 132, 66);
    private static final int BG = Color.rgb(246, 249, 247);
    private SharedPreferences prefs;
    private TextView accessStatus, lastStatus, historyStatus, monitorHealthStatus;
    private Switch monitorSwitch, autoSwitch, alertSwitch, visualSwitch, visualTestSwitch;
    private TextView rulesSummary;
    private TextView alertVolumeLabel;

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
        // New alerts default to standalone audio but preserve all current rule toggles.
        if (!prefs.getBoolean(Prefs.MIGRATED_V04, false)) {
            prefs.edit().putBoolean(Prefs.MIGRATED_V04,true)
                    .putBoolean(Prefs.AUDIO_USE_ALARM,true)
                    .putInt(Prefs.AUDIO_VOLUME,85)
                    .putInt(Prefs.AUDIO_REPEAT,2)
                    .putInt(Prefs.AUDIO_TONE,0)
                    .putBoolean(Prefs.AUDIO_VIBRATE,true).apply();
        }
        if(!prefs.getBoolean(Prefs.MIGRATED_V08,false)) {
            prefs.edit().putBoolean(Prefs.MIGRATED_V08,true)
                    .putBoolean(Prefs.OVERLAY_ENABLED,true)
                    .putBoolean(Prefs.ACCEPT_TRACKING,true).apply();
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
        root.addView(text("v2.6.1  ·  Rules + Offer History  ·  Australia", 13, MUTED, false), lp(-2, 12));
        LinearLayout historyLaunch=card(root,"Offer History · Concept B",
                "Browse all, accepted and highest offers with pickup, destination, estimated A$/km and A$/hour.");
        button(historyLaunch,"Open Offer History  →",true,
                v -> startActivity(new Intent(this,OfferHistoryActivity.class)));

        LinearLayout contexts=card(root,"Screen recognition · automatic",
                "Detects light and dark Uber Driver offer cards automatically. Supports DoorDash on screen and Uber overlays over DoorDash. Declines still require verified offer details and the on-screen X; review diagnostics if an offer is skipped.");

        LinearLayout settings = card(root, "Offer Filtering Rules · Concept B",
                "Set separate payment and estimated A$/hour minimums for 1, 2 and 3 deliveries. Distance does not affect automatic decisions. Choose ANY (OR) or ALL (AND).");
        rulesSummary=text(Prefs.ruleSummary(prefs),14,ACCENT,true);
        settings.addView(rulesSummary,lp(-2,12));
        button(settings,"Open Rules Builder  →",true,
                v -> startActivity(new Intent(this,OfferRulesActivity.class)));
        monitorSwitch = toggle(settings, "Monitor delivery offers", prefs.getBoolean(Prefs.MONITOR, false));
        autoSwitch = toggle(settings, "Auto-decline offers failing your selected rules", prefs.getBoolean(Prefs.AUTO_DECLINE, false));
        alertSwitch = toggle(settings, "Sound alert for qualifying or uncertain offers", prefs.getBoolean(Prefs.ALERT, true));
        monitorSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(Prefs.MONITOR, checked).apply();
            if (checked && !accessibilityEnabled()) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable Accessibility Service")
                        .setMessage("Enable 'Offer Guardian – delivery monitor' in Accessibility. It analyses visible Uber and DoorDash offer cards in light/dark mode. No internet connection is required.")
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
        button(settings, "Preview example rules", false, v -> testExamples());
        LinearLayout platforms=card(root,"Platforms · dual monitoring","Leave DoorDash open for the most reliable dual monitoring. Uber overlays are recognised separately from the foreground app. Notification switching is experimental; it avoids known active deliveries and competing offers. No automatic acceptance.");
        android.widget.RadioGroup modes=new android.widget.RadioGroup(this);
        modes.setOrientation(android.widget.RadioGroup.VERTICAL);
        String[] modeNames={"Monitoring off (both platforms)","Uber Eats only · reduced resources","DoorDash only · reduced resources","Both platforms · dual monitoring"};
        int currentMode=(prefs.getBoolean(Prefs.UBER_ENABLED,true)?1:0)|(prefs.getBoolean(Prefs.DASH_ENABLED,true)?2:0);
        int[] modeIds=new int[4];
        for(int i=0;i<4;i++){android.widget.RadioButton b=new android.widget.RadioButton(this);modeIds[i]=View.generateViewId();b.setId(modeIds[i]);b.setText(modeNames[i]);modes.addView(b);}
        modes.check(modeIds[currentMode]);platforms.addView(modes);
        modes.setOnCheckedChangeListener((group,id)->{for(int i=0;i<4;i++)if(modeIds[i]==id){prefs.edit().putBoolean(Prefs.UBER_ENABLED,(i&1)!=0).putBoolean(Prefs.DASH_ENABLED,(i&2)!=0).apply();break;}});
        Switch dashTest=toggle(platforms,"DoorDash TEST ONLY (no decline taps)",prefs.getBoolean(Prefs.DASH_DRY,true));
        dashTest.setOnCheckedChangeListener((b,on)->{
            if(!on&&prefs.getBoolean(Prefs.DASH_DRY,true)) {
                dashTest.setChecked(true);
                new AlertDialog.Builder(this).setTitle("Enable DoorDash decline automation?").setMessage("First verify recognition on real offers while parked. DoorDash requires two verified taps. Automation may affect your account. Never enable if prohibited by your agreement.").setNegativeButton("Cancel",null).setPositiveButton("Enable",(d,w)->{prefs.edit().putBoolean(Prefs.DASH_DRY,false).apply();dashTest.setChecked(false);}).show();
            } else prefs.edit().putBoolean(Prefs.DASH_DRY,on).apply();
        });
        toggle(platforms,"Separate DoorDash rules (after saving DoorDash tiers)",prefs.getBoolean(Prefs.DASH_SEPARATE,false)).setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(Prefs.DASH_SEPARATE,on).apply());
        button(platforms,"DoorDash rules builder",false,v->startActivity(new Intent(this,OfferRulesActivity.class).putExtra("doordash",true)));
        toggle(platforms,"Open DoorDash on New Delivery! notification (experimental)",prefs.getBoolean(Prefs.AUTO_SWITCH,false)).setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(Prefs.AUTO_SWITCH,on).apply());
        toggle(platforms,"Allow switching from other apps while waiting",prefs.getBoolean(Prefs.SWITCH_OTHER_IDLE,true)).setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(Prefs.SWITCH_OTHER_IDLE,on).apply());
        toggle(platforms,"Return to previous app after DoorDash decline",prefs.getBoolean(Prefs.AUTO_RETURN,true)).setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(Prefs.AUTO_RETURN,on).apply());
        button(platforms,"Enable notification access (required for auto switching)",false,v->startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(platforms,"Reconnect notification listener",false,v->{android.service.notification.NotificationListenerService.requestRebind(new android.content.ComponentName(this,OfferNotificationListener.class));refresh();});
        button(platforms,"Apply $30/hr to all six delivery tiers",false,v->new AlertDialog.Builder(this).setTitle("Set hourly rules to $30/hr?").setMessage("Enables the hourly rule and all delivery tiers on both platforms. Keeps your other rules and AND/OR choices.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{Prefs.resetHourlyDefaults(this);refresh();}).show());
        button(platforms,"Open DoorDash",false,v->{Intent i=getPackageManager().getLaunchIntentForPackage(OfferSurfacePolicy.DOORDASH);if(i!=null)startActivity(i);else Toast.makeText(this,"DoorDash not found",Toast.LENGTH_SHORT).show();});
        LinearLayout quick=card(root,"Quick controls · D / DF / DR","Movable vertical floating pill (default right edge). Hold and drag any button to move the whole pill. D opens DoorDash; DF pauses orders and returns to Uber; DR resumes a paused Dash. Use only while safely parked. DF never ends a scheduled Dash; a pause may expire after 35 minutes.");
        toggle(quick,"Enable floating quick controls",prefs.getBoolean(Prefs.QUICK_CONTROLS,false)).setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean(Prefs.QUICK_CONTROLS,on).apply();if(on&&!accessibilityEnabled())openAccessibility();});
        toggle(quick,"Lock quick controls position",prefs.getBoolean(Prefs.QUICK_LOCK,false)).setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(Prefs.QUICK_LOCK,on).apply());
        button(quick,"Reset quick controls position",false,v->{prefs.edit().remove(Prefs.QUICK_X).remove(Prefs.QUICK_Y).apply();Toast.makeText(this,"Quick controls reset to right edge",Toast.LENGTH_SHORT).show();});
        LinearLayout overlaySettings=card(root,"Live qualifying-offer overlay · v2.6.1",
                "Compact black rate pill, upper-right on either platform (below DoorDash Decline). Shows only estimated A$/hr and A$/km, with NO lightning icon or vertical line. It appears only if an offer meets your enabled ANY/ALL rules; never on failed or uncertain offers.");
        Switch overlaySwitch=toggle(overlaySettings,"Show small overlay for qualifying offers",
                prefs.getBoolean(Prefs.OVERLAY_ENABLED,true));
        overlaySwitch.setOnCheckedChangeListener((b,enabled)->
                prefs.edit().putBoolean(Prefs.OVERLAY_ENABLED,enabled).apply());
        overlaySettings.addView(text("No extra 'Draw over other apps' permission is required: the enabled accessibility service owns the non-interactive display. It disappears when the offer disappears, monitoring stops, or you leave Uber Driver.",12,MUTED,false),lp(-2,9));
        LinearLayout acceptance=card(root,"Accepted offer tracking · v2.6.1",
                "After a card disappears, check for an active Uber pickup screen naming the same merchant twice. Never assume an Accept or Match tap succeeded simply because the offer disappeared.");
        Switch acceptanceSwitch=toggle(acceptance,"Auto-mark verified assigned jobs as Accepted",
                prefs.getBoolean(Prefs.ACCEPT_TRACKING,true));
        acceptanceSwitch.setOnCheckedChangeListener((b,enabled)->
                prefs.edit().putBoolean(Prefs.ACCEPT_TRACKING,enabled).apply());
        acceptance.addView(text("If Uber does not expose a reliably matching assignment screen, the offer remains Unknown. You can still correct it in Offer History. The app NEVER taps Accept or Match.",12,MUTED,false),lp(-2,0));

        // Standalone alerts do NOT rely on Uber Driver's notification sounds.
        LinearLayout audio = card(root, "High-value offer sound",
                "Uber Driver can be silenced separately in Android notification settings. " +
                "Offer Guardian plays its own bundled tone when an offer meets your rules. " +
                "Do not mute the whole phone or use Do Not Disturb for this setup.");
        Switch audioAlarm = toggle(audio, "Use ALARM volume (recommended)",
                prefs.getBoolean(Prefs.AUDIO_USE_ALARM,true));
        audioAlarm.setOnCheckedChangeListener((b,checked) -> {
            prefs.edit().putBoolean(Prefs.AUDIO_USE_ALARM,checked).apply();
        });
        audio.addView(text("ALARM uses your phone's Alarm volume; OFF uses Media volume. " +
                "Choose whichever you can hear reliably through your phone or Bluetooth.",
                12,MUTED,false),lp(-2,12));
        alertVolumeLabel=text("Tone volume: "+prefs.getInt(Prefs.AUDIO_VOLUME,85)+"%",14,INK,true);
        audio.addView(alertVolumeLabel,lp(-2,5));
        SeekBar soundVolume = new SeekBar(this);
        soundVolume.setMax(100);
        soundVolume.setProgress(prefs.getInt(Prefs.AUDIO_VOLUME,85));
        soundVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int percent,boolean fromUser) {
                alertVolumeLabel.setText("Tone volume: "+percent+"%");
                if (fromUser) prefs.edit().putInt(Prefs.AUDIO_VOLUME,percent).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        audio.addView(soundVolume,lp(48,6));
        audio.addView(text("Tone",14,INK,true),lp(-2,5));
        Spinner toneSpinner=new Spinner(this);
        ArrayAdapter<String> tones=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Bright chime", "Triple beep"});
        toneSpinner.setAdapter(tones);
        toneSpinner.setSelection(Math.max(0,Math.min(1,prefs.getInt(Prefs.AUDIO_TONE,0))));
        toneSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,int pos,long id) {
                prefs.edit().putInt(Prefs.AUDIO_TONE,pos).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        audio.addView(toneSpinner,lp(48,10));
        audio.addView(text("Play tone this many times",14,INK,true),lp(-2,5));
        Spinner repeatsSpinner=new Spinner(this);
        ArrayAdapter<String> repeatsAdapter=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"1 time", "2 times", "3 times"});
        repeatsSpinner.setAdapter(repeatsAdapter);
        repeatsSpinner.setSelection(Math.max(0,Math.min(2,prefs.getInt(Prefs.AUDIO_REPEAT,2)-1)));
        repeatsSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id) {
                prefs.edit().putInt(Prefs.AUDIO_REPEAT,pos+1).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        audio.addView(repeatsSpinner,lp(48,10));
        Switch vibrateSwitch=toggle(audio,"Vibrate on high-value offer",
                prefs.getBoolean(Prefs.AUDIO_VIBRATE,true));
        vibrateSwitch.setOnCheckedChangeListener((b,checked) ->
                prefs.edit().putBoolean(Prefs.AUDIO_VIBRATE,checked).apply());
        button(audio,"TEST high-value ring (A$36.00)",true,v -> {
            boolean audioStarted=OfferAlert.signal(this,36.00);
            Toast.makeText(this,audioStarted
                    ? "Tone started. If silent, turn up your phone's Alarm/Media volume."
                    : "Audio did not start. Try Media mode and TEST again.",
                    Toast.LENGTH_LONG).show();
        });
        button(audio,"Stop test sound",false,v -> OfferAlert.stop());
        button(audio,"Open Android sound settings",false,v ->
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        audio.addView(text("Mute only Uber Driver's notifications (where supported), NOT the entire phone. " +
                "Some Uber sounds may come from inside Uber Driver and cannot be silenced by this app. " +
                "Android Do Not Disturb, audio routing and system alarm/media volume still apply.",
                12,MUTED,false),lp(-2,0));

        LinearLayout access = card(root, "Android permission", "Open Android settings and enable the Offer Guardian accessibility service. Android may show a warning about sensitive screen access.");
        accessStatus = text("Checking service…", 14, MUTED, true);
        access.addView(accessStatus, lp(-2, 9));
        button(access, "Open Accessibility settings", false, v -> openAccessibility());
        button(access, "Open Uber Driver", false, v -> openUber());

        LinearLayout visual = card(root, "Recognise Uber offers on screen",
                "No button learning required. Detects the grey-square X and either a green or black offer-card border, then reads the price below it.");
        visualSwitch = toggle(visual, "Recognise Uber and DoorDash offer screenshots",
                prefs.getBoolean(Prefs.VISUAL_X, false));
        visualTestSwitch = toggle(visual, "TEST ONLY — recognise offers without tapping (recommended first)",
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
                        .setTitle("Enable real decline taps?")
                        .setMessage("Only do this after verifying TEST ONLY on multiple real offers while safely parked. Recognition errors may tap the wrong control. Automation may affect delivery platform accounts. DoorDash has its own additional test-only switch.")
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

        LinearLayout recent = card(root, "Live diagnostics", "Shows why offers qualify, whether the overlay appears, and whether a pickup assignment was verified. No screenshots are stored.");
        monitorHealthStatus=text("Monitor health: no heartbeat yet.",13,MUTED,false);
        recent.addView(monitorHealthStatus,lp(-2,12));
        lastStatus = text("No offer detected yet.", 14, INK, false);
        recent.addView(lastStatus, lp(-2, 12));
        recent.addView(text("Recent detection steps",14,MUTED,true),lp(-2,6));
        historyStatus=text("No scan activity yet.",12,MUTED,false);
        recent.addView(historyStatus,lp(-2,8));
        button(recent, "Refresh diagnostics", false, v -> refresh());
        button(recent, "Copy diagnostics for troubleshooting", false, v -> {
            String report="Offer Guardian v2.6.1\n"+
                    monitorHealthSummary()+"\n"+
                    "Notification listener: "+OfferNotificationListener.connectionStatus(this)+"\nNotification: "+prefs.getString(Prefs.DASH_NOTICE_DIAG,"No Dasher notification observed")+"\n"+
                    "Last DoorDash rule decision: "+prefs.getString(Prefs.DASH_LAST_RULE_DIAG,"No parsed offer")+"\n"+
                    "Last Uber rule decision: "+prefs.getString("uber_last_rule_v25","No parsed offer")+"\n"+
                    "Last unresolved Uber windows: "+prefs.getString("uber_last_windows_v25","None recorded")+"\n"+
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
        root.addView(text("Independent personal utility; not affiliated with or endorsed by Uber or DoorDash.",
                11, MUTED, false), lp(-2, 0));
        refresh();
    }

    private void testExamples() {
        boolean tiered=Prefs.tieredEnabled(prefs);
        TieredOfferRules tiers=tiered?Prefs.tieredRules(prefs):null;
        OfferRuleEngine.Config c=tiered?null:Prefs.rules(prefs);
        OfferRuleEngine.Decision a=tiered?tiers.evaluate(20,27,5000,1):
                OfferRuleEngine.evaluate(c,20,27,5000);
        OfferRuleEngine.Decision b=tiered?tiers.evaluate(35,60,20000,2):
                OfferRuleEngine.evaluate(c,35,60,20000);
        OfferRuleEngine.Decision d=tiered?tiers.evaluate(50,65,20000,3):null;
        String message="Current rules: "+Prefs.ruleSummary(prefs)+"\n\n"+
                "1 delivery: A$20 / 27 min / 5 km: "+a.outcome+"\n"+
                "2 deliveries: A$35 / 60 min / 20 km: "+b.outcome+"\n"+
                (tiered?"3 deliveries: A$50 / 65 min / 20 km: "+d.outcome+"\n":"")+
                "\nThis preview does not press Uber Driver buttons.";
        new AlertDialog.Builder(this).setTitle("Rule simulation")
                .setMessage(message).setPositiveButton("OK",null).show();
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

    private String monitorHealthSummary() {
        long last=prefs.getLong(Prefs.HEALTH_HEARTBEAT_TIME,0L);
        String state=prefs.getString(Prefs.HEALTH_STATE,"no service heartbeat yet");
        int recoveries=prefs.getInt(Prefs.HEALTH_RECOVERIES,0);
        int screenshots=prefs.getInt(Prefs.HEALTH_SCREENSHOTS,0);
        if(last<=0) return "Service heartbeat: none recorded since installing this version";
        long seconds=Math.max(0,(System.currentTimeMillis()-last)/1000L);
        boolean service=prefs.getBoolean(Prefs.HEALTH_SERVICE_ACTIVE,false);
        String health= !service ? "SERVICE STOPPED / UNBOUND" :
                seconds>100 ? "HEARTBEAT STALE: Android may have suspended service" :
                "Heartbeat OK";
        return health+" ("+seconds+"s ago) | "+state+
                " | screenshots="+screenshots+" | recoveries="+recoveries;
    }

    private void refresh() {
        if (rulesSummary!=null)rulesSummary.setText(Prefs.ruleSummary(prefs));
        if(monitorHealthStatus!=null) {
            monitorHealthStatus.setText(monitorHealthSummary());
            long hb=prefs.getLong(Prefs.HEALTH_HEARTBEAT_TIME,0L);
            boolean stale=hb>0 && System.currentTimeMillis()-hb>100000L;
            monitorHealthStatus.setTextColor(stale ||
                    !prefs.getBoolean(Prefs.HEALTH_SERVICE_ACTIVE,true)?
                    Color.rgb(181,92,32):ACCENT);
        }
        if (accessStatus == null) return;
        boolean access = accessibilityEnabled();
        accessStatus.setText(access ? "✓ Accessibility service ENABLED" : "! Accessibility service NOT ENABLED");
        accessStatus.setTextColor(access ? Color.rgb(22, 120, 79) : Color.rgb(181, 92, 32));
        long timestamp = prefs.getLong(Prefs.LIVE_TIME, 0);
        if (timestamp == 0) lastStatus.setText("No live scan yet. Enable Monitoring and Accessibility; open Uber Driver or DoorDash.");
        else {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(new Date(timestamp));
            lastStatus.setText(time+"\n"+prefs.getString(Prefs.LIVE_STATUS,"No scan status"));
        }
        if(historyStatus!=null) historyStatus.setText("Notification listener: "+OfferNotificationListener.connectionStatus(this)+"\nNotification: "+prefs.getString(Prefs.DASH_NOTICE_DIAG,"No Dasher notification observed")+"\nLast DoorDash rule decision: "+prefs.getString(Prefs.DASH_LAST_RULE_DIAG,"No parsed offer")+"\n\n"+prefs.getString(Prefs.DIAG_HISTORY,"No scan activity yet."));
    }

    @Override protected void onResume() {
        super.onResume();
        if (accessStatus != null) refresh();
    }
}
