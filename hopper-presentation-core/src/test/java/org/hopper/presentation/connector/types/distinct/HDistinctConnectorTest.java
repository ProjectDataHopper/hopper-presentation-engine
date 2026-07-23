package org.hopper.presentation.connector.types.distinct;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HDistinctConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void removesConsecutiveDuplicatesOnlyWhenAdjacentAfterSourceOrder() throws Exception {
    // Distinct compares to previous row: non-adjacent duplicates pass through.
    HListConnector source =
        new HListConnector("v", Arrays.asList("a", "a", "b", "b", "a"));
    HConnector sourceConn = ConnectorTestSupport.wrap("source", source);

    HDistinctConnector distinct = new HDistinctConnector();
    distinct.setSourceConnectorName("source");
    HConnector distinctConn = ConnectorTestSupport.wrap("distinct", distinct);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceConn, distinctConn);
    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(distinctConn, ctx);

    assertEquals(3, rows.size());
    assertEquals("a", rows.get(0).getString("v", null));
    assertEquals("b", rows.get(1).getString("v", null));
    assertEquals("a", rows.get(2).getString("v", null));
  }
}
