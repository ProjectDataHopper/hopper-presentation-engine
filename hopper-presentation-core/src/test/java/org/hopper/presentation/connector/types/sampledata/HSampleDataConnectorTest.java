package org.hopper.presentation.connector.types.sampledata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HSampleDataConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void streamsConfiguredRowCount() throws Exception {
    int rowCount = 25;
    HSampleDataConnector sample = new HSampleDataConnector(rowCount);
    HConnector connector = ConnectorTestSupport.wrap("sample", sample);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(connector);

    IRowMeta meta = sample.describeOutput(ctx);
    assertEquals(7, meta.size());
    assertNotNull(meta.searchValueMeta("id"));
    assertNotNull(meta.searchValueMeta("name"));

    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(connector, ctx);
    assertEquals(rowCount, rows.size());
    assertEquals(1L, rows.get(0).getInteger("id", 0));
    assertTrue(rows.get(0).getString("name", "").length() > 0);
  }

  @Test
  void cloneCopiesRowCount() {
    HSampleDataConnector original = new HSampleDataConnector(12);
    HSampleDataConnector copy = original.clone();
    assertEquals(12, copy.getRowCount());
    assertEquals("SampleDataConnector", copy.getPluginId());
  }
}
