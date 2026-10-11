package org.hopper.presentation.swt;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.hop.core.SwingUniversalImage;
import org.apache.hop.core.SwingUniversalImageSvg;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.w3c.dom.svg.SVGDocument;

/**
 * Renders one SVG document to an SWT {@link ImageData} and releases the Batik bridge before
 * returning. The performance and Gantt charts call this about once a second; the previous path
 * kept the bridge alive for the life of the bitmap and allocated one {@code RGB} per pixel.
 */
final class HSvgRaster {

  private HSvgRaster() {}

  static ImageData raster(String svgXml, int imageWidth, int imageHeight) throws IOException {
    int width = Math.max(1, imageWidth);
    int height = Math.max(1, imageHeight);
    String parser = XMLResourceDescriptor.getXMLParserClassName();
    SVGDocument document =
        (SVGDocument) new SAXSVGDocumentFactory(parser).createSVGDocument("", new StringReader(svgXml));
    UserAgentAdapter userAgent = new UserAgentAdapter();
    DocumentLoader loader = new DocumentLoader(userAgent);
    BridgeContext context = new BridgeContext(userAgent, loader);
    try {
      GraphicsNode node = new GVTBuilder().build(context, document);
      Dimension2D size = context.getDocumentSize();
      if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) {
        size = new Dimension(width, height);
      }
      BufferedImage bitmap = SwingUniversalImage.createBitmap(width, height);
      Graphics2D graphics = SwingUniversalImage.createGraphics(bitmap);
      try {
        SwingUniversalImageSvg.render(
            graphics, node, size, width / 2, height / 2, width, height, 0d);
      } finally {
        graphics.dispose();
      }
      return toImageData(bitmap);
    } finally {
      context.dispose();
    }
  }

  /** Row copy. {@code ImageData} pixels are palette values; this palette is raw RGB. */
  static ImageData toImageData(BufferedImage bitmap) {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    ImageData data = new ImageData(width, height, 32, new PaletteData(0xFF0000, 0xFF00, 0xFF));
    byte[] alpha = new byte[width * height];
    data.alphaData = alpha;
    int[] pixels = new int[width];
    byte[] rowAlpha = new byte[width];
    for (int y = 0; y < height; y++) {
      bitmap.getRGB(0, y, width, 1, pixels, 0, width);
      for (int x = 0; x < width; x++) {
        int rgba = pixels[x];
        rowAlpha[x] = (byte) ((rgba >>> 24) & 0xFF);
        pixels[x] = rgba & 0x00FFFFFF;
      }
      data.setPixels(0, y, width, pixels, 0);
      System.arraycopy(rowAlpha, 0, alpha, y * width, width);
    }
    return data;
  }
}
