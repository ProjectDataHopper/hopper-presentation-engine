package org.hopper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HPresentationDataPathsTest {

  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    HPresentationDataPaths.resetToDefaults();
    System.clearProperty(HPresentationDataPaths.ENV_DATA_PATH);
  }

  @Test
  void applyFromProperties_setsRootAndFlags() {
    Properties p = new Properties();
    p.setProperty(HPresentationDataPaths.KEY_DATA_PATH, tempDir.toString());
    p.setProperty(HPresentationDataPaths.KEY_TIMINGS_CAPTURE, "true");
    p.setProperty(HPresentationDataPaths.KEY_TOOLBAR_TIMINGS_VISIBLE, "false");
    HPresentationDataPaths.applyFromProperties(p);
    assertTrue(HPresentationDataPaths.isConfigured());
    assertTrue(HPresentationDataPaths.getRoot().contains(tempDir.getFileName().toString()));
    assertTrue(HPresentationDataPaths.isTimingsCapture());
    assertFalse(HPresentationDataPaths.isToolbarTimingsVisible());
  }

  @Test
  void safeName_stripsTraversal() {
    assertEquals("_", HPresentationDataPaths.safeName(""));
    assertTrue(HPresentationDataPaths.safeName("../etc/passwd").contains("passwd"));
    assertFalse(HPresentationDataPaths.safeName("../etc/passwd").contains(".."));
  }

  @Test
  void timingsLatestFile_underRoot() {
    HPresentationDataPaths.setForTests(tempDir.toString(), true, true, true);
    String f = HPresentationDataPaths.timingsLatestFile("My Presentation");
    assertTrue(f.endsWith("latest.hoprows"));
    assertTrue(f.contains("timings"));
    assertTrue(f.contains("My Presentation") || f.contains("My_Presentation") || f.contains("My Presentation".replace(' ', ' ')));
  }

  @Test
  void defaultBesideMetadata() {
    String d = HPresentationDataPaths.defaultBesideMetadata("/var/hopper/metadata");
    assertTrue(d.endsWith("data") || d.contains("/data"));
  }
}
