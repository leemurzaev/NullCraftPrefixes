import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draws the NullCraft rank badges at twice Minecraft's GUI resolution (18 px tall for a 9 px
 * line), so they are pixel-exact at GUI scale 2 and 4 and still clean at 3. Run with:
 *   java tools/BadgeArt.java tools/badges
 * It writes badge_<group>.png for every rank plus preview.png (all badges, enlarged).
 */
public final class BadgeArt {

    private static final int H = 18;
    private static final int PAD = 6;

    private record Badge(String group, String label, Color bg, Color border, Color text) {
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r, g, b);
    }

    private static final Color GOLD = rgb(255, 215, 64);
    private static final Color WHITE = rgb(255, 255, 255);

    private static List<Badge> badges() {
        List<Badge> list = new java.util.ArrayList<>(List.of(
                new Badge("godtier1", "GOD I", rgb(150, 115, 10), GOLD, WHITE),
                new Badge("godtier2", "GOD II", rgb(190, 100, 15), rgb(255, 170, 60), WHITE),
                new Badge("godtier3", "GOD III", rgb(160, 25, 25), rgb(255, 90, 90), WHITE),
                new Badge("godtier4", "GOD IV", rgb(100, 35, 140), rgb(200, 110, 255), WHITE),
                new Badge("godtier5", "GOD V", rgb(55, 0, 0), GOLD, GOLD)));
        Object[][] staff = {
                {"helper", rgb(70, 62, 8), rgb(230, 200, 40), rgb(255, 236, 120)},
                {"mod", rgb(16, 40, 96), rgb(80, 140, 255), rgb(200, 220, 255)},
                {"admin", rgb(110, 16, 16), rgb(255, 80, 80), rgb(255, 205, 205)},
        };
        for (Object[] s : staff) {
            String role = (String) s[0];
            String name = role.toUpperCase();
            Color bg = (Color) s[1];
            Color border = (Color) s[2];
            Color text = (Color) s[3];
            list.add(new Badge("trial-" + role, "TRIAL " + name, bg, border, text));
            list.add(new Badge(role, name, bg, border, text));
            list.add(new Badge("senior-" + role, "SR " + name, bg, border, text));
            list.add(new Badge("head-" + role, "HEAD " + name, bg, border, text));
        }
        list.add(new Badge("owner", "OWNER", rgb(150, 0, 0), rgb(255, 60, 60), WHITE));
        return list;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "tools/badges");
        out.mkdirs();
        Font font = pickFont();
        List<Badge> all = badges();
        List<BufferedImage> images = new java.util.ArrayList<>();
        for (Badge badge : all) {
            BufferedImage image = draw(badge, font);
            ImageIO.write(image, "png", new File(out, "badge_" + badge.group() + ".png"));
            images.add(image);
            System.out.println(badge.group() + "\t" + image.getWidth() + "x" + image.getHeight());
        }
        ImageIO.write(preview(images), "png", new File(out, "preview.png"));
        System.out.println("font: " + font.getFontName());
    }

    /** A heavy, narrow face reads best at 11 px; fall back through other Windows bold fonts. */
    private static Font pickFont() {
        for (String file : new String[] {"C:/Windows/Fonts/impact.ttf", "C:/Windows/Fonts/ariblk.ttf", "C:/Windows/Fonts/arialbd.ttf"}) {
            File f = new File(file);
            if (f.exists()) {
                try {
                    return Font.createFont(Font.TRUETYPE_FONT, f);
                } catch (Exception ignored) {
                    // try the next one
                }
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 12);
    }

    private static BufferedImage draw(Badge badge, Font base) {
        Font font = base.deriveFont(Map.of(TextAttribute.SIZE, 13.5f, TextAttribute.KERNING, TextAttribute.KERNING_ON));
        FontRenderContext frc = new FontRenderContext(null, true, true);
        TextLayout layout = new TextLayout(badge.label(), font, frc);
        int textW = (int) Math.ceil(layout.getAdvance());
        int w = textW + PAD * 2;

        BufferedImage image = new BufferedImage(w, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Body: a vertical gradient from a lighter top to a darker bottom.
        RoundRectangle2D body = new RoundRectangle2D.Float(0, 0, w, H, 7, 7);
        g.setPaint(new GradientPaint(0, 0, shade(badge.bg(), 1.45f), 0, H, shade(badge.bg(), 0.75f)));
        g.fill(body);

        // Gloss on the upper half.
        g.setClip(body);
        g.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 70), 0, H / 2f, new Color(255, 255, 255, 8)));
        g.fillRect(0, 0, w, H / 2);
        g.setClip(null);

        // Border, one GUI pixel wide (two image pixels), with a brighter top edge.
        g.setStroke(new BasicStroke(2f));
        g.setPaint(new GradientPaint(0, 0, shade(badge.border(), 1.2f), 0, H, shade(badge.border(), 0.8f)));
        g.draw(new RoundRectangle2D.Float(1, 1, w - 2, H - 2, 6, 6));

        // Text, centred on the cap height, with a one-pixel dark shadow.
        float capTop = (float) -layout.getBounds().getY();
        float baseline = (H + capTop) / 2f;
        float x = (w - textW) / 2f;
        g.setColor(shade(badge.bg(), 0.35f));
        layout.draw(g, x + 1, baseline + 1);
        g.setColor(badge.text());
        layout.draw(g, x, baseline);
        g.dispose();
        return image;
    }

    private static Color shade(Color c, float f) {
        return new Color(Math.min(255, Math.round(c.getRed() * f)), Math.min(255, Math.round(c.getGreen() * f)),
                Math.min(255, Math.round(c.getBlue() * f)), c.getAlpha());
    }

    /** All badges on a dark and a light strip, enlarged 4x with hard pixels, to check them. */
    private static BufferedImage preview(List<BufferedImage> images) {
        int scale = 4;
        int rowH = (H + 4) * scale;
        int colW = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(1) * scale + 16;
        int rows = (images.size() + 1) / 2;
        BufferedImage out = new BufferedImage(colW * 4, rows * rowH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < images.size(); i++) {
            BufferedImage img = images.get(i);
            int col = (i % 2) * 2;
            int row = i / 2;
            for (int bgIndex = 0; bgIndex < 2; bgIndex++) {
                int x0 = (col + bgIndex) * colW;
                g.setColor(bgIndex == 0 ? new Color(40, 40, 40) : new Color(160, 200, 255));
                g.fillRect(x0, row * rowH, colW, rowH);
                g.drawImage(img, x0 + 8, row * rowH + 2 * scale, img.getWidth() * scale, img.getHeight() * scale, null);
            }
        }
        g.dispose();
        return out;
    }
}
