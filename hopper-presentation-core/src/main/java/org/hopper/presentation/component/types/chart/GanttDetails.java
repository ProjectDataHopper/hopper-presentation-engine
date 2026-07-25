package org.hopper.presentation.component.types.chart;

import java.util.ArrayList;
import java.util.List;

/** Parsed Gantt data prepared in {@code processSourceData} (or seeded inline for timings). */
public class GanttDetails {
  public List<GanttTask> tasks = new ArrayList<>();

  /** Inclusive timeline minimum (min of task starts). */
  public long minStart = 0;

  /** Exclusive-ish timeline maximum (max of task ends). */
  public long maxEnd = 0;

  public String emptyMessage;

  public long span() {
    return Math.max(0L, maxEnd - minStart);
  }

  public boolean isEmpty() {
    return tasks == null || tasks.isEmpty();
  }

  /** Recompute min/max from {@link #tasks}. */
  public void recomputeBounds() {
    if (isEmpty()) {
      minStart = 0;
      maxEnd = 0;
      return;
    }
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (GanttTask t : tasks) {
      if (t == null) {
        continue;
      }
      min = Math.min(min, t.getStart());
      max = Math.max(max, t.getEnd());
    }
    if (min == Long.MAX_VALUE) {
      minStart = 0;
      maxEnd = 0;
    } else {
      minStart = min;
      maxEnd = max <= min ? min + 1 : max;
    }
  }
}
