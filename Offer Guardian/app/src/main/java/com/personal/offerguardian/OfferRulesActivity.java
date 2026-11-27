package com.personal.offerguardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** v0.9.8: one independently saved rule set per verified delivery count. */
public final class OfferRulesActivity extends Activity {
    private static final int BG=Color.rgb(246,249,247),INK=Color.rgb(22,38,30);
    private static final int MUTED=Color.rgb(99,111,105),GREEN=Color.rgb(10,132,66);
    private static final int BORDER=Color.rgb(219,229,222),RED=Color.rgb(171,57,53);
    private SharedPreferences prefs;
    private int selected=0;
    private final Draft[] tiers={new Draft(),new Draft(),new Draft()};
    private LinearLayout content,tabRow,editor,preview;
    private TextView[] tabViews=new TextView[3];
    private boolean wasTiered;

    private static final class Draft {
        boolean enabled=true,priceOn=true,hourOn=false;
        String price="30.00",hour="40.00";
        OfferRuleEngine.Mode mode=OfferRuleEngine.Mode.ANY;
        OfferRuleEngine.Config config() {
            return new OfferRuleEngine.Config(priceOn,number(price),hourOn,number(hour),
                    false,0,mode);
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);prefs=getIntent().getBooleanExtra("doordash",false)?Prefs.dashRules(this):Prefs.of(this);
        wasTiered=Prefs.tieredEnabled(prefs);
        TieredOfferRules fromPrefs=Prefs.tieredRules(prefs);
        for(int i=0;i<3;i++){
            Draft d=tiers[i];OfferRuleEngine.Config c=fromPrefs.configFor(i+1);
            // An intentionally disabled category still has its saved draft in prefs.
            if(c==null)c=readSavedConfig(i+1);
            d.enabled=fromPrefs.enabled(i+1);
            d.priceOn=c.priceOn;d.hourOn=c.hourlyOn;d.mode=c.mode;
            d.price=money(c.minPrice);d.hour=money(c.minHourly);
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        build();
    }
    private OfferRuleEngine.Config readSavedConfig(int count){
        // Read tier values even when its enabled switch is OFF.
        OfferRuleEngine.Config c=Prefs.rules(prefs);
        return new OfferRuleEngine.Config(
                prefs.getBoolean(Prefs.tierKey(count,"price_on"),c.priceOn),
                prefs.getFloat(Prefs.tierKey(count,"min_price"),(float)c.minPrice),
                prefs.getBoolean(Prefs.tierKey(count,"hourly_on"),c.hourlyOn),
                prefs.getFloat(Prefs.tierKey(count,"min_hourly"),(float)c.minHourly),
                false,0.0,
                "ALL".equals(prefs.getString(Prefs.tierKey(count,"mode"),c.mode.name()))
                        ?OfferRuleEngine.Mode.ALL:OfferRuleEngine.Mode.ANY,false,1,false,0.0);
    }
    private static String money(double d){return String.format(Locale.US,"%.2f",d);}
    private static double number(String s) {
        try{double x=Double.parseDouble(s.trim());return Double.isFinite(x)&&x>0&&x<=10000?x:Double.NaN;}
        catch(Exception e){return Double.NaN;}
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int outline,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(outline!=0)d.setStroke(dp(1),outline);return d;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int height,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.bottomMargin=dp(bottom);return p;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(dp(2),1);return t;
    }
    private TextView button(String label,int bg,int fg,View.OnClickListener listener){
        TextView v=text(label,14,fg,true);v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(shape(bg,0,10));v.setOnClickListener(listener);return v;
    }
    private LinearLayout card(LinearLayout host){
        LinearLayout c=column();c.setPadding(dp(14),dp(14),dp(14),dp(14));
        c.setBackground(shape(Color.WHITE,BORDER,12));host.addView(c,lp(-2,12));return c;
    }
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        setContentView(scroll);content=column();content.setPadding(dp(15),dp(20),dp(15),dp(32));scroll.addView(content);
        LinearLayout header=row();content.addView(header,lp(-2,9));
        header.addView(button("‹",Color.TRANSPARENT,GREEN,v->finish()),new LinearLayout.LayoutParams(dp(46),dp(48)));
        LinearLayout branding=column();header.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        branding.addView(text("Offer Guardian",23,INK,true));
        branding.addView(text("Delivery-specific Rules Builder  ·  v0.9.9",12,MUTED,false));
        content.addView(text("Set payment and hourly-rate conditions for solo, double and triple offers. Distance no longer affects live decisions. Enable auto-decline separately on the home screen.",13,MUTED,false),lp(-2,14));
        LinearLayout notice=card(content);
        notice.setBackground(shape(Color.rgb(232,248,235),Color.rgb(179,224,190),12));
        notice.addView(text(wasTiered?"● Tiered rules currently active":"○ Existing rules remain active until you save these tiers",14,GREEN,true),lp(-2,5));
        notice.addView(text("The previous global minimum-deliveries rule will not override these tiers. A missing delivery count or an offer with 4+ deliveries is left for manual review.",12,MUTED,false));
        content.addView(text("CHOOSE DELIVERY TYPE",13,GREEN,true),lp(-2,9));
        tabRow=row();content.addView(tabRow,lp(48,12));
        for(int i=0;i<3;i++) {
            final int idx=i;
            tabViews[i]=button((i+1)+(i==0?" Delivery":" Deliveries"),Color.WHITE,INK,v->{selected=idx;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),1);p.rightMargin=dp(i<2?6:0);
            tabRow.addView(tabViews[i],p);
        }
        editor=column();content.addView(editor,lp(-2,8));
        TextView save=button("Save all three delivery rule sets",GREEN,Color.WHITE,v->save());
        content.addView(save,lp(52,12));
        if(wasTiered) {
            TextView restore=button("Restore previous global rules",Color.WHITE,MUTED,
                    v->new AlertDialog.Builder(this)
                    .setTitle("Restore old offer rules?")
                    .setMessage("This will turn off the delivery-specific rules and reactivate your saved old global conditions, including any previous minimum-deliveries requirement. Continue?")
                    .setPositiveButton("Restore",(dialog,which)->{
                        SharedPreferences.Editor e=prefs.edit().putBoolean(Prefs.TIERED_ACTIVE,false)
                                .putLong(Prefs.TIERED_GENERATION,
                                        prefs.getLong(Prefs.TIERED_GENERATION,0)+1);
                        if(e.commit()){Toast.makeText(this,"Previous rules restored",Toast.LENGTH_LONG).show();finish();}
                        else Toast.makeText(this,"Could not restore rules",Toast.LENGTH_LONG).show();
                    }).setNegativeButton("Cancel",null).show());
            content.addView(restore,lp(46,12));
        }
        preview=column();content.addView(preview,lp(-2,8));
        content.addView(text("For a verified offer that fails its matching rules, Offer Guardian declines only if auto-decline is enabled and TEST ONLY is off. Unknown counts, unavailable required measurements, disabled tiers and 4+ batches are not automatically declined. Gross hourly rates are estimates.",12,MUTED,false));
        render();
    }
    private void render(){
        for(int i=0;i<3;i++){
            boolean active=i==selected;
            tabViews[i].setBackground(shape(active?GREEN:Color.WHITE,active?0:BORDER,9));
            tabViews[i].setTextColor(active?Color.WHITE:INK);
        }
        editor.removeAllViews();Draft d=tiers[selected];
        LinearLayout box=card(editor);
        String name=selected==0?"Solo delivery":selected==1?"Double delivery":"Triple delivery";
        box.addView(text(name+" rules",18,INK,true),lp(-2,8));
        CheckBox enabled=new CheckBox(this);enabled.setText("Enable rules for this delivery count");
        enabled.setTextSize(14);enabled.setTextColor(INK);enabled.setChecked(d.enabled);box.addView(enabled,lp(-2,5));
        enabled.setOnCheckedChangeListener((v,on)->{d.enabled=on;refreshPreview();});
        box.addView(text("When disabled, offers in this category are left for manual review, not automatically declined.",12,MUTED,false),lp(-2,12));
        addMetric(box,"Minimum total payment", "A$", d.priceOn,d.price,(on,value)->{d.priceOn=on;d.price=value;refreshPreview();});
        addMetric(box,"Minimum estimated hourly rate","A$/hr",d.hourOn,d.hour,(on,value)->{d.hourOn=on;d.hour=value;refreshPreview();});
        box.addView(text("HOW SHOULD THESE CONDITIONS COMBINE?",12,GREEN,true),lp(-2,6));
        RadioGroup group=new RadioGroup(this);group.setOrientation(RadioGroup.VERTICAL);
        RadioButton any=new RadioButton(this);any.setText("ANY (OR) — one active condition must pass");
        RadioButton all=new RadioButton(this);all.setText("ALL (AND) — every active condition must pass");
        any.setTextColor(INK);all.setTextColor(INK);any.setTextSize(13);all.setTextSize(13);
        any.setId(View.generateViewId());all.setId(View.generateViewId());
        group.addView(any);group.addView(all);box.addView(group);
        group.check(d.mode==OfferRuleEngine.Mode.ALL?all.getId():any.getId());
        group.setOnCheckedChangeListener((v,id)->{d.mode=id==all.getId()?OfferRuleEngine.Mode.ALL:OfferRuleEngine.Mode.ANY;refreshPreview();});
        refreshPreview();
    }
    private interface MetricChange {void set(boolean enabled,String value);}
    private void addMetric(LinearLayout parent,String title,String suffix,boolean initial,String value,MetricChange change){
        LinearLayout panel=column();panel.setPadding(dp(8),dp(8),dp(8),dp(10));
        panel.setBackground(shape(BG,BORDER,10));parent.addView(panel,lp(-2,9));
        CheckBox cb=new CheckBox(this);cb.setText(title);cb.setTextColor(INK);cb.setTextSize(14);
        cb.setChecked(initial);panel.addView(cb,lp(-2,5));
        LinearLayout line=row();panel.addView(line,lp(48,0));
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(value);input.setTextColor(INK);input.setTextSize(18);
        input.setPadding(dp(12),0,dp(10),0);input.setBackground(shape(Color.WHITE,BORDER,8));
        line.addView(input,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView unit=text("  "+suffix,13,MUTED,true);line.addView(unit);
        cb.setOnCheckedChangeListener((v,on)->change.set(on,input.getText().toString()));
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){change.set(cb.isChecked(),s.toString());}
            @Override public void afterTextChanged(Editable s){}
        });
    }
    private void refreshPreview(){
        if(preview==null)return;
        preview.removeAllViews();Draft d=tiers[selected];
        LinearLayout panel=card(preview);panel.addView(text("RULE PREVIEW — "+(selected+1)+" DELIVERIES",13,GREEN,true),lp(-2,5));
        if(!d.enabled) {panel.addView(text("Manual review — this category is switched off",13,MUTED,false));return;}
        OfferRuleEngine.Config c=d.config();
        if(!c.valid()) {panel.addView(text("Select at least one criterion and enter positive valid values (maximum 10,000).",13,RED,true));return;}
        panel.addView(text(OfferRuleEngine.summary(c),13,INK,false),lp(-2,8));
        int[] counts={1,2,3};double[] payouts={20,35,50};int[] minutes={27,60,65};int[] metres={5000,12000,20000};
        int idx=selected;
        OfferRuleEngine.Decision result=OfferRuleEngine.evaluate(c,payouts[idx],minutes[idx],metres[idx],counts[idx]);
        String test=String.format(Locale.US,"Example: A$%.2f · %d min · %.1f km — %s",
                payouts[idx],minutes[idx],metres[idx]/1000.0,
                result.outcome==OfferRuleEngine.Outcome.SHOW?"Qualifies":
                    result.outcome==OfferRuleEngine.Outcome.DECLINE?"Decline if enabled":"Manual review");
        panel.addView(text(test,12,result.outcome==OfferRuleEngine.Outcome.DECLINE?RED:GREEN,true));
    }
    private void save(){
        OfferRuleEngine.Config[] configs=new OfferRuleEngine.Config[3];boolean[] enabled=new boolean[3];
        for(int i=0;i<3;i++){configs[i]=tiers[i].config();enabled[i]=tiers[i].enabled;}
        TieredOfferRules group=new TieredOfferRules(configs,enabled);
        if(!group.valid()){
            new AlertDialog.Builder(this).setTitle("Check delivery rules")
                    .setMessage("Enable at least one delivery category, and give every enabled category at least one active condition with a positive value no greater than 10,000.")
                    .setPositiveButton("OK",null).show();return;
        }
        SharedPreferences.Editor e=prefs.edit();
        for(int i=0;i<3;i++){
            int n=i+1;Draft d=tiers[i];OfferRuleEngine.Config c=configs[i];
            e.putBoolean(Prefs.tierKey(n,"enabled"),d.enabled)
                    .putBoolean(Prefs.tierKey(n,"price_on"),d.priceOn)
                    .putFloat(Prefs.tierKey(n,"min_price"),Float.isFinite((float)c.minPrice)?(float)c.minPrice:30.0f)
                    .putBoolean(Prefs.tierKey(n,"hourly_on"),d.hourOn)
                    .putFloat(Prefs.tierKey(n,"min_hourly"),Float.isFinite((float)c.minHourly)?(float)c.minHourly:40.0f)
                    .putBoolean(Prefs.tierKey(n,"km_on"),false)
                    .putBoolean(Prefs.tierKey(n,"distance_on"),false)
                    .putString(Prefs.tierKey(n,"mode"),d.mode.name());
        }
        e.putBoolean(Prefs.TIERED_ACTIVE,true)
                .putLong(Prefs.TIERED_GENERATION,prefs.getLong(Prefs.TIERED_GENERATION,0)+1);
        if(!e.commit()){
            new AlertDialog.Builder(this).setTitle("Save failed")
                    .setMessage("Your settings could not be saved. Previous rules remain active.")
                    .setPositiveButton("OK",null).show();return;
        }
        Toast.makeText(this,"Three delivery rule sets saved",Toast.LENGTH_LONG).show();finish();
    }
}
