package org.hopper.presentation.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColorRGB;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFont;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.render.IRenderContext;
import org.hopper.render.context.SimpleRenderContext;
import org.hopper.util.BasePresentationUtil;

/** Theme header font / background defaults used by tables (and later crosstabs). */
class HThemeHeaderDefaultsTest {

  /** Exposes protected table lookups for unit testing. */
  static final class TableLookups extends HTableComponent {
    HFont headerFontOf(IRenderContext ctx) throws Exception {
      return lookupHeaderFont(ctx);
    }

    HColorRGB headerColorOf(IRenderContext ctx) throws Exception {
      return lookupHeaderColor(ctx);
    }

    HColorRGB headerBgOf(IRenderContext ctx) throws Exception {
      return lookupHeaderBackGroundColor(ctx);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void defaultThemesDefineHeaderFontColorAndBackground() throws Exception {
    HTheme light = HTheme.getDefault();
    assertNotNull(light.getHeaderFont());
    assertTrue(light.getHeaderFont().isBold());
    assertEquals("Arial", light.getHeaderFont().getFontName());
    assertEquals(new HColorRGB("#000000"), light.getHeaderColor());
    assertNotNull(light.getHeaderBackGroundColor());
    assertEquals(new HColorRGB("#e8e8e8"), light.getHeaderBackGroundColor());
    assertEquals(light.getHeaderFont(), light.lookupHeaderFont());
    assertEquals(light.getHeaderColor(), light.lookupHeaderColor());
    assertEquals(light.getHeaderBackGroundColor(), light.lookupHeaderBackGroundColor());
    // Light header: dark ink on light gray
    assertTrue(
        HThemeAdapt.contrastRatio(light.getHeaderColor(), light.getHeaderBackGroundColor())
            >= 4.5);

    HTheme dark = HTheme.getDefaultDark();
    assertNotNull(dark.getHeaderFont());
    assertTrue(dark.getHeaderFont().isBold());
    assertEquals(new HColorRGB("#e8eef9"), dark.getHeaderColor());
    assertNotNull(dark.getHeaderBackGroundColor());
    assertEquals(new HColorRGB("#1b2740"), dark.getHeaderBackGroundColor());
    // Dark header: light ink on dark navy
    assertTrue(
        HThemeAdapt.contrastRatio(dark.getHeaderColor(), dark.getHeaderBackGroundColor()) >= 4.5);
  }

  @Test
  void lookupHeaderFallsBackWhenUnset() throws Exception {
    HTheme theme = new HTheme();
    theme.setName("partial");
    theme.setDefaultFont(new HFont("SansSerif", "11", false, false));
    theme.setDefaultColor(new HColorRGB("#101010"));
    theme.setHeaderFont(null);
    theme.setHeaderColor(null);
    assertEquals(theme.getDefaultFont(), theme.lookupHeaderFont());
    assertEquals(theme.getDefaultColor(), theme.lookupHeaderColor());
    assertNull(theme.lookupHeaderBackGroundColor());
  }

  @Test
  void tableUsesThemeHeaderWhenComponentFieldsNull() throws Exception {
    HTheme theme = HTheme.getDefault();
    theme.setName("TableHeaderTheme");
    theme.setHeaderFont(new HFont("Courier", "14", true, false));
    theme.setHeaderColor(new HColorRGB("#ffeecc"));
    theme.setHeaderBackGroundColor(new HColorRGB("#112233"));

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HTheme.class).save(theme);

    SimpleRenderContext rc = new SimpleRenderContext(provider);

    TableLookups table = new TableLookups();
    table.setThemeName("TableHeaderTheme");
    table.setHeaderFont(null);
    table.setHeaderBackGroundColor(null);

    assertEquals(new HFont("Courier", "14", true, false), table.headerFontOf(rc));
    assertEquals(new HColorRGB("#ffeecc"), table.headerColorOf(rc));
    assertEquals(new HColorRGB("#112233"), table.headerBgOf(rc));

    // Component override wins for font/background
    table.setHeaderFont(new HFont("Arial", "10", false, true));
    table.setHeaderBackGroundColor(new HColorRGB("#abcdef"));
    assertEquals(new HFont("Arial", "10", false, true), table.headerFontOf(rc));
    assertEquals(new HColorRGB("#abcdef"), table.headerBgOf(rc));
    // headerColor remains theme-driven
    assertEquals(new HColorRGB("#ffeecc"), table.headerColorOf(rc));
  }

  @Test
  void adaptDarkModeCopiesHeaderFontAndAdaptsColors() {
    HTheme light = HTheme.getDefault();
    HTheme dark = HThemeAdapt.forDarkMode(light);
    assertNotNull(dark.getHeaderFont());
    assertEquals(light.getHeaderFont().getFontName(), dark.getHeaderFont().getFontName());
    assertTrue(dark.getHeaderFont().isBold());
    assertNotNull(dark.getHeaderColor());
    assertNotNull(dark.getHeaderBackGroundColor());
  }
}
