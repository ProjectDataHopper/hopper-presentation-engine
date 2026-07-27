package org.hopper.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.variables.Variables;

/**
 * Configures Apache Hop plugin discovery for embedded / server use.
 *
 * <p>Hop loads transforms, actions, databases, etc. from one or more <em>plugin base folders</em>
 * ({@link Const#HOP_PLUGIN_BASE_FOLDERS}), not from an ad-hoc list of JARs on the classpath.
 * The standard layout is a directory containing a {@code plugins/} tree as shipped in {@code
 * hop-assemblies-plugins}.
 *
 * <p>Resolution order for the plugin base path:
 *
 * <ol>
 *   <li>System property or env {@code HOP_PLUGIN_BASE_FOLDERS} (Hop-native, comma-separated)
 *   <li>System property or env {@code HOPPER_HOP_HOME} → {@code $HOPPER_HOP_HOME/plugins}
 * </ol>
 *
 * <p>Must run <strong>before</strong> {@link org.apache.hop.core.HopClientEnvironment#init()} /
 * {@link org.apache.hop.core.HopEnvironment#init()}.
 */
public final class HHopRuntime {

  /** Env / system property: Hop home directory that contains {@code plugins/}. */
  public static final String PROP_HOPPER_HOP_HOME = "HOPPER_HOP_HOME";

  /**
   * Env / system property: when {@code true}/{@code Y}, always call full {@code HopEnvironment}
   * init (transforms/actions). When unset, full init is used only if a plugins folder was
   * configured.
   */
  public static final String PROP_HOP_FULL_RUNTIME = "HOPPER_HOP_FULL_RUNTIME";

  private HHopRuntime() {}

  /**
   * Apply plugin folder configuration from the environment. Idempotent and safe if nothing is
   * configured (Hop keeps its default {@code plugins} relative path).
   *
   * @return absolute plugin base folder path that was applied, or {@code null} if none configured
   */
  public static String applyPluginFoldersFromEnvironment() {
    String existing = firstNonBlank(
        System.getProperty(Const.HOP_PLUGIN_BASE_FOLDERS),
        System.getenv(Const.HOP_PLUGIN_BASE_FOLDERS));
    if (StringUtils.isNotBlank(existing)) {
      String absolute = toAbsolutePluginFolders(existing);
      setPluginBaseFolders(absolute);
      return absolute;
    }

    String hopHome = firstNonBlank(
        System.getProperty(PROP_HOPPER_HOP_HOME), System.getenv(PROP_HOPPER_HOP_HOME));
    if (StringUtils.isBlank(hopHome)) {
      return null;
    }

    Path plugins = Path.of(hopHome.trim()).toAbsolutePath().normalize().resolve("plugins");
    String absolute = plugins.toString();
    setPluginBaseFolders(absolute);
    return absolute;
  }

  /**
   * Whether full Hop engine plugin types (transforms, actions, …) should be registered.
   *
   * <p>True when {@link #PROP_HOP_FULL_RUNTIME} is true/Y, or when a plugins folder was
   * configured via {@link #applyPluginFoldersFromEnvironment()}.
   */
  public static boolean isFullRuntimeEnabled() {
    String flag = firstNonBlank(
        System.getProperty(PROP_HOP_FULL_RUNTIME), System.getenv(PROP_HOP_FULL_RUNTIME));
    if (flag != null) {
      return "Y".equalsIgnoreCase(flag.trim())
          || "true".equalsIgnoreCase(flag.trim())
          || "1".equals(flag.trim());
    }
    String folders = System.getProperty(Const.HOP_PLUGIN_BASE_FOLDERS);
    if (StringUtils.isBlank(folders)) {
      folders = System.getenv(Const.HOP_PLUGIN_BASE_FOLDERS);
    }
    if (StringUtils.isBlank(folders)) {
      return false;
    }
    // At least one configured path should exist
    for (String part : folders.split(",")) {
      File f = new File(part.trim());
      if (f.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  public static void setPluginBaseFolders(String absoluteFolders) {
    if (StringUtils.isBlank(absoluteFolders)) {
      return;
    }
    System.setProperty(Const.HOP_PLUGIN_BASE_FOLDERS, absoluteFolders);
    try {
      Variables.getADefaultVariableSpace()
          .setVariable(Const.HOP_PLUGIN_BASE_FOLDERS, absoluteFolders);
    } catch (Exception ignored) {
      // Variables may not be ready yet; system property is enough for JarCache
    }
  }

  private static String toAbsolutePluginFolders(String folderPaths) {
    StringBuilder out = new StringBuilder();
    for (String part : folderPaths.split(",")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      Path p = Path.of(trimmed).toAbsolutePath().normalize();
      if (out.length() > 0) {
        out.append(',');
      }
      out.append(p);
    }
    return out.toString();
  }

  private static String firstNonBlank(String a, String b) {
    if (StringUtils.isNotBlank(a)) {
      return a;
    }
    if (StringUtils.isNotBlank(b)) {
      return b;
    }
    return null;
  }

  /** True if the path exists and is a directory. */
  public static boolean pluginsDirectoryExists(String pluginBaseFolders) {
    if (StringUtils.isBlank(pluginBaseFolders)) {
      return false;
    }
    String first = pluginBaseFolders.split(",")[0].trim();
    return Files.isDirectory(Path.of(first));
  }
}
