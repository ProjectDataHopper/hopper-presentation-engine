package org.hopper.core.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plugin ID helpers for the Lean → Hopper rebrand.
 *
 * <p>Presentation JSON historically used {@code Lean*} component plugin keys (and a few {@code
 * Lean*} connectors). New writes use {@code H*} IDs. On load, known {@code Lean*} IDs are mapped to
 * the current {@code H*} registry ids so existing metadata still opens.
 */
public final class HPluginIds {

  private static final Map<String, String> LEGACY_LEAN_TO_H;

  static {
    Map<String, String> m = new LinkedHashMap<>();
    // Components
    m.put("LeanBarChartComponent", "HBarChartComponent");
    m.put("LeanLineChartComponent", "HLineChartComponent");
    m.put("LeanPieChartComponent", "HPieChartComponent");
    m.put("LeanTableComponent", "HTableComponent");
    m.put("LeanCrosstabComponent", "HCrosstabComponent");
    m.put("LeanLabelComponent", "HLabelComponent");
    m.put("LeanImageComponent", "HImageComponent");
    m.put("LeanSvgComponent", "HSvgComponent");
    m.put("LeanGroupComponent", "HGroupComponent");
    m.put("LeanCompositeComponent", "HCompositeComponent");
    // Connectors that used a Lean prefix
    m.put("LeanRestConnector", "HRestConnector");
    m.put("LeanListConnector", "HListConnector");
    LEGACY_LEAN_TO_H = Collections.unmodifiableMap(m);
  }

  private HPluginIds() {}

  /**
   * Resolve a plugin id from metadata to the current registry id.
   *
   * @param id raw id from JSON / Hop metadata
   * @return mapped id, or {@code id} unchanged when no alias applies
   */
  public static String resolve(String id) {
    if (id == null || id.isEmpty()) {
      return id;
    }
    String mapped = LEGACY_LEAN_TO_H.get(id);
    if (mapped != null) {
      return mapped;
    }
    // Generic fallback: LeanFoo → HFoo
    if (id.startsWith("Lean") && id.length() > 4 && Character.isUpperCase(id.charAt(4))) {
      return "H" + id.substring(4);
    }
    return id;
  }

  /** @return unmodifiable map of legacy Lean plugin ids to current H* ids */
  public static Map<String, String> leanToHopperAliases() {
    return LEGACY_LEAN_TO_H;
  }
}
