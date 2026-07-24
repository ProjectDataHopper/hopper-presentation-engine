package org.hopper.rest.admin.oauth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Named OIDC provider template: wizard fields + expansion to Hopper settings keys.
 */
public final class OAuthProviderPreset {

  private final String id;
  private final String label;
  private final String description;
  private final List<OAuthWizardField> fields;
  private final Function<Map<String, String>, Map<String, String>> expander;
  private final Function<Map<String, String>, String> issuerResolver;

  public OAuthProviderPreset(
      String id,
      String label,
      String description,
      List<OAuthWizardField> fields,
      Function<Map<String, String>, Map<String, String>> expander,
      Function<Map<String, String>, String> issuerResolver) {
    this.id = Objects.requireNonNull(id);
    this.label = Objects.requireNonNull(label);
    this.description = description != null ? description : "";
    this.fields = List.copyOf(fields);
    this.expander = Objects.requireNonNull(expander);
    this.issuerResolver = issuerResolver != null ? issuerResolver : inputs -> inputs.get("issuerUri");
  }

  public String getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public List<OAuthWizardField> getFields() {
    return fields;
  }

  /** Resolve issuer URI from wizard inputs (for discovery tests). */
  public String resolveIssuer(Map<String, String> inputs) {
    Map<String, String> in = withDefaults(inputs);
    String issuer = issuerResolver.apply(in);
    return issuer != null ? trimTrailingSlash(issuer.trim()) : "";
  }

  /**
   * Expand wizard inputs to a full settings patch (auth.* keys). Does not persist.
   *
   * @throws IllegalArgumentException when required fields are missing
   */
  public Map<String, String> expand(Map<String, String> inputs) {
    Map<String, String> in = withDefaults(inputs);
    validateRequired(in);
    Map<String, String> patch = new LinkedHashMap<>();
    // Always enable oauth2 when applying a provider preset
    patch.put("auth.enabled", "true");
    patch.put("auth.mode", "oauth2");
    patch.putAll(expander.apply(in));
    return patch;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("label", label);
    m.put("description", description);
    List<Map<String, Object>> fieldMaps = new ArrayList<>();
    for (OAuthWizardField f : fields) {
      fieldMaps.add(f.toMap());
    }
    m.put("fields", fieldMaps);
    return m;
  }

  private Map<String, String> withDefaults(Map<String, String> inputs) {
    Map<String, String> in = new LinkedHashMap<>();
    for (OAuthWizardField f : fields) {
      if (f.getDefaultValue() != null && !f.getDefaultValue().isEmpty()) {
        in.put(f.getName(), f.getDefaultValue());
      }
    }
    if (inputs != null) {
      for (Map.Entry<String, String> e : inputs.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) {
          continue;
        }
        if (e.getValue() != null) {
          in.put(e.getKey().trim(), e.getValue().trim());
        }
      }
    }
    return in;
  }

  private void validateRequired(Map<String, String> in) {
    List<String> missing = new ArrayList<>();
    for (OAuthWizardField f : fields) {
      if (!f.isRequired()) {
        continue;
      }
      String v = in.get(f.getName());
      if (v == null || v.isBlank()) {
        missing.add(f.getName());
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Missing required fields: " + String.join(", ", missing));
    }
  }

  static String trimTrailingSlash(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    String r = s;
    while (r.endsWith("/")) {
      r = r.substring(0, r.length() - 1);
    }
    return r;
  }

  static String require(Map<String, String> in, String key) {
    String v = in.get(key);
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException("Missing required field: " + key);
    }
    return v.trim();
  }

  static String opt(Map<String, String> in, String key, String defaultValue) {
    String v = in.get(key);
    if (v == null || v.isBlank()) {
      return defaultValue;
    }
    return v.trim();
  }

  static void putIfNonBlank(Map<String, String> patch, String key, String value) {
    if (value != null && !value.isBlank()) {
      patch.put(key, value.trim());
    }
  }

  static String normalizeSecretRef(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String v = value.trim();
    if (v.startsWith("${") && v.endsWith("}")) {
      return v;
    }
    // Bare env var name → wrap
    if (v.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return "${" + v + "}";
    }
    // Raw secret discouraged; still pass through (will be redacted on GET)
    return v;
  }

  static String lowerHost(String domain) {
    if (domain == null) {
      return "";
    }
    String d = domain.trim();
    d = d.replace("https://", "").replace("http://", "");
    while (d.endsWith("/")) {
      d = d.substring(0, d.length() - 1);
    }
    return d.toLowerCase(Locale.ROOT);
  }
}
