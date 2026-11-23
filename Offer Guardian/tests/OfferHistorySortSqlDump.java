package com.personal.offerguardian;

/** Feed the real production SQLite clauses to the standalone SQLite test. */
public final class OfferHistorySortSqlDump {
    public static void main(String[] ignored) {
        for(OfferHistorySort sort:OfferHistorySort.values())
            System.out.println(sort.code+"\t"+sort.orderBy());
        String[] values={"20", "", "", "", "", "", "", ""};
        OfferHistoryFilters.Sql filter=OfferHistoryFilters.fromInputs(values,true).toSql();
        System.out.println("FILTER\t"+filter.where+"\t"+String.join(",",filter.args));
    }
}
