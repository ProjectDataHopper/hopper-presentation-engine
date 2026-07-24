package org.hopper.presentation.layout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.core.HGeometry;
import org.hopper.core.HJson;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.variable.HParameter;

/** Hash helpers for layout-cache keys. */
public final class HLayoutFingerprint {

  private HLayoutFingerprint() {}

  public static String componentContent(HComponent component) {
    return componentContent(component, null);
  }

  /**
   * @param log optional metrics channel; when gathering metrics, snaps {@link
   *     org.hopper.core.log.HMetricsUtil#CODE_COMPONENT_FINGERPRINT}
   */
  public static String componentContent(
      HComponent component, org.apache.hop.core.logging.ILogChannel log) {
    if (component == null) {
      return "null";
    }
    String name = component.getName();
    org.hopper.core.log.HMetricsUtil.start(
        log,
        org.hopper.core.log.HMetricsUtil.CODE_COMPONENT_FINGERPRINT,
        "Component content fingerprint",
        name);
    try {
      try {
        String json = HJson.createMapper().writeValueAsString(component);
        return sha256(json);
      } catch (Exception e) {
        // Fallback: name + plugin id
        String plugin =
            component.getComponent() != null ? component.getComponent().getPluginId() : "";
        return sha256(String.valueOf(component.getName()) + "|" + plugin);
      }
    } finally {
      org.hopper.core.log.HMetricsUtil.stop(
          log,
          org.hopper.core.log.HMetricsUtil.CODE_COMPONENT_FINGERPRINT,
          "Component content fingerprint",
          name);
    }
  }

  public static String pageFrame(HPage page) {
    if (page == null) {
      return "null-page";
    }
    String raw =
        page.getWidth()
            + "x"
            + page.getHeight()
            + "|m="
            + page.getTopMargin()
            + ","
            + page.getLeftMargin()
            + ","
            + page.getBottomMargin()
            + ","
            + page.getRightMargin();
    return sha256(raw);
  }

  public static String parameters(List<HParameter> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "no-params";
    }
    StringBuilder sb = new StringBuilder();
    for (HParameter p : parameters) {
      if (p == null) {
        continue;
      }
      sb.append(p.getParameterName()).append('=').append(p.getParameterValue()).append('\n');
    }
    return sha256(sb.toString());
  }

  public static String geometry(HGeometry g) {
    if (g == null) {
      return "null-geo";
    }
    return g.getX() + "," + g.getY() + "," + g.getWidth() + "," + g.getHeight();
  }

  public static String dependencies(Map<String, HGeometry> depFirstGeometries) {
    if (depFirstGeometries == null || depFirstGeometries.isEmpty()) {
      return "no-deps";
    }
    StringBuilder sb = new StringBuilder();
    depFirstGeometries.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            e ->
                sb.append(e.getKey())
                    .append('=')
                    .append(geometry(e.getValue()))
                    .append('\n'));
    return sha256(sb.toString());
  }

  /**
   * Cheap fingerprint: column names/types + row count only. Does not scan cell values (full-cell
   * hashing was multi-second on large SQL results and ran per component on every soft-reload).
   */
  public static String connectorRowsSummary(IRowMeta rowMeta, int rowCount) {
    StringBuilder sb = new StringBuilder(128);
    if (rowMeta != null && rowMeta.getValueMetaList() != null) {
      for (IValueMeta vm : rowMeta.getValueMetaList()) {
        if (vm != null) {
          sb.append(vm.getName()).append(':').append(vm.getTypeDesc()).append('|');
        }
      }
    }
    sb.append('#').append(Math.max(0, rowCount));
    return sha256(sb.toString());
  }

  /**
   * Fingerprint connector rows already in memory. Uses meta + count only (see {@link
   * #connectorRowsSummary}); retained for call-site compatibility.
   */
  public static String connectorRows(IRowMeta rowMeta, List<Object[]> rows) {
    return connectorRowsSummary(rowMeta, rows != null ? rows.size() : 0);
  }

  public static String combine(String... parts) {
    StringBuilder sb = new StringBuilder();
    if (parts != null) {
      for (String p : parts) {
        sb.append(p == null ? "" : p).append('|');
      }
    }
    return sha256(sb.toString());
  }

  public static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig =
          md.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (Exception e) {
      return Integer.toHexString(input == null ? 0 : input.hashCode());
    }
  }

  public static boolean isBlank(String s) {
    return StringUtils.isBlank(s);
  }
}
