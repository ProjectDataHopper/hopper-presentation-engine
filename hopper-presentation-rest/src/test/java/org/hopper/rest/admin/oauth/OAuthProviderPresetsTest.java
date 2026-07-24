package org.hopper.rest.admin.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuthProviderPresetsTest {

  @Test
  void allPresetsRegistered() {
    assertEquals(7, OAuthProviderPresets.all().size());
    assertTrue(OAuthProviderPresets.find("google").isPresent());
    assertTrue(OAuthProviderPresets.find("hopper-keycloak").isPresent());
    assertTrue(OAuthProviderPresets.find("entra").isPresent());
    assertTrue(OAuthProviderPresets.find("keycloak").isPresent());
    assertTrue(OAuthProviderPresets.find("okta").isPresent());
    assertTrue(OAuthProviderPresets.find("auth0").isPresent());
    assertTrue(OAuthProviderPresets.find("generic").isPresent());
    assertTrue(OAuthProviderPresets.find("GOOGLE").isPresent());
  }

  @Test
  void hopperKeycloakExpand() {
    Map<String, String> settings =
        OAuthProviderPresets.find("hopper-keycloak")
            .orElseThrow()
            .expand(
                Map.of(
                    "baseUrl", "http://keycloak:8080",
                    "clientId", "hopper-ui",
                    "audience", "hopper-presentation"));
    assertEquals("http://keycloak:8080/realms/hopper", settings.get("auth.issuer-uri"));
    assertEquals("realm_access.roles", settings.get("auth.roles-claim"));
    assertEquals(
        "viewer:VIEWER,operator:AUTHOR,admin:ADMIN", settings.get("auth.role-aliases"));
    assertEquals("hopper-ui", settings.get("auth.oidc.client-id"));
  }

  @Test
  void googleExpand() {
    Map<String, String> settings =
        OAuthProviderPresets.find("google")
            .orElseThrow()
            .expand(
                Map.of(
                    "clientId", "1025587414122-abc.apps.googleusercontent.com",
                    "clientSecretRef", "GOOGLE_OAUTH_CLIENT_SECRET",
                    "adminEmails", "mattcasters@gmail.com",
                    "defaultRoles", "VIEWER"));

    assertEquals("true", settings.get("auth.enabled"));
    assertEquals("oauth2", settings.get("auth.mode"));
    assertEquals("https://accounts.google.com", settings.get("auth.issuer-uri"));
    assertEquals(
        "https://www.googleapis.com/oauth2/v3/certs", settings.get("auth.jwks-uri"));
    assertEquals(
        "1025587414122-abc.apps.googleusercontent.com", settings.get("auth.audience"));
    assertEquals(
        "1025587414122-abc.apps.googleusercontent.com", settings.get("auth.oidc.client-id"));
    assertEquals("${GOOGLE_OAUTH_CLIENT_SECRET}", settings.get("auth.oidc.client-secret"));
    assertEquals("email", settings.get("auth.username-claim"));
    assertEquals("", settings.get("auth.roles-claim"));
    assertEquals("VIEWER", settings.get("auth.default-roles"));
    assertEquals("mattcasters@gmail.com", settings.get("auth.admin-emails"));
  }

  @Test
  void keycloakExpandBuildsRealmIssuer() {
    Map<String, String> settings =
        OAuthProviderPresets.find("keycloak")
            .orElseThrow()
            .expand(
                Map.of(
                    "baseUrl", "https://idp.example.com/",
                    "realm", "hopper",
                    "clientId", "hopper-ui",
                    "audience", "hopper-presentation"));

    assertEquals("https://idp.example.com/realms/hopper", settings.get("auth.issuer-uri"));
    assertEquals("realm_access.roles", settings.get("auth.roles-claim"));
    assertEquals("hopper-ui", settings.get("auth.oidc.client-id"));
  }

  @Test
  void entraExpandUsesTenant() {
    Map<String, String> settings =
        OAuthProviderPresets.find("entra")
            .orElseThrow()
            .expand(
                Map.of(
                    "tenant", "common",
                    "clientId", "app-id-123"));
    assertEquals(
        "https://login.microsoftonline.com/common/v2.0", settings.get("auth.issuer-uri"));
    assertEquals("app-id-123", settings.get("auth.audience"));
  }

  @Test
  void oktaAndAuth0Domains() {
    Map<String, String> okta =
        OAuthProviderPresets.find("okta")
            .orElseThrow()
            .expand(Map.of("domain", "https://dev-123.okta.com/", "clientId", "okta-client"));
    assertEquals("https://dev-123.okta.com/oauth2/default", okta.get("auth.issuer-uri"));

    Map<String, String> auth0 =
        OAuthProviderPresets.find("auth0")
            .orElseThrow()
            .expand(Map.of("domain", "tenant.auth0.com", "clientId", "a0-client"));
    assertEquals("https://tenant.auth0.com", auth0.get("auth.issuer-uri"));
    assertEquals("https://hopper/roles", auth0.get("auth.roles-claim"));
  }

  @Test
  void missingRequiredFails() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OAuthProviderPresets.find("google").orElseThrow().expand(Map.of()));
  }

  @Test
  void inferProviderFromIssuer() {
    assertEquals(
        "google",
        OAuthAdminService.inferProvider(
            settingsWithIssuer("https://accounts.google.com")));
    assertEquals(
        "keycloak",
        OAuthAdminService.inferProvider(
            settingsWithIssuer("https://idp.example.com/realms/hopper")));
    assertEquals(
        "entra",
        OAuthAdminService.inferProvider(
            settingsWithIssuer("https://login.microsoftonline.com/xxx/v2.0")));
    assertEquals(
        "auth0",
        OAuthAdminService.inferProvider(settingsWithIssuer("https://x.auth0.com/")));
    assertEquals(
        "okta",
        OAuthAdminService.inferProvider(
            settingsWithIssuer("https://dev.okta.com/oauth2/default")));
  }

  private static org.hopper.rest.security.HSecuritySettings settingsWithIssuer(String issuer) {
    java.util.Properties p = new java.util.Properties();
    p.setProperty("auth.mode", "oauth2");
    p.setProperty("auth.issuer-uri", issuer);
    return new org.hopper.rest.security.HSecuritySettings(p);
  }
}
