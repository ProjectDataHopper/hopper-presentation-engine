package org.hopper.metadata.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.hopper.core.HAttachment;
import org.hopper.core.HColumn;
import org.hopper.core.HJson;
import org.hopper.core.exception.HException;
import org.hopper.metadata.codec.HMetadataCodec;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.presentation.component.types.textblock.HTextBlockComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.types.csv.HCsvConnector;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;

/**
 * Compiles a compact authoring document (JSON) into {@link HPresentation} / {@link HConnector}
 * models. Layout uses named place recipes instead of raw attachments.
 *
 * <p>v1 is <strong>JSON only</strong> (no new YAML dependency). YAML can wrap JSON later.
 */
public final class HAuthoringDsl {

  private static final Map<String, String> COMPONENT_ALIASES = new HashMap<>();
  private static final Map<String, String> CONNECTOR_ALIASES = new HashMap<>();

  static {
    COMPONENT_ALIASES.put("label", "HLabelComponent");
    COMPONENT_ALIASES.put("text", "HTextBlockComponent");
    COMPONENT_ALIASES.put("textblock", "HTextBlockComponent");
    COMPONENT_ALIASES.put("table", "HTableComponent");
    COMPONENT_ALIASES.put("bar", "HBarChartComponent");
    COMPONENT_ALIASES.put("line", "HLineChartComponent");
    COMPONENT_ALIASES.put("pie", "HPieChartComponent");
    COMPONENT_ALIASES.put("gantt", "HGanttChartComponent");
    COMPONENT_ALIASES.put("image", "HImageComponent");
    COMPONENT_ALIASES.put("svg", "HSvgComponent");
    COMPONENT_ALIASES.put("crosstab", "HCrosstabComponent");
    COMPONENT_ALIASES.put("group", "HGroupComponent");
    COMPONENT_ALIASES.put("composite", "HCompositeComponent");
    COMPONENT_ALIASES.put("pictorial", "HPictorialChartComponent");
    COMPONENT_ALIASES.put("pictorialchart", "HPictorialChartComponent");

    CONNECTOR_ALIASES.put("csv", "CsvConnector");
    CONNECTOR_ALIASES.put("sql", "SqlConnector");
    CONNECTOR_ALIASES.put("sample", "SampleDataConnector");
    CONNECTOR_ALIASES.put("rest", "HRestConnector");
    CONNECTOR_ALIASES.put("binary", "BinaryRowsConnector");
    CONNECTOR_ALIASES.put("sort", "SortConnector");
    CONNECTOR_ALIASES.put("filter", "SimpleFilterConnector");
    CONNECTOR_ALIASES.put("chain", "ChainConnector");
  }

  private HAuthoringDsl() {}

  public static HPresentation compilePresentation(String dslJson) throws HException {
    try {
      ObjectMapper mapper = HJson.createMapper();
      JsonNode root = mapper.readTree(dslJson);
      if (root == null || !root.isObject()) {
        throw new HException("DSL root must be a JSON object");
      }
      String kind = text(root, "kind");
      if (StringUtils.isNotBlank(kind) && !"presentation".equalsIgnoreCase(kind)) {
        throw new HException("Expected kind=presentation, got: " + kind);
      }

      HPresentation presentation = new HPresentation();
      presentation.setName(requiredText(root, "name"));
      presentation.setDescription(text(root, "description"));
      String theme = text(root, "theme");
      if (StringUtils.isNotBlank(theme)) {
        presentation.setDefaultThemeName(theme);
      }
      if (root.has("virtualPath")) {
        // HopMetadataBase may not expose virtualPath on presentation — set via reflection if present
        try {
          var m = HPresentation.class.getMethod("setVirtualPath", String.class);
          m.invoke(presentation, text(root, "virtualPath"));
        } catch (NoSuchMethodException ignored) {
          // optional field on some builds
        }
      }
      if (root.has("autoRefreshSeconds") && root.get("autoRefreshSeconds").canConvertToInt()) {
        presentation.setAutoRefreshSeconds(root.get("autoRefreshSeconds").asInt());
      }

      JsonNode pagesNode = root.get("pages");
      if (pagesNode == null || !pagesNode.isArray() || pagesNode.isEmpty()) {
        // single implicit page
        HPage page = defaultPage();
        page.getComponents().addAll(compileComponents(root.get("components"), mapper));
        presentation.getPages().add(page);
      } else {
        for (JsonNode pageNode : pagesNode) {
          HPage page = defaultPage();
          if (pageNode.has("width")) {
            page.setWidth(pageNode.get("width").asInt());
          }
          if (pageNode.has("height")) {
            page.setHeight(pageNode.get("height").asInt());
          }
          page.getComponents().addAll(compileComponents(pageNode.get("components"), mapper));
          presentation.getPages().add(page);
        }
      }
      return presentation;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("DSL compile failed: " + e.getMessage(), e);
    }
  }

  public static HConnector compileConnector(String dslJson) throws HException {
    try {
      ObjectMapper mapper = HJson.createMapper();
      JsonNode root = mapper.readTree(dslJson);
      if (root == null || !root.isObject()) {
        throw new HException("DSL root must be a JSON object");
      }
      String kind = text(root, "kind");
      if (StringUtils.isNotBlank(kind) && !"connector".equalsIgnoreCase(kind)) {
        throw new HException("Expected kind=connector, got: " + kind);
      }
      String name = requiredText(root, "name");
      String type = resolveConnectorType(requiredText(root, "type"));
      ObjectNode body = mapper.createObjectNode();
      body.put("pluginId", type);
      // copy remaining properties except kind/name/type
      Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> e = fields.next();
        String k = e.getKey();
        if ("kind".equals(k) || "name".equals(k) || "type".equals(k) || "shared".equals(k)) {
          continue;
        }
        body.set(k, e.getValue());
      }
      // field aliases for CSV
      if (body.has("delimiter") && !body.has("separator")) {
        body.set("separator", body.get("delimiter"));
      }
      IHConnector plugin =
          HJson.createMapper()
              .treeToValue(
                  body,
                  loadConnectorClass(type));
      return new HConnector(name, plugin);
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Connector DSL compile failed: " + e.getMessage(), e);
    }
  }

  public static String compilePresentationToJacksonJson(String dslJson, boolean pretty)
      throws HException {
    return HMetadataCodec.toJacksonJson(compilePresentation(dslJson), pretty);
  }

  private static List<HComponent> compileComponents(JsonNode componentsNode, ObjectMapper mapper)
      throws Exception {
    List<HComponent> list = new ArrayList<>();
    if (componentsNode == null || !componentsNode.isArray()) {
      return list;
    }
    for (JsonNode node : componentsNode) {
      list.add(compileComponent(node, mapper));
    }
    return list;
  }

  private static HComponent compileComponent(JsonNode node, ObjectMapper mapper) throws Exception {
    String name = requiredText(node, "name");
    String type = resolveComponentType(requiredText(node, "type"));

    ObjectNode body = mapper.createObjectNode();
    body.put("pluginId", type);

    // map friendly fields
    copyIfPresent(node, body, "sourceConnectorName");
    if (node.has("connector") && !body.has("sourceConnectorName")) {
      body.put("sourceConnectorName", node.get("connector").asText());
    }
    copyIfPresent(node, body, "themeName");
    copyIfPresent(node, body, "border");
    copyIfPresent(node, body, "background");

    // label / text aliases
    if (node.has("text") && "HLabelComponent".equals(type)) {
      body.put("label", node.get("text").asText());
    }
    if (node.has("label")) {
      body.put("label", node.get("label").asText());
    }
    if (node.has("text") && "HTextBlockComponent".equals(type)) {
      body.put("text", node.get("text").asText());
    }
    copyIfPresent(node, body, "wrap");
    copyIfPresent(node, body, "maxWidth");
    copyIfPresent(node, body, "paginate");
    copyIfPresent(node, body, "horizontalAlignment");
    copyIfPresent(node, body, "verticalAlignment");
    copyIfPresent(node, body, "header");
    copyIfPresent(node, body, "headerOnEveryPage");
    copyIfPresent(node, body, "evenHeights");
    copyIfPresent(node, body, "horizontalMargin");
    copyIfPresent(node, body, "verticalMargin");

    if (node.has("columns") && node.get("columns").isArray()) {
      // table columnSelection
      var arr = mapper.createArrayNode();
      for (JsonNode col : node.get("columns")) {
        ObjectNode c = mapper.createObjectNode();
        String colName =
            col.has("column")
                ? col.get("column").asText()
                : col.has("columnName") ? col.get("columnName").asText() : col.asText();
        c.put("columnName", colName);
        if (col.has("header")) {
          c.put("headerValue", col.get("header").asText());
        } else if (col.has("headerValue")) {
          c.put("headerValue", col.get("headerValue").asText());
        }
        if (col.has("width")) {
          c.put("width", col.get("width").asInt());
        }
        arr.add(c);
      }
      body.set("columnSelection", arr);
    }

    // pluginExtra: merge arbitrary plugin properties
    if (node.has("pluginExtra") && node.get("pluginExtra").isObject()) {
      Iterator<Map.Entry<String, JsonNode>> it = node.get("pluginExtra").fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        body.set(e.getKey(), e.getValue());
      }
    }

    // copy any remaining simple props that match Java beans (best effort)
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> e = fields.next();
      String k = e.getKey();
      if (List.of(
              "name",
              "type",
              "place",
              "layoutRaw",
              "text",
              "label",
              "connector",
              "columns",
              "pluginExtra")
          .contains(k)) {
        continue;
      }
      if (!body.has(k)) {
        body.set(k, e.getValue());
      }
    }

    IHComponent plugin = mapper.treeToValue(body, loadComponentClass(type));
    // ensure sensible defaults for known types
    if (plugin instanceof HTableComponent table && table.getColumnSelection() == null) {
      table.setColumnSelection(new ArrayList<HColumn>());
    }
    if (plugin instanceof HLabelComponent label && label.getLabel() == null) {
      label.setLabel("");
    }
    if (plugin instanceof HTextBlockComponent block && block.getText() == null) {
      block.setText("");
    }

    HComponent wrapper = new HComponent(name, plugin);
    if (node.has("layoutRaw") && node.get("layoutRaw").isObject()) {
      wrapper.setLayout(mapper.treeToValue(node.get("layoutRaw"), HLayout.class));
    } else {
      wrapper.setLayout(compilePlace(node.get("place")));
    }
    return wrapper;
  }

  private static HLayout compilePlace(JsonNode place) throws HException {
    if (place == null || place.isNull()) {
      return HLayout.topLeftPage();
    }
    String recipe =
        place.has("recipe") ? place.get("recipe").asText("topLeft") : "topLeft";
    recipe = recipe.toLowerCase(Locale.ROOT);
    int ox = 0;
    int oy = 0;
    if (place.has("offset") && place.get("offset").isArray() && place.get("offset").size() >= 2) {
      ox = place.get("offset").get(0).asInt();
      oy = place.get("offset").get(1).asInt();
    }
    if (place.has("x")) {
      ox = place.get("x").asInt();
    }
    if (place.has("y")) {
      oy = place.get("y").asInt();
    }
    int gap = place.has("gap") ? place.get("gap").asInt() : 0;
    String of = place.has("of") ? place.get("of").asText() : null;
    boolean spanWidth = place.has("spanWidth") && place.get("spanWidth").asBoolean(false);

    return switch (recipe) {
      case "fullpage", "full" -> HLayout.fullPage();
      case "under", "below" -> {
        if (StringUtils.isBlank(of)) {
          throw new HException("place.recipe=under requires place.of");
        }
        HLayout layout = HLayout.under(of, spanWidth);
        if (layout.getTop() != null) {
          layout.getTop().setOffset(gap);
        }
        if (ox != 0 && layout.getLeft() != null) {
          layout.getLeft().setOffset(ox);
        }
        yield layout;
      }
      case "rightof", "right" -> {
        if (StringUtils.isBlank(of)) {
          throw new HException("place.recipe=rightOf requires place.of");
        }
        HLayout layout = HLayout.right(of, spanWidth);
        if (layout.getLeft() != null) {
          layout.getLeft().setOffset(gap > 0 ? gap : ox);
        }
        yield layout;
      }
      case "belowfill" -> {
        if (StringUtils.isBlank(of)) {
          throw new HException("place.recipe=belowFill requires place.of");
        }
        HLayout layout = HLayout.under(of, true);
        if (layout.getTop() != null) {
          layout.getTop().setOffset(gap);
        }
        layout.setBottom(new HAttachment(null, 0, place.has("bottomMargin")
            ? place.get("bottomMargin").asInt()
            : 0, HAttachment.Alignment.BOTTOM));
        yield layout;
      }
      case "raw" -> throw new HException("Use layoutRaw for raw attachments");
      default -> {
        // topLeft
        HLayout layout = new HLayout();
        layout.setLeft(new HAttachment(null, 0, ox, HAttachment.Alignment.LEFT));
        layout.setTop(new HAttachment(null, 0, oy, HAttachment.Alignment.TOP));
        if (place.has("rightOffset") || place.has("widthToRight")) {
          int ro = place.has("rightOffset") ? place.get("rightOffset").asInt() : 0;
          layout.setRight(new HAttachment(null, 0, ro, HAttachment.Alignment.RIGHT));
        }
        if (place.has("bottomOffset")) {
          layout.setBottom(
              new HAttachment(
                  null, 0, place.get("bottomOffset").asInt(), HAttachment.Alignment.BOTTOM));
        }
        yield layout;
      }
    };
  }

  private static HPage defaultPage() {
    // A4 landscape-ish viewer default similar to ops samples: flexible canvas
    return new HPage(1200, 800, 0, 0, 0, 0);
  }

  private static void copyIfPresent(JsonNode from, ObjectNode to, String field) {
    if (from.has(field)) {
      to.set(field, from.get(field));
    }
  }

  private static String resolveComponentType(String type) {
    if (StringUtils.isBlank(type)) {
      return type;
    }
    String key = type.toLowerCase(Locale.ROOT);
    return COMPONENT_ALIASES.getOrDefault(key, type);
  }

  private static String resolveConnectorType(String type) {
    if (StringUtils.isBlank(type)) {
      return type;
    }
    String key = type.toLowerCase(Locale.ROOT);
    return CONNECTOR_ALIASES.getOrDefault(key, type);
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends IHComponent> loadComponentClass(String pluginId)
      throws HException {
    try {
      IPlugin plugin =
          PluginRegistry.getInstance().findPluginWithId(HComponentPluginType.class, pluginId);
      if (plugin == null) {
        throw new HException("Unknown component type: " + pluginId);
      }
      Object o = PluginRegistry.getInstance().loadClass(plugin);
      return (Class<? extends IHComponent>) o.getClass();
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Unable to load component type " + pluginId + ": " + e.getMessage(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends IHConnector> loadConnectorClass(String pluginId)
      throws HException {
    try {
      IPlugin plugin =
          PluginRegistry.getInstance().findPluginWithId(HConnectorPluginType.class, pluginId);
      if (plugin == null) {
        throw new HException("Unknown connector type: " + pluginId);
      }
      Object o = PluginRegistry.getInstance().loadClass(plugin);
      return (Class<? extends IHConnector>) o.getClass();
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Unable to load connector type " + pluginId + ": " + e.getMessage(), e);
    }
  }

  private static String text(JsonNode node, String field) {
    return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
  }

  private static String requiredText(JsonNode node, String field) throws HException {
    String v = text(node, field);
    if (StringUtils.isBlank(v)) {
      throw new HException("Missing required field: " + field);
    }
    return v;
  }
}
