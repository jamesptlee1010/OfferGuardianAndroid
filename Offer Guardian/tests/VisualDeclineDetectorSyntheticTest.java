package com.personal.offerguardian;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Offline fake screenshots for CI; user's actual map screenshots are never bundled. */
public final class VisualDeclineDetectorSyntheticTest {
    static int[] pixels(BufferedImage b) {
        return b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth());
    }
    public static void main(String[] args) throws Exception {
        BufferedImage image=ImageIO.read(new File(args[0]));
        int[] ref=pixels(image);
        BufferedImage fake=new BufferedImage(686,1536,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=fake.createGraphics();
        g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,686,1536);
        g.setColor(new java.awt.Color(33,138,82));g.fillRect(60,663,540,2);
        g.drawImage(image,559,717,null);g.dispose();
        VisualDeclineDetector.Match match=VisualDeclineDetector.locate(pixels(fake),686,1536,ref,41,41);
        if (!match.found || Math.abs(match.x-579)>1 || Math.abs(match.y-737)>1)
            throw new AssertionError("Synthetic X expected at (579,737) but was "+match.reason);
        // Black top border is used on Match cards.
        BufferedImage matchCard=new BufferedImage(686,1536,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D b=matchCard.createGraphics();
        b.setColor(java.awt.Color.WHITE);b.fillRect(0,0,686,1536);
        b.setColor(new java.awt.Color(18,18,18));b.fillRect(60,678,540,2);
        b.drawImage(image,559,732,null);b.dispose();
        VisualDeclineDetector.Match black=VisualDeclineDetector.locate(pixels(matchCard),686,1536,ref,41,41);
        if(!black.found || Math.abs(black.x-579)>1 || Math.abs(black.y-752)>1)
            throw new AssertionError("Synthetic black-border Match card failed: "+black.reason);
        fake.setRGB(579,737,0xFFFFFF);
        // An unrelated screen, even with the same gray X but no green border, must not match.
        BufferedImage unrelated=new BufferedImage(686,1536,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D u=unrelated.createGraphics();
        u.setColor(java.awt.Color.WHITE);u.fillRect(0,0,686,1536);
        u.drawImage(image,559,717,null);u.dispose();
        if (VisualDeclineDetector.locate(pixels(unrelated),686,1536,ref,41,41).found)
            throw new AssertionError("X without Uber green or black border MUST NOT match");
        System.out.println("Visual X detector synthetic positive/negative tests PASSED");
    }
}
