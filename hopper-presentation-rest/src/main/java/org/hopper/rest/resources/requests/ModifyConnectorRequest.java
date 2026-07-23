package org.hopper.rest.resources.requests;

/** Request body for saving a connector metadata element after form edit. */
public class ModifyConnectorRequest {
  /** Previous name (for renames / delete-old). */
  private String oldConnectorName;

  /** Full HConnector JSON (name, shared, connector.{pluginId}). */
  private String hopperConnectorJson;

  public ModifyConnectorRequest() {}

  public String getOldConnectorName() {
    return oldConnectorName;
  }

  public void setOldConnectorName(String oldConnectorName) {
    this.oldConnectorName = oldConnectorName;
  }

  public String getHopperConnectorJson() {
    return hopperConnectorJson;
  }

  public void setHopperConnectorJson(String hopperConnectorJson) {
    this.hopperConnectorJson = hopperConnectorJson;
  }
}
