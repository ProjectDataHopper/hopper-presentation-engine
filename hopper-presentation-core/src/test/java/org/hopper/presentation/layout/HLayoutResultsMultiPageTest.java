package org.hopper.presentation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HAttachment;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.core.HGeometry;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Multi-page tables leave {@code getCurrentRenderPage} on the last overflow page. Relatively
 * positioned siblings must use first-part geometry and land on the first body page.
 */
class HLayoutResultsMultiPageTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void firstGeometryIsStableAcrossParts() {
    HLayoutResults results = new HLayoutResults(new LogChannel("test"));
    results.addComponentGeometry("T", new HGeometry(10, 20, 100, 200));
    results.addComponentGeometry("T", new HGeometry(10, 20, 100, 50));
    assertEquals(200, results.findFirstGeometry("T").getHeight());
    assertEquals(50, results.findGeometry("T").getHeight());
  }

  @Test
  void firstRenderPageIsNotLastAfterOverflow() {
    HLayoutResults results = new HLayoutResults(new LogChannel("test"));
    HPage page = HPage.getA4(true);
    HRenderPage p1 = results.addNewPage(page, null);
    HRenderPage p2 = results.addNewPage(page, p1);
    HRenderPage p3 = results.addNewPage(page, p2);
    assertSame(p1, results.getFirstRenderPage(page));
    assertSame(p3, results.getCurrentRenderPage(page));
  }

  @Test
  void relativeSiblingLandsOnFirstPageBesideMultiPageTable() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    HTheme theme = HTheme.getDefault();
    metadata.getSerializer(HTheme.class).save(theme);
    // Enough rows that a short page forces table pagination
    HSampleDataConnector sample = new HSampleDataConnector(80);
    HConnector connector = new HConnector("sample", sample);
    metadata.getSerializer(HConnector.class).save(connector);

    HPresentation presentation = new HPresentation();
    presentation.setName("relative-multipage");
    presentation.setDefaultThemeName(theme.getName());

    // Small usable height so table overflows quickly (width, height, L, R, T, B margins)
    HPage page = new HPage(400, 300, 10, 10, 10, 10);
    presentation.getPages().add(page);

    HTableComponent tableType = new HTableComponent();
    tableType.setSourceConnectorName("sample");
    tableType.setColumnSelection(
        new ArrayList<>(Collections.singletonList(new HColumn("id"))));
    HComponent table = new HComponent("ProductsTable", tableType);
    table.setLayout(HLayout.topLeftPage());
    page.getComponents().add(table);

    // Side label: left = table RIGHT (same pattern as products Bar Chart)
    HLabelComponent labelType = new HLabelComponent("SIDE");
    HComponent side = new HComponent("SideChart", labelType);
    HLayout sideLayout = new HLayout();
    sideLayout.setLeft(
        new HAttachment("ProductsTable", 0, 5, HAttachment.Alignment.RIGHT));
    sideLayout.setTop(new HAttachment(null, 0, 10, HAttachment.Alignment.TOP));
    side.setLayout(sideLayout);
    page.getComponents().add(side);

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadata);
    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("test"),
            renderContext,
            metadata,
            Collections.emptyList());

    assertTrue(results.getRenderPages().size() > 1, "table should paginate");

    HGeometry tableFirst = results.findFirstGeometry("ProductsTable");
    assertNotNull(tableFirst);
    HGeometry sideGeo = results.findFirstGeometry("SideChart");
    assertNotNull(sideGeo, "side component must receive geometry");

    // Left of side = right of first table part + offset 5
    assertEquals(tableFirst.getX() + tableFirst.getWidth() + 5, sideGeo.getX());

    // Side chart must be on the first render page, not only the last
    HRenderPage firstPage = results.getFirstRenderPage(page);
    boolean foundOnFirst = false;
    for (var lr : firstPage.getLayoutResults()) {
      if (lr.getComponent() != null && "SideChart".equals(lr.getComponent().getName())) {
        foundOnFirst = true;
        break;
      }
    }
    assertTrue(foundOnFirst, "SideChart must be laid out on first body page");
  }
}
