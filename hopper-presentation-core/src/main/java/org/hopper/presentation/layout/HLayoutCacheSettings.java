package org.hopper.presentation.layout;

import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/** Process-wide knobs for the component layout result cache. */
public final class HLayoutCacheSettings {

  public static final String KEY_ENABLED = "server.layout-cache.enabled";
  public static final String KEY_MAX_COMPONENTS = "server.layout-cache.max-components";

  public static final boolean DEFAULT_ENABLED = true;
  public static final int DEFAULT_MAX_COMPONENTS = 500;

  private static volatile boolean enabled = DEFAULT_ENABLED;
  private static volatile int maxComponents = DEFAULT_MAX_COMPONENTS;

  private HLayoutCacheSettings() {}

  public static boolean isEnabled() {
    return enabled;
  }

  public static int getMaxComponents() {
    return maxComponents;
  }

  public static void applyFromProperties(Properties properties) {
    boolean en = DEFAULT_ENABLED;
    int max = DEFAULT_MAX_COMPONENTS;
    if (properties != null) {
      en = parseBoolean(properties.getProperty(KEY_ENABLED), DEFAULT_ENABLED);
      max = parseInt(properties.getProperty(KEY_MAX_COMPONENTS), DEFAULT_MAX_COMPONENTS);
    }
    String sysEn = System.getProperty(KEY_ENABLED);
    if (StringUtils.isNotBlank(sysEn)) {
      en = parseBoolean(sysEn, en);
    }
    String sysMax = System.getProperty(KEY_MAX_COMPONENTS);
    if (StringUtils.isNotBlank(sysMax)) {
      max = parseInt(sysMax, max);
    }
    enabled = en;
    maxComponents = Math.max(1, max);
  }

  public static void resetToDefaults() {
    enabled = DEFAULT_ENABLED;
    maxComponents = DEFAULT_MAX_COMPONENTS;
  }

  public static void setForTests(boolean enabledFlag, int max) {
    enabled = enabledFlag;
    maxComponents = Math.max(1, max);
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
