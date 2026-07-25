package org.hopper.render.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HSvgPdfExporterTest {

  @Test
  void singlePageSvgBecomesPdf() throws Exception {
    String svg =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\">"
            + "<rect x=\"10\" y=\"10\" width=\"50\" height=\"30\" fill=\"#336699\"/>"
            + "</svg>";
    byte[] pdf = HSvgPdfExporter.mergeSvgsToPdf(List.of(svg));
    assertTrue(pdf != null && pdf.length > 100, "expected non-trivial PDF");
    // PDF magic
    assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
  }

  @Test
  void multiPageSvgMerges() throws Exception {
    String page =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">"
            + "<circle cx=\"50\" cy=\"50\" r=\"20\" fill=\"#c00\"/>"
            + "</svg>";
    byte[] pdf = HSvgPdfExporter.mergeSvgsToPdf(List.of(page, page));
    assertTrue(pdf != null && pdf.length > 200);
    assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
  }

  @Test
  void paperPresets() {
    var a4p = HPdfPaper.toPage("a4", true, null, null, 25);
    assertTrue(a4p.getWidth() == 794 && a4p.getHeight() == 1123);
    var a4l = HPdfPaper.toPage("a4", false, null, null, 25);
    assertTrue(a4l.getWidth() == 1123 && a4l.getHeight() == 794);
  }
}
