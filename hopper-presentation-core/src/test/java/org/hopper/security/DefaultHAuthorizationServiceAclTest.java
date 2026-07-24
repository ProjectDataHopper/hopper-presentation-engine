package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DefaultHAuthorizationServiceAclTest {

  @AfterEach
  void reset() {
    HSecurityContext.clear();
    HSecurityContext.resetAuthorizationService();
  }

  @Test
  void roleAllowsWhenNoAcl() {
    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(HAclProvider.none(), false);
    HPrincipal viewer = viewer();
    assertTrue(
        authz.can(viewer, HAction.PRESENTATION_RENDER, HResourceRef.presentation("Public")));
    assertFalse(
        authz.can(viewer, HAction.PRESENTATION_UPDATE, HResourceRef.presentation("Public")));
  }

  @Test
  void explicitDenyBlocksEvenWhenRoleAllows() {
    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("PRESENTATION", "HR Salary");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.ROLE,
                "VIEWER",
                List.of("presentation.render"),
                HAclEffect.DENY));
    acls.put(acl);

    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(acls, false);
    assertFalse(
        authz.can(viewer(), HAction.PRESENTATION_RENDER, HResourceRef.presentation("HR Salary")));
    // Other presentations still OK
    assertTrue(
        authz.can(viewer(), HAction.PRESENTATION_RENDER, HResourceRef.presentation("Public")));
  }

  @Test
  void explicitAllowGrantsBeyondRole() {
    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("PRESENTATION", "Sales");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.USER,
                "alice",
                List.of("presentation.update"),
                HAclEffect.ALLOW));
    acls.put(acl);

    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(acls, false);
    HPrincipal aliceViewer =
        HPrincipal.builder()
            .username("alice")
            .role(HRole.VIEWER.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();
    // VIEWER cannot update globally, but ACL allows alice on Sales
    assertTrue(
        authz.can(aliceViewer, HAction.PRESENTATION_UPDATE, HResourceRef.presentation("Sales")));
    assertFalse(
        authz.can(aliceViewer, HAction.PRESENTATION_UPDATE, HResourceRef.presentation("Other")));
  }

  @Test
  void defaultDenyRequiresExplicitAllow() {
    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("PRESENTATION", "OpenBoard");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.ROLE,
                "VIEWER",
                List.of("presentation.render"),
                HAclEffect.ALLOW));
    acls.put(acl);

    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(acls, true);
    assertTrue(
        authz.can(viewer(), HAction.PRESENTATION_RENDER, HResourceRef.presentation("OpenBoard")));
    assertFalse(
        authz.can(viewer(), HAction.PRESENTATION_RENDER, HResourceRef.presentation("NoAclDoc")));
  }

  @Test
  void adminBypassesAcl() {
    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("PRESENTATION", "Secret");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.ROLE,
                "VIEWER",
                List.of("presentation.render"),
                HAclEffect.DENY));
    acls.put(acl);

    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(acls, true);
    HPrincipal admin =
        HPrincipal.builder()
            .username("root")
            .role(HRole.ADMIN.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();
    assertTrue(authz.can(admin, HAction.PRESENTATION_RENDER, HResourceRef.presentation("Secret")));
  }

  @Test
  void connectionUseHonoursAcl() {
    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("CONNECTION", "hr_db");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.ROLE,
                "VIEWER",
                List.of("connection.use"),
                HAclEffect.DENY));
    acls.put(acl);

    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(acls, false);
    assertFalse(authz.can(viewer(), HAction.CONNECTION_USE, HResourceRef.connection("hr_db")));
    assertTrue(authz.can(viewer(), HAction.CONNECTION_USE, HResourceRef.connection("public_db")));
  }

  @Test
  void wildcardActionOnEntry() {
    MemoryHAclProvider acls = new MemoryHAclProvider();
    HSecurityAcl acl = new HSecurityAcl("PRESENTATION", "AllOps");
    acl.getEntries()
        .add(
            new HAclEntry(
                HAclPrincipalType.USER,
                "bob",
                List.of("presentation.*"),
                HAclEffect.ALLOW));
    acls.put(acl);

    DefaultHAuthorizationService authz = new DefaultHAuthorizationService(acls, false);
    HPrincipal bob =
        HPrincipal.builder()
            .username("bob")
            .role(HRole.VIEWER.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();
    assertTrue(authz.can(bob, HAction.PRESENTATION_DELETE, HResourceRef.presentation("AllOps")));
  }

  private static HPrincipal viewer() {
    return HPrincipal.builder()
        .username("v")
        .role(HRole.VIEWER.roleName())
        .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
        .build();
  }
}
