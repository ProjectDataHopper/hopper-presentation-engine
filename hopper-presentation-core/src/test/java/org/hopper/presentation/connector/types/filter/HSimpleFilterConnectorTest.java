package org.hopper.presentation.connector.types.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HSimpleFilterConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void keepsOnlyMatchingValues() throws Exception {
    HListConnector source =
        new HListConnector("color", Arrays.asList("Red", "Green", "Blue", "Red", "Yellow"));
    HConnector sourceConn = ConnectorTestSupport.wrap("source", source);

    HSimpleFilterConnector filter =
        new HSimpleFilterConnector(
            Collections.singletonList(new SimpleFilterValue("color", "Red")));
    filter.setSourceConnectorName("source");
    HConnector filterConn = ConnectorTestSupport.wrap("filtered", filter);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceConn, filterConn);
    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(filterConn, ctx);

    assertEquals(2, rows.size());
    assertEquals("Red", rows.get(0).getString("color", null));
    assertEquals("Red", rows.get(1).getString("color", null));
  }
}
