package org.hopper.rest.security;

/**
 * Paths that must work without a logged-in principal (login flow, static assets, HTML shells that
 * bootstrap the auth redirect).
 */
public final class AuthPathExemptions {

  private AuthPathExemptions() {}

  public static String normalize(String path) {
    String p = path == null ? "" : path.trim();
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    // Strip trailing slash for consistent matching (except empty)
    while (p.endsWith("/") && p.length() > 1) {
      p = p.substring(0, p.length() - 1);
    }
    return p;
  }

  /**
   * @param method HTTP method
   * @param path path relative to JAX-RS application root (no leading {@code /api})
   */
  public static boolean isPublic(String method, String path) {
    if (method != null && "OPTIONS".equalsIgnoreCase(method)) {
      return true;
    }
    String p = normalize(path);
    if (p.startsWith("static/") || p.equals("static")) {
      return true;
    }
    // Docker/K8s probes
    if (p.equals("health")
        || p.startsWith("health/")
        || p.equals("system/health")
        || p.startsWith("system/health/")) {
      return true;
    }
    // Auth endpoints (login/callback must never require a session)
    if (p.equals("auth/config")
        || p.equals("auth/login")
        || p.equals("auth/callback")
        || p.equals("auth/me")
        || p.equals("auth/logout")
        || p.startsWith("auth/login/")
        || p.startsWith("auth/callback/")) {
      return true;
    }
    // Home HTML shell — loads hopper-auth.js which redirects to login
    if (method != null
        && "GET".equalsIgnoreCase(method)
        && (p.equals("render/main") || p.equals("render/main/"))) {
      return true;
    }
    return false;
  }

  /** Whether unauthenticated access should 302 to the login page instead of plain 401. */
  public static boolean isBrowserHtmlGet(String method, String path, String acceptHeader) {
    if (method == null || !"GET".equalsIgnoreCase(method)) {
      return false;
    }
    if (acceptHeader != null
        && acceptHeader.contains("application/json")
        && !acceptHeader.contains("text/html")) {
      return false;
    }
    String p = normalize(path);
    // Navigation GETs that return HTML
    return p.equals("render/main")
        || p.startsWith("render/page/")
        || p.startsWith("edit/")
        || p.equals("static/admin-usage.html")
        || p.startsWith("static/edit/");
  }
}
