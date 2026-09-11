package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void liveWebUpdateDoesNotRebuildDocumentOnceShellReady() {
    assertTrue(HPresentationViewerSupport.rebuildWebDocument(false));
    assertFalse(HPresentationViewerSupport.rebuildWebDocument(true));
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
