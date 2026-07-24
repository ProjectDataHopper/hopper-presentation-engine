package org.hopper.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default role → action grants for built-in roles. IdP-supplied role names that match these (case
 * insensitive) receive the corresponding actions.
 */
public final class HBuiltInRoles {

  private static final Map<HRole, Set<HAction>> GRANTS;

  static {
    Map<HRole, Set<HAction>> map = new EnumMap<>(HRole.class);

    Set<HAction> viewer = EnumSet.of(
        HAction.PRESENTATION_LIST,
        HAction.PRESENTATION_READ,
        HAction.PRESENTATION_RENDER,
        HAction.PRESENTATION_EXPORT,
        HAction.CONNECTOR_LIST,
        HAction.CONNECTOR_READ,
        HAction.CONNECTION_LIST,
        HAction.CONNECTION_READ,
        HAction.CONNECTION_USE,
        HAction.THEME_LIST,
        HAction.THEME_READ);

    Set<HAction> author = EnumSet.copyOf(viewer);
    author.addAll(
        EnumSet.of(
            HAction.PRESENTATION_CREATE,
            HAction.PRESENTATION_UPDATE,
            HAction.PRESENTATION_DELETE,
            HAction.COMPONENT_CREATE,
            HAction.COMPONENT_UPDATE,
            HAction.COMPONENT_DELETE,
            HAction.CONNECTOR_PREVIEW,
            HAction.THEME_CREATE,
            HAction.THEME_UPDATE,
            HAction.THEME_DELETE));

    Set<HAction> dataEngineer = EnumSet.copyOf(author);
    dataEngineer.addAll(
        EnumSet.of(
            HAction.CONNECTOR_CREATE,
            HAction.CONNECTOR_UPDATE,
            HAction.CONNECTOR_DELETE,
            HAction.CONNECTION_CREATE,
            HAction.CONNECTION_UPDATE,
            HAction.CONNECTION_DELETE));

    Set<HAction> auditor = EnumSet.of(
        HAction.PRESENTATION_LIST,
        HAction.PRESENTATION_READ,
        HAction.CONNECTOR_LIST,
        HAction.CONNECTOR_READ,
        HAction.CONNECTION_LIST,
        HAction.CONNECTION_READ,
        HAction.THEME_LIST,
        HAction.THEME_READ,
        HAction.AUDIT_READ);

    Set<HAction> admin = EnumSet.allOf(HAction.class);

    // AUTHENTICATED alone has no data actions
    map.put(HRole.AUTHENTICATED, EnumSet.noneOf(HAction.class));
    map.put(HRole.VIEWER, Collections.unmodifiableSet(viewer));
    map.put(HRole.AUTHOR, Collections.unmodifiableSet(author));
    map.put(HRole.DATA_ENGINEER, Collections.unmodifiableSet(dataEngineer));
    map.put(HRole.AUDITOR, Collections.unmodifiableSet(auditor));
    map.put(HRole.ADMIN, Collections.unmodifiableSet(admin));

    GRANTS = Collections.unmodifiableMap(map);
  }

  private HBuiltInRoles() {}

  public static Set<HAction> actionsFor(HRole role) {
    if (role == null) {
      return Set.of();
    }
    return GRANTS.getOrDefault(role, Set.of());
  }

  public static Set<HAction> actionsForRoleName(String roleName) {
    return HRole.fromName(roleName).map(HBuiltInRoles::actionsFor).orElse(Set.of());
  }

  /**
   * Union of actions granted by any of the principal's roles that match a built-in role name.
   */
  public static Set<HAction> actionsForRoles(Iterable<String> roleNames) {
    EnumSet<HAction> granted = EnumSet.noneOf(HAction.class);
    if (roleNames == null) {
      return granted;
    }
    for (String roleName : roleNames) {
      if (roleName == null) {
        continue;
      }
      HRole.fromName(roleName.trim().toUpperCase(Locale.ROOT))
          .ifPresent(role -> granted.addAll(actionsFor(role)));
    }
    return granted;
  }

  public static Map<HRole, Set<HAction>> allGrants() {
    return GRANTS;
  }
}
