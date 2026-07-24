package org.hopper.security;

import java.util.Locale;
import java.util.Optional;

/** Whether an ACL entry applies to a user or a role. */
public enum HAclPrincipalType {
  USER,
  ROLE;

  public static Optional<HAclPrincipalType> fromString(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(HAclPrincipalType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
