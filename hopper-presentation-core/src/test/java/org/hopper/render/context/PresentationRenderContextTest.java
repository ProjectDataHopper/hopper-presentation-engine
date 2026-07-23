package org.hopper.render.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.Constants;
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
    presentation = new HPresentation();
    presentation.setDefaultThemeName(Constants.DEFAULT_THEME_NAME);
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
