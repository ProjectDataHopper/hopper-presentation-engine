package org.hopper.presentation.connector.types.sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.core.HSortMethod;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HSortConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void sortsAscendingByColumn() throws Exception {
    HListConnector source =
        new HListConnector("name", Arrays.asList("Charlie", "Alice", "Bob"));
    HConnector sourceConn = ConnectorTestSupport.wrap("source", source);

    HSortConnector sort =
        new HSortConnector(
            Collections.singletonList(new HColumn("name")),
            Collections.singletonList(
                new HSortMethod(HSortMethod.Type.NATIVE_VALUE, true)));
    sort.setSourceConnectorName("source");
    HConnector sortConn = ConnectorTestSupport.wrap("sorted", sort);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceConn, sortConn);
    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(sortConn, ctx);

    assertEquals(3, rows.size());
    assertEquals("Alice", rows.get(0).getString("name", null));
    assertEquals("Bob", rows.get(1).getString("name", null));
    assertEquals("Charlie", rows.get(2).getString("name", null));
  }

  @Test
  void sortsDescendingByColumn() throws Exception {
    HListConnector source =
        new HListConnector("name", Arrays.asList("Charlie", "Alice", "Bob"));
    HConnector sourceConn = ConnectorTestSupport.wrap("source", source);

    HSortConnector sort =
        new HSortConnector(
            Collections.singletonList(new HColumn("name")),
            Collections.singletonList(
                new HSortMethod(HSortMethod.Type.NATIVE_VALUE, false)));
    sort.setSourceConnectorName("source");
    HConnector sortConn = ConnectorTestSupport.wrap("sorted", sort);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceConn, sortConn);
    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(sortConn, ctx);

    assertEquals("Charlie", rows.get(0).getString("name", null));
    assertEquals("Bob", rows.get(1).getString("name", null));
    assertEquals("Alice", rows.get(2).getString("name", null));
  }
}
