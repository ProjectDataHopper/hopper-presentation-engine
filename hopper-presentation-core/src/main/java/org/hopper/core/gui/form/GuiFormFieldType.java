package org.hopper.core.gui.form;

/**
 * UI-agnostic field types for generated plugin configuration forms.
 *
 * <p>Maps from Data Hopper {@code HWidgetType} plus Hopper-specific composites used by the browser editor.
 */
public enum GuiFormFieldType {
  TEXT,
  MULTI_LINE_TEXT,
  PASSWORD,
  CHECKBOX,
  COMBO,
  FILENAME,
  FOLDER,
  METADATA,
  /** HTML color input bound to {@code HColorRGB} JSON. */
  COLOR,
  /** Composite font editor bound to {@code HFont} JSON. */
  FONT,
  /** One of left/right/top/bottom layout attachment groups. */
  LAYOUT_SIDE,
  /**
   * Width/height pair bound to {@code HSize} JSON ({@code {width, height}}), e.g. clip size.
   */
  SIZE,
  /**
   * Editable list of beans (e.g. {@code List<HColumn>}, {@code List<HFact>}). Item shape is
   * described by {@link GuiFormField#getItemKind()}.
   */
  LIST,
  /**
   * Single nested {@code HComponent} (name + layout + typed plugin payload). Uses the schema
   * {@link GuiFormSchema#getComponentCatalog()} for type-specific fields.
   */
  COMPONENT,
  BUTTON,
  LINK,
  UNKNOWN
}
