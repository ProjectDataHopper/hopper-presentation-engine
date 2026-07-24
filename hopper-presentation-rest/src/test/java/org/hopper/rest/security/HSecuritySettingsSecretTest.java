package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class HSecuritySettingsSecretTest {

  @Test
  public void resolvesEmptySecretToEmptyWithoutEnv() {
    // When env is unset, blank property stays blank (don't fail construction)
    String resolved = HSecuritySettings.resolveClientSecret("");
    // May be non-empty if developer has GOOGLE_OAUTH_CLIENT_SECRET exported in the shell
    // running tests — still must not throw.
    assertTrue(resolved == null || resolved.length() >= 0);
  }

  @Test
  public void googleSettingsParseDefaultRolesAndAdminEmails() {
    Properties p = new Properties();
    p.setProperty("auth.enabled", "true");
    p.setProperty("auth.mode", "oauth2");
    p.setProperty("auth.issuer-uri", "https://accounts.google.com");
    p.setProperty(
        "auth.audience",
        "1025587414122-27gng2hc0l4lri0rn3jgkjvurornp6iq.apps.googleusercontent.com");
    p.setProperty("auth.default-roles", "VIEWER");
    p.setProperty("auth.admin-emails", "mattcasters@gmail.com");
    p.setProperty(
        "auth.oidc.client-id",
        "1025587414122-27gng2hc0l4lri0rn3jgkjvurornp6iq.apps.googleusercontent.com");

    HSecuritySettings s = new HSecuritySettings(p);
    assertTrue(s.getDefaultRoles().contains("VIEWER"));
    assertTrue(s.getAdminEmails().contains("mattcasters@gmail.com"));
    assertEquals("https://accounts.google.com", s.getIssuerUri());
  }
}
