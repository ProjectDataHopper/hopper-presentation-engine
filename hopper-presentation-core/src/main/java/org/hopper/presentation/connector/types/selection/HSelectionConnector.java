package org.hopper.presentation.connector.types.selection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.HColumn;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;
import lombok.Getter;
import lombok.Setter;

/** Select a bunch of columns from a source connector */
@JsonDeserialize(as = HSelectionConnector.class)
@HConnectorPlugin(
    id = "SelectionConnector",
    name = "Select fields",
    description = "Makes a selection of fields from a source connector",
    image = "ui/images/connectors/select.svg")
@Getter
@Setter
public class HSelectionConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  @HWidgetElement(
      order = "10000-columns",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Selected columns")
  @HopMetadataProperty
  private List<HColumn> columns;

  public HSelectionConnector() {
    super("SelectionConnector");
    finishedQueue = null;
    columns = new ArrayList<>();
  }

  public HSelectionConnector(HSelectionConnector c) {
    super(c);
    this.columns = new ArrayList<>();
    for (HColumn column : c.columns) {
      this.columns.add(new HColumn(column));
    }
  }

  public HSelectionConnector(List<HColumn> columns) {
    this();
    this.columns = columns;
  }

  public HSelectionConnector clone() {
    return new HSelectionConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for selection connector");
    }
    IRowMeta sourceRowMeta = connector.getConnector().describeOutput(dataContext);

    IRowMeta rowMeta = new RowMeta();
    for (HColumn column : columns) {
      IValueMeta sourceValueMeta = sourceRowMeta.searchValueMeta(column.getColumnName());
      if (sourceValueMeta == null) {
        throw new HException(
            "Unable to find column selection '"
                + column.getColumnName()
                + "' in source connector : "
                + getSourceConnectorName()
                + " : "
                + rowMeta.toString());
      }
      IValueMeta valueMeta = sourceValueMeta.clone();

      if (StringUtils.isNotEmpty(column.getFormatMask())) {
        valueMeta.setConversionMask(column.getFormatMask());
      }
      valueMeta.setOrigin(getSourceConnectorName());
      valueMeta.setComments(column.getHeaderValue());
      rowMeta.addValueMeta(valueMeta);
    }

    return rowMeta;
  }

  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for selection connector");
    }

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    final IRowMeta inputRowMeta = connector.describeOutput(dataContext);
    final IRowMeta outputRowMeta = describeOutput(dataContext);

    final int[] columnIndexes = new int[columns.size()];
    for (int i = 0; i < columnIndexes.length; i++) {
      columnIndexes[i] = inputRowMeta.indexOfValue(columns.get(i).getColumnName());
    }

    IHRowListener listener =
        (rowMeta, rowData) -> {
          if (rowData == null) {
            outputDone();
            finishedQueue.add(new Object());
            return;
          }

          Object[] outputRowData = RowDataUtil.allocateRowData(outputRowMeta.size());
          for (int i = 0; i < outputRowMeta.size(); i++) {
            outputRowData[i] = rowData[columnIndexes[i]];
          }

          passToRowListeners(outputRowMeta, outputRowData);
        };

    IHConnector source = connector.getConnector();
    attachToSource(source, listener);
    source.startStreaming(dataContext);
  }

  @Override
  public void waitUntilFinished() throws HException {
    try {
      while (finishedQueue != null && finishedQueue.poll(1, TimeUnit.DAYS) == null) {
        // wait for end-of-stream signal
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HException("Interrupted while waiting for more rows in connector", e);
    } finally {
      detachFromSource();
      finishedQueue = null;
    }
  }
}
