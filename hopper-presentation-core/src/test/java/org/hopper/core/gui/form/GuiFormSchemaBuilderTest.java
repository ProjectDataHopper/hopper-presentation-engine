package org.hopper.core.gui.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;

class GuiFormSchemaBuilderTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void buildsLabelSchemaFromHopperWidgetElements() throws Exception {
    GuiFormSchemaBuilder builder = new GuiFormSchemaBuilder();
    GuiFormSchema schema = builder.buildComponentSchema("HLabelComponent");

    assertEquals("HLabelComponent", schema.getPluginId());
    assertEquals("Label", schema.getPluginName());
    assertTrue(schema.isHasPluginWidgets());
    assertFalse(schema.getSections().isEmpty());

    GuiFormSection plugin =
        findSection(schema, HGuiFormConstants.SECTION_PLUGIN).orElseThrow();
    assertTrue(plugin.isOpenByDefault());

    assertTrue(findField(plugin, "label").isPresent());
    assertEquals(GuiFormFieldType.TEXT, findField(plugin, "label").get().getType());

    assertTrue(findField(plugin, "underline").isPresent());
    assertEquals(GuiFormFieldType.CHECKBOX, findField(plugin, "underline").get().getType());

    GuiFormField ha = findField(plugin, "horizontalAlignment").orElseThrow();
    assertEquals(GuiFormFieldType.COMBO, ha.getType());
    assertTrue(ha.getComboValues().contains("LEFT"));
    assertTrue(ha.getComboValues().contains("CENTER"));

    GuiFormSection base = findSection(schema, HGuiFormConstants.SECTION_BASE).orElseThrow();
    assertTrue(findField(base, "themeName").isPresent());
    assertEquals(GuiFormFieldType.COMBO, findField(base, "themeName").get().getType());
    assertEquals("themes", findField(base, "themeName").get().getComboSource());
    // Input connector is promoted out of base into the wrapper strip (under component name)
    assertTrue(findField(base, "sourceConnectorName").isEmpty());
    assertEquals(GuiFormFieldType.COLOR, findField(base, "borderColor").orElseThrow().getType());
    assertEquals(GuiFormFieldType.FONT, findField(base, "defaultFont").orElseThrow().getType());

    assertTrue(findSection(schema, HGuiFormConstants.SECTION_LAYOUT).isPresent());
    GuiFormSection wrapper = findSection(schema, HGuiFormConstants.SECTION_WRAPPER).orElseThrow();
    assertTrue(findField(wrapper, "sourceConnectorName").isPresent());
    assertEquals(
        "connectors", findField(wrapper, "sourceConnectorName").get().getComboSource());
    assertEquals("plugin", findField(wrapper, "sourceConnectorName").get().getBinding());
    assertEquals("Input connector", findField(wrapper, "sourceConnectorName").get().getLabel());

    GuiFormSection props =
        findSection(schema, HGuiFormConstants.SECTION_COMPONENT_PROPS).orElseThrow();
    assertEquals("Component properties", props.getTitle());
    assertTrue(findField(props, "rotation").isPresent());
    assertEquals(GuiFormFieldType.TEXT, findField(props, "rotation").get().getType());
    assertEquals("wrapper", findField(props, "rotation").get().getBinding());
    assertTrue(findField(props, "transparency").isPresent());
    GuiFormField clipSize = findField(props, "clipSize").orElseThrow();
    assertEquals(GuiFormFieldType.SIZE, clipSize.getType());
    assertEquals("wrapper", clipSize.getBinding());
  }

  @Test
  void sqlConnectorDatabaseComboUsesMetadataSource() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema("SqlConnector");
    GuiFormField db =
        schema.getSections().stream()
            .flatMap(s -> s.getFields().stream())
            .filter(f -> "databaseConnectionName".equals(f.getFieldName()))
            .findFirst()
            .orElseThrow();
    assertEquals(GuiFormFieldType.COMBO, db.getType());
    assertEquals("metadata", db.getComboSource());
    assertEquals("hopper-database-connection", db.getMetadataKey());
  }

  @Test
  void rendersHtmlWithLoadAndSaveScripts() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildComponentSchema("HLabelComponent");
    String html = new GuiFormHtmlRenderer().render(schema);

    assertTrue(html.contains("id=\"label\""));
    assertTrue(html.contains("id=\"underline\""));
    assertTrue(html.contains("id=\"horizontalAlignment\""));
    assertTrue(html.contains("id=\"initScript\""));
    assertTrue(html.contains("id=\"loadScript\""));
    assertTrue(html.contains("id=\"componentSaveScript\""));
    assertTrue(html.contains("setElement(iComponent, \"label\", \"label\")"));
    assertTrue(html.contains("getElement(iComponent, \"label\", \"label\")"));
    assertTrue(html.contains("setLayout(componentJson, \"left\")"));
    assertTrue(html.contains("id=\"rotation\""));
    assertTrue(html.contains("id=\"transparency\""));
    assertTrue(html.contains("id=\"clipSizeWidth\""));
    assertTrue(html.contains("id=\"clipSizeHeight\""));
    assertTrue(html.contains("setSize(componentJson, \"clipSize\", \"clipSize\")"));
    // Input connector row with preview/layout actions
    assertTrue(html.contains("id=\"sourceConnectorName\""));
    assertTrue(html.contains("source-connector-row"));
    assertTrue(html.contains("previewSourceConnectorData"));
    assertTrue(html.contains("previewSourceConnectorLayout"));
    assertTrue(html.contains("id=\"sourceConnectorInspect\""));
    assertTrue(html.contains("getSize(componentJson, \"clipSize\", \"clipSize\")"));
    assertTrue(html.contains("setElement(componentJson, \"rotation\", \"rotation\")"));
    assertTrue(html.contains("getElement(componentJson, \"rotation\", \"rotation\")"));
  }

  @Test
  void buildsTableSchemaWithColumnList() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildComponentSchema("HTableComponent");
    assertTrue(schema.isHasPluginWidgets());

    GuiFormSection plugin =
        findSection(schema, HGuiFormConstants.SECTION_PLUGIN).orElseThrow();
    GuiFormField columns = findField(plugin, "columnSelection").orElseThrow();
    assertEquals(GuiFormFieldType.LIST, columns.getType());
    assertEquals("column", columns.getItemKind());

    String html = new GuiFormHtmlRenderer().render(schema);
    assertTrue(html.contains("id=\"columnSelection\""));
    assertTrue(html.contains("setColumns(iComponent, \"columnSelection\""));
    assertTrue(html.contains("getColumns(iComponent, \"columnSelection\""));
  }

  @Test
  void buildsLineChartSchemaWithFactsList() throws Exception {
    GuiFormSchema schema =
        new GuiFormSchemaBuilder().buildComponentSchema("HLineChartComponent");
    assertTrue(schema.isHasPluginWidgets());
    GuiFormSection plugin =
        findSection(schema, HGuiFormConstants.SECTION_PLUGIN).orElseThrow();
    GuiFormField facts = findField(plugin, "facts").orElseThrow();
    assertEquals(GuiFormFieldType.LIST, facts.getType());
    assertEquals("fact", facts.getItemKind());
    assertTrue(findField(plugin, "drawingCurvedTrendLine").isPresent());
  }

  @Test
  void buildsSqlConnectorSchema() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema("SqlConnector");
    assertEquals("SqlConnector", schema.getPluginId());
    assertTrue(schema.isHasPluginWidgets());
    assertFalse(schema.getSections().isEmpty());
    GuiFormSection section = schema.getSections().get(0);
    assertTrue(findField(section, "databaseConnectionName").isPresent());
    assertTrue(findField(section, "sql").isPresent());
  }

  @Test
  void buildsSampleDataConnectorSchema() throws Exception {
    GuiFormSchema schema =
        new GuiFormSchemaBuilder().buildConnectorSchema("SampleDataConnector");
    GuiFormField rowCount = findField(schema.getSections().get(0), "rowCount").orElseThrow();
    assertTrue(rowCount.isIntegerValue());
  }

  @Test
  void buildsGroupSchemaWithNestedComponentAndCatalog() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildComponentSchema("HGroupComponent");
    assertTrue(schema.isHasPluginWidgets());
    assertFalse(schema.getComponentCatalog().isEmpty());

    GuiFormSection plugin =
        findSection(schema, HGuiFormConstants.SECTION_PLUGIN).orElseThrow();
    GuiFormField groupComponent = findField(plugin, "groupComponent").orElseThrow();
    assertEquals(GuiFormFieldType.COMPONENT, groupComponent.getType());

    // Catalog includes Label for nested editing
    assertTrue(
        schema.getComponentCatalog().stream()
            .anyMatch(c -> "HLabelComponent".equals(c.getPluginId())));

    String html = new GuiFormHtmlRenderer().render(schema);
    assertTrue(html.contains("window.componentCatalog"));
    assertTrue(html.contains("groupComponent_panel"));
    assertTrue(html.contains("setNestedComponent(iComponent, \"groupComponent\""));
    assertTrue(html.contains("getNestedComponent(iComponent, \"groupComponent\""));
  }

  @Test
  void buildsCompositeSchemaWithComponentList() throws Exception {
    GuiFormSchema schema =
        new GuiFormSchemaBuilder().buildComponentSchema("HCompositeComponent");
    assertTrue(schema.isHasPluginWidgets());
    assertFalse(schema.getComponentCatalog().isEmpty());

    GuiFormSection plugin =
        findSection(schema, HGuiFormConstants.SECTION_PLUGIN).orElseThrow();
    GuiFormField children = findField(plugin, "children").orElseThrow();
    assertEquals(GuiFormFieldType.LIST, children.getType());
    assertEquals("component", children.getItemKind());

    String html = new GuiFormHtmlRenderer().render(schema);
    assertTrue(html.contains("children_items"));
    assertTrue(html.contains("setNestedComponentList(iComponent, \"children\""));
    assertTrue(html.contains("getNestedComponentList(iComponent, \"children\""));
  }

  private Optional<GuiFormSection> findSection(GuiFormSchema schema, String id) {
    return schema.getSections().stream().filter(s -> id.equals(s.getId())).findFirst();
  }

  private Optional<GuiFormField> findField(GuiFormSection section, String id) {
    return section.getFields().stream().filter(f -> id.equals(f.getId())).findFirst();
  }
}
