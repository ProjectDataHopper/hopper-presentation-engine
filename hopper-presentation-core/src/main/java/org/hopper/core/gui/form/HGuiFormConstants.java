package org.hopper.core.gui.form;

/** Parent ids and section ids for Hopper component configuration forms. */
public final class HGuiFormConstants {

  /** Plugin-specific fields (label text, chart title, …). */
  public static final String PARENT_PLUGIN = "HComponent-Plugin";

  /** Shared fields from {@code HBaseComponent}. */
  public static final String PARENT_BASE = "HComponent-Base";

  /** Wrapper fields on {@code HComponent} (name, …). */
  public static final String PARENT_WRAPPER = "HComponent-Wrapper";

  /** Layout attachments on {@code HComponent.layout}. */
  public static final String PARENT_LAYOUT = "HComponent-Layout";

  /**
   * Wrapper transform/render properties on {@code HComponent} (rotation, transparency,
   * clip size).
   */
  public static final String PARENT_COMPONENT_PROPS = "HComponent-Props";

  public static final String SECTION_WRAPPER = "wrapper";
  public static final String SECTION_PLUGIN = "plugin";
  public static final String SECTION_BASE = "base";
  public static final String SECTION_COMPONENT_PROPS = "componentProps";
  public static final String SECTION_LAYOUT = "layout";

  private HGuiFormConstants() {}
}
