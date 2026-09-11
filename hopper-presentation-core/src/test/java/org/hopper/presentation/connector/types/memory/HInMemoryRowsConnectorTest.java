package org.hopper.presentation.connector.types.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.core.IHRowListener;
import org.hopper.presentation.datacontext.IDataContext;

class HInMemoryRowsConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void streamsHostRowsAndDescribesOutput() throws Exception {
    IRowMeta meta = new RowMetaBuilder().addString("name").addInteger("count").build();
    List<RowMetaAndData> rows =
        List.of(
            new RowMetaAndData(meta, "alpha", 1L),
            new RowMetaAndData(meta, "beta", 2L));
    HInMemoryRowsConnector connector = new HInMemoryRowsConnector(rows);
    assertEquals(2, connector.describeOutput(null).size());
    assertEquals("name", connector.describeOutput(null).getValueMeta(0).getName());

    List<Object[]> received = new ArrayList<>();
    connector.addRowListener(
        new IHRowListener() {
          @Override
          public void rowReceived(IRowMeta rowMeta, Object[] rowData) {
            if (rowData != null) {
              received.add(rowData);
            }
          }
        });
    connector.startStreaming((IDataContext) null);
    connector.waitUntilFinished();
    assertEquals(2, received.size());
    assertEquals("alpha", received.get(0)[0]);
    assertEquals(2L, received.get(1)[1]);
  }

  @Test
  void emptyRowsDescribeEmptyMeta() throws Exception {
    HInMemoryRowsConnector connector = new HInMemoryRowsConnector();
    assertEquals(0, connector.describeOutput(null).size());
  }
}
