package org.hopper.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in Hopper roles. Additional role names from an IdP may appear as free strings on {@link
 * HPrincipal}; only these have a default action grant matrix in {@link HBuiltInRoles}.
 */
public enum HRole {
  /** Implicit role granted to any authenticated principal. */
  AUTHENTICATED("AUTHENTICATED"),
  VIEWER("VIEWER"),
  AUTHOR("AUTHOR"),
  DATA_ENGINEER("DATA_ENGINEER"),
  ADMIN("ADMIN"),
  AUDITOR("AUDITOR");

  private static final Map<String, HRole> BY_NAME;

  static {
    Map<String, HRole> map = new HashMap<>();
    for (HRole role : values()) {
      map.put(role.name, role);
    }
    BY_NAME = Collections.unmodifiableMap(map);
  }

  private final String name;

  HRole(String name) {
    this.name = name;
  }

  public String roleName() {
    return name;
  }

  public static Optional<HRole> fromName(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_NAME.get(name.trim().toUpperCase(Locale.ROOT)));
  }
}
