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
    /** Weekday/shift quick filters. Days: 0=All, 1=Mon .. 7=Sun.
     * Period mask 0 means All. Multiple shifts are ORed, and weekday, shifts,
     * date range, and optional custom time window are combined using AND.
     * Midnight–05:59 belongs to the actual calendar day, not the prior evening. */
    public static final class Schedule {
        public static final int MORNING=1, LUNCH=2, AFTERNOON=4, EVENING=8, NIGHT=16;
        public static final int ALL_PERIODS=31;
        private static final int[] START={6*60,11*60,14*60,17*60,21*60+30};
        private static final int[] END={11*60,14*60,17*60,21*60+30,6*60};
        private static final String[] NAMES={"Morning","Lunch","Afternoon","Evening","Night"};
        public final int weekday, periodMask;
        public Schedule(int weekday,int periodMask) {
            if(weekday<0||weekday>7||periodMask<0||(periodMask&~ALL_PERIODS)!=0)
                throw new IllegalArgumentException("Invalid history weekday or time period");
            this.weekday=weekday;this.periodMask=periodMask;
        }
        public static Schedule any(){return new Schedule(0,0);}
        public boolean active(){return weekday!=0||periodMask!=0;}
        public Schedule withWeekday(int day){return new Schedule(day,periodMask);}
        public Schedule togglePeriod(int bit){
            if(bit==0||(bit & (bit-1))!=0||(bit & ~ALL_PERIODS)!=0)
                throw new IllegalArgumentException("Invalid time period");
            return new Schedule(weekday,periodMask^bit);
        }
        public boolean matches(int calendarDay,int minute) {
            if(calendarDay<1||calendarDay>7||minute<0||minute>=1440)return false;
            if(weekday!=0&&weekday!=calendarDay)return false;
            if(periodMask==0)return true;
            for(int i=0;i<5;i++) if((periodMask&(1<<i))!=0 &&
                    (i==4?(minute>=START[i]||minute<END[i]):
                          (minute>=START[i]&&minute<END[i]))) return true;
            return false;
        }
        public String summary() {
            StringBuilder s=new StringBuilder();
            if(weekday!=0) {
                String[] days={"All", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
                s.append(days[weekday]);
            }
            if(periodMask!=0)for(int i=0;i<5;i++)if((periodMask&(1<<i))!=0){
                if(s.length()>0)s.append(" · ");
                s.append(NAMES[i]);
            }
            return s.length()==0?"All days · all times":s.toString();
        }
        public Sql toSql() {
            List<String> checks=new ArrayList<>();
            List<String> args=new ArrayList<>();
            if(weekday!=0){
                // SQLite Sunday=0; UI uses Monday=1 through Sunday=7.
                checks.add("CAST(strftime('%w',seen_at / 1000,'unixepoch','localtime') AS INTEGER) = CAST(? AS INTEGER)");
                args.add(Integer.toString(weekday%7));
            }
            if(periodMask!=0 && periodMask!=ALL_PERIODS){
                String minute="(CAST(strftime('%H',seen_at / 1000,'unixepoch','localtime') AS INTEGER)*60 + " +
                    "CAST(strftime('%M',seen_at / 1000,'unixepoch','localtime') AS INTEGER))";
                List<String> times=new ArrayList<>();
                for(int i=0;i<5;i++)if((periodMask&(1<<i))!=0){
                    if(i==4)times.add("("+minute+" >= CAST(? AS INTEGER) OR "+minute+" < CAST(? AS INTEGER))");
                    else times.add("("+minute+" >= CAST(? AS INTEGER) AND "+minute+" < CAST(? AS INTEGER))");
                    args.add(Integer.toString(START[i]));args.add(Integer.toString(END[i]));
                }
                checks.add("("+String.join(" OR ",times)+")");
            }
            return new Sql(checks.isEmpty()?"1=1":"("+String.join(" AND ",checks)+")",args);
        }
    }
}
