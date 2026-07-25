package org.hopper.presentation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.Constants;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.core.HGeometry;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Continuous (browser scroll) layout: single tall surface, viewport width, no multi-page table
 * splits, content height from geometry (capped).
 */
class HContinuousLayoutTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void continuousTableIsSinglePageAndTallerThanAuthorHeight() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadata.getSerializer(HTheme.class).save(theme);

    // Many rows — would paginate on a short A4-like page
    HSampleDataConnector sample = new HSampleDataConnector(80);
    HConnector connector = new HConnector("sample", sample);
    metadata.getSerializer(HConnector.class).save(connector);

    HPresentation presentation = new HPresentation();
    presentation.setName("continuous-table");
    presentation.setDefaultThemeName(theme.getName());
    presentation.setLayoutMode(HLayoutMode.CONTINUOUS.wireValue());
    presentation.setDesignWidth(900);

    // Author-size page is short; continuous mode overrides width/height at layout
    HPage page = new HPage(400, 300, 10, 10, 10, 10);
    presentation.getPages().add(page);

    HTableComponent tableType = new HTableComponent();
    tableType.setSourceConnectorName("sample");
    tableType.setColumnSelection(
        new ArrayList<>(Collections.singletonList(new HColumn("id"))));
    HComponent table = new HComponent("ProductsTable", tableType);
    table.setLayout(HLayout.topLeftPage());
    page.getComponents().add(table);

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadata);
    renderContext.setViewportWidth(1100);

    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("continuous-table"),
            renderContext,
            metadata,
            Collections.emptyList());

    assertTrue(results.isContinuousScroll());
    assertEquals(1, results.getRenderPages().size(), "continuous must be a single render page");
    assertFalse(results.isPagesTruncated(), "80 sample rows should fit under the 5000px cap");

    assertEquals(1100, page.getWidth(), "width from viewport");
    assertEquals(1100, results.getContentWidth());

    HGeometry tableGeo = results.findGeometry("ProductsTable");
    assertNotNull(tableGeo);
    assertTrue(
        tableGeo.getHeight() > 300,
        "table height should exceed original author page height; got " + tableGeo.getHeight());
    assertTrue(
        results.getContentHeight() > 300,
        "content height should grow with table; got " + results.getContentHeight());
    assertTrue(
        results.getContentHeight()
            <= Constants.DEFAULT_MAX_CONTINUOUS_CONTENT_HEIGHT
                + page.getTopMargin()
                + page.getBottomMargin()
                + 1,
        "content height must respect continuous cap");

    // Paginated control: same data without continuous → multi-page on short sheet
    HPresentation paginated = new HPresentation();
    paginated.setName("paginated-control");
    paginated.setDefaultThemeName(theme.getName());
    paginated.setLayoutMode(HLayoutMode.PAGINATED.wireValue());
    HPage shortPage = new HPage(400, 300, 10, 10, 10, 10);
    paginated.getPages().add(shortPage);
    HTableComponent t2 = new HTableComponent();
    t2.setSourceConnectorName("sample");
    t2.setColumnSelection(new ArrayList<>(Collections.singletonList(new HColumn("id"))));
    HComponent table2 = new HComponent("ProductsTable", t2);
    table2.setLayout(HLayout.topLeftPage());
    shortPage.getComponents().add(table2);

    HLayoutResults multi =
        paginated.doLayout(
            new LoggingObject("paginated-control"),
            new PresentationRenderContext(paginated, metadata),
            metadata,
            Collections.emptyList());
    assertTrue(
        multi.getRenderPages().size() > 1,
        "paginated short page should split the same table across pages");
  }

  @Test
  void continuousUsesDesignWidthWhenNoViewport() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadata.getSerializer(HTheme.class).save(theme);

    HSampleDataConnector sample = new HSampleDataConnector(5);
    metadata.getSerializer(HConnector.class).save(new HConnector("sample", sample));

    HPresentation presentation = new HPresentation();
    presentation.setName("continuous-design-width");
    presentation.setDefaultThemeName(theme.getName());
    presentation.setLayoutMode(HLayoutMode.CONTINUOUS.wireValue());
    presentation.setDesignWidth(1280);

    HPage page = new HPage(400, 600, 20, 20, 20, 20);
    presentation.getPages().add(page);

    HTableComponent tableType = new HTableComponent();
    tableType.setSourceConnectorName("sample");
    tableType.setColumnSelection(
        new ArrayList<>(Collections.singletonList(new HColumn("id"))));
    HComponent table = new HComponent("T", tableType);
    table.setLayout(HLayout.topLeftPage());
    page.getComponents().add(table);

    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("design-width"),
            new PresentationRenderContext(presentation, metadata),
            metadata,
            Collections.emptyList());

    assertEquals(1280, page.getWidth());
    assertEquals(1280, results.getContentWidth());
    assertEquals(1, results.getRenderPages().size());
  }

  @Test
  void continuousContextFlagWithoutPresentationMode() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadata.getSerializer(HTheme.class).save(theme);

    HSampleDataConnector sample = new HSampleDataConnector(40);
    metadata.getSerializer(HConnector.class).save(new HConnector("sample", sample));

    // Presentation stays paginated in metadata; render context forces continuous view
    HPresentation presentation = new HPresentation();
    presentation.setName("context-force-continuous");
    presentation.setDefaultThemeName(theme.getName());
    assertFalse(presentation.isContinuousLayout());

    HPage page = new HPage(500, 280, 10, 10, 10, 10);
    presentation.getPages().add(page);

    HTableComponent tableType = new HTableComponent();
    tableType.setSourceConnectorName("sample");
    tableType.setColumnSelection(
        new ArrayList<>(Collections.singletonList(new HColumn("id"))));
    HComponent table = new HComponent("T", tableType);
    table.setLayout(HLayout.topLeftPage());
    page.getComponents().add(table);

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, metadata);
    ctx.setContinuousScroll(true);
    ctx.setViewportWidth(1000);

    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("ctx-continuous"), ctx, metadata, Collections.emptyList());

    assertTrue(results.isContinuousScroll());
    assertEquals(1, results.getRenderPages().size());
    assertEquals(1000, page.getWidth());
  }

  @Test
  void layoutModeFromStringAcceptsAliases() {
    assertTrue(HLayoutMode.fromString("continuous").isContinuous());
    assertTrue(HLayoutMode.fromString("SCROLL").isContinuous());
    assertTrue(HLayoutMode.fromString("web").isContinuous());
    assertFalse(HLayoutMode.fromString(null).isContinuous());
    assertFalse(HLayoutMode.fromString("paginated").isContinuous());
    assertEquals("continuous", HLayoutMode.CONTINUOUS.wireValue());
  }

  @Test
  void continuousWidthIsClamped() {
    assertEquals(
        Constants.CONTINUOUS_VIEWPORT_WIDTH_MIN, HPresentation.clampContinuousWidth(10));
    assertEquals(
        Constants.CONTINUOUS_VIEWPORT_WIDTH_MAX, HPresentation.clampContinuousWidth(99999));
    assertEquals(800, HPresentation.clampContinuousWidth(800));
  }
}
