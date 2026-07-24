package org.hopper.rest.admin.oauth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal OIDC discovery + JWKS reachability checks for the admin OAuth wizard (no secrets).
 */
public class OidcDiscoveryClient {

  private static final Pattern STRING_FIELD =
      Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

  private final Duration connectTimeout;
  private final Duration readTimeout;

  public OidcDiscoveryClient() {
    this(Duration.ofSeconds(5), Duration.ofSeconds(10));
  }

  public OidcDiscoveryClient(Duration connectTimeout, Duration readTimeout) {
    this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
    this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
  }

  public DiscoveryResult discover(String issuerUri) {
    if (issuerUri == null || issuerUri.isBlank()) {
      return DiscoveryResult.failure("", "issuerUri is blank");
    }
    String issuer = OAuthProviderPreset.trimTrailingSlash(issuerUri.trim());
    String discoveryUrl = issuer + "/.well-known/openid-configuration";
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(discoveryUrl))
              .timeout(readTimeout)
              .GET()
              .header("Accept", "application/json")
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return DiscoveryResult.failure(
            discoveryUrl, "HTTP " + response.statusCode() + " from discovery endpoint");
      }
      Map<String, String> fields = parseTopLevelStrings(response.body());
      String discoveredIssuer = fields.get("issuer");
      String authz = fields.get("authorization_endpoint");
      String token = fields.get("token_endpoint");
      String jwks = fields.get("jwks_uri");
      if (authz == null || authz.isBlank() || token == null || token.isBlank()) {
        return DiscoveryResult.failure(
            discoveryUrl, "Discovery document missing authorization_endpoint or token_endpoint");
      }
      JwksCheck jwksCheck = jwks != null && !jwks.isBlank() ? checkJwks(jwks) : JwksCheck.skipped();
      return DiscoveryResult.success(
          discoveryUrl, discoveredIssuer != null ? discoveredIssuer : issuer, authz, token, jwks, jwksCheck);
    } catch (Exception e) {
      return DiscoveryResult.failure(
          discoveryUrl, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  public JwksCheck checkJwks(String jwksUri) {
    if (jwksUri == null || jwksUri.isBlank()) {
      return JwksCheck.skipped();
    }
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(jwksUri.trim()))
              .timeout(readTimeout)
              .GET()
              .header("Accept", "application/json")
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return new JwksCheck(false, jwksUri, "HTTP " + response.statusCode(), 0);
      }
      String body = response.body() != null ? response.body() : "";
      int keyCount = countKeys(body);
      return new JwksCheck(true, jwksUri, "ok", keyCount);
    } catch (Exception e) {
      return new JwksCheck(false, jwksUri, e.getClass().getSimpleName() + ": " + e.getMessage(), 0);
    }
  }

  static Map<String, String> parseTopLevelStrings(String json) {
    Map<String, String> map = new LinkedHashMap<>();
    if (json == null) {
      return map;
    }
    Matcher m = STRING_FIELD.matcher(json);
    while (m.find()) {
      map.putIfAbsent(m.group(1), m.group(2));
    }
    return map;
  }

  static int countKeys(String jwksJson) {
    if (jwksJson == null || jwksJson.isBlank()) {
      return 0;
    }
    // Rough count of JWK objects via "kty"
    int count = 0;
    int idx = 0;
    while ((idx = jwksJson.indexOf("\"kty\"", idx)) >= 0) {
      count++;
      idx += 5;
    }
    return count;
  }

  public record JwksCheck(boolean ok, String jwksUri, String message, int keyCount) {
    static JwksCheck skipped() {
      return new JwksCheck(false, "", "skipped (no jwks_uri)", 0);
    }

    Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("ok", ok);
      m.put("jwksUri", jwksUri);
      m.put("message", message);
      m.put("keyCount", keyCount);
      return m;
    }
  }

  public record DiscoveryResult(
      boolean success,
      String discoveryUrl,
      String issuer,
      String authorizationEndpoint,
      String tokenEndpoint,
      String jwksUri,
      JwksCheck jwks,
      String error) {

    static DiscoveryResult success(
        String discoveryUrl,
        String issuer,
        String authz,
        String token,
        String jwksUri,
        JwksCheck jwks) {
      return new DiscoveryResult(true, discoveryUrl, issuer, authz, token, jwksUri, jwks, null);
    }

    static DiscoveryResult failure(String discoveryUrl, String error) {
      return new DiscoveryResult(false, discoveryUrl, null, null, null, null, null, error);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("success", success);
      m.put("discoveryUrl", discoveryUrl);
      if (success) {
        m.put("issuer", issuer);
        m.put("authorizationEndpoint", authorizationEndpoint);
        m.put("tokenEndpoint", tokenEndpoint);
        m.put("jwksUri", jwksUri);
        if (jwks != null) {
          m.put("jwks", jwks.toMap());
        }
      } else {
        m.put("error", error);
      }
      return m;
    }
  }
}
