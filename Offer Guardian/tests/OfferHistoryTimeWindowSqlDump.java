package com.personal.offerguardian;

/** Exports the actual production SQL condition for SQLite integration regression tests. */
public final class OfferHistoryTimeWindowSqlDump {
    public static void main(String[] args) {
        int start=Integer.parseInt(args[0]),end=Integer.parseInt(args[1]);
        OfferHistoryTimeWindow.Sql sql=new OfferHistoryTimeWindow(true,start,end).toSql();
        System.out.println(sql.where);
    }
}
