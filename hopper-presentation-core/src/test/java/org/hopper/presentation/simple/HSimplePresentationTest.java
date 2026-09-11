package org.hopper.presentation.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.Constants;
import org.hopper.core.HColorMode;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFont;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.theme.HTheme;
import org.hopper.presentation.component.types.chart.GanttTask;
import org.hopper.presentation.component.types.chart.HGanttChartComponent;
import org.hopper.presentation.connector.types.memory.HInMemoryRowsConnector;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.render.context.PresentationRenderContext;

class HSimplePresentationTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void trendRendersSvg() throws Exception {
    List<RowMetaAndData> rows = durationRows();
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("duration-trend")
            .addTitle("Duration")
            .addTrend("Run duration", rows, "run", "duration_ms")
            .build();
    assertNotNull(catalog.getProvider().getSerializer(HPresentation.class).load("duration-trend"));
    String svg = render(catalog);
    assertTrue(svg.contains("<svg") || svg.contains("<SVG"));
  }

  @Test
  void comparisonAndTableDashboardRenders() throws Exception {
    List<RowMetaAndData> rows = durationRows();
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("ops")
            .description("Generated ops dashboard")
            .addComparison("Duration by status", rows, "status", null, "duration_ms")
            .addTable("Runs", rows, "run", "status", "duration_ms")
            .build();
    String svg = render(catalog);
    assertFalse(svg.isBlank());
    assertNotNull(catalog.getPresentation().getPages());
    assertFalse(catalog.getPresentation().getPages().get(0).getComponents().isEmpty());
  }

  @Test
  void multiSeriesTrendRenders() throws Exception {
    List<RowMetaAndData> rows = seriesRows();
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("perf")
            .addTrend("Rows per second", rows, "elapsed_s", "transform", "value")
            .build();
    String svg = render(catalog);
    assertFalse(svg.isBlank());
    assertFalse(svg.toLowerCase().contains("configure dimensions"), svg);
    HInMemoryRowsConnector connector = catalog.findInMemoryConnector();
    assertNotNull(connector);
    assertEquals(6, connector.getRows().size());
    connector.setRows(seriesRows());
    assertEquals(6, catalog.findInMemoryConnector().getRows().size());
  }

  @Test
  void trendTileHeightSizesPage() throws Exception {
    int defaultH =
        HSimplePresentation.dashboard("h-default")
            .addTrend("t", List.of(), "elapsed_s", "transform", "value")
            .build()
            .getPresentation()
            .getPages()
            .get(0)
            .getHeight();
    int halfH =
        HSimplePresentation.dashboard("h-half")
            .trendTileHeight(140)
            .addTrend("t", List.of(), "elapsed_s", "transform", "value")
            .build()
            .getPresentation()
            .getPages()
            .get(0)
            .getHeight();
    assertTrue(halfH < defaultH, defaultH + " vs " + halfH);
    assertEquals(184, halfH);
  }

  @Test
  void ganttFromInlineTasksRendersAndMutates() throws Exception {
    List<GanttTask> tasks =
        List.of(
            new GanttTask("Load", 0, 1200, "wf", "success"),
            new GanttTask("Publish", 1100, 1800, "wf", "success"));
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("gantt").addGantt("Action timings", tasks).build();
    HGanttChartComponent gantt = catalog.findGanttComponent();
    assertNotNull(gantt);
    assertEquals(2, gantt.getInlineTasks().size());
    String svg = render(catalog);
    assertTrue(svg.contains("Load") || svg.toLowerCase().contains("action"), svg);
    gantt.setInlineTasks(
        List.of(
            new GanttTask("Load", 0, 1200, "wf", "success"),
            new GanttTask("Publish", 1100, 1800, "wf", "success"),
            new GanttTask("Notify", 1800, 2100, "wf", "running")));
    String svg2 = render(catalog);
    assertTrue(svg2.contains("Notify") || svg2.contains("Publish"), svg2);
  }

  @Test
  void ganttInlineTasksAreNotStuckOnEmptyLayoutCache() throws Exception {
    org.hopper.presentation.layout.HLayoutCacheSettings.setForTests(true, 100);
    org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().invalidateAll();
    try {
      HGeneratedCatalog catalog =
          HSimplePresentation.dashboard("gantt-cache").addGantt("Action timings", List.of()).build();
      String emptySvg = render(catalog);
      assertTrue(
          emptySvg.contains("No tasks") || emptySvg.contains("No input connector"), emptySvg);

      catalog
          .findGanttComponent()
          .setInlineTasks(List.of(new GanttTask("Load", 0, 1_200, "wf", "success")));
      String liveSvg = render(catalog);
      assertTrue(liveSvg.contains("Load"), liveSvg);
      assertFalse(liveSvg.contains("No input connector"), liveSvg);
    } finally {
      org.hopper.presentation.layout.HLayoutCacheSettings.resetToDefaults();
      org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().invalidateAll();
    }
  }

  @Test
  void manyCategoryTrendKeepsPlotAndFormatsAxis() throws Exception {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger("elapsed_s"));
    meta.addValueMeta(new ValueMetaString("transform"));
    meta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaNumber("value"));
    List<RowMetaAndData> rows = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      rows.add(new RowMetaAndData(meta, (long) i, "Dummy", 1_415_655d + (i % 7) * 1000d));
    }
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("perf-scale")
            .addTrend("Transform performance", rows, "elapsed_s", "transform", "value")
            .build();
    var chart = catalog.findLineChartComponent();
    assertNotNull(chart);
    // Default addTrend turns the legend on for a series column. Line charts used to size that
    // band from every category (time point), which collapsed the plot and drew the series
    // above the Y-axis. Keep the legend on for this first pass so the engine fix is covered.
    assertTrue(chart.isShowingLegend());
    String svgWithLegend = render(catalog);
    assertSeriesBelowTitle(svgWithLegend);

    chart.setUsingAngledHorizontalLabels(true);
    chart.setHorizontalLabelAngle("20");
    chart.setHorizontalDimensionsFont(new HFont("Arial", "9", false, false));
    chart.setShowingLegend(true);
    chart.setLegendPosition("RIGHT");
    chart.getFacts().get(0).setFormatMask("###,###,##0");
    String svg = render(catalog);
    assertFalse(svg.toLowerCase().contains("configure dimensions"), svg);
    assertTrue(svg.contains("1,421,655") || svg.contains("1,415,655"), svg);
    assertFalse(svg.contains("1415655.0"), svg);
    assertFalse(svg.contains(">0.0<"), svg);
    assertTrue(svg.contains("rotate(20)"), svg);
    assertTrue(svg.contains("font-size=\"9") || svg.contains("font-size:9"), svg);
    assertTrue(svg.contains("Dummy"), svg);
    assertSeriesBelowTitle(svg);
  }

  @Test
  void trendSortsNumericCategoriesAndDrawsRightLegend() throws Exception {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger("elapsed_s"));
    meta.addValueMeta(new ValueMetaString("transform"));
    meta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaNumber("value"));
    List<RowMetaAndData> rows = new ArrayList<>();
    for (long elapsed : new long[] {10, 2, 0, 1}) {
      rows.add(new RowMetaAndData(meta, elapsed, "Dummy", 100d));
      rows.add(new RowMetaAndData(meta, elapsed, "Generator", 80d));
    }
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("perf-order")
            .addTrend("Transform performance", rows, "elapsed_s", "transform", "value")
            .build();
    var chart = catalog.findLineChartComponent();
    assertNotNull(chart);
    assertTrue(chart.isShowingLegend());
    assertEquals("RIGHT", chart.getLegendPosition());
    String svg = render(catalog);
    int two = svg.indexOf(">2<");
    int ten = svg.indexOf(">10<");
    assertTrue(two >= 0 && ten >= 0, svg);
    assertTrue(two < ten, "elapsed seconds must sort numerically, not as strings: " + svg);
    assertTrue(svg.contains("Dummy"), svg);
    assertTrue(svg.contains("Generator"), svg);
  }

  @Test
  void generatedCatalogUsesLightAndDarkThemes() throws Exception {
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("themed").addNote("hello").build();
    assertEquals(Constants.GENERATED_THEME_NAME, catalog.getPresentation().getDefaultThemeName());
    assertEquals(
        Constants.GENERATED_DARK_THEME_NAME, catalog.getPresentation().getDarkThemeName());
    HTheme light =
        catalog.getProvider().getSerializer(HTheme.class).load(Constants.GENERATED_THEME_NAME);
    HTheme dark =
        catalog
            .getProvider()
            .getSerializer(HTheme.class)
            .load(Constants.GENERATED_DARK_THEME_NAME);
    assertNotNull(light);
    assertNotNull(dark);
    assertEquals("#ffffff", light.getBackgroundColor().getHexColor());
    assertEquals("#3c3f41", dark.getBackgroundColor().getHexColor());
    assertEquals(
        Constants.GENERATED_THEME_NAME,
        catalog
            .getPresentation()
            .resolveDefaultTheme(catalog.getProvider(), HColorMode.LIGHT)
            .getName());
    assertEquals(
        Constants.GENERATED_DARK_THEME_NAME,
        catalog
            .getPresentation()
            .resolveDefaultTheme(catalog.getProvider(), HColorMode.DARK)
            .getName());
  }

  @Test
  void ganttReservedRowsSizesPage() throws Exception {
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("gantt-rows")
            .addGantt("Action timings", List.of(), 10)
            .build();
    int tile = HSimplePresentation.ganttPixelHeight(10);
    assertEquals(tile + 44, catalog.getPresentation().getPages().get(0).getHeight());
    assertTrue(tile > HSimplePresentation.ganttPixelHeight(1));
  }

  @Test
  void emptyTrendStillLayouts() throws Exception {
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("empty-trend")
            .addTrend("Waiting", List.of(), "elapsed_s", "transform", "value")
            .build();
    assertNotNull(catalog.findInMemoryConnector());
    String svg = render(catalog);
    assertTrue(svg.contains("<svg") || svg.contains("<SVG"));
  }

  @Test
  void titleAndNoteStackWithPixelOffsetsNotPagePercents() throws Exception {
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("empty")
            .addTitle("Project overview")
            .addNote("No execution information was found.")
            .build();
    var page = catalog.getPresentation().getPages().get(0);
    assertTrue(page.getHeight() < 800, "empty dashboard should not be a tall empty sheet");
    var titleLayout = page.getComponents().get(0).getLayout();
    assertEquals(16, titleLayout.getTop().getOffset());
    assertEquals(60, titleLayout.getBottom().getOffset());
    String svg = render(catalog);
    assertTrue(svg.toLowerCase().contains("project overview"));
    assertTrue(svg.toLowerCase().contains("execution information"));
  }

  /**
   * The chart title is drawn at the top of the component. If the series is scaled into the title
   * band (or above the Y-axis), a high-value polyline sits on the same y as the title text.
   */
  private static void assertSeriesBelowTitle(String svg) {
    Matcher title =
        Pattern.compile("y=\"([0-9.]+)\"[^>]*>Transform performance<", Pattern.CASE_INSENSITIVE)
            .matcher(svg);
    if (!title.find()) {
      title =
          Pattern.compile(">Transform performance</text>", Pattern.CASE_INSENSITIVE).matcher(svg);
      assertTrue(title.find(), svg);
      return;
    }
    double titleY = Double.parseDouble(title.group(1));
    Matcher path = Pattern.compile("\\bd=\"[^\"]*\"").matcher(svg);
    int pointsBelowTitle = 0;
    int pointsTotal = 0;
    while (path.find()) {
      Matcher coord =
          Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s+([0-9]+(?:\\.[0-9]+)?)").matcher(path.group());
      while (coord.find()) {
        pointsTotal++;
        if (Double.parseDouble(coord.group(2)) > titleY + 2) {
          pointsBelowTitle++;
        }
      }
    }
    assertTrue(pointsTotal > 0, svg);
    assertTrue(
        pointsBelowTitle * 2 >= pointsTotal,
        "series should sit below the title, not above the chart: " + svg);
  }

  private static String render(HGeneratedCatalog catalog) throws Exception {
    HPresentation presentation = catalog.getPresentation();
    PresentationRenderContext context =
        new PresentationRenderContext(presentation, catalog.getProvider());
    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("simple-presentation-test"),
            context,
            catalog.getProvider(),
            List.of());
    presentation.render(results, catalog.getProvider(), context);
    assertFalse(results.getRenderPages().isEmpty());
    String svg = results.getRenderPages().get(0).getSvgXml();
    assertNotNull(svg);
    return svg;
  }

  private static List<RowMetaAndData> durationRows() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("run"));
    meta.addValueMeta(new ValueMetaString("status"));
    meta.addValueMeta(new ValueMetaInteger("duration_ms"));
    List<RowMetaAndData> rows = new ArrayList<>();
    rows.add(new RowMetaAndData(meta, "run-1", "SUCCESS", 1200L));
    rows.add(new RowMetaAndData(meta, "run-2", "SUCCESS", 900L));
    rows.add(new RowMetaAndData(meta, "run-3", "FAILED", 400L));
    rows.add(new RowMetaAndData(meta, "run-4", "SUCCESS", 1500L));
    return rows;
  }

  private static List<RowMetaAndData> seriesRows() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger("elapsed_s"));
    meta.addValueMeta(new ValueMetaString("transform"));
    meta.addValueMeta(new ValueMetaInteger("value"));
    List<RowMetaAndData> rows = new ArrayList<>();
    rows.add(new RowMetaAndData(meta, 0L, "Generator", 100L));
    rows.add(new RowMetaAndData(meta, 1L, "Generator", 140L));
    rows.add(new RowMetaAndData(meta, 2L, "Generator", 160L));
    rows.add(new RowMetaAndData(meta, 0L, "Dummy", 10L));
    rows.add(new RowMetaAndData(meta, 1L, "Dummy", 12L));
    rows.add(new RowMetaAndData(meta, 2L, "Dummy", 8L));
    return rows;
  }
}
