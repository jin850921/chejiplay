import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Launcher tile for 車機Play: play triangle over a road arc on a dark blue rounded square. */
public class MakeIcon {

    /** Glyph lives on a 108 grid, vertically centred: triangle y23-57, road arc centred y85. */
    static final double[] TRI = {46, 23, 72, 41, 46, 57};
    static final double[] ARC = {26, 67, 56, 36};

    public static void main(String[] args) throws Exception {
        String res = args[0];
        int[] sizes = {48, 72, 96, 144, 192};
        String[] dir = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int i = 0; i < sizes.length; i++) {
            save(render(sizes[i], true, 1.0), res + "/mipmap-" + dir[i] + "/ic_launcher.png");
        }
        save(render(432, false, 0.62), res + "/mipmap-xxxhdpi/ic_launcher_foreground.png");
    }

    static BufferedImage render(int outPx, boolean tile, double glyphScale) {
        int ss = outPx * 4;
        BufferedImage img = new BufferedImage(ss, ss, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double k = ss / 108.0;
        g.scale(k, k);
        if (tile) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x2E, 0x4C, 0x6B), 108, 108,
                    new Color(0x10, 0x1A, 0x24)));
            g.fill(new RoundRectangle2D.Double(0, 0, 107, 107, 24, 24));
        }
        double s = glyphScale;
        g.translate(54 * (1 - s), 54 * (1 - s));
        g.scale(s, s);
        g.setColor(new Color(255, 255, 255, 246));
        GeneralPath tri = new GeneralPath();
        tri.moveTo(TRI[0], TRI[1]);
        tri.lineTo(TRI[2], TRI[3]);
        tri.lineTo(TRI[4], TRI[5]);
        tri.closePath();
        g.fill(tri);
        g.setStroke(new BasicStroke(7.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(ARC[0], ARC[1], ARC[2], ARC[3], 0, 180, Arc2D.OPEN));
        g.dispose();

        BufferedImage small = new BufferedImage(outPx, outPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = small.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(img, 0, 0, outPx, outPx, null);
        g2.dispose();
        return small;
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        if (!ImageIO.write(img, "png", f)) {
            throw new IllegalStateException("PNG writer missing for " + path);
        }
        System.out.println(f.getAbsolutePath() + "  " + img.getWidth() + "px");
    }
}
