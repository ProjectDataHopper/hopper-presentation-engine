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
    if (pageW <= 0 || pageH <= 0) {
      return clamp(current);
    }
    int availW = Math.max(1, canvasW - MARGIN);
    int availH = Math.max(1, canvasH - MARGIN);
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
