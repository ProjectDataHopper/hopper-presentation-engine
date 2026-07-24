package org.hopper.rest.admin.oauth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in OIDC provider wizards for the admin panel.
 *
 * <p>Each preset expands wizard inputs into {@code auth.*} settings suitable for {@link
 * org.hopper.rest.admin.AdminSettingsService#applyPatch}.
 */
public final class OAuthProviderPresets {

  private static final List<OAuthProviderPreset> ALL = List.copyOf(buildAll());
  private static final Map<String, OAuthProviderPreset> BY_ID;

  static {
    Map<String, OAuthProviderPreset> map = new LinkedHashMap<>();
    for (OAuthProviderPreset p : ALL) {
      map.put(p.getId(), p);
    }
    BY_ID = Map.copyOf(map);
  }

  private OAuthProviderPresets() {}

  public static List<OAuthProviderPreset> all() {
    return ALL;
  }

  public static Optional<OAuthProviderPreset> find(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_ID.get(id.trim().toLowerCase(java.util.Locale.ROOT)));
  }

  private static List<OAuthProviderPreset> buildAll() {
    List<OAuthProviderPreset> list = new ArrayList<>();
    list.add(google());
    list.add(hopperKeycloak());
    list.add(entra());
    list.add(keycloak());
    list.add(okta());
    list.add(auth0());
    list.add(generic());
    return list;
  }

  /**
   * Shared Data Hopper fleet IdP (hopperShip / hopperHarbor / hopperFrontend Keycloak realm {@code
   * hopper}).
   */
  private static OAuthProviderPreset hopperKeycloak() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.add(
        OAuthWizardField.required(
            "baseUrl",
            "Keycloak base URL",
            "string",
            "Public or internal Keycloak root (e.g. http://localhost:8081 or http://keycloak:8080).",
            "http://keycloak:8080"));
    fields.add(
        OAuthWizardField.optional(
            "realm",
            "Realm",
            "string",
            "hopper",
            "Shared Data Hopper realm name.",
            "hopper"));
    fields.addAll(OAuthWizardField.commonClientFields());
    fields =
        replaceDefault(
            fields,
            "clientId",
            "hopper-ui",
            "PKCE public client used by hopperFrontend and presentation browser login.");
    fields =
        replaceDefault(
            fields,
            "redirectUri",
            "http://localhost:8088/hopper/api/auth/callback",
            "Must match a valid redirect URI on the hopper-ui client (presentation container port).");
    fields =
        replaceDefault(
            fields,
            "defaultRoles",
            "VIEWER",
            "Fallback when token has no realm roles.");
    fields.add(
        OAuthWizardField.optional(
            "audience",
            "Audience",
            "string",
            "hopper-presentation",
            "JWT audience (hopper-presentation or shared hopper-api).",
            "hopper-presentation"));
    fields.add(
        OAuthWizardField.optional(
            "roleAliases",
            "Role aliases",
            "string",
            "viewer:VIEWER,operator:AUTHOR,admin:ADMIN",
            "Map Ship/Harbor Keycloak roles to Hopper roles.",
            "viewer:VIEWER,operator:AUTHOR,admin:ADMIN"));

    return new OAuthProviderPreset(
        "hopper-keycloak",
        "Data Hopper Keycloak",
        "Shared realm hopper for Ship, Harbor, Frontend, and Presentation (SSO).",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String base =
              OAuthProviderPreset.trimTrailingSlash(OAuthProviderPreset.require(inputs, "baseUrl"));
          String realm = OAuthProviderPreset.opt(inputs, "realm", "hopper");
          String clientId = OAuthProviderPreset.opt(inputs, "clientId", "hopper-ui");
          String issuer = base + "/realms/" + realm.trim();
          p.put("auth.issuer-uri", issuer);
          p.put("auth.jwks-uri", "");
          p.put(
              "auth.audience",
              OAuthProviderPreset.opt(inputs, "audience", "hopper-presentation"));
          p.put("auth.username-claim", "preferred_username");
          p.put("auth.email-claim", "email");
          p.put("auth.roles-claim", "realm_access.roles");
          p.put("auth.roles-claim-prefix", "");
          p.put(
              "auth.role-aliases",
              OAuthProviderPreset.opt(
                  inputs, "roleAliases", "viewer:VIEWER,operator:AUTHOR,admin:ADMIN"));
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs -> {
          String base =
              OAuthProviderPreset.trimTrailingSlash(OAuthProviderPreset.require(inputs, "baseUrl"));
          String realm = OAuthProviderPreset.opt(inputs, "realm", "hopper");
          return base + "/realms/" + realm.trim();
        });
  }

  private static OAuthProviderPreset google() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.addAll(OAuthWizardField.commonClientFields());
    // Override default secret ref for Google
    fields =
        replaceDefault(
            fields,
            "clientSecretRef",
            "${GOOGLE_OAUTH_CLIENT_SECRET}",
            "Environment variable holding the Google OAuth client secret.");
    fields =
        replaceDefault(fields, "defaultRoles", "VIEWER", "Google tokens typically have no Hopper roles.");
    fields =
        replaceDefault(
            fields, "adminEmails", "", "Bootstrap ADMIN for your Google account emails.");

    return new OAuthProviderPreset(
        "google",
        "Google",
        "Google Cloud / Workspace OIDC. Prefer id_token (access tokens are often opaque).",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String clientId = OAuthProviderPreset.require(inputs, "clientId");
          p.put("auth.issuer-uri", "https://accounts.google.com");
          p.put("auth.jwks-uri", "https://www.googleapis.com/oauth2/v3/certs");
          p.put("auth.audience", clientId);
          p.put("auth.username-claim", "email");
          p.put("auth.email-claim", "email");
          p.put("auth.roles-claim", "");
          p.put("auth.roles-claim-prefix", "");
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs -> "https://accounts.google.com");
  }

  private static OAuthProviderPreset entra() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.add(
        OAuthWizardField.required(
            "tenant",
            "Tenant ID or domain",
            "string",
            "Directory (tenant) ID GUID, or 'common' / 'organizations' / 'consumers'.",
            "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"));
    fields.addAll(OAuthWizardField.commonClientFields());
    fields.add(
        OAuthWizardField.optional(
            "audience",
            "API audience",
            "string",
            "",
            "JWT audience. Defaults to client id when blank.",
            "api://your-app-id"));
    fields.add(
        OAuthWizardField.optional(
            "rolesClaim",
            "Roles claim",
            "string",
            "roles",
            "Claim path for app roles or groups (e.g. roles, groups).",
            "roles"));

    return new OAuthProviderPreset(
        "entra",
        "Microsoft Entra ID",
        "Azure AD / Entra ID (v2.0 endpoint).",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String tenant = OAuthProviderPreset.require(inputs, "tenant");
          String clientId = OAuthProviderPreset.require(inputs, "clientId");
          String issuer = "https://login.microsoftonline.com/" + tenant.trim() + "/v2.0";
          p.put("auth.issuer-uri", issuer);
          p.put("auth.jwks-uri", "");
          p.put(
              "auth.audience",
              OAuthProviderPreset.opt(inputs, "audience", clientId));
          p.put("auth.username-claim", "preferred_username");
          p.put("auth.email-claim", "email");
          p.put("auth.roles-claim", OAuthProviderPreset.opt(inputs, "rolesClaim", "roles"));
          p.put("auth.roles-claim-prefix", "");
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs -> {
          String tenant = OAuthProviderPreset.require(inputs, "tenant");
          return "https://login.microsoftonline.com/" + tenant.trim() + "/v2.0";
        });
  }

  private static OAuthProviderPreset keycloak() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.add(
        OAuthWizardField.required(
            "baseUrl",
            "Keycloak base URL",
            "string",
            "Server root without /realms/... (e.g. https://idp.example.com).",
            "https://keycloak.example.com"));
    fields.add(
        OAuthWizardField.required(
            "realm", "Realm", "string", "Keycloak realm name.", "hopper"));
    fields.addAll(OAuthWizardField.commonClientFields());
    fields.add(
        OAuthWizardField.optional(
            "audience",
            "Audience",
            "string",
            "hopper-presentation",
            "Expected JWT audience (configure audience mapper on the client if needed).",
            "hopper-presentation"));
    fields.add(
        OAuthWizardField.optional(
            "rolesClaim",
            "Roles claim",
            "string",
            "realm_access.roles",
            "Nested path for realm roles.",
            "realm_access.roles"));

    return new OAuthProviderPreset(
        "keycloak",
        "Keycloak",
        "Keycloak / Red Hat build of Keycloak realms.",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String base = OAuthProviderPreset.trimTrailingSlash(OAuthProviderPreset.require(inputs, "baseUrl"));
          String realm = OAuthProviderPreset.require(inputs, "realm");
          String clientId = OAuthProviderPreset.require(inputs, "clientId");
          String issuer = base + "/realms/" + realm.trim();
          p.put("auth.issuer-uri", issuer);
          p.put("auth.jwks-uri", "");
          p.put("auth.audience", OAuthProviderPreset.opt(inputs, "audience", "hopper-presentation"));
          p.put("auth.username-claim", "preferred_username");
          p.put("auth.email-claim", "email");
          p.put(
              "auth.roles-claim",
              OAuthProviderPreset.opt(inputs, "rolesClaim", "realm_access.roles"));
          p.put("auth.roles-claim-prefix", "");
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs -> {
          String base =
              OAuthProviderPreset.trimTrailingSlash(OAuthProviderPreset.require(inputs, "baseUrl"));
          String realm = OAuthProviderPreset.require(inputs, "realm");
          return base + "/realms/" + realm.trim();
        });
  }

  private static OAuthProviderPreset okta() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.add(
        OAuthWizardField.required(
            "domain",
            "Okta domain",
            "string",
            "Your Okta org domain (without https://).",
            "dev-xxxxx.okta.com"));
    fields.add(
        OAuthWizardField.optional(
            "authorizationServer",
            "Authorization server",
            "string",
            "default",
            "Auth server id path segment (default or custom AS id).",
            "default"));
    fields.addAll(OAuthWizardField.commonClientFields());
    fields.add(
        OAuthWizardField.optional(
            "rolesClaim",
            "Groups / roles claim",
            "string",
            "groups",
            "Claim containing group or role names.",
            "groups"));

    return new OAuthProviderPreset(
        "okta",
        "Okta",
        "Okta OIDC with default or custom authorization server.",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String domain = OAuthProviderPreset.lowerHost(OAuthProviderPreset.require(inputs, "domain"));
          String as =
              OAuthProviderPreset.opt(inputs, "authorizationServer", "default");
          String clientId = OAuthProviderPreset.require(inputs, "clientId");
          String issuer = "https://" + domain + "/oauth2/" + as;
          p.put("auth.issuer-uri", issuer);
          p.put("auth.jwks-uri", "");
          p.put("auth.audience", clientId);
          p.put("auth.username-claim", "preferred_username");
          p.put("auth.email-claim", "email");
          p.put("auth.roles-claim", OAuthProviderPreset.opt(inputs, "rolesClaim", "groups"));
          p.put("auth.roles-claim-prefix", "");
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs -> {
          String domain = OAuthProviderPreset.lowerHost(OAuthProviderPreset.require(inputs, "domain"));
          String as = OAuthProviderPreset.opt(inputs, "authorizationServer", "default");
          return "https://" + domain + "/oauth2/" + as;
        });
  }

  private static OAuthProviderPreset auth0() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.add(
        OAuthWizardField.required(
            "domain",
            "Auth0 domain",
            "string",
            "Tenant domain (without https://).",
            "your-tenant.auth0.com"));
    fields.addAll(OAuthWizardField.commonClientFields());
    fields.add(
        OAuthWizardField.optional(
            "audience",
            "API audience",
            "string",
            "",
            "API identifier if using an Auth0 API; defaults to client id.",
            "https://hopper-api"));
    fields.add(
        OAuthWizardField.optional(
            "rolesClaim",
            "Roles claim",
            "string",
            "https://hopper/roles",
            "Custom claim for Hopper roles (configure Action/Rule in Auth0).",
            "https://hopper/roles"));

    return new OAuthProviderPreset(
        "auth0",
        "Auth0",
        "Auth0 tenant OIDC. Configure a custom roles claim via Action if needed.",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String domain = OAuthProviderPreset.lowerHost(OAuthProviderPreset.require(inputs, "domain"));
          String clientId = OAuthProviderPreset.require(inputs, "clientId");
          String issuer = "https://" + domain + "/";
          p.put("auth.issuer-uri", OAuthProviderPreset.trimTrailingSlash(issuer));
          p.put("auth.jwks-uri", "");
          p.put("auth.audience", OAuthProviderPreset.opt(inputs, "audience", clientId));
          p.put("auth.username-claim", "nickname");
          p.put("auth.email-claim", "email");
          p.put(
              "auth.roles-claim",
              OAuthProviderPreset.opt(inputs, "rolesClaim", "https://hopper/roles"));
          p.put("auth.roles-claim-prefix", "");
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs -> {
          String domain = OAuthProviderPreset.lowerHost(OAuthProviderPreset.require(inputs, "domain"));
          return "https://" + domain;
        });
  }

  private static OAuthProviderPreset generic() {
    List<OAuthWizardField> fields = new ArrayList<>();
    fields.add(
        OAuthWizardField.required(
            "issuerUri",
            "Issuer URI",
            "string",
            "OIDC issuer (discovery at {issuer}/.well-known/openid-configuration).",
            "https://idp.example.com/realms/hopper"));
    fields.add(
        OAuthWizardField.optional(
            "jwksUri",
            "JWKS URI",
            "string",
            "",
            "Optional; discovered from issuer when blank.",
            "https://idp.example.com/.../certs"));
    fields.addAll(OAuthWizardField.commonClientFields());
    fields.add(
        OAuthWizardField.optional(
            "audience",
            "Audience",
            "string",
            "hopper-presentation",
            "Expected JWT audience.",
            "hopper-presentation"));
    fields.add(
        OAuthWizardField.optional(
            "usernameClaim",
            "Username claim",
            "string",
            "preferred_username",
            "JWT claim path for username.",
            "preferred_username"));
    fields.add(
        OAuthWizardField.optional(
            "emailClaim",
            "Email claim",
            "string",
            "email",
            "JWT claim path for email.",
            "email"));
    fields.add(
        OAuthWizardField.optional(
            "rolesClaim",
            "Roles claim",
            "string",
            "roles",
            "JWT claim path for roles.",
            "roles"));

    return new OAuthProviderPreset(
        "generic",
        "Generic OIDC",
        "Any standards-compliant OIDC provider via discovery.",
        fields,
        inputs -> {
          Map<String, String> p = new LinkedHashMap<>();
          String clientId = OAuthProviderPreset.require(inputs, "clientId");
          String issuer =
              OAuthProviderPreset.trimTrailingSlash(OAuthProviderPreset.require(inputs, "issuerUri"));
          p.put("auth.issuer-uri", issuer);
          p.put("auth.jwks-uri", OAuthProviderPreset.opt(inputs, "jwksUri", ""));
          p.put("auth.audience", OAuthProviderPreset.opt(inputs, "audience", "hopper-presentation"));
          p.put(
              "auth.username-claim",
              OAuthProviderPreset.opt(inputs, "usernameClaim", "preferred_username"));
          p.put("auth.email-claim", OAuthProviderPreset.opt(inputs, "emailClaim", "email"));
          p.put("auth.roles-claim", OAuthProviderPreset.opt(inputs, "rolesClaim", "roles"));
          p.put("auth.roles-claim-prefix", "");
          applyCommonClient(p, inputs, clientId);
          return p;
        },
        inputs ->
            OAuthProviderPreset.trimTrailingSlash(OAuthProviderPreset.require(inputs, "issuerUri")));
  }

  private static void applyCommonClient(
      Map<String, String> p, Map<String, String> inputs, String clientId) {
    p.put("auth.oidc.client-id", clientId);
    String secretRef = OAuthProviderPreset.opt(inputs, "clientSecretRef", "");
    if (!secretRef.isBlank()) {
      p.put("auth.oidc.client-secret", OAuthProviderPreset.normalizeSecretRef(secretRef));
    }
    p.put(
        "auth.oidc.redirect-uri",
        OAuthProviderPreset.opt(
            inputs, "redirectUri", "http://localhost:8080/hopper/api/auth/callback"));
    p.put(
        "auth.oidc.scopes",
        OAuthProviderPreset.opt(inputs, "scopes", "openid profile email"));
    p.put("auth.default-roles", OAuthProviderPreset.opt(inputs, "defaultRoles", "VIEWER"));
    String admins = OAuthProviderPreset.opt(inputs, "adminEmails", "");
    if (!admins.isBlank()) {
      p.put("auth.admin-emails", admins);
    }
  }

  private static List<OAuthWizardField> replaceDefault(
      List<OAuthWizardField> fields, String name, String defaultValue, String description) {
    List<OAuthWizardField> out = new ArrayList<>();
    for (OAuthWizardField f : fields) {
      if (name.equals(f.getName())) {
        out.add(
            new OAuthWizardField(
                f.getName(),
                f.getLabel(),
                f.getType(),
                f.isRequired(),
                defaultValue,
                description != null ? description : f.getDescription(),
                f.getPlaceholder()));
      } else {
        out.add(f);
      }
    }
    return out;
  }
}
