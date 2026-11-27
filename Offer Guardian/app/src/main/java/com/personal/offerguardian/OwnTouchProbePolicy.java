package com.personal.offerguardian;

/** Diagnostic only: accept a focused, explicitly identified in-app test screen. */
final class OwnTouchProbePolicy {
    private static final String SHORT="com.personal.offerguardian/.ShizukuDiagnosticsActivity";
    private static final String FULL="com.personal.offerguardian/com.personal.offerguardian.ShizukuDiagnosticsActivity";
    static boolean ownFocus(String dump) {
        if(dump==null)return false;
        int seen=0;
        for(String line:dump.split("\\r?\\n")) {
            String l=line.trim();
            if(!l.startsWith("mCurrentFocus="))continue;
            seen++;
            if(!l.contains(" "+SHORT+"}")&&!l.contains(" "+FULL+"}"))return false;
        }
        return seen==1;
    }
    static boolean allowed(int shellUid,int callingUid,int ownerUid,int x,int y,String dump) {
        return shellUid==2000 && ownerUid>=10000 && callingUid==ownerUid && x>0 && y>0 && x<10000 && y<10000 && ownFocus(dump);
    }
    static boolean verified(int down,int up,int click,int cancel) {return down==1&&up==1&&click==1&&cancel==0;}
}
