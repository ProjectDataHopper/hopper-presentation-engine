package org.hopper.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPointPluginType;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.gui.plugin.GuiPluginType;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.hopper.audit.plugin.HAuditPluginType;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.plugin.HGuiRegistry;
import org.hopper.core.plugin.HPluginIndexSupport;
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

  /**
   * Hop GUI extension-point id (lives in hopper-edw) used to resolve the plugin folder and
   * libraries so {@code lib/hopper-presentation-*.jar} plugins are not treated as native.
   */
  public static final String HOP_PLUGIN_EXTENSION_POINT_ID =
      "RegisterHopperPresentationExtensionPoint";

  /** Has the Hopper environment been initialized? */
  private static volatile boolean initialized;

  private static volatile boolean fullRuntime;

  private HEnvironment() {
    // utility
  }

  public static synchronized void init() throws HException {
    PluginRegistry registry = PluginRegistry.getInstance();
    IPlugin hopPlugin =
        registry.findPluginWithId(ExtensionPointPluginType.class, HOP_PLUGIN_EXTENSION_POINT_ID);
    if (hopPlugin != null) {
      // Hop GUI plugin present: draw SVG, but do not list presentation types in Metadata.
      initEmbed(
          HEnvironment.class.getClassLoader(),
          hopPlugin.getLibraries() != null
              ? new ArrayList<>(hopPlugin.getLibraries())
              : new ArrayList<>(),
          hopPlugin.getPluginDirectory());
    } else {
      init(HEnvironment.class.getClassLoader(), new ArrayList<>(), null);
    }
  }

  /**
   * Initialize Hopper plugin types and register annotated classes visible to {@code classLoader}.
   *
   * <p>When {@code pluginUrl} is set (Hop GUI plugin), classes are registered as <em>external</em>
   * plugins so {@link PluginRegistry#loadClass} uses the hopper-edw classloader (which includes
   * {@code lib/hopper-presentation-core.jar}). Unit tests leave {@code pluginUrl} null (native).
   */
  public static synchronized void init(
      ClassLoader classLoader, List<String> libraries, URL pluginUrl) throws HException {
    init(classLoader, libraries, pluginUrl, true);
  }

  /**
   * Hop GUI / hopper-edw embed: register component and connector plugins so SVG can render, but
   * do <em>not</em> register {@code @HopMetadata} types into the Metadata perspective.
   */
  public static synchronized void initEmbed(
      ClassLoader classLoader, List<String> libraries, URL pluginUrl) throws HException {
    init(classLoader, libraries, pluginUrl, false);
  }

  public static synchronized void init(
      ClassLoader classLoader,
      List<String> libraries,
      URL pluginUrl,
      boolean registerMetadataTypes)
      throws HException {
    if (!initialized) {
      bootstrapHopAndPluginTypes();
      initialized = true;
    }

    boolean nativePlugin = pluginUrl == null;
    int registered =
        HPluginIndexSupport.registerFromClassLoader(
            classLoader != null ? classLoader : HEnvironment.class.getClassLoader(),
            libraries,
            pluginUrl,
            nativePlugin,
            registerMetadataTypes);
    if (!registerMetadataTypes) {
      HPluginIndexSupport.unregisterHopperMetadataTypes();
    }
    HGuiRegistry.getInstance().scanFromPluginRegistry();
    if (registered > 0 && LogChannel.GENERAL != null) {
      LogChannel.GENERAL.logBasic(
          "Registered " + registered + " Hopper presentation plugin(s) from classloader indexes");
    }
  }

  private static void bootstrapHopAndPluginTypes() throws HException {
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
      // hop-tech-google (and similar) @GuiPlugin classes implement
      // IGuiPluginCompositeWidgetsListener from hop-ui. REST / slim embeds do not ship hop-ui
      // (SWT). Scanning GuiPluginType there throws NoClassDefFoundError during PluginRegistry.init.
      if (hopUiOnClasspath()) {
        PluginRegistry.addPluginType(GuiPluginType.getInstance());
      }
      PluginRegistry.addPluginType(HComponentPluginType.getInstance());
      PluginRegistry.addPluginType(HConnectorPluginType.getInstance());
      PluginRegistry.addPluginType(HAuditPluginType.getInstance());
      // registerType() skips types already loaded; only new Hopper/metadata types are scanned.
      // Hop JarCache skips plugin lib/ folders, so HLabelComponent is not found here in Hop GUI.
      PluginRegistry.init();

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
  }

  /**
   * True when Hop GUI ({@code hop-ui}) is loadable. Server/REST classpaths typically are not.
   */
  static boolean hopUiOnClasspath() {
    try {
      Class.forName(
          "org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener",
          false,
          HEnvironment.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return false;
    }
  }

  public static boolean isInitialized() {
    return initialized;
  }

  /** Whether this process initialized full Hop engine plugin types (transforms/actions). */
  public static boolean isFullRuntime() {
    return fullRuntime;
  }
}
