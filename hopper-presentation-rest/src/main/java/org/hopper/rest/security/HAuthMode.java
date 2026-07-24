package org.hopper.rest.security;

import java.util.Locale;

public enum HAuthMode {
  DISABLED,
  STATIC_DEV,
  OAUTH2;

  public static HAuthMode fromString(String value) {
    if (value == null || value.isBlank()) {
      return DISABLED;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "static-dev", "static_dev", "staticdev", "dev" -> STATIC_DEV;
      case "oauth2", "oidc", "jwt" -> OAUTH2;
      case "disabled", "off", "false", "none" -> DISABLED;
      default -> DISABLED;
    };
  }
}
