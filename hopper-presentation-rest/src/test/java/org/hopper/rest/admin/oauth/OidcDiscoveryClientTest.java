package org.hopper.rest.admin.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OidcDiscoveryClientTest {

  @Test
  void parseTopLevelStrings() {
    String json =
        """
        {
          "issuer": "https://accounts.google.com",
          "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
          "token_endpoint": "https://oauth2.googleapis.com/token",
          "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs"
        }
        """;
    Map<String, String> fields = OidcDiscoveryClient.parseTopLevelStrings(json);
    assertEquals("https://accounts.google.com", fields.get("issuer"));
    assertEquals("https://www.googleapis.com/oauth2/v3/certs", fields.get("jwks_uri"));
  }

  @Test
  void countKeysInJwks() {
    String jwks =
        """
        {"keys":[
          {"kty":"RSA","kid":"1","n":"x","e":"AQAB"},
          {"kty":"RSA","kid":"2","n":"y","e":"AQAB"}
        ]}
        """;
    assertEquals(2, OidcDiscoveryClient.countKeys(jwks));
  }

  @Test
  void blankIssuerFails() {
    OidcDiscoveryClient.DiscoveryResult r = new OidcDiscoveryClient().discover("  ");
    assertFalse(r.success());
    assertTrue(r.error().contains("blank"));
  }
}
