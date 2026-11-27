package com.personal.offerguardian;

/** Strictly scoped to the primary, focused Uber Driver window. */
final class LiveDeclinePolicy {
    enum Focus { UBER, MISSING, UNVERIFIED }
    static String primaryQuery(int sdk){return sdk>=36?"displays":"windows";}
    static String alternateQuery(String query){return "displays".equals(query)?"windows":"displays";}
    static Focus focus(String dump){
        if(dump==null)return Focus.MISSING;
        int seen=0;
        for(String line:dump.split("\\r?\\n")){
            String s=line.trim();
            if(!s.startsWith("mCurrentFocus="))continue;
            seen++;
            if(!s.matches("mCurrentFocus=Window\\{[^}]* u[0-9]+ com\\.ubercab\\.driver/[^}]+\\}"))return Focus.UNVERIFIED;
        }
        return seen==0?Focus.MISSING:seen==1?Focus.UBER:Focus.UNVERIFIED;
    }
    static boolean focusIsUber(String dump){return focus(dump)==Focus.UBER;}
    static boolean valid(int shell,int caller,int owner,int x,int y,int w,int h,long now,long deadline){
        return shell==2000&&owner>=10000&&caller==owner&&w>0&&h>0&&w<=10000&&h<=10000
            &&x>0&&x<w&&y>0&&y<h&&deadline>now&&deadline-now<=1900;
    }
}
