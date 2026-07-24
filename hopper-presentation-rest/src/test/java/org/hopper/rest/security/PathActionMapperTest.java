package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.hopper.security.HAction;
import org.junit.jupiter.api.Test;

public class PathActionMapperTest {

  private final PathActionMapper mapper = new PathActionMapper();

  @Test
  public void renderPresentationRequiresRenderAction() {
    assertEquals(
        Optional.of(HAction.PRESENTATION_RENDER),
        mapper.requiredAction("POST", "render/presentation"));
  }

  @Test
  public void editMutationsRequireUpdate() {
    assertEquals(
        Optional.of(HAction.PRESENTATION_UPDATE),
        mapper.requiredAction("POST", "edit/presentation/Sales/components/t1/nudge/"));
    assertEquals(
        Optional.of(HAction.PRESENTATION_CREATE),
        mapper.requiredAction("POST", "edit/presentation/create/"));
  }

  @Test
  public void metadataMapsByFamily() {
    assertEquals(
        Optional.of(HAction.PRESENTATION_LIST),
        mapper.requiredAction("GET", "metadata/list/presentation/"));
    assertEquals(
        Optional.of(HAction.CONNECTOR_CREATE),
        mapper.requiredAction("POST", "metadata/connector/"));
    assertEquals(
        Optional.of(HAction.CONNECTION_DELETE),
        mapper.requiredAction("DELETE", "metadata/hopper-database-connection/prod"));
  }

  @Test
  public void staticAndOptionsExempt() {
    assertTrue(mapper.requiredAction("GET", "static/edit/edit-presentation.html").isEmpty());
    assertTrue(mapper.requiredAction("OPTIONS", "render/presentation").isEmpty());
  }

  @Test
  public void mainPageIsList() {
    assertEquals(
        Optional.of(HAction.PRESENTATION_LIST), mapper.requiredAction("GET", "render/main/"));
  }

  @Test
  public void resourceRefFromEditPath() {
    var ref = mapper.resourceRef("POST", "edit/presentation/Sales%20Dash/components/t1/nudge/");
    assertTrue(ref.isPresent());
    assertEquals("Sales Dash", ref.get().getName());
    assertEquals(org.hopper.security.HResourceType.PRESENTATION, ref.get().getType());
  }

  @Test
  public void resourceRefFromMetadataPath() {
    var ref = mapper.resourceRef("GET", "metadata/connector/inventory-sql");
    assertTrue(ref.isPresent());
    assertEquals("inventory-sql", ref.get().getName());
    assertEquals(org.hopper.security.HResourceType.CONNECTOR, ref.get().getType());
  }

  @Test
  public void securityAclsRequireAdminAction() {
    assertEquals(
        Optional.of(HAction.SECURITY_ADMIN), mapper.requiredAction("GET", "security/acls/"));
  }

  @Test
  public void adminSettingsRequireSecurityAdmin() {
    assertEquals(
        Optional.of(HAction.SECURITY_ADMIN),
        mapper.requiredAction("GET", "admin/settings"));
    assertEquals(
        Optional.of(HAction.SECURITY_ADMIN),
        mapper.requiredAction("POST", "admin/settings/apply"));
  }

  @Test
  public void adminUsageRequiresAuditRead() {
    assertEquals(
        Optional.of(HAction.AUDIT_READ), mapper.requiredAction("GET", "admin/usage/active"));
  }

  @Test
  public void adminOAuthRequiresSecurityAdmin() {
    assertEquals(
        Optional.of(HAction.SECURITY_ADMIN),
        mapper.requiredAction("GET", "admin/oauth/presets"));
    assertEquals(
        Optional.of(HAction.SECURITY_ADMIN),
        mapper.requiredAction("POST", "admin/oauth/apply"));
  }
}
