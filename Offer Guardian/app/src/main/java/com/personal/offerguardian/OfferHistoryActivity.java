package com.personal.offerguardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
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
import android.widget.HorizontalScrollView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
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
    private static final int REQUEST_IMPORT_CSV=103;
    private OfferHistoryStore store;
    private LinearLayout root,results,tabs,summary,averages,deliveryFilterRow,matchRateRow,weekdayRow,periodRow;
    private int periodScrollX=0;
    private EditText search;
    private TextView dateButton,filterButton,sortButton,filterSummary,sortSummary,headerSub,activeRules;
    private static final String HISTORY_FILTER_PREFS="offer_history_view_filters_v1";
    private static final String HISTORY_SORT_PREFS="offer_history_view_sort_v1";
    private OfferHistorySort historySort=OfferHistorySort.NEWEST;
    private OfferHistoryFilters historyFilters=OfferHistoryFilters.none();
    private OfferHistoryTimeWindow historyTime=OfferHistoryTimeWindow.disabled();
    private OfferHistoryTimeWindow.Schedule schedule=OfferHistoryTimeWindow.Schedule.any();
    private OfferHistoryDeliveryFilter deliveryQuickFilter=OfferHistoryDeliveryFilter.ALL;
    private String selectedTab="ALL";
    private String searchTerm="";
    private String dateRange="TODAY";
    private int visible=35;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store=new OfferHistoryStore(this);
        historyFilters=loadHistoryFilters();
        historyTime=loadHistoryTime();
        schedule=loadSchedule();
        deliveryQuickFilter=loadDeliveryQuickFilter();
        historySort=loadHistorySort();
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
        addLine(titleCol,"Offer History  ·  v0.9.15",12,GRAY,false,0);
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
        averages=row();averages.setGravity(Gravity.TOP);root.addView(averages,box(-2,8));
        matchRateRow=col();root.addView(matchRateRow,box(-2,12));
        tabs=row();tabs.setBackground(bg(Color.rgb(237,243,238),0,12));
        tabs.setPadding(dp(4),dp(4),dp(4),dp(4));root.addView(tabs,box(50,11));
        weekdayRow=col();root.addView(weekdayRow,box(-2,7));
        periodRow=col();root.addView(periodRow,box(-2,12));
        deliveryFilterRow=col();
        root.addView(deliveryFilterRow,box(-2,12));

        LinearLayout searchRow=row();root.addView(searchRow,box(-2,9));
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
        search.setHint("Search pickup, destination or platform");
        search.setPadding(dp(11),0,dp(11),0);
        search.setBackground(bg(Color.WHITE,BORDER,10));
        searchRow.addView(search,new LinearLayout.LayoutParams(-1,dp(49)));
        LinearLayout platformRow=row();root.addView(platformRow,box(-2,7));
        for(String label:new String[]{"All platforms","Uber","DoorDash"}) {
            TextView b=softButton(label,v->search.setText("All platforms".equals(label)?"":"@"+label));
            platformRow.addView(b,new LinearLayout.LayoutParams(0,dp(39),1));
        }
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
        sortButton=softButton("Sort ▾",v->selectHistorySort());
        sortButton.setAutoSizeTextTypeUniformWithConfiguration(10,13,1,TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams sortParams=new LinearLayout.LayoutParams(0,dp(46),1);
        sortParams.leftMargin=dp(8);
        controls.addView(sortButton,sortParams);
        sortSummary=label("",11,GRAY,false);
        sortSummary.setPadding(dp(9),dp(3),dp(9),dp(2));
        root.addView(sortSummary,box(-2,4));
        filterSummary=label("",11,GREEN,false);
        filterSummary.setPadding(dp(9),dp(7),dp(9),dp(7));
        filterSummary.setBackground(bg(Color.rgb(231,247,234),0,9));
        root.addView(filterSummary,box(-2,11));

        LinearLayout actions=row();root.addView(actions,box(-2,7));
        TextView export=softButton("↓ Export CSV",v->exportCsv());
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView importButton=softButton("↑ Import History",v->importCsv());
        LinearLayout.LayoutParams importLp=new LinearLayout.LayoutParams(0,dp(43),1);
        importLp.leftMargin=dp(9);actions.addView(importButton,importLp);
        TextView dedup=softButton("Scan for duplicate offers",v->previewDuplicates());
        root.addView(dedup,box(42,7));
        TextView wipe=softButton("Clear history",v->confirmClear());
        root.addView(wipe,box(42,14));
        results=col();root.addView(results,box(-2,8));
        addLine(root,"Payout, kilometres and hourly rates are estimates from Uber’s offer card at request time, not final earnings or actual travel. Accepted/declined outcomes can be marked manually; unconfirmed outcomes remain Unknown. History stays on this phone. Press and hold an offer card to delete that record.",11,GRAY,false,0);
        refreshList();
    }
    private void tab(String label,String value) {
        TextView t=label(label,14,selectedTab.equals(value)?Color.WHITE:DARK,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(selectedTab.equals(value)?GREEN:Color.TRANSPARENT,0,9));
        t.setOnClickListener(v->{
            selectedTab=value;
            if("HIGHEST".equals(value)) {
                historySort=OfferHistorySort.PAYMENT_HIGH;
                saveHistorySort();
            }
            visible=35;
            refreshList();
        });
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
    private OfferHistoryTimeWindow loadHistoryTime() {
        SharedPreferences p=getSharedPreferences(HISTORY_FILTER_PREFS,MODE_PRIVATE);
        try {
            return new OfferHistoryTimeWindow(p.getBoolean("time_enabled",false),
                    p.getInt("time_start",17*60),p.getInt("time_end",20*60));
        } catch(IllegalArgumentException invalid){return OfferHistoryTimeWindow.disabled();}
    }
    private OfferHistoryTimeWindow.Schedule loadSchedule() {
        SharedPreferences prefs=getSharedPreferences(HISTORY_FILTER_PREFS,MODE_PRIVATE);
        try {return new OfferHistoryTimeWindow.Schedule(prefs.getInt("weekday",0),prefs.getInt("shift_mask",0));}
        catch(IllegalArgumentException e) {return OfferHistoryTimeWindow.Schedule.any();}
    }
    private OfferHistoryDeliveryFilter loadDeliveryQuickFilter() {
        String code=getSharedPreferences(HISTORY_FILTER_PREFS,MODE_PRIVATE)
                .getString("delivery_quick","ALL");
        return OfferHistoryDeliveryFilter.fromCode(code);
    }
    private void saveHistoryFilters() {
        SharedPreferences.Editor edit=getSharedPreferences(HISTORY_FILTER_PREFS,MODE_PRIVATE).edit();
        for(int i=0;i<OfferHistoryFilters.FIELD_COUNT;i++)edit.putString("field_"+i,historyFilters.input(i));
        edit.putBoolean("match_all",historyFilters.matchAll());
        edit.putBoolean("time_enabled",historyTime.enabled);
        edit.putInt("time_start",historyTime.startMinute);
        edit.putInt("time_end",historyTime.endMinute);
        edit.putString("delivery_quick",deliveryQuickFilter.code);
        edit.putInt("weekday",schedule.weekday);
        edit.putInt("shift_mask",schedule.periodMask);
        edit.apply();
    }
    private OfferHistorySort loadHistorySort() {
        String code=getSharedPreferences(HISTORY_SORT_PREFS,MODE_PRIVATE)
            .getString("order",OfferHistorySort.NEWEST.code);
        return OfferHistorySort.fromCode(code);
    }
    private void saveHistorySort() {
        getSharedPreferences(HISTORY_SORT_PREFS,MODE_PRIVATE).edit()
            .putString("order",historySort.code).apply();
    }
    private void selectHistorySort() {
        OfferHistorySort[] choices=OfferHistorySort.values();
        String[] names=new String[choices.length];
        for(int i=0;i<choices.length;i++)names[i]=choices[i].label;
        new AlertDialog.Builder(this).setTitle("Sort Offer History")
            .setSingleChoiceItems(names,historySort.ordinal(),(dialog,index)->{
                historySort=choices[index];
                saveHistorySort();
                visible=35;
                dialog.dismiss();
                refreshList();
            }).setNegativeButton("Cancel",null).show();
    }
    private void renderWeekdayAndPeriods() {
        if(weekdayRow==null || periodRow==null)return;
        weekdayRow.removeAllViews();periodRow.removeAllViews();
        LinearLayout days=row();days.setPadding(dp(4),dp(5),dp(4),dp(5));
        days.setBackground(bg(Color.rgb(237,243,238),0,12));
        weekdayRow.addView(days,box(47,0));
        String[] letters={"All","M","T","W","T","F","S","S"};
        String[] names={"All weekdays","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
        for(int i=0;i<letters.length;i++) {
            final int weekday=i;
            boolean selected=schedule.weekday==weekday;
            TextView chip=label(letters[i],i==0?12:14,selected?Color.WHITE:GREEN,true);
            chip.setGravity(Gravity.CENTER);
            chip.setContentDescription(names[i]);
            chip.setBackground(bg(selected?GREEN:Color.TRANSPARENT,0,9));
            chip.setOnClickListener(v->{
                schedule=schedule.withWeekday(schedule.weekday==weekday?0:weekday);
                saveHistoryFilters();visible=35;refreshList();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(37),i==0?1.4f:1f);
            days.addView(chip,lp);
        }
        // Five multi-select chips remain reachable on narrower phone screens.
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout periods=row();periods.setPadding(dp(2),dp(2),dp(2),dp(2));
        scroller.addView(periods);
        periodRow.addView(scroller,box(-2,0));
        boolean allShifts=schedule.periodMask==0;
        TextView allPeriods=label("All",12,allShifts?Color.WHITE:GREEN,true);
        allPeriods.setGravity(Gravity.CENTER);
        allPeriods.setPadding(dp(13),dp(11),dp(13),dp(11));
        allPeriods.setContentDescription("All time periods"+(allShifts?" selected":""));
        allPeriods.setBackground(bg(allShifts?GREEN:Color.WHITE,allShifts?GREEN:BORDER,12));
        allPeriods.setOnClickListener(v->{periodScrollX=scroller.getScrollX();
            schedule=new OfferHistoryTimeWindow.Schedule(schedule.weekday,0);
            saveHistoryFilters();visible=35;refreshList();
        });
        periods.addView(allPeriods,new LinearLayout.LayoutParams(-2,-2));
        String[] periodNames={"Morning","Lunch","Afternoon","Evening","Night"};
        String[] periodRanges={"6 am to 11 am","11 am to 2 pm","2 pm to 5 pm","5 pm to 9:30 pm","9:30 pm to 6 am"};
        for(int i=0;i<periodNames.length;i++) {
            final int bit=1<<i;
            boolean selected=(schedule.periodMask&bit)!=0;
            TextView chip=label(periodNames[i],12,selected?Color.WHITE:GREEN,true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13),dp(11),dp(13),dp(11));
            chip.setContentDescription(periodNames[i]+" "+periodRanges[i]+(selected?" selected":" not selected"));
            chip.setBackground(bg(selected?GREEN:Color.WHITE,selected?GREEN:BORDER,12));
            chip.setOnClickListener(v->{
                periodScrollX=scroller.getScrollX();
                schedule=schedule.togglePeriod(bit);
                saveHistoryFilters();visible=35;refreshList();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
            lp.leftMargin=dp(7);
            periods.addView(chip,lp);
        }
        scroller.post(()->scroller.scrollTo(periodScrollX,0));
        if(schedule.active()) {
            TextView caption=label(schedule.summary()+" · tap a selected chip again to clear",11,GRAY,false);
            periodRow.addView(caption,box(-2,0));
        }
    }
    private void renderDeliveryQuickFilter() {
        if(deliveryFilterRow==null)return;
        deliveryFilterRow.removeAllViews();
        LinearLayout shell=row();
        shell.setPadding(dp(12),dp(9),dp(12),dp(9));
        shell.setBackground(bg(Color.rgb(239,248,241),Color.rgb(170,214,181),12));
        deliveryFilterRow.addView(shell,box(-2,6));

        TextView lead=label("Deliveries",14,GREEN,true);
        lead.setPadding(dp(2),0,dp(8),0);
        shell.addView(lead);
        TextView divider=label("|",14,Color.rgb(170,190,175),false);
        divider.setPadding(dp(4),0,dp(8),0);
        shell.addView(divider);
        for(OfferHistoryDeliveryFilter filter:OfferHistoryDeliveryFilter.values()) {
            TextView chip=label(filter.chipLabel,14,deliveryQuickFilter==filter?Color.WHITE:GREEN,true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12),dp(7),dp(12),dp(7));
            chip.setBackground(bg(deliveryQuickFilter==filter?GREEN:Color.WHITE,
                    deliveryQuickFilter==filter?GREEN:Color.rgb(170,214,181),10));
            chip.setOnClickListener(v->{
                deliveryQuickFilter=filter;
                saveHistoryFilters();
                visible=35;
                refreshList();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            if(shell.getChildCount()>2) lp.leftMargin=dp(7);
            shell.addView(chip,lp);
        }
        if(deliveryQuickFilter.active()) {
            TextView hint=label("Showing "+deliveryQuickFilter.summary()+" · older unknown-count offers appear under All",12,GRAY,false);
            hint.setGravity(Gravity.CENTER_HORIZONTAL);
            deliveryFilterRow.addView(hint,box(-2,0));
        }
    }
    private boolean anyDisplayFilterActive() {
        return historyFilters.active() || historyTime.enabled || deliveryQuickFilter.active() || schedule.active();
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
        addLine(panel,"TIME OF DAY · LOCAL PHONE TIME",12,GREEN,true,8);
        CheckBox useTime=new CheckBox(this);
        useTime.setText("Only offers received between these times");
        useTime.setChecked(historyTime.enabled);useTime.setTextSize(13);
        panel.addView(useTime,box(-2,7));
        int[] clockTimes={historyTime.startMinute,historyTime.endMinute};
        LinearLayout timeRow=row();panel.addView(timeRow,box(-2,8));
        TextView[] timeButtons=new TextView[2];
        for(int i=0;i<2;i++) {
            final int index=i;
            timeButtons[i]=softButton((i==0?"From ":"To ")+OfferHistoryTimeWindow.clock(clockTimes[i]),v->{
                TimePickerDialog pick=new TimePickerDialog(this,(picker,hour,minute)->{
                    clockTimes[index]=hour*60+minute;
                    timeButtons[index].setText((index==0?"From ":"To ")+
                            OfferHistoryTimeWindow.clock(clockTimes[index]));
                },clockTimes[index]/60,clockTimes[index]%60,false);
                pick.show();
            });
            LinearLayout.LayoutParams clockParams=new LinearLayout.LayoutParams(0,dp(46),1);
            if(i==1)clockParams.leftMargin=dp(9);
            timeRow.addView(timeButtons[i],clockParams);
        }
        addLine(panel,"5 pm–8 pm includes offers at 5:00 pm through 8:00 pm on each selected day. Overnight ranges such as 10 pm–2 am also work. Time is always combined with the date range and other filters.",11,GRAY,false,14);
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
                historyFilters=OfferHistoryFilters.none();historyTime=OfferHistoryTimeWindow.disabled();deliveryQuickFilter=OfferHistoryDeliveryFilter.ALL;schedule=OfferHistoryTimeWindow.Schedule.any();saveHistoryFilters();visible=35;refreshList();
            })
            .setPositiveButton("Apply",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String[] values=new String[OfferHistoryFilters.FIELD_COUNT];
            for(int i=0;i<values.length;i++)values[i]=fields[i].getText().toString();
            try {
                OfferHistoryFilters selected=OfferHistoryFilters.fromInputs(values,all.isChecked());
                historyFilters=selected;
                historyTime=new OfferHistoryTimeWindow(useTime.isChecked(),clockTimes[0],clockTimes[1]);
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
        if(activeRules!=null)activeRules.setText(Prefs.ruleSummary(Prefs.of(this)));
        final long cutoff=firstIncludedMillis();
        OfferHistoryStats s=store.statistics(selectedTab,searchTerm,cutoff,historyFilters,historyTime,deliveryQuickFilter,schedule);
        int timelineTotal=store.totalInTimeline(cutoff,historyTime,schedule);
        boolean criteriaActive=historyFilters.active() || deliveryQuickFilter.active() ||
                !searchTerm.trim().isEmpty();
        matchRateRow.removeAllViews();
        matchRateRow.setVisibility(criteriaActive?View.VISIBLE:View.GONE);
        if(criteriaActive) {
            LinearLayout matchCard=row();
            matchCard.setPadding(dp(13),dp(11),dp(13),dp(11));
            matchCard.setBackground(bg(Color.rgb(228,248,235),Color.rgb(179,218,192),12));
            LinearLayout textCol=col();
            matchCard.addView(textCol,new LinearLayout.LayoutParams(0,-2,1));
            addLine(textCol,"OFFERS MATCHING YOUR FILTERS",11,GREEN,true,3);
            addLine(textCol,s.count()+" of "+timelineTotal+" offers in selected date/day/time window",12,DARK,false,0);
            String matchPercentage=timelineTotal==0?"—":
                    String.format(Locale.US,"%.1f%%",100.0*s.count()/timelineTotal);
            TextView rate=label(matchPercentage,24,GREEN,true);
            rate.setGravity(Gravity.CENTER_VERTICAL);
            matchCard.addView(rate);
            matchRateRow.addView(matchCard,box(-2,0));
        }
        summary.removeAllViews();averages.removeAllViews();
        if("ACCEPTED".equals(selectedTab)) {
            headerSub.setText("Confirmed accepted offers and their estimated averages");
            summaryItem(summary,"Accepted",Integer.toString(s.count()),Color.rgb(226,247,230));
            summaryItem(summary,"Avg. offer",s.averageOffer(),Color.rgb(229,249,235));
            averages.setVisibility(View.VISIBLE);
            summaryItem(averages,"Avg. $/hr",s.averageHourly(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/km",s.averagePerKm(),Color.rgb(229,249,235));
        } else if("HIGHEST".equals(selectedTab)) {
            headerSub.setText("Top offers · "+historySort.label.toLowerCase(Locale.US));
            summaryItem(summary,"Highest",s.highestOffer(),Color.rgb(226,247,230));
            summaryItem(summary,"Avg. offer",s.averageOffer(),Color.rgb(229,249,235));
            averages.setVisibility(View.VISIBLE);
            summaryItem(averages,"Avg. $/hr",s.averageHourly(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/km",s.averagePerKm(),Color.rgb(229,249,235));
        } else {
            headerSub.setText("Your offer performance and estimated earning rates");
            summaryItem(summary,anyDisplayFilterActive()?"Matching offers":"All offers",Integer.toString(s.count()),Color.WHITE);
            summaryItem(summary,"Accepted",Integer.toString(s.accepted()),Color.rgb(226,247,230));
            summaryItem(summary,"Declined",Integer.toString(s.declined()),Color.rgb(255,238,237));
            summaryItem(summary,"Highest",s.highestOffer(),Color.rgb(229,247,233));
            averages.setVisibility(View.VISIBLE);
            summaryItem(averages,"Avg. offer",s.averageOffer(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/hr",s.averageHourly(),Color.rgb(229,249,235));
            summaryItem(averages,"Avg. $/km",s.averagePerKm(),Color.rgb(229,249,235));
        }
        tabs.removeAllViews();tab("All offers","ALL");tab("Accepted","ACCEPTED");tab("Highest","HIGHEST");
        renderWeekdayAndPeriods();
        renderDeliveryQuickFilter();
        dateButton.setText(periodLabel());
        filterButton.setText(historyFilters.active()||historyTime.enabled?
                "Filters ("+(historyFilters.activeGroups()+(historyTime.enabled?1:0))+") ▾":"Filters ▾");
        sortButton.setText(historySort.shortLabel);
        sortButton.setBackground(bg(historySort==OfferHistorySort.NEWEST?Color.WHITE:Color.rgb(225,247,230),
            historySort==OfferHistorySort.NEWEST?BORDER:Color.rgb(148,207,160),12));
        sortSummary.setText("Sorting: "+historySort.label+" · shown after date, search and filters");
        filterButton.setBackground(bg(historyFilters.active()||historyTime.enabled?Color.rgb(225,247,230):Color.WHITE,
            historyFilters.active()||historyTime.enabled?Color.rgb(148,207,160):BORDER,12));
        boolean showSummary=historyFilters.active()||historyTime.enabled||deliveryQuickFilter.active()||schedule.active();
        filterSummary.setVisibility(showSummary?View.VISIBLE:View.GONE);
        StringBuilder summaryText=new StringBuilder();
        if(schedule.active())summaryText.append(schedule.summary());
        if(deliveryQuickFilter.active()) {
            if(summaryText.length()>0) summaryText.append(" · ");
            summaryText.append(deliveryQuickFilter.summary());
        }
        if(historyTime.enabled) {
            if(summaryText.length()>0) summaryText.append(" · ");
            summaryText.append("Received ").append(historyTime.summary());
        }
        if(historyFilters.active()) {
            if(summaryText.length()>0) summaryText.append(" · ");
            summaryText.append(historyFilters.summary());
        }
        if(summaryText.length()==0) summaryText.append("Display only; live rules unchanged");
        else summaryText.append(" · Display only; live rules unchanged");
        filterSummary.setText(summaryText.toString());
        results.removeAllViews();
        if("ACCEPTED".equals(selectedTab))
            addLine(results,"Estimated total of accepted offers in this view: "+s.acceptedEstimate(),13,GREEN,true,9);
        else if("HIGHEST".equals(selectedTab))
            addLine(results,"Ranking: "+historySort.label,13,GRAY,false,9);
        addLine(results,"Averages use every matching offer, including offers below the visible list limit. Hourly and kilometre averages omit offers with missing time or distance.",11,GRAY,false,11);
        List<OfferHistoryStore.Item> offers=store.list(selectedTab,searchTerm,cutoff,historyFilters,historySort,historyTime,deliveryQuickFilter,schedule,Math.min(100,visible),0);
        if(offers.isEmpty()) {
            LinearLayout empty=col();empty.setPadding(dp(15),dp(28),dp(15),dp(28));
            empty.setBackground(bg(Color.WHITE,BORDER,14));results.addView(empty,box(-2,8));
            addLine(empty,"No offers match this view",17,DARK,true,9);
            addLine(empty,anyDisplayFilterActive()?"Try a wider delivery-count filter, time window, price/rate range, date period or clear the history filters.":
                "Open Uber Driver with monitoring enabled. Newly detected offers appear here. If an outcome is unknown, open an offer to mark it.",13,GRAY,false,8);
            if(anyDisplayFilterActive()) {
                TextView reset=softButton("Clear history filters",v->{historyFilters=OfferHistoryFilters.none();historyTime=OfferHistoryTimeWindow.disabled();deliveryQuickFilter=OfferHistoryDeliveryFilter.ALL;schedule=OfferHistoryTimeWindow.Schedule.any();saveHistoryFilters();visible=35;refreshList();});
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
        int deliveryCount=item.deliveryCount;
        if(deliveryCount>0) first.addView(pill(deliveryCount==1?"1 Delivery":deliveryCount+" Deliveries",Color.rgb(227,246,229),GREEN));
        int badgeColor=OfferHistoryStore.ACCEPTED.equals(item.outcome)?GREEN:
            OfferHistoryStore.DECLINED.equals(item.outcome)?Color.rgb(168,45,43):GRAY;
        String outcome=offerOutcome(item);
        first.addView(pill(outcome,OfferHistoryStore.ACCEPTED.equals(item.outcome)?Color.rgb(227,249,230):
            OfferHistoryStore.DECLINED.equals(item.outcome)?Color.rgb(255,238,237):Color.rgb(241,243,242),badgeColor));
        addLine(card,historyTypeLabel(item)+"   ·   "+new SimpleDateFormat("EEE d MMM, h:mm a",Locale.getDefault()).format(new Date(item.seenAt)),12,GRAY,false,8);
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
        card.setOnLongClickListener(v->{confirmDeleteOffer(item);return true;});
    }
    private static String historyTypeLabel(OfferHistoryStore.Item item) {
        String base=item.type==null?"Delivery":item.type;
        if(!base.startsWith("DoorDash · "))base="Uber · "+base;
        if(item.deliveryCount<=0)return base;
        if(base.contains("(multiple)"))base=base.replace("(multiple)","").trim();
        if(base.matches(".*\\([0-9]+\\).*"))return base;
        return item.deliveryCount==1?base:base+" ("+item.deliveryCount+")";
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
            OfferRate.money(item.cents),historyTypeLabel(item)+(item.deliveryCount>0?" · "+item.deliveryCount+" deliveries":" · Count unknown"),fallback(item.pickup),fallback(item.destination),
            item.minutes>0?item.minutes+" min":"Unknown",OfferRate.distance(item.metres),
            item.trustworthyRates()&&item.metres>0?OfferRate.dollarsPerKm(item.cents,item.metres):"Unknown / unverified",
            item.trustworthyRates()&&item.minutes>0?OfferRate.dollarsPerHour(item.cents,item.minutes):"Unknown / unverified",
            item.extractionStatus,item.verifiedFrames,
            item.parsedHours,item.parsedMinutes,fallback(item.rawPayout),
            fallback(item.rawDuration),fallback(item.rawDistance),
            fallback(item.extractionNote),offerOutcome(item),
            "DECLINE_REQUESTED".equals(item.action)?"\nAutomated decline attempted; platform confirmation unavailable.":"DASH_DISMISSED_UNCONFIRMED".equals(item.action)?"\nDoorDash dialog dismissed after second tap; rejection not independently confirmed.":"");
        new AlertDialog.Builder(this).setTitle("Offer details")
            .setMessage(detail).setNeutralButton("Close",null)
            .setPositiveButton("Set outcome",(d,w)->chooseOutcome(id)).show();
    }
    private void confirmDeleteOffer(OfferHistoryStore.Item item) {
        String when=new SimpleDateFormat("EEE d MMM, h:mm a",Locale.getDefault()).format(new Date(item.seenAt));
        new AlertDialog.Builder(this)
            .setTitle("Delete this offer?")
            .setMessage(OfferRate.money(item.cents)+" · "+fallback(item.pickup)+"\n"+when+
                "\n\nPermanently remove only this historical offer? This cannot be undone and will update History statistics.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete offer",(d,w)->{
                if(store.deleteById(item.id)) {
                    visible=35;refreshList();
                    Toast.makeText(this,"Offer deleted",Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this,"Offer was not found",Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void chooseOutcome(long id) {
        String[] statuses={"Accepted", "Declined", "Expired / timed out", "Unknown (not confirmed)"};
        String[] values={OfferHistoryStore.ACCEPTED,OfferHistoryStore.DECLINED,OfferHistoryStore.EXPIRED,OfferHistoryStore.UNKNOWN};
        new AlertDialog.Builder(this).setTitle("Confirm offer outcome")
            .setItems(statuses,(d,which)->{
                store.setOutcome(id,values[which]);refreshList();
            }).setNegativeButton("Cancel",null).show();
    }
    private void previewDuplicates(){
        final java.util.List<OfferHistoryStore.DuplicateGroup> groups;
        try{groups=store.findDuplicates();}catch(Exception e){Toast.makeText(this,"Could not scan history",Toast.LENGTH_LONG).show();return;}
        if(groups.isEmpty()){new AlertDialog.Builder(this).setTitle("Duplicate scan").setMessage("No high-confidence duplicates found. Uncertain records are kept.").setPositiveButton("OK",null).show();return;}
        String[] labels=new String[groups.size()];boolean[] selected=new boolean[groups.size()];
        for(int i=0;i<groups.size();i++){
            OfferHistoryStore.DuplicateGroup g=groups.get(i);OfferHistoryStore.Item k=g.keeper();
            labels[i]=String.format(java.util.Locale.US,"A$%.2f • %d min • %.1f km\n%s → %s\n%s • %d records; remove %d",k.cents/100.0,k.minutes,k.metres/1000.0,k.pickup,k.destination,new java.text.SimpleDateFormat("dd MMM HH:mm:ss",java.util.Locale.US).format(new java.util.Date(k.seenAt)),g.rows.size(),g.rows.size()-1);
        }
        AlertDialog review=new AlertDialog.Builder(this).setTitle("Review suspected duplicates ("+groups.size()+" groups)")
            .setMultiChoiceItems(labels,selected,(d,which,checked)->selected[which]=checked)
            .setNegativeButton("Cancel",null).setNeutralButton("Select all",null)
            .setPositiveButton("Review removal",(d,w)->{
                java.util.List<OfferHistoryStore.DuplicateGroup> chosen=new java.util.ArrayList<>();int count=0;
                for(int i=0;i<selected.length;i++)if(selected[i]){chosen.add(groups.get(i));count+=groups.get(i).rows.size()-1;}
                if(count==0){Toast.makeText(this,"No groups selected",Toast.LENGTH_SHORT).show();return;}
                new AlertDialog.Builder(this).setTitle("Remove up to "+count+" duplicate records?")
                    .setMessage("Export CSV first if you need a backup. One record per selected group is kept, including any single confirmed outcome. Changed or conflicting groups are skipped. Statistics are refreshed.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Remove duplicates",(dd,ww)->{
                        try{int removed=store.mergeDuplicates(chosen);refreshList();Toast.makeText(this,removed+" duplicate records removed",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Duplicate cleanup failed; no changes committed",Toast.LENGTH_LONG).show();}
                    }).show();
            }).create();
        review.show();review.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{for(int i=0;i<selected.length;i++){selected[i]=true;review.getListView().setItemChecked(i,true);}});
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
    private void importCsv() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Some Android file providers label CSV as application/octet-stream.
        // Allow selection, then validate the OfferGuardian CSV header in the importer.
        startActivityForResult(intent,REQUEST_IMPORT_CSV);
    }
    private void readImportedCsv(Uri uri) {
        Toast.makeText(this,"Importing offer history…",Toast.LENGTH_SHORT).show();
        final android.content.Context app=getApplicationContext();
        new Thread(()->{
            OfferHistoryStore importStore=new OfferHistoryStore(app);
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null)throw new java.io.IOException("Could not open selected CSV");
                Reader reader=new InputStreamReader(in,StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT));
                OfferHistoryStore.ImportSummary summary=importStore.importCsv(reader);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(summary.imported>0) {
                        dateRange="ALL"; // Imported historic records may pre-date today.
                        selectedTab="ALL";
                        visible=35;
                        refreshList();
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("History import complete")
                        .setMessage(summary.imported+" offers imported\n"+
                                summary.duplicates+" duplicates skipped\n"+
                                summary.invalid+" invalid rows skipped\n\n"+
                                "The original offer dates and outcomes were preserved. "+
                                "Imported legacy hourly rates remain unverified. "+
                                "Other active History filters may still hide some imported offers.")
                        .setPositiveButton("OK",null).show();
                });
            } catch(Exception error) {
                String message=error.getMessage()==null?"Unable to read this CSV":error.getMessage();
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    new AlertDialog.Builder(this).setTitle("History import unsuccessful")
                        .setMessage("No partial import was saved. "+message)
                        .setPositiveButton("OK",null).show();
                });
            } finally {importStore.close();}
        },"OfferGuardian-History-Import").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==REQUEST_IMPORT_CSV) {readImportedCsv(uri);return;}
        if(request!=REQUEST_CSV)return;
        try(OutputStream stream=getContentResolver().openOutputStream(uri)) {
            if(stream==null)throw new IllegalStateException("Unable to open selected file");
            stream.write(store.csv().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"Offer history exported",Toast.LENGTH_SHORT).show();
        } catch(Exception e) {Toast.makeText(this,"CSV export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onDestroy() {if(store!=null)store.close();super.onDestroy();}
}
