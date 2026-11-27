package com.personal.offerguardian;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit, foreground-only Shizuku connection and own-screen touch diagnostic. */
public final class ShizukuDiagnosticsActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status,counts,report;
    private Button target,authorize,connect,test;
    private final StringBuilder log=new StringBuilder();
    private boolean active,binding,running;
    private int down,up,click,cancel,serial;
    private IOwnTouchProbe remote;
    private Shizuku.UserServiceArgs args;
    private final Shizuku.OnBinderReceivedListener received=()->ui.post(this::refresh);
    private final Shizuku.OnBinderDeadListener dead=()->ui.post(()->{remote=null;binding=false;running=false;serial++;line("Shizuku disconnected; test suspended");refresh();});
    private final Shizuku.OnRequestPermissionResultListener permission=(code,result)->ui.post(()->{if(code==320){line(result==PackageManager.PERMISSION_GRANTED?"Permission granted":"Permission denied");refresh();}});
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){ui.post(()->{if(!active||!binding)return;remote=IOwnTouchProbe.Stub.asInterface(binder);binding=false;line("Diagnostic user service connected");refresh();});}
        @Override public void onServiceDisconnected(ComponentName name){ui.post(()->{remote=null;binding=false;running=false;serial++;line("Diagnostic service disconnected");refresh();});}
    };
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout root,String text,int sp){TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(Color.rgb(25,38,45));root.addView(v);return v;}
    private Button button(LinearLayout root,String text,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setOnClickListener(l);root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(24));root.setBackgroundColor(Color.WHITE);scroll.addView(root);setContentView(scroll);
        label(root,"Shizuku diagnostics · v3.3.1",23);
        label(root,"This screen tests only our own button. For live offers, select Shizuku in Touch Diagnostics after a successful test. Test parked; do not touch the screen during the test.",14);
        status=label(root,"Checking…",15);
        target=button(root,"X — TEST TARGET",v->{click++;updateCounts();line("TARGET CLICK received");});target.setTextColor(Color.WHITE);target.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(10,132,66)));target.setMinHeight(dp(64));
        target.setOnTouchListener((v,e)->{switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:down++;line("TARGET DOWN "+e.getRawX()+","+e.getRawY());break;case MotionEvent.ACTION_UP:up++;line("TARGET UP");break;case MotionEvent.ACTION_CANCEL:cancel++;line("TARGET CANCEL");break;}updateCounts();return false;});
        counts=label(root,"",14);updateCounts();
        authorize=button(root,"Authorise Shizuku",v->request());
        connect=button(root,"Connect diagnostic service",v->bindProbe());
        test=button(root,"Test one Shizuku tap on green X",v->runTest());
        button(root,"Refresh connection",v->refresh());
        button(root,"Copy diagnostic report",v->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText("Shizuku diagnostics",status.getText()+"\n"+log));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}});
        report=label(root,"",12);report.setTextIsSelectable(true);
        args=new Shizuku.UserServiceArgs(new ComponentName(this,OwnTouchProbe.class)).daemon(false).processNameSuffix("touch_probe").version(37);
        Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addBinderDeadListener(dead);Shizuku.addRequestPermissionResultListener(permission);
        line(Build.MANUFACTURER+" "+Build.MODEL+" | Android "+Build.VERSION.RELEASE+" | "+Build.DISPLAY);
    }
    private void updateCounts(){if(counts!=null)counts.setText("DOWN "+down+"   UP "+up+"   CLICK "+click+"   CANCEL "+cancel);}
    private void line(String msg){if(report==null)return;if(log.length()>12000)log.delete(0,log.length()-9000);log.append(SystemClock.elapsedRealtime()).append(" | ").append(msg).append('\n');report.setText(log.toString());}
    private boolean granted(){return Shizuku.pingBinder()&&!Shizuku.isPreV11()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
    private void refresh(){if(!active||status==null)return;boolean live=false,allowed=false;String s;
        try{live=Shizuku.pingBinder();if(!live)s="Shizuku not connected. Start it, then return here.";else if(Shizuku.isPreV11())s="Shizuku API too old.";else{allowed=granted();s="Shizuku API "+Shizuku.getVersion()+" | UID "+Shizuku.getUid()+" | permission "+(allowed?"GRANTED":"NOT GRANTED");if(allowed)s+="\nRemote input permission: "+(Shizuku.checkRemotePermission("android.permission.INJECT_EVENTS")==PackageManager.PERMISSION_GRANTED?"reported granted":"reported denied");s+="\nDiagnostic service: "+(remote!=null?"connected":binding?"connecting":"not connected");}}
        catch(RuntimeException e){s="Connection check failed: "+e.getClass().getSimpleName();allowed=false;}
        status.setText(s);authorize.setEnabled(live&&!allowed);connect.setEnabled(allowed&&remote==null&&!binding&&!running);test.setEnabled(allowed&&remote!=null&&!running);
    }
    private void request(){try{if(!Shizuku.pingBinder()){line("Start Shizuku first");return;}if(granted()){refresh();return;}if(Shizuku.shouldShowRequestPermissionRationale()){line("Permission denied previously; review authorised apps in Shizuku");return;}Shizuku.requestPermission(320);}catch(RuntimeException e){line("Permission request failed: "+e.getMessage());}}
    private void bindProbe(){try{if(!active||binding||remote!=null||!granted())return;if(Shizuku.getUid()!=2000){line("ADB shell mode only; root is not supported");return;}binding=true;refresh();Shizuku.bindUserService(args,connection);final int token=++serial;ui.postDelayed(()->{if(active&&binding&&token==serial){line("Diagnostic service connection timed out");release();refresh();}},8000);}catch(RuntimeException e){binding=false;line("Connect failed: "+e.getMessage());refresh();}}
    private void runTest(){if(!active||running||!hasWindowFocus()||remote==null)return;Rect r=new Rect();if(!target.getGlobalVisibleRect(r)||r.width()<target.getWidth()/2||r.height()<target.getHeight()/2){line("Target must be visible");return;}int[] pos=new int[2];target.getLocationOnScreen(pos);int x=pos[0]+target.getWidth()/2,y=pos[1]+target.getHeight()/2;if(!r.contains(x,y)){line("Target centre is not visible");return;}
        final int d=down,u=up,c=click,ca=cancel,token=++serial;final IOwnTouchProbe service=remote;running=true;refresh();line("START one own-screen Shizuku test");
        ui.postDelayed(()->{if(!active||!hasWindowFocus()||!running||serial!=token){if(active&&serial==token){running=false;refresh();line("Test cancelled: no focus");}return;}worker.execute(()->{String result;try{result=service.probe()+" | "+service.tapOwnTarget(x,y);}catch(Exception e){result="Test failed: "+e.getClass().getSimpleName()+": "+e.getMessage();}final String message=result;ui.post(()->{if(!active||serial!=token)return;line(message);ui.postDelayed(()->{if(!active||serial!=token)return;running=false;line("RESULT "+(OwnTouchProbePolicy.verified(down-d,up-u,click-c,cancel-ca)?"PASS":"NOT VERIFIED")+" | DOWN="+(down-d)+" UP="+(up-u)+" CLICK="+(click-c)+" CANCEL="+(cancel-ca));refresh();},500);});});},350);
        ui.postDelayed(()->{if(active&&running&&serial==token){running=false;serial++;line("Test timed out; reconnect before retry");release();refresh();}},9000);
    }
    private void release(){boolean had=remote!=null||binding;remote=null;binding=false;running=false;serial++;if(had&&args!=null){try{Shizuku.unbindUserService(args,connection,true);}catch(RuntimeException ignored){}}}
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onPause(){active=false;release();ui.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onDestroy(){release();Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);Shizuku.removeRequestPermissionResultListener(permission);worker.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
