package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.junit.jupiter.api.Test;

public class OAuth2JwtValidatorTest {

  private static final String ISSUER = "https://idp.test/realms/hopper";
  private static final String AUDIENCE = "hopper-presentation";
  private static final String HMAC_SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes

  @Test
  public void acceptsValidRsaJwtAndMapsRoles() throws Exception {
    RSAKey rsa = new RSAKeyGenerator(2048).keyID("test-key").generate();
    Properties props = oauthProps();
    OAuth2JwtValidator validator =
        new OAuth2JwtValidator(
            new HSecuritySettings(props),
            OAuth2JwtValidator.immutableJwkSet(new JWKSet(rsa.toPublicJWK())));

    String token =
        signRsa(
            rsa,
            new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 120_000))
                .claim("preferred_username", "alice")
                .claim("email", "alice@example.com")
                .claim("realm_access", Map.of("roles", List.of("AUTHOR")))
                .build());

    HPrincipal principal = validator.authenticateBearer("Bearer " + token);
    assertEquals("alice", principal.getUsername());
    assertTrue(principal.hasRole(HRole.AUTHOR));
    assertEquals(HPrincipal.AUTH_METHOD_OAUTH2, principal.getAuthMethod());
  }

  @Test
  public void rejectsExpiredToken() throws Exception {
    RSAKey rsa = new RSAKeyGenerator(2048).keyID("k").generate();
    OAuth2JwtValidator validator =
        new OAuth2JwtValidator(
            new HSecuritySettings(oauthProps()),
            OAuth2JwtValidator.immutableJwkSet(new JWKSet(rsa.toPublicJWK())));

    String token =
        signRsa(
            rsa,
            new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(new Date(System.currentTimeMillis() - 120_000))
                .claim("preferred_username", "alice")
                .build());

    assertThrows(
        OAuth2AuthenticationException.class, () -> validator.authenticateBearer("Bearer " + token));
  }

  @Test
  public void rejectsWrongAudience() throws Exception {
    RSAKey rsa = new RSAKeyGenerator(2048).keyID("k").generate();
    OAuth2JwtValidator validator =
        new OAuth2JwtValidator(
            new HSecuritySettings(oauthProps()),
            OAuth2JwtValidator.immutableJwkSet(new JWKSet(rsa.toPublicJWK())));

    String token =
        signRsa(
            rsa,
            new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience("other-api")
                .expirationTime(new Date(System.currentTimeMillis() + 120_000))
                .claim("preferred_username", "alice")
                .build());

    assertThrows(
        OAuth2AuthenticationException.class, () -> validator.authenticateBearer("Bearer " + token));
  }

  @Test
  public void rejectsWrongIssuer() throws Exception {
    RSAKey rsa = new RSAKeyGenerator(2048).keyID("k").generate();
    OAuth2JwtValidator validator =
        new OAuth2JwtValidator(
            new HSecuritySettings(oauthProps()),
            OAuth2JwtValidator.immutableJwkSet(new JWKSet(rsa.toPublicJWK())));

    String token =
        signRsa(
            rsa,
            new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("https://evil.example/realms/hopper")
                .audience(AUDIENCE)
                .expirationTime(new Date(System.currentTimeMillis() + 120_000))
                .claim("preferred_username", "alice")
                .build());

    assertThrows(
        OAuth2AuthenticationException.class, () -> validator.authenticateBearer("Bearer " + token));
  }

  @Test
  public void rejectsMissingBearerHeader() {
    OAuth2JwtValidator validator = new OAuth2JwtValidator(new HSecuritySettings(oauthProps()));
    assertThrows(OAuth2AuthenticationException.class, () -> validator.authenticateBearer(null));
    assertThrows(
        OAuth2AuthenticationException.class, () -> validator.authenticateBearer("Basic xyz"));
  }

  @Test
  public void acceptsHmacSignedDevToken() throws Exception {
    Properties props = new Properties();
    props.setProperty("auth.enabled", "true");
    props.setProperty("auth.mode", "oauth2");
    props.setProperty("auth.jwt.hmac-secret", HMAC_SECRET);
    props.setProperty("auth.audience", AUDIENCE);
    // no issuer → skip iss check
    props.setProperty("auth.roles-claim", "roles");

    OAuth2JwtValidator validator = new OAuth2JwtValidator(new HSecuritySettings(props));

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("dev-1")
            .audience(AUDIENCE)
            .expirationTime(new Date(System.currentTimeMillis() + 120_000))
            .claim("preferred_username", "devuser")
            .claim("roles", List.of("VIEWER"))
            .build();

    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));

    HPrincipal principal = validator.authenticateToken(jwt.serialize());
    assertEquals("devuser", principal.getUsername());
    assertTrue(principal.hasRole(HRole.VIEWER));
  }

  @Test
  public void extractBearerTokenParsesHeader() throws Exception {
    assertEquals("abc.def.ghi", OAuth2JwtValidator.extractBearerToken("Bearer abc.def.ghi"));
    assertEquals("abc.def.ghi", OAuth2JwtValidator.extractBearerToken("bearer abc.def.ghi"));
  }

  @Test
  public void extractJsonStringFieldFromDiscoveryDocument() {
    String json =
        "{\n  \"issuer\": \"https://idp/realms/x\",\n  \"jwks_uri\": \"https://idp/realms/x/protocol/openid-connect/certs\"\n}";
    assertEquals(
        "https://idp/realms/x/protocol/openid-connect/certs",
        OAuth2JwtValidator.extractJsonStringField(json, "jwks_uri"));
  }

  private static Properties oauthProps() {
    Properties props = new Properties();
    props.setProperty("auth.enabled", "true");
    props.setProperty("auth.mode", "oauth2");
    props.setProperty("auth.issuer-uri", ISSUER);
    props.setProperty("auth.audience", AUDIENCE);
    props.setProperty("auth.username-claim", "preferred_username");
    props.setProperty("auth.roles-claim", "realm_access.roles");
    return props;
  }

  private static String signRsa(RSAKey rsa, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsa.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(rsa));
    return jwt.serialize();
  }
}
