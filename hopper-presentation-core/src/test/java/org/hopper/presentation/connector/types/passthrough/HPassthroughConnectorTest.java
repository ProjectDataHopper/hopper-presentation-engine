package org.hopper.presentation.connector.types.passthrough;

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

class HPassthroughConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void passesAllSourceRowsUnchanged() throws Exception {
    HListConnector source = new HListConnector("v", Arrays.asList("one", "two", "three"));
    HConnector sourceConn = ConnectorTestSupport.wrap("source", source);

    HPassthroughConnector passthrough = new HPassthroughConnector("source");
    HConnector passConn = ConnectorTestSupport.wrap("pass", passthrough);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceConn, passConn);
    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(passConn, ctx);

    assertEquals(3, rows.size());
    assertEquals("one", rows.get(0).getString("v", null));
    assertEquals("three", rows.get(2).getString("v", null));
  }
}
