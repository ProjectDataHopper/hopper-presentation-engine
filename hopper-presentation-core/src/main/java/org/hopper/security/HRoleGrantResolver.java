package org.hopper.security;

import java.util.Set;

/**
 * Resolves the set of {@link HAction}s granted by role names (built-in and custom).
 */
public interface HRoleGrantResolver {

  /** Union of actions granted by any of the given role names. */
  Set<HAction> actionsForRoles(Iterable<String> roleNames);

  /** Drop any cached custom-role data (call after role CRUD). */
  default void invalidate() {}

  /** Built-in matrix only (no custom roles). */
  static HRoleGrantResolver builtInOnly() {
    return HBuiltInRoles::actionsForRoles;
  }
}
