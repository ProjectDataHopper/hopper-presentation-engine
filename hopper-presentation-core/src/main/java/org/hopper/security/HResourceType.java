package org.hopper.security;

import java.util.Locale;
import java.util.Optional;

/** Resource types that can appear in an authorization or audit context. */
public enum HResourceType {
  PRESENTATION,
  CONNECTOR,
  CONNECTION,
  THEME,
  COMPONENT,
  METADATA,
  AUDIT_SINK,
  SECURITY;

  public static Optional<HResourceType> fromString(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(HResourceType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
