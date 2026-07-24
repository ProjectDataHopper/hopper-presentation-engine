package org.hopper.rest.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.hopper.security.HPrincipal;

/**
 * Authorization Code + PKCE browser login against an OIDC provider (Keycloak, etc.).
 *
 * <p>Uses JDK HTTP + OIDC discovery; access tokens are validated by {@link OAuth2JwtValidator}.
 */
public class OidcBrowserLoginService {

  private static final Logger LOG = Logger.getLogger(OidcBrowserLoginService.class.getName());

  private final HSecuritySettings settings;
  private final OAuth2JwtValidator jwtValidator;
  private final Map<String, PendingLogin> pendingByState = new ConcurrentHashMap<>();
  private volatile OidcEndpoints endpoints;
  private final SecureRandom random = new SecureRandom();

  public OidcBrowserLoginService(HSecuritySettings settings, OAuth2JwtValidator jwtValidator) {
    this.settings = settings;
    this.jwtValidator = jwtValidator;
  }

  public boolean isConfigured() {
    return settings != null
        && settings.isAuthEnabled()
        && settings.getAuthMode() == HAuthMode.OAUTH2
        && StringUtils.isNotBlank(settings.getIssuerUri())
        && StringUtils.isNotBlank(settings.getOidcClientId());
  }

  /** Build IdP authorize URL and remember PKCE state. */
  public String beginLogin(String returnTo) throws OAuth2AuthenticationException {
    if (!isConfigured()) {
      throw new OAuth2AuthenticationException("OIDC browser login is not configured");
    }
    OidcEndpoints ep = resolveEndpoints();
    String state = randomUrlSafe(24);
    String verifier = randomUrlSafe(48);
    String challenge = s256Challenge(verifier);
    String nonce = randomUrlSafe(16);

    pendingByState.put(
        state,
        new PendingLogin(
            verifier, nonce, sanitizeReturnTo(returnTo), Instant.now().plusSeconds(600)));

    String redirectUri = settings.getOidcRedirectUri();
    StringBuilder url = new StringBuilder(ep.authorizationEndpoint);
    url.append(ep.authorizationEndpoint.contains("?") ? "&" : "?");
    url.append("response_type=code");
    url.append("&client_id=").append(enc(settings.getOidcClientId()));
    url.append("&redirect_uri=").append(enc(redirectUri));
    url.append("&scope=").append(enc(settings.getOidcScopes()));
    url.append("&state=").append(enc(state));
    url.append("&nonce=").append(enc(nonce));
    url.append("&code_challenge=").append(enc(challenge));
    url.append("&code_challenge_method=S256");
    return url.toString();
  }

  /**
   * Exchange authorization code for tokens, validate identity JWT, create browser session.
   *
   * <p>Google returns an opaque access token plus a JWT {@code id_token}. Keycloak-style IdPs often
   * return a JWT access token. Prefer {@code id_token} when present.
   */
  public LoginResult completeLoginWithReturn(String code, String state)
      throws OAuth2AuthenticationException {
    if (StringUtils.isBlank(code) || StringUtils.isBlank(state)) {
      throw new OAuth2AuthenticationException("Missing code or state");
    }
    PendingLogin pending = pendingByState.remove(state);
    if (pending == null || pending.expiresAt.isBefore(Instant.now())) {
      throw new OAuth2AuthenticationException("Invalid or expired login state");
    }
    OidcEndpoints ep = resolveEndpoints();
    TokenResponse tokens = exchangeCode(ep.tokenEndpoint, code, pending.codeVerifier);
    String jwt =
        StringUtils.isNotBlank(tokens.idToken())
            ? tokens.idToken()
            : tokens.accessToken();
    if (StringUtils.isBlank(jwt)) {
      throw new OAuth2AuthenticationException("Token response missing id_token and access_token");
    }
    HPrincipal principal = jwtValidator.authenticateToken(jwt);
    // Prefer access_token for Ship/Harbor API calls when present (id_token used for identity)
    if (StringUtils.isNotBlank(tokens.accessToken())) {
      principal =
          principal.withAttribute(HPrincipal.ATTR_BEARER_TOKEN, tokens.accessToken().trim());
    }
    HBrowserSession session = HSessionStore.getInstance().create(principal);
    return new LoginResult(session, pending.returnTo);
  }

  private TokenResponse exchangeCode(String tokenEndpoint, String code, String codeVerifier)
      throws OAuth2AuthenticationException {
    try {
      StringBuilder body = new StringBuilder();
      body.append("grant_type=authorization_code");
      body.append("&code=").append(enc(code));
      body.append("&redirect_uri=").append(enc(settings.getOidcRedirectUri()));
      body.append("&client_id=").append(enc(settings.getOidcClientId()));
      body.append("&code_verifier=").append(enc(codeVerifier));
      if (StringUtils.isNotBlank(settings.getOidcClientSecret())) {
        body.append("&client_secret=").append(enc(settings.getOidcClientSecret()));
      }

      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(tokenEndpoint))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new OAuth2AuthenticationException(
            "Token endpoint HTTP " + response.statusCode() + ": " + truncate(response.body()));
      }
      String accessToken =
          OAuth2JwtValidator.extractJsonStringField(response.body(), "access_token");
      String idToken = OAuth2JwtValidator.extractJsonStringField(response.body(), "id_token");
      if (StringUtils.isBlank(accessToken) && StringUtils.isBlank(idToken)) {
        throw new OAuth2AuthenticationException(
            "Token response missing both access_token and id_token");
      }
      return new TokenResponse(accessToken, idToken);
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      throw new OAuth2AuthenticationException("Token exchange failed: " + e.getMessage(), e);
    }
  }

  private OidcEndpoints resolveEndpoints() throws OAuth2AuthenticationException {
    if (endpoints != null) {
      return endpoints;
    }
    synchronized (this) {
      if (endpoints != null) {
        return endpoints;
      }
      String issuer = trimSlash(settings.getIssuerUri());
      String discovery = issuer + "/.well-known/openid-configuration";
      try {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(discovery))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          throw new OAuth2AuthenticationException(
              "OIDC discovery failed HTTP " + response.statusCode());
        }
        String authz =
            OAuth2JwtValidator.extractJsonStringField(response.body(), "authorization_endpoint");
        String token =
            OAuth2JwtValidator.extractJsonStringField(response.body(), "token_endpoint");
        if (StringUtils.isBlank(authz) || StringUtils.isBlank(token)) {
          throw new OAuth2AuthenticationException("OIDC discovery missing endpoints");
        }
        endpoints = new OidcEndpoints(authz, token);
        LOG.info("OIDC browser login endpoints ready");
        return endpoints;
      } catch (OAuth2AuthenticationException e) {
        LOG.log(Level.WARNING, "OIDC discovery failed, trying Keycloak-style paths", e);
        endpoints =
            new OidcEndpoints(
                issuer + "/protocol/openid-connect/auth",
                issuer + "/protocol/openid-connect/token");
        return endpoints;
      } catch (Exception e) {
        LOG.log(Level.WARNING, "OIDC discovery failed, trying Keycloak-style paths", e);
        endpoints =
            new OidcEndpoints(
                issuer + "/protocol/openid-connect/auth",
                issuer + "/protocol/openid-connect/token");
        return endpoints;
      }
    }
  }

  public static String sanitizeReturnTo(String returnTo) {
    if (returnTo == null || returnTo.isBlank()) {
      return "/hopper/api/render/main/";
    }
    String r = returnTo.trim();
    if (r.startsWith("/") && !r.startsWith("//")) {
      return r;
    }
    return "/hopper/api/render/main/";
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String trimSlash(String uri) {
    if (uri == null) {
      return "";
    }
    String s = uri.trim();
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 200 ? s.substring(0, 200) + "…" : s;
  }

  private String randomUrlSafe(int bytes) {
    byte[] buf = new byte[bytes];
    random.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  private static String s256Challenge(String verifier) throws OAuth2AuthenticationException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception e) {
      throw new OAuth2AuthenticationException("PKCE challenge failed", e);
    }
  }

  public record LoginResult(HBrowserSession session, String returnTo) {}

  private record TokenResponse(String accessToken, String idToken) {}

  private record PendingLogin(
      String codeVerifier, String nonce, String returnTo, Instant expiresAt) {}

  private record OidcEndpoints(String authorizationEndpoint, String tokenEndpoint) {}
}
