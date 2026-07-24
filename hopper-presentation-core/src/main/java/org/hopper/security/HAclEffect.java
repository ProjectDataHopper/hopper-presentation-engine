package org.hopper.security;

import java.util.Locale;
import java.util.Optional;

/** Effect of an ACL entry. DENY always wins over ALLOW when both match. */
public enum HAclEffect {
  ALLOW,
  DENY;

  public static Optional<HAclEffect> fromString(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(HAclEffect.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
