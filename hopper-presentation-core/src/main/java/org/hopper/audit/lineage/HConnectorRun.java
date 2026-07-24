package org.hopper.audit.lineage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import org.hopper.audit.HAuditOutcome;

/** One connector {@code startStreaming} execution within an {@link HExecutionTrace}. */
@Getter
@Setter
public class HConnectorRun {

  private String connectorName;
  private String pluginId;
  private String sourceConnectorName;
  private String databaseConnectionName;
  private String statementText;
  private String statementFingerprint;
  private final AtomicLong rowCount = new AtomicLong();
  private Instant startedAt = Instant.now();
  private Instant finishedAt;
  private long durationMs;
  private HAuditOutcome outcome = HAuditOutcome.SUCCESS;
  private String errorMessage;
  private final Map<String, Object> attributes = new LinkedHashMap<>();

  public void incrementRowCount() {
    rowCount.incrementAndGet();
  }

  public long getRowCount() {
    return rowCount.get();
  }

  public void completeSuccess() {
    finishedAt = Instant.now();
    durationMs = Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
    outcome = HAuditOutcome.SUCCESS;
  }

  public void completeFailure(Throwable error) {
    finishedAt = Instant.now();
    durationMs = Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
    outcome = HAuditOutcome.FAILURE;
    if (error != null) {
      errorMessage = error.getMessage();
    }
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("connectorName", connectorName);
    map.put("pluginId", pluginId);
    map.put("sourceConnectorName", sourceConnectorName);
    map.put("databaseConnectionName", databaseConnectionName);
    map.put("statementFingerprint", statementFingerprint);
    map.put("statementText", statementText);
    map.put("rowCount", getRowCount());
    map.put("startedAt", startedAt != null ? startedAt.toString() : null);
    map.put("finishedAt", finishedAt != null ? finishedAt.toString() : null);
    map.put("durationMs", durationMs);
    map.put("outcome", outcome != null ? outcome.name() : null);
    map.put("errorMessage", errorMessage);
    if (!attributes.isEmpty()) {
      map.put("attributes", new LinkedHashMap<>(attributes));
    }
    return map;
  }
}
