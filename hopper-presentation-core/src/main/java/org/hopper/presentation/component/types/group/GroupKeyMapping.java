package org.hopper.presentation.component.types.group;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;

/**
 * Maps a group-key column (outer) to a column on a nested component's connector (inner) for
 * automatic equality filtering in {@link org.hopper.presentation.datacontext.GroupDataContext}.
 *
 * <p>When a Group component lists key mappings, nested connectors are filtered with {@code
 * connectorColumn = groupRow[groupColumn]} for each mapping whose columns exist. When the list is
 * empty, matching by equal column names is used (legacy behaviour).
 */
@Getter
@Setter
@NoArgsConstructor
public class GroupKeyMapping {

  @HWidgetElement(
      order = "100-groupColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Group column",
      toolTip = "Column on the group key row (typically one of the group columns)")
  @HopMetadataProperty
  private String groupColumn;

  @HWidgetElement(
      order = "200-connectorColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Connector column",
      toolTip = "Column on nested connectors to filter (equality with the group value)")
  @HopMetadataProperty
  private String connectorColumn;

  public GroupKeyMapping(String groupColumn, String connectorColumn) {
    this.groupColumn = groupColumn;
    this.connectorColumn = connectorColumn;
  }

  public GroupKeyMapping(GroupKeyMapping m) {
    this.groupColumn = m.groupColumn;
    this.connectorColumn = m.connectorColumn;
  }
}
