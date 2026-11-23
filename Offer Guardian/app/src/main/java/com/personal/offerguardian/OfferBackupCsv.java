package com.personal.offerguardian;
import java.io.*;
import java.util.*;
import java.math.BigDecimal;
import java.text.*;
final class OfferBackupCsv {
    static List<String> readRow(PushbackReader in) throws IOException {
        List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false, closed=false, any=false;int c;
        while((c=in.read())!=-1){
            any=true;
            if(quoted){if(c=='"'){int next=in.read();if(next=='"')cell.append('"');else{quoted=false;closed=true;if(next!=-1)in.unread(next);}}else cell.append((char)c);}
            else if(c==','||c=='\n'||c=='\r'){row.add(cell.toString());cell.setLength(0);closed=false;if(c!=','){if(c=='\r'){int next=in.read();if(next!=-1&&next!='\n')in.unread(next);}return row;}}
            else if(c=='"'&&cell.length()==0&&!closed)quoted=true;
            else {if(closed||c=='"')throw new IOException("Invalid CSV quoting");cell.append((char)c);}
            if(cell.length()>1000000||row.size()>100)throw new IOException("CSV field too large");
        }
        if(quoted)throw new IOException("Unclosed CSV field");if(!any)return null;row.add(cell.toString());return row;
    }
    static Map<String,String> map(List<String> header,List<String> row)throws IOException{
        if(header.size()!=row.size())throw new IOException("Wrong number of CSV fields");Map<String,String> m=new HashMap<>();for(int i=0;i<header.size();i++)m.put(header.get(i),row.get(i));return m;
    }
    static String value(Map<String,String> m,String key,String fallback){String s=m.get(key);return s==null||s.isEmpty()?fallback:s;}
    static String original(String s){return s.length()>1&&s.charAt(0)=='\''&&"=+-@".indexOf(s.charAt(1))>=0?s.substring(1):s;}
    static int integer(Map<String,String> m,String k,int fallback){String s=value(m,k,"");return s.isEmpty()||s.equalsIgnoreCase("UNKNOWN")?fallback:Integer.parseInt(s);}
    static int scaled(String s,int factor){return s.isEmpty()?0:new BigDecimal(s).multiply(BigDecimal.valueOf(factor)).intValueExact();}
    static long timestamp(Map<String,String> m)throws ParseException{
        if(m.containsKey("Offer time epoch ms"))return Long.parseLong(m.get("Offer time epoch ms"));
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);f.setLenient(false);String t=value(m,"Offer time","");ParsePosition pos=new ParsePosition(0);Date d=f.parse(t,pos);if(d==null||pos.getIndex()!=t.length())throw new ParseException("Invalid offer time",0);return d.getTime();
    }
}
