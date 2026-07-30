package org.hopper.render.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HSvgToPngTest {

  private static final String SVG =
      "<?xml version=\"1.0\"?>\n"
          + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\">"
          + "<rect x=\"0\" y=\"0\" width=\"200\" height=\"100\" fill=\"white\"/>"
          + "</svg>";

  /** Black text on white — used to detect greyscale anti-aliased glyph edges. */
  private static final String SVG_WITH_TEXT =
      "<?xml version=\"1.0\"?>\n"
          + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"240\" height=\"80\">"
          + "<rect x=\"0\" y=\"0\" width=\"240\" height=\"80\" fill=\"white\"/>"
          + "<text x=\"12\" y=\"48\" font-family=\"SansSerif, sans-serif\" font-size=\"36\""
          + " fill=\"black\">AaMm</text>"
          + "</svg>";

  @Test
  void parseSvgUserSizeFromWidthHeight() {
    float[] size = HSvgToPng.parseSvgUserSize(SVG);
    assertNotNull(size);
    assertEquals(200f, size[0], 0.01f);
    assertEquals(100f, size[1], 0.01f);
  }

  @Test
  void pixelScaleDoublesOutputDimensions() throws Exception {
    byte[] one = HSvgToPng.toPngBytes(SVG, 1f);
    byte[] two = HSvgToPng.toPngBytes(SVG, 2f);
    BufferedImage a = ImageIO.read(new ByteArrayInputStream(one));
    BufferedImage b = ImageIO.read(new ByteArrayInputStream(two));
    assertEquals(200, a.getWidth());
    assertEquals(100, a.getHeight());
    assertEquals(400, b.getWidth());
    assertEquals(200, b.getHeight());
  }

  @Test
  void qualityTextHintsEnableTextAntialiasAndFractionalMetrics() {
    RenderingHints hints = HSvgRenderHints.qualityTextHints();
    assertEquals(
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        hints.get(RenderingHints.KEY_TEXT_ANTIALIASING));
    assertEquals(
        RenderingHints.VALUE_FRACTIONALMETRICS_ON,
        hints.get(RenderingHints.KEY_FRACTIONALMETRICS));
    assertEquals(
        RenderingHints.VALUE_ANTIALIAS_ON, hints.get(RenderingHints.KEY_ANTIALIASING));
  }

  /**
   * Soft-reload path should produce greyscale-smoothed glyph edges (not pure binary black/white).
   * Intermediate luminances near the letters indicate text anti-aliasing ran.
   */
  @Test
  void textRasterHasAntialiasedEdges() throws Exception {
    byte[] png = HSvgToPng.toPngBytes(SVG_WITH_TEXT, 2f);
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
    assertNotNull(img);
    assertEquals(480, img.getWidth());
    assertEquals(160, img.getHeight());

    int intermediate = 0;
    int dark = 0;
    int light = 0;
    for (int y = 0; y < img.getHeight(); y++) {
      for (int x = 0; x < img.getWidth(); x++) {
        int argb = img.getRGB(x, y);
        int a = (argb >>> 24) & 0xff;
        if (a < 8) {
          continue;
        }
        int r = (argb >>> 16) & 0xff;
        int g = (argb >>> 8) & 0xff;
        int b = argb & 0xff;
        // Luminance-ish; white bg and black ink with greyscale AA edges.
        int lum = (r + g + b) / 3;
        if (lum <= 40) {
          dark++;
        } else if (lum >= 220) {
          light++;
        } else {
          intermediate++;
        }
      }
    }
    assertTrue(dark > 50, "expected solid ink pixels inside glyphs, dark=" + dark);
    assertTrue(light > 1000, "expected white background, light=" + light);
    assertTrue(
        intermediate > 20,
        "expected greyscale anti-aliased edge samples, intermediate=" + intermediate);
  }
}
