package com.personal.offerguardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Concept B: light, scrollable ledger. No third-party UI or cloud data dependencies. */
public final class OfferHistoryActivity extends Activity {
    private static final int DARK=Color.rgb(23,31,29);
    private static final int GREEN=Color.rgb(5,133,65);
    private static final int GRAY=Color.rgb(106,113,115);
    private static final int BG=Color.rgb(247,250,247);
    private static final int BORDER=Color.rgb(224,231,226);
    private static final int REQUEST_CSV=102;
    private OfferHistoryStore store;
    private LinearLayout root,results,tabs,summary;
    private EditText search;
    private TextView dateButton,headerSub;
    private String selectedTab="ALL";
    private String searchTerm="";
    private boolean todayOnly=true;
    private int visible=35;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store=new OfferHistoryStore(this);
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
        addLine(titleCol,"Offer History  ·  v0.5.0",12,GRAY,false,0);
        headerSub=label("",12,GRAY,false);
        root.addView(headerSub,box(-2,16));

        summary=row();summary.setGravity(Gravity.TOP);root.addView(summary,box(-2,16));
        tabs=row();tabs.setBackground(bg(Color.rgb(237,243,238),0,12));
        tabs.setPadding(dp(4),dp(4),dp(4),dp(4));root.addView(tabs,box(50,14));

        LinearLayout searchRow=row();root.addView(searchRow,box(-2,9));
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
        search.setHint("Search pickup or destination");
        search.setPadding(dp(11),0,dp(11),0);
        search.setBackground(bg(Color.WHITE,BORDER,10));
        searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(49),1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int cnt,int aft) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                searchTerm=s.toString();visible=35;refreshList();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        dateButton=softButton("Today ▾",v->{todayOnly=!todayOnly;visible=35;refreshList();});
        LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(dp(95),dp(49));
        dateParams.leftMargin=dp(7);searchRow.addView(dateButton,dateParams);

        LinearLayout actions=row();root.addView(actions,box(-2,14));
        TextView export=softButton("↓ Export CSV",v->exportCsv());
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView wipe=softButton("Clear history",v->confirmClear());
        LinearLayout.LayoutParams wipeLp=new LinearLayout.LayoutParams(0,dp(43),1);wipeLp.leftMargin=dp(10);
        actions.addView(wipe,wipeLp);
        results=col();root.addView(results,box(-2,8));
        addLine(root,"Offer payouts and distances are estimates shown by Uber at request time, not final earnings or actual travel. Accepted/declined outcomes can be marked manually; unconfirmed outcomes remain Unknown. History stays on this phone.",11,GRAY,false,0);
        refreshList();
    }
    private void tab(String label,String value) {
        TextView t=label(label,14,selectedTab.equals(value)?Color.WHITE:DARK,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(selectedTab.equals(value)?GREEN:Color.TRANSPARENT,0,9));
        t.setOnClickListener(v->{selectedTab=value;visible=35;refreshList();});
        tabs.addView(t,new LinearLayout.LayoutParams(0,-1,1));
    }
    private void summaryItem(String title,String amount,int color,int weight) {
        LinearLayout c=col();c.setBackground(bg(color,0,12));
        c.setPadding(dp(10),dp(12),dp(6),dp(12));
        addLine(c,title,11,GRAY,false,5);addLine(c,amount,19,DARK,true,0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,weight);
        if(summary.getChildCount()>0) p.leftMargin=dp(8);
        summary.addView(c,p);
    }
    private void refreshList() {
        if(results==null)return;
        headerSub.setText("All detected requests, whether accepted, declined or unanswered");
        OfferHistoryStore.Summary s=store.summary();
        summary.removeAllViews();
        summaryItem("Today",Integer.toString(s.today),Color.WHITE,1);
        summaryItem("Accepted",Integer.toString(s.accepted),Color.rgb(226,247,230),1);
        summaryItem("Highest",s.today>0?OfferRate.money(s.highestCents):"—",Color.rgb(229,247,233),1);
        tabs.removeAllViews();tab("All offers","ALL");tab("Accepted","ACCEPTED");tab("Highest","HIGHEST");
        dateButton.setText(todayOnly?"Today ▾":"All dates ▾");
        results.removeAllViews();
        if("ACCEPTED".equals(selectedTab))
            addLine(results,"Estimated accepted-offer total today: "+OfferRate.money(s.estimatedAcceptedCents),14,GREEN,true,12);
        else if("HIGHEST".equals(selectedTab))
            addLine(results,"Highest offered amounts · ranked by payout",13,GRAY,false,12);
        List<OfferHistoryStore.Item> offers=store.list(selectedTab,searchTerm,todayOnly,Math.min(100,visible),0);
        if(offers.isEmpty()) {
            LinearLayout empty=col();empty.setPadding(dp(15),dp(28),dp(15),dp(28));
            empty.setBackground(bg(Color.WHITE,BORDER,14));results.addView(empty,box(-2,8));
            addLine(empty,"No offers in this view",17,DARK,true,9);
            addLine(empty,"Open Uber Driver with monitoring enabled. Newly detected offers appear here. If an outcome is unknown, open an offer to mark it.",13,GRAY,false,0);
            return;
        }
        int rank=0;
        for(OfferHistoryStore.Item item:offers) addOffer(item,"HIGHEST".equals(selectedTab)?++rank:0);
        if(offers.size()>=visible) {
            TextView more=softButton("Show more offers",v->{visible=Math.min(100,visible+35);refreshList();});
            results.addView(more,box(45,12));
        }
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
            "   ·   "+OfferRate.distance(item.metres)+
            "   ·   "+OfferRate.dollarsPerKm(item.cents,item.metres),12,GREEN,true,0);
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
            "%s  ·  %s\n\nPickup: %s\n\nDeliver to: %s\n\nEstimated duration: %s\nEstimated distance: %s\nEstimated payout per km: %s\n\nOutcome: %s%s\n\nSelect an outcome only if you know what happened to this request.",
            OfferRate.money(item.cents),item.type,fallback(item.pickup),fallback(item.destination),
            item.minutes>0?item.minutes+" min":"Unknown",OfferRate.distance(item.metres),
            OfferRate.dollarsPerKm(item.cents,item.metres),offerOutcome(item),
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
    @Override protected void onResume() {super.onResume();if(results!=null)refreshList();}
    @Override protected void onDestroy() {if(store!=null)store.close();super.onDestroy();}
}
