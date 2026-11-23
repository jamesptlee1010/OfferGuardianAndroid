package com.personal.offerguardian;

public final class OfferHistoryTimeWindowStandaloneTest {
    static int checks;
    static void ok(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        OfferHistoryTimeWindow day=new OfferHistoryTimeWindow(true,17*60,20*60);
        ok(day.matchesLocalMinute(17*60),"5 pm inclusive");
        ok(day.matchesLocalMinute(19*60+59),"7:59 pm included");
        ok(day.matchesLocalMinute(20*60),"8 pm inclusive");
        ok(!day.matchesLocalMinute(16*60+59),"4:59 pm excluded");
        ok(!day.matchesLocalMinute(20*60+1),"8:01 pm excluded");
        ok(day.summary().equals("5:00 pm–8:00 pm"),"display");
        ok(day.toSql().where.contains(" AND "),"SQL daytime AND");
        ok(day.toSql().args.size()==2,"bound args");
        OfferHistoryTimeWindow night=new OfferHistoryTimeWindow(true,22*60,2*60);
        ok(night.matchesLocalMinute(23*60+59),"11:59 pm included");
        ok(night.matchesLocalMinute(0),"midnight included");
        ok(night.matchesLocalMinute(2*60),"2 am included");
        ok(!night.matchesLocalMinute(12*60),"noon excluded");
        ok(night.toSql().where.contains(" OR "),"overnight SQL OR");
        ok(OfferHistoryTimeWindow.disabled().matchesLocalMinute(12*60),"disabled all day");
        ok(!OfferHistoryTimeWindow.disabled().enabled,"disabled flag");
        ok(new OfferHistoryTimeWindow(true,7,7).matchesLocalMinute(7),"single minute inclusive");
        ok(!new OfferHistoryTimeWindow(true,7,7).matchesLocalMinute(8),"single minute excludes others");
        try{new OfferHistoryTimeWindow(true,1440,0);throw new AssertionError("must reject 1440");}
        catch(IllegalArgumentException expected){checks++;}
        System.out.println("OfferHistoryTimeWindow: "+checks+" assertions passed");
    }
}
