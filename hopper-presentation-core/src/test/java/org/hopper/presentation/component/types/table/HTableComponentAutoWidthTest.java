package org.hopper.presentation.component.types.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFont;
import org.hopper.core.HGeometry;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HTextGeometry;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.render.context.SimpleRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Auto column width (width=0 / form "auto"): must include header text in measured max widths, and
 * empty columnSelection must still measure after auto-fill from the connector.
 */
class HTableComponentAutoWidthTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void autoWidth_considersHeaderWhenLongerThanBody() throws Exception {
    List<String> values = Arrays.asList("1", "2", "3");
    HListConnector list = new HListConnector("id", values);
    HConnector connector = new HConnector("auto-src", list);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HConnector.class).save(connector);

    HColumn col =
        new HColumn(
            "id",
            "Very Long Header Label XXX",
            HHorizontalAlignment.LEFT,
            HVerticalAlignment.MIDDLE);
    col.setWidth(0);

    HTableComponent table = new HTableComponent();
    table.setHeader(true);
    table.setHorizontalMargin(1);
    table.setVerticalMargin(2);
    table.setSourceConnectorName("auto-src");
    table.setDefaultFont(new HFont("SansSerif", "12", false, false));
    table.setHeaderFont(new HFont("SansSerif", "12", true, false));
    table.setColumnSelection(new ArrayList<>(List.of(col)));

    HPresentation presentation = new HPresentation();
    presentation.setName("auto-width");
    HPage page = HPage.getA4(false);
    HComponent wrapper = new HComponent("T", table);
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    HLayoutResults results = new HLayoutResults(null);
    SimpleRenderContext rc = new SimpleRenderContext(provider);
    PresentationDataContext ctx = new PresentationDataContext(presentation, provider);

    table.processSourceData(presentation, page, wrapper, ctx, rc, results);

    TableDetails details =
        (TableDetails) results.getDataSet(wrapper, HTableComponent.DATA_TABLE_DETAILS);

    int headerTextW = details.columnSizesList.get(0).get(0).getWidth();
    int bodyTextW = details.columnSizesList.get(1).get(0).getWidth();
    int maxW = details.maxWidths.get(0);

    assertTrue(headerTextW > bodyTextW, "header should be wider than body value");
    assertEquals(
        headerTextW, maxW, "auto maxWidth must equal the longer of header/body (header here)");
    assertTrue(details.totalWidth >= maxW + 2 * table.getHorizontalMargin());
  }

  @Test
  void emptyColumnSelection_autoFillsAndMeasuresIncludingHeaders() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadata.getSerializer(HTheme.class).save(theme);
    metadata
        .getSerializer(HConnector.class)
        .save(new HConnector("sample", new HSampleDataConnector(5)));

    HTableComponent table = new HTableComponent();
    table.setSourceConnectorName("sample");
    table.setHeader(true);
    table.setHorizontalMargin(1);
    table.setColumnSelection(new ArrayList<>()); // empty = all connector columns

    HPresentation presentation = new HPresentation();
    presentation.setName("empty-cols");
    presentation.setDefaultThemeName(theme.getName());
    HPage page = HPage.getA4(false);
    HComponent wrapper = new HComponent("T", table);
    wrapper.setLayout(HLayout.topLeftPage());
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("t"),
            new PresentationRenderContext(presentation, metadata),
            metadata,
            Collections.emptyList());

    TableDetails details =
        (TableDetails) results.getDataSet(wrapper, HTableComponent.DATA_TABLE_DETAILS);
    assertNotNull(details);
    assertFalse(table.getColumnSelection().isEmpty(), "should auto-fill columns from connector");
    assertFalse(details.maxWidths.isEmpty(), "should measure column widths");
    assertTrue(
        details.totalWidth > 0, "totalWidth should be positive, got " + details.totalWidth);
    // Header row present and each maxWidth at least the header string width for that column
    assertFalse(details.columnSizesList.isEmpty());
    List<HTextGeometry> headerGeom = details.columnSizesList.get(0);
    assertEquals(details.maxWidths.size(), headerGeom.size());
    for (int i = 0; i < details.maxWidths.size(); i++) {
      assertTrue(
          details.maxWidths.get(i) >= headerGeom.get(i).getWidth(),
          "maxWidth["
              + i
              + "]="
              + details.maxWidths.get(i)
              + " must be >= header text width "
              + headerGeom.get(i).getWidth());
    }
    HGeometry g = results.findGeometry("T");
    assertNotNull(g);
    assertTrue(g.getWidth() > 10, "geometry width should reflect content, got " + g.getWidth());
  }

  @Test
  void previewSvg_usesNaturalColumnWidths_notForcedShrink() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadata.getSerializer(HTheme.class).save(theme);

    List<String> values = Arrays.asList("a", "bb", "ccc");
    metadata
        .getSerializer(HConnector.class)
        .save(new HConnector("auto-src", new HListConnector("code", values)));

    HColumn col =
        new HColumn(
            "code",
            "Long Header For Preview",
            HHorizontalAlignment.LEFT,
            HVerticalAlignment.MIDDLE);
    col.setWidth(0);

    HTableComponent table = new HTableComponent();
    table.setHeader(true);
    table.setHorizontalMargin(1);
    table.setSourceConnectorName("auto-src");
    table.setDefaultFont(new HFont("SansSerif", "12", false, false));
    table.setHeaderFont(new HFont("SansSerif", "12", true, false));
    table.setColumnSelection(new ArrayList<>(List.of(col)));

    HComponent wrapper = new HComponent("T", table);
    // Tiny preview frame used to force shrink under the old fullPage path
    String svg = wrapper.getSvgXml(80, 60, metadata, null, null);
    assertNotNull(svg);
    assertTrue(svg.length() > 50, "preview SVG should not be empty");
    // Natural header is much wider than 80px; SVG width/viewBox should reflect content, not 80
    assertTrue(
        svg.contains("width=") || svg.contains("viewBox"),
        "SVG should declare a size: " + svg.substring(0, Math.min(200, svg.length())));
    // Extract root width if present
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("width\\s*=\\s*\"(\\d+(?:\\.\\d+)?)\"").matcher(svg);
    if (m.find()) {
      double w = Double.parseDouble(m.group(1));
      assertTrue(
          w > 80,
          "preview page width should grow to natural table width (>80), was " + w);
    }
  }
}
