package com.personal.offerguardian;

import android.content.Context;
import android.os.Binder;
import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Shizuku user service; no exported Android component or general command interface. */
public final class OwnTouchProbe extends IOwnTouchProbe.Stub {
    private final int ownerUid;
    public OwnTouchProbe(Context context){ownerUid=context.getApplicationInfo().uid;}
    public OwnTouchProbe(){ownerUid=-1;}
    private void verifyCaller(){if(ownerUid<10000||Binder.getCallingUid()!=ownerUid||Process.myUid()!=2000)throw new SecurityException("Only owning app via ADB shell is supported");}
    @Override public String probe(){verifyCaller();return "User service connected; UID="+Process.myUid();}
    @Override public synchronized String tapOwnTarget(int x,int y){
        verifyCaller();
        try {
            String focus=run("/system/bin/dumpsys","window","windows");
            if(!OwnTouchProbePolicy.allowed(Process.myUid(),Binder.getCallingUid(),ownerUid,x,y,focus))return "BLOCKED: diagnostic activity not uniquely focused or coordinates invalid";
            run("/system/bin/input","tap",Integer.toString(x),Integer.toString(y));
            return "Shell input command completed; check actual target DOWN/UP/CLICK";
        }catch(Exception e){return "Probe failed: "+e.getClass().getSimpleName()+": "+e.getMessage();}
    }
    private static String run(String... args)throws Exception{
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n<=2000000)out.write(b,0,n);}}catch(Exception ignored){}});
        reader.setDaemon(true);reader.start();
        try{if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("command timeout");}reader.join(500);if(reader.isAlive())throw new IllegalStateException("output timeout");if(p.exitValue()!=0)throw new IllegalStateException("command exit "+p.exitValue());return new String(out.toByteArray(),StandardCharsets.UTF_8);}finally{p.destroy();}
    }
    @Override public void destroy(){int caller=Binder.getCallingUid();if(caller==ownerUid||caller==2000)System.exit(0);}
}
