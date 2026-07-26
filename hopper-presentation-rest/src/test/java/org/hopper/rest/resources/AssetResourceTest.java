package org.hopper.rest.resources;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.ws.rs.core.Response;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;
import org.hopper.rest.HRest;

class AssetResourceTest {

  @TempDir
  File tempMetadataDir;

  private AssetResource resource;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    System.setProperty("HOPPER_METADATA_PATH", tempMetadataDir.getAbsolutePath());
    resource = new AssetResource();
  }

  @Test
  void testGetAssetNotFound() {
    Response response = resource.getAsset("DemoPres", "missing.png");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  void testGetAssetSuccess() throws Exception {
    File assetsDir = new File(tempMetadataDir, "assets/DemoPres");
    assetsDir.mkdirs();
    File samplePng = new File(assetsDir, "test.png");
    Files.writeString(samplePng.toPath(), "fake-png-content");

    Response response = resource.getAsset("DemoPres", "test.png");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals("image/png", response.getMediaType().toString());
  }
}
