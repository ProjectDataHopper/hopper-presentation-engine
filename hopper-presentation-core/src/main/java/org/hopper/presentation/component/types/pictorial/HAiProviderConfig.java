package org.hopper.presentation.component.types.pictorial;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.variables.IVariables;
import org.hopper.presentation.datacontext.HGlobalVariables;

/**
 * Configuration model for AI image generation providers with safe API key handling via Apache Hop
 * {@link Encr}.
 *
 * <p>API keys may be stored as:
 *
 * <ul>
 *   <li>plain secrets (obfuscated with {@code Encrypted …} prefix on save)
 *   <li>environment / system variables: {@code ${GOOGLE_AI_API_KEY}}
 *   <li>Hop variable resolvers (e.g. Google Secret Manager): {@code #{gsm:my-ai-api-key}}
 * </ul>
 *
 * Variable forms are <strong>not</strong> encrypted on save ({@link
 * Encr#encryptPasswordIfNotUsingVariables}) and are resolved at use time via {@link
 * IVariables#resolve(String)} (process {@link HGlobalVariables} when available).
 */
@Getter
@Setter
public class HAiProviderConfig {

  public enum ProviderType {
    BUILTIN,
    GOOGLE_IMAGEN,
    XAI_GROK,
    OPENAI_DALLE
  }

  private ProviderType providerType = ProviderType.BUILTIN;
  private String encryptedApiKey = "";
  private String modelName = "";
  private String endpointUrl = "";
  private int timeoutSeconds = 30;

  public HAiProviderConfig() {}

  public HAiProviderConfig(ProviderType providerType, String rawApiKey, String modelName) {
    this.providerType = providerType;
    setRawApiKey(rawApiKey);
    this.modelName = modelName;
  }

  /**
   * Sets API key: encrypts plain secrets; leaves {@code ${…}} / {@code #{…}} expressions as-is.
   */
  public void setRawApiKey(String rawApiKey) {
    if (StringUtils.isBlank(rawApiKey)) {
      this.encryptedApiKey = "";
      return;
    }
    String trimmed = rawApiKey.trim();
    // Explicit check: Hop Encr skips ${…}/%%…%% via getUsedVariables; also protect #{resolver:…}
    // even if an older Hop encoder build does not list RESOLVER_OPEN.
    if (looksLikeVariableExpression(trimmed)) {
      this.encryptedApiKey = trimmed;
    } else {
      this.encryptedApiKey = Encr.encryptPasswordIfNotUsingVariables(trimmed);
    }
  }

  static boolean looksLikeVariableExpression(String value) {
    if (StringUtils.isBlank(value)) {
      return false;
    }
    java.util.List<String> vars = new java.util.ArrayList<>();
    try {
      StringUtil.getUsedVariables(value, vars, true);
    } catch (Exception ignored) {
    }
    if (!vars.isEmpty()) {
      return true;
    }
    return value.contains("${")
        || value.contains("#{")
        || (value.contains("%%") && value.indexOf("%%") != value.lastIndexOf("%%"));
  }

  /**
   * Returns the usable API key for HTTP calls: decrypt if obfuscated, then resolve Hop variables /
   * variable-resolver expressions ({@code ${ENV}}, {@code #{gsm:secret-id}}).
   */
  public String getDecryptedApiKey() {
    return getDecryptedApiKey(HGlobalVariables.get());
  }

  /**
   * Same as {@link #getDecryptedApiKey()} but resolve against the given variable space (tests /
   * explicit host wiring).
   */
  public String getDecryptedApiKey(IVariables variables) {
    if (StringUtils.isBlank(encryptedApiKey)) {
      return "";
    }
    String value = Encr.decryptPasswordOptionallyEncrypted(encryptedApiKey);
    if (StringUtils.isBlank(value)) {
      return "";
    }
    if (variables != null) {
      try {
        String resolved = variables.resolve(value);
        if (StringUtils.isNotBlank(resolved)) {
          value = resolved;
        }
      } catch (Exception ignored) {
        // keep unresolved value; caller will fail on auth if still an expression
      }
    }
    return value != null ? value.trim() : "";
  }

  /** True when the stored value is a variable / resolver expression (not Hop-obfuscated). */
  public boolean isApiKeyVariableExpression() {
    if (StringUtils.isBlank(encryptedApiKey)) {
      return false;
    }
    if (encryptedApiKey.startsWith("Encrypted ")) {
      return false;
    }
    return looksLikeVariableExpression(encryptedApiKey);
  }

  /**
   * Safe UI display: show variable expressions in clear text; mask obfuscated secrets.
   */
  public String getMaskedApiKey() {
    if (StringUtils.isBlank(encryptedApiKey)) {
      return "";
    }
    if (isApiKeyVariableExpression()) {
      return encryptedApiKey.trim();
    }
    if (encryptedApiKey.startsWith("Encrypted ")) {
      return "Encrypted ••••••••";
    }
    return "••••••••";
  }

  public String getEffectiveModelName() {
    if (StringUtils.isNotBlank(modelName)) {
      return modelName;
    }
    switch (providerType) {
      case GOOGLE_IMAGEN:
        return "imagen-3.0-generate-002";
      case XAI_GROK:
        return "grok-2-image";
      case OPENAI_DALLE:
        return "dall-e-3";
      case BUILTIN:
      default:
        return "builtin-v1";
    }
  }

  public String getEffectiveEndpointUrl() {
    if (StringUtils.isNotBlank(endpointUrl)) {
      return endpointUrl;
    }
    switch (providerType) {
      case GOOGLE_IMAGEN:
        return "https://generativelanguage.googleapis.com/v1beta/models/" + getEffectiveModelName() + ":generateImages";
      case XAI_GROK:
        return "https://api.x.ai/v1/images/generations";
      case OPENAI_DALLE:
        return "https://api.openai.com/v1/images/generations";
      case BUILTIN:
      default:
        return "";
    }
  }

  /** Result of a live credential / reachability check against the provider. */
  @Getter
  public static class ConnectionTestResult {
    private final boolean ok;
    private final String message;
    private final int httpStatus;
    private final String resolvedKeyHint;

    public ConnectionTestResult(boolean ok, String message, int httpStatus, String resolvedKeyHint) {
      this.ok = ok;
      this.message = message;
      this.httpStatus = httpStatus;
      this.resolvedKeyHint = resolvedKeyHint != null ? resolvedKeyHint : "";
    }
  }

  /**
   * Live check: resolve the API key (variables / GSM), then call a lightweight provider endpoint.
   *
   * <ul>
   *   <li>BUILTIN — offline OK
   *   <li>OPENAI / XAI — {@code GET /v1/models} with Bearer token
   *   <li>GOOGLE_IMAGEN — {@code GET …/v1beta/models?key=}
   * </ul>
   *
   * Does <strong>not</strong> generate an image (avoids cost / latency).
   */
  public ConnectionTestResult testLiveConnection() {
    return testLiveConnection(HGlobalVariables.get());
  }

  public ConnectionTestResult testLiveConnection(IVariables variables) {
    if (providerType == null || providerType == ProviderType.BUILTIN) {
      return new ConnectionTestResult(
          true, "Built-in local renderer active (no API key required).", 0, "");
    }

    String key = getDecryptedApiKey(variables);
    if (StringUtils.isBlank(key)) {
      return new ConnectionTestResult(
          false,
          "API key is empty after resolution. Set a secret, ${ENV_VAR}, or #{resolver:secret-id}.",
          0,
          "");
    }
    if (key.contains("${") || key.contains("#{") || key.contains("%%")) {
      return new ConnectionTestResult(
          false,
          "API key still looks like an unresolved expression: '"
              + key
              + "'. Check system variables / variable-resolver metadata (e.g. gsm project id) and credentials.",
          0,
          maskResolvedHint(key));
    }

    String keyHint = maskResolvedHint(key);
    try {
      java.net.http.HttpClient client =
          java.net.http.HttpClient.newBuilder()
              .connectTimeout(java.time.Duration.ofSeconds(Math.max(5, timeoutSeconds > 0 ? timeoutSeconds : 30)))
              .build();

      String url;
      java.net.http.HttpRequest.Builder req =
          java.net.http.HttpRequest.newBuilder()
              .timeout(java.time.Duration.ofSeconds(Math.max(5, timeoutSeconds > 0 ? timeoutSeconds : 30)))
              .GET();

      switch (providerType) {
        case OPENAI_DALLE -> {
          url = "https://api.openai.com/v1/models";
          req.header("Authorization", "Bearer " + key);
        }
        case XAI_GROK -> {
          // OpenAI-compatible models list — validates the key without generating images
          url = "https://api.x.ai/v1/models";
          req.header("Authorization", "Bearer " + key);
        }
        case GOOGLE_IMAGEN -> {
          url =
              "https://generativelanguage.googleapis.com/v1beta/models?key="
                  + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
        }
        default -> {
          return new ConnectionTestResult(false, "Unknown provider: " + providerType, 0, keyHint);
        }
      }

      req.uri(java.net.URI.create(url));
      java.net.http.HttpResponse<String> response =
          client.send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      String body = response.body() != null ? response.body() : "";

      if (status >= 200 && status < 300) {
        return new ConnectionTestResult(
            true,
            "Live check OK for "
                + providerType.name()
                + " (HTTP "
                + status
                + "). Model default: "
                + getEffectiveModelName()
                + ". Key resolved ("
                + keyHint
                + ").",
            status,
            keyHint);
      }

      String snippet = body.length() > 280 ? body.substring(0, 280) + "…" : body;
      String reason;
      if (status == 401 || status == 403) {
        reason = "Authentication failed (HTTP " + status + ") — API key rejected or lacks permission.";
      } else if (status == 404) {
        reason =
            "HTTP 404 from probe URL — endpoint may differ for this model; key may still be wrong. Body: "
                + snippet;
      } else {
        reason = "HTTP " + status + " from provider. Body: " + snippet;
      }
      return new ConnectionTestResult(false, reason, status, keyHint);
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.toString();
      return new ConnectionTestResult(
          false, "Connection probe failed: " + msg, 0, keyHint);
    }
  }

  private static String maskResolvedHint(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    if (key.length() <= 8) {
      return "len=" + key.length();
    }
    return key.substring(0, 4) + "…" + key.substring(key.length() - 4) + " (len=" + key.length() + ")";
  }
}
