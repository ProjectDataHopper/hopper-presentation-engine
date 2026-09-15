package org.hopper.presentation.swt;

/**
 * Zoom policy for {@link HPresentationViewer}. Fit modes scale the page to the canvas; {@link
 * #ACTUAL} is 100%; {@link #MANUAL} keeps the last explicit zoom (in/out).
 */
public enum HPresentationZoom {
  PAGE,
  WIDTH,
  HEIGHT,
  ACTUAL,
  MANUAL;

  public static final float MIN = 0.05f;
  public static final float MAX = 16f;
  public static final int MARGIN = 8;
  /** Hop Web inset: larger than a RAP iframe scrollbar so Fit width/height cannot overflow. */
  public static final int WEB_MARGIN = 24;

  public static float clamp(float zoom) {
    if (Float.isNaN(zoom) || Float.isInfinite(zoom) || zoom <= 0f) {
      return 1f;
    }
    return Math.max(MIN, Math.min(MAX, zoom));
  }

  /**
   * @param canvasW usable canvas width in pixels
   * @param canvasH usable canvas height in pixels
   * @param pageW presentation page width
   * @param pageH presentation page height
   * @param current zoom used for {@link #MANUAL}
   */
  public static float compute(
      HPresentationZoom mode, int canvasW, int canvasH, int pageW, int pageH, float current) {
    return compute(mode, canvasW, canvasH, pageW, pageH, current, MARGIN);
  }

  public static float compute(
      HPresentationZoom mode,
      int canvasW,
      int canvasH,
      int pageW,
      int pageH,
      float current,
      int margin) {
    if (pageW <= 0 || pageH <= 0) {
      return clamp(current);
    }
    int inset = Math.max(0, margin);
    int availW = Math.max(1, canvasW - inset);
    int availH = Math.max(1, canvasH - inset);
    if (availW <= 1 && availH <= 1) {
      return clamp(current);
    }
    float widthZoom = (float) availW / (float) pageW;
    float heightZoom = (float) availH / (float) pageH;
    return clamp(
        switch (mode == null ? PAGE : mode) {
          case PAGE -> Math.min(widthZoom, heightZoom);
          case WIDTH -> widthZoom;
          case HEIGHT -> heightZoom;
          case ACTUAL -> 1f;
          case MANUAL -> current;
        });
  }

  /**
   * Fit width/height/page must not exceed the pane on the other axis. Otherwise RAP shows both
   * scrollbars and the next layout tick recomputes zoom (~200% flicker).
   */
  public static float clampFitToPane(
      HPresentationZoom mode, float zoom, int pageW, int pageH, int viewW, int viewH) {
    return clampFitToPane(mode, zoom, pageW, pageH, viewW, viewH, WEB_MARGIN);
  }

  public static float clampFitToPane(
      HPresentationZoom mode,
      float zoom,
      int pageW,
      int pageH,
      int viewW,
      int viewH,
      int margin) {
    if (mode != PAGE && mode != WIDTH && mode != HEIGHT) {
      return clamp(zoom);
    }
    if (pageW <= 0 || pageH <= 0) {
      return clamp(zoom);
    }
    int inset = Math.max(0, margin);
    float maxW = (float) Math.max(1, viewW - inset) / (float) pageW;
    float maxH = (float) Math.max(1, viewH - inset) / (float) pageH;
    return clamp(Math.min(zoom, Math.min(maxW, maxH)));
  }

  /**
   * Page height so {@link #WIDTH} maps the page onto {@code canvasW}×{@code canvasH}. Never shorter
   * than {@code minPageH}. Returns {@code minPageH} when the canvas size is not yet known.
   */
  public static int pageHeightToFillWidth(int pageW, int minPageH, int canvasW, int canvasH) {
    int min = Math.max(1, minPageH);
    if (pageW <= 0 || canvasW <= MARGIN || canvasH <= MARGIN) {
      return min;
    }
    int availW = canvasW - MARGIN;
    int availH = canvasH - MARGIN;
    int fillH = Math.round((float) pageW * (float) availH / (float) availW);
    return Math.max(min, fillH);
  }

  public static float zoomIn(float current) {
    return clamp(current * 1.25f);
  }

  public static float zoomOut(float current) {
    return clamp(current / 1.25f);
  }

  public String label(float zoom) {
    return switch (this) {
      case PAGE -> "Fit page";
      case WIDTH -> "Fit width";
      case HEIGHT -> "Fit height";
      case ACTUAL -> "100%";
      case MANUAL -> Math.round(zoom * 100f) + "%";
    };
  }
}
