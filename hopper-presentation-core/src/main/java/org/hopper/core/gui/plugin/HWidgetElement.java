package org.hopper.core.gui.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a configuration form widget for a Hopper component or connector field.
 *
 * <p>Scanned by {@link HGuiRegistry} and exported as form schema by {@code
 * GuiFormSchemaBuilder}. Used next to {@code @HopMetadataProperty} on plugin fields.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface HWidgetElement {

  /**
   * Unique widget id (defaults to the Java field name when empty).
   */
  String id() default "";

  /** Declared widget kind; domain types may still be inferred from the field class. */
  HWidgetType type();

  /** Field label shown in the editor. */
  String label() default "";

  /** Optional tooltip for the widget and label. */
  String toolTip() default "";

  /**
   * Optional tab title for grouping widgets onto separate editor tabs. Empty means the default /
   * primary tab for the section.
   */
  String tabName() default "";

  /**
   * Tooltip for the tab when {@link #tabName()} is set.
   */
  String tabTooltip() default "";

  /**
   * Parent form group id (see {@code HGuiFormConstants}). Empty defaults to the plugin section.
   */
  String parentId() default "";

  /**
   * Sort key; fields are ordered alphabetically by this value.
   */
  String order() default "";

  /** When true, render as a masked password field. */
  boolean password() default false;

  /** When true, the field supports Hop variable substitution. */
  boolean variables() default true;

  /** Non-standard setter method name, if any. */
  String setterMethod() default "";

  /** Non-standard getter method name, if any. */
  String getterMethod() default "";

  /** Method returning {@code String[]} or {@code List} of combo options. */
  String comboValuesMethod() default "";

  /**
   * Dynamic option source for COMBO fields (connectors, themes, columns, …). When {@link
   * HComboSource#NONE}, options come from enums or {@link #comboValuesMethod()}.
   */
  HComboSource comboSource() default HComboSource.NONE;

  /**
   * For {@link HComboSource#CONNECTOR_COLUMNS}: id of the field that holds the connector name
   * (default {@code sourceConnectorName} when empty).
   */
  String dependsOn() default "";

  /**
   * For {@link HComboSource#METADATA} or {@link HWidgetType#METADATA}: Hop metadata key
   * (e.g. {@code hopper-database-connection}, {@code theme}).
   */
  String metadataKey() default "";

  /**
   * When true, this field is ignored as a GUI element (can override a base-class widget).
   */
  boolean ignored() default false;

  /** When true, a visual separator precedes this element. */
  boolean separator() default false;

  /**
   * Preferred height for {@link HWidgetType#MULTI_LINE_TEXT}, in text lines (at least 1).
   */
  int multiLineTextHeight() default 1;
}
