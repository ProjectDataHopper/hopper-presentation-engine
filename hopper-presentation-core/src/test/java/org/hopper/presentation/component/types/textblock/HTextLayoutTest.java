package org.hopper.presentation.component.types.textblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HTextLayoutTest {

  private FontMetrics fm;

  @BeforeEach
  void setUp() {
    BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    fm = g.getFontMetrics();
    g.dispose();
  }

  @Test
  void hardNewlines_noWrap() {
    HTextLayout.Result r =
        HTextLayout.layout("one\ntwo\nthree", fm, Integer.MAX_VALUE, false, 1.0f, 0, 0);
    assertEquals(3, r.getLines().size());
    assertEquals("one", r.getLines().get(0).getText());
    assertEquals("two", r.getLines().get(1).getText());
    assertEquals("three", r.getLines().get(2).getText());
    assertEquals(3 * r.getLineHeight(), r.getHeight());
    int expectedWidth =
        Math.max(
            fm.stringWidth("one"),
            Math.max(fm.stringWidth("two"), fm.stringWidth("three")));
    assertEquals(expectedWidth, r.getWidth());
  }

  @Test
  void emptyLinesPreserved() {
    HTextLayout.Result r =
        HTextLayout.layout("a\n\nb", fm, Integer.MAX_VALUE, false, 1.0f, 0, 0);
    assertEquals(3, r.getLines().size());
    assertEquals("", r.getLines().get(1).getText());
  }

  @Test
  void softWrap_atWordBoundaries() {
    String text = "hello world again friends";
    int maxWidth = fm.stringWidth("hello world") + 2; // enough for two short words, not all
    HTextLayout.Result r = HTextLayout.layout(text, fm, maxWidth, true, 1.0f, 0, 0);
    assertTrue(r.getLines().size() > 1, "expected soft wrap, lines=" + r.getLines().size());
    for (HTextLayout.Line line : r.getLines()) {
      assertTrue(
          line.getWidth() <= maxWidth,
          "line wider than max: '" + line.getText() + "' w=" + line.getWidth());
    }
    // All words present in order when joined
    String joined =
        r.getLines().stream()
            .map(HTextLayout.Line::getText)
            .reduce((a, b) -> a + " " + b)
            .orElse("");
    assertEquals(text, joined.replaceAll(" +", " ").trim());
  }

  @Test
  void softWrap_midWordWhenTokenTooLong() {
    String token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    int maxWidth = Math.max(1, fm.stringWidth("ABCDE"));
    HTextLayout.Result r = HTextLayout.layout(token, fm, maxWidth, true, 1.0f, 0, 0);
    assertTrue(r.getLines().size() > 1);
    String rebuilt =
        r.getLines().stream().map(HTextLayout.Line::getText).reduce("", String::concat);
    assertEquals(token, rebuilt);
    for (HTextLayout.Line line : r.getLines()) {
      assertTrue(line.getWidth() <= maxWidth || line.getText().length() == 1);
    }
  }

  @Test
  void margins_increaseBoxSize() {
    HTextLayout.Result bare =
        HTextLayout.layout("hi", fm, Integer.MAX_VALUE, false, 1.0f, 0, 0);
    HTextLayout.Result padded =
        HTextLayout.layout("hi", fm, Integer.MAX_VALUE, false, 1.0f, 4, 6);
    assertEquals(bare.getWidth() + 8, padded.getWidth());
    assertEquals(bare.getHeight() + 12, padded.getHeight());
  }

  @Test
  void lineSpacing_scalesHeight() {
    HTextLayout.Result normal =
        HTextLayout.layout("a\nb", fm, Integer.MAX_VALUE, false, 1.0f, 0, 0);
    HTextLayout.Result spaced =
        HTextLayout.layout("a\nb", fm, Integer.MAX_VALUE, false, 2.0f, 0, 0);
    assertEquals(normal.getLineHeight() * 2, spaced.getLineHeight());
    assertEquals(normal.getHeight() * 2, spaced.getHeight());
  }

  @Test
  void nullText_singleEmptyLine() {
    HTextLayout.Result r =
        HTextLayout.layout(null, fm, Integer.MAX_VALUE, true, 1.0f, 0, 0);
    assertEquals(1, r.getLines().size());
    assertEquals("", r.getLines().get(0).getText());
  }

  @Test
  void crlf_normalized() {
    HTextLayout.Result r =
        HTextLayout.layout("a\r\nb\rc", fm, Integer.MAX_VALUE, false, 1.0f, 0, 0);
    assertEquals(3, r.getLines().size());
    assertEquals("a", r.getLines().get(0).getText());
    assertEquals("b", r.getLines().get(1).getText());
    assertEquals("c", r.getLines().get(2).getText());
  }
}
