package org.hopper.presentation.connector.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HColumn;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.connector.types.selection.HSelectionConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

class ConnectorPreviewSupportTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void clampMaxRowsDefaultsAndCaps() {
    assertEquals(20, ConnectorPreviewSupport.clampMaxRows(null));
    assertEquals(20, ConnectorPreviewSupport.clampMaxRows(0));
    assertEquals(20, ConnectorPreviewSupport.clampMaxRows(-5));
    assertEquals(5, ConnectorPreviewSupport.clampMaxRows(5));
    assertEquals(100, ConnectorPreviewSupport.clampMaxRows(500));
  }

  @Test
  void samplesSourceConnectorOutputOnly() throws Exception {
    HSampleDataConnector sample = new HSampleDataConnector(50);
    HConnector sampleConn = ConnectorTestSupport.wrap("Sample Data", sample);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sampleConn);

    ConnectorPreviewResult result =
        ConnectorPreviewSupport.preview(ctx, sampleConn, 5);

    assertTrue(result.isOk(), () -> String.valueOf(result.getError()));
    assertNull(result.getInput(), "source has no upstream input pane");
    assertNotNull(result.getOutput());
    assertEquals(5, result.getOutput().getRows().size());
    assertEquals(5, result.getOutput().getRowCountReturned());
    assertTrue(result.getOutput().isTruncated());
    assertFalse(result.getOutput().getRowMeta().isEmpty());
    assertTrue(
        result.getOutput().getRowMeta().stream().anyMatch(v -> "name".equals(v.getName())));
  }

  @Test
  void samplesTransformInputAndOutput() throws Exception {
    HSampleDataConnector sample = new HSampleDataConnector(12);
    HConnector sampleConn = ConnectorTestSupport.wrap("sample", sample);

    HSelectionConnector selection =
        new HSelectionConnector(Collections.singletonList(new HColumn("name")));
    selection.setSourceConnectorName("sample");
    HConnector selectConn = ConnectorTestSupport.wrap("selected", selection);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sampleConn, selectConn);

    ConnectorPreviewResult result =
        ConnectorPreviewSupport.preview(ctx, selectConn, 10);

    assertTrue(result.isOk(), () -> String.valueOf(result.getError()));
    assertNotNull(result.getInput());
    assertEquals("sample", result.getInput().getConnectorName());
    assertEquals(10, result.getInput().getRows().size());
    assertTrue(result.getInput().isTruncated());

    assertNotNull(result.getOutput());
    assertEquals(10, result.getOutput().getRows().size());
    assertEquals(1, result.getOutput().getRowMeta().size());
    assertEquals("name", result.getOutput().getRowMeta().get(0).getName());
    // Each output row is a single projected column
    assertEquals(1, result.getOutput().getRows().get(0).size());
  }

  @Test
  void missingSourceReturnsStructuredError() throws Exception {
    HSelectionConnector selection =
        new HSelectionConnector(Collections.singletonList(new HColumn("name")));
    selection.setSourceConnectorName("does-not-exist");
    HConnector selectConn = ConnectorTestSupport.wrap("selected", selection);

    PresentationDataContext ctx = ConnectorTestSupport.dataContext(selectConn);

    ConnectorPreviewResult result =
        ConnectorPreviewSupport.preview(ctx, selectConn, 5);

    assertFalse(result.isOk());
    assertNotNull(result.getError());
    assertNotNull(result.getError().getSummary());
    assertTrue(
        result.getError().getSummary().toLowerCase().contains("does-not-exist")
            || result.getError().getSummary().toLowerCase().contains("unable")
            || result.getError().getSummary().toLowerCase().contains("source"));
  }

  @Test
  void emptyConnectorDefinitionFailsGracefully() {
    ConnectorPreviewResult result =
        ConnectorPreviewSupport.preview(
            ConnectorTestSupport.dataContext(), new HConnector("x", null), 5);
    assertFalse(result.isOk());
    assertNotNull(result.getError());
  }

  @Test
  void notTruncatedWhenFewerRowsThanMax() throws Exception {
    HSampleDataConnector sample = new HSampleDataConnector(3);
    HConnector sampleConn = ConnectorTestSupport.wrap("tiny", sample);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sampleConn);

    ConnectorPreviewResult result =
        ConnectorPreviewSupport.preview(ctx, sampleConn, 20);

    assertTrue(result.isOk());
    assertEquals(3, result.getOutput().getRows().size());
    assertFalse(result.getOutput().isTruncated());
  }

  @Test
  void displayStringsForSampleCells() throws Exception {
    HSampleDataConnector sample = new HSampleDataConnector(2);
    HConnector sampleConn = ConnectorTestSupport.wrap("s", sample);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sampleConn);

    ConnectorPreviewResult result =
        ConnectorPreviewSupport.preview(ctx, sampleConn, 2);

    List<List<String>> rows = result.getOutput().getRows();
    assertEquals(2, rows.size());
    // First column is id (integer as string)
    assertNotNull(rows.get(0).get(0));
    assertFalse(rows.get(0).get(0).isEmpty());
  }
}
