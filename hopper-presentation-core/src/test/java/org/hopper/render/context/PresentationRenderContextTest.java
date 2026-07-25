package org.hopper.render.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.Constants;
import org.hopper.core.HColorMode;
import org.hopper.core.HColorRGB;
import org.hopper.core.HEnvironment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.theme.HTheme;

public class PresentationRenderContextTest {

  private MemoryMetadataProvider metadataProvider;
  private HPresentation presentation;

  @BeforeEach
  public void setUp() throws Exception {
    HEnvironment.init();
    metadataProvider = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadataProvider.getSerializer(HTheme.class).save(theme);
    HTheme dark = HTheme.getDefaultDark();
    if (dark.getName() == null || dark.getName().isBlank()) {
      dark.setName("Default Dark");
    }
    metadataProvider.getSerializer(HTheme.class).save(dark);
    presentation = new HPresentation();
    presentation.setDefaultThemeName(Constants.DEFAULT_THEME_NAME);
    presentation.setDarkThemeName(dark.getName());
  }

  @Test
  public void blankThemeUsesDarkThemeInDarkMode() throws HException {
    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadataProvider);
    ctx.setColorMode(HColorMode.DARK);
    HTheme theme = ctx.lookupTheme(null);
    assertEquals(presentation.getDarkThemeName(), theme.getName());
    // Light ink on dark background (Default Dark)
    assertTrue(theme.lookupDefaultColor().getR() + theme.lookupDefaultColor().getG()
        + theme.lookupDefaultColor().getB() > 400);
  }

  @Test
  public void darkModeUsesCatalogDefaultDarkWhenPresentationDarkThemeUnset() throws Exception {
    // Demo presentations often only set defaultThemeName=Default with empty darkThemeName.
    // Dark mode must still load authored "Default Dark" (header bg, fonts), not only adapt light.
    presentation.setDarkThemeName(null);
    HTheme customDark = HTheme.getDefaultDark();
    customDark.setName(Constants.DEFAULT_DARK_THEME_NAME);
    customDark.setHeaderBackGroundColor(new HColorRGB("#ff00aa"));
    metadataProvider.getSerializer(HTheme.class).save(customDark);

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadataProvider);
    ctx.setColorMode(HColorMode.DARK);
    HTheme theme = ctx.lookupTheme(null);
    assertEquals(Constants.DEFAULT_DARK_THEME_NAME, theme.getName());
    assertEquals(new HColorRGB("#ff00aa"), theme.lookupHeaderBackGroundColor());
  }

  @Test
  public void darkModeTableHeaderBackgroundFollowsCatalogDarkTheme() throws Exception {
    presentation.setDarkThemeName(null);
    HTheme customDark = HTheme.getDefaultDark();
    customDark.setName(Constants.DEFAULT_DARK_THEME_NAME);
    customDark.setHeaderBackGroundColor(new HColorRGB("#112233"));
    metadataProvider.getSerializer(HTheme.class).save(customDark);

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadataProvider);
    ctx.setColorMode(HColorMode.DARK);

    class TableLookups extends org.hopper.presentation.component.types.table.HTableComponent {
      HColorRGB bg() throws HException {
        return lookupHeaderBackGroundColor(ctx);
      }
    }
    TableLookups table = new TableLookups();
    table.setThemeName(null);
    table.setHeaderBackGroundColor(null);
    assertEquals(new HColorRGB("#112233"), table.bg());
  }

  @Test
  public void blankOrNullThemeNameUsesPresentationDefaultInLightMode() throws HException {
    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadataProvider);
    ctx.setColorMode(HColorMode.LIGHT);

    HTheme fromNull = ctx.lookupTheme(null);
    assertTrue(fromNull.isRenderable());
    assertEquals(Constants.DEFAULT_THEME_NAME, fromNull.getName());
    // Must resolve grid (table paint path) without throwing
    assertTrue(fromNull.lookupGridColor() != null);

    HTheme fromBlank = ctx.lookupTheme("");
    assertEquals(Constants.DEFAULT_THEME_NAME, fromBlank.getName());

    // Literal "null" string from bad form/JSON saves
    HTheme fromNullString = ctx.lookupTheme("null");
    assertTrue(fromNullString.isRenderable());
    assertEquals(Constants.DEFAULT_THEME_NAME, fromNullString.getName());
  }

  @Test
  public void incompleteThemeInContextListFallsBackToBuiltin() throws HException {
    // Empty theme object in the local list (name set, no colors) is not renderable
    HTheme empty = new HTheme();
    empty.setName("Broken Theme");
    // no defaultColor / gridColor

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadataProvider);
    ctx.getThemes().add(empty);

    HTheme resolved = ctx.lookupTheme("Broken Theme");
    assertTrue(resolved.isRenderable(), "should replace incomplete theme with built-in");
    assertTrue(resolved.lookupGridColor() != null);
  }

  @Test
  public void simpleRenderContextBlankThemeNameReturnsNullWithoutThrowing() throws HException {
    SimpleRenderContext simple = new SimpleRenderContext(metadataProvider);
    assertNull(simple.lookupTheme(null));
    assertNull(simple.lookupTheme(""));
  }

  @Test
  public void stampedDefaultThemeNameStillTracksColorMode() throws HException {
    // Layout used to stamp blank → defaultThemeName; that string must not lock light ink in dark
    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadataProvider);
    ctx.setColorMode(HColorMode.DARK);
    HTheme theme = ctx.lookupTheme(Constants.DEFAULT_THEME_NAME);
    assertEquals(presentation.getDarkThemeName(), theme.getName());

    ctx.setColorMode(HColorMode.LIGHT);
    HTheme light = ctx.lookupTheme(Constants.DEFAULT_THEME_NAME);
    assertEquals(Constants.DEFAULT_THEME_NAME, light.getName());
  }

  @Test
  public void getStableColor() throws HException {

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadataProvider);

    HColorRGB a1 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "A");
    HColorRGB a2 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "A");
    assertEquals(a1, a2);

    HColorRGB b1 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "B");
    HColorRGB b2 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "B");
    assertEquals(b1, b2);
    assertNotSame(a1, b1);

    HColorRGB c1 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "C");
    HColorRGB c2 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "C");
    assertEquals(c1, c2);
    assertNotSame(a1, c1);
    assertNotSame(b1, c1);

    HColorRGB a3 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "A");
    assertEquals(a1, a3);
    HColorRGB b3 = renderContext.getStableColor(Constants.DEFAULT_THEME_NAME, "B");
    assertEquals(b1, b3);
  }

  @Test
  public void distinctLabelsGetDistinctColorsUntilPaletteWraps() throws HException {
    PresentationRenderContext ctx =
        new PresentationRenderContext(presentation, metadataProvider);

    // These three collided under hash%palette; sequential must keep them distinct
    HColorRGB home = ctx.getStableColor(Constants.DEFAULT_THEME_NAME, "Home");
    HColorRGB electronics = ctx.getStableColor(Constants.DEFAULT_THEME_NAME, "Electronics");
    HColorRGB sports = ctx.getStableColor(Constants.DEFAULT_THEME_NAME, "Sports");

    assertNotEquals(home, electronics);
    assertNotEquals(home, sports);
    assertNotEquals(electronics, sports);

    // Re-lookup is stable
    assertEquals(home, ctx.getStableColor(Constants.DEFAULT_THEME_NAME, "Home"));
    assertEquals(electronics, ctx.getStableColor(Constants.DEFAULT_THEME_NAME, "Electronics"));
    assertEquals(sports, ctx.getStableColor(Constants.DEFAULT_THEME_NAME, "Sports"));
  }

  @Test
  public void sharedCategoryLabelsMatchAcrossDiscoveryOrder() throws HException {
    // Simulate pie then bar (pie claims categories first)
    PresentationRenderContext pieFirst =
        new PresentationRenderContext(presentation, metadataProvider);
    HColorRGB homePie = pieFirst.getStableColor(Constants.DEFAULT_THEME_NAME, "Home");
    HColorRGB elecPie = pieFirst.getStableColor(Constants.DEFAULT_THEME_NAME, "Electronics");
    HColorRGB homeBarAfterPie = pieFirst.getStableColor(Constants.DEFAULT_THEME_NAME, "Home");
    assertEquals(homePie, homeBarAfterPie);
    assertEquals(elecPie, pieFirst.getStableColor(Constants.DEFAULT_THEME_NAME, "Electronics"));

    // Same labels within one context stay distinct
    assertNotEquals(homePie, elecPie);
  }
}

