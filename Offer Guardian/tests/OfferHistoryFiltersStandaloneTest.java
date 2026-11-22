package com.personal.offerguardian;

import java.util.Arrays;
import java.util.Objects;

/** No Android dependencies: tests the *same* range model used to make SQLite WHERE clauses. */
public final class OfferHistoryFiltersStandaloneTest {
    static int n;
    static void check(boolean ok,String context) {
        n++;
        if(!ok)throw new AssertionError(context);
    }
    static void equal(Object a,Object b) {check(Objects.equals(a,b),a+" != "+b);}
    static OfferHistoryFilters f(boolean all,String... fields) {
        if(fields.length!=8)throw new AssertionError("Need eight fields");
        return OfferHistoryFilters.fromInputs(fields,all);
    }
    static void rejects(String... fields) {
        try {f(true,fields);throw new AssertionError("Expected rejection: "+Arrays.toString(fields));}
        catch(IllegalArgumentException expected) {n++;}
    }
    public static void main(String[] args) {
        OfferHistoryFilters empty=OfferHistoryFilters.none();
        check(!empty.active(),"No filters initially");
        equal(empty.activeGroups(),0);
        equal(empty.toSql().where,"1=1");
        check(empty.matches(1,0,0,false),"Unfiltered rows, including old ones, stay visible");
        OfferHistoryFilters payment=f(true,"20","35","","","","","","");
        equal(payment.activeGroups(),1);
        equal(payment.toSql().args.toString(),"[2000, 3500]");
        check(payment.matches(2000,0,0,false),"Minimum payout inclusive, even old rows");
        check(payment.matches(3500,0,0,false),"Maximum payout inclusive");
        check(!payment.matches(1999,61,6800,true),"Below minimum");
        check(!payment.matches(3501,61,6800,true),"Above maximum");
        OfferHistoryFilters hourly=f(true,"","","26.60","27","","","","");
        equal(hourly.toSql().args.toString(),"[2660, 2700]");
        // 2710 cents in 61 min = A$26.6557/hour; hour is NOT stripped.
        check(hourly.matches(2710,61,6800,true),"Long shopping job within hourly band");
        check(!hourly.matches(2710,1,6800,true),"One-minute reading exceeds upper bound");
        check(!hourly.matches(2710,61,6800,false),"Unverified rate never qualifies");
        check(!hourly.matches(2710,0,6800,true),"Unknown time never qualifies");
        OfferHistoryFilters minHourly=f(true,"","","26.66","","","","","");
        check(!minHourly.matches(2710,61,6800,true),"Compare unrounded rate; 26.6557 < 26.66");
        OfferHistoryFilters maxHourly=f(true,"","","","26.65","","","","");
        check(!maxHourly.matches(2710,61,6800,true),"Compare unrounded rate; 26.6557 > 26.65");
        OfferHistoryFilters perKm=f(true,"","","","","3.98","4.00","","");
        check(perKm.matches(2710,61,6800,true),"3.98529/km within bounds");
        check(!perKm.matches(2710,61,6800,false),"Unverified distance rate omitted");
        OfferHistoryFilters distance=f(true,"","","","","","","6.8","6.800");
        check(distance.matches(2710,61,6800,true),"Distance inclusive exact metres");
        check(!distance.matches(2710,61,6799,true),"Distance must not round to 6.8 km");
        check(!distance.matches(2710,61,6800,false),"Unverified distance omitted");
        OfferHistoryFilters twoAll=f(true,"40","","50","","","","","");
        OfferHistoryFilters twoAny=f(false,"40","","50","","","","","");
        check(!twoAll.matches(2710,61,6800,true),"Reject 27.10 / 61min with both 40 and 50 thresholds");
        check(!twoAny.matches(2710,61,6800,true),"Reject 27.10 / 61min with OR too");
        check(!twoAny.matches(2966,67,9800,true),"Reject 29.66 / 67min with OR too");
        check(twoAny.matches(4100,61,6800,false),"OR: payout alone can qualify unverified rates");
        check(!twoAll.matches(4100,61,6800,false),"AND: payout alone cannot qualify");
        check(twoAny.matches(1100,10,1600,true),"OR: hourly rate alone qualifies");
        check(!twoAll.matches(1100,10,1600,true),"AND: hourly alone does not qualify");
        equal(twoAll.toSql().args.toString(),"[4000, 5000]");
        check(twoAll.toSql().where.contains(" AND "),"AND SQL operator");
        check(twoAny.toSql().where.contains(" OR "),"OR SQL operator");
        equal(f(true,"","","","","","","0.001","").toSql().args.toString(),"[1]");
        equal(f(true,"0.01","","","","","","","").toSql().args.toString(),"[1]");
        rejects("20","19","","","","","","");
        rejects("","","25","24","","","","");
        rejects("","","","","","","9","8");
        rejects("1.001","","","","","","","");
        rejects("","","","","","","1.0001","");
        rejects("-1","","","","","","","");
        rejects("9999999","","","","","","","");
        rejects("nonsense","","","","","","","");
        check(twoAny.summary().contains("ANY:"),"Summary reflects chosen combiner");
        check(twoAny.summary().contains("Payment"),"Summary shows criteria");
        check(twoAny.summary().contains("Hourly"),"Summary shows rate");
        System.out.println("PASS OfferHistoryFilters "+n+" assertions");
    }
}
