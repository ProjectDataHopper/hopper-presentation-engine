package org.hopper.security;

/**
 * Thread-local holder for the current {@link HPrincipal} and optional request correlation id.
 *
 * <p>REST filters set and clear this around each request. Async work must capture and restore the
 * principal explicitly.
 */
public final class HSecurityContext {

  private static final ThreadLocal<HPrincipal> PRINCIPAL = new ThreadLocal<>();
  private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

  private static volatile HAuthorizationService authorizationService =
      new DefaultHAuthorizationService();

  private HSecurityContext() {}

  public static void setPrincipal(HPrincipal principal) {
    if (principal == null) {
      PRINCIPAL.remove();
    } else {
      PRINCIPAL.set(principal);
    }
  }

  public static HPrincipal getPrincipal() {
    return PRINCIPAL.get();
  }

  /**
   * @return current principal, or {@link HPrincipal#anonymous()} if unset
   */
  public static HPrincipal requirePrincipalOrAnonymous() {
    HPrincipal principal = PRINCIPAL.get();
    return principal != null ? principal : HPrincipal.anonymous();
  }

  public static void setRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      REQUEST_ID.remove();
    } else {
      REQUEST_ID.set(requestId);
    }
  }

  public static String getRequestId() {
    return REQUEST_ID.get();
  }

  public static void clear() {
    PRINCIPAL.remove();
    REQUEST_ID.remove();
  }

  public static HAuthorizationService getAuthorizationService() {
    return authorizationService;
  }

  /** Allows tests or hosts to install a custom authorization service. */
  public static void setAuthorizationService(HAuthorizationService service) {
    authorizationService =
        service != null ? service : new DefaultHAuthorizationService();
  }

  /** Restores the default authorization service (e.g. after tests). */
  public static void resetAuthorizationService() {
    authorizationService = new DefaultHAuthorizationService();
  }

  /**
   * Whether resource-level checks should run for the current principal (real auth, not open/dev
   * disabled mode).
   */
  public static boolean isResourceEnforcementActive() {
    HPrincipal principal = PRINCIPAL.get();
    if (principal == null || principal.isAnonymous()) {
      return false;
    }
    if (HPrincipal.AUTH_METHOD_DISABLED.equalsIgnoreCase(principal.getAuthMethod())) {
      return false;
    }
    return true;
  }

  /**
   * Checks resource access when enforcement is active; no-op otherwise. Throws {@link
   * HAccessDeniedException} on deny.
   */
  public static void checkResource(HAction action, HResourceRef resource)
      throws HAccessDeniedException {
    if (!isResourceEnforcementActive()) {
      return;
    }
    getAuthorizationService().check(getPrincipal(), action, resource);
  }
}
