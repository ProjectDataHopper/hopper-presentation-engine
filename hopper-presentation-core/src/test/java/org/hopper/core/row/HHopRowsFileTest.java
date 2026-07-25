package org.hopper.core.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HHopRowsFileTest {

  @TempDir Path tempDir;

  @Test
  void writeAndReadRoundTrip() throws Exception {
    Path file = tempDir.resolve("sample.hoprows");
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("label"));
    meta.addValueMeta(new ValueMetaInteger("ms"));
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {"Layout", 120L});
    rows.add(new Object[] {"Render", 80L});

    HHopRowsFile.write(file.toString(), meta, rows);
    assertTrue(HHopRowsFile.exists(file.toString()));

    HHopRowsFile.Snapshot snap = HHopRowsFile.read(file.toString());
    assertNotNull(snap.getRowMeta());
    assertEquals(2, snap.getRowMeta().size());
    assertEquals(2, snap.size());
    assertEquals("Layout", snap.getRows().get(0)[0]);
    assertEquals(120L, ((Number) snap.getRows().get(0)[1]).longValue());
    assertEquals("Render", snap.getRows().get(1)[0]);
  }

  @Test
  void writeAtomicOverwrites() throws Exception {
    Path file = tempDir.resolve("atomic.hoprows");
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("a"));
    List<Object[]> one = new ArrayList<>();
    one.add(new Object[] {"one"});
    HHopRowsFile.writeAtomic(file.toString(), meta, one);
    List<Object[]> two = new ArrayList<>();
    two.add(new Object[] {"two"});
    HHopRowsFile.writeAtomic(file.toString(), meta, two);
    HHopRowsFile.Snapshot snap = HHopRowsFile.read(file.toString());
    assertEquals(1, snap.size());
    assertEquals("two", snap.getRows().get(0)[0]);
    assertTrue(Files.exists(file));
  }
}
