package com.personal.offerguardian;

/** Strictly scoped to the primary, focused Uber Driver window. */
final class LiveDeclinePolicy {
    static boolean focusIsUber(String dump){
        if(dump==null)return false;
        int seen=0;
        for(String line:dump.split("\\r?\\n")){
            String s=line.trim();
            if(!s.startsWith("mCurrentFocus="))continue;
            seen++;
            if(!s.matches("mCurrentFocus=Window\\{[^}]* u[0-9]+ com\\.ubercab\\.driver/[^}]+\\}"))return false;
        }
        return seen==1;
    }
    static boolean valid(int shell,int caller,int owner,int x,int y,int w,int h,long now,long deadline){
        return shell==2000&&owner>=10000&&caller==owner&&w>0&&h>0&&w<=10000&&h<=10000
            &&x>0&&x<w&&y>0&&y<h&&deadline>now&&deadline-now<=1900;
    }
}
