package org.hopper.presentation.layout;

/**
 * How a presentation is laid out for render.
 *
 * <ul>
 *   <li>{@link #PAGINATED} — fixed page size (e.g. A4); long tables/crosstabs split across {@code
 *       HRenderPage}s (current default).
 *   <li>{@link #CONTINUOUS} — browser-oriented: width from viewport/design width; height grows with
 *       content; overflow is vertical scroll (single render surface, no multi-page table splits).
 * </ul>
 */
public enum HLayoutMode {
  PAGINATED,
  CONTINUOUS;

  public static HLayoutMode fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return PAGINATED;
    }
    String v = raw.trim();
    if ("continuous".equalsIgnoreCase(v)
        || "scroll".equalsIgnoreCase(v)
        || "web".equalsIgnoreCase(v)) {
      return CONTINUOUS;
    }
    return PAGINATED;
  }

  public String wireValue() {
    return name().toLowerCase();
  }

  public boolean isContinuous() {
    return this == CONTINUOUS;
  }
}
