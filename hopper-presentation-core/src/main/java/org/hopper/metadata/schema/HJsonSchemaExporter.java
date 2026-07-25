package org.hopper.metadata.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.hopper.core.HJson;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.GuiFormField;
import org.hopper.core.gui.form.GuiFormFieldType;
import org.hopper.core.gui.form.GuiFormSchema;
import org.hopper.core.gui.form.GuiFormSchemaBuilder;
import org.hopper.core.gui.form.GuiFormSection;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.HConnectorPluginType;

/**
 * Builds JSON Schema (draft-07 style) documents for Hopper wire formats. Schemas document the
 * <strong>canonical Hop polymorphic file shape</strong>; the engine also accepts flat pluginId
 * form via {@link org.hopper.metadata.codec.HMetadataCodec}.
 */
public class HJsonSchemaExporter {

  private final ObjectMapper mapper = HJson.createMapper();
  private final GuiFormSchemaBuilder formBuilder = new GuiFormSchemaBuilder();

  public String presentationSchemaJson() throws HException {
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presentationSchema());
    } catch (Exception e) {
      throw new HException("Unable to write presentation schema: " + e.getMessage(), e);
    }
  }

  public String connectorSchemaJson() throws HException {
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(connectorSchema());
    } catch (Exception e) {
      throw new HException("Unable to write connector schema: " + e.getMessage(), e);
    }
  }

  public ObjectNode presentationSchema() throws HException {
    ObjectNode root = baseSchema("Hopper presentation (Hop disk JSON)");
    ObjectNode props = mapper.createObjectNode();
    props.set("name", stringProp("Presentation name"));
    props.set("description", stringProp("Description"));
    props.set("defaultThemeName", stringProp("Light theme catalog name"));
    props.set("darkThemeName", stringProp("Dark theme catalog name"));
    props.set("virtualPath", stringProp("Optional folder path in the catalog UI"));
    props.set("autoRefreshSeconds", intProp("Optional auto-refresh interval"));
    props.set(
        "layoutMode",
        stringProp(
            "paginated (fixed sheets, multi-page tables) or continuous (browser width + vertical scroll)"));
    props.set(
        "designWidth",
        intProp(
            "Continuous design/fallback width in CSS px when no client viewport is sent (default 1200)"));
    props.set("pages", arrayOf(pageSchema()));
    props.set("interactions", arrayOf(mapper.createObjectNode().put("type", "object")));
    props.set("parameterMappings", arrayOf(mapper.createObjectNode().put("type", "object")));
    root.set("properties", props);
    root.set("required", mapper.createArrayNode().add("pages"));
    root.put(
        "description",
        "Canonical file shape uses Hop polymorphic plugins: "
            + "component: { \"HLabelComponent\": { \"pluginId\": \"HLabelComponent\", ... } }. "
            + "Flat { \"pluginId\": \"...\" } is also accepted by HMetadataCodec.");
    return root;
  }

  public ObjectNode connectorSchema() throws HException {
    ObjectNode root = baseSchema("Hopper connector (Hop disk JSON)");
    ObjectNode props = mapper.createObjectNode();
    props.set("name", stringProp("Connector name"));
    props.set("shared", boolProp("Shared catalog connector"));
    props.set(
        "connector",
        mapper
            .createObjectNode()
            .put("type", "object")
            .put(
                "description",
                "Single-key map: pluginId -> property bag (e.g. CsvConnector: { pluginId, filename, ... })"));
    root.set("properties", props);
    root.set("required", mapper.createArrayNode().add("name").add("connector"));
    // document known connector plugins
    ArrayNode oneOf = mapper.createArrayNode();
    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HConnectorPluginType.class)) {
      oneOf.add(pluginBodySchema(plugin.getIds()[0], true));
    }
    root.set("x-hopper-connector-plugins", oneOf);
    return root;
  }

  /** Per-plugin property schema derived from form annotations. */
  public ObjectNode componentPluginSchema(String pluginId) throws HException {
    GuiFormSchema form = formBuilder.buildComponentSchema(pluginId);
    return formToObjectSchema(pluginId, form, false);
  }

  public ObjectNode connectorPluginSchema(String pluginId) throws HException {
    GuiFormSchema form = formBuilder.buildConnectorSchema(pluginId);
    return formToObjectSchema(pluginId, form, true);
  }

  public ObjectNode aiContextDocument() throws HException {
    ObjectNode ctx = mapper.createObjectNode();
    ctx.put("docs", "docs/ai/README.md");
    ctx.put("wireFormat", "docs/ai/wire-format.md");
    ArrayNode components = mapper.createArrayNode();
    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HComponentPluginType.class)) {
      ObjectNode n = mapper.createObjectNode();
      n.put("id", plugin.getIds()[0]);
      n.put("name", plugin.getName());
      n.put("description", plugin.getDescription());
      components.add(n);
    }
    ctx.set("components", components);
    ArrayNode connectors = mapper.createArrayNode();
    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HConnectorPluginType.class)) {
      ObjectNode n = mapper.createObjectNode();
      n.put("id", plugin.getIds()[0]);
      n.put("name", plugin.getName());
      n.put("description", plugin.getDescription());
      connectors.add(n);
    }
    ctx.set("connectors", connectors);
    ctx.put("validate", "POST /hopper/api/ai/validate/presentation");
    ctx.put("compile", "POST /hopper/api/ai/compile/presentation");
    return ctx;
  }

  private ObjectNode pageSchema() {
    ObjectNode page = mapper.createObjectNode();
    page.put("type", "object");
    ObjectNode props = mapper.createObjectNode();
    props.set("width", intProp("Page width px"));
    props.set("height", intProp("Page height px"));
    props.set("leftMargin", intProp(null));
    props.set("rightMargin", intProp(null));
    props.set("topMargin", intProp(null));
    props.set("bottomMargin", intProp(null));
    props.set("components", arrayOf(componentWrapperSchema()));
    page.set("properties", props);
    return page;
  }

  private ObjectNode componentWrapperSchema() {
    ObjectNode wrap = mapper.createObjectNode();
    wrap.put("type", "object");
    ObjectNode props = mapper.createObjectNode();
    props.set("name", stringProp("Unique component name on the page"));
    props.set("layout", layoutSchema());
    props.set(
        "component",
        mapper
            .createObjectNode()
            .put("type", "object")
            .put(
                "description",
                "Hop polymorphic: { \"HTableComponent\": { \"pluginId\": \"HTableComponent\", ... } }"));
    wrap.set("properties", props);
    wrap.set("required", mapper.createArrayNode().add("name").add("component"));
    return wrap;
  }

  private ObjectNode layoutSchema() {
    ObjectNode layout = mapper.createObjectNode();
    layout.put("type", "object");
    ObjectNode props = mapper.createObjectNode();
    ObjectNode attachment = attachmentSchema();
    props.set("left", attachment);
    props.set("right", attachment);
    props.set("top", attachment);
    props.set("bottom", attachment);
    layout.set("properties", props);
    return layout;
  }

  private ObjectNode attachmentSchema() {
    ObjectNode a = mapper.createObjectNode();
    a.put("type", "object");
    ObjectNode props = mapper.createObjectNode();
    props.set("componentName", stringProp("Null/empty = page; else peer component name"));
    props.set("percentage", intProp("Percent of reference size"));
    props.set("offset", intProp("Pixel offset"));
    ObjectNode align = mapper.createObjectNode();
    align.put("type", "string");
    align.set(
        "enum",
        mapper
            .createArrayNode()
            .add("DEFAULT")
            .add("TOP")
            .add("BOTTOM")
            .add("LEFT")
            .add("RIGHT")
            .add("CENTER"));
    props.set("alignment", align);
    a.set("properties", props);
    return a;
  }

  private ObjectNode pluginBodySchema(String pluginId, boolean connector) throws HException {
    try {
      return connector ? connectorPluginSchema(pluginId) : componentPluginSchema(pluginId);
    } catch (Exception e) {
      ObjectNode n = mapper.createObjectNode();
      n.put("title", pluginId);
      n.put("type", "object");
      return n;
    }
  }

  private ObjectNode formToObjectSchema(String pluginId, GuiFormSchema form, boolean connector) {
    ObjectNode n = mapper.createObjectNode();
    n.put("$schema", "http://json-schema.org/draft-07/schema#");
    n.put("title", pluginId);
    n.put("type", "object");
    if (form.getPluginDescription() != null) {
      n.put("description", form.getPluginDescription());
    }
    ObjectNode props = mapper.createObjectNode();
    props.set("pluginId", stringProp("Must equal " + pluginId));
    if (form.getSections() != null) {
      for (GuiFormSection section : form.getSections()) {
        if (section.getFields() == null) {
          continue;
        }
        for (GuiFormField field : section.getFields()) {
          if (field.getFieldName() == null || "name".equals(field.getFieldName())) {
            continue;
          }
          props.set(field.getFieldName(), fieldToSchema(field));
        }
      }
    }
    n.set("properties", props);
    return n;
  }

  private ObjectNode fieldToSchema(GuiFormField field) {
    ObjectNode n = mapper.createObjectNode();
    GuiFormFieldType type = field.getType();
    if (type == null) {
      n.put("type", "string");
    } else {
      switch (type) {
        case CHECKBOX -> n.put("type", "boolean");
        case COMBO -> {
          n.put("type", "string");
          if (field.getComboValues() != null && !field.getComboValues().isEmpty()) {
            ArrayNode en = mapper.createArrayNode();
            for (String v : field.getComboValues()) {
              en.add(v);
            }
            n.set("enum", en);
          }
        }
        case LIST -> n.put("type", "array");
        default -> n.put("type", "string");
      }
    }
    if (field.getLabel() != null) {
      n.put("title", field.getLabel());
    }
    if (field.getToolTip() != null) {
      n.put("description", field.getToolTip());
    }
    return n;
  }

  private ObjectNode baseSchema(String title) {
    ObjectNode root = mapper.createObjectNode();
    root.put("$schema", "http://json-schema.org/draft-07/schema#");
    root.put("title", title);
    root.put("type", "object");
    return root;
  }

  private ObjectNode stringProp(String description) {
    ObjectNode n = mapper.createObjectNode().put("type", "string");
    if (description != null) {
      n.put("description", description);
    }
    return n;
  }

  private ObjectNode intProp(String description) {
    ObjectNode n = mapper.createObjectNode().put("type", "integer");
    if (description != null) {
      n.put("description", description);
    }
    return n;
  }

  private ObjectNode boolProp(String description) {
    ObjectNode n = mapper.createObjectNode().put("type", "boolean");
    if (description != null) {
      n.put("description", description);
    }
    return n;
  }

  private ObjectNode arrayOf(ObjectNode item) {
    ObjectNode n = mapper.createObjectNode();
    n.put("type", "array");
    n.set("items", item);
    return n;
  }

  /** Write schemas under a directory (for docs/ai/schemas). */
  public void writeToDirectory(java.nio.file.Path dir) throws Exception {
    java.nio.file.Files.createDirectories(dir);
    java.nio.file.Files.writeString(dir.resolve("presentation.schema.json"), presentationSchemaJson());
    java.nio.file.Files.writeString(dir.resolve("connector.schema.json"), connectorSchemaJson());
    java.nio.file.Path compDir = dir.resolve("plugins/components");
    java.nio.file.Path connDir = dir.resolve("plugins/connectors");
    java.nio.file.Files.createDirectories(compDir);
    java.nio.file.Files.createDirectories(connDir);
    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HComponentPluginType.class)) {
      String id = plugin.getIds()[0];
      java.nio.file.Files.writeString(
          compDir.resolve(id + ".schema.json"),
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(componentPluginSchema(id)));
    }
    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HConnectorPluginType.class)) {
      String id = plugin.getIds()[0];
      java.nio.file.Files.writeString(
          connDir.resolve(id + ".schema.json"),
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(connectorPluginSchema(id)));
    }
  }
}
