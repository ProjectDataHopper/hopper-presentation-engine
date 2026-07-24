package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultHAuthorizationServiceCustomRoleTest {

  @AfterEach
  void reset() {
    HSecurityContext.clear();
    HSecurityContext.resetAuthorizationService();
  }

  @Test
  void aclAllowWithCustomRolePrincipal() {
    MemoryRoles roles = new MemoryRoles();
    HSecurityRole r = new HSecurityRole("EDITOR_LITE");
    r.setActions(List.of("presentation.read"));
    roles.put(r);

    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("PRESENTATION", "Board");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.ROLE,
                "EDITOR_LITE",
                List.of("presentation.update"),
                HAclEffect.ALLOW));
    acls.put(acl);

    DefaultHAuthorizationService authz =
        new DefaultHAuthorizationService(acls, false, new DefaultHRoleGrantResolver(roles));

    HPrincipal p =
        HPrincipal.builder()
            .subject("u1")
            .username("u1")
            .role("EDITOR_LITE")
            .authMethod(HPrincipal.AUTH_METHOD_OAUTH2)
            .build();

    // Global role does not allow update
    assertFalse(authz.can(p, HAction.PRESENTATION_UPDATE));
    // ACL allows update on Board for that role
    assertTrue(authz.can(p, HAction.PRESENTATION_UPDATE, HResourceRef.presentation("Board")));
    assertTrue(authz.can(p, HAction.PRESENTATION_READ, HResourceRef.presentation("Other")));
  }

  static final class MemoryRoles implements HCustomRoleSource {
    private final Map<String, HSecurityRole> byName = new ConcurrentHashMap<>();

    void put(HSecurityRole role) {
      byName.put(HSecurityRole.normalizeName(role.getName()), role);
    }

    @Override
    public Optional<HSecurityRole> find(String roleName) {
      return Optional.ofNullable(byName.get(HSecurityRole.normalizeName(roleName)));
    }

    @Override
    public List<HSecurityRole> listAll() {
      return List.copyOf(byName.values());
    }
  }
}
