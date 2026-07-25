package org.hopper.core.row;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopEofException;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.vfs.HopVfs;
import org.hopper.core.exception.HException;

/**
 * Read/write Hop binary row files using {@link IRowMeta#writeMeta}, {@link IRowMeta#writeData},
 * {@link RowMeta#RowMeta(DataInputStream)}, and {@link IRowMeta#readData}.
 *
 * <p>Format: one metadata block followed by zero or more data rows until EOF.
 */
public final class HHopRowsFile {

  private HHopRowsFile() {}

  /** In-memory snapshot of a Hop rows file. */
  public static final class Snapshot {
    private final IRowMeta rowMeta;
    private final List<Object[]> rows;

    public Snapshot(IRowMeta rowMeta, List<Object[]> rows) {
      this.rowMeta = rowMeta != null ? rowMeta : new RowMeta();
      this.rows = rows != null ? rows : List.of();
    }

    public IRowMeta getRowMeta() {
      return rowMeta;
    }

    public List<Object[]> getRows() {
      return rows;
    }

    public int size() {
      return rows.size();
    }
  }

  /** Write metadata + all rows to a VFS path (overwrite). */
  public static void write(String vfsPath, IRowMeta rowMeta, List<Object[]> rows) throws HException {
    if (vfsPath == null || vfsPath.isBlank()) {
      throw new HException("Hop rows path is empty");
    }
    IRowMeta meta = rowMeta != null ? rowMeta : new RowMeta();
    List<Object[]> data = rows != null ? rows : List.of();
    try (OutputStream os = HopVfs.getOutputStream(vfsPath, false);
        DataOutputStream dos = new DataOutputStream(os)) {
      meta.writeMeta(dos);
      for (Object[] row : data) {
        meta.writeData(dos, row);
      }
      dos.flush();
    } catch (Exception e) {
      throw new HException("Error writing Hop rows file: " + vfsPath, e);
    }
  }

  /**
   * Atomic write: write to {@code path + ".tmp"} then rename over {@code path}. Best-effort on
   * filesystems that do not support atomic rename.
   */
  public static void writeAtomic(String vfsPath, IRowMeta rowMeta, List<Object[]> rows)
      throws HException {
    if (vfsPath == null || vfsPath.isBlank()) {
      throw new HException("Hop rows path is empty");
    }
    String tmp = vfsPath + ".tmp";
    write(tmp, rowMeta, rows);
    try {
      // Prefer NIO when both are local file: paths
      java.nio.file.Path target = toNioPath(vfsPath);
      java.nio.file.Path temp = toNioPath(tmp);
      if (target != null && temp != null) {
        java.nio.file.Files.createDirectories(target.getParent());
        java.nio.file.Files.move(
            temp,
            target,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        return;
      }
    } catch (Exception ignored) {
      // fall through to non-atomic overwrite
    }
    // Fallback: re-write destination and delete temp
    write(vfsPath, rowMeta, rows);
    try {
      HopVfs.getFileObject(tmp).delete();
    } catch (Exception ignored) {
      // best effort
    }
  }

  /** Read entire file into memory. */
  public static Snapshot read(String vfsPath) throws HException {
    return read(vfsPath, 0);
  }

  /**
   * Read up to {@code limit} data rows ({@code 0} = unlimited).
   *
   * @return snapshot (never null; empty meta/rows if file empty)
   */
  public static Snapshot read(String vfsPath, int limit) throws HException {
    if (vfsPath == null || vfsPath.isBlank()) {
      throw new HException("Hop rows path is empty");
    }
    try (InputStream is = HopVfs.getInputStream(vfsPath);
        DataInputStream dis = new DataInputStream(is)) {
      IRowMeta meta = new RowMeta(dis);
      List<Object[]> rows = new ArrayList<>();
      while (true) {
        if (limit > 0 && rows.size() >= limit) {
          break;
        }
        try {
          Object[] row = meta.readData(dis);
          rows.add(row);
        } catch (HopEofException eof) {
          break;
        }
      }
      return new Snapshot(meta, rows);
    } catch (HopFileException e) {
      throw new HException("Error reading Hop rows file: " + vfsPath, e);
    } catch (Exception e) {
      throw new HException("Error reading Hop rows file: " + vfsPath, e);
    }
  }

  /** Read only the row metadata (first block). */
  public static IRowMeta readMeta(String vfsPath) throws HException {
    if (vfsPath == null || vfsPath.isBlank()) {
      throw new HException("Hop rows path is empty");
    }
    try (InputStream is = HopVfs.getInputStream(vfsPath);
        DataInputStream dis = new DataInputStream(is)) {
      return new RowMeta(dis);
    } catch (Exception e) {
      throw new HException("Error reading Hop rows metadata: " + vfsPath, e);
    }
  }

  /** Whether a VFS path exists and is a readable file. */
  public static boolean exists(String vfsPath) {
    if (vfsPath == null || vfsPath.isBlank()) {
      return false;
    }
    try {
      return HopVfs.fileExists(vfsPath) && HopVfs.getFileObject(vfsPath).isFile();
    } catch (Exception e) {
      return false;
    }
  }

  private static java.nio.file.Path toNioPath(String vfsPath) {
    if (vfsPath == null) {
      return null;
    }
    String p = vfsPath;
    if (p.startsWith("file://")) {
      p = p.substring("file://".length());
    } else if (p.startsWith("file:")) {
      p = p.substring("file:".length());
    }
    // Only treat plain absolute/relative filesystem paths as NIO
    if (p.contains("://")) {
      return null;
    }
    try {
      return java.nio.file.Paths.get(p);
    } catch (Exception e) {
      return null;
    }
  }
}
