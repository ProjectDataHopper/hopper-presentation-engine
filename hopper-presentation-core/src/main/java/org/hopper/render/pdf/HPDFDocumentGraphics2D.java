package org.hopper.render.pdf;

import org.apache.fop.svg.PDFDocumentGraphics2D;

import java.io.IOException;

public class HPDFDocumentGraphics2D extends PDFDocumentGraphics2D {
  public HPDFDocumentGraphics2D(boolean textAsShapes) {
    super(textAsShapes);
  }

  public void preparePainting() {
    super.preparePainting();
  }

  public void closePage() {
    super.closePage();
  }

  public void startPage() throws IOException {
    super.startPage();
  }
}
