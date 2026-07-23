package org.hopper.presentation.connector.types.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.types.distinct.HDistinctConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.connector.types.selection.HSelectionConnector;
import org.hopper.presentation.connector.types.sort.HSortConnector;
import org.hopper.core.HSortMethod;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HChainConnectorTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void chainsSelectionOverSampleData() throws Exception {
    HSampleDataConnector sample = new HSampleDataConnector(8);
    HConnector sampleConn = ConnectorTestSupport.wrap("sample", sample);

    HSelectionConnector selection =
        new HSelectionConnector(Collections.singletonList(new HColumn("name")));
    HChainConnector chain =
        new HChainConnector("sample", Collections.singletonList(selection));
    HConnector chainConn = ConnectorTestSupport.wrap("chained", chain);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sampleConn, chainConn);

    assertEquals(1, chain.describeOutput(ctx).size());
    assertEquals("name", chain.describeOutput(ctx).getValueMeta(0).getName());

    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(chainConn, ctx);
    assertEquals(8, rows.size());
    assertEquals(1, rows.get(0).getRowMeta().size());
    assertNull(rows.get(0).getRowMeta().searchValueMeta("id"));
  }

  /**
   * Multi-step chains wire intermediate sources as {@code __ChainConnector_N}. describeOutput must
   * use {@link org.hopper.presentation.datacontext.ChainDataContext} so those names resolve (streaming
   * already did).
   */
  @Test
  void multiStepDescribeUsesChainContext() throws Exception {
    HSampleDataConnector sample = new HSampleDataConnector(12);
    HConnector sampleConn = ConnectorTestSupport.wrap("sample", sample);

    HSelectionConnector selection =
        new HSelectionConnector(
            Arrays.asList(new HColumn("name"), new HColumn("color")));
    HSortConnector sort =
        new HSortConnector(
            Collections.singletonList(new HColumn("color")),
            Collections.singletonList(new HSortMethod()));
    HDistinctConnector distinct = new HDistinctConnector();

    List<IHConnector> steps = Arrays.asList(selection, sort, distinct);
    HChainConnector chain = new HChainConnector("sample", steps);
    HConnector chainConn = ConnectorTestSupport.wrap("product-style-chain", chain);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sampleConn, chainConn);

    IRowMeta meta =
        assertDoesNotThrow(
            () -> chain.describeOutput(ctx),
            "describeOutput must resolve intermediate chain step sources");
    assertEquals(2, meta.size());
    assertEquals("name", meta.getValueMeta(0).getName());
    assertEquals("color", meta.getValueMeta(1).getName());

    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(chainConn, ctx);
    // 12 sample rows with repeating colors → fewer after adjacent distinct on sorted color
    assertEquals(2, rows.get(0).getRowMeta().size());
  }
}
