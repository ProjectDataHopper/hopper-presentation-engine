package org.hopper.presentation.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.core.HColorRGB;
import org.junit.jupiter.api.Test;

class HThemeAdaptTest {

  @Test
  void forDarkModeRaisesInkContrastOnDarkBackground() {
    HTheme light = HTheme.getDefault();
    HTheme dark = HThemeAdapt.forDarkMode(light);
    assertNotNull(dark.getBackgroundColor());
    assertNotNull(dark.getDefaultColor());
    double contrast =
        HThemeAdapt.contrastRatio(dark.getDefaultColor(), dark.getBackgroundColor());
    assertTrue(contrast >= 4.5, "ink contrast was " + contrast);
  }

  @Test
  void seriesPaletteKeepsReadableLightnessOnDark() {
    HTheme light = HTheme.getDefault();
    HTheme dark = HThemeAdapt.forDarkMode(light);
    assertNotNull(dark.getColors());
    assertTrue(dark.getColors().size() >= 4);
    for (HColorRGB c : dark.getColors()) {
      double L = HThemeAdapt.relativeLuminance(c);
      assertTrue(L > 0.15, "series too dark: " + c + " L=" + L);
      assertTrue(L < 0.95, "series too bright: " + c + " L=" + L);
    }
  }

  @Test
  void defaultDarkBuiltInHasDarkBackground() {
    HTheme dark = HTheme.getDefaultDark();
    assertTrue(HThemeAdapt.relativeLuminance(dark.getBackgroundColor()) < 0.2);
    assertTrue(
        HThemeAdapt.contrastRatio(dark.getDefaultColor(), dark.getBackgroundColor()) >= 4.5);
    // PDI assessment navy
    assertEquals(0x0b, dark.getBackgroundColor().getR());
    assertEquals(0x12, dark.getBackgroundColor().getG());
    assertEquals(0x20, dark.getBackgroundColor().getB());
  }
}
