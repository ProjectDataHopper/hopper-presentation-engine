package org.hopper.presentation.connector.types.filter;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;

@Getter
@Setter
public class SimpleFilterValue {

  /**
   * Source field to match. Form list editors use connector column names from {@code
   * sourceConnectorName}; annotation documents the intended combo source for future nested schemas.
   */
  @HWidgetElement(
      order = "100-fieldName",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "Field name",
      toolTip = "Column from the source connector")
  @HopMetadataProperty
  private String fieldName;

  @HWidgetElement(
      order = "200-filterValue",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Filter value")
  @HopMetadataProperty
  private String filterValue;

  public SimpleFilterValue() {}

  public SimpleFilterValue(String fieldName, String filterValue) {
    this.fieldName = fieldName;
    this.filterValue = filterValue;
  }
}
