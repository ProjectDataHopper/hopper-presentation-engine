package org.hopper.presentation.component.types.chart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One bar on a Gantt chart (task name + timeline interval). Serializable for embedded snapshots.
 *
 * <p>Start/end use {@code long} (typically milliseconds) because Hop JSON metadata supports
 * int/long but not double — double fields were previously serialized as empty objects {@code {}}
 * and failed to load.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GanttTask {
  /** Row label shown on the left. */
  @HopMetadataProperty private String label;

  /** Timeline start (same unit as {@link #end}, typically ms relative or epoch). */
  @HopMetadataProperty private long start;

  /** Timeline end (exclusive-friendly; must be &gt;= start). */
  @HopMetadataProperty private long end;

  /** Optional group / swimlane. */
  @HopMetadataProperty private String group;

  /** Optional stable color key for theme palette. */
  @HopMetadataProperty private String colorKey;

  public long duration() {
    return Math.max(0L, end - start);
  }
}
