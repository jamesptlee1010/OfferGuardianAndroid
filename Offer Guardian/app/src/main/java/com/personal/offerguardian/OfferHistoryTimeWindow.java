package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A local-device time-of-day filter applied to every day in the chosen date range.
 * A range such as 22:00–02:00 crosses midnight; both ends are inclusive.
 * This never affects the live Uber/Waze rules engine. */
public final class OfferHistoryTimeWindow {
    public final boolean enabled;
    public final int startMinute, endMinute;
    public OfferHistoryTimeWindow(boolean enabled,int startMinute,int endMinute) {
        if(startMinute<0||startMinute>=1440||endMinute<0||endMinute>=1440)
            throw new IllegalArgumentException("Time must be between 00:00 and 23:59");
        this.enabled=enabled;this.startMinute=startMinute;this.endMinute=endMinute;
    }
    public static OfferHistoryTimeWindow disabled(){return new OfferHistoryTimeWindow(false,17*60,20*60);}
    public String summary(){return !enabled?"Any time":clock(startMinute)+"–"+clock(endMinute);}
    public static String clock(int minute) {
        int hour=minute/60;
        return String.format(Locale.US,"%d:%02d %s",(hour%12==0?12:hour%12),minute%60,hour<12?"am":"pm");
    }
    public boolean matchesLocalMinute(int minute) {
        if(!enabled)return true;
        if(minute<0||minute>=1440)return false;
        return startMinute<=endMinute?
                minute>=startMinute&&minute<=endMinute:
                minute>=startMinute||minute<=endMinute;
    }
    public static final class Sql {
        public final String where;
        public final List<String> args;
        Sql(String where,List<String> args){this.where=where;this.args=args;}
    }
    public Sql toSql() {
        List<String> args=new ArrayList<>();
        if(!enabled)return new Sql("1=1",args);
        // seen_at is an epoch-millisecond INTEGER. SQLite 'localtime' uses the
        // Android device time zone, including the historical DST offset.
        String m="(CAST(strftime('%H', seen_at / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60 + " +
                "CAST(strftime('%M', seen_at / 1000, 'unixepoch', 'localtime') AS INTEGER))";
        args.add(Integer.toString(startMinute));args.add(Integer.toString(endMinute));
        String op=startMinute<=endMinute?" AND ":" OR ";
        return new Sql("("+m+" >= CAST(? AS INTEGER)"+op+m+" <= CAST(? AS INTEGER))",args);
    }
}
