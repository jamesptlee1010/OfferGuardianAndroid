package com.personal.offerguardian;

import java.util.Locale;
/** Conservative history-only matching; never authorises a decline gesture. */
final class OfferHistoryDedup {
    static final long WINDOW_MS=60000;
    static String canonical(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");}
    static boolean merchantSimilar(String a,String b){
        a=canonical(a);b=canonical(b);
        if(a.length()<6||b.length()<6)return false;
        if(a.equals(b))return true;
        String shortName=a.length()<=b.length()?a:b,longName=a.length()<=b.length()?b:a;
        if(shortName.length()>=10&&longName.length()-shortName.length()<=3&&longName.contains(shortName))return true;
        if(a.length()<12||b.length()<12||Math.abs(a.length()-b.length())>2)return false;
        int[] prev=new int[b.length()+1];for(int j=0;j<prev.length;j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){int[] row=new int[b.length()+1];row[0]=i;for(int j=1;j<=b.length();j++)row[j]=Math.min(Math.min(prev[j]+1,row[j-1]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=row;}
        return prev[b.length()]<=2;
    }
    static boolean same(long age,String typeA,String typeB,int centsA,int centsB,int minsA,int minsB,int metresA,int metresB,int countA,int countB,String pickupA,String pickupB,String destA,String destB){
        String a=canonical(destA),b=canonical(destB);
        return age>=0&&age<=WINDOW_MS&&typeA!=null&&typeB!=null&&typeA.equals(typeB)&&centsA==centsB&&minsA>0&&minsA==minsB&&metresA>0&&metresA==metresB&&countA>0&&countA==countB&&a.length()>=8&&a.equals(b)&&merchantSimilar(pickupA,pickupB);
    }
}
