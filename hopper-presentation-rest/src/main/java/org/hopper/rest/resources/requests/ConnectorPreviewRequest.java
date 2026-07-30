package org.hopper.rest.resources.requests;

/**
 * Request body for {@code POST edit/connector/preview/}: sample input/output rows from form state
 * (possibly unsaved).
 */
public class ConnectorPreviewRequest {

  /**
   * Full Hop-format connector JSON ({@code name}, {@code shared}, {@code connector.{PluginId}}),
   * same shape as {@code metadata/modify/connector}.
   */
  private String hopperConnectorJson;

  /** Optional sample size (default 20, hard max 100). */
  private Integer maxRows;

  /**
   * Optional active presentation render id so presentation-local connectors participate in the data
   * context and session parameter values (e.g. {@code SHIP_NAME}) seed filters.
   */
  private String renderId;

  /**
   * Optional presentation metadata name. Used to load parameter definition defaults when {@link
   * #renderId} is missing or expired.
   */
  private String presentationName;

  public ConnectorPreviewRequest() {}

  public String getHopperConnectorJson() {
    return hopperConnectorJson;
  }

  public void setHopperConnectorJson(String hopperConnectorJson) {
    this.hopperConnectorJson = hopperConnectorJson;
  }

  public Integer getMaxRows() {
    return maxRows;
  }

  public void setMaxRows(Integer maxRows) {
    this.maxRows = maxRows;
  }

  public String getRenderId() {
    return renderId;
  }

  public void setRenderId(String renderId) {
    this.renderId = renderId;
  }

  public String getPresentationName() {
    return presentationName;
  }

  public void setPresentationName(String presentationName) {
    this.presentationName = presentationName;
  }
}
