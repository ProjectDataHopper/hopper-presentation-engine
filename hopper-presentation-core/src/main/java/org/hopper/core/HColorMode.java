package org.hopper.core;

/**
 * Presentation / UI color mode. {@link #LIGHT} and {@link #DARK} are resolved modes used for
 * rendering; {@link #SYSTEM} is client-only preference (never sent to the server as-is — resolve
 * first).
 */
public enum HColorMode {
  LIGHT,
  DARK;

  public static HColorMode fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return LIGHT;
    }
    String v = raw.trim();
    if ("dark".equalsIgnoreCase(v) || "DARK".equals(v)) {
      return DARK;
    }
    return LIGHT;
  }

  public String wireValue() {
    return name().toLowerCase();
  }
}
