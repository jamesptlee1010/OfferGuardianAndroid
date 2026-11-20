package com.personal.offerguardian;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Private on-device offer history; no account sync or network permission. */
public final class OfferHistoryStore extends SQLiteOpenHelper {
    private static final String DB="guardian_offer_history.db";
    private static final int VERSION=1;
    public static final String UNKNOWN="UNKNOWN", ACCEPTED="ACCEPTED", DECLINED="DECLINED", EXPIRED="EXPIRED";
    private static final String TABLE="offers";

    public static final class Item {
        public long id, seenAt;
        public int cents, minutes, metres;
        public String type, pickup, destination, outcome, action;
    }
    public static final class Summary {
        public int today, accepted, declined, highestCents, estimatedAcceptedCents;
    }

    public OfferHistoryStore(Context context) { super(context,DB,null,VERSION); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE "+TABLE+" ("+
            "id INTEGER PRIMARY KEY AUTOINCREMENT, seen_at INTEGER NOT NULL,"+
            "payout_cents INTEGER NOT NULL, type TEXT NOT NULL, pickup TEXT NOT NULL DEFAULT '',"+
            "destination TEXT NOT NULL DEFAULT '', minutes INTEGER NOT NULL DEFAULT 0,"+
            "metres INTEGER NOT NULL DEFAULT 0, outcome TEXT NOT NULL DEFAULT 'UNKNOWN',"+
            "action TEXT NOT NULL DEFAULT 'NONE')");
        db.execSQL("CREATE INDEX idx_offer_seen ON offers(seen_at DESC)");
        db.execSQL("CREATE INDEX idx_offer_outcome ON offers(outcome,seen_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to) {
        // Future changes must migrate in place: never drop user history.
        if (from!=to) throw new IllegalStateException("Offer history migration missing");
    }

    public synchronized long record(int payoutCents,OfferDetailsParser.Details details) {
        ContentValues values=new ContentValues();
        values.put("seen_at",System.currentTimeMillis());
        values.put("payout_cents",payoutCents);
        values.put("type",details.type);
        values.put("pickup",details.pickup);
        values.put("destination",details.destination);
        values.put("minutes",details.minutes);
        values.put("metres",details.metres);
        return getWritableDatabase().insertOrThrow(TABLE,null,values);
    }

    /** OCR sometimes resolves the second address line one frame later; fill only gaps. */
    public synchronized void fillMissing(long id, OfferDetailsParser.Details more) {
        if(id<=0) return;
        Cursor cur=getReadableDatabase().rawQuery(
            "SELECT pickup,destination,minutes,metres,type FROM offers WHERE id=?",
            new String[]{Long.toString(id)});
        try {
            if(!cur.moveToFirst()) return;
            ContentValues update=new ContentValues();
            if(cur.getString(0).isEmpty() && !more.pickup.isEmpty()) update.put("pickup",more.pickup);
            if(cur.getString(1).isEmpty() && !more.destination.isEmpty()) update.put("destination",more.destination);
            if(cur.getInt(2)==0 && more.minutes>0) update.put("minutes",more.minutes);
            if(cur.getInt(3)==0 && more.metres>0) update.put("metres",more.metres);
            if(cur.getString(4).equals("Delivery") && !more.type.equals("Delivery")) update.put("type",more.type);
            if(update.size()>0) getWritableDatabase().update(TABLE,update,"id=?",new String[]{Long.toString(id)});
        } finally {cur.close();}
    }

    public synchronized void markDeclineAttempt(long id) {
        if(id<=0) return;
        ContentValues c=new ContentValues();c.put("action","DECLINE_REQUESTED");
        getWritableDatabase().update(TABLE,c,"id=?",new String[]{Long.toString(id)});
    }
    /** Confirmed pickup assignment only; never overwrite a manually selected outcome. */
    public synchronized boolean markAcceptedIfUnknown(long id) {
        if(id<=0)return false;
        ContentValues c=new ContentValues();
        c.put("outcome",ACCEPTED);
        c.put("action","ASSIGNMENT_CONFIRMED");
        return getWritableDatabase().update(TABLE,c,"id=? AND outcome=? AND action!='DECLINE_REQUESTED'",
                new String[]{Long.toString(id),UNKNOWN})==1;
    }

    public synchronized void setOutcome(long id,String outcome) {
        if(id<=0 || !(UNKNOWN.equals(outcome) || ACCEPTED.equals(outcome) ||
                DECLINED.equals(outcome) || EXPIRED.equals(outcome))) return;
        ContentValues c=new ContentValues();c.put("outcome",outcome);
        getWritableDatabase().update(TABLE,c,"id=?",new String[]{Long.toString(id)});
    }
    public synchronized void clearAll() {getWritableDatabase().delete(TABLE,null,null);}

    private Item parse(Cursor cur) {
        Item o=new Item();o.id=cur.getLong(0);o.seenAt=cur.getLong(1);
        o.cents=cur.getInt(2);o.type=cur.getString(3);o.pickup=cur.getString(4);
        o.destination=cur.getString(5);o.minutes=cur.getInt(6);o.metres=cur.getInt(7);
        o.outcome=cur.getString(8);o.action=cur.getString(9);return o;
    }
    /** Shared filtering for the list and the stats, so their figures always agree. */
    private static final class Filter {
        final String where;
        final List<String> args;
        Filter(String w,List<String> a) {where=w;args=a;}
    }
    private Filter filter(String tab,String search,long firstIncludedMillis) {
        StringBuilder where=new StringBuilder("1=1");
        List<String> args=new ArrayList<>();
        if("ACCEPTED".equals(tab)) where.append(" AND outcome='ACCEPTED'");
        if(firstIncludedMillis>0) {
            where.append(" AND seen_at>=?");
            args.add(Long.toString(firstIncludedMillis));
        }
        if(search!=null && !search.trim().isEmpty()) {
            where.append(" AND (pickup LIKE ? OR destination LIKE ? OR type LIKE ?)");
            String token="%"+search.trim()+"%";
            args.add(token);args.add(token);args.add(token);
        }
        return new Filter(where.toString(),args);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,int limit,int offset) {
        Filter filter=filter(tab,search,firstIncludedMillis);
        List<String> args=new ArrayList<>(filter.args);
        String order="HIGHEST".equals(tab) ? "payout_cents DESC, seen_at DESC":"seen_at DESC";
        String sql="SELECT id,seen_at,payout_cents,type,pickup,destination,minutes,metres,outcome,action "+
            "FROM offers WHERE "+filter.where+" ORDER BY "+order+" LIMIT ? OFFSET ?";
        args.add(Integer.toString(Math.max(1,Math.min(100,limit))));
        args.add(Integer.toString(Math.max(0,offset)));
        List<Item> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))) {
            while(c.moveToNext())list.add(parse(c));
        }
        return list;
    }
    /** Aggregates all matching rows, not just the first page displayed on screen. */
    public synchronized OfferHistoryStats statistics(String tab,String search,long firstIncludedMillis) {
        Filter filter=filter(tab,search,firstIncludedMillis);
        OfferHistoryStats stats=new OfferHistoryStats();
        String sql="SELECT payout_cents,minutes,metres,outcome FROM offers WHERE "+filter.where;
        try(Cursor c=getReadableDatabase().rawQuery(sql,filter.args.toArray(new String[0]))) {
            while(c.moveToNext())stats.add(c.getInt(0),c.getInt(1),c.getInt(2),c.getString(3));
        }
        return stats;
    }
    public synchronized Item byId(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,seen_at,payout_cents,type,pickup,destination,minutes,metres,outcome,action FROM offers WHERE id=?",
                    new String[]{Long.toString(id)})) {return c.moveToFirst()?parse(c):null;}
    }
    public synchronized Summary summary() {
        Summary s=new Summary();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COUNT(*),COALESCE(SUM(CASE WHEN outcome='ACCEPTED' THEN 1 ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN outcome='DECLINED' THEN 1 ELSE 0 END),0),"+
            "COALESCE(MAX(payout_cents),0),"+
            "COALESCE(SUM(CASE WHEN outcome='ACCEPTED' THEN payout_cents ELSE 0 END),0) " +
            "FROM offers WHERE seen_at>=?",new String[]{Long.toString(todayStart())})) {
            if(c.moveToFirst()) {s.today=c.getInt(0);s.accepted=c.getInt(1);s.declined=c.getInt(2);
                s.highestCents=c.getInt(3);s.estimatedAcceptedCents=c.getInt(4);}
        }return s;
    }
    private long todayStart() {
        Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();
    }
    public synchronized String csv() {
        StringBuilder b=new StringBuilder("Offer time,Estimated payout AUD,Type,Pickup,Destination shown,Estimated minutes,Estimated km,Estimated AUD per km,Estimated AUD per hour,Outcome,Automation action\r\n");
        for(Item v:allRowsForExport()) {
            String when=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(v.seenAt));
            append(b,when);append(b,String.format(Locale.US,"%.2f",v.cents/100.0));
            append(b,v.type);append(b,v.pickup);append(b,v.destination);
            append(b,v.minutes==0?"":Integer.toString(v.minutes));
            append(b,v.metres==0?"":String.format(Locale.US,"%.1f",v.metres/1000.0));
            append(b,OfferRate.dollarsPerKm(v.cents,v.metres));append(b,OfferRate.dollarsPerHour(v.cents,v.minutes));append(b,v.outcome);append(b,v.action);
            b.append("\r\n");
        }return b.toString();
    }
    private List<Item> allRowsForExport() {
        List<Item> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,seen_at,payout_cents,type,pickup,destination,minutes,metres,outcome,action FROM offers ORDER BY seen_at DESC",null)) {
            while(c.moveToNext())out.add(parse(c));
        }return out;
    }
    private static void append(StringBuilder out,String cell) {
        if(out.length()>0 && out.charAt(out.length()-1)!= '\n')out.append(',');
        String safe=cell==null?"":cell.replace("\"","\"\"");
        // Prevent CSV spreadsheet formula injection when opened in Excel.
        if(!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0))>=0) safe="'"+safe;
        out.append('"').append(safe).append('"');
    }
}
