package org.hopper.core.gui.plugin;

/**
 * Declared widget type on {@link HWidgetElement}.
 *
 * <p>Hopper-specific composites (color, font, nested component, bean lists) are still inferred from
 * the field's Java type in {@code GuiFormSchemaBuilder}; this enum covers the explicit annotation
 * cases.
 */
public enum HWidgetType {
  NONE,
  TEXT,
  MULTI_LINE_TEXT,
  FILENAME,
  FOLDER,
  COMBO,
  CHECKBOX,
  METADATA,
  BUTTON,
  LINK
}
