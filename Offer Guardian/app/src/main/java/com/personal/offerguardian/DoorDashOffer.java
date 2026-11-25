package com.personal.offerguardian;

import java.util.*;
import java.util.regex.*;

/** DoorDash offer parser. Coordinates normalised to 686x1536; no Android dependencies. */
public final class DoorDashOffer {
    private static final Pattern MONEY=Pattern.compile("^\\$\\s*(\\d{1,4})[.,](\\d{2})$");
    private static final Pattern METRICS=Pattern.compile("(?i)^\\s*(\\d{1,2})\\s+stops?\\s*\\(\\s*(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*km\\s*\\)\\s*[•·.∙|]?\\s*(?:(\\d{1,2})\\s*(?:hr|hrs|hour|hours)\\s*)?(\\d{1,3})\\s*min(?:s|utes)?\\s*$");
    private static final Pattern CLOCK=Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    public static final class Read {
        public final int cents,minutes,metres,stops,count,seconds;
        public final String pickup,destination,rawPrice,rawMetrics;
        public final OfferScreenEvaluator.Line target;
        Read(int cents,int minutes,int metres,int stops,int count,int seconds,String pickup,String destination,String rawPrice,String rawMetrics,OfferScreenEvaluator.Line target){
            this.cents=cents;this.minutes=minutes;this.metres=metres;this.stops=stops;this.count=count;this.seconds=seconds;this.pickup=pickup;this.destination=destination;this.rawPrice=rawPrice;this.rawMetrics=rawMetrics;this.target=target;
        }
        public String key(){return cents+"/"+minutes+"/"+metres+"/"+stops+"/"+count+"/"+pickup.toLowerCase(Locale.ROOT)+"/"+destination.toLowerCase(Locale.ROOT);}
        public OfferDetailsParser.Details details(){return new OfferDetailsParser.Details("DoorDash · Delivery",pickup,destination,minutes,metres,0,minutes/60,minutes%60,count,rawMetrics,rawMetrics,minutes>=60,true,false);}
    }
    public static final class Confirmation {
        public final OfferScreenEvaluator.Line target; public final int seconds;
        Confirmation(OfferScreenEvaluator.Line target,int seconds){this.target=target;this.seconds=seconds;}
    }
    private static int cy(OfferScreenEvaluator.Line l){return (l.top+l.bottom)/2;}
    private static String norm(String s){return s.trim().replaceAll("\\s+"," ");}
    private static int timer(List<OfferScreenEvaluator.Line> lines,int y){
        for(OfferScreenEvaluator.Line l:lines){if(Math.abs(cy(l)-y)>65||l.right<510)continue;Matcher m=CLOCK.matcher(l.text);if(m.find()){int s=Integer.parseInt(m.group(2));if(s<60)return Integer.parseInt(m.group(1))*60+s;}}
        return -1;
    }
    public static Read parse(List<OfferScreenEvaluator.Line> input){
        if(input==null)return null;
        List<OfferScreenEvaluator.Line> lines=new ArrayList<>(input);lines.sort(Comparator.comparingInt(DoorDashOffer::cy).thenComparingInt(x->x.left));
        OfferScreenEvaluator.Line decline=null,accept=null,price=null,metric=null;Matcher mm=null;int cents=0;
        for(OfferScreenEvaluator.Line l:lines){String s=norm(l.text);
            if(s.equalsIgnoreCase("Decline")&&l.left>420&&cy(l)<240)decline=l;
            if(s.matches("(?i)^Accept(?:\\s+\\d{1,2}:\\d{2})?$")&&cy(l)>1190&&l.left>90)accept=l;
            Matcher m=METRICS.matcher(s);if(m.matches()&&cy(l)>690&&cy(l)<1200){if(metric!=null)return null;metric=l;mm=m;}
        }
        if(metric==null){
            // Some recognizers split the metrics across neighbouring OCR text boxes.
            // Merge only lines sharing the same baseline, never a map label or a timer.
            for(OfferScreenEvaluator.Line anchor:lines){
                if(cy(anchor)<690||cy(anchor)>1200||anchor.left>120||!norm(anchor.text).matches("(?i)^\\d{1,2}\\s+stops?.*"))continue;
                List<OfferScreenEvaluator.Line> row=new ArrayList<>();
                for(OfferScreenEvaluator.Line l:lines)if(Math.abs(cy(l)-cy(anchor))<=18&&l.left>=anchor.left)row.add(l);
                row.sort(Comparator.comparingInt(l->l.left));StringBuilder joined=new StringBuilder();
                int right=anchor.right, bottom=anchor.bottom;
                for(OfferScreenEvaluator.Line l:row){if(joined.length()>0)joined.append(' ');joined.append(norm(l.text));right=Math.max(right,l.right);bottom=Math.max(bottom,l.bottom);}
                Matcher m=METRICS.matcher(joined);if(m.matches()){if(metric!=null)return null;metric=new OfferScreenEvaluator.Line(joined.toString(),anchor.left,anchor.top,right,bottom);mm=m;}
            }
        }
        if(decline==null||accept==null||metric==null||mm==null)return null;
        for(OfferScreenEvaluator.Line l:lines){Matcher m=MONEY.matcher(norm(l.text));if(m.matches()&&l.left<150&&cy(l)>620&&cy(l)<cy(metric)-28&&cy(l)>cy(metric)-180){if(price!=null)return null;price=l;cents=Integer.parseInt(m.group(1))*100+Integer.parseInt(m.group(2));}}
        if(price==null||cents<=0||cents>100000)return null;
        int stops=Integer.parseInt(mm.group(1));int mins=(mm.group(3)==null?0:Integer.parseInt(mm.group(3))*60)+Integer.parseInt(mm.group(4));
        int metres=(int)Math.round(Double.parseDouble(mm.group(2).replace(',','.'))*1000);
        if(stops<2||mins<1||mins>600||metres<1||metres>300000)return null;
        String pickup="";int drops=0;boolean multiple=false;
        for(OfferScreenEvaluator.Line l:lines){if(cy(l)<=cy(metric)+30||cy(l)>=cy(accept)-35||l.left<40)continue;String s=norm(l.text);
            if(s.toLowerCase(Locale.ROOT).contains("customer drop-off")){drops++;continue;}
            if(s.matches("(?i).*\\b(?:deliveries|orders|drop-offs)\\b.*"))multiple=true;
            if(pickup.isEmpty()&&s.length()>2&&s.matches(".*[A-Za-z].*"))pickup=s;
        }
        // Two stops plus one explicitly labelled customer drop-off is a single delivery.
        // Stacked/ambiguous formats remain count unknown, never assume stops == deliveries.
        if(pickup.isEmpty()||drops<1)return null;
        int count=stops==2&&drops==1&&!multiple?1:0;
        int seconds=timer(lines,cy(accept));
        if(seconds<1)return null;
        return new Read(cents,mins,metres,stops,count,seconds,pickup,"",price.text,metric.text,decline);
    }
    public static Confirmation confirmation(List<OfferScreenEvaluator.Line> lines){
        if(lines==null)return null;boolean questionA=false,questionB=false,details=false;OfferScreenEvaluator.Line decline=null;
        for(OfferScreenEvaluator.Line l:lines){String s=norm(l.text).toLowerCase(Locale.ROOT);
            if(cy(l)>580&&cy(l)<1000){if(s.contains("are you sure you want to"))questionA=true;if(s.contains("decline this offer?"))questionB=true;}
            if(cy(l)>1050&&cy(l)<1370&&s.matches("decline offer(?:\\s+\\d{1,2}:\\d{2})?"))decline=l;
            if(cy(l)>1150&&s.equals("view offer details"))details=true;
        }
        if(!questionA||!questionB||!details||decline==null)return null;
        int sec=timer(lines,cy(decline));return sec>0?new Confirmation(decline,sec):null;
    }
    public static boolean agrees(Read a,Read b,long elapsed){return a!=null&&b!=null&&elapsed>=0&&elapsed<=4500&&a.key().equals(b.key())&&b.seconds<=a.seconds&&a.seconds-b.seconds<=7;}
}
