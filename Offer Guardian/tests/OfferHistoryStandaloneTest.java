package com.personal.offerguardian;
import java.util.*;
public class OfferHistoryStandaloneTest {
 private static void eq(Object a,Object b) { if (!Objects.equals(a,b)) throw new AssertionError(a+" != "+b); }
 private static OfferScreenEvaluator.Line line(String t,int y) {return new OfferScreenEvaluator.Line(t,62,y,540,y+36);}
 public static void main(String[] args) {
   eq(OfferRate.dollarsPerKm(1903,20300),"A$0.94/km");
   eq(OfferRate.dollarsPerKm(3059,6600),"A$4.63/km");
   eq(OfferRate.dollarsPerKm(2000,0),"N/A");
   eq(OfferRate.money(3059),"A$30.59");
   eq(OfferRate.distance(20300),"20.3 km");
   List<OfferScreenEvaluator.Line> lines=Arrays.asList(
     line("Navigation map Brisbane",400),line("Pack & Deliver (3)",720),
     line("$30.59",795),line("Est. earnings for completed trip",867),
     line("19 items (34 units)",928),line("56 min (6.6 km) total",985),
     line("Coles (Chermside)",1110),line("Henry Street & Parkhill",1190),
     line("Street, Chermside",1230),line("Match",1360));
   OfferDetailsParser.Details d=OfferDetailsParser.parse(752,lines);
   eq(d.type,"Pack & Deliver");eq(d.minutes,56);eq(d.metres,6600);
   eq(d.pickup,"Coles (Chermside)");
   eq(d.destination,"Henry Street & Parkhill Street, Chermside");
   OfferDetailsParser.Details noMetric=OfferDetailsParser.parse(752,Arrays.asList(
     line("Delivery (2)",720),line("$19.03",790),line("Toast Malone Melts",1120)));
   eq(noMetric.pickup,"");eq(noMetric.destination,"");
   System.out.println("Offer history rate/location tests passed (12 assertions)");
 }
}
