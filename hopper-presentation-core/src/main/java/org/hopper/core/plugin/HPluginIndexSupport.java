package org.hopper.core.plugin;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.gui.plugin.GuiRegistry;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.hopper.audit.plugin.HAuditPlugin;
import org.hopper.audit.plugin.HAuditPluginType;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

/**
 * Registers Hopper presentation plugins from Jandex indexes visible to a classloader.
 *
 * <p>Hop's {@code JarCache} skips plugin {@code lib/} folders, so {@code
 * hopper-presentation-core} inside {@code plugins/misc/hopper-edw/lib/} is never scanned for
 * {@code @HopMetadata} / {@code @HComponentPlugin}. This scanner reads those indexes from the
 * plugin classloader instead.
 */
public final class HPluginIndexSupport {

  /**
   * Toolbar hosts that live in {@code hopper-presentation-swt} ({@code lib/}). Hop never indexes
   * those jars; we copy {@code @GuiToolbarElement} methods into {@link GuiRegistry} ourselves.
   */
  static final String[] TOOLBAR_GUI_CLASSES = {
    "org.hopper.presentation.swt.HPresentationViewer"
  };

  private HPluginIndexSupport() {}

  public static int registerFromClassLoader(
      ClassLoader classLoader, List<String> libraries, URL pluginUrl, boolean nativePlugin)
      throws HException {
    return registerFromClassLoader(classLoader, libraries, pluginUrl, nativePlugin, true);
  }

  /**
   * @param registerMetadata when false (Hop GUI embed), skip {@code @HopMetadata} types so
   *     presentation/connector/theme do not appear in the Metadata perspective
   */
  public static int registerFromClassLoader(
      ClassLoader classLoader,
      List<String> libraries,
      URL pluginUrl,
      boolean nativePlugin,
      boolean registerMetadata)
      throws HException {
    if (classLoader == null) {
      return 0;
    }
    // Hop's handlePluginAnnotation mutates this list (addAll extra jars).
    List<String> libs = libraries != null ? new ArrayList<>(libraries) : new ArrayList<>();
    int count = 0;
    try {
      Enumeration<URL> indexes = classLoader.getResources("META-INF/jandex.idx");
      while (indexes.hasMoreElements()) {
        URL url = indexes.nextElement();
        if (!isHopperPresentationIndex(url)) {
          continue;
        }
        try (InputStream in = url.openStream()) {
          Index index = new IndexReader(in).read();
          count +=
              registerAnnotated(
                  index,
                  classLoader,
                  libs,
                  pluginUrl,
                  nativePlugin,
                  HComponentPlugin.class,
                  HComponentPluginType.class);
          count +=
              registerAnnotated(
                  index,
                  classLoader,
                  libs,
                  pluginUrl,
                  nativePlugin,
                  HConnectorPlugin.class,
                  HConnectorPluginType.class);
          count +=
              registerAnnotated(
                  index,
                  classLoader,
                  libs,
                  pluginUrl,
                  nativePlugin,
                  HAuditPlugin.class,
                  HAuditPluginType.class);
          if (registerMetadata) {
            count +=
                registerAnnotated(
                    index,
                    classLoader,
                    libs,
                    pluginUrl,
                    nativePlugin,
                    HopMetadata.class,
                    MetadataPluginType.class);
          }
        }
      }
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error scanning Hopper presentation plugin indexes", e);
    }
    registerHopperGuiElements(classLoader);
    return count;
  }

  /**
   * Copy {@code @GuiToolbarElement} methods from the SWT viewer into {@link GuiRegistry}. Hop GUI
   * never indexes plugin {@code lib/} jars, so {@code HPresentationViewer} is otherwise missing
   * from the toolbar registry. Does not walk every {@code org.hopper.*} {@code @GuiPlugin} (that
   * re-registered hopper-edw classes against the wrong classloader).
   */
  public static void registerHopperGuiElements() {
    registerHopperGuiElements(HPluginIndexSupport.class.getClassLoader());
  }

  public static void registerHopperGuiElements(ClassLoader classLoader) {
    ClassLoader loader =
        classLoader != null ? classLoader : HPluginIndexSupport.class.getClassLoader();
    for (String className : TOOLBAR_GUI_CLASSES) {
      try {
        registerGuiElements(loader.loadClass(className));
      } catch (ClassNotFoundException ignored) {
        // hopper-presentation-swt is absent on REST / core-only classloaders
      } catch (Exception e) {
        if (LogChannel.GENERAL != null) {
          LogChannel.GENERAL.logError(
              "Unable to register Hopper GUI elements for " + className, e);
        }
      }
    }
  }

  public static void registerGuiElements(Class<?> clazz) {
    if (clazz == null) {
      return;
    }
    GuiRegistry guiRegistry = GuiRegistry.getInstance();
    String className = clazz.getName();
    ClassLoader classLoader = clazz.getClassLoader();
    for (Method method : clazz.getDeclaredMethods()) {
      GuiToolbarElement toolbarElement = method.getAnnotation(GuiToolbarElement.class);
      if (toolbarElement != null) {
        guiRegistry.addGuiToolbarElement(className, toolbarElement, method, classLoader);
      }
    }
  }

  /**
   * Drop hopper-presentation {@code @HopMetadata} types from the registry so they do not appear in
   * Hop GUI's Metadata perspective. Needed when {@code PluginRegistry.init()} already scanned them
   * from the compile classpath (unit tests) or a non-{@code lib/} jar.
   */
  public static void unregisterHopperMetadataTypes() {
    PluginRegistry registry = PluginRegistry.getInstance();
    List<IPlugin> plugins = new ArrayList<>(registry.getPlugins(MetadataPluginType.class));
    for (IPlugin plugin : plugins) {
      String className = plugin.getClassMap().get(IHopMetadata.class);
      if (className != null && className.startsWith("org.hopper.")) {
        registry.removePlugin(MetadataPluginType.class, plugin);
      }
    }
  }

  static boolean isHopperPresentationIndex(URL url) {
    if (url == null) {
      return false;
    }
    String s = url.toString().toLowerCase();
    if (s.contains("hop-core")
        || s.contains("hop-engine")
        || s.contains("hop-ui")
        || s.contains("/hop-")) {
      return false;
    }
    // Only presentation-engine jars (including target/classes of this reactor). Matching
    // hopper-edw.jar re-registers every EDW @GuiPlugin as a native plugin and then fails
    // to load those classes.
    return s.contains("hopper-presentation");
  }

  private static int registerAnnotated(
      Index index,
      ClassLoader classLoader,
      List<String> libraries,
      URL pluginUrl,
      boolean nativePlugin,
      Class<? extends java.lang.annotation.Annotation> annotationClass,
      Class<? extends org.apache.hop.core.plugins.IPluginType> pluginTypeClass)
      throws HException {
    int count = 0;
    PluginRegistry registry = PluginRegistry.getInstance();
    for (AnnotationInstance instance :
        index.getAnnotations(DotName.createSimple(annotationClass.getName()))) {
      if (!(instance.target() instanceof ClassInfo classInfo)) {
        continue;
      }
      String className = classInfo.name().toString();
      if (!className.startsWith("org.hopper.")) {
        continue;
      }
      try {
        registry.registerPluginClass(
            classLoader,
            new ArrayList<>(libraries),
            pluginUrl,
            className,
            pluginTypeClass,
            annotationClass,
            nativePlugin);
        count++;
      } catch (HopPluginException e) {
        if (LogChannel.GENERAL != null) {
          LogChannel.GENERAL.logError(
              "Unable to register Hopper plugin " + className + ": " + e.getMessage());
        }
      }
    }
    return count;
  }
}
