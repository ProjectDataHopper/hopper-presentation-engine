package org.hopper.presentation.swt.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.util.TranslateUtil;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;

/** Discovers editable metadata fields from {@link HopMetadataProperty} / {@link HWidgetElement}. */
public final class HMetadataFields {

  private HMetadataFields() {}

  public record Spec(
      Field field,
      String label,
      String toolTip,
      String order,
      HWidgetType widgetType,
      boolean password,
      HComboSource comboSource,
      String metadataKey,
      String dependsOn,
      String comboValuesMethod,
      String tabName,
      int multiLineTextHeight) {

    public Class<?> type() {
      return field.getType();
    }

    public Class<?> listElementType() {
      Type generic = field.getGenericType();
      if (generic instanceof ParameterizedType parameterized
          && parameterized.getActualTypeArguments().length >= 1
          && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
        return element;
      }
      return Object.class;
    }

    public boolean isMap() {
      return Map.class.isAssignableFrom(type());
    }

    public boolean isList() {
      return List.class.isAssignableFrom(type());
    }
  }

  public static List<Spec> discover(Class<?> type) {
    List<Spec> specs = new ArrayList<>();
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (!include(field, type)) {
          continue;
        }
        field.setAccessible(true);
        specs.add(toSpec(field));
      }
    }
    specs.sort(
        Comparator.comparing(Spec::order, Comparator.nullsLast(String::compareTo))
            .thenComparing(spec -> spec.field().getName()));
    return specs;
  }

  static boolean include(Field field, Class<?> rootType) {
    int modifiers = field.getModifiers();
    if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
      return false;
    }
    if (field.getAnnotation(JsonIgnore.class) != null
        && field.getAnnotation(HopMetadataProperty.class) == null) {
      return false;
    }
    HWidgetElement widget = field.getAnnotation(HWidgetElement.class);
    if (widget != null && widget.ignored()) {
      return false;
    }
    if (field.getAnnotation(HopMetadataProperty.class) == null && widget == null) {
      return false;
    }
    // Top-level catalog types already have a name widget in MetadataEditor.
    // Nested beans that extend HopMetadataBase (HComponent) still need name.
    if ("name".equals(field.getName()) && rootType.getAnnotation(HopMetadata.class) != null) {
      return false;
    }
    if ("virtualPath".equals(field.getName())
        && HopMetadataBase.class.isAssignableFrom(rootType)) {
      return false;
    }
    if ("metadataProviderName".equals(field.getName())) {
      return false;
    }
    if ("pluginId".equals(field.getName())
        && (IHConnector.class.isAssignableFrom(rootType)
            || IHComponent.class.isAssignableFrom(rootType))) {
      return false;
    }
    return true;
  }

  static Spec toSpec(Field field) {
    HWidgetElement widget = field.getAnnotation(HWidgetElement.class);
    HopMetadataProperty property = field.getAnnotation(HopMetadataProperty.class);
    String rawLabel =
        widget != null && StringUtils.isNotBlank(widget.label())
            ? widget.label()
            : humanize(field.getName());
    String label = TranslateUtil.translate(rawLabel, field.getDeclaringClass());
    String toolTip =
        widget != null
            ? TranslateUtil.translate(widget.toolTip(), field.getDeclaringClass())
            : "";
    String order =
        widget != null && StringUtils.isNotBlank(widget.order())
            ? widget.order()
            : field.getName();
    HComboSource comboSource =
        widget != null && widget.comboSource() != HComboSource.NONE
            ? widget.comboSource()
            : inferComboSource(field);
    HWidgetType widgetType = widget != null ? widget.type() : inferType(field, comboSource);
    if (comboSource != HComboSource.NONE && widgetType == HWidgetType.TEXT) {
      widgetType = HWidgetType.COMBO;
    }
    boolean password =
        (widget != null && widget.password()) || (property != null && property.password());
    String metadataKey = widget != null ? widget.metadataKey() : "";
    String dependsOn = widget != null ? widget.dependsOn() : "";
    String comboValuesMethod = widget != null ? widget.comboValuesMethod() : "";
    String tabName = widget != null ? widget.tabName() : "";
    int multiLineTextHeight = widget != null ? Math.max(1, widget.multiLineTextHeight()) : 1;
    return new Spec(
        field,
        label,
        toolTip,
        order,
        widgetType,
        password,
        comboSource,
        metadataKey,
        dependsOn,
        comboValuesMethod,
        tabName,
        multiLineTextHeight);
  }

  static HComboSource inferComboSource(Field field) {
    if (field.getType() != String.class) {
      return HComboSource.NONE;
    }
    String name = field.getName();
    if (name.toLowerCase().endsWith("themename")) {
      return HComboSource.THEMES;
    }
    if (name.toLowerCase().endsWith("connectorname")) {
      return HComboSource.CONNECTORS;
    }
    return HComboSource.NONE;
  }

  static HWidgetType inferType(Field field, HComboSource comboSource) {
    Class<?> type = field.getType();
    if (type == boolean.class || type == Boolean.class) {
      return HWidgetType.CHECKBOX;
    }
    if (type.isEnum() || comboSource != HComboSource.NONE) {
      return HWidgetType.COMBO;
    }
    String name = field.getName();
    if ("layoutMode".equals(name) || "databaseTypeCode".equals(name)) {
      return HWidgetType.COMBO;
    }
    if (List.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
      return HWidgetType.NONE;
    }
    return HWidgetType.TEXT;
  }

  public static String humanize(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return "";
    }
    String spaced = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  public static Object get(Object bean, Field field) {
    try {
      field.setAccessible(true);
      return field.get(bean);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Unable to read field " + field.getName(), e);
    }
  }

  public static void set(Object bean, Field field, Object value) {
    try {
      field.setAccessible(true);
      field.set(bean, convert(field.getType(), value));
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Unable to write field " + field.getName(), e);
    }
  }

  public static Object convert(Class<?> type, Object value) {
    boolean blank = value == null || (value instanceof String string && string.isBlank());
    if (blank) {
      if (type == boolean.class) {
        return false;
      }
      if (type == int.class) {
        return 0;
      }
      if (type == long.class) {
        return 0L;
      }
      if (type == double.class) {
        return 0d;
      }
      if (type == float.class) {
        return 0f;
      }
      if (type == short.class) {
        return (short) 0;
      }
      if (type == byte.class) {
        return (byte) 0;
      }
      return null;
    }
    if (type == String.class) {
      return String.valueOf(value);
    }
    if (type == int.class || type == Integer.class) {
      if (value instanceof Number number) {
        return number.intValue();
      }
      return Integer.parseInt(String.valueOf(value).trim());
    }
    if (type == long.class || type == Long.class) {
      if (value instanceof Number number) {
        return number.longValue();
      }
      return Long.parseLong(String.valueOf(value).trim());
    }
    if (type == double.class || type == Double.class) {
      if (value instanceof Number number) {
        return number.doubleValue();
      }
      return Double.parseDouble(String.valueOf(value).trim());
    }
    if (type == float.class || type == Float.class) {
      if (value instanceof Number number) {
        return number.floatValue();
      }
      return Float.parseFloat(String.valueOf(value).trim());
    }
    if (type == boolean.class || type == Boolean.class) {
      if (value instanceof Boolean bool) {
        return bool;
      }
      return Boolean.parseBoolean(String.valueOf(value));
    }
    if (type.isEnum() && value instanceof String string) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Object enumerated = Enum.valueOf((Class) type, string);
      return enumerated;
    }
    if (type == Date.class) {
      if (value instanceof Date date) {
        return date;
      }
      try {
        return new Date(Long.parseLong(String.valueOf(value).trim()));
      } catch (NumberFormatException ignored) {
        // Fall through to string parse in callers; keep original if already Date-compatible.
      }
    }
    if (type.isInstance(value)) {
      return value;
    }
    return value;
  }

  public static boolean isPluginInterface(Class<?> type) {
    return type == IHConnector.class || type == IHComponent.class;
  }

  public static boolean isSimple(Class<?> type) {
    return type == String.class
        || type == boolean.class
        || type == Boolean.class
        || type == Date.class
        || Number.class.isAssignableFrom(type)
        || type.isPrimitive()
        || type.isEnum();
  }

  public static Object newInstance(Class<?> type) {
    if (type == HPage.class) {
      return HPage.getA4(true);
    }
    if (type == HLayout.class) {
      return HLayout.topLeftPage();
    }
    if (type == HColorRGB.class) {
      return new HColorRGB();
    }
    if (type == HFont.class) {
      return new HFont("Arial", "12", false, false);
    }
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create " + type.getName(), e);
    }
  }

  public static String labelOf(Object item, int index) {
    if (item == null) {
      return "[" + index + "]";
    }
    if (item instanceof IHopMetadata metadata && StringUtils.isNotBlank(metadata.getName())) {
      return metadata.getName();
    }
    if (item instanceof IHConnector connector && StringUtils.isNotBlank(connector.getPluginId())) {
      return connector.getPluginId();
    }
    if (item instanceof IHComponent component && StringUtils.isNotBlank(component.getPluginId())) {
      return component.getPluginId();
    }
    if (item instanceof HPage page) {
      int count = page.getComponents() == null ? 0 : page.getComponents().size();
      return page.getWidth() + "×" + page.getHeight() + " (" + count + ")";
    }
    if (item instanceof HColorRGB color) {
      return color.getHexColor();
    }
    if (item instanceof HFont font) {
      return (font.getFontName() == null ? "font" : font.getFontName())
          + " "
          + (font.getFontSize() == null ? "" : font.getFontSize());
    }
    try {
      java.lang.reflect.Method getter = item.getClass().getMethod("getName");
      Object name = getter.invoke(item);
      if (name != null && StringUtils.isNotBlank(String.valueOf(name))) {
        return String.valueOf(name);
      }
    } catch (Exception ignored) {
      // no name
    }
    String text = String.valueOf(item);
    if (text.startsWith(item.getClass().getName() + "@")) {
      return item.getClass().getSimpleName() + " " + (index + 1);
    }
    return text.length() > 80 ? text.substring(0, 77) + "…" : text;
  }
}
