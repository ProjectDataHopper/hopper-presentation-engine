package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class HPresentationViewerExportTest {

  @Test
  void withExtensionAddsMissingSuffix() {
    assertEquals("chart.svg", HPresentationViewerSupport.withExtension("chart", ".svg"));
    assertEquals("chart.SVG", HPresentationViewerSupport.withExtension("chart.SVG", ".svg"));
    assertEquals("out.pdf", HPresentationViewerSupport.withExtension("out", ".pdf"));
    assertEquals("out.pdf", HPresentationViewerSupport.withExtension("out.pdf", ".pdf"));
  }

  @Test
  void resolveExportFilenameExpandsProjectHome() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/data/project");
    assertEquals(
        "/data/project/test/gantt.pdf",
        HPresentationViewerSupport.resolveExportFilename(
            variables, "${PROJECT_HOME}/test/gantt.pdf"));
    assertEquals(
        "/tmp/out.svg",
        HPresentationViewerSupport.resolveExportFilename(variables, "/tmp/out.svg"));
    assertEquals(
        "${PROJECT_HOME}/x.pdf",
        HPresentationViewerSupport.resolveExportFilename(null, "${PROJECT_HOME}/x.pdf"));
  }

  @Test
  void safeDownloadFilenameUsesBasenameAndExtension() {
    assertEquals(
        "workflow-gantt.pdf",
        HPresentationViewerSupport.safeDownloadFilename("workflow-gantt", ".pdf"));
    assertEquals("chart.svg", HPresentationViewerSupport.safeDownloadFilename("chart", ".svg"));
    assertEquals(
        "gantt.pdf",
        HPresentationViewerSupport.safeDownloadFilename("${PROJECT_HOME}/test/gantt", ".pdf"));
    assertEquals("presentation.pdf", HPresentationViewerSupport.safeDownloadFilename("..", ".pdf"));
  }

  @Test
  void proposedExportPathPrefersProjectHome() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/data/project");
    variables.setVariable("user.home", "/home/matt");
    assertEquals(
        "/data/project/gantt.pdf",
        HPresentationViewerSupport.proposedExportPath(variables, "gantt.pdf"));
    variables.setVariable("PROJECT_HOME", "");
    assertEquals(
        "/home/matt/gantt.pdf",
        HPresentationViewerSupport.proposedExportPath(variables, "gantt.pdf"));
  }

  @Test
  void contentTypeAndDisposition() {
    assertEquals("application/pdf", HPresentationViewerSupport.contentType(true));
    assertEquals("image/svg+xml", HPresentationViewerSupport.contentType(false));
    String header = HPresentationViewerSupport.contentDisposition("gantt.pdf");
    assertTrue(header.startsWith("attachment;"));
    assertTrue(header.contains("filename=\"gantt.pdf\""));
  }

  @Test
  void liveWebUpdateDoesNotRebuildDocumentOnceShellReady() {
    assertTrue(HPresentationViewerSupport.rebuildWebDocument(false));
    assertFalse(HPresentationViewerSupport.rebuildWebDocument(true));
  }

  @Test
  void liveWebReloadKeepsCurrentZoom() {
    assertTrue(HPresentationViewerSupport.liveReloadRecomputesZoom(false));
    assertFalse(HPresentationViewerSupport.liveReloadRecomputesZoom(true));
  }

  @Test
  void iframeOverflowOnlyWhenSlotExceedsPane() {
    assertFalse(HPresentationViewerSupport.iframeOverflow(1792, 320, 1800, 400));
    assertFalse(HPresentationViewerSupport.iframeOverflow(1800, 400, 1800, 400));
    assertTrue(HPresentationViewerSupport.iframeOverflow(1804, 320, 1800, 400));
    assertTrue(HPresentationViewerSupport.iframeOverflow(1792, 404, 1800, 400));
  }

  @Test
  void fitModeHidesOverflow() {
    assertTrue(HPresentationViewerSupport.fitModeHidesOverflow(HPresentationZoom.WIDTH));
    assertTrue(HPresentationViewerSupport.fitModeHidesOverflow(HPresentationZoom.HEIGHT));
    assertTrue(HPresentationViewerSupport.fitModeHidesOverflow(HPresentationZoom.PAGE));
    assertFalse(HPresentationViewerSupport.fitModeHidesOverflow(HPresentationZoom.ACTUAL));
    assertFalse(HPresentationViewerSupport.fitModeHidesOverflow(HPresentationZoom.MANUAL));
  }

  @Test
  void toolbarTextUnchangedIgnoresNull() {
    assertTrue(HPresentationViewerSupport.toolbarTextUnchanged("Fit width", "Fit width"));
    assertTrue(HPresentationViewerSupport.toolbarTextUnchanged(null, ""));
    assertFalse(HPresentationViewerSupport.toolbarTextUnchanged("Fit width", "Fit page"));
  }

  @Test
  void parseRefreshRateMs() {
    assertEquals(0, HPresentationViewerSupport.parseRefreshRateMs("Paused"));
    assertEquals(0, HPresentationViewerSupport.parseRefreshRateMs("pauzed"));
    assertEquals(0, HPresentationViewerSupport.parseRefreshRateMs(""));
    assertEquals(1000, HPresentationViewerSupport.parseRefreshRateMs("1s"));
    assertEquals(2000, HPresentationViewerSupport.parseRefreshRateMs("2s"));
    assertEquals(5000, HPresentationViewerSupport.parseRefreshRateMs("5s"));
    assertEquals(10000, HPresentationViewerSupport.parseRefreshRateMs("10s"));
    assertEquals(15000, HPresentationViewerSupport.parseRefreshRateMs("15s"));
    assertEquals(30000, HPresentationViewerSupport.parseRefreshRateMs("30s"));
  }
}
