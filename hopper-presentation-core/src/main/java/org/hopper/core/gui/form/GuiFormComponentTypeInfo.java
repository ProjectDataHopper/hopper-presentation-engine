package org.hopper.core.gui.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catalog entry for a component plugin type, used by nested component editors to switch type and
 * render the matching fields.
 */
@Getter
@Setter
@NoArgsConstructor
public class GuiFormComponentTypeInfo {
  private String pluginId;
  private String name;
  private String description;

  /**
   * Sections for this plugin type only (plugin + base). Nested COMPONENT/LIST-component fields are
   * included so the client can recurse; catalog schemas are built with a depth limit.
   */
  private List<GuiFormSection> sections = new ArrayList<>();

  public GuiFormComponentTypeInfo(String pluginId, String name, String description) {
    this.pluginId = pluginId;
    this.name = name;
    this.description = description;
  }
}
