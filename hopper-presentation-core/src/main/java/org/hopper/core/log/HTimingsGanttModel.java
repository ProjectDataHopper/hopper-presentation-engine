package org.hopper.core.log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hopper.presentation.component.types.chart.GanttTask;

/**
 * Builds {@link GanttTask} rows from {@link HMetricsUtil#buildTimingsSummary} spans (and optional
 * client soft-reload phases) for the refresh-timings Gantt panel.
 */
public final class HTimingsGanttModel {

  public static final String CLIENT_CODE = "HOPPER_CLIENT";

  private HTimingsGanttModel() {}

  /**
   * Convert timing spans into Gantt tasks on a relative timeline (0 … cycle).
   *
   * @param timings map from {@link HMetricsUtil#buildTimingsSummary}
   * @param clientPhases optional client phases: keys like xhrMs, pngMs, svgLoadMs, geometriesMs,
   *     paintMs, perceivedMs (numbers, ms). Appended after the last server end when present.
   * @param maxBars maximum task rows (top by duration; always keeps layout/render totals if present)
   */
  @SuppressWarnings("unchecked")
  public static List<GanttTask> fromTimings(
      Map<String, Object> timings, Map<String, Object> clientPhases, int maxBars) {
    List<GanttTask> out = new ArrayList<>();
    if (timings == null && (clientPhases == null || clientPhases.isEmpty())) {
      return out;
    }

    List<Map<String, Object>> spans = new ArrayList<>();
    if (timings != null) {
      Object spansObj = timings.get("spans");
      if (spansObj instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            spans.add((Map<String, Object>) m);
          }
        }
      }
      // Fall back to "top" if spans missing
      if (spans.isEmpty()) {
        Object topObj = timings.get("top");
        if (topObj instanceof List<?> list) {
          for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
              spans.add((Map<String, Object>) m);
            }
          }
        }
      }
    }

    long origin = Long.MAX_VALUE;
    long maxEnd = 0;
    for (Map<String, Object> s : spans) {
      long start = asLong(s.get("startMs"), -1);
      long end = asLong(s.get("endMs"), -1);
      if (start >= 0) {
        origin = Math.min(origin, start);
      }
      if (end >= 0) {
        maxEnd = Math.max(maxEnd, end);
      }
    }
    if (origin == Long.MAX_VALUE) {
      origin = 0;
    }

    List<GanttTask> serverTasks = new ArrayList<>();
    for (Map<String, Object> s : spans) {
      long start = asLong(s.get("startMs"), -1);
      long end = asLong(s.get("endMs"), -1);
      long ms = asLong(s.get("ms"), -1);
      if (start < 0 && ms >= 0) {
        start = origin;
        end = origin + ms;
      }
      if (start < 0 || end < 0) {
        continue;
      }
      if (end < start) {
        long t = start;
        start = end;
        end = t;
      }
      String code = string(s.get("code"));
      String subject = string(s.get("subject"));
      String description = string(s.get("description"));
      String label = buildLabel(code, subject, description);
      String group = groupForCode(code);
      String colorKey = StringUtils.isNotBlank(subject) ? subject : code;
      serverTasks.add(
          new GanttTask(label, start - origin, end - origin, group, colorKey));
      maxEnd = Math.max(maxEnd, end);
    }

    // Cap: keep longest bars + any layout/render totals
    int cap = maxBars > 0 ? maxBars : 40;
    if (serverTasks.size() > cap) {
      serverTasks.sort(Comparator.comparingLong(GanttTask::duration).reversed());
      List<GanttTask> kept = new ArrayList<>();
      for (GanttTask t : serverTasks) {
        if (kept.size() >= cap) {
          break;
        }
        kept.add(t);
      }
      serverTasks = kept;
      serverTasks.sort(Comparator.comparingLong(GanttTask::getStart));
    } else {
      serverTasks.sort(Comparator.comparingLong(GanttTask::getStart));
    }
    out.addAll(serverTasks);

    // Client phases after server max (sequential chain for visibility)
    if (clientPhases != null && !clientPhases.isEmpty()) {
      long cursor = maxEnd > origin ? maxEnd - origin : 0L;
      // Prefer wall-aligned chain of known keys
      String[][] phases = {
        {"xhrMs", "Client XHR"},
        {"pngMs", "Client PNG encode"},
        {"svgLoadMs", "Client image decode"},
        {"geometriesMs", "Client geometries"},
        {"paintMs", "Client paint"},
        {"refreshMs", "Client UI refresh"}
      };
      for (String[] phase : phases) {
        long ms = asLong(clientPhases.get(phase[0]), -1);
        if (ms <= 0) {
          continue;
        }
        out.add(new GanttTask(phase[1], cursor, cursor + ms, "Client", phase[0]));
        cursor += ms;
      }
    }

    return out;
  }

  /** Human label for a metrics span. */
  public static String buildLabel(String code, String subject, String description) {
    String sub = StringUtils.trimToEmpty(subject);
    String desc = StringUtils.trimToEmpty(description);
    String c = StringUtils.trimToEmpty(code);
    if (StringUtils.isNotBlank(sub) && StringUtils.isNotBlank(desc) && !desc.equals(sub)) {
      return desc + " · " + sub;
    }
    if (StringUtils.isNotBlank(sub)) {
      return sub;
    }
    if (StringUtils.isNotBlank(desc)) {
      return desc;
    }
    if (StringUtils.isNotBlank(c)) {
      return humanizeCode(c);
    }
    return "span";
  }

  public static String groupForCode(String code) {
    if (code == null) {
      return "Other";
    }
    if (code.contains("CONNECTOR")) {
      return "Connector";
    }
    if (code.contains("LAYOUT") || code.contains("FINGERPRINT") || code.contains("CACHE_LOOKUP")) {
      return "Layout";
    }
    if (code.contains("RENDER") || code.contains("IMAGE")) {
      return "Render";
    }
    if (code.contains("CLIENT") || CLIENT_CODE.equals(code)) {
      return "Client";
    }
    return "Other";
  }

  private static String humanizeCode(String code) {
    String s = code;
    if (s.startsWith("HOPPER_")) {
      s = s.substring("HOPPER_".length());
    }
    return s.replace('_', ' ').toLowerCase();
  }

  private static long asLong(Object o, long def) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o instanceof String s && StringUtils.isNotBlank(s)) {
      try {
        return Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
        try {
          return (long) Double.parseDouble(s.trim());
        } catch (NumberFormatException e2) {
          return def;
        }
      }
    }
    return def;
  }

  private static String string(Object o) {
    return o == null ? null : String.valueOf(o);
  }

  /** Convenience summary map for the timings panel header. */
  public static Map<String, Object> summaryChips(
      Map<String, Object> timings, Map<String, Object> clientPhases) {
    Map<String, Object> chips = new LinkedHashMap<>();
    if (timings != null) {
      chips.put("layoutMs", timings.get("layoutMs"));
      chips.put("renderMs", timings.get("renderMs"));
      chips.put("totalMs", timings.get("totalMs"));
      chips.put("wallMs", timings.get("wallMs"));
      chips.put("spanCount", timings.get("spanCount"));
    }
    if (clientPhases != null) {
      chips.put("xhrMs", clientPhases.get("xhrMs"));
      chips.put("perceivedMs", clientPhases.get("perceivedMs"));
      chips.put("pngMs", clientPhases.get("pngMs"));
    }
    return chips;
  }
}
