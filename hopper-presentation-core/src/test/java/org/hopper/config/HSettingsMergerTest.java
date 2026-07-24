package org.hopper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class HSettingsMergerTest {

  @Test
  void mergeOrder_defaultsBootstrapOverrides() {
    Properties bootstrap = new Properties();
    bootstrap.setProperty("auth.mode", "static-dev");
    bootstrap.setProperty("auth.session.ttl-minutes", "120");

    Map<String, String> overrides = new LinkedHashMap<>();
    overrides.put("auth.mode", "oauth2");
    overrides.put("auth.issuer-uri", "https://accounts.google.com");

    Properties merged = HSettingsMerger.merge(bootstrap, overrides);
    assertEquals("oauth2", merged.getProperty("auth.mode"));
    assertEquals("https://accounts.google.com", merged.getProperty("auth.issuer-uri"));
    assertEquals("120", merged.getProperty("auth.session.ttl-minutes"));
    // default from catalog still present when not overridden
    assertEquals("hopper-presentation", merged.getProperty("auth.audience"));
  }

  @Test
  void sourceOf_reportsCorrectLayer() {
    Properties bootstrap = new Properties();
    bootstrap.setProperty("auth.mode", "static-dev");
    bootstrap.setProperty("auth.oidc.client-secret", "${MY_SECRET}");

    Map<String, String> overrides = Map.of("auth.issuer-uri", "https://example.com");

    assertEquals(
        HSettingSource.OVERRIDE, HSettingsMerger.sourceOf("auth.issuer-uri", bootstrap, overrides));
    assertEquals(
        HSettingSource.BOOTSTRAP, HSettingsMerger.sourceOf("auth.mode", bootstrap, overrides));
    assertEquals(
        HSettingSource.ENV,
        HSettingsMerger.sourceOf("auth.oidc.client-secret", bootstrap, overrides));
    assertEquals(
        HSettingSource.DEFAULT, HSettingsMerger.sourceOf("auth.audience", bootstrap, overrides));
  }

  @Test
  void redact_masksRawSecrets_butNotEnvRefs() {
    HSettingDefinition secret =
        HSettingsCatalog.find("auth.oidc.client-secret").orElseThrow();
    assertTrue(HSettingsMerger.shouldRedact(secret, "GOCSPX-raw-secret"));
    assertFalse(HSettingsMerger.shouldRedact(secret, "${GOOGLE_OAUTH_CLIENT_SECRET}"));
    assertFalse(HSettingsMerger.shouldRedact(secret, ""));
  }

  @Test
  void effectiveList_redactsSensitiveValues() {
    Properties bootstrap = new Properties();
    bootstrap.setProperty("auth.jwt.hmac-secret", "super-secret-hmac");
    bootstrap.setProperty("auth.oidc.client-secret", "${ENV_SECRET}");

    List<HEffectiveSetting> list =
        HSettingsMerger.effectiveList(bootstrap, Map.of(), true);

    HEffectiveSetting hmac =
        list.stream().filter(s -> "auth.jwt.hmac-secret".equals(s.getKey())).findFirst().orElseThrow();
    assertEquals(HSettingsMerger.REDACTED, hmac.getValue());
    assertTrue(hmac.isConfigured());

    HEffectiveSetting oidc =
        list.stream()
            .filter(s -> "auth.oidc.client-secret".equals(s.getKey()))
            .findFirst()
            .orElseThrow();
    assertEquals("${ENV_SECRET}", oidc.getValue());
  }

  @Test
  void validatePatch_rejectsReadOnlyAndBadEnum() {
    Map<String, String> patch = new LinkedHashMap<>();
    patch.put("metadata.path", "/tmp/evil");
    patch.put("auth.mode", "not-a-mode");

    HSettingsMerger.ValidationResult result = HSettingsMerger.validatePatch(patch);
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("read-only")));
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("auth.mode")));
  }

  @Test
  void validatePatch_acceptsValidOAuthPatch() {
    Map<String, String> patch = new LinkedHashMap<>();
    patch.put("auth.enabled", "true");
    patch.put("auth.mode", "oauth2");
    patch.put("auth.issuer-uri", "https://accounts.google.com");
    patch.put("auth.oidc.client-id", "client-123");
    patch.put("auth.oidc.client-secret", "${GOOGLE_OAUTH_CLIENT_SECRET}");
    patch.put("auth.session.ttl-minutes", "480");

    HSettingsMerger.ValidationResult result = HSettingsMerger.validatePatch(patch);
    assertTrue(result.isValid(), () -> result.errors().toString());
  }

  @Test
  void applyPatchToOverrides_nullRemovesKey() {
    Map<String, String> existing = new LinkedHashMap<>();
    existing.put("auth.mode", "oauth2");
    existing.put("auth.issuer-uri", "https://a");

    Map<String, String> patch = new LinkedHashMap<>();
    patch.put("auth.issuer-uri", null);
    patch.put("auth.audience", "my-aud");

    Map<String, String> next = HSettingsMerger.applyPatchToOverrides(existing, patch);
    assertFalse(next.containsKey("auth.issuer-uri"));
    assertEquals("oauth2", next.get("auth.mode"));
    assertEquals("my-aud", next.get("auth.audience"));
  }

  @Test
  void catalog_containsCoreKeys() {
    assertTrue(HSettingsCatalog.isKnown("auth.mode"));
    assertTrue(HSettingsCatalog.isKnown("auth.oidc.client-id"));
    assertTrue(HSettingsCatalog.isKnown("audit.enabled"));
    assertTrue(HSettingsCatalog.isKnown("server.render.ttl-minutes"));
    assertTrue(HSettingsCatalog.isKnown("metadata.path"));
    assertFalse(HSettingsCatalog.isKnown("not.a.real.key"));
  }
}
