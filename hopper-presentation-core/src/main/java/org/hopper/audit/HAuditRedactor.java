package org.hopper.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies PII / sensitive-data policy to audit events before they reach sinks. Mutates a defensive
 * copy of design/execution maps so callers can still hold the original structure if needed.
 */
public class HAuditRedactor {

  public static final String REDACTED = "***";

  private final HAuditConfig config;

  public HAuditRedactor(HAuditConfig config) {
    this.config = config != null ? config : HAuditConfig.defaults();
  }

  /**
   * Returns the same event instance after redacting nested design/execution maps in place (with
   * replaced nested maps so partial shared state is not a problem for typical emit paths).
   */
  @SuppressWarnings("unchecked")
  public HAuditEvent redact(HAuditEvent event) {
    if (event == null) {
      return null;
    }

    if (event.getDesign() != null && !event.getDesign().isEmpty()) {
      event.setDesign(redactDesign(copyMap(event.getDesign())));
    }
    if (event.getExecution() != null && !event.getExecution().isEmpty()) {
      event.setExecution(redactExecution(copyMap(event.getExecution())));
    }
    return event;
  }

  private Map<String, Object> redactDesign(Map<String, Object> design) {
    Object parameters = design.get("parameters");
    if (parameters instanceof Map<?, ?> paramMap) {
      Map<String, Object> redactedParams = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : paramMap.entrySet()) {
        String name = entry.getKey() != null ? entry.getKey().toString() : "";
        Object raw = entry.getValue();
        if (raw instanceof Map<?, ?> valueMap) {
          Map<String, Object> value = copyMap((Map<?, ?>) valueMap);
          if (shouldRedactParameter(name)) {
            value.put("value", REDACTED);
            value.put("redacted", true);
          }
          redactedParams.put(name, value);
        } else {
          if (shouldRedactParameter(name)) {
            redactedParams.put(name, REDACTED);
          } else {
            redactedParams.put(name, raw);
          }
        }
      }
      design.put("parameters", redactedParams);
    }
    return design;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> redactExecution(Map<String, Object> execution) {
    Object runs = execution.get("connectorRuns");
    if (runs instanceof List<?> list) {
      List<Object> redactedRuns = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> runMap) {
          Map<String, Object> run = copyMap(runMap);
          if (!config.isIncludeSqlText()) {
            run.remove("statementText");
          } else {
            Object text = run.get("statementText");
            if (text instanceof String s && s.length() > config.getMaxStatementLength()) {
              run.put(
                  "statementText",
                  s.substring(0, Math.max(0, config.getMaxStatementLength())) + "…");
            }
          }
          if (!config.isIncludeRowSamples()) {
            run.remove("rowSamples");
            run.remove("sampleRows");
          }
          redactedRuns.add(run);
        } else {
          redactedRuns.add(item);
        }
      }
      execution.put("connectorRuns", redactedRuns);
    }
    if (!config.isIncludeRowSamples()) {
      execution.remove("rowSamples");
      execution.remove("sampleRows");
    }
    return execution;
  }

  private boolean shouldRedactParameter(String name) {
    if (config.isRedactParameterValues()) {
      return true;
    }
    if (name == null || name.isBlank()) {
      return false;
    }
    Set<String> names = config.getRedactParameterNames();
    return names != null && names.contains(name.toLowerCase(Locale.ROOT));
  }

  private static Map<String, Object> copyMap(Map<?, ?> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    if (source == null) {
      return copy;
    }
    for (Map.Entry<?, ?> e : source.entrySet()) {
      if (e.getKey() != null) {
        copy.put(e.getKey().toString(), e.getValue());
      }
    }
    return copy;
  }
}
