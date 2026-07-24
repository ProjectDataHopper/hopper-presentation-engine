package org.hopper.render.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
