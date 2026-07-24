package org.hopper.rest.admin.oauth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Input field shown in the OAuth setup wizard for a provider preset. */
public final class OAuthWizardField {

  private final String name;
  private final String label;
  private final String type; // string | secret-ref | string-list | boolean
  private final boolean required;
  private final String defaultValue;
  private final String description;
  private final String placeholder;

  public OAuthWizardField(
      String name,
      String label,
      String type,
      boolean required,
      String defaultValue,
      String description,
      String placeholder) {
    this.name = name;
    this.label = label;
    this.type = type;
    this.required = required;
    this.defaultValue = defaultValue != null ? defaultValue : "";
    this.description = description != null ? description : "";
    this.placeholder = placeholder != null ? placeholder : "";
  }

  public static OAuthWizardField required(
      String name, String label, String type, String description, String placeholder) {
    return new OAuthWizardField(name, label, type, true, "", description, placeholder);
  }

  public static OAuthWizardField optional(
      String name,
      String label,
      String type,
      String defaultValue,
      String description,
      String placeholder) {
    return new OAuthWizardField(name, label, type, false, defaultValue, description, placeholder);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("label", label);
    m.put("type", type);
    m.put("required", required);
    m.put("defaultValue", defaultValue);
    m.put("description", description);
    m.put("placeholder", placeholder);
    return m;
  }

  public String getName() {
    return name;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public String getPlaceholder() {
    return placeholder;
  }

  public boolean isRequired() {
    return required;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public String getType() {
    return type;
  }

  public static List<OAuthWizardField> commonClientFields() {
    return List.of(
        required(
            "clientId",
            "Client ID",
            "string",
            "OAuth2 / OIDC application client id from the IdP console.",
            "your-client-id"),
        optional(
            "clientSecretRef",
            "Client secret (env ref)",
            "secret-ref",
            "${OIDC_CLIENT_SECRET}",
            "Store only an environment variable reference, e.g. ${GOOGLE_OAUTH_CLIENT_SECRET}.",
            "${OIDC_CLIENT_SECRET}"),
        optional(
            "redirectUri",
            "Redirect URI",
            "string",
            "http://localhost:8080/hopper/api/auth/callback",
            "Must match an authorized redirect URI registered at the IdP.",
            "http://localhost:8080/hopper/api/auth/callback"),
        optional(
            "scopes",
            "Scopes",
            "string",
            "openid profile email",
            "Space-separated OIDC scopes.",
            "openid profile email"),
        optional(
            "defaultRoles",
            "Default roles",
            "string-list",
            "VIEWER",
            "Roles granted to every authenticated user when the IdP does not send Hopper roles.",
            "VIEWER"),
        optional(
            "adminEmails",
            "Admin emails",
            "string-list",
            "",
            "Comma-separated emails that receive the ADMIN role.",
            "admin@example.com"));
  }
}
