package org.hopper.core.gui.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A single field in a generated configuration form. */
@Getter
@Setter
@NoArgsConstructor
public class GuiFormField {
  /** Stable id (DOM id / schema id), usually the Java field name. */
  private String id;

  /** Sort key (from {@code @HWidgetElement#order()}). */
  private String order;

  private GuiFormFieldType type;
  private String label;
  private String toolTip;

  /**
   * Optional editor tab title ({@code @HWidgetElement#tabName()}). Empty means the default tab.
   */
  private String tabName;

  /** Tooltip for {@link #tabName} when set. */
  private String tabTooltip;

  /** Java field name on the plugin / base / wrapper object. */
  private String fieldName;

  /**
   * Where the value lives in the hopper-presentation-rest component JSON.
   *
   * <ul>
   *   <li>{@code plugin} — under {@code componentJson.component[pluginId]}
   *   <li>{@code wrapper} — on {@code componentJson} itself (name, layout, …)
   * </ul>
   */
  private String binding = "plugin";

  private boolean password;
  private boolean variablesEnabled = true;
  private int multiLineTextHeight = 1;

  /** Combo options (enums or comboValuesMethod results). */
  private List<String> comboValues = new ArrayList<>();

  /**
   * Dynamic option source for COMBO fields at edit time. Values: {@code none}, {@code connectors},
   * {@code themes}, {@code components}, {@code connectorColumns}, {@code metadata}.
   */
  private String comboSource = "none";

  /**
   * For {@code connectorColumns}: DOM/field id of the connector name field (default {@code
   * sourceConnectorName}).
   */
  private String comboDependsOn;

  /** For {@link GuiFormFieldType#METADATA} / comboSource {@code metadata}: Hop metadata key. */
  private String metadataKey;

  /**
   * For {@link GuiFormFieldType#LIST}: kind of list item for HTML/JS helpers.
   *
   * <ul>
   *   <li>{@code column} — {@code HColumn} / {@code HDimension}
   *   <li>{@code fact} — {@code HFact}
   *   <li>{@code string} — {@code List<String>}
   *   <li>{@code component} — {@code HComponent}
   * </ul>
   */
  private String itemKind;

  /** Fully qualified item class name for list fields (diagnostics / future nested schema). */
  private String itemClassName;

  /** When true, load/save as integer (text box). */
  private boolean integerValue;

  public GuiFormField(String id, GuiFormFieldType type, String label, String fieldName) {
    this.id = id;
    this.type = type;
    this.label = label;
    this.fieldName = fieldName;
  }
}
