package com.personal.offerguardian;
/** Two distinct fresh frames on the same unexposed overlay; never trusts a coordinate alone. */
public final class UberVisualFallback {
    private int window=-1,x,y,top,frames; private String key=""; private long capture=-1;
    public void reset(){window=-1;frames=0;key="";capture=-1;}
    public int frames(){return frames;}
    private boolean valid(int id,String host,String kind,String financial,int px,int py,int cardTop,double confidence,boolean identity,long frame,long now){
        return id>=0&&"DOORDASH".equals(host)&&kind!=null&&!kind.isEmpty()&&financial!=null&&!financial.isEmpty()&&px>=0&&py>=0&&cardTop>=0&&confidence>=.94&&identity&&frame>=0&&now>=frame&&now-frame<=1900;
    }
    private String key(String host,String kind,String financial){return host+"|"+kind+"|"+financial;}
    private boolean same(int id,String k,int px,int py,int cardTop){return window==id&&key.equals(k)&&Math.abs(px-x)<=3&&Math.abs(py-y)<=4&&Math.abs(cardTop-top)<=5;}
    public void observe(int id,String host,String kind,String financial,int px,int py,int cardTop,double confidence,boolean identity,long frame,long now){
        if(!valid(id,host,kind,financial,px,py,cardTop,confidence,identity,frame,now)){reset();return;}
        String k=key(host,kind,financial);
        if(frame==capture)return;
        boolean agrees=same(id,k,px,py,cardTop)&&frame>capture&&frame-capture<=4500;
        frames=agrees?Math.min(3,frames+1):1;window=id;key=k;x=px;y=py;top=cardTop;capture=frame;
    }
    public boolean ready(int id,String host,String kind,String financial,int px,int py,int cardTop,double confidence,boolean identity,long frame,long now){
        return frames>=2&&frame==capture&&valid(id,host,kind,financial,px,py,cardTop,confidence,identity,frame,now)&&same(id,key(host,kind,financial),px,py,cardTop);
    }
}
