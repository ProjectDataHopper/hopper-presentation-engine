package org.hopper.rest.resources;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.ws.rs.core.Response;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;
import org.hopper.rest.HRest;

class AiPictorialGeneratorResourceTest {

  @TempDir
  File tempMetadataDir;

  private AiPictorialGeneratorResource resource;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    System.setProperty("HOPPER_METADATA_PATH", tempMetadataDir.getAbsolutePath());
    resource = new AiPictorialGeneratorResource();
  }

  @Test
  void testGenerateStepImages() {
    Map<String, Object> body = new HashMap<>();
    body.put("presentationName", "BeerDashboard");
    body.put("componentName", "BeerGlass");
    body.put("prompt", "A tall glass of beer filled to {percentage}%");
    body.put("renderMode", "STEP_IMAGES");
    body.put("stepSize", 50);

    Response response = resource.generatePictorialAssets(body);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    String entityJson = response.getEntity().toString();
    assertTrue(entityJson.contains("\"ok\":true"));
    assertTrue(entityJson.contains("STEP_IMAGES"));
    assertTrue(entityJson.contains("step_0.jpg") || entityJson.contains("step_0.jpeg"));
    assertTrue(entityJson.contains("step_50.jpg") || entityJson.contains("step_50.jpeg"));
    assertTrue(entityJson.contains("step_100.jpg") || entityJson.contains("step_100.jpeg"));

    File assetDir = new File(tempMetadataDir, "assets/BeerDashboard/BeerGlass");
    assertTrue(assetDir.exists());
    assertTrue(
        new File(assetDir, "step_50.jpg").exists(),
        "expected compact JPEG step asset under " + assetDir);
    assertTrue(new File(assetDir, "step_50.jpg").length() < 100_000);
  }

  @Test
  void testGenerateClippedLayers() {
    Map<String, Object> body = new HashMap<>();
    body.put("presentationName", "BatteryReport");
    body.put("componentName", "BatteryGauge");
    body.put("prompt", "A futuristic battery icon");
    body.put("renderMode", "CLIPPED_LAYERS");

    Response response = resource.generatePictorialAssets(body);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    String entityJson = response.getEntity().toString();
    assertTrue(entityJson.contains("\"ok\":true"));
    assertTrue(entityJson.contains("CLIPPED_LAYERS"));
    assertTrue(entityJson.contains("bg_empty.png"));
    assertTrue(entityJson.contains("fill_full.png"));

    File assetDir = new File(tempMetadataDir, "assets/BatteryReport/BatteryGauge");
    assertTrue(assetDir.exists());
    assertTrue(new File(assetDir, "bg_empty.png").exists());
    assertTrue(new File(assetDir, "fill_full.png").exists());
  }
}
