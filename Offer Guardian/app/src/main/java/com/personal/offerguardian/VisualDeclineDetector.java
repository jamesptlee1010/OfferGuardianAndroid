package com.personal.offerguardian;

/** Fail-closed screenshot X detector. Input is normalized to 686 x 1536. */
public final class VisualDeclineDetector {
    public static final int WIDTH = 686, HEIGHT = 1536;
    private static final int TEMPLATE_SIZE = 41;
    private VisualDeclineDetector() { }
    public static final class Match {
        public final boolean found;
        public final int x, y;
        public final double confidence;
        public final String reason;
        Match(boolean found, int x, int y, double confidence, String reason) {
            this.found = found; this.x = x; this.y = y;
            this.confidence = confidence; this.reason = reason;
        }
    }
    private static boolean green(int p) {
        int r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255;
        return r < 100 && g >= 95 && g <= 192 && b < 145 && g > r + 38 && g > b + 24;
    }
    private static int gray(int p) {
        int r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255;
        return (r * 77 + g * 150 + b * 29) >>> 8;
    }
    public static Match locate(int[] pixels, int width, int height, int[] reference,
                               int refWidth, int refHeight) {
        if (pixels == null || reference == null || width != WIDTH || height != HEIGHT ||
            pixels.length < width * height || refWidth != TEMPLATE_SIZE ||
            refHeight != TEMPLATE_SIZE || reference.length < TEMPLATE_SIZE * TEMPLATE_SIZE)
            return new Match(false, -1, -1, 0, "Wrong image dimensions");
        double meanT = 0;
        for (int p : reference) meanT += gray(p);
        meanT /= reference.length;
        double varT = 0;
        for (int p : reference) { double d = gray(p) - meanT; varT += d*d; }
        if (varT < 10000) return new Match(false,-1,-1,0,"Reference X is blank");
        double best = -1;
        int bestX = -1, bestY = -1;
        int lastBorder = -20;
        int[] anchors = {95,195,330,490};
        for (int top = 325; top < 1270; top++) {
            int greens = 0;
            for (int x : anchors) if (green(pixels[top*width+x])) greens++;
            if (greens != 4 || top-lastBorder < 8) continue;
            lastBorder = top;
            // A real offer's X is roughly 74 px below the green card border.
            for (int y = top+65; y <= top+84; y++) {
                if (y+21 >= height) continue;
                for (int x = 558; x <= 598; x++) {
                    if (gray(pixels[y*width+x]) > 145) continue;
                    if (gray(pixels[(y-18)*width+x+18]) < 210) continue;
                    double corr = correlation(pixels,width,reference,x,y,meanT,varT);
                    if (corr > best) { best = corr; bestX=x; bestY=y; }
                }
            }
        }
        if (best < 0.90)
            return new Match(false,-1,-1,Math.max(0,best),"X / card match below confidence limit");
        return new Match(true,bestX,bestY,best,"X and green offer-card border agree");
    }
    private static double correlation(int[] pixels, int width, int[] template,
                                      int cx,int cy,double tMean,double tVar) {
        int n = TEMPLATE_SIZE*TEMPLATE_SIZE;
        double sum=0, sq=0, cross=0;
        int i=0;
        for (int dy=-20;dy<=20;dy++) {
            int off=(cy+dy)*width+cx-20;
            for (int dx=0;dx<TEMPLATE_SIZE;dx++) {
                double lum=gray(pixels[off+dx]);
                sum+=lum; sq+=lum*lum;
                cross+=lum*(gray(template[i++])-tMean);
            }
        }
        double var=sq-sum*sum/n;
        return var < 100 ? -1 : cross/Math.sqrt(var*tVar);
    }
}
