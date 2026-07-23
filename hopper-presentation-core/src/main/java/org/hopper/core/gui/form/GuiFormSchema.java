package org.hopper.core.gui.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * UI-agnostic form schema for editing a Hopper component (or connector) plugin in the browser.
 *
 * <p>Produced from {@code @HWidgetElement} annotations plus shared wrapper/base sections.
 */
@Getter
@Setter
@NoArgsConstructor
public class GuiFormSchema {
  private String pluginId;
  private String pluginName;
  private String pluginDescription;
  private String pluginClassName;

  /** True when the schema was built from annotations (not only shared chrome). */
  private boolean hasPluginWidgets;

  private List<GuiFormSection> sections = new ArrayList<>();

  /**
   * Available component plugin types for nested COMPONENT / component-list editors. Populated when
   * the schema contains nested component fields.
   */
  private List<GuiFormComponentTypeInfo> componentCatalog = new ArrayList<>();

  /**
   * Available connector plugin types for nested chain / connector-list editors. Populated when the
   * schema contains a {@code LIST} field with {@code itemKind=connector}. Same shape as the
   * component catalog (plugin id, name, description, field sections).
   */
  private List<GuiFormComponentTypeInfo> connectorCatalog = new ArrayList<>();

  public GuiFormSchema(String pluginId, String pluginName) {
    this.pluginId = pluginId;
    this.pluginName = pluginName;
  }
}
