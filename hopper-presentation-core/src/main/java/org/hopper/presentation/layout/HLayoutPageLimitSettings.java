package org.hopper.presentation.layout;

import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/**
 * Process-wide ceiling on how many body render pages a single layout may create. Protects the
 * server from multi-page tables/crosstabs/groups that would otherwise allocate unbounded SVG
 * sheets.
 */
public final class HLayoutPageLimitSettings {

  public static final String KEY_MAX_RENDER_PAGES = "server.layout.max-render-pages";

  /** Default: first 10 pages only (matches product intent for interactive dashboards). */
  public static final int DEFAULT_MAX_RENDER_PAGES = 10;

  public static final int MIN_MAX_RENDER_PAGES = 1;
  public static final int ABSOLUTE_MAX_RENDER_PAGES = 1000;

  private static volatile int maxRenderPages = DEFAULT_MAX_RENDER_PAGES;

  private HLayoutPageLimitSettings() {}

  public static int getMaxRenderPages() {
    return maxRenderPages;
  }

  public static void applyFromProperties(Properties properties) {
    int max = DEFAULT_MAX_RENDER_PAGES;
    if (properties != null) {
      max = parseInt(properties.getProperty(KEY_MAX_RENDER_PAGES), DEFAULT_MAX_RENDER_PAGES);
    }
    String sys = System.getProperty(KEY_MAX_RENDER_PAGES);
    if (StringUtils.isNotBlank(sys)) {
      max = parseInt(sys, max);
    }
    maxRenderPages = clamp(max);
  }

  public static void resetToDefaults() {
    maxRenderPages = DEFAULT_MAX_RENDER_PAGES;
  }

  /** Test helper. */
  public static void setForTests(int max) {
    maxRenderPages = clamp(max);
  }

  private static int clamp(int max) {
    if (max < MIN_MAX_RENDER_PAGES) {
      return MIN_MAX_RENDER_PAGES;
    }
    return Math.min(ABSOLUTE_MAX_RENDER_PAGES, max);
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
