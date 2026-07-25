package org.hopper.presentation.component.types.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HTextGeometry;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.context.SimpleRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Fixed column widths must still measure cell text so RIGHT/CENTER alignment does not paint
 * strings at the cell edge (which made columns appear to print over each other).
 */
class HTableComponentFixedWidthAlignmentTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void fixedWidthColumns_stillMeasureTextGeometryForAlignment() throws Exception {
    List<String> values = Arrays.asList("24000", "94.5", "SHIP-001");
    HListConnector list = new HListConnector("capacity", values);
    HConnector connector = new HConnector("fixed-width-src", list);

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HConnector.class).save(connector);

    HColumn col = new HColumn("capacity", "Max Capacity (TEU)", HHorizontalAlignment.RIGHT, HVerticalAlignment.MIDDLE);
    col.setWidth(80); // narrower than the long header; must still measure text width

    HTableComponent table = new HTableComponent();
    table.setHeader(true);
    table.setHeaderOnEveryPage(true);
    table.setHorizontalMargin(4);
    table.setVerticalMargin(2);
    table.setSourceConnectorName("fixed-width-src");
    table.setDefaultFont(new HFont("SansSerif", "12", false, false));
    table.setHeaderFont(new HFont("SansSerif", "12", true, false));
    table.setColumnSelection(new ArrayList<>(List.of(col)));

    HPresentation presentation = new HPresentation();
    presentation.setName("fixed-width-align");
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

    // Column width is fixed at 80
    assertEquals(1, details.maxWidths.size());
    assertEquals(80, details.maxWidths.get(0));

    // Header + 3 data rows
    assertEquals(4, details.columnSizesList.size());
    assertEquals(4, details.rowStringsList.size());

    // Header text geometry must be measured (not 0) so RIGHT alignment can inset the string
    HTextGeometry headerGeom = details.columnSizesList.get(0).get(0);
    assertTrue(
        headerGeom.getWidth() > 0,
        "header text width must be measured even when column.width is set; was "
            + headerGeom.getWidth());
    assertEquals("Max Capacity (TEU)", details.rowStringsList.get(0).get(0));

    // Body values measured too
    HTextGeometry bodyGeom = details.columnSizesList.get(1).get(0);
    assertTrue(
        bodyGeom.getWidth() > 0,
        "body text width must be measured for fixed-width columns; was " + bodyGeom.getWidth());
    assertEquals("24000", details.rowStringsList.get(1).get(0));
  }

  @Test
  void fitColumnWidthsToGeometry_shrinksWhenOverflowing_doesNotExpand() {
    HTableComponent table = new HTableComponent();
    table.setHorizontalMargin(4);

    // 3 cols * (100 + 8 margin) = 324 total
    List<Integer> widths = Arrays.asList(100, 100, 100);

    // Underfull geometry: leave as-is (no expand)
    List<Integer> under = table.fitColumnWidthsToGeometry(widths, 500);
    assertEquals(Arrays.asList(100, 100, 100), under);

    // Overflow: shrink proportionally so content+margins fit 200
    // marginsTotal = 3 * 8 = 24, availableContent = 176
    List<Integer> over = table.fitColumnWidthsToGeometry(widths, 200);
    assertEquals(3, over.size());
    int sum = over.stream().mapToInt(Integer::intValue).sum();
    assertEquals(176, sum, "scaled content widths should fill geometry minus margins");
    assertTrue(over.get(0) < 100 && over.get(1) < 100 && over.get(2) < 100);
  }
}
