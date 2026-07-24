package org.hopper.core.gui.plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.resolver.IVariableResolver;
import org.apache.hop.core.variables.resolver.VariableResolverPluginType;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HConnectorPluginType;

/**
 * Singleton registry of {@link HWidgetElement} (and Hop {@link GuiWidgetElement}) declarations on
 * Hopper component/connector plugins and Hop variable-resolver plugins.
 *
 * <p>Populated by {@link #scanFromPluginRegistry()} during {@code HEnvironment.init()}.
 */
public final class HGuiRegistry {

  private static HGuiRegistry instance;

  /**
   * className → parentId → widget list (sorted by order after {@link #sortAll()}).
   */
  private final Map<String, Map<String, List<HWidgetElements>>> dataElementsMap =
      new LinkedHashMap<>();

  private boolean scanned;

  private HGuiRegistry() {}

  public static synchronized HGuiRegistry getInstance() {
    if (instance == null) {
      instance = new HGuiRegistry();
    }
    return instance;
  }

  /**
   * Scan all registered Hopper component/connector and Hop variable-resolver plugin classes for
   * form widgets. Safe to call more than once; clears and rebuilds the map.
   */
  public synchronized void scanFromPluginRegistry() {
    clear();
    PluginRegistry pluginRegistry = PluginRegistry.getInstance();

    for (IPlugin plugin : pluginRegistry.getPlugins(HComponentPluginType.class)) {
      scanPluginClass(plugin, IHComponent.class, pluginRegistry);
    }
    for (IPlugin plugin : pluginRegistry.getPlugins(HConnectorPluginType.class)) {
      scanPluginClass(plugin, IHConnector.class, pluginRegistry);
    }
    for (IPlugin plugin : pluginRegistry.getPlugins(VariableResolverPluginType.class)) {
      scanPluginClass(plugin, IVariableResolver.class, pluginRegistry);
    }

    sortAll();
    scanned = true;
  }

  private void scanPluginClass(
      IPlugin plugin, Class<?> mainType, PluginRegistry pluginRegistry) {
    try {
      String className = plugin.getClassMap().get(mainType);
      if (className == null) {
        return;
      }
      ClassLoader classLoader = pluginRegistry.getClassLoader(plugin);
      Class<?> clazz = classLoader.loadClass(className);
      registerClass(clazz);
    } catch (Exception e) {
      // Skip plugins that cannot be loaded for widget scan
    }
  }

  /**
   * Reflect form-widget fields on {@code clazz} (and superclasses) into the registry.
   *
   * <p>Prefers Hopper {@link HWidgetElement}; otherwise converts Hop {@link GuiWidgetElement}
   * (variable resolvers, etc.) via {@link HGuiWidgetAdapter}.
   */
  public synchronized void registerClass(Class<?> clazz) {
    if (clazz == null) {
      return;
    }
    String className = clazz.getName();
    for (Field field : getAllFields(clazz)) {
      HWidgetElement hAnnotation = field.getAnnotation(HWidgetElement.class);
      if (hAnnotation != null) {
        addWidgetElement(className, hAnnotation, field, clazz);
        continue;
      }
      GuiWidgetElement guiAnnotation = field.getAnnotation(GuiWidgetElement.class);
      if (guiAnnotation != null) {
        HWidgetElements adapted =
            HGuiWidgetAdapter.fromGuiWidgetElement(guiAnnotation, field, clazz);
        if (adapted != null) {
          addWidgetElements(className, adapted);
        }
      }
    }
  }

  /**
   * Add one annotated field. Honors {@link HWidgetElement#ignored()} override semantics.
   */
  public synchronized void addWidgetElement(
      String dataClassName, HWidgetElement annotation, Field field, Class<?> ownerClass) {
    HWidgetElements child = new HWidgetElements(annotation, field, ownerClass);
    addWidgetElements(dataClassName, child);
  }

  /**
   * Add a pre-built widget descriptor (from {@link HWidgetElement} or adapted {@link
   * GuiWidgetElement}).
   */
  public synchronized void addWidgetElements(String dataClassName, HWidgetElements child) {
    if (child == null || dataClassName == null) {
      return;
    }
    String parentId =
        StringUtils.isEmpty(child.getParentId())
            ? HGuiFormConstants.PARENT_PLUGIN
            : child.getParentId();
    child.setParentId(parentId);

    Map<String, List<HWidgetElements>> byParent =
        dataElementsMap.computeIfAbsent(dataClassName, k -> new LinkedHashMap<>());
    List<HWidgetElements> list = byParent.computeIfAbsent(parentId, k -> new ArrayList<>());

    HWidgetElements existing = findById(list, child.getId());
    if (existing != null && existing.isIgnored()) {
      return;
    }
    if (existing != null && child.isIgnored()) {
      existing.setIgnored(true);
      return;
    }
    if (existing != null) {
      list.remove(existing);
    }
    if (!child.isIgnored()) {
      list.add(child);
    }
  }

  private static HWidgetElements findById(List<HWidgetElements> list, String id) {
    for (HWidgetElements e : list) {
      if (id != null && id.equals(e.getId())) {
        return e;
      }
    }
    return null;
  }

  /**
   * Widgets for a class, grouped by parent id. If the class is unknown, reflects it once and
   * returns the result (lazy fallback for tests / late-loaded classes).
   */
  public synchronized Map<String, List<HWidgetElements>> getElementsByParent(Class<?> clazz) {
    if (clazz == null) {
      return Map.of();
    }
    Map<String, List<HWidgetElements>> existing = dataElementsMap.get(clazz.getName());
    if (existing == null) {
      registerClass(clazz);
      existing = dataElementsMap.get(clazz.getName());
      if (existing != null) {
        sortClass(clazz.getName());
      }
    }
    if (existing == null) {
      return Map.of();
    }
    // defensive copy
    Map<String, List<HWidgetElements>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<HWidgetElements>> e : existing.entrySet()) {
      copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
    }
    return copy;
  }

  public synchronized List<HWidgetElements> findElements(String dataClassName, String parentId) {
    Map<String, List<HWidgetElements>> byParent = dataElementsMap.get(dataClassName);
    if (byParent == null) {
      return List.of();
    }
    List<HWidgetElements> list = byParent.get(parentId);
    if (list == null) {
      return List.of();
    }
    return Collections.unmodifiableList(new ArrayList<>(list));
  }

  public synchronized void sortAll() {
    for (String className : dataElementsMap.keySet()) {
      sortClass(className);
    }
  }

  private void sortClass(String className) {
    Map<String, List<HWidgetElements>> byParent = dataElementsMap.get(className);
    if (byParent == null) {
      return;
    }
    for (List<HWidgetElements> list : byParent.values()) {
      list.sort(Comparator.naturalOrder());
    }
  }

  public synchronized void clear() {
    dataElementsMap.clear();
    scanned = false;
  }

  public boolean isScanned() {
    return scanned;
  }

  public Map<String, Map<String, List<HWidgetElements>>> getDataElementsMap() {
    return dataElementsMap;
  }

  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        fields.add(field);
      }
      current = current.getSuperclass();
    }
    return fields;
  }
}
