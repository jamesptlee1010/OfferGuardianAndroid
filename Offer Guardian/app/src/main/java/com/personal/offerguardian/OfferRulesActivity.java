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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Concept B: editable, add/remove ANY/ALL rule builder. Settings persist locally. */
public final class OfferRulesActivity extends Activity {
    private static final int BG=Color.rgb(246,249,247);
    private static final int INK=Color.rgb(22,38,30);
    private static final int MUTED=Color.rgb(99,111,105);
    private static final int GREEN=Color.rgb(10,132,66);
    private static final int BORDER=Color.rgb(219,229,222);
    private SharedPreferences prefs;
    private LinearLayout rulesList, liveExamples;
    private boolean priceOn, hourlyOn, kmOn;
    private String priceText,hourlyText,kmText;
    private OfferRuleEngine.Mode mode;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=Prefs.of(this);
        OfferRuleEngine.Config c=Prefs.rules(prefs);
        priceOn=c.priceOn;hourlyOn=c.hourlyOn;kmOn=c.kilometreOn;
        priceText=String.format(Locale.US,"%.2f",c.minPrice);
        hourlyText=String.format(Locale.US,"%.2f",c.minHourly);
        kmText=String.format(Locale.US,"%.2f",c.minPerKm);
        mode=c.mode;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        build();
    }
    private int dp(float p) {return Math.round(getResources().getDisplayMetrics().density*p);}
    private GradientDrawable background(int fill,int line,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(line!=0)d.setStroke(dp(1),line);
        return d;
    }
    private LinearLayout col() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int height,int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.bottomMargin=dp(margin);return p;
    }
    private TextView text(String v,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(v);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setLineSpacing(dp(2),1f);return t;
    }
    private TextView touch(String label,int bg,int fg,View.OnClickListener click) {
        TextView v=text(label,14,fg,true);v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14),dp(12),dp(14),dp(12));
        v.setBackground(background(bg,0,11));v.setOnClickListener(click);return v;
    }
    private LinearLayout card(LinearLayout host,int pad) {
        LinearLayout c=col();c.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        c.setBackground(background(Color.WHITE,BORDER,14));host.addView(c,lp(-2,12));return c;
    }
    private void build() {
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        setContentView(scroll);
        LinearLayout content=col();content.setPadding(dp(15),dp(20),dp(15),dp(32));scroll.addView(content);
        LinearLayout header=row();content.addView(header,lp(-2,10));
        TextView back=touch("‹",Color.TRANSPARENT,GREEN,v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(46),dp(48)));
        LinearLayout brand=col();header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(text("Offer Guardian",23,INK,true));
        brand.addView(text("Offer Rules Builder  ·  v0.7.1",12,MUTED,false));
        content.addView(text("Choose one, two or three conditions. Only offers meeting your chosen rules are alerted; other verified offers can be declined when auto-decline is enabled.",13,MUTED,false),lp(-2,14));
        LinearLayout monitoring=card(content,12);
        monitoring.setBackground(background(Color.rgb(230,247,235),Color.rgb(173,220,187),12));
        monitoring.addView(text(prefs.getBoolean(Prefs.MONITOR,false)?"● Monitoring active":"○ Monitoring switched off",15,GREEN,true));
        monitoring.addView(text("All changes are local to this device. Save before returning to Uber Driver.",12,MUTED,false));
        content.addView(text("YOUR ACTIVE CONDITIONS",13,GREEN,true),lp(-2,9));
        rulesList=col();content.addView(rulesList,lp(-2,3));
        TextView add=touch("＋  Add another rule",Color.rgb(232,249,238),GREEN,v->addRule());
        content.addView(add,lp(47,18));
        content.addView(text("HOW SHOULD THE RULES COMBINE?",14,INK,true),lp(-2,8));
        LinearLayout comb=card(content,12);
        RadioGroup group=new RadioGroup(this);group.setOrientation(RadioGroup.VERTICAL);
        RadioButton any=new RadioButton(this);any.setText("ANY (OR) — one qualifying rule is enough");
        RadioButton all=new RadioButton(this);all.setText("ALL (AND) — every selected rule must pass");
        any.setId(View.generateViewId());all.setId(View.generateViewId());
        any.setTextColor(INK);all.setTextColor(INK);any.setTextSize(14);all.setTextSize(14);
        group.addView(any);group.addView(all);comb.addView(group);
        group.check(mode==OfferRuleEngine.Mode.ANY?any.getId():all.getId());
        group.setOnCheckedChangeListener((g,id)->{mode=id==all.getId()?OfferRuleEngine.Mode.ALL:OfferRuleEngine.Mode.ANY;refreshExamples();});
        TextView save=touch("Save rules",GREEN,Color.WHITE,v->save());content.addView(save,lp(52,16));
        content.addView(text("LIVE EXAMPLES",15,GREEN,true),lp(-2,6));
        content.addView(text("The examples below update as you edit rules. They simulate offers; no Uber buttons are pressed here.",12,MUTED,false),lp(-2,10));
        liveExamples=col();content.addView(liveExamples,lp(-2,10));
        content.addView(text("Missing estimated minutes or kilometres: if a selected rate cannot be verified, Offer Guardian leaves the offer for manual review instead of automatically declining it. Amounts and rates are estimated gross earnings, not take-home pay.",12,MUTED,false),lp(-2,0));
        renderRuleCards();refreshExamples();
    }
    private boolean enabled(int rule){return rule==0?priceOn:rule==1?hourlyOn:kmOn;}
    private void setEnabled(int rule,boolean yes){if(rule==0)priceOn=yes;else if(rule==1)hourlyOn=yes;else kmOn=yes;}
    private String ruleName(int rule){return rule==0?"Minimum offer price":rule==1?"Minimum hourly rate":"Minimum rate per kilometre";}
    private String description(int rule){return rule==0?"Total payment for the request":rule==1?"Estimated AUD earned each hour":"Estimated AUD earned per kilometre";}
    private String unit(int rule){return rule==0?"AUD":rule==1?"A$/hr":"A$/km";}
    private String draft(int rule){return rule==0?priceText:rule==1?hourlyText:kmText;}
    private void setDraft(int rule,String value){if(rule==0)priceText=value;else if(rule==1)hourlyText=value;else kmText=value;}
    private void renderRuleCards(){
        if(rulesList==null)return;
        rulesList.removeAllViews();
        for(int i=0;i<3;i++)if(enabled(i))addRuleCard(i);
    }
    private void addRuleCard(int index) {
        LinearLayout c=card(rulesList,14);
        LinearLayout head=row();c.addView(head,lp(-2,7));
        TextView label=text("✓  "+ruleName(index),16,INK,true);
        head.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView remove=touch("Remove",Color.rgb(255,240,238),Color.rgb(174,53,53),v->{
            if((priceOn?1:0)+(hourlyOn?1:0)+(kmOn?1:0)<=1){
                Toast.makeText(this,"Keep at least one rule enabled",Toast.LENGTH_SHORT).show();return;
            }
            setEnabled(index,false);renderRuleCards();refreshExamples();
        });
        head.addView(remove,new LinearLayout.LayoutParams(dp(88),dp(43)));
        c.addView(text(description(index),12,MUTED,false),lp(-2,10));
        LinearLayout editLine=row();c.addView(editLine,lp(-2,0));
        EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);input.setText(draft(index));input.setTextSize(21);input.setTextColor(INK);
        input.setPadding(dp(12),0,dp(12),0);input.setBackground(background(BG,BORDER,9));
        editLine.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));
        TextView suffix=text("  "+unit(index),13,MUTED,true);editLine.addView(suffix);
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){setDraft(index,s.toString());refreshExamples();}
            @Override public void afterTextChanged(Editable e){}
        });
    }
    private void addRule(){
        int[] missing=new int[3];int size=0;
        for(int i=0;i<3;i++)if(!enabled(i))missing[size++]=i;
        if(size==0){Toast.makeText(this,"All three rules are active",Toast.LENGTH_SHORT).show();return;}
        String[] options=new String[size];for(int i=0;i<size;i++)options[i]=ruleName(missing[i]);
        new AlertDialog.Builder(this).setTitle("Add a condition")
            .setItems(options,(dialog,position)->{
                setEnabled(missing[position],true);renderRuleCards();refreshExamples();
            }).setNegativeButton("Cancel",null).show();
    }
    private static double numeric(String value){
        try {
            double n=Double.parseDouble(value.trim());
            return Double.isFinite(n) && n>0 && n<=10000?n:Double.NaN;
        }catch(Exception e){return Double.NaN;}
    }
    private OfferRuleEngine.Config currentConfig(){return new OfferRuleEngine.Config(
        priceOn,Double.isNaN(numeric(priceText))&&!priceOn?30:numeric(priceText),
        hourlyOn,Double.isNaN(numeric(hourlyText))&&!hourlyOn?40:numeric(hourlyText),
        kmOn,Double.isNaN(numeric(kmText))&&!kmOn?3:numeric(kmText),mode);
    }
    private void refreshExamples(){
        if(liveExamples==null)return;
        liveExamples.removeAllViews();
        OfferRuleEngine.Config config=currentConfig();
        if(!config.valid()){
            liveExamples.addView(text("Enter a positive number for every active rule (maximum 10,000).",13,Color.rgb(180,55,50),true),lp(-2,8));return;
        }
        double[] payouts={20,35,15};int[] minutes={27,60,45};int[] metres={5000,20000,6000};
        for(int i=0;i<payouts.length;i++){
            OfferRuleEngine.Decision result=OfferRuleEngine.evaluate(config,payouts[i],minutes[i],metres[i]);
            LinearLayout c=card(liveExamples,12);
            c.addView(text(String.format(Locale.US,"Offer %c    A$%.2f",'A'+i,payouts[i]),17,INK,true),lp(-2,4));
            c.addView(text(String.format(Locale.US,"%d min  ·  %.1f km  ·  A$%.2f/hr  ·  A$%.2f/km",
                minutes[i],metres[i]/1000.0,result.hourly,result.perKm),12,MUTED,false),lp(-2,6));
            int color=result.outcome==OfferRuleEngine.Outcome.SHOW?GREEN:
                result.outcome==OfferRuleEngine.Outcome.REVIEW?Color.rgb(150,105,35):Color.rgb(170,50,50);
            String label=result.outcome==OfferRuleEngine.Outcome.SHOW?"✓ Show + alert":
                result.outcome==OfferRuleEngine.Outcome.REVIEW?"? Manual review":"× Decline if enabled";
            c.addView(text(label,13,color,true));
        }
    }
    private void save(){
        OfferRuleEngine.Config c=currentConfig();
        if(!c.valid()){
            new AlertDialog.Builder(this).setTitle("Check your rules")
                .setMessage("Keep at least one rule active, and enter positive thresholds no greater than 10,000.")
                .setPositiveButton("OK",null).show();return;
        }
        prefs.edit().putBoolean(Prefs.RULE_PRICE,priceOn)
            .putBoolean(Prefs.RULE_HOURLY,hourlyOn).putBoolean(Prefs.RULE_KM,kmOn)
            .putFloat(Prefs.THRESHOLD,(float)c.minPrice)
            .putFloat(Prefs.RULE_MIN_HOUR,(float)c.minHourly)
            .putFloat(Prefs.RULE_MIN_KM,(float)c.minPerKm)
            .putString(Prefs.RULE_MODE,mode.name()).apply();
        Toast.makeText(this,"Rules saved — "+(mode==OfferRuleEngine.Mode.ANY?"ANY (OR)":"ALL (AND)"),Toast.LENGTH_LONG).show();
        finish();
    }
}
