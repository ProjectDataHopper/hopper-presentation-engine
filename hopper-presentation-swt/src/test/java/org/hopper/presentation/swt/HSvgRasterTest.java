package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import org.eclipse.swt.graphics.ImageData;
import org.junit.jupiter.api.Test;

class HSvgRasterTest {

  @Test
  void toImageDataCopiesPixelsAndAlphaByRow() {
    BufferedImage bitmap = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
    bitmap.setRGB(0, 0, 0x80FF0000);
    bitmap.setRGB(1, 0, 0xFF00FF00);

    ImageData data = HSvgRaster.toImageData(bitmap);

    assertEquals(2, data.width);
    assertEquals(1, data.height);
    assertEquals(0xFF0000, data.getPixel(0, 0));
    assertEquals(0x80, data.getAlpha(0, 0));
    assertEquals(0x00FF00, data.getPixel(1, 0));
    assertEquals(0xFF, data.getAlpha(1, 0));
  }
}
