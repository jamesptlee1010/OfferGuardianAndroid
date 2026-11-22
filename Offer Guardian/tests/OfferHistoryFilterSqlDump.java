package com.personal.offerguardian;

/** Emits filter SQL straight from production filters for SQLite integration tests. */
public final class OfferHistoryFilterSqlDump {
    static void one(String label, boolean all, String[] input) {
        OfferHistoryFilters.Sql clause=OfferHistoryFilters.fromInputs(input,all).toSql();
        System.out.println(label+"\t"+clause.where+"\t"+String.join(",",clause.args));
    }
    static String[] fields(String... values) {
        if(values.length!=8)throw new AssertionError("8 fields expected");
        return values;
    }
    public static void main(String[] unused) {
        one("unfiltered",true,fields("","","","","","","",""));
        one("price_range",true,fields("20","35","","","","","",""));
        one("hourly_range",true,fields("","","26.60","27","","","",""));
        one("hourly_max",true,fields("","","","30","","","",""));
        one("perkm_range",true,fields("","","","","3.98","4.00","",""));
        one("distance_range",true,fields("","","","","","","6.8","9.8"));
        one("price_hourly_and",true,fields("40","","50","","","","",""));
        one("price_hourly_or",false,fields("40","","50","","","","",""));
        one("price_or_unverified",false,fields("","30","70","","","","",""));
    }
}
