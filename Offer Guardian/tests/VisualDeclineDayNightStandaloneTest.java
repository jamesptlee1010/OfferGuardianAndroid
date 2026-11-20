package com.personal.offerguardian;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.File;
import javax.imageio.ImageIO;

/** Synthetic day/night/Waze offer-card tests. User screenshots are not bundled. */
public final class VisualDeclineDayNightStandaloneTest {
    private static final int W=686,H=1536;
    static int[] pixels(BufferedImage img) {
        return img.getRGB(0,0,img.getWidth(),img.getHeight(),null,0,img.getWidth());
    }
    private static void check(BufferedImage refLight,BufferedImage refDark,
                              Color background,Color border,BufferedImage target,
                              int top,VisualDeclineDetector.Theme expected,String name) {
        BufferedImage screen=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=screen.createGraphics();
        g.setColor(background);g.fillRect(0,0,W,H);
        g.setColor(border);g.fillRect(55,top,560,3);
        g.drawImage(target,559,top+74-20,null);g.dispose();
        VisualDeclineDetector.Match m=VisualDeclineDetector.locate(pixels(screen),W,H,
                pixels(refLight),pixels(refDark),41,41);
        if(!m.found||m.theme!=expected||Math.abs(m.x-579)>1||Math.abs(m.y-top-74)>1)
            throw new AssertionError(name+" match wrong: "+m.reason+" "+m.theme+" "+m.x+","+m.y);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("light.png dark.png required");
        BufferedImage light=ImageIO.read(new File(args[0]));
        BufferedImage dark=ImageIO.read(new File(args[1]));
        check(light,dark,Color.WHITE,new Color(42,135,78),light,663,
                VisualDeclineDetector.Theme.LIGHT,"normal light offer");
        check(light,dark,Color.WHITE,Color.BLACK,light,678,
                VisualDeclineDetector.Theme.LIGHT,"Match card");
        check(light,dark,new Color(28,28,28),new Color(43,132,85),dark,623,
                VisualDeclineDetector.Theme.DARK,"dark Uber offer");
        check(light,dark,new Color(28,28,28),new Color(215,215,215),dark,527,
                VisualDeclineDetector.Theme.DARK,"dark Match white-outline offer");
        check(light,dark,Color.WHITE,new Color(43,132,85),light,584,
                VisualDeclineDetector.Theme.LIGHT,"Waze light offer, higher card");
        // A random screen, or a navigation banner with a copied X but without
        // a real offer-card border, must NOT be acted on.
        BufferedImage navigation=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=navigation.createGraphics();g.setColor(new Color(25,25,27));g.fillRect(0,0,W,H);
        g.setColor(Color.WHITE);g.fillRect(0,0,W,300);g.drawImage(dark,559,640,null);g.dispose();
        VisualDeclineDetector.Match bad=VisualDeclineDetector.locate(pixels(navigation),W,H,
                pixels(light),pixels(dark),41,41);
        if(bad.found)throw new AssertionError("X on unrelated navigation screen must not match");
        System.out.println("Visual X day/night + Waze synthetic regression tests PASS");
    }
}
