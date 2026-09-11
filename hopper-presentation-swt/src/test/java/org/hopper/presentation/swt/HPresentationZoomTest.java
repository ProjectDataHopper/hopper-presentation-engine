package org.hopper.presentation.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HPresentationZoomTest {

  @Test
  void toolbarItemsRegisterForLibJarViewer() {
    org.hopper.core.plugin.HPluginIndexSupport.registerGuiElements(HPresentationViewer.class);
    assertFalse(
        org.apache.hop.core.gui.plugin.GuiRegistry.getInstance()
            .findGuiToolbarItems(HPresentationViewer.GUI_PLUGIN_TOOLBAR_PARENT_ID)
            .isEmpty());
  }

  @Test
  void fitPageUsesLimitingDimension() {
    float zoom = HPresentationZoom.compute(HPresentationZoom.PAGE, 1000, 800, 1200, 240, 1f);
    assertEquals((1000f - HPresentationZoom.MARGIN) / 1200f, zoom, 0.001f);
    assertTrue(zoom < 1f);
  }

  @Test
  void fitWidthScalesToCanvasWidth() {
    float zoom = HPresentationZoom.compute(HPresentationZoom.WIDTH, 800, 600, 400, 400, 1f);
    assertEquals((800f - HPresentationZoom.MARGIN) / 400f, zoom, 0.001f);
  }

  @Test
  void fitHeightScalesToCanvasHeight() {
    float zoom = HPresentationZoom.compute(HPresentationZoom.HEIGHT, 800, 600, 400, 200, 1f);
    assertEquals((600f - HPresentationZoom.MARGIN) / 200f, zoom, 0.001f);
  }

  @Test
  void actualIsOne() {
    assertEquals(1f, HPresentationZoom.compute(HPresentationZoom.ACTUAL, 800, 600, 400, 200, 3f));
  }

  @Test
  void manualKeepsCurrent() {
    assertEquals(
        1.5f, HPresentationZoom.compute(HPresentationZoom.MANUAL, 800, 600, 400, 200, 1.5f));
  }

  @Test
  void zeroCanvasKeepsCurrent() {
    assertEquals(1.2f, HPresentationZoom.compute(HPresentationZoom.PAGE, 0, 0, 400, 200, 1.2f), 0.01f);
  }

  @Test
  void labels() {
    assertEquals("Fit page", HPresentationZoom.PAGE.label(0.5f));
    assertEquals("125%", HPresentationZoom.MANUAL.label(1.25f));
  }
}
