package org.hopper.presentation.component.types.table;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.layout.HLayoutPageLimitSettings;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.context.SimpleRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Large tables must not measure every connector row when the render-page cap limits how many can
 * paint.
 */
class HTableComponentMeasureBudgetTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
    HLayoutPageLimitSettings.setForTests(3);
  }

  @AfterEach
  void tearDown() {
    HLayoutPageLimitSettings.resetToDefaults();
  }

  @Test
  void processSourceData_measuresOnlyPageBudgetNotAllRows() throws Exception {
    int totalRows = 5000;
    List<String> values = new ArrayList<>(totalRows);
    for (int i = 0; i < totalRows; i++) {
      values.add("row-" + i + "-xxxxxxxx");
    }
    HListConnector list = new HListConnector("name", values);
    HConnector connector = new HConnector("big-list", list);

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HConnector.class).save(connector);

    HTableComponent table = new HTableComponent();
    table.setHeader(true);
    table.setHeaderOnEveryPage(true);
    table.setHorizontalMargin(4);
    table.setVerticalMargin(2);
    table.setSourceConnectorName("big-list");
    table.setDefaultFont(new org.hopper.core.HFont("SansSerif", "11", false, false));
    table.setHeaderFont(new org.hopper.core.HFont("SansSerif", "11", true, false));
    List<HColumn> cols = new ArrayList<>();
    cols.add(new HColumn("name"));
    table.setColumnSelection(cols);

    HPresentation presentation = new HPresentation();
    presentation.setName("table-budget");
    HPage page = HPage.getA4(false);
    HComponent wrapper = new HComponent("T", table);
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    HLayoutResults results = new HLayoutResults(null);
    results.setMaxRenderPages(3);
    SimpleRenderContext rc = new SimpleRenderContext(provider);
    rc.setMaxRenderPages(3);
    PresentationDataContext ctx = new PresentationDataContext(presentation, provider);

    long t0 = System.nanoTime();
    table.processSourceData(presentation, page, wrapper, ctx, rc, results);
    long ms = (System.nanoTime() - t0) / 1_000_000L;

    TableDetails details =
        (TableDetails) results.getDataSet(wrapper, HTableComponent.DATA_TABLE_DETAILS);
    int measuredLines = details.maxHeights.size();
    assertTrue(
        measuredLines < 500,
        "expected far fewer than 5000 measured lines, got " + measuredLines + " in " + ms + "ms");
    assertTrue(results.isPagesTruncated(), "should mark truncated when input exceeds budget");
    assertTrue(ms < 3000, "measure took too long: " + ms + "ms for " + measuredLines + " lines");
  }
}
