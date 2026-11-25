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
import java.io.Reader;
import java.io.IOException;

/** Private on-device offer history; no account sync or network permission. */
public final class OfferHistoryStore extends SQLiteOpenHelper {
    private static final String DB="guardian_offer_history.db";
    private static final int VERSION=3;
    public static final String UNKNOWN="UNKNOWN", ACCEPTED="ACCEPTED", DECLINED="DECLINED", EXPIRED="EXPIRED";
    private static final String TABLE="offers";

    public static final class Item {
        public long id, seenAt;
        public int cents, minutes, metres, deliveryCount;
        public String type, pickup, destination, outcome, action;
        public String rawPayout, rawDuration, rawDistance, extractionStatus, extractionNote;
        public int verifiedFrames, parsedHours, parsedMinutes;
        public boolean trustworthyRates() {
            return "VERIFIED".equals(extractionStatus)||"CORRECTED".equals(extractionStatus);
        }
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
            "action TEXT NOT NULL DEFAULT 'NONE',"+
            "raw_payout TEXT NOT NULL DEFAULT '', raw_duration TEXT NOT NULL DEFAULT '',"+
            "raw_distance TEXT NOT NULL DEFAULT '', extraction_status TEXT NOT NULL DEFAULT 'LEGACY_UNVERIFIED',"+
            "extraction_note TEXT NOT NULL DEFAULT '', verified_frames INTEGER NOT NULL DEFAULT 0,"+
            "parsed_hours INTEGER NOT NULL DEFAULT 0, parsed_minutes INTEGER NOT NULL DEFAULT 0,"+
            "delivery_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_offer_seen ON offers(seen_at DESC)");
        db.execSQL("CREATE INDEX idx_offer_outcome ON offers(outcome,seen_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to) {
        // Preserve all v0.9.1 rows, but explicitly flag their single-frame
        // rates as unverified. Never silently reinterpret past OCR errors.
        if(from==1&&to>=2) {
            db.execSQL("ALTER TABLE offers ADD COLUMN raw_payout TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE offers ADD COLUMN raw_duration TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE offers ADD COLUMN raw_distance TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE offers ADD COLUMN extraction_status TEXT NOT NULL DEFAULT 'LEGACY_UNVERIFIED'");
            db.execSQL("ALTER TABLE offers ADD COLUMN extraction_note TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE offers ADD COLUMN verified_frames INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE offers ADD COLUMN parsed_hours INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE offers ADD COLUMN parsed_minutes INTEGER NOT NULL DEFAULT 0");
            from=2;
        }
        if(from==2 && to>=3) {
            db.execSQL("ALTER TABLE offers ADD COLUMN delivery_count INTEGER NOT NULL DEFAULT 0");
            // Only recover historical counts where explicit evidence survived.
            // A generic 'Pack & Deliver' or 'Delivery (multiple)' is ambiguous.
            for(int n=1;n<=3;n++) {
                db.execSQL("UPDATE offers SET delivery_count="+n+
                    " WHERE delivery_count=0 AND type IN ('Delivery ("+n+")','Pack & Deliver ("+n+")')");
                db.execSQL("UPDATE offers SET delivery_count="+n+
                    " WHERE delivery_count=0 AND extraction_note LIKE '%confirm "+n+" deliveries.%'");
            }
            from=3;
        }
        if(from!=to)throw new IllegalStateException("Offer history migration missing");
    }

    public synchronized long record(int payoutCents,OfferDetailsParser.Details details,String rawPayout) {
        return record(payoutCents,details,rawPayout,details.metres);
    }
    /** Optional kilometres are stored only if the verifier saw the same value twice. */
    public synchronized long record(int payoutCents,OfferDetailsParser.Details details,
            String rawPayout,int independentlyVerifiedMetres) {
        ContentValues values=new ContentValues();
        values.put("seen_at",System.currentTimeMillis());
        values.put("payout_cents",payoutCents);
        values.put("type",details.type);
        values.put("delivery_count",details.deliveryCount>0 && details.deliveryCount<=12?details.deliveryCount:0);
        values.put("pickup",details.pickup);
        values.put("destination",details.destination);
        values.put("minutes",details.minutes);
        values.put("metres",Math.max(0,independentlyVerifiedMetres));
        values.put("raw_payout",rawPayout==null?"":rawPayout);
        values.put("raw_duration",details.rawDuration);
        values.put("raw_distance",details.rawDistance);
        values.put("extraction_status","VERIFIED");
        values.put("extraction_note","Two matching readings; units and plausibility checked");
        values.put("verified_frames",2);
        values.put("parsed_hours",details.parsedHours);
        values.put("parsed_minutes",details.parsedMinutes);
        return getWritableDatabase().insertOrThrow(TABLE,null,values);
    }

    /**
     * Store a two-image confirmed hour LOWER bound without inventing minutes.
     * History and SQL filters must see an UNKNOWN hourly rate until a later
     * pair of complete, consistent reads upgrades the same row.
     */
    public synchronized long recordHourBound(int payoutCents,
            OfferDetailsParser.Details details,String rawPayout) {
        if (!OfferSafeDecline.hasReliableHourBound(details))
            throw new IllegalArgumentException("No reliable duration lower bound");
        ContentValues values=new ContentValues();
        values.put("seen_at",System.currentTimeMillis());
        values.put("payout_cents",payoutCents);
        values.put("type",details.type);
        values.put("delivery_count",details.deliveryCount>0 && details.deliveryCount<=12?details.deliveryCount:0);
        values.put("pickup",details.pickup);
        values.put("destination",details.destination);
        values.put("minutes",0);  // Deliberately unknown, NOT parsedHours*60.
        values.put("metres",details.metres);
        values.put("raw_payout",rawPayout==null?"":rawPayout);
        values.put("raw_duration",details.rawDuration);
        values.put("raw_distance",details.rawDistance);
        values.put("extraction_status","HOUR_BOUND");
        values.put("extraction_note","Two confirming frames; duration at least " +
                (details.parsedHours*60)+" min. Remaining minutes unreadable; hourly unknown.");
        values.put("verified_frames",2);
        values.put("parsed_hours",details.parsedHours);
        values.put("parsed_minutes",0);
        return getWritableDatabase().insertOrThrow(TABLE,null,values);
    }

    /**
     * A verified delivery-count failure does not require an estimated journey
     * duration. Keep that duration unknown if OCR was incomplete: History must
     * NEVER show a fabricated A$/hour or include it in verified-rate averages.
     */
    public synchronized long recordCountRule(int payoutCents,
            OfferDetailsParser.Details d,String rawPayout) {
        if(d==null||d.deliveryCount<1||d.deliveryCount>12||
                d.pickup==null||d.pickup.trim().length()<3)
            throw new IllegalArgumentException("Delivery count or pickup unverified");
        ContentValues values=new ContentValues();
        values.put("seen_at",System.currentTimeMillis());
        values.put("payout_cents",payoutCents);
        values.put("type",d.type);
        values.put("delivery_count",d.deliveryCount>0 && d.deliveryCount<=12?d.deliveryCount:0);
        values.put("pickup",d.pickup);
        values.put("destination",d.destination);
        values.put("minutes",0); // Unknown, even when one hour is partly visible.
        values.put("metres",d.metres>0?d.metres:0);
        values.put("raw_payout",rawPayout==null?"":rawPayout);
        values.put("raw_duration",d.rawDuration);
        values.put("raw_distance",d.rawDistance);
        values.put("extraction_status","COUNT_ONLY");
        values.put("extraction_note","Two matching delivery headings confirm "+
                d.deliveryCount+" deliveries. Duration not independently verified; hourly unknown.");
        values.put("verified_frames",2);
        values.put("parsed_hours",d.parsedHours);
        values.put("parsed_minutes",d.parsedMinutes);
        return getWritableDatabase().insertOrThrow(TABLE,null,values);
    }

    /** Replace metrics only after two NEW consistent and plausible observations. */
    public synchronized void correctVerified(long id,OfferDetailsParser.Details d,String rawPayout) {
        correctVerified(id,d,rawPayout,d.metres);
    }
    public synchronized void correctVerified(long id,OfferDetailsParser.Details d,
            String rawPayout,int independentlyVerifiedMetres) {
        if(id<=0||!OfferExtractionGuard.check(d).valid)return;
        ContentValues c=new ContentValues();
        c.put("minutes",d.minutes);c.put("metres",Math.max(0,independentlyVerifiedMetres));
        c.put("raw_duration",d.rawDuration);c.put("raw_distance",d.rawDistance);
        c.put("raw_payout",rawPayout==null?"":rawPayout);
        c.put("extraction_status","CORRECTED");
        c.put("extraction_note","Duration/distance revised after two matching new readings");
        c.put("verified_frames",2);
        c.put("parsed_hours",d.parsedHours);c.put("parsed_minutes",d.parsedMinutes);
        if(d.deliveryCount>0&&d.deliveryCount<=12)c.put("delivery_count",d.deliveryCount);
        getWritableDatabase().update(TABLE,c,"id=?",new String[]{Long.toString(id)});
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
            // Never promote a single-frame distance to independently verified data.
            if(cur.getString(4).equals("Delivery") && !more.type.equals("Delivery")) update.put("type",more.type);
            if(update.size()>0) getWritableDatabase().update(TABLE,update,"id=?",new String[]{Long.toString(id)});
        } finally {cur.close();}
    }

    public synchronized void markDeclineAttempt(long id) {
        if(id<=0) return;
        ContentValues c=new ContentValues();c.put("action","DECLINE_REQUESTED");
        getWritableDatabase().update(TABLE,c,"id=?",new String[]{Long.toString(id)});
    }
    public synchronized void markDashDismissed(long id) {
        if(id<=0)return;
        ContentValues c=new ContentValues();c.put("action","DASH_DISMISSED_UNCONFIRMED");
        getWritableDatabase().update(TABLE,c,"id=? AND outcome=? AND action='DECLINE_REQUESTED'",new String[]{Long.toString(id),UNKNOWN});
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
    /** Permanently remove exactly one History record after UI confirmation. */
    public synchronized boolean deleteById(long id) {
        return id>0 && getWritableDatabase().delete(TABLE,"id=?",new String[]{Long.toString(id)})==1;
    }
    public synchronized void clearAll() {getWritableDatabase().delete(TABLE,null,null);}
    public static final class ImportSummary {
        public int imported, duplicates, invalid, processed;
    }

    /** Merge a History CSV without erasing local rows. The entire file is
     * applied in one SQLite transaction; a broken CSV rolls back all inserts.
     * Duplicate matching deliberately ignores OCR quality and outcomes, as
     * these can differ between two exports of the SAME offer.
     */
    public synchronized ImportSummary importCsv(Reader input) throws IOException {
        final ImportSummary summary=new ImportSummary();
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            OfferHistoryCsvImport.Report read=OfferHistoryCsvImport.read(input,entry->{
                final boolean blankAddress=entry.pickup.isEmpty()&&entry.destination.isEmpty();
                // Export timestamps have second precision, while local rows have millis.
                // Compare within the same second to avoid duplicating still-local offers.
                String where="seen_at>=? AND seen_at<? AND payout_cents=? AND pickup=? AND destination=?"+
                    (blankAddress?" AND type=?":"");
                String[] keys=blankAddress?
                    new String[]{Long.toString(entry.seenAt),Long.toString(entry.seenAt+1000),Integer.toString(entry.payoutCents),
                        entry.pickup,entry.destination,entry.type}:
                    new String[]{Long.toString(entry.seenAt),Long.toString(entry.seenAt+1000),Integer.toString(entry.payoutCents),
                        entry.pickup,entry.destination};
                try(Cursor found=db.query(TABLE,new String[]{"id"},where,keys,
                        null,null,null,"1")) {
                    if(found.moveToFirst()) {
                        summary.duplicates++;
                        return;
                    }
                }
                ContentValues values=new ContentValues();
                values.put("seen_at",entry.seenAt);
                values.put("payout_cents",entry.payoutCents);
                values.put("type",entry.type);
                values.put("delivery_count",entry.deliveryCount);
                values.put("pickup",entry.pickup);
                values.put("destination",entry.destination);
                values.put("minutes",entry.minutes);
                values.put("metres",entry.metres);
                values.put("outcome",entry.outcome);
                values.put("action",entry.action);
                values.put("raw_payout",entry.rawPayout);
                values.put("raw_duration",entry.rawDuration);
                values.put("raw_distance",entry.rawDistance);
                values.put("extraction_status",entry.extractionStatus);
                values.put("extraction_note",entry.extractionNote);
                values.put("verified_frames",entry.verifiedFrames);
                values.put("parsed_hours",entry.parsedHours);
                values.put("parsed_minutes",entry.parsedMinutes);
                db.insertOrThrow(TABLE,null,values);
                summary.imported++;
            });
            summary.processed=read.rows;
            summary.invalid=read.invalid;
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return summary;
    }


    private Item parse(Cursor cur) {
        Item o=new Item();o.id=cur.getLong(0);o.seenAt=cur.getLong(1);
        o.cents=cur.getInt(2);o.type=cur.getString(3);o.pickup=cur.getString(4);
        o.destination=cur.getString(5);o.minutes=cur.getInt(6);o.metres=cur.getInt(7);
        o.outcome=cur.getString(8);o.action=cur.getString(9);
        o.rawPayout=cur.getString(10);o.rawDuration=cur.getString(11);
        o.rawDistance=cur.getString(12);o.extractionStatus=cur.getString(13);
        o.extractionNote=cur.getString(14);o.verifiedFrames=cur.getInt(15);
        o.parsedHours=cur.getInt(16);o.parsedMinutes=cur.getInt(17);
        o.deliveryCount=cur.getInt(18);
        return o;
    }
    /** Shared filtering for the list and the stats, so their figures always agree. */
    private static final class Filter {
        final String where;
        final List<String> args;
        Filter(String w,List<String> a) {where=w;args=a;}
    }
    private Filter filter(String tab,String search,long firstIncludedMillis,OfferHistoryFilters historyFilters,
            OfferHistoryTimeWindow timeWindow,OfferHistoryDeliveryFilter deliveryFilter,OfferHistoryTimeWindow.Schedule schedule) {
        StringBuilder where=new StringBuilder("1=1");
        List<String> args=new ArrayList<>();
        if("ACCEPTED".equals(tab)) where.append(" AND outcome='ACCEPTED'");
        if(firstIncludedMillis>0) {
            where.append(" AND seen_at>=?");
            args.add(Long.toString(firstIncludedMillis));
        }
        if("@Uber".equals(search)) {where.append(" AND type NOT LIKE 'DoorDash · %'");}
        else if("@DoorDash".equals(search)) {where.append(" AND type LIKE 'DoorDash · %'");}
        else if(search!=null && !search.trim().isEmpty()) {
            where.append(" AND (pickup LIKE ? OR destination LIKE ? OR type LIKE ?)");
            String token="%"+search.trim()+"%";
            args.add(token);args.add(token);args.add(token);
        }
        if(historyFilters!=null && historyFilters.active()) {
            OfferHistoryFilters.Sql display=historyFilters.toSql();
            where.append(" AND ").append(display.where);
            args.addAll(display.args);
        }
        if(timeWindow!=null&&timeWindow.enabled) {
            OfferHistoryTimeWindow.Sql window=timeWindow.toSql();
            where.append(" AND ").append(window.where);
            args.addAll(window.args);
        }
        if(deliveryFilter!=null && deliveryFilter.active()) {
            OfferHistoryDeliveryFilter.Sql quick=deliveryFilter.toSql();
            where.append(" AND ").append(quick.where);
            for(String arg:quick.args) args.add(arg);
        }
        if(schedule!=null && schedule.active()) {
            OfferHistoryTimeWindow.Sql quick=schedule.toSql();
            where.append(" AND ").append(quick.where);
            args.addAll(quick.args);
        }
        return new Filter(where.toString(),args);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,int limit,int offset) {
        return list(tab,search,firstIncludedMillis,OfferHistoryFilters.none(),limit,offset);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,
                                        OfferHistoryFilters displayFilters,int limit,int offset) {
        return list(tab,search,firstIncludedMillis,displayFilters,
            "HIGHEST".equals(tab)?OfferHistorySort.PAYMENT_HIGH:OfferHistorySort.NEWEST,
            limit,offset);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,
                                        OfferHistoryFilters displayFilters,OfferHistorySort sort,
                                        int limit,int offset) {
        return list(tab,search,firstIncludedMillis,displayFilters,sort,
                OfferHistoryTimeWindow.disabled(),OfferHistoryDeliveryFilter.ALL,limit,offset);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,
            OfferHistoryFilters displayFilters,OfferHistorySort sort,OfferHistoryTimeWindow timeWindow,
            int limit,int offset) {
        return list(tab,search,firstIncludedMillis,displayFilters,sort,timeWindow,OfferHistoryDeliveryFilter.ALL,limit,offset);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,
            OfferHistoryFilters displayFilters,OfferHistorySort sort,OfferHistoryTimeWindow timeWindow,
            OfferHistoryDeliveryFilter deliveryFilter,int limit,int offset) {
        return list(tab,search,firstIncludedMillis,displayFilters,sort,timeWindow,
                deliveryFilter,OfferHistoryTimeWindow.Schedule.any(),limit,offset);
    }
    public synchronized List<Item> list(String tab,String search,long firstIncludedMillis,
            OfferHistoryFilters displayFilters,OfferHistorySort sort,OfferHistoryTimeWindow timeWindow,
            OfferHistoryDeliveryFilter deliveryFilter,OfferHistoryTimeWindow.Schedule schedule,int limit,int offset) {
        Filter filter=filter(tab,search,firstIncludedMillis,displayFilters,timeWindow,deliveryFilter,schedule);
        List<String> args=new ArrayList<>(filter.args);
        String order=(sort==null?OfferHistorySort.NEWEST:sort).orderBy();
        String sql="SELECT id,seen_at,payout_cents,type,pickup,destination,minutes,metres,outcome,action,raw_payout,raw_duration,raw_distance,extraction_status,extraction_note,verified_frames,parsed_hours,parsed_minutes,delivery_count "+
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
        return statistics(tab,search,firstIncludedMillis,OfferHistoryFilters.none());
    }
    public synchronized OfferHistoryStats statistics(String tab,String search,long firstIncludedMillis,
                                                     OfferHistoryFilters displayFilters) {
        return statistics(tab,search,firstIncludedMillis,displayFilters,OfferHistoryTimeWindow.disabled(),OfferHistoryDeliveryFilter.ALL);
    }
    public synchronized OfferHistoryStats statistics(String tab,String search,long firstIncludedMillis,
            OfferHistoryFilters displayFilters,OfferHistoryTimeWindow timeWindow) {
        return statistics(tab,search,firstIncludedMillis,displayFilters,timeWindow,OfferHistoryDeliveryFilter.ALL);
    }
    public synchronized OfferHistoryStats statistics(String tab,String search,long firstIncludedMillis,
            OfferHistoryFilters displayFilters,OfferHistoryTimeWindow timeWindow,OfferHistoryDeliveryFilter deliveryFilter) {
        return statistics(tab,search,firstIncludedMillis,displayFilters,timeWindow,
                deliveryFilter,OfferHistoryTimeWindow.Schedule.any());
    }
    public synchronized OfferHistoryStats statistics(String tab,String search,long firstIncludedMillis,
            OfferHistoryFilters displayFilters,OfferHistoryTimeWindow timeWindow,
            OfferHistoryDeliveryFilter deliveryFilter,OfferHistoryTimeWindow.Schedule schedule) {
        Filter filter=filter(tab,search,firstIncludedMillis,displayFilters,timeWindow,deliveryFilter,schedule);
        OfferHistoryStats stats=new OfferHistoryStats();
        String sql="SELECT payout_cents,CASE WHEN extraction_status IN ('VERIFIED','CORRECTED') " +
            "THEN minutes ELSE 0 END," +
            "CASE WHEN extraction_status IN ('VERIFIED','CORRECTED') " +
            "THEN metres ELSE 0 END,outcome FROM offers WHERE "+filter.where;
        try(Cursor c=getReadableDatabase().rawQuery(sql,filter.args.toArray(new String[0]))) {
            while(c.moveToNext())stats.add(c.getInt(0),c.getInt(1),c.getInt(2),c.getString(3));
        }
        return stats;
    }
    /** Denominator for the History match rate: every offer in the chosen date
     * range and time-of-day window, BEFORE search, thresholds, tabs and count. */
    public synchronized int totalInTimeline(long firstIncludedMillis,OfferHistoryTimeWindow timeWindow) {
        return totalInTimeline(firstIncludedMillis,timeWindow,OfferHistoryTimeWindow.Schedule.any());
    }
    public synchronized int totalInTimeline(long firstIncludedMillis,OfferHistoryTimeWindow timeWindow,
            OfferHistoryTimeWindow.Schedule schedule) {
        StringBuilder where=new StringBuilder("1=1");
        List<String> args=new ArrayList<>();
        if(firstIncludedMillis>0) {
            where.append(" AND seen_at>=?");args.add(Long.toString(firstIncludedMillis));
        }
        if(timeWindow!=null && timeWindow.enabled) {
            OfferHistoryTimeWindow.Sql window=timeWindow.toSql();
            where.append(" AND ").append(window.where);
            args.addAll(window.args);
        }
        if(schedule!=null && schedule.active()) {
            OfferHistoryTimeWindow.Sql shift=schedule.toSql();
            where.append(" AND ").append(shift.where);
            args.addAll(shift.args);
        }
        try(Cursor cursor=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM offers WHERE "+where,args.toArray(new String[0]))) {
            return cursor.moveToFirst()?cursor.getInt(0):0;
        }
    }
    public synchronized Item byId(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,seen_at,payout_cents,type,pickup,destination,minutes,metres,outcome,action,raw_payout,raw_duration,raw_distance,extraction_status,extraction_note,verified_frames,parsed_hours,parsed_minutes,delivery_count FROM offers WHERE id=?",
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
        StringBuilder b=new StringBuilder("Offer time,Estimated payout AUD,Type,Delivery count,Pickup,Destination shown,Estimated minutes,Estimated km,Estimated AUD per km,Estimated AUD per hour,Outcome,Automation action,Raw payout OCR,Raw duration OCR,Raw distance OCR,Extraction status,Extraction note,Verified frames,Parsed hours,Parsed minute component\r\n");
        for(Item v:allRowsForExport()) {
            String when=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(v.seenAt));
            append(b,when);append(b,String.format(Locale.US,"%.2f",v.cents/100.0));
            append(b,v.type);append(b,v.deliveryCount>0?Integer.toString(v.deliveryCount):"UNKNOWN");append(b,v.pickup);append(b,v.destination);
            append(b,v.minutes==0?"":Integer.toString(v.minutes));
            append(b,v.metres==0?"":String.format(Locale.US,"%.1f",v.metres/1000.0));
            append(b,v.trustworthyRates()?OfferRate.dollarsPerKm(v.cents,v.metres):"UNVERIFIED");
            append(b,v.trustworthyRates()?OfferRate.dollarsPerHour(v.cents,v.minutes):"UNVERIFIED");
            append(b,v.outcome);append(b,v.action);
            append(b,v.rawPayout);append(b,v.rawDuration);append(b,v.rawDistance);
            append(b,v.extractionStatus);append(b,v.extractionNote);
            append(b,Integer.toString(v.verifiedFrames));
            append(b,Integer.toString(v.parsedHours));
            append(b,Integer.toString(v.parsedMinutes));
            b.append("\r\n");
        }return b.toString();
    }
    private List<Item> allRowsForExport() {
        List<Item> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,seen_at,payout_cents,type,pickup,destination,minutes,metres,outcome,action,raw_payout,raw_duration,raw_distance,extraction_status,extraction_note,verified_frames,parsed_hours,parsed_minutes,delivery_count FROM offers ORDER BY seen_at DESC",null)) {
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
