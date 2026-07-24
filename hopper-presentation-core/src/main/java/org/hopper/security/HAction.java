package org.hopper.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Named authorization actions for Hopper Presentation Engine.
 *
 * <p>Codes are stable API strings (e.g. {@code presentation.render}). Prefer the enum in Java code;
 * use {@link #fromCode(String)} when reading from config or ACLs.
 */
public enum HAction {
  PRESENTATION_LIST("presentation.list"),
  PRESENTATION_READ("presentation.read"),
  PRESENTATION_CREATE("presentation.create"),
  PRESENTATION_UPDATE("presentation.update"),
  PRESENTATION_DELETE("presentation.delete"),
  PRESENTATION_RENDER("presentation.render"),
  PRESENTATION_EXPORT("presentation.export"),

  CONNECTOR_LIST("connector.list"),
  CONNECTOR_READ("connector.read"),
  CONNECTOR_CREATE("connector.create"),
  CONNECTOR_UPDATE("connector.update"),
  CONNECTOR_DELETE("connector.delete"),
  CONNECTOR_PREVIEW("connector.preview"),

  COMPONENT_CREATE("component.create"),
  COMPONENT_UPDATE("component.update"),
  COMPONENT_DELETE("component.delete"),

  CONNECTION_LIST("connection.list"),
  CONNECTION_READ("connection.read"),
  CONNECTION_CREATE("connection.create"),
  CONNECTION_UPDATE("connection.update"),
  CONNECTION_DELETE("connection.delete"),
  CONNECTION_USE("connection.use"),

  THEME_LIST("theme.list"),
  THEME_READ("theme.read"),
  THEME_CREATE("theme.create"),
  THEME_UPDATE("theme.update"),
  THEME_DELETE("theme.delete"),

  SECURITY_ADMIN("security.admin"),
  AUDIT_READ("audit.read"),
  METADATA_ADMIN("metadata.admin");

  private static final Map<String, HAction> BY_CODE;

  static {
    Map<String, HAction> map = new HashMap<>();
    for (HAction action : values()) {
      map.put(action.code, action);
    }
    BY_CODE = Collections.unmodifiableMap(map);
  }

  private final String code;

  HAction(String code) {
    this.code = code;
  }

  /** Stable action identifier used in ACLs, logs, and configuration. */
  public String code() {
    return code;
  }

  public static Optional<HAction> fromCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_CODE.get(code.trim().toLowerCase(Locale.ROOT)));
  }

  public static HAction requireCode(String code) {
    return fromCode(code)
        .orElseThrow(() -> new IllegalArgumentException("Unknown action code: " + code));
  }
}
