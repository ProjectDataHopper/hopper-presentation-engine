package org.hopper.presentation.datacontext;

import org.apache.hop.core.variables.IVariables;

/**
 * Optional process-wide parent variable space (system variables from the admin panel).
 *
 * <p>Set by the REST host at startup after loading {@code system-variables} metadata. When non-null,
 * {@link PresentationDataContext} copies these values into each presentation context so connectors
 * and components resolve {@code ${NAME}} consistently.
 */
public final class HGlobalVariables {

  private static volatile IVariables shared;

  private HGlobalVariables() {}

  public static void set(IVariables variables) {
    shared = variables;
  }

  public static IVariables get() {
    return shared;
  }

  /** Clear for tests. */
  public static void clear() {
    shared = null;
  }
}
