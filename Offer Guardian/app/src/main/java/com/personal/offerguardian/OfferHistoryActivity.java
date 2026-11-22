package com.personal.offerguardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.TypedValue;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Concept B: light, scrollable ledger. No third-party UI or cloud data dependencies. */
public final class OfferHistoryActivity extends Activity {
    private static final int DARK=Color.rgb(23,31,29);
    private static final int GREEN=Color.rgb(5,133,65);
    private static final int GRAY=Color.rgb(106,113,115);
    private static final int MUTED=GRAY;
    private static final int BG=Color.rgb(247,250,247);
    private static final int BORDER=Color.rgb(224,231,226);
    private static final int REQUEST_CSV=102;
    private OfferHistoryStore store;
    private LinearLayout root,results,tabs,summary,averages;
    private EditText search;
    private TextView dateButton,filterButton,filterSummary,headerSub,activeRules;
    private static final String HISTORY_FILTER_PREFS="offer_history_view_filters_v1";
    private OfferHistoryFilters historyFilters=OfferHistoryFilters.none();
    private String selectedTab="ALL";
    private String searchTerm="";
    private String dateRange="TODAY";
    private int visible=35;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store=new OfferHistoryStore(this);
        historyFilters=loadHistoryFilters();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        draw();
    }
    private int dp(float x) {return Math.round(getResources().getDisplayMetrics().density*x);}
    private LinearLayout col() {LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout row() {LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);return c;}
    private LinearLayout.LayoutParams box(int height,int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.bottomMargin=dp(margin);return p;
    }
    private GradientDrawable bg(int color,int border,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(border!=0) d.setStroke(dp(1),border);return d;
    }
    private TextView label(String t,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);
        v.setIncludeFontPadding(true);v.setLineSpacing(dp(1),1.05f);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private void addLine(LinearLayout into,String value,int size,int color,boolean bold,int bottom) {
        into.addView(label(value,size,color,bold),box(-2,bottom));
    }
    private TextView softButton(String title, View.OnClickListener action) {
        TextView b=label(title,13,GREEN,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(9),dp(11),dp(9),dp(11));b.setBackground(bg(Color.WHITE,BORDER,12));
        b.setOnClickListener(action);return b;
    }
    private void draw() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);setContentView(scroll);
        root=col();root.setPadding(dp(16),dp(20),dp(16),dp(28));scroll.addView(root);
        LinearLayout heading=row();root.addView(heading,box(-2,14));
        TextView back=label("‹",32,GREEN,true);back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        heading.addView(back,new LinearLayout.LayoutParams(dp(38),dp(50)));
        LinearLayout titleCol=col();heading.addView(titleCol,new LinearLayout.LayoutParams(0,-2,1));
        addLine(titleCol,"Offer Guardian",23,DARK,true,0);
        addLine(titleCol,"Offer History  ·  v0.9.4",12,GRAY,false,0);
        headerSub=label("",12,GRAY,false);
        root.addView(headerSub,box(-2,16));

        LinearLayout rulesBox=col();
        rulesBox.setPadding(dp(12),dp(10),dp(12),dp(12));
        rulesBox.setBackground(bg(Color.rgb(233,249,237),Color.rgb(185,223,192),12));
        root.addView(rulesBox,box(-2,14));
        addLine(rulesBox,"ACTIVE RULES  ·  Edit →",13,GREEN,true,5);
        activeRules=label("",12,DARK,false);rulesBox.addView(activeRules);
        rulesBox.setOnClickListener(v->startActivity(new Intent(this,OfferRulesActivity.class)));

        summary=row();summary.setGravity(Gravity.TOP);root.addView(summary,box(-2,8));
        averages=row();averages.setGravity(Gravity.TOP);root.addView(averages,box(-2,16));
        tabs=row();tabs.setBackground(bg(Color.rgb(237,243,238),0,12));
        tabs.setPadding(dp(4),dp(4),dp(4),dp(4));root.addView(tabs,box(50,14));

        LinearLayout searchRow=row();root.addView(searchRow,box(-2,9));
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
        search.setHint("Search pickup or destination");
        search.setPadding(dp(11),0,dp(11),0);
        search.setBackground(bg(Color.WHITE,BORDER,10));
        searchRow.addView(search,new LinearLayout.LayoutParams(-1,dp(49)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int cnt,int aft) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                searchTerm=s.toString();visible=35;refreshList();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        LinearLayout controls=row();root.addView(controls,box(-2,7));
        dateButton=softButton("Today ▾",v->selectDateRange());
        controls.addView(dateButton,new LinearLayout.LayoutParams(0,dp(46),1));
        filterButton=softButton("Filters ▾",v->showHistoryFilters());
        LinearLayout.LayoutParams filterParams=new LinearLayout.LayoutParams(0,dp(46),1);
        filterParams.leftMargin=dp(8);
        controls.addView(filterButton,filterParams);
        filterSummary=label("",11,GREEN,false);
        filterSummary.setPadding(dp(9),dp(7),dp(9),dp(7));
        filterSummary.setBackground(bg(Color.rgb(231,247,234),0,9));
        root.addView(filterSummary,box(-2,11));

        LinearLayout actions=row();root.addView(actions,box(-2,14));
        TextView export=softButton("↓ Export all CSV",v->exportCsv());
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView wipe=softButton("Clear history",v->confirmClear());
        LinearLayout.LayoutParams wipeLp=new LinearLayout.LayoutParams(0,dp(43),1);wipeLp.leftMargin=dp(10);
        actions.addView(wipe,wipeLp);
        results=col();root.addView(results,box(-2,8));
        addLine(root,"Payout, kilometres and hourly rates are estimates from Uber’s offer card at request time, not final earnings or actual travel. Accepted/declined outcomes can be marked manually; unconfirmed outcomes remain Unknown. History stays on this phone.",11,GRAY,false,0);
        refreshList();
    }
    private void tab(String label,String value) {
        TextView t=label(label,14,selectedTab.equals(value)?Color.WHITE:DARK,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(selectedTab.equals(value)?GREEN:Color.TRANSPARENT,0,9));
        t.setOnClickListener(v->{selectedTab=value;visible=35;refreshList();});
        tabs.addView(t,new LinearLayout.LayoutParams(0,-1,1));
    }
    private void summaryItem(LinearLayout target,String title,String amount,int color) {
        LinearLayout c=col();c.setBackground(bg(color,0,12));
        c.setPadding(dp(10),dp(12),dp(6),dp(12));
        addLine(c,title,10,GRAY,false,5);
        TextView metric=label(amount,15,GREEN,true);
        metric.setSingleLine(true);
        metric.setAutoSizeTextTypeUniformWithConfiguration(10,16,1,TypedValue.COMPLEX_UNIT_SP);
        c.addView(metric);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        if(target.getChildCount()>0) p.leftMargin=dp(5);
        target.addView(c,p);
    }
    private void selectDateRange() {
        final String[] names={"Today","Last 7 days","Last 30 days","All dates"};
        final String[] codes={"TODAY","7D","30D","ALL"};
        new AlertDialog.Builder(this).setTitle("Offer history period")
            .setSingleChoiceItems(names,dateRangeIndex(),(dialog,index)->{
                dateRange=codes[index];visible=35;dialog.dismiss();refreshList();
            }).setNegativeButton("Cancel",null).show();
    }
    private int dateRangeIndex() {
        switch(dateRange) {case "7D":return 1;case "30D":return 2;case "ALL":return 3;default:return 0;}
    }
    private long firstIncludedMillis() {
        if("ALL".equals(dateRange))return 0;
        Calendar day=Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);
        day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        if("7D".equals(dateRange))day.add(Calendar.DATE,-6);
        if("30D".equals(dateRange))day.add(Calendar.DATE,-29);
        return day.getTimeInMillis();
    }
    private String periodLabel() {
        switch(dateRange) {case "7D":return "Last 7 days ▾";
            case "30D":return "Last 30 days ▾";case "ALL":return "All dates ▾";
            default:return "Today ▾";}
    }
    /** Filters are private history-display preferences; never saved in live offer-rule Prefs. */
    private OfferHistoryFilters loadHistoryFilters() {
        SharedPreferences prefs=getSharedPreferences(HISTORY_FILTER_PREFS,MODE_PRIVATE);
        String[] values=new String[OfferHistoryFilters.FIELD_COUNT];
        for(int i=0;i<values.length;i++)values[i]=prefs.getString("field_"+i,"");
        try {return OfferHistoryFilters.fromInputs(values,prefs.getBoolean("match_all",true));}
        catch(IllegalArgumentException badOldState) {return OfferHistoryFilters.none();}
    }
    private void saveHistoryFilters() {
        SharedPreferences.Editor edit=getSharedPreferences(HISTORY_FILTER_PREFS,MODE_PRIVATE).edit();
        for(int i=0;i<OfferHistoryFilters.FIELD_COUNT;i++)edit.putString("field_"+i,historyFilters.input(i));
        edit.putBoolean("match_all",historyFilters.matchAll());
        edit.apply();
    }
    private void showHistoryFilters() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=col();panel.setPadding(dp(17),dp(12),dp(17),dp(10));
        scroll.addView(panel);
        addLine(panel,"Filter the history you see. These settings never change live offer rules, alerts or auto-declines.",12,GRAY,false,12);
        addLine(panel,"Enter a minimum, maximum or both. Blank means no limit. Values at the boundary are included.",12,GRAY,false,14);
        EditText[] fields=new EditText[OfferHistoryFilters.FIELD_COUNT];
        String[] groupTitles={"PAYMENT · A$", "ESTIMATED ROUTED HOURLY · A$/hr",
            "PAYOUT PER DISTANCE · A$/km", "ESTIMATED DISTANCE · km"};
        for(int i=0;i<OfferHistoryFilters.FIELD_COUNT;i+=2) {
            addLine(panel,groupTitles[i/2],12,GREEN,true,8);
            LinearLayout pair=row();panel.addView(pair,box(-2,16));
            for(int j=i;j<i+2;j++) {
                LinearLayout control=col();
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
                if(j%2==1)cp.leftMargin=dp(9);
                pair.addView(control,cp);
                addLine(control,(j%2==0?"Minimum":"Maximum"),12,DARK,false,5);
                EditText field=new EditText(this);
                field.setTextSize(16);field.setSingleLine(true);field.setHint("Any");
                field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
                field.setPadding(dp(10),0,dp(10),0);
                field.setBackground(bg(Color.WHITE,BORDER,10));
                field.setText(historyFilters.input(j));
                control.addView(field,new LinearLayout.LayoutParams(-1,dp(48)));
                fields[j]=field;
            }
        }
        addLine(panel,"HOW SHOULD THE FILTERS COMBINE?",12,GREEN,true,8);
        RadioGroup combine=new RadioGroup(this);
        combine.setOrientation(RadioGroup.VERTICAL);
        RadioButton all=new RadioButton(this);all.setText("ALL (AND) — every selected metric must match");
        RadioButton any=new RadioButton(this);any.setText("ANY (OR) — at least one metric must match");
        all.setTextSize(13);any.setTextSize(13);
        all.setId(View.generateViewId());any.setId(View.generateViewId());
        combine.addView(all);combine.addView(any);
        combine.check(historyFilters.matchAll()?all.getId():any.getId());
        panel.addView(combine,box(-2,10));
        addLine(panel,"An upper and lower bound on the same metric always form one range. Orders with unverified or missing rates cannot match an hourly or A$/km condition.",11,GRAY,false,0);

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Advanced History Filters")
            .setView(scroll)
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Clear all",(d,w)->{
                historyFilters=OfferHistoryFilters.none();saveHistoryFilters();visible=35;refreshList();
            })
            .setPositiveButton("Apply",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String[] values=new String[OfferHistoryFilters.FIELD_COUNT];
            for(int i=0;i<values.length;i++)values[i]=fields[i].getText().toString();
            try {
                OfferHistoryFilters selected=OfferHistoryFilters.fromInputs(values,all.isChecked());
                historyFilters=selected;
                saveHistoryFilters();
                visible=35;refreshList();dialog.dismiss();
            } catch(IllegalArgumentException error) {
                Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
    private void refreshList() {
        if(results==null)return;
        if(activeRules!=null)activeRules.setText(OfferRuleEngine.summary(Prefs.rules(Prefs.of(this))));
        final long cutoff=firstIncludedMillis();
        OfferHistoryStats s=store.statistics(selectedTab,searchTerm,cutoff,historyFilters);
        summary.removeAllViews();averages.removeAllViews();
        if("ACCEPTED".equals(selectedTab)) {
            headerSub.setText("Confirmed accepted offers and their estimated averages");
            summaryItem(summary,"Accepted",Integer.toString(s.count()),Color.rgb(226,247,230));
            summaryItem(summary,"Avg. offer",s.averageOffer(),Color.rgb(229,249,235));
            averages.setVisibility(View.VISIBLE);
            summaryItem(averages,"Avg. $/hr",s.averageHourly(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/km",s.averagePerKm(),Color.rgb(229,249,235));
        } else if("HIGHEST".equals(selectedTab)) {
            headerSub.setText("All offers ranked by estimated total payout");
            summaryItem(summary,"Highest",s.highestOffer(),Color.rgb(226,247,230));
            summaryItem(summary,"Avg. offer",s.averageOffer(),Color.rgb(229,249,235));
            averages.setVisibility(View.VISIBLE);
            summaryItem(averages,"Avg. $/hr",s.averageHourly(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/km",s.averagePerKm(),Color.rgb(229,249,235));
        } else {
            headerSub.setText("Your offer performance and estimated earning rates");
            summaryItem(summary,historyFilters.active()?"Matching offers":"All offers",Integer.toString(s.count()),Color.WHITE);
            summaryItem(summary,"Accepted",Integer.toString(s.accepted()),Color.rgb(226,247,230));
            summaryItem(summary,"Declined",Integer.toString(s.declined()),Color.rgb(255,238,237));
            summaryItem(summary,"Highest",s.highestOffer(),Color.rgb(229,247,233));
            averages.setVisibility(View.VISIBLE);
            summaryItem(averages,"Avg. offer",s.averageOffer(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/hr",s.averageHourly(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/km",s.averagePerKm(),Color.rgb(229,249,235));
        }
        tabs.removeAllViews();tab("All offers","ALL");tab("Accepted","ACCEPTED");tab("Highest","HIGHEST");
        dateButton.setText(periodLabel());
        filterButton.setText(historyFilters.active()?"Filters ("+historyFilters.activeGroups()+") ▾":"Filters ▾");
        filterButton.setBackground(bg(historyFilters.active()?Color.rgb(225,247,230):Color.WHITE,
            historyFilters.active()?Color.rgb(148,207,160):BORDER,12));
        filterSummary.setVisibility(historyFilters.active()?View.VISIBLE:View.GONE);
        filterSummary.setText(historyFilters.summary()+"   ·   Display only; auto-decline rules unchanged");
        results.removeAllViews();
        if("ACCEPTED".equals(selectedTab))
            addLine(results,"Estimated total of accepted offers in this view: "+s.acceptedEstimate(),13,GREEN,true,9);
        else if("HIGHEST".equals(selectedTab))
            addLine(results,"Highest offered amounts · ranked by payout",13,GRAY,false,9);
        addLine(results,"Averages use every matching offer, including offers below the visible list limit. Hourly and kilometre averages omit offers with missing time or distance.",11,GRAY,false,11);
        List<OfferHistoryStore.Item> offers=store.list(selectedTab,searchTerm,cutoff,historyFilters,Math.min(100,visible),0);
        if(offers.isEmpty()) {
            LinearLayout empty=col();empty.setPadding(dp(15),dp(28),dp(15),dp(28));
            empty.setBackground(bg(Color.WHITE,BORDER,14));results.addView(empty,box(-2,8));
            addLine(empty,"No offers match this view",17,DARK,true,9);
            addLine(empty,historyFilters.active()?"Try a wider price/rate range, change the date period, or clear the history filters.":
                "Open Uber Driver with monitoring enabled. Newly detected offers appear here. If an outcome is unknown, open an offer to mark it.",13,GRAY,false,8);
            if(historyFilters.active()) {
                TextView reset=softButton("Clear history filters",v->{historyFilters=OfferHistoryFilters.none();saveHistoryFilters();visible=35;refreshList();});
                empty.addView(reset,box(45,0));
            }
            return;
        }
        int rank=0;
        for(OfferHistoryStore.Item item:offers)addOffer(item,"HIGHEST".equals(selectedTab)?++rank:0);
        if(offers.size()>=visible) {
            TextView more=softButton("Show more offers",v->{visible=Math.min(100,visible+35);refreshList();});
            results.addView(more,box(45,12));
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if(results!=null)refreshList();
    }
    private TextView pill(String text,int background,int foreground) {
        TextView v=label(text,11,foreground,true);
        v.setPadding(dp(9),dp(6),dp(9),dp(6));v.setBackground(bg(background,0,10));
        return v;
    }
    private void addOffer(OfferHistoryStore.Item item,int rank) {
        LinearLayout card=col();card.setPadding(dp(13),dp(12),dp(13),dp(12));
        card.setBackground(bg(Color.WHITE,BORDER,14));results.addView(card,box(-2,10));
        LinearLayout first=row();card.addView(first,box(-2,8));
        if(rank>0) {
            TextView ranking=pill("#"+rank,Color.rgb(227,246,229),GREEN);
            first.addView(ranking);}
        TextView price=label(OfferRate.money(item.cents),23,rank==1?GREEN:DARK,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=rank>0?dp(7):0;first.addView(price,p);
        int badgeColor=OfferHistoryStore.ACCEPTED.equals(item.outcome)?GREEN:
            OfferHistoryStore.DECLINED.equals(item.outcome)?Color.rgb(168,45,43):GRAY;
        String outcome=offerOutcome(item);
        first.addView(pill(outcome,OfferHistoryStore.ACCEPTED.equals(item.outcome)?Color.rgb(227,249,230):
            OfferHistoryStore.DECLINED.equals(item.outcome)?Color.rgb(255,238,237):Color.rgb(241,243,242),badgeColor));
        addLine(card,item.type+"   ·   "+new SimpleDateFormat("EEE d MMM, h:mm a",Locale.getDefault()).format(new Date(item.seenAt)),12,GRAY,false,8);
        addLine(card,"↗  Pickup: "+fallback(item.pickup),14,DARK,true,5);
        addLine(card,"⌖  Deliver to: "+fallback(item.destination),13,DARK,false,9);
        addLine(card,"◷  "+(item.minutes>0?item.minutes+" min":"Duration unknown")+
            "   ·   "+OfferRate.distance(item.metres),12,MUTED,false,4);
        addLine(card,"◈  "+(item.trustworthyRates()&&item.metres>0?OfferRate.dollarsPerKm(item.cents,item.metres):
                    "A$/km Unknown")+
            "     |     "+(item.trustworthyRates()&&item.minutes>0?OfferRate.dollarsPerHour(item.cents,item.minutes):
                    "A$/hr Unknown"),13,GREEN,true,0);
        if(!item.trustworthyRates())
            addLine(card,"Rate not verified: cannot use for hourly or A$/km filters",11,GRAY,false,0);
        card.setOnClickListener(v->showDetail(item.id));
    }
    private static String fallback(String value) {return value==null||value.trim().isEmpty()?"Not readable on offer":value;}
    private String offerOutcome(OfferHistoryStore.Item item) {
        switch(item.outcome) {
            case OfferHistoryStore.ACCEPTED:return "Accepted";
            case OfferHistoryStore.DECLINED:return "Declined";
            case OfferHistoryStore.EXPIRED:return "Expired";
            default:return "Unknown";
        }
    }
    private void showDetail(long id) {
        OfferHistoryStore.Item item=store.byId(id);if(item==null)return;
        String detail=String.format(Locale.US,
            "%s  ·  %s\n\nPickup: %s\n\nDeliver to: %s\n\nEstimated duration: %s\nEstimated distance: %s\nEstimated payout per km: %s\nEstimated payout per hour: %s\n\nReading status: %s (%d frames)\nParsed time: %d hr + %d min\nRaw payout: %s\nRaw duration: %s\nRaw distance: %s\nValidation: %s\n\nOutcome: %s%s\n\nSelect an outcome only if you know what happened to this request.",
            OfferRate.money(item.cents),item.type,fallback(item.pickup),fallback(item.destination),
            item.minutes>0?item.minutes+" min":"Unknown",OfferRate.distance(item.metres),
            item.trustworthyRates()&&item.metres>0?OfferRate.dollarsPerKm(item.cents,item.metres):"Unknown / unverified",
            item.trustworthyRates()&&item.minutes>0?OfferRate.dollarsPerHour(item.cents,item.minutes):"Unknown / unverified",
            item.extractionStatus,item.verifiedFrames,
            item.parsedHours,item.parsedMinutes,fallback(item.rawPayout),
            fallback(item.rawDuration),fallback(item.rawDistance),
            fallback(item.extractionNote),offerOutcome(item),
            "DECLINE_REQUESTED".equals(item.action)?"\nAutomated decline tap was attempted; Uber confirmation unavailable.":"");
        new AlertDialog.Builder(this).setTitle("Offer details")
            .setMessage(detail).setNeutralButton("Close",null)
            .setPositiveButton("Set outcome",(d,w)->chooseOutcome(id)).show();
    }
    private void chooseOutcome(long id) {
        String[] statuses={"Accepted", "Declined", "Expired / timed out", "Unknown (not confirmed)"};
        String[] values={OfferHistoryStore.ACCEPTED,OfferHistoryStore.DECLINED,OfferHistoryStore.EXPIRED,OfferHistoryStore.UNKNOWN};
        new AlertDialog.Builder(this).setTitle("Confirm offer outcome")
            .setItems(statuses,(d,which)->{
                store.setOutcome(id,values[which]);refreshList();
            }).setNegativeButton("Cancel",null).show();
    }
    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Delete all offer history?")
            .setMessage("This permanently deletes locally stored offers, including accepted and declined records. Export a CSV first if needed.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete history",(d,w)->{
                store.clearAll();refreshList();
            }).show();
    }
    private void exportCsv() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"OfferGuardian-History-"+
            new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+".csv");
        startActivityForResult(intent,REQUEST_CSV);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_CSV||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try(OutputStream stream=getContentResolver().openOutputStream(uri)) {
            if(stream==null)throw new IllegalStateException("Unable to open selected file");
            stream.write(store.csv().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"Offer history exported",Toast.LENGTH_SHORT).show();
        } catch(Exception e) {Toast.makeText(this,"CSV export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onDestroy() {if(store!=null)store.close();super.onDestroy();}
}
