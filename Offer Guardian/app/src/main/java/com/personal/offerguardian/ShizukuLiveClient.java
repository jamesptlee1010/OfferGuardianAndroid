package com.personal.offerguardian;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import rikka.shizuku.Shizuku;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One in-flight request, explicit opt-in, no fallback. All public calls on main thread. */
final class ShizukuLiveClient {
    interface Reporter {void report(String message);}
    private final Handler handler;
    private final Reporter status;
    private final Shizuku.UserServiceArgs args;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ServiceConnection connection;
    private ILiveDeclineInput remote;
    private boolean busy,closed;
    private long nextBind;
    ShizukuLiveClient(Context context,Handler handler,Reporter status){
        this.handler=handler;this.status=status;
        args=new Shizuku.UserServiceArgs(new ComponentName(context,LiveDeclineInput.class)).daemon(false).processNameSuffix("live_decline").version(36);
    }
    private boolean permitted(){try{return Shizuku.pingBinder()&&Shizuku.getUid()==2000&&Shizuku.getVersion()>=13&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED&&Shizuku.checkRemotePermission("android.permission.INJECT_EVENTS")==PackageManager.PERMISSION_GRANTED;}catch(RuntimeException e){return false;}}
    boolean ready(){return !closed&&!busy&&remote!=null&&remote.asBinder().isBinderAlive()&&permitted();}
    void prepare(){
        if(closed)return;
        if(!permitted()){release();return;}
        if(remote!=null&&remote.asBinder().isBinderAlive())return;
        if(remote!=null)release();
        if(connection!=null||busy||SystemClock.elapsedRealtime()<nextBind)return;
        nextBind=SystemClock.elapsedRealtime()+10000;
        ServiceConnection c=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){handler.post(()->{if(connection!=this||closed)return;remote=ILiveDeclineInput.Stub.asInterface(binder);status.report("Shizuku live input connected (ADB mode)");});}
            @Override public void onServiceDisconnected(ComponentName name){handler.post(()->{if(connection==this){release();status.report("Shizuku live input disconnected; no fallback");}});}
        };
        connection=c;
        try{Shizuku.bindUserService(args,c);handler.postDelayed(()->{if(connection==c&&remote==null){release();status.report("Shizuku live connection timed out; no input");}},8000);}catch(RuntimeException e){release();status.report("Shizuku connection failed: "+e.getClass().getSimpleName());}
    }
    boolean send(int x,int y,int w,int h,long deadline,Reporter result){
        if(!ready()||SystemClock.elapsedRealtime()>=deadline)return false;
        final ILiveDeclineInput service=remote;busy=true;
        worker.execute(()->{String msg;try{msg=service.declineVerified(x,y,w,h,deadline);}catch(Exception e){msg="UNKNOWN: "+e.getClass().getSimpleName();}final String r=msg;handler.post(()->{busy=false;result.report(r);});});
        return true;
    }
    void release(){ServiceConnection c=connection;connection=null;remote=null;if(c!=null){try{Shizuku.unbindUserService(args,c,true);}catch(RuntimeException ignored){}}}
    void close(){closed=true;release();worker.shutdownNow();}
}
