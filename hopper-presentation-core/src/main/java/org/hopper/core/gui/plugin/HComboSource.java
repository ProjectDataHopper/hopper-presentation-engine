package org.hopper.core.gui.plugin;

/**
 * Declares where a COMBO / METADATA field gets its options at edit time (project / presentation
 * context).
 */
public enum HComboSource {
  /** No dynamic source; use static {@link HWidgetElement#comboValuesMethod()} or enum names. */
  NONE,

  /** Shared connector metadata names (+ presentation-local connectors when available). */
  CONNECTORS,

  /** Theme metadata names. */
  THEMES,

  /** Component names on the current page (for layout attachments). */
  COMPONENTS,

  /**
   * Output column names of a connector. Pair with {@link HWidgetElement#dependsOn()} naming the
   * field that holds the connector name (default {@code sourceConnectorName}).
   */
  CONNECTOR_COLUMNS,

  /**
   * Names of elements for a Hop metadata type. Pair with {@link HWidgetElement#metadataKey()}
   * (e.g. {@code hopper-database-connection}).
   */
  METADATA
}
