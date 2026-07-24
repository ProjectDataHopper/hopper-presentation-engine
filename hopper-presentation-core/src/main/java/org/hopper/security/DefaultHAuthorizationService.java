package org.hopper.security;

/**
 * Role-based authorization with optional resource ACLs.
 *
 * <p>Evaluation order for a resource-scoped check:
 *
 * <ol>
 *   <li>Anonymous / missing principal → deny
 *   <li>System principal or {@code ADMIN} role → allow
 *   <li>Matching ACL {@link HAclEffect#DENY} → deny
 *   <li>Matching ACL {@link HAclEffect#ALLOW} → allow
 *   <li>If {@code defaultDenyResources} and a resource is named → deny (no explicit allow)
 *   <li>Otherwise fall back to global role → action grants
 * </ol>
 */
public class DefaultHAuthorizationService implements HAuthorizationService {

  private final HAclProvider aclProvider;
  private final boolean defaultDenyResources;
  private final HRoleGrantResolver roleGrantResolver;

  public DefaultHAuthorizationService() {
    this(HAclProvider.none(), false, HRoleGrantResolver.builtInOnly());
  }

  public DefaultHAuthorizationService(HAclProvider aclProvider, boolean defaultDenyResources) {
    this(aclProvider, defaultDenyResources, HRoleGrantResolver.builtInOnly());
  }

  public DefaultHAuthorizationService(
      HAclProvider aclProvider, boolean defaultDenyResources, HRoleGrantResolver roleGrantResolver) {
    this.aclProvider = aclProvider != null ? aclProvider : HAclProvider.none();
    this.defaultDenyResources = defaultDenyResources;
    this.roleGrantResolver =
        roleGrantResolver != null ? roleGrantResolver : HRoleGrantResolver.builtInOnly();
  }

  public HAclProvider getAclProvider() {
    return aclProvider;
  }

  public boolean isDefaultDenyResources() {
    return defaultDenyResources;
  }

  public HRoleGrantResolver getRoleGrantResolver() {
    return roleGrantResolver;
  }

  @Override
  public boolean can(HPrincipal principal, HAction action) {
    return can(principal, action, null);
  }

  @Override
  public boolean can(HPrincipal principal, HAction action, HResourceRef resource) {
    if (action == null) {
      return false;
    }
    if (principal == null || principal.isAnonymous()) {
      return false;
    }
    if (principal.isSystem() || principal.hasRole(HRole.ADMIN)) {
      return true;
    }

    boolean roleAllows = roleGrantResolver.actionsForRoles(principal.getRoles()).contains(action);

    if (resource == null || resource.getName() == null || resource.getName().isBlank()) {
      return roleAllows;
    }

    HSecurityAcl acl = aclProvider.find(resource).orElse(null);
    if (acl != null && acl.getEntries() != null) {
      boolean deny = false;
      boolean allow = false;
      for (HAclEntry entry : acl.getEntries()) {
        if (entry == null || !entry.matchesPrincipal(principal) || !entry.coversAction(action)) {
          continue;
        }
        if (entry.effectEnum() == HAclEffect.DENY) {
          deny = true;
        } else if (entry.effectEnum() == HAclEffect.ALLOW) {
          allow = true;
        }
      }
      if (deny) {
        return false;
      }
      if (allow) {
        return true;
      }
    }

    if (defaultDenyResources) {
      return false;
    }
    return roleAllows;
  }

  @Override
  public void check(HPrincipal principal, HAction action) throws HAccessDeniedException {
    check(principal, action, null);
  }

  @Override
  public void check(HPrincipal principal, HAction action, HResourceRef resource)
      throws HAccessDeniedException {
    if (!can(principal, action, resource)) {
      throw new HAccessDeniedException(principal, action, resource);
    }
  }
}
