package org.hopper.presentation.connector.types.sort;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.HColumn;
import org.hopper.core.HSortMethod;
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

/** Sort rows from a source connector using a selection of columns */
@JsonDeserialize(as = HSortConnector.class)
@HConnectorPlugin(
    id = "SortConnector",
    name = "Sort rows",
    description = "Sorts all rows",
    image = "ui/images/connectors/sort.svg")
@Getter
@Setter
public class HSortConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  @HWidgetElement(
      order = "10000-columns",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Sort columns",
      toolTip = "Columns used as sort keys (list editor; annotation type ignored for List fields)")
  @HopMetadataProperty
  private List<HColumn> columns;

  @HWidgetElement(
      order = "10100-sortMethods",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Sort methods",
      toolTip = "One method per sort column (same list size; List fields always render as LIST)")
  @HopMetadataProperty
  private List<HSortMethod> sortMethods;

  public HSortConnector() {
    super("SortConnector");
    finishedQueue = null;
    columns = new ArrayList<>();
    sortMethods = new ArrayList<>();
  }

  public HSortConnector(List<HColumn> columns, List<HSortMethod> sortMethods) {
    this();
    this.columns = columns;
    this.sortMethods = sortMethods;
  }

  public HSortConnector(HSortConnector c) {
    super(c);
    columns = new ArrayList<>();
    for (HColumn column : c.columns) {
      this.columns.add(new HColumn(column));
    }
    sortMethods = new ArrayList<>();
    for (HSortMethod method : c.sortMethods) {
      this.sortMethods.add(new HSortMethod(method));
    }
  }

  public HSortConnector clone() {
    return new HSortConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find source '" + getSourceConnectorName() + "' for sort connector");
    }
    return connector.getConnector().describeOutput(dataContext);
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find source '" + getSourceConnectorName() + "' for sort connector");
    }

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    final IRowMeta inputRowMeta = connector.describeOutput(dataContext);
    final IRowMeta outputRowMeta = inputRowMeta.clone();

    final List<Object[]> rows = new ArrayList<>();
    final int[] fieldIndexes = new int[columns.size()];

    for (int i = 0; i < fieldIndexes.length; i++) {
      HColumn column = columns.get(i);
      HSortMethod sortMethod = sortMethods.get(i);
      fieldIndexes[i] = inputRowMeta.indexOfValue(column.getColumnName());
      if (fieldIndexes[i] < 0) {
        throw new HException(
            "Sort column '" + column.getColumnName() + "' could not be found in the input");
      }

      IValueMeta valueMeta = outputRowMeta.getValueMeta(fieldIndexes[i]);
      valueMeta.setSortedDescending(!sortMethod.isAscending());
      switch (sortMethod.getType()) {
        case NATIVE_VALUE:
          break;
        case STRING_ALPHA_CASE_INSENSITIVE:
          valueMeta.setCaseInsensitive(true);
          break;
        case STRING_ALPHA:
          valueMeta.setCaseInsensitive(false);
          break;
        default:
          throw new HException(
              "Sort method " + sortMethod.getType().name() + " is not yet implemented");
      }
    }

    IHRowListener listener =
        (rowMeta, rowData) -> {
          if (rowData == null) {
            try {
              Collections.sort(
                  rows,
                  (o1, o2) -> {
                    try {
                      return outputRowMeta.compare(o1, o2, fieldIndexes);
                    } catch (HopValueException e) {
                      throw new RuntimeException("Error comparing rows", e);
                    }
                  });
            } catch (Exception e) {
              throw new HException("Error sorting rows", e);
            }

            for (Object[] row : rows) {
              passToRowListeners(outputRowMeta, row);
            }
            rows.clear();
            outputDone();
            finishedQueue.add(new Object());
            return;
          }

          rows.add(rowData);
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
