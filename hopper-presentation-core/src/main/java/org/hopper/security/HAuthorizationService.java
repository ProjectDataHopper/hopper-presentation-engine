package org.hopper.security;

/**
 * Evaluates whether a principal may perform an action, optionally on a named resource.
 *
 * <p>Phase 1: global role → action grants. Phase 5 adds resource ACLs.
 */
public interface HAuthorizationService {

  boolean can(HPrincipal principal, HAction action);

  boolean can(HPrincipal principal, HAction action, HResourceRef resource);

  /**
   * @throws HAccessDeniedException if the principal may not perform the action
   */
  void check(HPrincipal principal, HAction action) throws HAccessDeniedException;

  /**
   * @throws HAccessDeniedException if the principal may not perform the action on the resource
   */
  void check(HPrincipal principal, HAction action, HResourceRef resource)
      throws HAccessDeniedException;

  /** Checks the current {@link HSecurityContext} principal. */
  default void checkCurrent(HAction action) throws HAccessDeniedException {
    check(HSecurityContext.getPrincipal(), action, null);
  }

  default void checkCurrent(HAction action, HResourceRef resource) throws HAccessDeniedException {
    check(HSecurityContext.getPrincipal(), action, resource);
  }
}
