package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.junit.jupiter.api.Test;
// Date used by JWTClaimsSet.Builder

public class JwtClaimMapperTest {

  private final JwtClaimMapper mapper = new JwtClaimMapper();

  @Test
  public void mapsKeycloakStyleRolesAndUsername() throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("oidc-sub-1")
            .issuer("https://idp.example/realms/hopper")
            .audience("hopper-presentation")
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .claim("preferred_username", "alice")
            .claim("email", "alice@example.com")
            .claim("realm_access", Map.of("roles", List.of("author", "offline_access")))
            .build();

    HPrincipal principal = mapper.toPrincipal(claims, oauthSettings());
    assertEquals("oidc-sub-1", principal.getSubject());
    assertEquals("alice", principal.getUsername());
    assertEquals("alice@example.com", principal.getEmail());
    assertTrue(principal.hasRole(HRole.AUTHOR));
    assertTrue(principal.hasRole(HRole.AUTHENTICATED));
    assertEquals(HPrincipal.AUTH_METHOD_OAUTH2, principal.getAuthMethod());
  }

  @Test
  public void mapsShipKeycloakRoleAliases() throws Exception {
    Properties props = baseOAuthProps();
    props.setProperty("auth.roles-claim", "realm_access.roles");
    props.setProperty("auth.role-aliases", "viewer:VIEWER,operator:AUTHOR,admin:ADMIN");
    HSecuritySettings settings = new HSecuritySettings(props);

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("s-ship")
            .claim("preferred_username", "ops")
            .claim("realm_access", Map.of("roles", List.of("viewer", "operator")))
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();

    HPrincipal principal = mapper.toPrincipal(claims, settings);
    assertTrue(principal.hasRole(HRole.VIEWER));
    assertTrue(principal.hasRole(HRole.AUTHOR));
  }

  @Test
  public void stripsRolePrefix() throws Exception {
    Properties props = baseOAuthProps();
    props.setProperty("auth.roles-claim", "roles");
    props.setProperty("auth.roles-claim-prefix", "hopper_");
    HSecuritySettings settings = new HSecuritySettings(props);

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("s1")
            .claim("preferred_username", "bob")
            .claim("roles", List.of("hopper_viewer", "hopper_auditor"))
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();

    HPrincipal principal = mapper.toPrincipal(claims, settings);
    assertTrue(principal.hasRole(HRole.VIEWER));
    assertTrue(principal.hasRole(HRole.AUDITOR));
  }

  @Test
  public void requiredScopeEnforced() {
    Properties props = baseOAuthProps();
    props.setProperty("auth.required-scopes", "hopper.read hopper.write");
    HSecuritySettings settings = new HSecuritySettings(props);

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("s1")
            .claim("preferred_username", "carol")
            .claim("scope", "hopper.read openid")
            .claim("roles", List.of("VIEWER"))
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();

    assertThrows(OAuth2AuthenticationException.class, () -> mapper.toPrincipal(claims, settings));
  }

  @Test
  public void missingSubjectRejected() {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .claim("preferred_username", "nobody")
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    assertThrows(
        OAuth2AuthenticationException.class, () -> mapper.toPrincipal(claims, oauthSettings()));
  }

  @Test
  public void extractNestedRolesPath() {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .claim("realm_access", Map.of("roles", List.of("ADMIN", "uma_authorization")))
            .build();
    Set<String> roles = JwtClaimMapper.extractRoles(claims, "realm_access.roles");
    assertTrue(roles.contains("ADMIN"));
  }

  @Test
  public void defaultRolesAndAdminEmailForGoogleStyleToken() throws Exception {
    Properties props = baseOAuthProps();
    props.setProperty("auth.roles-claim", "");
    props.setProperty("auth.username-claim", "email");
    props.setProperty("auth.default-roles", "VIEWER");
    props.setProperty("auth.admin-emails", "mattcasters@gmail.com");
    HSecuritySettings settings = new HSecuritySettings(props);

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("google-sub-123")
            .issuer("https://accounts.google.com")
            .audience(props.getProperty("auth.audience"))
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .claim("email", "mattcasters@gmail.com")
            .claim("email_verified", true)
            .claim("name", "Matt Casters")
            .build();

    HPrincipal principal = mapper.toPrincipal(claims, settings);
    assertEquals("mattcasters@gmail.com", principal.getUsername());
    assertEquals("mattcasters@gmail.com", principal.getEmail());
    assertTrue(principal.hasRole(HRole.VIEWER));
    assertTrue(principal.hasRole(HRole.ADMIN));
  }

  @Test
  public void nonAdminGetsOnlyDefaultRoles() throws Exception {
    Properties props = baseOAuthProps();
    props.setProperty("auth.roles-claim", "");
    props.setProperty("auth.default-roles", "VIEWER");
    props.setProperty("auth.admin-emails", "mattcasters@gmail.com");
    HSecuritySettings settings = new HSecuritySettings(props);

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("other-sub")
            .claim("email", "someone.else@gmail.com")
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();

    HPrincipal principal = mapper.toPrincipal(claims, settings);
    assertTrue(principal.hasRole(HRole.VIEWER));
    assertTrue(!principal.hasRole(HRole.ADMIN));
  }

  private static HSecuritySettings oauthSettings() {
    return new HSecuritySettings(baseOAuthProps());
  }

  private static Properties baseOAuthProps() {
    Properties props = new Properties();
    props.setProperty("auth.enabled", "true");
    props.setProperty("auth.mode", "oauth2");
    props.setProperty("auth.issuer-uri", "https://idp.example/realms/hopper");
    props.setProperty("auth.audience", "hopper-presentation");
    props.setProperty("auth.username-claim", "preferred_username");
    props.setProperty("auth.roles-claim", "realm_access.roles");
    return props;
  }
}
