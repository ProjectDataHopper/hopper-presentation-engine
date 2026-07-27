package org.hopper.core;

import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.hopper.audit.plugin.HAuditPluginType;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.plugin.HGuiRegistry;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.HConnectorPluginType;

/**
 * Initializes Hop and registers Hopper component/connector/audit (and metadata) plugin types.
 *
 * <p>Safe to call repeatedly and from multiple threads; initialization runs once.
 *
 * <p>When a Hop plugins tree is configured ({@link HHopRuntime#PROP_HOPPER_HOP_HOME} or {@code
 * HOP_PLUGIN_BASE_FOLDERS}), performs a <strong>full</strong> {@link HopEnvironment#init()} so
 * transforms and actions are discovered from the folder layout (as in a Hop client install).
 * Without that configuration, only {@link HopClientEnvironment} is initialized (slim mode for unit
 * tests and minimal embeds).
 */
public class HEnvironment {

  /** Has the Hopper environment been initialized? */
  private static volatile boolean initialized;

  private static volatile boolean fullRuntime;

  private HEnvironment() {
    // utility
  }

  public static synchronized void init() throws HException {
    if (initialized) {
      return;
    }

    // Must run before any HopClientEnvironment / HopEnvironment / PluginRegistry.init()
    String pluginFolders = HHopRuntime.applyPluginFoldersFromEnvironment();
    fullRuntime = HHopRuntime.isFullRuntimeEnabled();
    boolean pluginsMissing =
        pluginFolders != null && !HHopRuntime.pluginsDirectoryExists(pluginFolders);

    try {
      if (fullRuntime) {
        // Registers TransformPluginType, ActionPluginType, engines, … then scans folders.
        // Do not use LogChannel before HopEnvironment/HopClientEnvironment (HopLogStore).
        HopEnvironment.init();
      } else if (!HopClientEnvironment.isInitialized()) {
        HopClientEnvironment.init();
      }
    } catch (HopException e) {
      throw new HException(
          fullRuntime
              ? "Unable to initialize the full Hop environment"
              : "Unable to initialize the Hop client API environment",
          e);
    }

    try {
      // MetadataPluginType may already be registered by HopEnvironment; addPluginType is safe.
      PluginRegistry.addPluginType(MetadataPluginType.getInstance());
      PluginRegistry.addPluginType(HComponentPluginType.getInstance());
      PluginRegistry.addPluginType(HConnectorPluginType.getInstance());
      PluginRegistry.addPluginType(HAuditPluginType.getInstance());
      // registerType() skips types already loaded; only new Hopper/metadata types are scanned.
      PluginRegistry.init();
      HGuiRegistry.getInstance().scanFromPluginRegistry();

      if (fullRuntime) {
        if (pluginsMissing) {
          LogChannel.GENERAL.logError(
              "HOP_PLUGIN_BASE_FOLDERS is set but the directory does not exist: "
                  + pluginFolders
                  + " — transform/action plugins will not load. Unpack hop-assemblies-plugins "
                  + "or point HOPPER_HOP_HOME at a Hop install.");
        } else if (pluginFolders != null) {
          LogChannel.GENERAL.logBasic(
              "Full Hop runtime: HOP_PLUGIN_BASE_FOLDERS=" + pluginFolders);
        } else {
          LogChannel.GENERAL.logBasic(
              "Full Hop runtime without plugin folders — only native/classpath plugins available.");
        }
        int transforms =
            PluginRegistry.getInstance().getPlugins(TransformPluginType.class).size();
        int actions = PluginRegistry.getInstance().getPlugins(ActionPluginType.class).size();
        LogChannel.GENERAL.logBasic(
            "Hop plugin registry: " + transforms + " transform(s), " + actions + " action(s)");
      }
    } catch (Exception e) {
      throw new HException("Unable to register hopper plugin types", e);
    }

    initialized = true;
  }

  public static boolean isInitialized() {
    return initialized;
  }

  /** Whether this process initialized full Hop engine plugin types (transforms/actions). */
  public static boolean isFullRuntime() {
    return fullRuntime;
  }
}
