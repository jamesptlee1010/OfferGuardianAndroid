package com.personal.offerguardian;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Host-only regression tests; provide three screenshot paths as arguments. */
public final class VisualDeclineDetectorStandaloneTest {
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
    private static void check(String path, int expectedY, int[] template) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        VisualDeclineDetector.Match match = VisualDeclineDetector.locate(
            pixels(img), img.getWidth(), img.getHeight(), template,41,41);
        System.out.printf("%s: found=%s x=%d y=%d score=%.3f%n",path,match.found,match.x,match.y,match.confidence);
        boolean expected = expectedY >= 0;
        if (match.found != expected || (expected && (Math.abs(match.x-579)>2 || Math.abs(match.y-expectedY)>2)))
            throw new AssertionError("Unexpected visual locator result for "+path);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("reference PNG, 2 real Uber offers, 1 unrelated screen");
        int[] template=pixels(ImageIO.read(new File(args[0])));
        check(args[1],737,template);
        check(args[2],651,template);
        check(args[3],-1,template);
        System.out.println("Visual X locator: all 3 screenshot tests passed");
    }
}
