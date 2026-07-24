package org.hopper.rest.admin.oauth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.hopper.config.HSettingsMerger;
import org.hopper.rest.HRest;
import org.hopper.rest.admin.AdminSettingsService;
import org.hopper.rest.security.HAuthMode;
import org.hopper.rest.security.HSecuritySettings;
import org.hopper.rest.security.OidcBrowserLoginService;

/** Orchestrates OAuth provider presets: preview, connectivity test, and apply via settings L1. */
public class OAuthAdminService {

  private final HRest hopperRest;
  private final OidcDiscoveryClient discoveryClient;
  private final AtomicReference<Map<String, Object>> lastTest =
      new AtomicReference<>(Map.of());

  public OAuthAdminService(HRest hopperRest) {
    this(hopperRest, new OidcDiscoveryClient());
  }

  public OAuthAdminService(HRest hopperRest, OidcDiscoveryClient discoveryClient) {
    this.hopperRest = hopperRest;
    this.discoveryClient = discoveryClient != null ? discoveryClient : new OidcDiscoveryClient();
  }

  public List<Map<String, Object>> listPresets() {
    List<Map<String, Object>> list = new ArrayList<>();
    for (OAuthProviderPreset p : OAuthProviderPresets.all()) {
      list.add(p.toMap());
    }
    return list;
  }

  public Map<String, Object> preview(String providerId, Map<String, String> inputs) {
    OAuthProviderPreset preset =
        OAuthProviderPresets.find(providerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerId));
    Map<String, String> settings = preset.expand(inputs);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("provider", preset.getId());
    body.put("label", preset.getLabel());
    body.put("issuer", preset.resolveIssuer(inputs));
    body.put("settings", settings);
    body.put("redirectUri", settings.getOrDefault("auth.oidc.redirect-uri", ""));
    body.put(
        "clientSecretConfigured",
        settings.containsKey("auth.oidc.client-secret")
            && !settings.get("auth.oidc.client-secret").isBlank());
    return body;
  }

  /**
   * Test OIDC discovery (and JWKS) for a provider + inputs, or for explicit issuer/jwks without a
   * preset.
   */
  public Map<String, Object> test(String providerId, Map<String, String> inputs) {
    String issuer;
    String explicitJwks = null;
    String provider = providerId;
    if (providerId != null && !providerId.isBlank() && !"generic".equalsIgnoreCase(providerId)) {
      OAuthProviderPreset preset =
          OAuthProviderPresets.find(providerId)
              .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerId));
      // Allow partial inputs for test: only need fields required for issuer resolution
      try {
        issuer = preset.resolveIssuer(inputs != null ? inputs : Map.of());
      } catch (IllegalArgumentException e) {
        // Fall back: try expand validation message
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("success", false);
        fail.put("provider", providerId);
        fail.put("error", e.getMessage());
        fail.put("testedAt", Instant.now().toString());
        lastTest.set(Map.copyOf(fail));
        return fail;
      }
      provider = preset.getId();
    } else if (inputs != null && inputs.get("issuerUri") != null) {
      issuer = OAuthProviderPreset.trimTrailingSlash(inputs.get("issuerUri"));
      explicitJwks = inputs.get("jwksUri");
      provider = providerId != null && !providerId.isBlank() ? providerId : "generic";
    } else if (inputs != null && inputs.get("auth.issuer-uri") != null) {
      issuer = OAuthProviderPreset.trimTrailingSlash(inputs.get("auth.issuer-uri"));
      explicitJwks = inputs.get("auth.jwks-uri");
      provider = "settings";
    } else {
      // Test currently applied configuration
      HSecuritySettings s = hopperRest.getSecuritySettings();
      issuer = s != null ? s.getIssuerUri() : "";
      explicitJwks = s != null ? s.getJwksUri() : null;
      provider = "current";
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("provider", provider);
    result.put("testedAt", Instant.now().toString());

    if (issuer == null || issuer.isBlank()) {
      result.put("success", false);
      result.put("error", "No issuer URI to test");
      lastTest.set(Map.copyOf(result));
      return result;
    }

    OidcDiscoveryClient.DiscoveryResult discovery = discoveryClient.discover(issuer);
    result.putAll(discovery.toMap());

    // If discovery failed but explicit jwks provided, still try JWKS
    if (!discovery.success() && explicitJwks != null && !explicitJwks.isBlank()) {
      OidcDiscoveryClient.JwksCheck jwks = discoveryClient.checkJwks(explicitJwks);
      result.put("jwks", jwks.toMap());
      result.put("success", jwks.ok());
      if (jwks.ok()) {
        result.put("warning", "Discovery failed but JWKS URI is reachable: " + discovery.error());
      }
    } else if (discovery.success()
        && explicitJwks != null
        && !explicitJwks.isBlank()
        && (discovery.jwksUri() == null || discovery.jwksUri().isBlank())) {
      OidcDiscoveryClient.JwksCheck jwks = discoveryClient.checkJwks(explicitJwks);
      result.put("jwks", jwks.toMap());
      if (!jwks.ok()) {
        result.put("success", false);
        result.put("error", "JWKS check failed: " + jwks.message());
      }
    }

    // Overall success requires discovery success (or explicit jwks success above)
    if (discovery.success()) {
      boolean jwksOk =
          discovery.jwks() == null
              || discovery.jwks().jwksUri() == null
              || discovery.jwks().jwksUri().isBlank()
              || discovery.jwks().ok();
      result.put("success", jwksOk);
      if (!jwksOk && discovery.jwks() != null) {
        result.put("error", "JWKS check failed: " + discovery.jwks().message());
      }
    }

    lastTest.set(Map.copyOf(result));
    return result;
  }

  /** Expand preset, optionally require successful discovery, then apply via settings service. */
  public Map<String, Object> apply(
      String providerId, Map<String, String> inputs, boolean requireTestPass) throws Exception {
    OAuthProviderPreset preset =
        OAuthProviderPresets.find(providerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerId));

    Map<String, String> settings = preset.expand(inputs);

    Map<String, Object> discoveryInfo = null;
    if (requireTestPass) {
      Map<String, Object> testResult = test(providerId, inputs);
      discoveryInfo = testResult;
      Object success = testResult.get("success");
      if (!(success instanceof Boolean b && b)) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("success", false);
        fail.put("applied", false);
        fail.put("error", "OIDC connectivity test failed; fix configuration or set requireTest=false");
        fail.put("test", testResult);
        return fail;
      }
    }

    AdminSettingsService.ApplyResult applyResult = hopperRest.applySettingsPatch(settings);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", applyResult.success());
    body.put("applied", applyResult.success());
    body.put("provider", preset.getId());
    body.put("settingsApplied", applyResult.applied());
    body.put("restartRequired", applyResult.restartRequired());
    body.put("errors", applyResult.errors());
    body.put("warnings", applyResult.warnings());
    body.put("status", status());
    if (discoveryInfo != null) {
      body.put("test", discoveryInfo);
    }
    return body;
  }

  public Map<String, Object> status() {
    HSecuritySettings s = hopperRest.getSecuritySettings();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("authEnabled", s != null && s.isAuthEnabled());
    m.put("authMode", s != null && s.getAuthMode() != null ? s.getAuthMode().name() : "DISABLED");
    m.put("issuerUri", s != null ? nullToEmpty(s.getIssuerUri()) : "");
    m.put("jwksUri", s != null ? nullToEmpty(s.getJwksUri()) : "");
    m.put("audience", s != null ? nullToEmpty(s.getAudience()) : "");
    m.put("clientId", s != null ? nullToEmpty(s.getOidcClientId()) : "");
    m.put("redirectUri", s != null ? nullToEmpty(s.getOidcRedirectUri()) : "");
    m.put("scopes", s != null ? nullToEmpty(s.getOidcScopes()) : "");
    m.put(
        "clientSecretConfigured",
        s != null && s.getOidcClientSecret() != null && !s.getOidcClientSecret().isBlank());
    // Show secret as env ref if stored that way in overrides/bootstrap
    String secretDisplay = secretRefDisplay();
    m.put("clientSecretRef", secretDisplay);
    m.put(
        "defaultRoles",
        s != null && s.getDefaultRoles() != null ? List.copyOf(s.getDefaultRoles()) : List.of());
    m.put(
        "adminEmails",
        s != null && s.getAdminEmails() != null ? List.copyOf(s.getAdminEmails()) : List.of());
    m.put("usernameClaim", s != null ? nullToEmpty(s.getUsernameClaim()) : "");
    m.put("rolesClaim", s != null ? nullToEmpty(s.getRolesClaim()) : "");

    OidcBrowserLoginService oidc = hopperRest.getOidcBrowserLoginService();
    m.put("oidcBrowserConfigured", oidc != null && oidc.isConfigured());
    m.put(
        "oauth2ResourceServer",
        s != null
            && s.isAuthEnabled()
            && s.getAuthMode() == HAuthMode.OAUTH2
            && hopperRest.getOAuth2JwtValidator() != null);

    m.put("inferredProvider", inferProvider(s));
    m.put("lastTest", lastTest.get());
    return m;
  }

  private String secretRefDisplay() {
    // Prefer override/bootstrap raw property (env ref) over resolved secret
    try {
      var effective = hopperRest.getAdminSettingsService().effectiveProperties();
      String raw = effective.getProperty("auth.oidc.client-secret", "");
      if (raw != null && HSettingsMerger.isEnvRef(raw)) {
        return raw;
      }
      if (raw != null && !raw.isBlank()) {
        return HSettingsMerger.REDACTED;
      }
    } catch (Exception ignored) {
      // fall through
    }
    return "";
  }

  static String inferProvider(HSecuritySettings s) {
    if (s == null || s.getIssuerUri() == null || s.getIssuerUri().isBlank()) {
      return "none";
    }
    String iss = s.getIssuerUri().toLowerCase();
    if (iss.contains("accounts.google.com")) {
      return "google";
    }
    if (iss.contains("login.microsoftonline.com") || iss.contains("sts.windows.net")) {
      return "entra";
    }
    if (iss.contains("/realms/")) {
      return "keycloak";
    }
    if (iss.contains("okta.com") || iss.contains("/oauth2/")) {
      // Okta often has /oauth2/; Auth0 does not
      if (iss.contains("auth0.com")) {
        return "auth0";
      }
      if (iss.contains("okta.com") || iss.contains("oktapreview.com") || iss.contains("okta-emea.com")) {
        return "okta";
      }
    }
    if (iss.contains("auth0.com")) {
      return "auth0";
    }
    return "generic";
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
