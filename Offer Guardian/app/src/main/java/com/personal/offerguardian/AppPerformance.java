package com.personal.offerguardian;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import java.util.Locale;
final class AppPerformance {
    private static long scans, previousScans, previousTime, previousCpu;
    static synchronized void scan(){scans++;}
    static synchronized String sample(){
        long now=SystemClock.elapsedRealtime(),cpu=Process.getElapsedCpuTime();
        Debug.MemoryInfo mem=new Debug.MemoryInfo();Debug.getMemoryInfo(mem);
        String delta=previousTime==0||now<=previousTime?"CPU and scans/min: refresh again after a test interval":String.format(Locale.US,"CPU: %.1f%% of one core · scans/min: %.1f",100.0*(cpu-previousCpu)/(now-previousTime),60000.0*(scans-previousScans)/(now-previousTime));
        previousTime=now;previousCpu=cpu;previousScans=scans;
        return String.format(Locale.US,"App memory (PSS): %.1f MB\n%s\nScreenshots requested since app process started: %d",mem.getTotalPss()/1024.0,delta,scans);
    }
}
