package com.personal.offerguardian;


/** History-only filter using the numeric, confirmed delivery_count SQLite field.
 * Historic rows with ambiguous counts remain 0 (unknown) and appear under All.
 * This never changes live alerting or auto-decline rules. */
public enum OfferHistoryDeliveryFilter {
    ALL("ALL",0,"All"),
    ONE("ONE",1,"1"),
    TWO("TWO",2,"2"),
    THREE("THREE",3,"3");

    public final String code;
    public final int count;
    public final String chipLabel;

    OfferHistoryDeliveryFilter(String code,int count,String chipLabel) {
        this.code=code;
        this.count=count;
        this.chipLabel=chipLabel;
    }

    public static OfferHistoryDeliveryFilter fromCode(String code) {
        for(OfferHistoryDeliveryFilter item:values()) if(item.code.equals(code)) return item;
        return ALL;
    }

    public boolean active() { return this!=ALL; }

    public String summary() {
        if(this==ALL) return "All delivery counts";
        return count==1?"1 delivery only":count+" deliveries only";
    }

    public String cardLabel() {
        if(this==ALL) return "All";
        return count==1?"1 Delivery":count+" Deliveries";
    }

    public boolean matchesCount(int storedCount) {
        return this==ALL || storedCount==count;
    }

    public Sql toSql() {
        if(this==ALL) return new Sql("1=1",new String[0]);
        return new Sql("delivery_count = ?",new String[]{Integer.toString(count)});
    }

    public static final class Sql {
        public final String where;
        public final String[] args;
        Sql(String where,String[] args) {this.where=where;this.args=args;}
    }
}
