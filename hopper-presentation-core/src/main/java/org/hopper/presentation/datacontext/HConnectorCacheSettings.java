package org.hopper.presentation.datacontext;

import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/**
 * Process-wide runtime knobs for the layout-scoped connector result cache. Updated from admin
 * settings (hot-reload) and readable from core without depending on the REST module.
 */
public final class HConnectorCacheSettings {

  public static final String KEY_ENABLED = "server.connector-cache.enabled";
  public static final String KEY_MAX_ROWS = "server.connector-cache.max-rows";

  public static final boolean DEFAULT_ENABLED = true;
  /** Default cap: enough for typical chart/table sources; large CSV dumps skip caching. */
  public static final int DEFAULT_MAX_ROWS = 50_000;

  private static volatile boolean enabled = DEFAULT_ENABLED;
  private static volatile int maxRows = DEFAULT_MAX_ROWS;

  private HConnectorCacheSettings() {}

  public static boolean isEnabled() {
    return enabled;
  }

  public static int getMaxRows() {
    return maxRows;
  }

  /** Apply from merged admin/bootstrap properties (and optional system properties). */
  public static void applyFromProperties(Properties properties) {
    boolean en = DEFAULT_ENABLED;
    int max = DEFAULT_MAX_ROWS;
    if (properties != null) {
      en = parseBoolean(properties.getProperty(KEY_ENABLED), DEFAULT_ENABLED);
      max = parseInt(properties.getProperty(KEY_MAX_ROWS), DEFAULT_MAX_ROWS);
    }
    // System properties win for emergency override / tests
    String sysEn = System.getProperty(KEY_ENABLED);
    if (StringUtils.isNotBlank(sysEn)) {
      en = parseBoolean(sysEn, en);
    }
    String sysMax = System.getProperty(KEY_MAX_ROWS);
    if (StringUtils.isNotBlank(sysMax)) {
      max = parseInt(sysMax, max);
    }
    enabled = en;
    maxRows = Math.max(0, max);
  }

  /** Test helper: reset to defaults and clear system property overrides if set by tests. */
  public static void resetToDefaults() {
    enabled = DEFAULT_ENABLED;
    maxRows = DEFAULT_MAX_ROWS;
  }

  public static void setForTests(boolean enabledFlag, int maxRowsValue) {
    enabled = enabledFlag;
    maxRows = Math.max(0, maxRowsValue);
  }

  private static boolean parseBoolean(String raw, boolean defaultValue) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    String v = raw.trim();
    if ("true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v)) {
      return true;
    }
    if ("false".equalsIgnoreCase(v) || "no".equalsIgnoreCase(v) || "0".equals(v)) {
      return false;
    }
    return defaultValue;
  }

  private static int parseInt(String raw, int defaultValue) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
