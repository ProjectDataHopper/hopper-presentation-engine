package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HWebSvgDocumentTest {

  @Test
  void htmlHostsSvgAndPointerScript() {
    String html = HWebSvgDocument.html("<svg xmlns='http://www.w3.org/2000/svg'></svg>", 0.5f);
    assertTrue(html.contains("background:#e6e6e6"));
    assertTrue(html.contains("id='page'"));
    assertTrue(html.contains("replaceSvg"));
    assertTrue(html.contains("hopPointer"));
    assertTrue(html.contains("<svg"));
    assertTrue(html.contains("setZoom(0.5"));
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
  void htmlUsesProvidedBackground() {
    String html =
        HWebSvgDocument.html("<svg xmlns='http://www.w3.org/2000/svg'></svg>", 1f, "#3c3f41");
    assertTrue(html.contains("background:#3c3f41"));
  }

  @Test
  void nullSvgIsEmptyDocument() {
    String html = HWebSvgDocument.html(null, 1f);
    assertTrue(html.contains("id='page'"));
    assertTrue(HWebSvgDocument.replaceSvgScript(null, 1f).contains("replaceSvg(\"\""));
  }
}
