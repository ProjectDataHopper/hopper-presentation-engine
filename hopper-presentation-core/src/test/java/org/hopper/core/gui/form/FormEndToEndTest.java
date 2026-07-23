package org.hopper.core.gui.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;

/**
 * End-to-end checks for annotation → schema → HTML, plus connector metadata save/load round-trips
 * using Hop's JSON metadata format (same path hopper-presentation-rest uses).
 *
 * <p>Also writes sample schema/HTML under {@code target/form-review/} for manual inspection.
 */
class FormEndToEndTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static Path reviewDir;

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
    reviewDir = Path.of("target/form-review");
    Files.createDirectories(reviewDir.resolve("components"));
    Files.createDirectories(reviewDir.resolve("connectors"));
  }

  @Test
  void everyComponentAndConnectorProducesUsableHtml() throws Exception {
    GuiFormSchemaBuilder builder = new GuiFormSchemaBuilder();
    GuiFormHtmlRenderer renderer = new GuiFormHtmlRenderer();
    List<String> failures = new ArrayList<>();

    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HComponentPluginType.class)) {
      String id = plugin.getIds()[0];
      try {
        GuiFormSchema schema = builder.buildComponentSchema(id);
        String html = renderer.render(schema);
        assertTrue(html.contains("componentSaveScript"), id);
        assertTrue(html.contains("loadScript"), id);
        assertTrue(html.contains("initScript"), id);
        for (GuiFormSection section : schema.getSections()) {
          for (GuiFormField field : section.getFields()) {
            if (field.getType() == GuiFormFieldType.LAYOUT_SIDE) {
              continue;
            }
            // Field id appears as DOM id (or nested panel prefix)
            assertTrue(
                html.contains("id=\"" + field.getId() + "\"")
                    || html.contains("id=\"" + field.getId() + "_")
                    || html.contains("\"" + field.getId() + "\""),
                id + " HTML missing field id=" + field.getId());
          }
        }
        writeReview("components", id, schema, html);
      } catch (Exception e) {
        failures.add("component " + id + ": " + e.getMessage());
      }
    }

    for (IPlugin plugin : PluginRegistry.getInstance().getPlugins(HConnectorPluginType.class)) {
      String id = plugin.getIds()[0];
      try {
        GuiFormSchema schema = builder.buildConnectorSchema(id);
        String html = renderer.renderConnector(schema);
        assertTrue(html.contains("connectorSaveScript"), id);
        assertTrue(html.contains("connectorName"), id);
        writeReview("connectors", id, schema, html);
      } catch (Exception e) {
        failures.add("connector " + id + ": " + e.getMessage());
      }
    }

    writeIndex();
    if (!failures.isEmpty()) {
      fail(String.join("\n", failures));
    }
  }

  @Test
  void connectorMetadataRoundTripSampleData() throws Exception {
    IHopMetadataProvider provider = new MemoryMetadataProvider();
    IHopMetadataSerializer<HConnector> serializer =
        provider.getSerializer(HConnector.class);

    HSampleDataConnector plugin = new HSampleDataConnector(42);
    HConnector original = new HConnector("Sample Data", plugin);
    serializer.save(original);

    HConnector loaded = serializer.load("Sample Data");
    assertNotNull(loaded);
    assertEquals("Sample Data", loaded.getName());
    assertTrue(loaded.getConnector() instanceof HSampleDataConnector);
    assertEquals(42, ((HSampleDataConnector) loaded.getConnector()).getRowCount());

    // Mutate and save again (simulates form edit)
    ((HSampleDataConnector) loaded.getConnector()).setRowCount(99);
    serializer.save(loaded);

    HConnector again = serializer.load("Sample Data");
    assertEquals(99, ((HSampleDataConnector) again.getConnector()).getRowCount());

    // Hop JSON shape used by hopper-presentation-rest GET connector-json / modify
    JsonMetadataParser<HConnector> parser =
        new JsonMetadataParser<>(HConnector.class, provider);
    JSONObject json = parser.getJsonObject(again);
    assertNotNull(json.get("name"));
    assertNotNull(json.get("connector"));

    String jsonString = json.toJSONString();
    assertTrue(jsonString.contains("SampleDataConnector") || jsonString.contains("rowCount"));

    HConnector fromJson =
        parser.loadJsonObject(
            HConnector.class, new JsonFactory().createParser(jsonString));
    assertEquals("Sample Data", fromJson.getName());
    assertEquals(99, ((HSampleDataConnector) fromJson.getConnector()).getRowCount());

    // Schema fields include rowCount for this plugin
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema("SampleDataConnector");
    assertTrue(schema.getSections().stream()
        .flatMap(s -> s.getFields().stream())
        .anyMatch(f -> "rowCount".equals(f.getFieldName())));
  }

  @Test
  void connectorMetadataRoundTripSql() throws Exception {
    IHopMetadataProvider provider = new MemoryMetadataProvider();
    IHopMetadataSerializer<HConnector> serializer =
        provider.getSerializer(HConnector.class);

    HSqlConnector sql = new HSqlConnector("SteelWheels", "select 1 as x");
    HConnector original = new HConnector("territories", sql);
    serializer.save(original);

    HConnector loaded = serializer.load("territories");
    HSqlConnector loadedSql = (HSqlConnector) loaded.getConnector();
    assertEquals("SteelWheels", loadedSql.getDatabaseConnectionName());
    assertTrue(loadedSql.getSql().contains("select"));

    loadedSql.setSql("select 2 as y");
    serializer.save(loaded);

    JsonMetadataParser<HConnector> parser =
        new JsonMetadataParser<>(HConnector.class, provider);
    JSONObject json = parser.getJsonObject(serializer.load("territories"));
    HConnector fromJson =
        parser.loadJsonObject(
            HConnector.class, new JsonFactory().createParser(json.toJSONString()));
    assertEquals("select 2 as y", ((HSqlConnector) fromJson.getConnector()).getSql());

    // Rename path (as modify/connector does)
    fromJson.setName("territories-renamed");
    serializer.save(fromJson);
    serializer.delete("territories");
    assertFalse(serializer.exists("territories"));
    assertTrue(serializer.exists("territories-renamed"));
  }

  @Test
  void labelAndGroupSchemasHaveExpectedStructure() throws Exception {
    GuiFormSchema label = new GuiFormSchemaBuilder().buildComponentSchema("HLabelComponent");
    assertTrue(label.isHasPluginWidgets());
    assertTrue(label.getSections().stream().anyMatch(s -> "plugin".equals(s.getId())));
    assertTrue(label.getSections().stream().anyMatch(s -> "layout".equals(s.getId())));

    GuiFormSchema group = new GuiFormSchemaBuilder().buildComponentSchema("HGroupComponent");
    assertFalse(group.getComponentCatalog().isEmpty());
    assertTrue(group.getSections().stream()
        .flatMap(s -> s.getFields().stream())
        .anyMatch(f -> f.getType() == GuiFormFieldType.COMPONENT));
  }

  private void writeReview(String kind, String id, GuiFormSchema schema, String html)
      throws Exception {
    Path dir = reviewDir.resolve(kind);
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve(id + ".schema.json"),
        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema),
        StandardCharsets.UTF_8);
    Files.writeString(dir.resolve(id + ".form.html"), wrapStandaloneHtml(id, html), StandardCharsets.UTF_8);
  }

  private String wrapStandaloneHtml(String title, String bodySnippet) {
    // Minimal shell so files open in a browser (scripts need jQuery globals for full interactivity)
    return """
        <!DOCTYPE html>
        <html lang="en"><head>
        <meta charset="utf-8"/>
        <title>%s form preview</title>
        <style>
          body { font-family: system-ui, sans-serif; margin: 16px; max-width: 960px; }
          .content { margin-left: 8px; }
          fieldset { margin: 8px 0; }
          table { border-collapse: collapse; width: 100%%; }
          th, td { border: 1px solid #ccc; padding: 4px; }
          button.collapsible { width: 100%%; text-align: left; margin-top: 8px; }
        </style>
        </head><body>
        <h1>%s</h1>
        <p><em>Preview of generated form markup (save/load scripts require hopper-presentation-rest runtime).</em></p>
        %s
        </body></html>
        """
        .formatted(title, title, bodySnippet);
  }

  private void writeIndex() throws Exception {
    StringBuilder idx = new StringBuilder();
    idx.append("# Generated form review\n\n");
    idx.append("Produced by `FormEndToEndTest` under `target/form-review/`.\n\n");
    idx.append("## Components\n\n");
    try (var stream = Files.list(reviewDir.resolve("components"))) {
      stream
          .filter(p -> p.toString().endsWith(".form.html"))
          .sorted()
          .forEach(
              p -> {
                String name = p.getFileName().toString().replace(".form.html", "");
                idx.append("- **")
                    .append(name)
                    .append("**: [form](components/")
                    .append(name)
                    .append(".form.html) · [schema](components/")
                    .append(name)
                    .append(".schema.json)\n");
              });
    }
    idx.append("\n## Connectors\n\n");
    try (var stream = Files.list(reviewDir.resolve("connectors"))) {
      stream
          .filter(p -> p.toString().endsWith(".form.html"))
          .sorted()
          .forEach(
              p -> {
                String name = p.getFileName().toString().replace(".form.html", "");
                idx.append("- **")
                    .append(name)
                    .append("**: [form](connectors/")
                    .append(name)
                    .append(".form.html) · [schema](connectors/")
                    .append(name)
                    .append(".schema.json)\n");
              });
    }
    Files.writeString(reviewDir.resolve("README.md"), idx.toString(), StandardCharsets.UTF_8);
  }
}
