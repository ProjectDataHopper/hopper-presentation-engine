package org.hopper.core.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.hopper.config.HPresentationDataPaths;
import org.hopper.core.exception.HException;
import org.hopper.core.row.HHopRowsFile;
import org.hopper.presentation.component.types.chart.GanttTask;

/**
 * Persist refresh-timing spans as Hop binary row files under {@link
 * HPresentationDataPaths#timingsLatestFile(String)}.
 */
public final class HTimingsCapture {

  public static final String COL_LABEL = "label";
  public static final String COL_MS = "ms";
  public static final String COL_START_MS = "startMs";
  public static final String COL_END_MS = "endMs";
  public static final String COL_GROUP = "group";
  public static final String COL_COLOR_KEY = "colorKey";

  private HTimingsCapture() {}

  public static IRowMeta timingsRowMeta() {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString(COL_LABEL));
    meta.addValueMeta(new ValueMetaInteger(COL_MS));
    meta.addValueMeta(new ValueMetaInteger(COL_START_MS));
    meta.addValueMeta(new ValueMetaInteger(COL_END_MS));
    meta.addValueMeta(new ValueMetaString(COL_GROUP));
    meta.addValueMeta(new ValueMetaString(COL_COLOR_KEY));
    return meta;
  }

  public static List<Object[]> rowsFromTasks(List<GanttTask> tasks) {
    List<Object[]> rows = new ArrayList<>();
    if (tasks == null) {
      return rows;
    }
    for (GanttTask t : tasks) {
      if (t == null) {
        continue;
      }
      Object[] row = new Object[6];
      row[0] = t.getLabel();
      row[1] = t.duration();
      row[2] = t.getStart();
      row[3] = t.getEnd();
      row[4] = t.getGroup();
      row[5] = t.getColorKey();
      rows.add(row);
    }
    return rows;
  }

  /**
   * When admin capture is enabled and data path is set, write {@code latest.hoprows} for the
   * presentation. No-op otherwise. Never throws to callers of soft-reload.
   */
  public static boolean captureQuietly(
      String presentationName,
      Map<String, Object> timings,
      Map<String, Object> clientPhases,
      int maxBars) {
    if (!HPresentationDataPaths.isTimingsCapture() || !HPresentationDataPaths.isConfigured()) {
      return false;
    }
    if (presentationName == null || presentationName.isBlank()) {
      return false;
    }
    try {
      List<GanttTask> tasks = HTimingsGanttModel.fromTimings(timings, clientPhases, maxBars);
      return writeTasks(presentationName, tasks);
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean writeTasks(String presentationName, List<GanttTask> tasks)
      throws HException {
    if (!HPresentationDataPaths.isConfigured()) {
      throw new HException("server.data.path is not configured");
    }
    String dir = HPresentationDataPaths.timingsDir(presentationName);
    try {
      java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dir));
    } catch (Exception e) {
      throw new HException("Cannot create timings dir: " + dir, e);
    }
    String path = HPresentationDataPaths.timingsLatestFile(presentationName);
    HHopRowsFile.writeAtomic(path, timingsRowMeta(), rowsFromTasks(tasks));
    return true;
  }
}
