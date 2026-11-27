package com.personal.offerguardian;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.util.Locale;

/** A test target owned by this application, not an Uber offer. */
public final class TouchDiagnosticsActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView report, counts;
    private Button target;
    private final StringBuilder log=new StringBuilder();
    private int downs,ups,clicks,cancels,serial;
    private boolean running=false,active=false;
    private long downAt;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(Color.rgb(25,38,45));return v;}
    private Button button(LinearLayout parent,String label,View.OnClickListener listener){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setOnClickListener(listener);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void line(String msg){if(log.length()>14000)log.delete(0,log.length()-10000);log.append(String.format(Locale.US,"%d | %s\n",SystemClock.elapsedRealtime(),msg));if(report!=null)report.setText(log.toString());}
    private void counts(){counts.setText("DOWN "+downs+"   UP "+ups+"   CLICK "+clicks+"   CANCEL "+cancels);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);SharedPreferences prefs=Prefs.of(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(20),dp(18),dp(24));root.setBackgroundColor(Color.WHITE);scroll.addView(root);setContentView(scroll);
        root.addView(text("Touch Diagnostics · v3.1.0",24));
        root.addView(text("Test while parked. Tap the green target manually first. Then run a simulated tap and do not touch the screen until the result appears. No Uber offer is touched by these test buttons.",14));
        target=button(root,"X — TAP TEST TARGET",v->{clicks++;counts();line("TARGET CLICK received");});
        target.setContentDescription("Touch test target");target.setTextColor(Color.WHITE);target.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(10,132,66)));target.setMinHeight(dp(65));
        target.setOnTouchListener((v,e)->{switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downs++;downAt=e.getEventTime();line("TARGET DOWN at "+e.getRawX()+","+e.getRawY()+" flags="+e.getFlags());break;
            case MotionEvent.ACTION_UP:ups++;line("TARGET UP; held "+(e.getEventTime()-downAt)+"ms flags="+e.getFlags());break;
            case MotionEvent.ACTION_CANCEL:cancels++;line("TARGET CANCEL");break;
        }counts();return false;});
        counts=text("",14);root.addView(counts);counts();
        button(root,"Test native accessibility click",v->runTest(0,true));
        button(root,"Test 100 ms gesture",v->runTest(100,false));
        button(root,"Test 200 ms gesture",v->runTest(200,false));
        button(root,"Test 300 ms gesture",v->runTest(300,false));
        button(root,"Open accessibility settings",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(text("Live Uber decline method",19));
        root.addView(text("Only applies to offers that already fail your rules and pass existing fresh-X and window verification. The app removes its own overlay before any decline. Native-only never falls back to gestures.",14));
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,TouchMethod.LABELS);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(TouchMethod.normalize(prefs.getInt(TouchMethod.KEY,0)));root.addView(spinner);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){prefs.edit().putInt(TouchMethod.KEY,TouchMethod.normalize(i)).apply();}public void onNothingSelected(AdapterView<?> p){}});
        button(root,"Copy touch report",v->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText("Touch diagnostics",header()+"\n"+log));Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();}});
        button(root,"Clear report",v->{if(!running){log.setLength(0);downs=ups=clicks=cancels=0;counts();line("Report cleared");}});
        report=text("",12);report.setTextIsSelectable(true);root.addView(report);line(header());
    }
    private String header(){return "Offer Guardian 3.1.0 | "+Build.MANUFACTURER+" "+Build.MODEL+" | Android "+Build.VERSION.RELEASE+" | build "+Build.DISPLAY+" | "+getResources().getDisplayMetrics().widthPixels+"x"+getResources().getDisplayMetrics().heightPixels;}
    private void runTest(int duration,boolean nativeClick){
        if(running||!active||!hasWindowFocus())return;
        OfferAccessibilityService service=OfferAccessibilityService.connectedService();if(service==null){line("Accessibility not connected; enable Offer Guardian first.");return;}
        if(!target.isShown()||!target.getGlobalVisibleRect(new android.graphics.Rect())){line("Target not visible: scroll to target first");return;}
        int[] xy=new int[2];target.getLocationOnScreen(xy);int x=xy[0]+target.getWidth()/2,y=xy[1]+target.getHeight()/2;
        final int d=downs,u=ups,c=clicks,ca=cancels,token=++serial;running=true;
        line("START "+(nativeClick?"native ACTION_CLICK":duration+"ms gesture"));
        service.testOwnButton(x,y,duration,nativeClick,msg->{if(active&&token==serial)line(msg);});
        handler.postDelayed(()->{if(!active||token!=serial)return;running=false;boolean pass=clicks-c==1&&(nativeClick||(downs-d==1&&ups-u==1&&cancels-ca==0));line("RESULT "+(pass?"PASS":"NOT VERIFIED")+" | down="+(downs-d)+" up="+(ups-u)+" click="+(clicks-c)+" cancel="+(cancels-ca));},3000);
    }
    @Override protected void onResume(){super.onResume();active=true;}
    @Override protected void onPause(){active=false;running=false;serial++;handler.removeCallbacksAndMessages(null);super.onPause();}
}
