package org.hopper.presentation.connector.types.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;
import org.hopper.core.row.HHopRowsFile;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.util.BasePresentationUtil;

class HBinaryRowsConnectorTest {

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void streamsWrittenHopRowsFile() throws Exception {
    Path file = tempDir.resolve("t.hoprows");
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("label"));
    meta.addValueMeta(new ValueMetaInteger("ms"));
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {"A", 10L});
    rows.add(new Object[] {"B", 20L});
    HHopRowsFile.write(file.toString(), meta, rows);

    HBinaryRowsConnector plugin = new HBinaryRowsConnector();
    plugin.setFilename(file.toString());

    HPresentation presentation = new HPresentation();
    presentation.setName("bin-test");
    PresentationDataContext ctx =
        new PresentationDataContext(presentation, new MemoryMetadataProvider());

    IRowMeta out = plugin.describeOutput(ctx);
    assertEquals(2, out.size());

    HConnector wrapper = new HConnector("bin", plugin);
    List<?> retrieved = wrapper.retrieveRows(ctx);
    assertNotNull(retrieved);
    assertEquals(2, retrieved.size());
  }

  @Test
  void cloneCopiesFields() {
    HBinaryRowsConnector a = new HBinaryRowsConnector();
    a.setFilename("/tmp/x.hoprows");
    a.setLimit(5);
    a.setCacheOnDisk(true);
    HBinaryRowsConnector b = a.clone();
    assertEquals("/tmp/x.hoprows", b.getFilename());
    assertEquals(5, b.getLimit());
    assertTrue(b.isCacheOnDisk());
  }
}
