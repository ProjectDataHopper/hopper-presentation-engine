package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class OidcBrowserLoginServiceTest {

  @Test
  void notConfiguredWithoutClientId() {
    Properties p = new Properties();
    p.setProperty("auth.enabled", "true");
    p.setProperty("auth.mode", "oauth2");
    p.setProperty("auth.issuer-uri", "https://idp.example/realms/hopper");
    HSecuritySettings settings = new HSecuritySettings(p);
    OidcBrowserLoginService svc = new OidcBrowserLoginService(settings, null);
    assertFalse(svc.isConfigured());
  }

  @Test
  void configuredWithClientIdAndIssuer() {
    Properties p = new Properties();
    p.setProperty("auth.enabled", "true");
    p.setProperty("auth.mode", "oauth2");
    p.setProperty("auth.issuer-uri", "https://idp.example/realms/hopper");
    p.setProperty("auth.oidc.client-id", "hopper-ui");
    HSecuritySettings settings = new HSecuritySettings(p);
    OidcBrowserLoginService svc = new OidcBrowserLoginService(settings, null);
    assertTrue(svc.isConfigured());
  }

  @Test
  void sanitizeReturnToRejectsExternal() {
    assertEquals(
        "/hopper/api/render/main/",
        OidcBrowserLoginService.sanitizeReturnTo("https://evil.example/"));
    assertEquals(
        "/hopper/api/render/main/?x=1",
        OidcBrowserLoginService.sanitizeReturnTo("/hopper/api/render/main/?x=1"));
  }
}
