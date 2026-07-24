package org.hopper.rest.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.hopper.security.HPrincipal;

/**
 * Validates OAuth2/OIDC access tokens (JWT) and maps them to {@link HPrincipal}.
 *
 * <p>Key material resolution order:
 *
 * <ol>
 *   <li>{@code auth.jwt.hmac-secret} — HS256 shared secret (dev/test only)
 *   <li>{@code auth.jwks-uri} — remote JWKS
 *   <li>OIDC discovery from {@code auth.issuer-uri} → {@code jwks_uri}
 *   <li>Injected {@link JWKSource} (unit tests)
 * </ol>
 */
public class OAuth2JwtValidator {

  private static final Logger LOG = Logger.getLogger(OAuth2JwtValidator.class.getName());

  private final HSecuritySettings settings;
  private final JwtClaimMapper claimMapper;
  private final JWKSource<SecurityContext> injectedJwkSource;

  private volatile ConfigurableJWTProcessor<SecurityContext> processor;
  private volatile boolean initialized;

  public OAuth2JwtValidator(HSecuritySettings settings) {
    this(settings, null, new JwtClaimMapper());
  }

  /** Test / advanced: supply a fixed JWK source (e.g. in-memory RSA public key). */
  public OAuth2JwtValidator(HSecuritySettings settings, JWKSource<SecurityContext> jwkSource) {
    this(settings, jwkSource, new JwtClaimMapper());
  }

  public OAuth2JwtValidator(
      HSecuritySettings settings, JWKSource<SecurityContext> jwkSource, JwtClaimMapper claimMapper) {
    this.settings = settings;
    this.injectedJwkSource = jwkSource;
    this.claimMapper = claimMapper != null ? claimMapper : new JwtClaimMapper();
  }

  public HPrincipal authenticateBearer(String authorizationHeader)
      throws OAuth2AuthenticationException {
    String token = extractBearerToken(authorizationHeader);
    return authenticateToken(token);
  }

  public HPrincipal authenticateToken(String token) throws OAuth2AuthenticationException {
    if (token == null || token.isBlank()) {
      throw new OAuth2AuthenticationException("Missing Bearer access token");
    }
    try {
      ensureInitialized();
      JWTClaimsSet claims = processor.process(token, null);
      HPrincipal principal = claimMapper.toPrincipal(claims, settings);
      // Keep raw token for REST connectors that call Ship/Harbor as this user
      return principal.withAttribute(HPrincipal.ATTR_BEARER_TOKEN, token);
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      throw new OAuth2AuthenticationException("Invalid access token: " + e.getMessage(), e);
    }
  }

  public static String extractBearerToken(String authorizationHeader)
      throws OAuth2AuthenticationException {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      throw new OAuth2AuthenticationException("Authorization header required");
    }
    String value = authorizationHeader.trim();
    if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      String token = value.substring(7).trim();
      if (token.isEmpty()) {
        throw new OAuth2AuthenticationException("Empty Bearer token");
      }
      return token;
    }
    throw new OAuth2AuthenticationException("Authorization scheme must be Bearer");
  }

  private void ensureInitialized() throws OAuth2AuthenticationException {
    if (initialized && processor != null) {
      return;
    }
    synchronized (this) {
      if (initialized && processor != null) {
        return;
      }
      processor = buildProcessor();
      initialized = true;
    }
  }

  private ConfigurableJWTProcessor<SecurityContext> buildProcessor()
      throws OAuth2AuthenticationException {
    if (StringUtils.isBlank(settings.getIssuerUri())
        && StringUtils.isBlank(settings.getHmacSecret())
        && injectedJwkSource == null
        && StringUtils.isBlank(settings.getJwksUri())) {
      throw new OAuth2AuthenticationException(
          "OAuth2 misconfigured: set auth.issuer-uri and/or auth.jwks-uri (or auth.jwt.hmac-secret for dev)");
    }

    JWKSource<SecurityContext> jwkSource = resolveJwkSource();

    Set<JWSAlgorithm> algorithms = new HashSet<>();
    if (StringUtils.isNotBlank(settings.getHmacSecret()) && injectedJwkSource == null
        && StringUtils.isBlank(settings.getJwksUri())
        && isLikelyHmacOnly()) {
      algorithms.add(JWSAlgorithm.HS256);
      algorithms.add(JWSAlgorithm.HS384);
      algorithms.add(JWSAlgorithm.HS512);
    } else if (StringUtils.isNotBlank(settings.getHmacSecret()) && injectedJwkSource == null) {
      // HMAC configured alongside JWKS is unusual; allow both for flexibility
      algorithms.add(JWSAlgorithm.HS256);
      algorithms.add(JWSAlgorithm.HS384);
      algorithms.add(JWSAlgorithm.HS512);
      algorithms.addAll(rsaAndEcAlgorithms());
    } else {
      algorithms.addAll(rsaAndEcAlgorithms());
      if (StringUtils.isNotBlank(settings.getHmacSecret())) {
        algorithms.add(JWSAlgorithm.HS256);
        algorithms.add(JWSAlgorithm.HS384);
        algorithms.add(JWSAlgorithm.HS512);
      }
    }

    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(algorithms, jwkSource);

    DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
    // Access tokens may omit typ, use "JWT", or RFC 9068 "at+jwt"
    jwtProcessor.setJWSTypeVerifier(
        new DefaultJOSEObjectTypeVerifier<>(
            JOSEObjectType.JWT, new JOSEObjectType("at+jwt"), null));
    jwtProcessor.setJWSKeySelector(keySelector);

    JWTClaimsSet.Builder exact = new JWTClaimsSet.Builder();
    if (StringUtils.isNotBlank(settings.getIssuerUri())) {
      exact.issuer(trimTrailingSlash(settings.getIssuerUri()));
    }
    if (StringUtils.isNotBlank(settings.getAudience())) {
      exact.audience(settings.getAudience());
    }

    Set<String> requiredClaims = new HashSet<>();
    requiredClaims.add("sub");
    requiredClaims.add("exp");
    if (StringUtils.isNotBlank(settings.getIssuerUri())) {
      requiredClaims.add("iss");
    }

    DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier =
        new DefaultJWTClaimsVerifier<>(exact.build(), requiredClaims);
    claimsVerifier.setMaxClockSkew(settings.getClockSkewSeconds());
    jwtProcessor.setJWTClaimsSetVerifier(claimsVerifier);

    LOG.info(
        () ->
            "OAuth2 JWT validator ready (issuer="
                + settings.getIssuerUri()
                + ", audience="
                + settings.getAudience()
                + ", jwks="
                + (StringUtils.isNotBlank(settings.getJwksUri())
                    ? settings.getJwksUri()
                    : (injectedJwkSource != null ? "injected" : "discovered/hmac"))
                + ")");

    return jwtProcessor;
  }

  private boolean isLikelyHmacOnly() {
    return StringUtils.isBlank(settings.getJwksUri())
        && StringUtils.isBlank(settings.getIssuerUri())
        && injectedJwkSource == null;
  }

  private static Set<JWSAlgorithm> rsaAndEcAlgorithms() {
    return new HashSet<>(
        Arrays.asList(
            JWSAlgorithm.RS256,
            JWSAlgorithm.RS384,
            JWSAlgorithm.RS512,
            JWSAlgorithm.PS256,
            JWSAlgorithm.PS384,
            JWSAlgorithm.PS512,
            JWSAlgorithm.ES256,
            JWSAlgorithm.ES384,
            JWSAlgorithm.ES512));
  }

  private JWKSource<SecurityContext> resolveJwkSource() throws OAuth2AuthenticationException {
    if (injectedJwkSource != null) {
      return injectedJwkSource;
    }

    if (StringUtils.isNotBlank(settings.getHmacSecret())
        && StringUtils.isBlank(settings.getJwksUri())
        && StringUtils.isBlank(settings.getIssuerUri())) {
      LOG.warning("OAuth2 using HMAC shared secret (dev/test only)");
      return new ImmutableSecret<>(settings.getHmacSecret().getBytes(StandardCharsets.UTF_8));
    }

    String jwksUri = settings.getJwksUri();
    if (StringUtils.isBlank(jwksUri) && StringUtils.isNotBlank(settings.getIssuerUri())) {
      jwksUri = discoverJwksUri(settings.getIssuerUri());
    }
    if (StringUtils.isBlank(jwksUri)) {
      if (StringUtils.isNotBlank(settings.getHmacSecret())) {
        LOG.warning("OAuth2 using HMAC shared secret (no JWKS configured)");
        return new ImmutableSecret<>(settings.getHmacSecret().getBytes(StandardCharsets.UTF_8));
      }
      throw new OAuth2AuthenticationException(
          "Unable to resolve JWKS URI; set auth.jwks-uri or auth.issuer-uri");
    }

    try {
      URL url = URI.create(jwksUri).toURL();
      DefaultResourceRetriever retriever =
          new DefaultResourceRetriever(
              settings.getJwksConnectTimeoutMs(),
              settings.getJwksReadTimeoutMs(),
              50 * 1024 * 1024);
      return new RemoteJWKSet<>(url, retriever);
    } catch (Exception e) {
      throw new OAuth2AuthenticationException("Invalid JWKS URI: " + jwksUri, e);
    }
  }

  /**
   * OIDC discovery: {@code {issuer}/.well-known/openid-configuration} → {@code jwks_uri}.
   */
  String discoverJwksUri(String issuerUri) throws OAuth2AuthenticationException {
    String issuer = trimTrailingSlash(issuerUri);
    String discoveryUrl = issuer + "/.well-known/openid-configuration";
    try {
      HttpClient client =
          HttpClient.newBuilder().connectTimeout(Duration.ofMillis(settings.getJwksConnectTimeoutMs())).build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(discoveryUrl))
              .timeout(Duration.ofMillis(settings.getJwksReadTimeoutMs()))
              .GET()
              .header("Accept", "application/json")
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new OAuth2AuthenticationException(
            "OIDC discovery failed for "
                + discoveryUrl
                + " HTTP "
                + response.statusCode());
      }
      // Minimal JSON parse without pulling another parser — use Nimbus or simple search
      String body = response.body();
      String jwks = extractJsonStringField(body, "jwks_uri");
      if (StringUtils.isBlank(jwks)) {
        throw new OAuth2AuthenticationException(
            "OIDC discovery document missing jwks_uri: " + discoveryUrl);
      }
      LOG.info("Discovered JWKS URI from OIDC metadata: " + jwks);
      return jwks;
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      LOG.log(Level.WARNING, "OIDC discovery failed for issuer " + issuer, e);
      throw new OAuth2AuthenticationException(
          "OIDC discovery failed for issuer " + issuer + ": " + e.getMessage(), e);
    }
  }

  /** Tiny extractor for a top-level JSON string field (discovery docs are flat enough). */
  static String extractJsonStringField(String json, String field) {
    if (json == null || field == null) {
      return null;
    }
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) {
      return null;
    }
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) {
      return null;
    }
    int startQuote = json.indexOf('"', colon + 1);
    if (startQuote < 0) {
      return null;
    }
    int endQuote = startQuote + 1;
    while (endQuote < json.length()) {
      char c = json.charAt(endQuote);
      if (c == '"' && json.charAt(endQuote - 1) != '\\') {
        return json.substring(startQuote + 1, endQuote);
      }
      endQuote++;
    }
    return null;
  }

  private static String trimTrailingSlash(String uri) {
    if (uri == null) {
      return null;
    }
    String s = uri.trim();
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  /** Visible for tests: wrap a JWKSet as a source. */
  public static JWKSource<SecurityContext> immutableJwkSet(JWKSet jwkSet) {
    return new ImmutableJWKSet<>(jwkSet);
  }
}
