package com.personal.offerguardian;

/**
 * Screenshot-only recognition of Uber's X in a validated offer card.
 * Supports the white/green, white/black and night-mode dark/green cards,
 * including when the offer appears above Waze navigation. No fixed tap position.
 * Inputs are normalised to the same 686x1536 used by the OCR geometry.
 */
public final class VisualDeclineDetector {
    public static final int WIDTH=686, HEIGHT=1536;
    private static final int SIZE=41;
    public enum Theme { LIGHT, DARK }
    private VisualDeclineDetector() {}

    public static final class Match {
        public final boolean found;
        public final int x,y;
        public final double confidence;
        public final String reason;
        public final Theme theme;
        public final int cardTop;
        Match(boolean found,int x,int y,double confidence,String reason) {
            this(found,x,y,confidence,reason,Theme.LIGHT,-1);
        }
        Match(boolean found,int x,int y,double confidence,String reason,Theme theme,int cardTop) {
            this.found=found;this.x=x;this.y=y;this.confidence=confidence;
            this.reason=reason;this.theme=theme;this.cardTop=cardTop;
        }
    }
    private static int red(int p) {return (p>>>16)&255;}
    private static int greenChannel(int p) {return (p>>>8)&255;}
    private static int blue(int p) {return p&255;}
    private static boolean green(int p) {
        int r=red(p),g=greenChannel(p),b=blue(p);
        return r<115 && g>=94 && g<=200 && b<150 && g>r+33 && g>b+22;
    }
    private static boolean grayBorder(int p,boolean bright) {
        int r=red(p),g=greenChannel(p),b=blue(p);
        return (bright? Math.min(r,Math.min(g,b))>172 : Math.max(r,Math.max(g,b))<86) &&
                Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<45;
    }
    private static int gray(int p) {
        return (red(p)*77 + greenChannel(p)*150 + blue(p)*29)>>>8;
    }
    private static final int[] ANCHORS={95,195,330,490};

    /** Legacy public overload retained for existing integrations and tests. */
    public static Match locate(int[] pixels,int width,int height,int[] light,int tw,int th) {
        return locate(pixels,width,height,light,null,tw,th);
    }

    /** Auto-detects the actual card theme from pixels; no manual mode switch. */
    public static Match locate(int[] pixels,int width,int height,int[] light,int[] dark,int tw,int th) {
        if(pixels==null||light==null||width!=WIDTH||height!=HEIGHT||
                pixels.length<width*height||tw!=SIZE||th!=SIZE||light.length<SIZE*SIZE||
                (dark!=null&&dark.length<SIZE*SIZE))
            return new Match(false,-1,-1,0,"Wrong image or X template dimensions");
        Template a=new Template(light),b=dark==null?null:new Template(dark);
        double best=-1;int cx=-1,cy=-1,bestTop=-1;Theme chosen=Theme.LIGHT;
        int previousTop=-20;
        for(int top=325;top<1270;top++) {
            int g=0,black=0,white=0;
            for(int x:ANCHORS) {
                int p=pixels[top*width+x];
                if(green(p))g++;
                if(grayBorder(p,false))black++;
                if(grayBorder(p,true))white++;
            }
            if(g<4&&black<4&&white<4)continue;
            // Uniform black navigation bars and white map backgrounds are not
            // candidate card borders. Demand a real transition beneath them.
            if(g<4) {
                int changed=0;
                for(int x:ANCHORS) {
                    int delta=Math.abs(gray(pixels[top*width+x])-gray(pixels[(top+16)*width+x]));
                    if(delta>62)changed++;
                }
                if(changed<3)continue;
            }
            if(top-previousTop<5)continue;
            previousTop=top;
            // The X's visual centre is about 74 px below the thin top border.
            boolean isDark=gray(pixels[(top+95)*width+490])<125;
            Template primary=isDark&&b!=null?b:a;
            Template fallback=isDark?a:b;
            for(int y=top+66;y<=top+84;y++) {
                if(y+21>=height)break;
                for(int x=558;x<=598;x++) {
                    int centre=gray(pixels[y*width+x]);
                    // Dark-X-on-light and light-X-on-dark require different templates.
                    if(Math.abs(centre-primary.centre)>93) {
                        if(fallback==null||Math.abs(centre-fallback.centre)>93)continue;
                    }
                    double score=score(pixels,width,primary,x,y);
                    Theme theme=isDark?Theme.DARK:Theme.LIGHT;
                    if(fallback!=null && score<.90) {
                        double alternate=score(pixels,width,fallback,x,y);
                        if(alternate>score) {score=alternate;theme=isDark?Theme.LIGHT:Theme.DARK;}
                    }
                    if(score>best) {best=score;cx=x;cy=y;bestTop=top;chosen=theme;}
                }
            }
        }
        if(best<.90) return new Match(false,-1,-1,Math.max(0,best),
                "No matching X plus actual offer-card border");
        return new Match(true,cx,cy,best,"X and card border verified",chosen,bestTop);
    }

    private static final class Template {
        final int[] pixels;
        final double mean,variance;
        final int centre;
        Template(int[] pixels) {
            this.pixels=pixels;this.centre=gray(pixels[20*SIZE+20]);
            double m=0,v=0;
            for(int p:pixels)m+=gray(p);
            m/=pixels.length;
            for(int p:pixels) {double d=gray(p)-m;v+=d*d;}
            mean=m;variance=v;
        }
    }
    private static double score(int[] screen,int width,Template t,int x,int y) {
        if(t.variance<10000)return -1;
        int n=SIZE*SIZE,i=0;double sum=0,sq=0,cross=0;
        for(int dy=-20;dy<=20;dy++) {
            int off=(y+dy)*width+x-20;
            for(int dx=0;dx<SIZE;dx++) {
                double lum=gray(screen[off+dx]);
                sum+=lum;sq+=lum*lum;
                cross+=lum*(gray(t.pixels[i++])-t.mean);
            }
        }
        double v=sq-sum*sum/n;
        return v<100?-1:cross/Math.sqrt(v*t.variance);
    }
}
