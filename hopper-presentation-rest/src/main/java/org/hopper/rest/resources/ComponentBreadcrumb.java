package org.hopper.rest.resources;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPluginType;

/**
 * Build a navigable breadcrumb trail for the component property panel:
 *
 * <pre>
 *   Presentation › Page N › Group (Group) › Composite1 (Composite) › Label1 (Label)
 * </pre>
 */
public final class ComponentBreadcrumb {

  private ComponentBreadcrumb() {}

  /**
   * @return list of crumb maps suitable for JSON (also convertible via {@link #toJsonArray})
   */
  public static List<Map<String, Object>> build(
      HPresentation presentation, ComponentLookup.Found found) {
    List<Map<String, Object>> crumbs = new ArrayList<>();
    if (found == null) {
      return crumbs;
    }

    // Presentation root
    Map<String, Object> pres = new LinkedHashMap<>();
    pres.put("kind", "presentation");
    String presName =
        presentation != null && StringUtils.isNotBlank(presentation.getName())
            ? presentation.getName()
            : "Presentation";
    pres.put("label", presName);
    crumbs.add(pres);

    // Page / header / footer
    Map<String, Object> pageCrumb = new LinkedHashMap<>();
    pageCrumb.put("kind", "page");
    String role = found.pageRole != null ? found.pageRole : "page";
    pageCrumb.put("pageRole", role);
    pageCrumb.put("logicalPageNumber", found.logicalPageNumber);
    pageCrumb.put("label", pageLabel(role, found.logicalPageNumber));
    crumbs.add(pageCrumb);

    // Component lineage (top-level → current)
    List<HComponent> lineage = found.lineage;
    if (lineage == null || lineage.isEmpty()) {
      lineage = found.component != null ? List.of(found.component) : List.of();
    }
    for (int i = 0; i < lineage.size(); i++) {
      HComponent c = lineage.get(i);
      if (c == null) {
        continue;
      }
      boolean current = i == lineage.size() - 1;
      crumbs.add(componentCrumb(c, current));
    }
    return crumbs;
  }

  @SuppressWarnings("unchecked")
  public static JSONArray toJsonArray(List<Map<String, Object>> crumbs) {
    JSONArray arr = new JSONArray();
    if (crumbs == null) {
      return arr;
    }
    for (Map<String, Object> crumb : crumbs) {
      JSONObject o = new JSONObject();
      if (crumb != null) {
        o.putAll(crumb);
      }
      arr.add(o);
    }
    return arr;
  }

  public static JSONArray buildJson(HPresentation presentation, ComponentLookup.Found found) {
    return toJsonArray(build(presentation, found));
  }

  private static String pageLabel(String pageRole, int logicalPageNumber) {
    if ("header".equalsIgnoreCase(pageRole)) {
      return "Header";
    }
    if ("footer".equalsIgnoreCase(pageRole)) {
      return "Footer";
    }
    if (logicalPageNumber >= 0) {
      return "Page " + (logicalPageNumber + 1);
    }
    return "Page";
  }

  private static Map<String, Object> componentCrumb(HComponent component, boolean current) {
    Map<String, Object> crumb = new LinkedHashMap<>();
    crumb.put("kind", "component");
    String name = component.getName() != null ? component.getName() : "";
    crumb.put("name", name);
    String pluginId = pluginIdOf(component);
    String pluginName = pluginDisplayName(pluginId);
    crumb.put("pluginId", pluginId != null ? pluginId : "");
    crumb.put("pluginName", pluginName);
    // "Label1 (Label)" / "Group (Group)"
    String label =
        StringUtils.isNotBlank(pluginName) ? name + " (" + pluginName + ")" : name;
    crumb.put("label", label);
    if (current) {
      crumb.put("current", true);
    }
    return crumb;
  }

  private static String pluginIdOf(HComponent component) {
    if (component == null || component.getComponent() == null) {
      return null;
    }
    IHComponent impl = component.getComponent();
    return impl.getPluginId();
  }

  /**
   * Prefer plugin registry name ({@code @HComponentPlugin(name=…)}); fallback to a short form
   * of the plugin id ({@code HGroupComponent} → {@code Group}).
   */
  static String pluginDisplayName(String pluginId) {
    if (StringUtils.isBlank(pluginId)) {
      return "Component";
    }
    try {
      IPlugin plugin =
          PluginRegistry.getInstance().findPluginWithId(HComponentPluginType.class, pluginId);
      if (plugin != null && StringUtils.isNotBlank(plugin.getName())) {
        return plugin.getName();
      }
    } catch (Exception ignored) {
      // registry not ready in some unit tests
    }
    return shortPluginName(pluginId);
  }

  static String shortPluginName(String pluginId) {
    String s = pluginId;
    // Legacy Lean* ids and current H* ids both strip to a human-readable stem
    if (s.startsWith("Lean") && s.length() > 4) {
      s = s.substring(4);
    } else if (s.length() > 1 && s.charAt(0) == 'H' && Character.isUpperCase(s.charAt(1))) {
      s = s.substring(1);
    }
    if (s.endsWith("Component")) {
      s = s.substring(0, s.length() - "Component".length());
    }
    return StringUtils.isNotBlank(s) ? s : pluginId;
  }
}
