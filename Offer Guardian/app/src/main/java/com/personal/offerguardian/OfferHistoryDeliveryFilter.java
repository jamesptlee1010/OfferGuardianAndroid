package com.personal.offerguardian;

import java.util.Locale;

/** Quick display-only filter for delivery counts on Offer History.
 * This never changes the live rules engine or auto-decline logic.
 * It infers count from the stored type heading shown on the offer card:
 * a bare "Delivery" / "Pack & Deliver" means one; a heading suffix
 * such as "(2)" or "(3)" means a multiple-delivery request. */
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

    public boolean matchesType(String type) {
        if(this==ALL) return true;
        return detectCount(type)==count;
    }

    public Sql toSql() {
        if(this==ALL) return new Sql("1=1", new String[0]);
        if(this==ONE) {
            return new Sql("(type IN ('Delivery','Pack & Deliver') OR type LIKE '%(1)%')", new String[0]);
        }
        return new Sql("type LIKE ?", new String[]{"%("+count+")%"});
    }

    public static int detectCount(String type) {
        String value=type==null?"":type.trim();
        if(value.isEmpty()) return 0;
        if(value.contains("(1)")) return 1;
        if(value.contains("(2)")) return 2;
        if(value.contains("(3)")) return 3;
        String lower=value.toLowerCase(Locale.US);
        if(lower.equals("delivery") || lower.equals("pack & deliver") || lower.equals("pack and deliver")) return 1;
        return 0;
    }

    public static final class Sql {
        public final String where;
        public final String[] args;
        Sql(String where,String[] args) {this.where=where;this.args=args;}
    }
}
