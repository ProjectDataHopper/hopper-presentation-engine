package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HPresentationViewerExportTest {

  @Test
  void withExtensionAddsMissingSuffix() {
    assertEquals("chart.svg", HPresentationViewer.withExtension("chart", ".svg"));
    assertEquals("chart.SVG", HPresentationViewer.withExtension("chart.SVG", ".svg"));
    assertEquals("out.pdf", HPresentationViewer.withExtension("out", ".pdf"));
    assertEquals("out.pdf", HPresentationViewer.withExtension("out.pdf", ".pdf"));
  }

  @Test
  void parseRefreshRateMs() {
    assertEquals(0, HPresentationViewer.parseRefreshRateMs("Paused"));
    assertEquals(0, HPresentationViewer.parseRefreshRateMs("pauzed"));
    assertEquals(0, HPresentationViewer.parseRefreshRateMs(""));
    assertEquals(1000, HPresentationViewer.parseRefreshRateMs("1s"));
    assertEquals(2000, HPresentationViewer.parseRefreshRateMs("2s"));
    assertEquals(5000, HPresentationViewer.parseRefreshRateMs("5s"));
    assertEquals(10000, HPresentationViewer.parseRefreshRateMs("10s"));
    assertEquals(15000, HPresentationViewer.parseRefreshRateMs("15s"));
    assertEquals(30000, HPresentationViewer.parseRefreshRateMs("30s"));
  }
}
