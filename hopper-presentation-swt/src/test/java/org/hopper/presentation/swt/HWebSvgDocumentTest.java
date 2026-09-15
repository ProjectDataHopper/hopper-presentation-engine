package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HWebSvgDocumentTest {

  @Test
  void htmlHostsSvgAndPointerScript() {
    String html = HWebSvgDocument.html("<svg xmlns='http://www.w3.org/2000/svg'></svg>", 0.5f);
    assertTrue(html.contains("background:#e6e6e6"));
    assertTrue(html.contains("id='page'"));
    assertTrue(html.contains("id='slot'"));
    assertTrue(html.contains("replaceSvg"));
    assertTrue(html.contains("hopPointer"));
    assertTrue(html.contains("downloadFile"));
    assertTrue(html.contains("<svg"));
    assertTrue(html.contains("setZoom(0.5"));
    assertTrue(html.contains("slot.style.width"));
    assertTrue(html.contains("overflow:hidden;width:100%;height:100%"));
    assertTrue(html.contains("max-width:100%;max-height:100%"));
    assertTrue(html.contains("hopZoom=Math.min(hopZoom,widthZ,heightZ)"));
    assertTrue(html.contains("var fit=m==='WIDTH'||m==='HEIGHT'||m==='PAGE'"));
    assertTrue(html.contains("var ov=fit?'hidden'"));
  }

  @Test
  void htmlPassesPageSizeIntoSetZoom() {
    String html =
        HWebSvgDocument.html(
            "<svg xmlns='http://www.w3.org/2000/svg'></svg>", 1.25f, "#e6e6e6", 960, 240);
    assertTrue(html.contains("setZoom(1.25,960,240)"));
  }

  @Test
  void replaceSvgScriptEscapesQuotesAndScriptEnd() {
    String script =
        HWebSvgDocument.replaceSvgScript("<svg id=\"a\"></svg></script>", 1.25f);
    assertTrue(script.startsWith("replaceSvg("));
    assertTrue(script.contains("1.25"));
    assertFalse(script.contains("</script>"));
    assertTrue(script.contains("\\x3c") || script.contains("</scr"));
    assertTrue(HWebSvgDocument.jsString("a\"b").contains("\\\""));
  }

  @Test
  void replaceSvgScriptIncludesPageSize() {
    String script = HWebSvgDocument.replaceSvgScript("<svg/>", 0.5f, 800, 400);
    assertTrue(script.contains("0.5,800,400"));
  }

  @Test
  void htmlAndReplacePassFitMode() {
    String html =
        HWebSvgDocument.html(
            "<svg xmlns='http://www.w3.org/2000/svg'></svg>",
            1.25f,
            "#e6e6e6",
            960,
            240,
            HPresentationZoom.WIDTH);
    assertTrue(html.contains("setZoom(1.25,960,240,\"WIDTH\")"));
    String script =
        HWebSvgDocument.replaceSvgScript("<svg/>", 0.5f, 800, 400, HPresentationZoom.HEIGHT);
    assertTrue(script.contains("0.5,800,400,\"HEIGHT\""));
  }

  @Test
  void htmlUsesProvidedBackground() {
    String html =
        HWebSvgDocument.html("<svg xmlns='http://www.w3.org/2000/svg'></svg>", 1f, "#3c3f41");
    assertTrue(html.contains("background:#3c3f41"));
  }

  @Test
  void downloadScriptCallsDownloadFileWithBase64() {
    String script =
        HWebSvgDocument.downloadScript(
            "chart.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8));
    assertTrue(script.startsWith("downloadFile("));
    assertTrue(script.contains("chart.svg"));
    assertTrue(script.contains("image/svg+xml"));
    assertFalse(script.contains("</script>"));
  }

  @Test
  void nullSvgIsEmptyDocument() {
    String html = HWebSvgDocument.html(null, 1f);
    assertTrue(html.contains("id='page'"));
    assertTrue(HWebSvgDocument.replaceSvgScript(null, 1f).contains("replaceSvg(\"\""));
  }
}
