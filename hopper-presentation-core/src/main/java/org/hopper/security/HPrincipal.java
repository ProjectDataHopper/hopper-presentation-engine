package org.hopper.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;

/**
 * Authenticated subject for Hopper. Immutable; build via {@link #builder()} or factories.
 *
 * <p>Identity is normally supplied by an external IdP (OAuth2). Hopper maps claims into roles used
 * by {@link HAuthorizationService}.
 */
@Getter
public final class HPrincipal {
  public static final String AUTH_METHOD_OAUTH2 = "oauth2";
  public static final String AUTH_METHOD_STATIC_DEV = "static-dev";
  public static final String AUTH_METHOD_SYSTEM = "system";
  public static final String AUTH_METHOD_DISABLED = "disabled";

  /**
   * Optional raw Bearer token (access or id token) for server-side REST connectors calling Ship/Harbor
   * APIs as the current user. Never log this attribute.
   */
  public static final String ATTR_BEARER_TOKEN = "bearer_token";

  private final String subject;
  private final String username;
  private final String email;
  private final Set<String> roles;
  private final Set<String> rawClaimsRoles;
  private final String authMethod;
  private final Map<String, String> attributes;

  private HPrincipal(Builder builder) {
    this.subject = builder.subject;
    this.username = builder.username;
    this.email = builder.email;
    this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(builder.roles));
    this.rawClaimsRoles = Collections.unmodifiableSet(new LinkedHashSet<>(builder.rawClaimsRoles));
    this.authMethod = builder.authMethod;
    this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** System principal for background work; has all built-in admin powers via role ADMIN. */
  public static HPrincipal system() {
    return builder()
        .subject("system")
        .username("system")
        .authMethod(AUTH_METHOD_SYSTEM)
        .role(HRole.ADMIN.roleName())
        .role(HRole.AUTHENTICATED.roleName())
        .build();
  }

  /**
   * Anonymous / unauthenticated. Authorization must deny protected actions when auth is enabled.
   */
  public static HPrincipal anonymous() {
    return builder().subject("anonymous").username("anonymous").authMethod("anonymous").build();
  }

  public boolean hasRole(String role) {
    if (role == null) {
      return false;
    }
    for (String r : roles) {
      if (r.equalsIgnoreCase(role)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasRole(HRole role) {
    return role != null && hasRole(role.roleName());
  }

  public boolean isAnonymous() {
    return "anonymous".equalsIgnoreCase(authMethod) || "anonymous".equalsIgnoreCase(subject);
  }

  public boolean isSystem() {
    return AUTH_METHOD_SYSTEM.equalsIgnoreCase(authMethod);
  }

  /** Bearer token for downstream API calls, if captured at authentication time. */
  public String getBearerToken() {
    if (attributes == null) {
      return null;
    }
    String t = attributes.get(ATTR_BEARER_TOKEN);
    return t != null && !t.isBlank() ? t : null;
  }

  /** Copy this principal with an additional/replaced attribute. */
  public HPrincipal withAttribute(String key, String value) {
    Builder b =
        builder()
            .subject(subject)
            .username(username)
            .email(email)
            .authMethod(authMethod)
            .roles(roles)
            .rawClaimsRoles(rawClaimsRoles);
    if (attributes != null) {
      attributes.forEach(b::attribute);
    }
    b.attribute(key, value);
    return b.build();
  }

  @Override
  public String toString() {
    return "HPrincipal{username='" + username + "', roles=" + roles + ", authMethod=" + authMethod
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HPrincipal that)) {
      return false;
    }
    return Objects.equals(subject, that.subject) && Objects.equals(authMethod, that.authMethod);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subject, authMethod);
  }

  public static final class Builder {
    private String subject;
    private String username;
    private String email;
    private final Set<String> roles = new LinkedHashSet<>();
    private final Set<String> rawClaimsRoles = new LinkedHashSet<>();
    private String authMethod = AUTH_METHOD_OAUTH2;
    private final Map<String, String> attributes = new LinkedHashMap<>();

    public Builder subject(String subject) {
      this.subject = subject;
      return this;
    }

    public Builder username(String username) {
      this.username = username;
      return this;
    }

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder authMethod(String authMethod) {
      this.authMethod = authMethod;
      return this;
    }

    public Builder role(String role) {
      if (role != null && !role.isBlank()) {
        this.roles.add(role.trim());
      }
      return this;
    }

    public Builder roles(Iterable<String> roles) {
      if (roles != null) {
        for (String role : roles) {
          role(role);
        }
      }
      return this;
    }

    public Builder rawClaimsRole(String role) {
      if (role != null && !role.isBlank()) {
        this.rawClaimsRoles.add(role.trim());
      }
      return this;
    }

    public Builder rawClaimsRoles(Iterable<String> roles) {
      if (roles != null) {
        for (String role : roles) {
          rawClaimsRole(role);
        }
      }
      return this;
    }

    public Builder attribute(String key, String value) {
      if (key != null) {
        this.attributes.put(key, value);
      }
      return this;
    }

    public HPrincipal build() {
      if (username == null || username.isBlank()) {
        username = subject != null ? subject : "unknown";
      }
      if (subject == null || subject.isBlank()) {
        subject = username;
      }
      // Every non-anonymous principal is AUTHENTICATED
      if (!"anonymous".equalsIgnoreCase(authMethod)) {
        roles.add(HRole.AUTHENTICATED.roleName());
      }
      return new HPrincipal(this);
    }
  }
}
