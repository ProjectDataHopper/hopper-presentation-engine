package org.hopper.core.log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.IMetrics;
import org.apache.hop.core.logging.Metrics;
import org.apache.hop.core.logging.MetricsRegistry;
import org.apache.hop.core.metrics.IMetricsSnapshot;
import org.apache.hop.core.metrics.MetricsDuration;
import org.apache.hop.core.metrics.MetricsSnapshotType;
import org.apache.hop.core.metrics.MetricsUtil;
import org.hopper.core.exception.HException;

/**
 * Hopper presentation metrics helpers on top of Hop {@link ILogChannel#snap} / {@link
 * MetricsRegistry}.
 *
 * <p>New instrumentation uses Hop's convention: <b>same metric code</b> for START and STOP, optional
 * {@code subject} (component/connector name). That matches {@link MetricsUtil#getLastDuration} and
 * supports a Gantt-friendly span list ({@link #collectSpans}).
 *
 * <p>Legacy dual-code helpers ({@link #getLastDuration(ILogChannel, String, String)}) remain for
 * existing {@code PRESENTATION_START_*} / {@code PRESENTATION_FINISH_*} snaps.
 */
public final class HMetricsUtil {

  // ── Legacy dual-code metrics (existing snaps) ───────────────────────────
  public static final String PRESENTATION_START_LAYOUT = "PRESENTATION_START_LAYOUT";
  public static final String PRESENTATION_FINISH_LAYOUT = "PRESENTATION_FINISH_LAYOUT";
  public static final String PRESENTATION_START_RENDER = "PRESENTATION_START_RENDER";
  public static final String PRESENTATION_FINISH_RENDER = "PRESENTATION_FINISH_RENDER";
  public static final String IMAGE_START_RENDER = "IMAGE_START_RENDER";
  public static final String IMAGE_FINISH_RENDER = "IMAGE_FINISH_RENDER";

  // ── Hop-style codes (START + STOP share the code) ───────────────────────
  public static final String CODE_PRESENTATION_LAYOUT = "HOPPER_PRESENTATION_LAYOUT";
  public static final String CODE_PRESENTATION_RENDER = "HOPPER_PRESENTATION_RENDER";
  public static final String CODE_LAYOUT_COMPONENT = "HOPPER_LAYOUT_COMPONENT";
  public static final String CODE_LAYOUT_CACHE_LOOKUP = "HOPPER_LAYOUT_CACHE_LOOKUP";
  public static final String CODE_COMPONENT_FINGERPRINT = "HOPPER_COMPONENT_FINGERPRINT";
  public static final String CODE_CONNECTOR_RETRIEVE = "HOPPER_CONNECTOR_RETRIEVE";
  public static final String CODE_CONNECTOR_CACHE_REPLAY = "HOPPER_CONNECTOR_CACHE_REPLAY";

  private HMetricsUtil() {}

  // ── Snap helpers ────────────────────────────────────────────────────────

  public static void start(ILogChannel log, String code, String description) {
    start(log, code, description, null);
  }

  public static void start(ILogChannel log, String code, String description, String subject) {
    if (log == null || !log.isGatheringMetrics()) {
      return;
    }
    log.snap(metric(MetricsSnapshotType.START, code, description), subject);
  }

  public static void stop(ILogChannel log, String code, String description) {
    stop(log, code, description, null);
  }

  public static void stop(ILogChannel log, String code, String description, String subject) {
    if (log == null || !log.isGatheringMetrics()) {
      return;
    }
    log.snap(metric(MetricsSnapshotType.STOP, code, description), subject);
  }

  private static IMetrics metric(MetricsSnapshotType type, String code, String description) {
    return new Metrics(type, code, description != null ? description : code);
  }

  // ── Duration readers ────────────────────────────────────────────────────

  /**
   * Last START→STOP duration for a Hop-style metric code (same code, START/STOP types).
   *
   * @return ms, or null if incomplete
   */
  public static Long lastMs(ILogChannel log, String metricsCode) {
    if (log == null || StringUtils.isBlank(metricsCode)) {
      return null;
    }
    MetricsDuration d = MetricsUtil.getLastDuration(log.getLogChannelId(), metricsCode);
    return d != null ? d.getDuration() : null;
  }

  /**
   * Last START→STOP for {@code metricsCode} restricted to {@code subject} (component/connector
   * name).
   */
  public static Long lastMs(ILogChannel log, String metricsCode, String subject) {
    if (log == null || StringUtils.isBlank(metricsCode)) {
      return null;
    }
    Queue<IMetricsSnapshot> list =
        MetricsRegistry.getInstance().getSnapshotList(log.getLogChannelId());
    if (list == null || list.isEmpty()) {
      return null;
    }
    IMetricsSnapshot lastStart = null;
    IMetricsSnapshot lastStop = null;
    for (IMetricsSnapshot snapshot : list) {
      if (snapshot == null || snapshot.getMetric() == null) {
        continue;
      }
      if (!metricsCode.equals(snapshot.getMetric().getCode())) {
        continue;
      }
      if (!Objects.equals(subject, snapshot.getSubject())) {
        continue;
      }
      if (snapshot.getMetric().getType() == MetricsSnapshotType.START) {
        lastStart = snapshot;
      } else if (snapshot.getMetric().getType() == MetricsSnapshotType.STOP) {
        lastStop = snapshot;
      }
    }
    if (lastStart == null || lastStop == null || lastStart.getDate() == null || lastStop.getDate() == null) {
      return null;
    }
    return lastStop.getDate().getTime() - lastStart.getDate().getTime();
  }

  /**
   * Individual START→STOP spans on this channel, oldest first. Shape is Gantt-ready: each entry has
   * {@code code}, {@code subject}, {@code description}, {@code startMs}, {@code endMs}, {@code ms}.
   */
  public static List<Map<String, Object>> collectSpans(ILogChannel log) {
    List<Map<String, Object>> spans = new ArrayList<>();
    if (log == null) {
      return spans;
    }
    Queue<IMetricsSnapshot> list =
        MetricsRegistry.getInstance().getSnapshotList(log.getLogChannelId());
    if (list == null || list.isEmpty()) {
      return spans;
    }
    // open START key → snapshot
    Map<String, IMetricsSnapshot> open = new HashMap<>();
    for (IMetricsSnapshot snapshot : list) {
      if (snapshot == null || snapshot.getMetric() == null || snapshot.getDate() == null) {
        continue;
      }
      IMetrics m = snapshot.getMetric();
      MetricsSnapshotType type = m.getType();
      if (type != MetricsSnapshotType.START && type != MetricsSnapshotType.STOP) {
        continue;
      }
      String key = spanKey(m.getCode(), snapshot.getSubject());
      if (type == MetricsSnapshotType.START) {
        open.put(key, snapshot);
        continue;
      }
      // STOP
      IMetricsSnapshot start = open.remove(key);
      if (start == null || start.getDate() == null) {
        continue;
      }
      long startMs = start.getDate().getTime();
      long endMs = snapshot.getDate().getTime();
      Map<String, Object> span = new LinkedHashMap<>();
      span.put("code", m.getCode());
      span.put("subject", snapshot.getSubject());
      span.put(
          "description",
          m.getDescription() != null ? m.getDescription() : m.getCode());
      span.put("startMs", startMs);
      span.put("endMs", endMs);
      span.put("ms", Math.max(0L, endMs - startMs));
      spans.add(span);
    }
    return spans;
  }

  /**
   * Summary map for API/debug: coarse totals + top spans by duration (Gantt data source).
   *
   * <p>{@code totalMs} is {@code layoutMs + renderMs} for the <em>last</em> presentation pass —
   * not wall-clock from the earliest historical START on the log channel (LoggingRegistry often
   * reuses the same channel id for a presentation name, so the snapshot list accumulates across
   * soft-reloads; min→max would look like minutes/hours).
   *
   * @param topN max detailed spans (0 = none); spans are filtered to the last layout+render cycle
   */
  public static Map<String, Object> buildTimingsSummary(ILogChannel log, int topN) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (log == null) {
      return out;
    }
    List<Map<String, Object>> allSpans = collectSpans(log);

    Long layoutMs = lastMs(log, CODE_PRESENTATION_LAYOUT);
    if (layoutMs == null) {
      // fall back to legacy dual-code snaps
      try {
        layoutMs =
            getLastDuration(log, PRESENTATION_START_LAYOUT, PRESENTATION_FINISH_LAYOUT);
      } catch (HException ignored) {
        layoutMs = null;
      }
    }
    Long renderMs = lastMs(log, CODE_PRESENTATION_RENDER);
    if (renderMs == null) {
      try {
        renderMs =
            getLastDuration(log, PRESENTATION_START_RENDER, PRESENTATION_FINISH_RENDER);
      } catch (HException ignored) {
        renderMs = null;
      }
    }

    // Coarse total = last layout + last render only (not historical epoch span)
    long totalMs =
        (layoutMs != null ? layoutMs : 0L) + (renderMs != null ? renderMs : 0L);

    // Keep only spans from the last presentation layout START (this soft-reload)
    long cycleStartMs = lastStartEpochMs(log, CODE_PRESENTATION_LAYOUT);
    if (cycleStartMs < 0) {
      cycleStartMs = lastStartEpochMs(log, PRESENTATION_START_LAYOUT);
    }
    List<Map<String, Object>> spans = allSpans;
    if (cycleStartMs >= 0) {
      final long since = cycleStartMs;
      spans = new ArrayList<>();
      for (Map<String, Object> s : allSpans) {
        Object a = s.get("startMs");
        if (a instanceof Number && ((Number) a).longValue() >= since) {
          spans.add(s);
        }
      }
    }

    out.put("layoutMs", layoutMs);
    out.put("renderMs", renderMs);
    out.put("totalMs", totalMs);
    out.put("logChannelId", log.getLogChannelId());
    out.put("spanCount", spans.size());
    out.put("spanCountAllTime", allSpans.size());

    if (topN > 0 && !spans.isEmpty()) {
      List<Map<String, Object>> sorted = new ArrayList<>(spans);
      sorted.sort(
          Comparator.comparingLong(
                  (Map<String, Object> m) ->
                      m.get("ms") instanceof Number ? ((Number) m.get("ms")).longValue() : 0L)
              .reversed());
      int n = Math.min(topN, sorted.size());
      out.put("top", new ArrayList<>(sorted.subList(0, n)));
      // Timeline for this cycle only — Gantt input (startMs/endMs/ms)
      out.put("spans", spans);
    }
    return out;
  }

  /** Epoch ms of the last START snap for {@code metricsCode}, or -1. */
  private static long lastStartEpochMs(ILogChannel log, String metricsCode) {
    if (log == null || StringUtils.isBlank(metricsCode)) {
      return -1L;
    }
    Queue<IMetricsSnapshot> list =
        MetricsRegistry.getInstance().getSnapshotList(log.getLogChannelId());
    if (list == null) {
      return -1L;
    }
    long last = -1L;
    for (IMetricsSnapshot snapshot : list) {
      if (snapshot == null || snapshot.getMetric() == null || snapshot.getDate() == null) {
        continue;
      }
      IMetrics m = snapshot.getMetric();
      // Match hop-style code, or legacy dual-code key (PRESENTATION_START_LAYOUT, …)
      boolean codeMatch =
          metricsCode.equals(m.getCode()) || metricsCode.equals(snapshot.getKey());
      if (!codeMatch) {
        continue;
      }
      if (m.getType() == MetricsSnapshotType.START || metricsCode.contains("START")) {
        last = snapshot.getDate().getTime();
      }
    }
    return last;
  }

  private static String spanKey(String code, String subject) {
    return code + "\0" + (subject == null ? "" : subject);
  }

  /**
   * Return the time difference in miliseconds between the end and the start
   *
   * @param log
   * @param startMetricsCode
   * @param finishMetricsCode
   * @throws HException in case the logChannel, start or end code couldn't be found in the registry
   * @return
   */
  public static long getLastDuration(
      ILogChannel log, String startMetricsCode, String finishMetricsCode) throws HException {

    String logChannelId = log.getLogChannelId();

    MetricsRegistry registry = MetricsRegistry.getInstance();

    final AtomicLong startTime = new AtomicLong(-1L);
    final AtomicLong endTime = new AtomicLong(-1L);

    Queue<IMetricsSnapshot> snapshotList = registry.getSnapshotList(logChannelId);
    if (snapshotList == null) {
      throw new HException(
          "Unable to find metrics snapshot list for log channel ID " + logChannelId);
    }

    snapshotList.stream()
        .forEach(
            snapshot -> {
              if (snapshot.getKey().equals(startMetricsCode)) {
                startTime.set(snapshot.getDate().getTime());
              }
              if (snapshot.getKey().equals(finishMetricsCode)) {
                endTime.set(snapshot.getDate().getTime());
              }
            });

    if (startTime.get() < 0) {
      throw new HException(
          "Unable to find start metrics for code '"
              + startMetricsCode
              + "' in log channel with ID "
              + logChannelId);
    }
    if (endTime.get() < 0) {
      throw new HException(
          "Unable to find end metrics for code '"
              + finishMetricsCode
              + "' in log channel with ID "
              + logChannelId);
    }
    return Math.abs(endTime.get() - startTime.get());
  }

  /** Package-visible for tests: epoch ms of a Date. */
  static long dateMs(Date d) {
    return d != null ? d.getTime() : 0L;
  }
}
