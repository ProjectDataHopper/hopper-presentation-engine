package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One ACL rule: grant or deny a set of actions to a user or role on the parent {@link
 * HSecurityAcl}'s resource.
 */
@Getter
@Setter
@NoArgsConstructor
public class HAclEntry {

  @HopMetadataProperty private String principalType = HAclPrincipalType.ROLE.name();

  /** Username or role name (case-insensitive match). */
  @HopMetadataProperty private String principal;

  /** Action codes, e.g. {@code presentation.render}. Empty = no actions. */
  @HopMetadataProperty private List<String> actions = new ArrayList<>();

  @HopMetadataProperty private String effect = HAclEffect.ALLOW.name();

  public HAclEntry(
      HAclPrincipalType principalType,
      String principal,
      List<String> actions,
      HAclEffect effect) {
    this.principalType = principalType != null ? principalType.name() : HAclPrincipalType.ROLE.name();
    this.principal = principal;
    this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    this.effect = effect != null ? effect.name() : HAclEffect.ALLOW.name();
  }

  public HAclPrincipalType principalTypeEnum() {
    return HAclPrincipalType.fromString(principalType).orElse(HAclPrincipalType.ROLE);
  }

  public HAclEffect effectEnum() {
    return HAclEffect.fromString(effect).orElse(HAclEffect.ALLOW);
  }

  public boolean coversAction(HAction action) {
    if (action == null || actions == null || actions.isEmpty()) {
      return false;
    }
    String code = action.code();
    for (String a : actions) {
      if (a == null || a.isBlank()) {
        continue;
      }
      if ("*".equals(a) || a.equalsIgnoreCase(code)) {
        return true;
      }
      // family wildcard: presentation.*
      if (a.endsWith(".*")) {
        String prefix = a.substring(0, a.length() - 1); // "presentation."
        if (code.regionMatches(true, 0, prefix, 0, prefix.length())) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean matchesPrincipal(HPrincipal principal) {
    if (principal == null || this.principal == null || this.principal.isBlank()) {
      return false;
    }
    HAclPrincipalType type = principalTypeEnum();
    if (type == HAclPrincipalType.USER) {
      return this.principal.equalsIgnoreCase(principal.getUsername())
          || this.principal.equalsIgnoreCase(principal.getSubject());
    }
    // ROLE
    return principal.hasRole(this.principal);
  }
}
