package org.hopper.render.svg;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Shared Java2D / Batik rendering hints for readable presentation text.
 *
 * <p>Batik's {@code StaticRenderer} defaults enable shape anti-aliasing but leave {@link
 * RenderingHints#KEY_TEXT_ANTIALIASING} and {@link RenderingHints#KEY_FRACTIONALMETRICS} unset.
 * Soft-reload PNGs and page SVG generation both apply these hints so labels, tables, and chart
 * text are greyscale-smoothed with sub-pixel positioning.
 *
 * <p>Greyscale text AA (not LCD ClearType) is intentional: soft-reload bitmaps are zoomed and
 * scaled on the browser canvas; baked LCD color fringes look wrong at non-1:1 zoom.
 */
public final class HSvgRenderHints {

  private HSvgRenderHints() {}

  /**
   * Rendering hints for quality text + shapes (copy-safe for Batik {@code ImageRenderer}).
   */
  public static RenderingHints qualityTextHints() {
    RenderingHints hints = new RenderingHints(null);
    putQualityTextHints(hints);
    return hints;
  }

  /**
   * Merge quality text/shape hints into {@code hints} (does not remove unrelated keys).
   *
   * @param hints destination map (must not be null)
   */
  public static void putQualityTextHints(RenderingHints hints) {
    if (hints == null) {
      return;
    }
    hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    hints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }

  /**
   * Apply quality text/shape hints on a {@link Graphics2D} (including Batik {@code SVGGraphics2D}).
   * Batik maps {@link RenderingHints#KEY_TEXT_ANTIALIASING} to SVG {@code text-rendering} when
   * streaming.
   */
  public static void applyQualityText(Graphics2D g) {
    if (g == null) {
      return;
    }
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }
}
