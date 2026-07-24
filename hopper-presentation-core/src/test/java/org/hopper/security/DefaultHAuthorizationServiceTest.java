package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DefaultHAuthorizationServiceTest {

  private final HAuthorizationService authz = new DefaultHAuthorizationService();

  @AfterEach
  public void clearContext() {
    HSecurityContext.clear();
    HSecurityContext.resetAuthorizationService();
  }

  @Test
  public void viewerAllowedAndDeniedMatrix() {
    HPrincipal viewer =
        HPrincipal.builder()
            .username("v")
            .role(HRole.VIEWER.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();

    assertTrue(authz.can(viewer, HAction.PRESENTATION_RENDER));
    assertTrue(authz.can(viewer, HAction.PRESENTATION_RENDER, HResourceRef.presentation("Sales")));
    assertFalse(authz.can(viewer, HAction.PRESENTATION_UPDATE));
    assertThrows(
        HAccessDeniedException.class, () -> authz.check(viewer, HAction.PRESENTATION_UPDATE));
  }

  @Test
  public void anonymousDenied() {
    assertFalse(authz.can(HPrincipal.anonymous(), HAction.PRESENTATION_LIST));
    assertFalse(authz.can(null, HAction.PRESENTATION_LIST));
  }

  @Test
  public void systemAllowedEverything() {
    assertTrue(authz.can(HPrincipal.system(), HAction.SECURITY_ADMIN));
    assertTrue(authz.can(HPrincipal.system(), HAction.CONNECTION_DELETE));
  }

  @Test
  public void multiRoleUnionsGrants() {
    HPrincipal hybrid =
        HPrincipal.builder()
            .username("hybrid")
            .role(HRole.VIEWER.roleName())
            .role(HRole.AUDITOR.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();
    assertTrue(authz.can(hybrid, HAction.PRESENTATION_RENDER));
    assertTrue(authz.can(hybrid, HAction.AUDIT_READ));
    assertFalse(authz.can(hybrid, HAction.PRESENTATION_UPDATE));
  }

  @Test
  public void securityContextCheckCurrent() throws Exception {
    HPrincipal author =
        HPrincipal.builder()
            .username("a")
            .role(HRole.AUTHOR.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();
    HSecurityContext.setPrincipal(author);
    HSecurityContext.getAuthorizationService().checkCurrent(HAction.PRESENTATION_UPDATE);
    assertThrows(
        HAccessDeniedException.class,
        () -> HSecurityContext.getAuthorizationService().checkCurrent(HAction.CONNECTION_CREATE));
  }
}
