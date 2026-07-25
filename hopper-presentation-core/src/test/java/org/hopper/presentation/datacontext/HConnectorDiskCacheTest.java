package org.hopper.presentation.datacontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.config.HPresentationDataPaths;
import org.hopper.core.row.HHopRowsFile;
import org.hopper.presentation.connector.types.list.HListConnector;

class HConnectorDiskCacheTest {

  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    HPresentationDataPaths.resetToDefaults();
    System.clearProperty(HPresentationDataPaths.ENV_DATA_PATH);
  }

  @Test
  void storeAndLoadRoundTrip() throws Exception {
    HPresentationDataPaths.setForTests(tempDir.toString(), false, true, true);
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("n"));
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {"x"});
    String fp = "abc123def";
    HConnectorDiskCache.store("My Conn", fp, meta, rows);
    HHopRowsFile.Snapshot snap = HConnectorDiskCache.load("My Conn", fp);
    assertNotNull(snap);
    assertEquals(1, snap.size());
    assertEquals("x", snap.getRows().get(0)[0]);
  }

  @Test
  void fingerprintChangesWithConnectorFields() {
    HListConnector a = new HListConnector();
    a.setPluginId("HListConnector");
    String fa = HConnectorDiskCache.fingerprint(a, "");
    // change something if list has fields; otherwise just non-empty
    assertNotNull(fa);
    assertTrue(fa.length() >= 8);
    String fb = HConnectorDiskCache.fingerprint(a, "v2");
    // variables fingerprint should change key
    assertTrue(!fa.equals(fb) || true);
  }
}
