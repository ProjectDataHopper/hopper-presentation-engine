package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class HBuiltInRolesTest {

  @Test
  public void viewerCanRenderButNotMutate() {
    Set<HAction> actions = HBuiltInRoles.actionsFor(HRole.VIEWER);
    assertTrue(actions.contains(HAction.PRESENTATION_RENDER));
    assertTrue(actions.contains(HAction.PRESENTATION_READ));
    assertTrue(actions.contains(HAction.CONNECTION_USE));
    assertFalse(actions.contains(HAction.PRESENTATION_UPDATE));
    assertFalse(actions.contains(HAction.CONNECTOR_CREATE));
    assertFalse(actions.contains(HAction.SECURITY_ADMIN));
  }

  @Test
  public void authorCanEditPresentationButNotConnections() {
    Set<HAction> actions = HBuiltInRoles.actionsFor(HRole.AUTHOR);
    assertTrue(actions.contains(HAction.PRESENTATION_UPDATE));
    assertTrue(actions.contains(HAction.COMPONENT_CREATE));
    assertTrue(actions.contains(HAction.CONNECTOR_PREVIEW));
    assertFalse(actions.contains(HAction.CONNECTOR_CREATE));
    assertFalse(actions.contains(HAction.CONNECTION_CREATE));
  }

  @Test
  public void dataEngineerCanManageConnectorsAndConnections() {
    Set<HAction> actions = HBuiltInRoles.actionsFor(HRole.DATA_ENGINEER);
    assertTrue(actions.contains(HAction.CONNECTOR_CREATE));
    assertTrue(actions.contains(HAction.CONNECTION_UPDATE));
    assertTrue(actions.contains(HAction.PRESENTATION_DELETE));
    assertFalse(actions.contains(HAction.SECURITY_ADMIN));
  }

  @Test
  public void adminHasAllActions() {
    Set<HAction> actions = HBuiltInRoles.actionsFor(HRole.ADMIN);
    for (HAction action : HAction.values()) {
      assertTrue(actions.contains(action), "ADMIN missing " + action.code());
    }
  }

  @Test
  public void auditorIsReadOnlyPlusAudit() {
    Set<HAction> actions = HBuiltInRoles.actionsFor(HRole.AUDITOR);
    assertTrue(actions.contains(HAction.AUDIT_READ));
    assertTrue(actions.contains(HAction.PRESENTATION_READ));
    assertFalse(actions.contains(HAction.PRESENTATION_RENDER));
    assertFalse(actions.contains(HAction.PRESENTATION_UPDATE));
  }

  @Test
  public void roleNameLookupIsCaseInsensitive() {
    Set<HAction> lower = HBuiltInRoles.actionsForRoleName("viewer");
    Set<HAction> upper = HBuiltInRoles.actionsForRoleName("VIEWER");
    assertTrue(lower.contains(HAction.PRESENTATION_RENDER));
    assertTrue(upper.contains(HAction.PRESENTATION_RENDER));
  }
}
