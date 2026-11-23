package com.personal.offerguardian;
import java.io.*;
import java.util.*;
public final class OfferResourceAndBackupStandaloneTest {
 static void check(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
 check(OfferSurfacePolicy.shouldScan(OfferSurfacePolicy.Host.UBER,true));
 check(!OfferSurfacePolicy.shouldScan(OfferSurfacePolicy.Host.WAZE,true));
 check(OfferSurfacePolicy.shouldScan(OfferSurfacePolicy.Host.WAZE,false));
 check(!OfferSurfacePolicy.shouldScan(OfferSurfacePolicy.Host.OTHER,false));
 PushbackReader r=new PushbackReader(new StringReader("a,b,c\r\n\"A, B\",\"line1\nline2\",\"A\"\"B\"\r\n"),1);
 List<String> h=OfferBackupCsv.readRow(r),row=OfferBackupCsv.readRow(r);
 check(row.equals(Arrays.asList("A, B","line1\nline2","A\"B")));
 check(OfferBackupCsv.map(h,row).get("a").equals("A, B"));check(OfferBackupCsv.readRow(r)==null);
 check(OfferBackupCsv.scaled("12.34",100)==1234);
 Map<String,String> m=new HashMap<>();m.put("Offer time epoch ms","1795500100123");check(OfferBackupCsv.timestamp(m)==1795500100123L);
 m.clear();m.put("Offer time","2026-11-24 10:02:03");check(OfferBackupCsv.timestamp(m)>0);
 check(OfferBackupCsv.original("'=test").equals("=test"));
 try{OfferBackupCsv.readRow(new PushbackReader(new StringReader("\"bad"),1));throw new AssertionError();}catch(IOException expected){}
 System.out.println("Resource policy and backup CSV tests passed");
 }
}
