package org.hopper.presentation.component.types.table;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.Constants;
import org.hopper.core.HColorRGB;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFont;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Tables with no component-level themeName must still resolve grid/header colors from the
 * presentation default theme (Ship Operational Status regression).
 */
class HTableComponentThemeFallbackTest {

  static final class TableLookups extends HTableComponent {
    HColorRGB grid(PresentationRenderContext ctx) throws Exception {
      return lookupGridColor(ctx);
    }

    HColorRGB headerInk(PresentationRenderContext ctx) throws Exception {
      return lookupHeaderColor(ctx);
    }

    HFont headerFont(PresentationRenderContext ctx) throws Exception {
      return lookupHeaderFont(ctx);
    }
  }

  private MemoryMetadataProvider provider;
  private HPresentation presentation;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
    provider = new MemoryMetadataProvider();
    provider.getSerializer(HTheme.class).save(HTheme.getDefault());
    presentation = new HPresentation();
    presentation.setDefaultThemeName(Constants.DEFAULT_THEME_NAME);
  }

  @Test
  void nullThemeName_resolvesGridAndHeaderFromPresentationTheme() throws Exception {
    PresentationRenderContext ctx = new PresentationRenderContext(presentation, provider);

    TableLookups table = new TableLookups();
    table.setThemeName(null);
    table.setGridColor(null);
    table.setHeaderFont(null);
    table.setHeaderBackGroundColor(null);

    assertDoesNotThrow(() -> table.grid(ctx));
    assertNotNull(table.grid(ctx));
    assertNotNull(table.headerInk(ctx));
    assertNotNull(table.headerFont(ctx));
  }
}
