package org.hopper.presentation.connector.types.list;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HListConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void streamsListValuesInOrder() throws Exception {
    HListConnector list =
        new HListConnector("country", Arrays.asList("Atlantis", "Wakanda", "Zamunda"));
    HConnector connector = ConnectorTestSupport.wrap("list", list);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(connector);

    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(connector, ctx);
    assertEquals(3, rows.size());
    assertEquals("Atlantis", rows.get(0).getString("country", null));
    assertEquals("Wakanda", rows.get(1).getString("country", null));
    assertEquals("Zamunda", rows.get(2).getString("country", null));
    assertEquals(1, list.describeOutput(ctx).size());
  }
}
