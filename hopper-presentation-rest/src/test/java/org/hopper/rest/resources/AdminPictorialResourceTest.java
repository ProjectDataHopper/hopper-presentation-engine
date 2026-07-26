package org.hopper.rest.resources;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.ws.rs.core.Response;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;

class AdminPictorialResourceTest {

  @TempDir
  File tempMetadataDir;

  private AdminPictorialResource resource;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    System.setProperty("HOPPER_METADATA_PATH", tempMetadataDir.getAbsolutePath());
    resource = new AdminPictorialResource();
  }

  @Test
  void testListAssetsEmpty() {
    Response response = resource.listAssets();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    String json = response.getEntity().toString();
    assertTrue(json.contains("\"ok\":true"));
  }

  @Test
  void testListAssetsWithFiles() throws Exception {
    File compDir = new File(tempMetadataDir, "assets/PresA/CompA");
    compDir.mkdirs();
    File f1 = new File(compDir, "step_0.png");
    Files.writeString(f1.toPath(), "test");

    Response response = resource.listAssets();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    String json = response.getEntity().toString();
    assertTrue(json.contains("PresA"));
    assertTrue(json.contains("CompA"));
    assertTrue(json.contains("step_0.png"));
  }

  @Test
  void testAdminGenerate() {
    Map<String, Object> body = new HashMap<>();
    body.put("presentationName", "AdminPres");
    body.put("componentName", "AdminGauge");
    body.put("prompt", "A vessel full");
    body.put("renderMode", "STEP_IMAGES");

    Response response = resource.generate(body);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    String json = response.getEntity().toString();
    assertTrue(json.contains("\"ok\":true"));
    assertTrue(json.contains("AdminPres"));
    // Paths stored for server-side render use metadata variable, not browser URLs
    assertTrue(json.contains("${HOPPER_METADATA_PATH}/assets/AdminPres/AdminGauge"));
    assertFalse(json.contains("/hopper/api/assets/AdminPres"));
  }

  @Test
  void testGetSettings() {
    Response response = resource.getSettings();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    String json = response.getEntity().toString();
    assertTrue(json.contains("providerType"));
  }

  @Test
  void testSaveAndTestConnection() {
    Map<String, Object> body = new HashMap<>();
    body.put("providerType", "BUILTIN");
    body.put("rawApiKey", "");
    body.put("modelName", "");

    Response saveResp = resource.saveSettings(body);
    assertEquals(Response.Status.OK.getStatusCode(), saveResp.getStatus());

    Response testResp = resource.testConnection(body);
    assertEquals(Response.Status.OK.getStatusCode(), testResp.getStatus());
    String json = testResp.getEntity().toString();
    assertTrue(json.contains("BUILTIN") || json.contains("Built-in") || json.contains("\"ok\":true"));
  }

  @Test
  void testConnectionMissingKeyReportsFailure() {
    Map<String, Object> body = new HashMap<>();
    body.put("providerType", "XAI_GROK");
    body.put("rawApiKey", "");
    // Ensure no leftover key from other tests in temp dir
    Response testResp = resource.testConnection(body);
    assertEquals(Response.Status.OK.getStatusCode(), testResp.getStatus());
    String json = testResp.getEntity().toString();
    // ok:false after live check (empty key) — entity may be ObjectNode or string
    assertTrue(
        json.contains("\"ok\":false") || json.contains("empty") || json.contains("API key"),
        json);
  }
}
