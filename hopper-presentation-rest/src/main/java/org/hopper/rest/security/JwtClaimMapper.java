package org.hopper.rest.security;

import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.hopper.security.HPrincipal;

/** Maps validated JWT claims to a Hopper {@link HPrincipal}. */
public class JwtClaimMapper {

  public HPrincipal toPrincipal(JWTClaimsSet claims, HSecuritySettings settings)
      throws OAuth2AuthenticationException {
    if (claims == null) {
      throw new OAuth2AuthenticationException("JWT claims are empty");
    }

    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new OAuth2AuthenticationException("JWT missing required claim 'sub'");
    }

    String username =
        firstNonBlank(
            stringFromPath(claims, settings.getUsernameClaim()),
            stringClaim(claims, "preferred_username"),
            stringClaim(claims, "name"),
            subject);

    String email =
        firstNonBlank(stringFromPath(claims, settings.getEmailClaim()), stringClaim(claims, "email"));

    Set<String> rawRoles = extractRoles(claims, settings.getRolesClaim());
    Set<String> roles =
        new LinkedHashSet<>(
            mapRoles(rawRoles, settings.getRolesClaimPrefix(), settings.getRoleAliases()));

    // Config bootstrap roles (Google has no role claims)
    if (settings.getDefaultRoles() != null) {
      for (String r : settings.getDefaultRoles()) {
        if (r != null && !r.isBlank()) {
          roles.add(r.trim().toUpperCase(Locale.ROOT));
        }
      }
    }
    if (email != null
        && settings.getAdminEmails() != null
        && settings.getAdminEmails().contains(email.trim().toLowerCase(Locale.ROOT))) {
      roles.add("ADMIN");
    }

    // Prefer email as display username when claim path yields a bare subject
    if ((username == null || username.equals(subject)) && email != null && !email.isBlank()) {
      username = email;
    }

    if (!settings.getRequiredScopes().isEmpty()) {
      Set<String> tokenScopes = parseSpaceSeparated(stringClaim(claims, "scope"));
      for (String required : settings.getRequiredScopes()) {
        if (!tokenScopes.contains(required)) {
          throw new OAuth2AuthenticationException("JWT missing required scope '" + required + "'");
        }
      }
    }

    HPrincipal.Builder builder =
        HPrincipal.builder()
            .subject(subject)
            .username(username)
            .email(email)
            .authMethod(HPrincipal.AUTH_METHOD_OAUTH2)
            .rawClaimsRoles(rawRoles)
            .roles(roles);

    String issuer = claims.getIssuer();
    if (issuer != null) {
      builder.attribute("iss", issuer);
    }
    List<String> audiences = claims.getAudience();
    if (audiences != null && !audiences.isEmpty()) {
      builder.attribute("aud", String.join(",", audiences));
    }

    return builder.build();
  }

  static Set<String> extractRoles(JWTClaimsSet claims, String rolesClaimPath) {
    Set<String> roles = new LinkedHashSet<>();
    if (rolesClaimPath == null || rolesClaimPath.isBlank()) {
      return roles;
    }

    addRolesFromValue(resolveClaimPath(claims, rolesClaimPath.trim()), roles);

    if (roles.isEmpty() && !"roles".equals(rolesClaimPath)) {
      addRolesFromValue(resolveClaimPath(claims, "roles"), roles);
    }
    if (roles.isEmpty()) {
      addRolesFromValue(resolveClaimPath(claims, "groups"), roles);
    }

    return roles;
  }

  static Object resolveClaimPath(JWTClaimsSet claims, String path) {
    if (path == null || path.isBlank() || claims == null) {
      return null;
    }
    String[] parts = path.split("\\.");
    Object current = claims.getClaim(parts[0]);
    for (int i = 1; i < parts.length && current != null; i++) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(parts[i]);
      } else {
        return null;
      }
    }
    return current;
  }

  static void addRolesFromValue(Object value, Set<String> roles) {
    if (value == null) {
      return;
    }
    if (value instanceof String s) {
      if (s.contains(" ") || s.contains(",")) {
        for (String part : s.split("[,\\s]+")) {
          if (!part.isBlank()) {
            roles.add(part.trim());
          }
        }
      } else if (!s.isBlank()) {
        roles.add(s.trim());
      }
      return;
    }
    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (item != null && !item.toString().isBlank()) {
          roles.add(item.toString().trim());
        }
      }
      return;
    }
    if (value instanceof Object[] array) {
      for (Object item : array) {
        if (item != null && !item.toString().isBlank()) {
          roles.add(item.toString().trim());
        }
      }
    }
  }

  /**
   * Applies optional claim prefix stripping, role aliases (Ship Keycloak {@code viewer} → {@code
   * VIEWER}), and uppercases simple role tokens.
   */
  static Set<String> mapRoles(Set<String> rawRoles, String prefix) {
    return mapRoles(rawRoles, prefix, Map.of());
  }

  static Set<String> mapRoles(
      Set<String> rawRoles, String prefix, Map<String, String> roleAliases) {
    Set<String> mapped = new LinkedHashSet<>();
    if (rawRoles == null) {
      return mapped;
    }
    String p = prefix == null ? "" : prefix;
    Map<String, String> aliases = roleAliases != null ? roleAliases : Map.of();
    for (String raw : rawRoles) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String role = raw.trim();
      if (!p.isEmpty() && role.regionMatches(true, 0, p, 0, p.length())) {
        role = role.substring(p.length()).trim();
      }
      if (role.isEmpty()) {
        continue;
      }
      String alias = aliases.get(role.toLowerCase(Locale.ROOT));
      if (alias != null && !alias.isBlank()) {
        mapped.add(alias.toUpperCase(Locale.ROOT));
        continue;
      }
      if (role.indexOf(' ') < 0 && role.indexOf('.') < 0) {
        mapped.add(role.toUpperCase(Locale.ROOT));
      } else {
        mapped.add(role);
      }
    }
    return mapped;
  }

  private static Set<String> parseSpaceSeparated(String value) {
    Set<String> scopes = new LinkedHashSet<>();
    if (value == null || value.isBlank()) {
      return scopes;
    }
    for (String part : value.split("\\s+")) {
      if (!part.isBlank()) {
        scopes.add(part.trim());
      }
    }
    return scopes;
  }

  private static String stringFromPath(JWTClaimsSet claims, String path) {
    Object value = resolveClaimPath(claims, path);
    return value == null ? null : value.toString();
  }

  private static String stringClaim(JWTClaimsSet claims, String name) {
    try {
      return claims.getStringClaim(name);
    } catch (ParseException e) {
      Object v = claims.getClaim(name);
      return v == null ? null : v.toString();
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
