package org.hopper.rest.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.hop.core.Const;

/** Auth/authz settings loaded from {@code hopper-presentation.properties}. */
public class HSecuritySettings {

  private final boolean authEnabled;
  private final HAuthMode authMode;
  private final String devUser;
  private final Set<String> devRoles;
  private final boolean allowDevHeaderOverride;
  private final String issuerUri;
  private final String jwksUri;
  private final String audience;
  private final String usernameClaim;
  private final String rolesClaim;
  private final String rolesClaimPrefix;
  private final String emailClaim;
  private final Set<String> requiredScopes;
  private final int clockSkewSeconds;
  private final String hmacSecret;
  private final int jwksConnectTimeoutMs;
  private final int jwksReadTimeoutMs;
  /** When true, named resources require an explicit ACL ALLOW (DENY still always wins). */
  private final boolean defaultDenyResources;

  // Browser OIDC (PKCE) + session
  private final String oidcClientId;
  private final String oidcClientSecret;
  private final String oidcRedirectUri;
  private final String oidcScopes;
  private final String sessionCookieName;
  private final int sessionTtlMinutes;
  private final boolean sessionCookieSecure;
  /** Roles always granted after claim mapping (e.g. VIEWER for Google). */
  private final Set<String> defaultRoles;
  /** Emails that receive ADMIN (case-insensitive). */
  private final Set<String> adminEmails;
  /**
   * Map IdP role names to Hopper roles (e.g. {@code viewer→VIEWER} for shared Keycloak realm with
   * hopperShip). Keys compared case-insensitively after optional prefix strip.
   */
  private final Map<String, String> roleAliases;

  public HSecuritySettings(Properties props) {
    Properties p = props != null ? props : new Properties();
    this.authMode = HAuthMode.fromString(p.getProperty("auth.mode", "disabled"));
    // auth.enabled defaults from mode: disabled → false; otherwise true unless explicitly false
    String enabledProp = p.getProperty("auth.enabled");
    if (enabledProp != null && !enabledProp.isBlank()) {
      this.authEnabled = Const.toBoolean(enabledProp);
    } else {
      this.authEnabled = authMode != HAuthMode.DISABLED;
    }
    this.devUser = p.getProperty("auth.dev.user", "admin");
    this.devRoles = parseRoles(p.getProperty("auth.dev.roles", "ADMIN"));
    this.allowDevHeaderOverride =
        Const.toBoolean(p.getProperty("auth.dev.allow-header-override", "true"));
    this.issuerUri = trimToEmpty(p.getProperty("auth.issuer-uri", ""));
    this.jwksUri = trimToEmpty(p.getProperty("auth.jwks-uri", ""));
    this.audience = trimToEmpty(p.getProperty("auth.audience", "hopper-presentation"));
    this.usernameClaim = p.getProperty("auth.username-claim", "preferred_username");
    this.rolesClaim = p.getProperty("auth.roles-claim", "realm_access.roles");
    this.rolesClaimPrefix = p.getProperty("auth.roles-claim-prefix", "");
    this.emailClaim = p.getProperty("auth.email-claim", "email");
    this.requiredScopes = parseSpaceSeparated(p.getProperty("auth.required-scopes", ""));
    this.clockSkewSeconds = parseInt(p.getProperty("auth.clock-skew-seconds"), 60);
    this.hmacSecret = p.getProperty("auth.jwt.hmac-secret", "");
    this.jwksConnectTimeoutMs = parseInt(p.getProperty("auth.jwks.connect-timeout-ms"), 5000);
    this.jwksReadTimeoutMs = parseInt(p.getProperty("auth.jwks.read-timeout-ms"), 5000);
    this.defaultDenyResources =
        Const.toBoolean(
            p.getProperty(
                "authz.default-deny-resources",
                p.getProperty("auth.default-deny-resources", "false")));
    this.oidcClientId = trimToEmpty(p.getProperty("auth.oidc.client-id", ""));
    this.oidcClientSecret = resolveClientSecret(p.getProperty("auth.oidc.client-secret", ""));
    this.oidcRedirectUri =
        trimToEmpty(
            p.getProperty(
                "auth.oidc.redirect-uri", "http://localhost:8080/hopper/api/auth/callback"));
    this.oidcScopes = trimToEmpty(p.getProperty("auth.oidc.scopes", "openid profile email"));
    this.sessionCookieName = trimToEmpty(p.getProperty("auth.session.cookie-name", "HOPPER_SESSION"));
    this.sessionTtlMinutes = parseInt(p.getProperty("auth.session.ttl-minutes"), 480);
    this.sessionCookieSecure = Const.toBoolean(p.getProperty("auth.session.cookie-secure", "false"));
    this.defaultRoles = parseRoles(p.getProperty("auth.default-roles", ""));
    this.adminEmails = parseEmails(p.getProperty("auth.admin-emails", ""));
    this.roleAliases =
        parseRoleAliases(
            p.getProperty(
                "auth.role-aliases",
                // Data Hopper fleet defaults (hopperShip/Harbor Keycloak roles → HRole)
                "viewer:VIEWER,operator:AUTHOR,admin:ADMIN"));
  }

  public static HSecuritySettings disabled() {
    Properties p = new Properties();
    p.setProperty("auth.enabled", "false");
    p.setProperty("auth.mode", "disabled");
    return new HSecuritySettings(p);
  }

  public static HSecuritySettings fromProperties(Properties props) {
    return new HSecuritySettings(props);
  }

  public boolean isAuthEnabled() {
    return authEnabled;
  }

  public HAuthMode getAuthMode() {
    return authMode;
  }

  public String getDevUser() {
    return devUser;
  }

  public Set<String> getDevRoles() {
    return devRoles;
  }

  public boolean isAllowDevHeaderOverride() {
    return allowDevHeaderOverride;
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public String getJwksUri() {
    return jwksUri;
  }

  public String getAudience() {
    return audience;
  }

  public String getUsernameClaim() {
    return usernameClaim;
  }

  public String getRolesClaim() {
    return rolesClaim;
  }

  public String getRolesClaimPrefix() {
    return rolesClaimPrefix;
  }

  public String getEmailClaim() {
    return emailClaim;
  }

  public Set<String> getRequiredScopes() {
    return requiredScopes;
  }

  public int getClockSkewSeconds() {
    return clockSkewSeconds;
  }

  /** HS256 shared secret for local/dev tokens. Empty in production (use JWKS). */
  public String getHmacSecret() {
    return hmacSecret;
  }

  public int getJwksConnectTimeoutMs() {
    return jwksConnectTimeoutMs;
  }

  public int getJwksReadTimeoutMs() {
    return jwksReadTimeoutMs;
  }

  public boolean isDefaultDenyResources() {
    return defaultDenyResources;
  }

  public String getOidcClientId() {
    return oidcClientId;
  }

  public String getOidcClientSecret() {
    return oidcClientSecret;
  }

  public String getOidcRedirectUri() {
    return oidcRedirectUri;
  }

  public String getOidcScopes() {
    return oidcScopes;
  }

  public String getSessionCookieName() {
    return sessionCookieName;
  }

  public int getSessionTtlMinutes() {
    return sessionTtlMinutes;
  }

  public boolean isSessionCookieSecure() {
    return sessionCookieSecure;
  }

  public Set<String> getDefaultRoles() {
    return defaultRoles;
  }

  public Set<String> getAdminEmails() {
    return adminEmails;
  }

  /** IdP role name (lower) → Hopper role name. Never null. */
  public Map<String, String> getRoleAliases() {
    return roleAliases;
  }

  /**
   * Resolve client secret from property, {@code ${ENV}} placeholder, or known environment variable
   * names (never logs the value).
   */
  static String resolveClientSecret(String propertyValue) {
    String secret = propertyValue == null ? "" : propertyValue.trim();
    if (secret.startsWith("${") && secret.endsWith("}") && secret.length() > 3) {
      String envKey = secret.substring(2, secret.length() - 1).trim();
      String fromEnv = System.getenv(envKey);
      secret = fromEnv != null ? fromEnv.trim() : "";
    }
    if (secret.isBlank()) {
      String fromEnv =
          firstNonBlankEnv("GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_AUTH_CLIENT_SECRET");
      secret = fromEnv != null ? fromEnv : "";
    }
    return secret;
  }

  private static String firstNonBlankEnv(String... keys) {
    if (keys == null) {
      return null;
    }
    for (String key : keys) {
      String v = System.getenv(key);
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return null;
  }

  private static Set<String> parseEmails(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    LinkedHashSet<String> emails = new LinkedHashSet<>();
    for (String part : csv.split("[,\\s]+")) {
      String trimmed = part.trim().toLowerCase(java.util.Locale.ROOT);
      if (!trimmed.isEmpty()) {
        emails.add(trimmed);
      }
    }
    return Collections.unmodifiableSet(emails);
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static int parseInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static Set<String> parseRoles(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    for (String part : csv.split("[,\\s]+")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        roles.add(trimmed);
      }
    }
    return Collections.unmodifiableSet(roles);
  }

  private static Set<String> parseSpaceSeparated(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    LinkedHashSet<String> scopes = new LinkedHashSet<>();
    for (String part : value.split("\\s+")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        scopes.add(trimmed);
      }
    }
    return Collections.unmodifiableSet(scopes);
  }

  /**
   * Parse {@code from:to,from2:to2} role aliases. Empty string disables aliases.
   */
  static Map<String, String> parseRoleAliases(String csv) {
    if (csv == null || csv.isBlank()) {
      return Map.of();
    }
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    for (String part : csv.split("[,\\s]+")) {
      if (part == null || part.isBlank() || !part.contains(":")) {
        continue;
      }
      int colon = part.indexOf(':');
      String from = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      String to = part.substring(colon + 1).trim().toUpperCase(Locale.ROOT);
      if (!from.isEmpty() && !to.isEmpty()) {
        map.put(from, to);
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
