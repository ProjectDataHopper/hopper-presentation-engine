package org.hopper.audit;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import org.apache.hop.core.Const;

/** Runtime configuration for the audit emitter, redaction, and bootstrap sinks. */
@Getter
@Builder(toBuilder = true)
public class HAuditConfig {

  public enum QueueFullPolicy {
    DROP,
    BLOCK
  }

  @Builder.Default private final boolean enabled = true;
  @Builder.Default private final boolean failOpen = true;
  @Builder.Default private final boolean async = true;
  @Builder.Default private final int queueSize = 10_000;
  @Builder.Default private final QueueFullPolicy queueFullPolicy = QueueFullPolicy.DROP;

  /** When true, all parameter values in design.parameters are replaced with {@code ***}. */
  @Builder.Default private final boolean redactParameterValues = false;

  /** Parameter names (case-insensitive) whose values are always redacted. */
  @Builder.Default private final Set<String> redactParameterNames = Set.of();

  /** Include SQL/statement text on connector runs (fingerprints always kept when present). */
  @Builder.Default private final boolean includeSqlText = true;

  @Builder.Default private final int maxStatementLength = 4000;

  @Builder.Default private final boolean includeRowSamples = false;

  /** Bootstrap sinks from properties (before metadata). */
  @Builder.Default private final boolean bootstrapLogging = true;

  @Builder.Default private final String bootstrapJsonlPath = "";

  public static HAuditConfig defaults() {
    return HAuditConfig.builder().build();
  }

  public static HAuditConfig fromProperties(Properties props) {
    Properties p = props != null ? props : new Properties();
    return HAuditConfig.builder()
        .enabled(Const.toBoolean(p.getProperty("audit.enabled", "true")))
        .failOpen(Const.toBoolean(p.getProperty("audit.fail-open", "true")))
        .async(Const.toBoolean(p.getProperty("audit.async", "true")))
        .queueSize(parseInt(p.getProperty("audit.queue.size"), 10_000))
        .queueFullPolicy(parsePolicy(p.getProperty("audit.queue.full-policy", "drop")))
        .redactParameterValues(
            Const.toBoolean(p.getProperty("audit.redact.parameter-values", "false")))
        .redactParameterNames(
            parseCsvSet(p.getProperty("audit.redact.parameter-names", "ssn,email,password,secret")))
        .includeSqlText(Const.toBoolean(p.getProperty("audit.include.sql-text", "true")))
        .maxStatementLength(parseInt(p.getProperty("audit.max-statement-length"), 4000))
        .includeRowSamples(Const.toBoolean(p.getProperty("audit.include.row-samples", "false")))
        .bootstrapLogging(Const.toBoolean(p.getProperty("audit.bootstrap.logging", "true")))
        .bootstrapJsonlPath(nullToEmpty(p.getProperty("audit.bootstrap.jsonl.path", "")))
        .build();
  }

  private static QueueFullPolicy parsePolicy(String value) {
    if (value == null || value.isBlank()) {
      return QueueFullPolicy.DROP;
    }
    if ("block".equalsIgnoreCase(value.trim())) {
      return QueueFullPolicy.BLOCK;
    }
    return QueueFullPolicy.DROP;
  }

  private static int parseInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static Set<String> parseCsvSet(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    LinkedHashSet<String> set = new LinkedHashSet<>();
    for (String part : csv.split("[,\\s]+")) {
      if (!part.isBlank()) {
        set.add(part.trim().toLowerCase(Locale.ROOT));
      }
    }
    return Collections.unmodifiableSet(set);
  }
}
