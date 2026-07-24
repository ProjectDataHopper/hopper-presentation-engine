package org.hopper.security;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Merges Hopper-side user role assignments into an authenticated principal (additive).
 *
 * <p>If the matching {@link HSecurityUser} is {@code disabled}, returns an anonymous-equivalent
 * denial principal (no roles except preserved identity for audit) — actually we return the principal
 * with a flag... simpler: strip to no roles + attribute disabled=true so authz denies.
 */
public final class HPrincipalEnricher {

  private final HUserAssignmentSource userSource;

  public HPrincipalEnricher(HUserAssignmentSource userSource) {
    this.userSource = userSource != null ? userSource : HUserAssignmentSource.NONE;
  }

  public HUserAssignmentSource getUserSource() {
    return userSource;
  }

  /**
   * Return a principal with assignment roles merged. Identity fields unchanged. No-op for null,
   * anonymous, or system principals.
   */
  public HPrincipal enrich(HPrincipal principal) {
    if (principal == null || principal.isAnonymous() || principal.isSystem()) {
      return principal;
    }
    if (userSource == HUserAssignmentSource.NONE) {
      return principal;
    }

    Optional<HSecurityUser> match = findAssignment(principal);
    if (match.isEmpty()) {
      return principal;
    }
    HSecurityUser user = match.get();
    if (user.isDisabled()) {
      // Keep identity for audit; clear effective roles so authz denies data actions
      return HPrincipal.builder()
          .subject(principal.getSubject())
          .username(principal.getUsername())
          .email(principal.getEmail())
          .authMethod(principal.getAuthMethod())
          .rawClaimsRoles(principal.getRawClaimsRoles())
          .attribute("disabled", "true")
          .attribute("assignment", user.getName())
          .build();
    }

    Set<String> roles = new LinkedHashSet<>(principal.getRoles());
    if (user.getRoles() != null) {
      for (String r : user.getRoles()) {
        if (r != null && !r.isBlank()) {
          roles.add(r.trim().toUpperCase(Locale.ROOT));
        }
      }
    }

    HPrincipal.Builder b =
        HPrincipal.builder()
            .subject(principal.getSubject())
            .username(principal.getUsername())
            .email(
                firstNonBlank(
                    principal.getEmail(),
                    user.getEmail(),
                    user.getName() != null && user.getName().contains("@") ? user.getName() : null))
            .authMethod(principal.getAuthMethod())
            .rawClaimsRoles(principal.getRawClaimsRoles())
            .roles(roles)
            .attribute("assignment", user.getName() != null ? user.getName() : "");

    if (principal.getAttributes() != null) {
      principal.getAttributes().forEach(b::attribute);
    }
    if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
      b.attribute("displayName", user.getDisplayName());
    }
    return b.build();
  }

  public Optional<HSecurityUser> findAssignment(HPrincipal principal) {
    if (principal == null) {
      return Optional.empty();
    }
    if (principal.getEmail() != null && !principal.getEmail().isBlank()) {
      Optional<HSecurityUser> byEmail = userSource.findByEmail(principal.getEmail());
      if (byEmail.isPresent()) {
        return byEmail;
      }
    }
    if (principal.getUsername() != null && principal.getUsername().contains("@")) {
      Optional<HSecurityUser> byUser = userSource.findByEmail(principal.getUsername());
      if (byUser.isPresent()) {
        return byUser;
      }
    }
    if (principal.getSubject() != null && !principal.getSubject().isBlank()) {
      return userSource.findBySubject(principal.getSubject());
    }
    return Optional.empty();
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
