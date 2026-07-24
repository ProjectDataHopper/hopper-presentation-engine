package org.hopper.presentation.connector.types.list;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMetaBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** A simple wrapper around a java.util.List of Strings. */
@JsonDeserialize(as = HListConnector.class)
@HConnectorPlugin(
    id = "HListConnector",
    name = "List",
    description = "A simple connector for embedded usage",
    image = "ui/images/connectors/list.svg")
@Getter
@Setter
public class HListConnector extends HBaseConnector implements IHConnector {

  @HWidgetElement(
      order = "10000-columnName",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Column name")
  @HopMetadataProperty
  private String columnName;

  @HWidgetElement(
      order = "10100-list",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Values")
  @HopMetadataProperty
  private List<String> list;

  public HListConnector() {
    super("HListConnector");
    this.columnName = "value";
    this.list = new ArrayList<>();
  }

  public HListConnector(String columnName, List<String> list) {
    this();
    this.columnName = columnName;
    this.list = list;
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    return new RowMetaBuilder().addString(columnName).build();
  }

  @Override
  public HBaseConnector clone() {
    return new HListConnector(this.columnName, new ArrayList<>(this.list));
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    IRowMeta rowMeta = describeOutput(dataContext);

    for (String value : list) {
      Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
      rowData[0] = value;

      passToRowListeners(rowMeta, rowData);
    }
    outputDone();
  }

  @Override
  public void waitUntilFinished() throws HException {
    // Nothing to do here, everything was done in startStreaming()
  }
}
