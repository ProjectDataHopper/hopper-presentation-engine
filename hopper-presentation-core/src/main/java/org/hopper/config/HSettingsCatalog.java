package org.hopper.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema registry for all admin-configurable Hopper Presentation settings.
 *
 * <p>Keys align with {@code hopper-presentation.properties} plus future server/render knobs.
 */
public final class HSettingsCatalog {

  private static final List<HSettingDefinition> ALL;
  private static final Map<String, HSettingDefinition> BY_KEY;

  static {
    List<HSettingDefinition> defs = new ArrayList<>();

    // --- AUTH ---
    defs.add(
        def("auth.enabled")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.BOOLEAN)
            .defaultValue("")
            .description(
                "Master switch for authentication. Empty = derived from auth.mode (disabled → false).")
            .build());
    defs.add(
        def("auth.mode")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.ENUM)
            .defaultValue("disabled")
            .enumValues("disabled", "static-dev", "oauth2")
            .description("Authentication mode: disabled, static-dev, or oauth2.")
            .build());
    defs.add(
        def("auth.dev.user")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.STRING)
            .defaultValue("admin")
            .description("Username for static-dev mode.")
            .build());
    defs.add(
        def("auth.dev.roles")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.STRING_LIST)
            .defaultValue("ADMIN")
            .description("Comma-separated roles for static-dev mode.")
            .build());
    defs.add(
        def("auth.dev.allow-header-override")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Allow X-Hopper-User / X-Hopper-Roles headers in static-dev (CI only).")
            .build());
    defs.add(
        def("auth.issuer-uri")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("")
            .description("OIDC issuer URI (e.g. https://accounts.google.com).")
            .build());
    defs.add(
        def("auth.jwks-uri")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("")
            .description("Optional JWKS URI; discovered from issuer when blank.")
            .build());
    defs.add(
        def("auth.audience")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("hopper-presentation")
            .description("Expected JWT audience (often the OAuth client id for Google/Entra).")
            .build());
    defs.add(
        def("auth.username-claim")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("preferred_username")
            .description("JWT claim path for display username.")
            .build());
    defs.add(
        def("auth.email-claim")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("email")
            .description("JWT claim path for email.")
            .build());
    defs.add(
        def("auth.roles-claim")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("realm_access.roles")
            .description("JWT claim path for roles (Keycloak-style nested path supported).")
            .build());
    defs.add(
        def("auth.roles-claim-prefix")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("")
            .description("Optional prefix stripped from IdP role names (e.g. hopper_).")
            .build());
    defs.add(
        def("auth.required-scopes")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING_LIST)
            .defaultValue("")
            .description("Space-separated scopes required on the access token.")
            .build());
    defs.add(
        def("auth.clock-skew-seconds")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.INT)
            .defaultValue("60")
            .min(0)
            .max(600)
            .description("Allowed JWT clock skew in seconds.")
            .build());
    defs.add(
        def("auth.jwt.hmac-secret")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.SECRET_REF)
            .defaultValue("")
            .sensitive(true)
            .description("HS256 shared secret for local/dev tokens only. Prefer ${ENV} reference.")
            .build());
    defs.add(
        def("auth.jwks.connect-timeout-ms")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.INT)
            .defaultValue("5000")
            .min(100)
            .max(120_000)
            .description("JWKS HTTP connect timeout.")
            .build());
    defs.add(
        def("auth.jwks.read-timeout-ms")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.INT)
            .defaultValue("5000")
            .min(100)
            .max(120_000)
            .description("JWKS HTTP read timeout.")
            .build());
    defs.add(
        def("auth.default-roles")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.STRING_LIST)
            .defaultValue("")
            .description("Roles always granted after claim mapping (e.g. VIEWER for Google).")
            .build());
    defs.add(
        def("auth.admin-emails")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.STRING_LIST)
            .defaultValue("")
            .description("Emails (case-insensitive) that receive the ADMIN role.")
            .build());
    defs.add(
        def("auth.role-aliases")
            .category(HSettingCategory.AUTH)
            .type(HSettingType.STRING)
            .defaultValue("viewer:VIEWER,operator:AUTHOR,admin:ADMIN")
            .description(
                "Map IdP role names to Hopper roles (from:to pairs). Aligns Keycloak viewer/operator/admin with VIEWER/AUTHOR/ADMIN.")
            .build());

    // --- OIDC browser ---
    defs.add(
        def("auth.oidc.client-id")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("")
            .description("OAuth2/OIDC client id for browser PKCE login.")
            .build());
    defs.add(
        def("auth.oidc.client-secret")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.SECRET_REF)
            .defaultValue("")
            .sensitive(true)
            .description("Client secret as ${ENV_VAR} reference (never store raw secrets).")
            .build());
    defs.add(
        def("auth.oidc.redirect-uri")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("http://localhost:8080/hopper/api/auth/callback")
            .description("Authorized redirect URI registered at the IdP.")
            .build());
    defs.add(
        def("auth.oidc.scopes")
            .category(HSettingCategory.OAUTH)
            .type(HSettingType.STRING)
            .defaultValue("openid profile email")
            .description("OIDC scopes requested during browser login.")
            .build());

    // --- SESSION ---
    defs.add(
        def("auth.session.cookie-name")
            .category(HSettingCategory.SESSION)
            .type(HSettingType.STRING)
            .defaultValue("HOPPER_SESSION")
            .description("Browser session cookie name.")
            .build());
    defs.add(
        def("auth.session.ttl-minutes")
            .category(HSettingCategory.SESSION)
            .type(HSettingType.INT)
            .defaultValue("480")
            .min(5)
            .max(10080)
            .description("Browser session time-to-live in minutes.")
            .build());
    defs.add(
        def("auth.session.cookie-secure")
            .category(HSettingCategory.SESSION)
            .type(HSettingType.BOOLEAN)
            .defaultValue("false")
            .description("Set Secure flag on the session cookie (require HTTPS).")
            .build());
    defs.add(
        def("server.session.sweep-interval-seconds")
            .category(HSettingCategory.SESSION)
            .type(HSettingType.INT)
            .defaultValue("60")
            .min(10)
            .max(3600)
            .description("How often expired sessions are swept (future / reserved).")
            .build());

    // --- AUTHZ ---
    defs.add(
        def("authz.default-deny-resources")
            .category(HSettingCategory.AUTHZ)
            .type(HSettingType.BOOLEAN)
            .defaultValue("false")
            .description(
                "When true, named resources require an explicit ACL ALLOW (DENY still wins).")
            .build());

    // --- AUDIT ---
    defs.add(
        def("audit.enabled")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Enable audit event emission.")
            .build());
    defs.add(
        def("audit.fail-open")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Do not fail requests when audit sinks error.")
            .build());
    defs.add(
        def("audit.async")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Emit audit events asynchronously.")
            .build());
    defs.add(
        def("audit.queue.size")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.INT)
            .defaultValue("10000")
            .min(100)
            .max(1_000_000)
            .restartRequired(true)
            .description("Async audit queue capacity (restart recommended after change).")
            .build());
    defs.add(
        def("audit.queue.full-policy")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.ENUM)
            .defaultValue("drop")
            .enumValues("drop", "block")
            .description("Behavior when the audit queue is full.")
            .build());
    defs.add(
        def("audit.redact.parameter-values")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("false")
            .description("Redact all parameter values in audit design snapshots.")
            .build());
    defs.add(
        def("audit.redact.parameter-names")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.STRING_LIST)
            .defaultValue("ssn,email,password,secret")
            .description("Parameter names always redacted in audit events.")
            .build());
    defs.add(
        def("audit.include.sql-text")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Include SQL text on connector runs in audit (fingerprints always kept).")
            .build());
    defs.add(
        def("audit.max-statement-length")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.INT)
            .defaultValue("4000")
            .min(64)
            .max(100_000)
            .description("Max characters of SQL/statement text retained in audit.")
            .build());
    defs.add(
        def("audit.include.row-samples")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("false")
            .description("Include row samples in audit (off by default).")
            .build());
    defs.add(
        def("audit.bootstrap.logging")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Register the bootstrap logging audit sink.")
            .build());
    defs.add(
        def("audit.bootstrap.jsonl.path")
            .category(HSettingCategory.AUDIT)
            .type(HSettingType.STRING)
            .defaultValue("")
            .description("Optional path for bootstrap JSONL audit sink.")
            .build());

    // --- CORS ---
    defs.add(
        def("cors.allow.origin")
            .category(HSettingCategory.CORS)
            .type(HSettingType.BOOLEAN)
            .defaultValue("false")
            .description("Allow permissive CORS origin handling.")
            .build());

    // --- SERVER (bootstrap path is read-only) ---
    defs.add(
        def("metadata.path")
            .category(HSettingCategory.SERVER)
            .type(HSettingType.STRING)
            .defaultValue("")
            .readOnly(true)
            .restartRequired(true)
            .description("Metadata root directory (bootstrap only; change requires restart).")
            .build());

    // --- RENDER / CACHE (schema now; behavior in later PR) ---
    defs.add(
        def("server.render.ttl-minutes")
            .category(HSettingCategory.RENDER)
            .type(HSettingType.INT)
            .defaultValue("60")
            .min(1)
            .max(10080)
            .description("Auto-evict idle renderings after this many minutes (server ops).")
            .build());
    defs.add(
        def("server.render.max-entries")
            .category(HSettingCategory.CACHE)
            .type(HSettingType.INT)
            .defaultValue("200")
            .min(1)
            .max(100_000)
            .description("Maximum concurrent renderings in memory (LRU when exceeded).")
            .build());
    defs.add(
        def("server.cache.enabled")
            .category(HSettingCategory.CACHE)
            .type(HSettingType.BOOLEAN)
            .defaultValue("true")
            .description("Enable response/metadata caching features when implemented.")
            .build());

    ALL = Collections.unmodifiableList(defs);
    Map<String, HSettingDefinition> map = new LinkedHashMap<>();
    for (HSettingDefinition d : ALL) {
      map.put(d.getKey(), d);
    }
    BY_KEY = Collections.unmodifiableMap(map);
  }

  private HSettingsCatalog() {}

  public static List<HSettingDefinition> all() {
    return ALL;
  }

  public static Optional<HSettingDefinition> find(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_KEY.get(key.trim()));
  }

  public static boolean isKnown(String key) {
    return find(key).isPresent();
  }

  public static Map<String, String> defaultProperties() {
    Map<String, String> map = new LinkedHashMap<>();
    for (HSettingDefinition d : ALL) {
      if (d.getDefaultValue() != null && !d.getDefaultValue().isEmpty()) {
        map.put(d.getKey(), d.getDefaultValue());
      }
    }
    return map;
  }

  private static HSettingDefinition.Builder def(String key) {
    return HSettingDefinition.builder(key);
  }
}
