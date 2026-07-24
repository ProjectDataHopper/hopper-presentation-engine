package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class HSecuritySettingsOAuthTest {

  @Test
  public void parsesOAuthProperties() {
    Properties props = new Properties();
    props.setProperty("auth.enabled", "true");
    props.setProperty("auth.mode", "oauth2");
    props.setProperty("auth.issuer-uri", "https://idp.example/realms/hopper/");
    props.setProperty("auth.jwks-uri", "https://idp.example/realms/hopper/protocol/openid-connect/certs");
    props.setProperty("auth.audience", "hopper-presentation");
    props.setProperty("auth.roles-claim-prefix", "hopper_");
    props.setProperty("auth.required-scopes", "openid hopper.api");
    props.setProperty("auth.clock-skew-seconds", "120");

    HSecuritySettings settings = new HSecuritySettings(props);
    assertTrue(settings.isAuthEnabled());
    assertEquals(HAuthMode.OAUTH2, settings.getAuthMode());
    assertEquals("https://idp.example/realms/hopper/", settings.getIssuerUri());
    assertEquals("hopper_", settings.getRolesClaimPrefix());
    assertTrue(settings.getRequiredScopes().contains("openid"));
    assertTrue(settings.getRequiredScopes().contains("hopper.api"));
    assertEquals(120, settings.getClockSkewSeconds());
  }
}
