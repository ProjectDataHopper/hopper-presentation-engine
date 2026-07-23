package org.hopper.presentation.connector.types.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HSelectionConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void selectsSubsetOfFields() throws Exception {
    HSampleDataConnector source = new HSampleDataConnector(5);
    HConnector sourceConn = ConnectorTestSupport.wrap("source", source);

    HSelectionConnector selection =
        new HSelectionConnector(Collections.singletonList(new HColumn("name")));
    selection.setSourceConnectorName("source");
    HConnector selectionConn = ConnectorTestSupport.wrap("selected", selection);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceConn, selectionConn);

    IRowMeta outMeta = selection.describeOutput(ctx);
    assertEquals(1, outMeta.size());
    assertEquals("name", outMeta.getValueMeta(0).getName());

    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(selectionConn, ctx);
    assertEquals(5, rows.size());
    assertNull(rows.get(0).getRowMeta().searchValueMeta("id"));
    assertEquals(1, rows.get(0).getRowMeta().size());
  }
}
