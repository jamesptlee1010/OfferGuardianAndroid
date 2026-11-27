package com.personal.offerguardian;

import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Dedicated non-root user service. No arbitrary command or general tap API. */
public final class LiveDeclineInput extends ILiveDeclineInput.Stub {
    private final int ownerUid;
    public LiveDeclineInput(Context context){ownerUid=context.getApplicationInfo().uid;}
    public LiveDeclineInput(){ownerUid=-1;}
    @Override public synchronized String declineVerified(int x,int y,int w,int h,long deadline){
        int caller=Binder.getCallingUid();
        if(!LiveDeclinePolicy.valid(Process.myUid(),caller,ownerUid,x,y,w,h,SystemClock.elapsedRealtime(),deadline))return "BLOCKED: caller, target or deadline invalid";
        try{
            String focus=run(Math.max(1,deadline-SystemClock.elapsedRealtime()),"/system/bin/dumpsys","window","windows");
            if(!LiveDeclinePolicy.focusIsUber(focus))return "BLOCKED: Uber window focus not verified";
            if(!LiveDeclinePolicy.valid(Process.myUid(),caller,ownerUid,x,y,w,h,SystemClock.elapsedRealtime(),deadline))return "BLOCKED: stale target";
            run(1500,"/system/bin/input","tap",Integer.toString(x),Integer.toString(y));
            return "DISPATCHED: shell command completed; dismissal unconfirmed";
        }catch(Exception e){return "UNKNOWN: "+e.getClass().getSimpleName()+": "+e.getMessage();}
    }
    private static String run(long timeout,String... args)throws Exception{
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n<=2000000)out.write(b,0,n);}}catch(Exception ignored){}});
        reader.setDaemon(true);reader.start();
        try{if(!p.waitFor(timeout,TimeUnit.MILLISECONDS)){p.destroyForcibly();throw new IllegalStateException("command timeout");}reader.join(250);if(reader.isAlive())throw new IllegalStateException("output timeout");if(p.exitValue()!=0)throw new IllegalStateException("command exit "+p.exitValue());return new String(out.toByteArray(),StandardCharsets.UTF_8);}finally{p.destroy();}
    }
    @Override public void destroy(){int caller=Binder.getCallingUid();if(caller==ownerUid||caller==2000)System.exit(0);}
}
