package org.hopper.audit.lineage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hopper.audit.HAuditOutcome;

/**
 * In-flight collector for design/execution lineage during a presentation layout or connector
 * preview. Attach to {@link org.hopper.presentation.datacontext.IDataContext}.
 *
 * <p>Thread-safe for concurrent connector runs on the same request (row counts use atomics).
 */
public class HExecutionTrace {

  private static final HExecutionTrace NOOP = new NoOpExecutionTrace();

  private final Instant startedAt = Instant.now();
  private Instant finishedAt;
  private long layoutMs;
  private long renderMs;
  private int pageCount;
  private HAuditOutcome outcome = HAuditOutcome.SUCCESS;
  private String errorMessage;

  private final List<HConnectorRun> connectorRuns = new CopyOnWriteArrayList<>();
  private final List<HComponentRun> componentRuns = new CopyOnWriteArrayList<>();
  private final Deque<String> connectorNameStack = new ArrayDeque<>();
  private final Object stackLock = new Object();

  public static HExecutionTrace create() {
    return new HExecutionTrace();
  }

  /** No-op trace used when auditing is disabled or not requested. */
  public static HExecutionTrace noop() {
    return NOOP;
  }

  public boolean isNoop() {
    return false;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setLayoutMs(long layoutMs) {
    this.layoutMs = layoutMs;
  }

  public long getLayoutMs() {
    return layoutMs;
  }

  public void setRenderMs(long renderMs) {
    this.renderMs = renderMs;
  }

  public long getRenderMs() {
    return renderMs;
  }

  public void setPageCount(int pageCount) {
    this.pageCount = pageCount;
  }

  public int getPageCount() {
    return pageCount;
  }

  public HAuditOutcome getOutcome() {
    return outcome;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void finishSuccess() {
    finishedAt = Instant.now();
    outcome = HAuditOutcome.SUCCESS;
  }

  public void finishFailure(Throwable error) {
    finishedAt = Instant.now();
    outcome = HAuditOutcome.FAILURE;
    if (error != null) {
      errorMessage = error.getMessage();
    }
  }

  public long getDurationMs() {
    Instant end = finishedAt != null ? finishedAt : Instant.now();
    return Math.max(0, end.toEpochMilli() - startedAt.toEpochMilli());
  }

  public void pushConnectorName(String name) {
    if (name == null) {
      return;
    }
    synchronized (stackLock) {
      connectorNameStack.push(name);
    }
  }

  public void popConnectorName() {
    synchronized (stackLock) {
      if (!connectorNameStack.isEmpty()) {
        connectorNameStack.pop();
      }
    }
  }

  public String peekConnectorName() {
    synchronized (stackLock) {
      return connectorNameStack.peek();
    }
  }

  public HConnectorRun beginConnectorRun(String pluginId, String sourceConnectorName) {
    HConnectorRun run = new HConnectorRun();
    run.setPluginId(pluginId);
    run.setSourceConnectorName(sourceConnectorName);
    run.setConnectorName(peekConnectorName());
    connectorRuns.add(run);
    return run;
  }

  public void addComponentRun(HComponentRun run) {
    if (run != null) {
      componentRuns.add(run);
    }
  }

  public List<HConnectorRun> getConnectorRuns() {
    return Collections.unmodifiableList(new ArrayList<>(connectorRuns));
  }

  public List<HComponentRun> getComponentRuns() {
    return Collections.unmodifiableList(new ArrayList<>(componentRuns));
  }

  /** Execution section for {@link org.hopper.audit.HAuditEvent#setExecution}. */
  public Map<String, Object> toExecutionMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("startedAt", startedAt.toString());
    map.put("finishedAt", finishedAt != null ? finishedAt.toString() : null);
    map.put("durationMs", getDurationMs());
    map.put("layoutMs", layoutMs);
    map.put("renderMs", renderMs);
    map.put("pageCount", pageCount);
    map.put("outcome", outcome != null ? outcome.name() : null);
    map.put("errorMessage", errorMessage);
    List<Map<String, Object>> runs = new ArrayList<>();
    for (HConnectorRun run : connectorRuns) {
      runs.add(run.toMap());
    }
    map.put("connectorRuns", runs);
    if (!componentRuns.isEmpty()) {
      List<Map<String, Object>> components = new ArrayList<>();
      for (HComponentRun run : componentRuns) {
        components.add(run.toMap());
      }
      map.put("componentRuns", components);
    }
    return map;
  }

  /**
   * SHA-256 hex fingerprint of normalized SQL/statement text (whitespace collapsed, lower-cased).
   */
  public static String fingerprintStatement(String statement) {
    if (statement == null || statement.isBlank()) {
      return null;
    }
    String normalized = statement.replaceAll("\\s+", " ").trim().toLowerCase();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return "sha256:unavailable";
    }
  }

  private static final class NoOpExecutionTrace extends HExecutionTrace {
    @Override
    public boolean isNoop() {
      return true;
    }

    @Override
    public HConnectorRun beginConnectorRun(String pluginId, String sourceConnectorName) {
      return new HConnectorRun();
    }

    @Override
    public void addComponentRun(HComponentRun run) {
      // no-op
    }

    @Override
    public void pushConnectorName(String name) {
      // no-op
    }

    @Override
    public void popConnectorName() {
      // no-op
    }
  }
}
