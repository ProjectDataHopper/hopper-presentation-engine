package org.hopper.core.gui.form;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HSize;
import org.hopper.core.HSortMethod;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HGuiRegistry;
import org.hopper.core.gui.plugin.HGuiWidgetAdapter;
import org.hopper.core.gui.plugin.HWidgetElements;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.hopper.presentation.component.types.group.GroupKeyMapping;
import org.hopper.presentation.connector.types.filter.SimpleFilterValue;
import org.hopper.presentation.connector.types.rest.HRestConnector;

/**
 * Builds {@link GuiFormSchema} from {@link org.hopper.core.gui.plugin.HWidgetElement} annotations
 * on Hopper plugin classes (via {@link HGuiRegistry}), plus shared wrapper / base / layout
 * sections for the browser editor.
 */
public class GuiFormSchemaBuilder {

  /** Max nesting depth for COMPONENT fields inside the component catalog (Group→Composite→…). */
  public static final int MAX_NESTED_COMPONENT_DEPTH = 3;

  /**
   * Max nesting depth for nested connector lists inside the connector catalog (Chain inside
   * Chain). At this depth, {@code itemKind=connector} list fields are stripped from catalog
   * entries so the catalog stays finite.
   */
  public static final int MAX_NESTED_CONNECTOR_DEPTH = 1;

  /**
   * Build a form schema for a component plugin id (e.g. {@code HLabelComponent}).
   *
   * @param pluginId plugin id from {@link HComponentPlugin#id()}
   * @return schema including shared sections; {@link GuiFormSchema#isHasPluginWidgets()} is true
   *     when the plugin class declared at least one {@code @HWidgetElement}
   */
  public GuiFormSchema buildComponentSchema(String pluginId) throws HException {
    GuiFormSchema schema = buildComponentSchemaInternal(pluginId, true, 0);
    if (schemaNeedsComponentCatalog(schema)) {
      schema.setComponentCatalog(buildComponentCatalog(0));
    }
    return schema;
  }

  /**
   * Build plugin + base (+ optional wrapper/layout) schema without attaching a catalog (used when
   * building catalog entries).
   */
  private GuiFormSchema buildComponentSchemaInternal(
      String pluginId, boolean includeWrapperAndLayout, int nestedDepth) throws HException {
    IPlugin plugin = PluginRegistry.getInstance().findPluginWithId(HComponentPluginType.class, pluginId);
    if (plugin == null) {
      throw new HException("Component plugin not found: " + pluginId);
    }

    Class<? extends IHComponent> componentClass = loadComponentClass(plugin, pluginId);

    HComponentPlugin annotation = componentClass.getAnnotation(HComponentPlugin.class);
    String name = annotation != null ? annotation.name() : plugin.getName();
    String description = annotation != null ? annotation.description() : plugin.getDescription();

    GuiFormSchema schema = new GuiFormSchema(pluginId, name);
    schema.setPluginDescription(description);
    schema.setPluginClassName(componentClass.getName());

    Map<String, List<GuiFormField>> byParent = collectAnnotatedFields(componentClass);

    if (includeWrapperAndLayout) {
      schema.getSections().add(buildWrapperSection());
    }

    // Plugin-specific (parent HComponent-Plugin or any parent not base)
    GuiFormSection pluginSection =
        buildSectionFromFields(
            HGuiFormConstants.SECTION_PLUGIN,
            name,
            true,
            byParent.getOrDefault(HGuiFormConstants.PARENT_PLUGIN, List.of()));
    // Also include fields with empty parent or custom parents under plugin section
    for (Map.Entry<String, List<GuiFormField>> entry : byParent.entrySet()) {
      String parentId = entry.getKey();
      if (HGuiFormConstants.PARENT_PLUGIN.equals(parentId)
          || HGuiFormConstants.PARENT_BASE.equals(parentId)
          || HGuiFormConstants.PARENT_WRAPPER.equals(parentId)
          || HGuiFormConstants.PARENT_LAYOUT.equals(parentId)
          || HGuiFormConstants.PARENT_COMPONENT_PROPS.equals(parentId)) {
        continue;
      }
      pluginSection.getFields().addAll(entry.getValue());
    }

    // At max depth, strip nested component fields so the catalog stays finite
    if (nestedDepth >= MAX_NESTED_COMPONENT_DEPTH) {
      pluginSection
          .getFields()
          .removeIf(
              f ->
                  f.getType() == GuiFormFieldType.COMPONENT
                      || (f.getType() == GuiFormFieldType.LIST
                          && "component".equals(f.getItemKind())));
    }

    sortFields(pluginSection.getFields());
    if (!pluginSection.getFields().isEmpty()) {
      schema.setHasPluginWidgets(true);
      schema.getSections().add(pluginSection);
    }

    // Base component options from annotations on HBaseComponent, else defaults.
    List<GuiFormField> baseFields =
        new ArrayList<>(
            byParent.getOrDefault(HGuiFormConstants.PARENT_BASE, new ArrayList<>()));
    if (baseFields.isEmpty()) {
      baseFields = defaultBaseFields();
    }

    // Input connector sits in the top strip under the component name (not buried in
    // "General component options"). Binding stays "plugin" — value is on iComponent.
    if (includeWrapperAndLayout) {
      promoteSourceConnectorToWrapper(schema, baseFields);
    }

    GuiFormSection baseSection =
        buildSectionFromFields(
            HGuiFormConstants.SECTION_BASE, "General component options", false, baseFields);
    schema.getSections().add(baseSection);

    if (includeWrapperAndLayout) {
      // HComponent-level rotation / transparency / clip size
      schema.getSections().add(buildComponentPropertiesSection());
      schema.getSections().add(buildLayoutSection());
    }

    return schema;
  }

  /**
   * Move {@code sourceConnectorName} from base fields into the wrapper section (right below
   * component name) so it is always visible with preview/layout actions.
   */
  private void promoteSourceConnectorToWrapper(
      GuiFormSchema schema, List<GuiFormField> baseFields) {
    GuiFormField source = removeFieldByName(baseFields, "sourceConnectorName");
    if (source == null) {
      source =
          baseField("sourceConnectorName", GuiFormFieldType.COMBO, "Input connector", "00200");
      source.setComboSource("connectors");
    } else {
      source.setLabel("Input connector");
      source.setOrder("00200");
    }
    source.setBinding("plugin");
    for (GuiFormSection section : schema.getSections()) {
      if (HGuiFormConstants.SECTION_WRAPPER.equals(section.getId())) {
        section.getFields().add(source);
        return;
      }
    }
    // No wrapper section (should not happen when includeWrapperAndLayout) — put back
    baseFields.add(0, source);
  }

  private static GuiFormField removeFieldByName(List<GuiFormField> fields, String fieldName) {
    for (int i = 0; i < fields.size(); i++) {
      GuiFormField f = fields.get(i);
      if (fieldName.equals(f.getFieldName()) || fieldName.equals(f.getId())) {
        return fields.remove(i);
      }
    }
    return null;
  }

  private Class<? extends IHComponent> loadComponentClass(IPlugin plugin, String pluginId)
      throws HException {
    try {
      String className = plugin.getClassMap().get(IHComponent.class);
      if (className == null) {
        throw new HException("No main class mapping for component plugin " + pluginId);
      }
      ClassLoader classLoader = PluginRegistry.getInstance().getClassLoader(plugin);
      @SuppressWarnings("unchecked")
      Class<? extends IHComponent> loaded =
          (Class<? extends IHComponent>) classLoader.loadClass(className);
      return loaded;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Unable to load class for component plugin " + pluginId, e);
    }
  }

  private boolean schemaNeedsComponentCatalog(GuiFormSchema schema) {
    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        if (field.getType() == GuiFormFieldType.COMPONENT) {
          return true;
        }
        if (field.getType() == GuiFormFieldType.LIST && "component".equals(field.getItemKind())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Build a catalog of all registered component plugins for nested editors.
   *
   * @param nestedDepth depth used when building each type's field list (0 = top of catalog)
   */
  public List<GuiFormComponentTypeInfo> buildComponentCatalog(int nestedDepth)
      throws HException {
    List<GuiFormComponentTypeInfo> catalog = new ArrayList<>();
    PluginRegistry registry = PluginRegistry.getInstance();
    List<IPlugin> plugins = registry.getPlugins(HComponentPluginType.class);
    for (IPlugin plugin : plugins) {
      String pluginId = plugin.getIds()[0];
      try {
        GuiFormSchema typeSchema = buildComponentSchemaInternal(pluginId, false, nestedDepth + 1);
        GuiFormComponentTypeInfo info =
            new GuiFormComponentTypeInfo(
                pluginId, typeSchema.getPluginName(), typeSchema.getPluginDescription());
        info.setSections(typeSchema.getSections());
        catalog.add(info);
      } catch (Exception e) {
        // Skip plugins that cannot be schema-built
      }
    }
    catalog.sort(Comparator.comparing(GuiFormComponentTypeInfo::getPluginId));
    return catalog;
  }

  /**
   * Whether a component plugin has a usable generated form (always true for known plugins once
   * shared sections exist; preferred when annotations are present).
   */
  public boolean canBuildComponentSchema(String pluginId) {
    try {
      IPlugin plugin =
          PluginRegistry.getInstance().findPluginWithId(HComponentPluginType.class, pluginId);
      return plugin != null;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Build a form schema for an arbitrary annotated class (e.g. Hop variable-resolver plugins
   * discovered via {@link HGuiRegistry} from {@code @GuiWidgetElement} or {@code
   * @HWidgetElement}).
   *
   * @param pluginId stable id (plugin id)
   * @param pluginName display name
   * @param pluginDescription optional description
   * @param clazz class whose fields are registered in {@link HGuiRegistry}
   */
  public GuiFormSchema buildClassSchema(
      String pluginId, String pluginName, String pluginDescription, Class<?> clazz) {
    GuiFormSchema schema =
        new GuiFormSchema(
            pluginId != null ? pluginId : "",
            pluginName != null ? pluginName : (clazz != null ? clazz.getSimpleName() : ""));
    schema.setPluginDescription(pluginDescription);
    if (clazz != null) {
      schema.setPluginClassName(clazz.getName());
    }

    if (clazz == null) {
      return schema;
    }

    // Ensure widgets are registered (lazy path for tests / late-loaded plugins)
    HGuiRegistry.getInstance().getElementsByParent(clazz);

    Map<String, List<GuiFormField>> byParent = collectAnnotatedFields(clazz);
    List<GuiFormField> all = new ArrayList<>();
    for (List<GuiFormField> fields : byParent.values()) {
      all.addAll(fields);
    }
    sortFields(all);

    GuiFormSection section =
        buildSectionFromFields(
            HGuiFormConstants.SECTION_PLUGIN,
            pluginName != null ? pluginName : "Plugin options",
            true,
            all);
    if (!section.getFields().isEmpty()) {
      schema.setHasPluginWidgets(true);
      schema.getSections().add(section);
    }
    return schema;
  }

  private Map<String, List<GuiFormField>> collectAnnotatedFields(Class<?> clazz) {
    Map<String, List<HWidgetElements>> byParentWidgets =
        HGuiRegistry.getInstance().getElementsByParent(clazz);
    Map<String, List<GuiFormField>> byParent = new LinkedHashMap<>();
    for (Map.Entry<String, List<HWidgetElements>> entry : byParentWidgets.entrySet()) {
      List<GuiFormField> fields = new ArrayList<>();
      for (HWidgetElements widget : entry.getValue()) {
        if (widget.isIgnored()) {
          continue;
        }
        fields.add(toFormField(widget));
      }
      sortFields(fields);
      byParent.put(entry.getKey(), fields);
    }
    return byParent;
  }

  private GuiFormField toFormField(HWidgetElements widget) {
    Field field = widget.getField();
    Class<?> ownerClass = widget.getOwnerClass();
    Class<?> resourceClass =
        field != null && field.getDeclaringClass() != null
            ? field.getDeclaringClass()
            : ownerClass;
    String i18nPackage =
        resourceClass != null && resourceClass.getPackage() != null
            ? resourceClass.getPackage().getName()
            : "";

    GuiFormField formField = new GuiFormField();
    formField.setId(widget.getId());
    formField.setOrder(widget.getOrder());
    String rawLabel =
        StringUtils.isEmpty(widget.getLabel()) ? widget.getFieldName() : widget.getLabel();
    // Re-resolve Hop i18n (i18n:pkg:key or !Key!) so admin forms show human labels
    formField.setLabel(
        HGuiWidgetAdapter.resolveI18n(rawLabel, i18nPackage, resourceClass));
    formField.setToolTip(
        HGuiWidgetAdapter.resolveI18n(widget.getToolTip(), i18nPackage, resourceClass));
    formField.setTabName(widget.getTabName());
    formField.setTabTooltip(widget.getTabTooltip());
    formField.setFieldName(widget.getFieldName());
    formField.setPassword(widget.isPassword());
    formField.setVariablesEnabled(widget.isVariablesEnabled());
    formField.setMultiLineTextHeight(widget.getMultiLineTextHeight());
    formField.setBinding(bindingForParent(widget.getParentId()));
    formField.setType(mapType(widget, field));

    Class<?> fieldType = field.getType();
    if (fieldType == int.class
        || fieldType == Integer.class
        || fieldType == long.class
        || fieldType == Long.class) {
      formField.setIntegerValue(true);
    }

    if (formField.getType() == GuiFormFieldType.LIST) {
      Class<?> itemClass = resolveListItemClass(field);
      if (itemClass != null) {
        formField.setItemClassName(itemClass.getName());
        formField.setItemKind(resolveItemKind(itemClass));
      } else {
        formField.setItemKind("string");
      }
    }

    if (formField.getType() == GuiFormFieldType.COMBO
        || formField.getType() == GuiFormFieldType.METADATA) {
      formField.setComboValues(resolveComboValues(widget, field, ownerClass));
      applyComboSource(formField, widget, field);
    }
    return formField;
  }

  /**
   * Resolve dynamic combo source from annotation or well-known field names / widget types.
   */
  private void applyComboSource(GuiFormField formField, HWidgetElements widget, Field field) {
    HComboSource source =
        widget.getComboSource() == null ? HComboSource.NONE : widget.getComboSource();
    String metadataKey = StringUtils.defaultString(widget.getMetadataKey());
    String dependsOn = StringUtils.defaultString(widget.getDependsOn());

    // Infer when not explicitly set
    if (source == HComboSource.NONE) {
      String name = field.getName();
      if ("sourceConnectorName".equals(name)) {
        source = HComboSource.CONNECTORS;
      } else if ("themeName".equals(name)) {
        source = HComboSource.THEMES;
      } else if ("databaseConnectionName".equals(name)
          || formField.getType() == GuiFormFieldType.METADATA) {
        source = HComboSource.METADATA;
        if (StringUtils.isEmpty(metadataKey)) {
          if ("databaseConnectionName".equals(name)) {
            metadataKey = "hopper-database-connection";
          } else if ("themeName".equals(name)) {
            metadataKey = "theme";
          }
        }
      }
    }

    formField.setComboSource(comboSourceName(source));
    if (StringUtils.isNotEmpty(dependsOn)) {
      formField.setComboDependsOn(dependsOn);
    } else if (source == HComboSource.CONNECTOR_COLUMNS) {
      formField.setComboDependsOn("sourceConnectorName");
    }
    if (StringUtils.isNotEmpty(metadataKey)) {
      formField.setMetadataKey(metadataKey);
    } else if (source == HComboSource.METADATA && StringUtils.isEmpty(formField.getMetadataKey())) {
      // keep empty — client may still list nothing useful
    }
  }

  private static String comboSourceName(HComboSource source) {
    if (source == null || source == HComboSource.NONE) {
      return "none";
    }
    return switch (source) {
      case CONNECTORS -> "connectors";
      case THEMES -> "themes";
      case COMPONENTS -> "components";
      case CONNECTOR_COLUMNS -> "connectorColumns";
      case METADATA -> "metadata";
      default -> "none";
    };
  }

  private Class<?> resolveListItemClass(Field field) {
    Type generic = field.getGenericType();
    if (generic instanceof ParameterizedType parameterized) {
      Type[] args = parameterized.getActualTypeArguments();
      if (args.length == 1 && args[0] instanceof Class<?> itemClass) {
        return itemClass;
      }
    }
    return null;
  }

  private String resolveItemKind(Class<?> itemClass) {
    if (HFact.class.isAssignableFrom(itemClass)) {
      return "fact";
    }
    if (HColumn.class.isAssignableFrom(itemClass)
        || HDimension.class.isAssignableFrom(itemClass)) {
      return "column";
    }
    if (HComponent.class.isAssignableFrom(itemClass)) {
      return "component";
    }
    if (HSortMethod.class.isAssignableFrom(itemClass)) {
      return "sort";
    }
    if (SimpleFilterValue.class.isAssignableFrom(itemClass)) {
      return "filter";
    }
    if (GroupKeyMapping.class.isAssignableFrom(itemClass)) {
      return "groupKey";
    }
    if (HRestConnector.JsonField.class.isAssignableFrom(itemClass)) {
      return "jsonField";
    }
    if (org.hopper.presentation.connector.types.csv.HCsvConnector.CsvField.class.isAssignableFrom(
        itemClass)) {
      return "csvField";
    }
    if (IHConnector.class.isAssignableFrom(itemClass)) {
      return "connector";
    }
    if (String.class.equals(itemClass)) {
      return "string";
    }
    return "bean";
  }

  private String bindingForParent(String parentId) {
    if (HGuiFormConstants.PARENT_WRAPPER.equals(parentId)
        || HGuiFormConstants.PARENT_LAYOUT.equals(parentId)
        || HGuiFormConstants.PARENT_COMPONENT_PROPS.equals(parentId)) {
      return "wrapper";
    }
    return "plugin";
  }

  private GuiFormFieldType mapType(HWidgetElements widget, Field field) {
    // Honor explicit action widgets before class-based inference (e.g. boolean would
    // otherwise become CHECKBOX).
    HWidgetType elementType = widget.getType();
    if (elementType == HWidgetType.BUTTON) {
      return GuiFormFieldType.BUTTON;
    }
    if (elementType == HWidgetType.LINK) {
      return GuiFormFieldType.LINK;
    }

    Class<?> type = field.getType();
    if (HComponent.class.isAssignableFrom(type)) {
      return GuiFormFieldType.COMPONENT;
    }
    if (List.class.isAssignableFrom(type)) {
      return GuiFormFieldType.LIST;
    }
    if (HColorRGB.class.isAssignableFrom(type)) {
      return GuiFormFieldType.COLOR;
    }
    if (HFont.class.isAssignableFrom(type)) {
      return GuiFormFieldType.FONT;
    }
    if (HSize.class.isAssignableFrom(type)) {
      return GuiFormFieldType.SIZE;
    }
    if (widget.isPassword()) {
      return GuiFormFieldType.PASSWORD;
    }
    if (type == boolean.class || type == Boolean.class) {
      return GuiFormFieldType.CHECKBOX;
    }
    if (type.isEnum()) {
      return GuiFormFieldType.COMBO;
    }
    if (elementType == null) {
      return GuiFormFieldType.TEXT;
    }
    return switch (elementType) {
      case TEXT -> GuiFormFieldType.TEXT;
      case MULTI_LINE_TEXT -> GuiFormFieldType.MULTI_LINE_TEXT;
      case FILENAME -> GuiFormFieldType.FILENAME;
      case FOLDER -> GuiFormFieldType.FOLDER;
      case CHECKBOX -> GuiFormFieldType.CHECKBOX;
      case COMBO -> GuiFormFieldType.COMBO;
      case METADATA -> GuiFormFieldType.METADATA;
      case BUTTON -> GuiFormFieldType.BUTTON;
      case LINK -> GuiFormFieldType.LINK;
      default -> GuiFormFieldType.TEXT;
    };
  }

  private List<String> resolveComboValues(
      HWidgetElements widget, Field field, Class<?> ownerClass) {
    Class<?> type = field.getType();
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      List<String> values = new ArrayList<>();
      for (Object c : constants) {
        values.add(((Enum<?>) c).name());
      }
      return values;
    }
    if (StringUtils.isNotEmpty(widget.getComboValuesMethod())) {
      try {
        Method method = ownerClass.getMethod(widget.getComboValuesMethod());
        Object instance = ownerClass.getDeclaredConstructor().newInstance();
        Object result = method.invoke(instance);
        if (result instanceof String[] array) {
          return Arrays.asList(array);
        }
        if (result instanceof List<?> list) {
          List<String> values = new ArrayList<>();
          for (Object o : list) {
            values.add(String.valueOf(o));
          }
          return values;
        }
      } catch (Exception e) {
        // leave empty; client may fill dynamically
      }
    }
    return new ArrayList<>();
  }

  private GuiFormSection buildWrapperSection() {
    GuiFormSection section =
        new GuiFormSection(HGuiFormConstants.SECTION_WRAPPER, "Component", true);
    GuiFormField name = new GuiFormField("componentName", GuiFormFieldType.TEXT, "Component name", "name");
    name.setBinding("wrapper");
    name.setOrder("00100");
    section.getFields().add(name);
    return section;
  }

  private List<GuiFormField> defaultBaseFields() {
    List<GuiFormField> fields = new ArrayList<>();
    // sourceConnectorName is promoted to the wrapper strip when editing a full component;
    // keep it here only as a fallback for nested / connector-less base lists.
    GuiFormField source =
        baseField("sourceConnectorName", GuiFormFieldType.COMBO, "Input connector", "01000");
    source.setComboSource("connectors");
    fields.add(source);
    // COLOR fields include their enable checkboxes (border / background / setDefaultColor)
    GuiFormField theme = baseField("themeName", GuiFormFieldType.COMBO, "Theme name", "01100");
    theme.setComboSource("themes");
    fields.add(theme);
    fields.add(baseField("borderColor", GuiFormFieldType.COLOR, "Border color", "01200"));
    fields.add(baseField("backGroundColor", GuiFormFieldType.COLOR, "Background color", "01300"));
    fields.add(baseField("defaultFont", GuiFormFieldType.FONT, "Default font", "01400"));
    fields.add(baseField("defaultColor", GuiFormFieldType.COLOR, "Default color", "01500"));
    return fields;
  }

  private GuiFormField baseField(String name, GuiFormFieldType type, String label, String order) {
    GuiFormField f = new GuiFormField(name, type, label, name);
    f.setBinding("plugin");
    f.setOrder(order);
    return f;
  }

  /**
   * Wrapper properties on {@link HComponent}: rotation (degrees), transparency (0–100), and
   * optional clip size (width × height).
   */
  private GuiFormSection buildComponentPropertiesSection() {
    GuiFormSection section =
        new GuiFormSection(
            HGuiFormConstants.SECTION_COMPONENT_PROPS, "Component properties", false);

    GuiFormField rotation =
        new GuiFormField("rotation", GuiFormFieldType.TEXT, "Rotation (degrees)", "rotation");
    rotation.setBinding("wrapper");
    rotation.setOrder("03010");
    rotation.setToolTip("Rotation angle in degrees around the component center");
    section.getFields().add(rotation);

    GuiFormField transparency =
        new GuiFormField(
            "transparency", GuiFormFieldType.TEXT, "Transparency (0-100)", "transparency");
    transparency.setBinding("wrapper");
    transparency.setOrder("03020");
    transparency.setToolTip("Opacity as a percentage: 0 = invisible, 100 = fully opaque");
    section.getFields().add(transparency);

    GuiFormField clipSize =
        new GuiFormField("clipSize", GuiFormFieldType.SIZE, "Clip size", "clipSize");
    clipSize.setBinding("wrapper");
    clipSize.setOrder("03030");
    clipSize.setToolTip("When width and height are set (>0), drawing is clipped to the component geometry");
    section.getFields().add(clipSize);

    return section;
  }

  private GuiFormSection buildLayoutSection() {
    GuiFormSection section =
        new GuiFormSection(HGuiFormConstants.SECTION_LAYOUT, "Layout options", false);
    for (String side : List.of("left", "right", "top", "bottom")) {
      GuiFormField field =
          new GuiFormField(side + "Layout", GuiFormFieldType.LAYOUT_SIDE, capitalize(side) + " alignment", side);
      field.setBinding("wrapper");
      field.setOrder("02000-" + side);
      section.getFields().add(field);
    }
    return section;
  }

  private GuiFormSection buildSectionFromFields(
      String id, String title, boolean open, List<GuiFormField> fields) {
    GuiFormSection section = new GuiFormSection(id, title, open);
    section.setFields(new ArrayList<>(fields));
    sortFields(section.getFields());
    return section;
  }

  private void sortFields(List<GuiFormField> fields) {
    fields.sort(
        Comparator.comparing(
                (GuiFormField f) -> StringUtils.defaultString(f.getOrder()),
                Comparator.naturalOrder())
            .thenComparing(f -> StringUtils.defaultString(f.getId())));
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * Build a form schema for a connector plugin id (e.g. {@code SqlConnector}).
   *
   * <p>No presentation wrapper/layout sections — connectors are edited as standalone metadata.
   * When the schema contains a nested connector list, attaches {@link
   * GuiFormSchema#getConnectorCatalog()}.
   */
  public GuiFormSchema buildConnectorSchema(String pluginId) throws HException {
    GuiFormSchema schema = buildConnectorSchemaInternal(pluginId, 0);
    if (schemaNeedsConnectorCatalog(schema)) {
      schema.setConnectorCatalog(buildConnectorCatalog(0));
    }
    return schema;
  }

  /**
   * Build connector plugin + base fields without attaching a catalog (used when building catalog
   * entries).
   *
   * @param nestedDepth depth of nested connector lists already expanded (0 = top-level form)
   */
  private GuiFormSchema buildConnectorSchemaInternal(String pluginId, int nestedDepth)
      throws HException {
    IPlugin plugin =
        PluginRegistry.getInstance().findPluginWithId(HConnectorPluginType.class, pluginId);
    if (plugin == null) {
      throw new HException("Connector plugin not found: " + pluginId);
    }

    Class<? extends IHConnector> connectorClass = loadConnectorClass(plugin, pluginId);

    HConnectorPlugin annotation = connectorClass.getAnnotation(HConnectorPlugin.class);
    String name = annotation != null ? annotation.name() : plugin.getName();
    String description = annotation != null ? annotation.description() : plugin.getDescription();

    GuiFormSchema schema = new GuiFormSchema(pluginId, name);
    schema.setPluginDescription(description);
    schema.setPluginClassName(connectorClass.getName());

    Map<String, List<GuiFormField>> byParent = collectAnnotatedFields(connectorClass);
    List<GuiFormField> pluginFields = new ArrayList<>();
    // Base fields first (e.g. source connector), then plugin-specific, then any other parents
    pluginFields.addAll(
        byParent.getOrDefault(HGuiFormConstants.PARENT_BASE, List.of()));
    pluginFields.addAll(
        byParent.getOrDefault(HGuiFormConstants.PARENT_PLUGIN, List.of()));
    for (Map.Entry<String, List<GuiFormField>> entry : byParent.entrySet()) {
      String parentId = entry.getKey();
      if (HGuiFormConstants.PARENT_PLUGIN.equals(parentId)
          || HGuiFormConstants.PARENT_BASE.equals(parentId)) {
        continue;
      }
      pluginFields.addAll(entry.getValue());
    }

    // At max depth, strip nested connector lists so the catalog stays finite
    if (nestedDepth >= MAX_NESTED_CONNECTOR_DEPTH) {
      pluginFields.removeIf(
          f -> f.getType() == GuiFormFieldType.LIST && "connector".equals(f.getItemKind()));
    }

    sortFields(pluginFields);
    if (!pluginFields.isEmpty()) {
      schema.setHasPluginWidgets(true);
      GuiFormSection section =
          buildSectionFromFields(
              HGuiFormConstants.SECTION_PLUGIN, name, true, pluginFields);
      schema.getSections().add(section);
    }
    return schema;
  }

  private Class<? extends IHConnector> loadConnectorClass(IPlugin plugin, String pluginId)
      throws HException {
    try {
      String className = plugin.getClassMap().get(IHConnector.class);
      if (className == null) {
        throw new HException("No main class mapping for connector plugin " + pluginId);
      }
      ClassLoader classLoader = PluginRegistry.getInstance().getClassLoader(plugin);
      @SuppressWarnings("unchecked")
      Class<? extends IHConnector> loaded =
          (Class<? extends IHConnector>) classLoader.loadClass(className);
      return loaded;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Unable to load class for connector plugin " + pluginId, e);
    }
  }

  private boolean schemaNeedsConnectorCatalog(GuiFormSchema schema) {
    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        if (field.getType() == GuiFormFieldType.LIST && "connector".equals(field.getItemKind())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Build a catalog of all registered connector plugins for nested chain / connector-list editors.
   *
   * @param nestedDepth depth used when building each type's field list (0 = top of catalog)
   */
  public List<GuiFormComponentTypeInfo> buildConnectorCatalog(int nestedDepth)
      throws HException {
    List<GuiFormComponentTypeInfo> catalog = new ArrayList<>();
    PluginRegistry registry = PluginRegistry.getInstance();
    List<IPlugin> plugins = registry.getPlugins(HConnectorPluginType.class);
    for (IPlugin plugin : plugins) {
      String id = plugin.getIds()[0];
      try {
        GuiFormSchema typeSchema = buildConnectorSchemaInternal(id, nestedDepth + 1);
        GuiFormComponentTypeInfo info =
            new GuiFormComponentTypeInfo(
                id, typeSchema.getPluginName(), typeSchema.getPluginDescription());
        info.setSections(typeSchema.getSections());
        catalog.add(info);
      } catch (Exception e) {
        // Skip plugins that cannot be schema-built
      }
    }
    catalog.sort(Comparator.comparing(GuiFormComponentTypeInfo::getPluginId));
    return catalog;
  }

  public boolean canBuildConnectorSchema(String pluginId) {
    try {
      return PluginRegistry.getInstance()
              .findPluginWithId(HConnectorPluginType.class, pluginId)
          != null;
    } catch (Exception e) {
      return false;
    }
  }

  /** Convenience for tests: enum names used by label alignment combos. */
  public static List<String> horizontalAlignments() {
    return Arrays.stream(HHorizontalAlignment.values()).map(Enum::name).toList();
  }

  public static List<String> verticalAlignments() {
    return Arrays.stream(HVerticalAlignment.values()).map(Enum::name).toList();
  }
}
