package org.hopper.presentation.component.types.textblock;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Pure multi-line text measurement and word wrap (hard newlines + optional soft wrap).
 *
 * <p>Uses {@link FontMetrics} only so unit tests can run without a full render pipeline. Soft wrap
 * is greedy at spaces; tokens wider than the content width are broken mid-word.
 */
public final class HTextLayout {

  private HTextLayout() {}

  /** One laid-out visual line. */
  @Getter
  public static final class Line {
    private final String text;
    private final int width;

    public Line(String text, int width) {
      this.text = text == null ? "" : text;
      this.width = Math.max(0, width);
    }
  }

  /** Aggregate box after layout. */
  @Getter
  public static final class Result {
    private final List<Line> lines;
    /** Max line width (content only, excluding horizontal margins). */
    private final int contentWidth;
    /** Full box width: content + horizontal margins. */
    private final int width;
    /** Full box height: lines × lineHeight + vertical margins. */
    private final int height;
    private final int lineHeight;
    private final int ascent;

    public Result(
        List<Line> lines,
        int contentWidth,
        int width,
        int height,
        int lineHeight,
        int ascent) {
      this.lines = lines == null ? List.of() : Collections.unmodifiableList(lines);
      this.contentWidth = Math.max(0, contentWidth);
      this.width = Math.max(0, width);
      this.height = Math.max(0, height);
      this.lineHeight = Math.max(1, lineHeight);
      this.ascent = Math.max(0, ascent);
    }
  }

  /**
   * Layout {@code text} for drawing.
   *
   * @param text source text (may be null)
   * @param fm font metrics for measurement
   * @param maxWidth total available width including horizontal margins; use {@link
   *     Integer#MAX_VALUE} for unconstrained soft wrap
   * @param wrap when true, soft-wrap at word boundaries within the content width
   * @param lineSpacing multiplier on {@link FontMetrics#getHeight()} (minimum 0.5)
   * @param horizontalMargin padding left/right inside the box
   * @param verticalMargin padding top/bottom inside the box
   */
  public static Result layout(
      String text,
      FontMetrics fm,
      int maxWidth,
      boolean wrap,
      float lineSpacing,
      int horizontalMargin,
      int verticalMargin) {
    if (fm == null) {
      throw new IllegalArgumentException("FontMetrics is required");
    }

    int hMargin = Math.max(0, horizontalMargin);
    int vMargin = Math.max(0, verticalMargin);
    float spacing = lineSpacing < 0.5f ? 1.0f : lineSpacing;
    int lineHeight = Math.max(1, Math.round(fm.getHeight() * spacing));
    int ascent = fm.getAscent();

    int boxMax = maxWidth <= 0 ? Integer.MAX_VALUE : maxWidth;
    int contentMax =
        boxMax == Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : Math.max(1, boxMax - 2 * hMargin);

    List<Line> lines = new ArrayList<>();
    String source = text == null ? "" : text;

    // Split on hard breaks; keep empty segments as blank lines
    String[] paragraphs = source.split("\\r\\n|\\n|\\r", -1);
    if (paragraphs.length == 0) {
      paragraphs = new String[] {""};
    }

    for (String paragraph : paragraphs) {
      if (!wrap || contentMax == Integer.MAX_VALUE) {
        int w = measure(fm, paragraph);
        lines.add(new Line(paragraph, w));
      } else {
        wrapParagraph(paragraph, fm, contentMax, lines);
      }
    }

    if (lines.isEmpty()) {
      lines.add(new Line("", 0));
    }

    int contentWidth = 0;
    for (Line line : lines) {
      contentWidth = Math.max(contentWidth, line.getWidth());
    }

    int width = contentWidth + 2 * hMargin;
    int height = lines.size() * lineHeight + 2 * vMargin;
    return new Result(lines, contentWidth, width, height, lineHeight, ascent);
  }

  private static void wrapParagraph(
      String paragraph, FontMetrics fm, int contentMax, List<Line> out) {
    if (paragraph.isEmpty()) {
      out.add(new Line("", 0));
      return;
    }

    // Split on whitespace runs but treat each whitespace-separated token as a word
    String[] words = paragraph.split(" ", -1);
    StringBuilder current = new StringBuilder();
    int currentWidth = 0;

    for (int i = 0; i < words.length; i++) {
      String word = words[i];
      // Reconstruct spaces: split(" ", -1) keeps empty tokens for consecutive spaces
      String piece = (i == 0 || current.length() == 0) ? word : " " + word;
      int pieceWidth = measure(fm, piece);

      if (current.length() == 0) {
        // First token on line (may be empty string from leading space)
        if (measure(fm, word) <= contentMax) {
          current.append(word);
          currentWidth = measure(fm, current.toString());
        } else {
          // Mid-word break for oversized token
          breakLongToken(word, fm, contentMax, out);
          current.setLength(0);
          currentWidth = 0;
        }
        continue;
      }

      if (currentWidth + pieceWidth <= contentMax) {
        current.append(piece);
        currentWidth += pieceWidth;
      } else {
        out.add(new Line(current.toString(), currentWidth));
        current.setLength(0);
        currentWidth = 0;
        if (measure(fm, word) <= contentMax) {
          current.append(word);
          currentWidth = measure(fm, current.toString());
        } else {
          breakLongToken(word, fm, contentMax, out);
        }
      }
    }

    if (current.length() > 0) {
      out.add(new Line(current.toString(), currentWidth));
    }
  }

  private static void breakLongToken(
      String token, FontMetrics fm, int contentMax, List<Line> out) {
    if (token.isEmpty()) {
      out.add(new Line("", 0));
      return;
    }
    StringBuilder chunk = new StringBuilder();
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      String trial = chunk.toString() + c;
      if (measure(fm, trial) <= contentMax || chunk.length() == 0) {
        chunk.append(c);
      } else {
        out.add(new Line(chunk.toString(), measure(fm, chunk.toString())));
        chunk.setLength(0);
        chunk.append(c);
      }
    }
    if (chunk.length() > 0) {
      out.add(new Line(chunk.toString(), measure(fm, chunk.toString())));
    }
  }

  private static int measure(FontMetrics fm, String s) {
    if (s == null || s.isEmpty()) {
      return 0;
    }
    return fm.stringWidth(s);
  }
}
