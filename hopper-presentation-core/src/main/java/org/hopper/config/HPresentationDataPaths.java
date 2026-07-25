package org.hopper.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/**
 * Process-wide root for presentation working data on disk (connector Hop-row cache, captured
 * timings). Configured via admin setting {@link #KEY_DATA_PATH} / bootstrap properties.
 */
public final class HPresentationDataPaths {

  public static final String KEY_DATA_PATH = "server.data.path";
  public static final String KEY_TIMINGS_CAPTURE = "server.timings.capture";
  public static final String KEY_TOOLBAR_TIMINGS_VISIBLE = "ui.toolbar.timings-visible";
  public static final String KEY_CONNECTOR_DISK_CACHE_ENABLED = "server.connector-disk-cache.enabled";

  public static final String ENV_DATA_PATH = "HOPPER_DATA_PATH";

  public static final boolean DEFAULT_TIMINGS_CAPTURE = false;
  public static final boolean DEFAULT_TOOLBAR_TIMINGS_VISIBLE = true;
  public static final boolean DEFAULT_CONNECTOR_DISK_CACHE_ENABLED = true;

  private static volatile String rootPath = "";
  private static volatile boolean timingsCapture = DEFAULT_TIMINGS_CAPTURE;
  private static volatile boolean toolbarTimingsVisible = DEFAULT_TOOLBAR_TIMINGS_VISIBLE;
  private static volatile boolean connectorDiskCacheEnabled = DEFAULT_CONNECTOR_DISK_CACHE_ENABLED;

  private HPresentationDataPaths() {}

  public static String getRoot() {
    return rootPath != null ? rootPath : "";
  }

  public static boolean isConfigured() {
    return StringUtils.isNotBlank(rootPath);
  }

  public static boolean isTimingsCapture() {
    return timingsCapture;
  }

  public static boolean isToolbarTimingsVisible() {
    return toolbarTimingsVisible;
  }

  public static boolean isConnectorDiskCacheEnabled() {
    return connectorDiskCacheEnabled;
  }

  /** Absolute path for connector disk cache files for one catalog connector name. */
  public static String connectorCacheDir(String connectorName) {
    return join(getRoot(), "connector-cache", safeName(connectorName));
  }

  public static String connectorCacheFile(String connectorName, String fingerprint) {
    return join(connectorCacheDir(connectorName), safeName(fingerprint) + ".hoprows");
  }

  /** Directory for captured refresh timings of a presentation. */
  public static String timingsDir(String presentationName) {
    return join(getRoot(), "timings", safeName(presentationName));
  }

  /** Latest captured timings file for a presentation. */
  public static String timingsLatestFile(String presentationName) {
    return join(timingsDir(presentationName), "latest.hoprows");
  }

  /**
   * Apply from effective admin/bootstrap properties. When {@code server.data.path} is blank, leave
   * root empty unless {@code defaultRootIfEmpty} is provided by the caller (boot).
   */
  public static void applyFromProperties(Properties properties) {
    applyFromProperties(properties, null);
  }

  /**
   * @param defaultRootIfEmpty optional fallback when setting is blank (e.g. sibling of metadata)
   */
  public static void applyFromProperties(Properties properties, String defaultRootIfEmpty) {
    String path = "";
    boolean capture = DEFAULT_TIMINGS_CAPTURE;
    boolean toolbar = DEFAULT_TOOLBAR_TIMINGS_VISIBLE;
    boolean diskCache = DEFAULT_CONNECTOR_DISK_CACHE_ENABLED;

    if (properties != null) {
      path = StringUtils.trimToEmpty(properties.getProperty(KEY_DATA_PATH, ""));
      capture = parseBoolean(properties.getProperty(KEY_TIMINGS_CAPTURE), DEFAULT_TIMINGS_CAPTURE);
      toolbar =
          parseBoolean(
              properties.getProperty(KEY_TOOLBAR_TIMINGS_VISIBLE),
              DEFAULT_TOOLBAR_TIMINGS_VISIBLE);
      diskCache =
          parseBoolean(
              properties.getProperty(KEY_CONNECTOR_DISK_CACHE_ENABLED),
              DEFAULT_CONNECTOR_DISK_CACHE_ENABLED);
    }

    // System property / env wins for ops override
    String sysPath = System.getProperty(ENV_DATA_PATH);
    if (StringUtils.isBlank(sysPath)) {
      sysPath = System.getenv(ENV_DATA_PATH);
    }
    if (StringUtils.isNotBlank(sysPath)) {
      path = sysPath.trim();
    }

    if (StringUtils.isBlank(path) && StringUtils.isNotBlank(defaultRootIfEmpty)) {
      path = defaultRootIfEmpty.trim();
    }

    if (StringUtils.isNotBlank(path)) {
      File f = new File(path);
      path = f.getAbsolutePath();
      ensureDir(path);
      ensureDir(join(path, "connector-cache"));
      ensureDir(join(path, "timings"));
      System.setProperty(ENV_DATA_PATH, path);
    }

    rootPath = path != null ? path : "";
    timingsCapture = capture;
    toolbarTimingsVisible = toolbar;
    connectorDiskCacheEnabled = diskCache;
  }

  /** Suggest default data dir next to metadata: {@code {metadataParent}/data}. */
  public static String defaultBesideMetadata(String metadataPath) {
    if (StringUtils.isBlank(metadataPath)) {
      return "";
    }
    try {
      Path meta = Paths.get(metadataPath).toAbsolutePath().normalize();
      Path parent = meta.getParent();
      if (parent == null) {
        return meta.resolveSibling("data").toString();
      }
      return parent.resolve("data").toString();
    } catch (Exception e) {
      return "";
    }
  }

  public static void resetToDefaults() {
    rootPath = "";
    timingsCapture = DEFAULT_TIMINGS_CAPTURE;
    toolbarTimingsVisible = DEFAULT_TOOLBAR_TIMINGS_VISIBLE;
    connectorDiskCacheEnabled = DEFAULT_CONNECTOR_DISK_CACHE_ENABLED;
  }

  /** Test helper. */
  public static void setForTests(
      String root, boolean capture, boolean toolbarVisible, boolean diskCacheEnabled) {
    rootPath = root != null ? root : "";
    timingsCapture = capture;
    toolbarTimingsVisible = toolbarVisible;
    connectorDiskCacheEnabled = diskCacheEnabled;
    if (StringUtils.isNotBlank(rootPath)) {
      System.setProperty(ENV_DATA_PATH, rootPath);
    }
  }

  /**
   * Sanitize a name for use as a single path segment (no separators / traversal).
   */
  public static String safeName(String name) {
    if (name == null || name.isBlank()) {
      return "_";
    }
    String s =
        name.trim()
            .replace('\\', '_')
            .replace('/', '_')
            .replace("..", "_")
            .replaceAll("[^A-Za-z0-9._\\- @+()\\[\\]]", "_");
    s = s.replaceAll("\\s+", " ").trim();
    if (s.isEmpty()) {
      return "_";
    }
    // Cap length for filesystem friendliness
    if (s.length() > 120) {
      s = s.substring(0, 120);
    }
    return s;
  }

  private static String join(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p == null || p.isEmpty()) {
        continue;
      }
      if (sb.length() == 0) {
        sb.append(p);
      } else {
        char last = sb.charAt(sb.length() - 1);
        if (last != '/' && last != File.separatorChar) {
          sb.append(File.separatorChar);
        }
        String rest = p;
        while (rest.startsWith("/") || rest.startsWith("\\")) {
          rest = rest.substring(1);
        }
        sb.append(rest);
      }
    }
    return sb.toString();
  }

  private static void ensureDir(String path) {
    if (StringUtils.isBlank(path)) {
      return;
    }
    try {
      Files.createDirectories(Paths.get(path));
    } catch (Exception ignored) {
      // best effort; write failures surface later
    }
  }

  private static boolean parseBoolean(String raw, boolean defaultValue) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    String v = raw.trim().toLowerCase(Locale.ROOT);
    if ("true".equals(v) || "yes".equals(v) || "1".equals(v)) {
      return true;
    }
    if ("false".equals(v) || "no".equals(v) || "0".equals(v)) {
      return false;
    }
    return defaultValue;
  }
}
