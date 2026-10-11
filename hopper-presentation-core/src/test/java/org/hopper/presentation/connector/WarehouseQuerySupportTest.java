package org.hopper.presentation.connector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.connector.types.chain.HChainConnector;
import org.hopper.presentation.connector.types.memory.HInMemoryRowsConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;
import org.hopper.presentation.page.HPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseQuerySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void unknownPresentationStillShowsProgress() {
    assertTrue(WarehouseQuerySupport.needsProgressDialog(null, new MemoryMetadataProvider()));
  }

  @Test
  void ganttWithoutAConnectorSkipsProgress() {
    assertFalse(
        WarehouseQuerySupport.needsProgressDialog(
            presentation(new HLineChartComponent()), new MemoryMetadataProvider()));
  }

  @Test
  void inMemoryChartSkipsProgress() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider
        .getSerializer(HConnector.class)
        .save(new HConnector("rows", new HInMemoryRowsConnector(List.of())));
    HLineChartComponent chart = new HLineChartComponent("rows");
    assertFalse(WarehouseQuerySupport.needsProgressDialog(presentation(chart), provider));
  }

  @Test
  void sqlConnectorShowsProgress() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider
        .getSerializer(HConnector.class)
        .save(new HConnector("query", new HSqlConnector("vault", "select 1")));
    HLineChartComponent chart = new HLineChartComponent("query");
    assertTrue(WarehouseQuerySupport.needsProgressDialog(presentation(chart), provider));
  }

  @Test
  void chainWhoseSourceIsSqlShowsProgress() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider
        .getSerializer(HConnector.class)
        .save(new HConnector("query", new HSqlConnector("vault", "select 1")));
    HChainConnector chain = new HChainConnector();
    chain.setSourceConnectorName("query");
    provider.getSerializer(HConnector.class).save(new HConnector("chain", chain));
    HLineChartComponent chart = new HLineChartComponent("chain");
    assertTrue(WarehouseQuerySupport.needsProgressDialog(presentation(chart), provider));
  }

  private static HPresentation presentation(HLineChartComponent chart) {
    HPage page = new HPage();
    page.setComponents(new ArrayList<>(List.of(new HComponent("chart", chart))));
    HPresentation presentation = new HPresentation();
    presentation.setName("chart");
    presentation.setPages(new ArrayList<>(List.of(page)));
    return presentation;
  }
}
