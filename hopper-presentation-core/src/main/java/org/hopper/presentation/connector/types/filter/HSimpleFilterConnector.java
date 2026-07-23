package org.hopper.presentation.connector.types.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Filters rows by exact string equality on field values.
 *
 * <p>When multiple {@link SimpleFilterValue}s are configured, a row must match <strong>all</strong>
 * of them (logical AND). For a single field, multiple allowed values act as OR within that field
 * (the row value must be in the set of filter values for that field).
 */
@JsonDeserialize(as = HSimpleFilterConnector.class)
@HConnectorPlugin(
    id = "SimpleFilterConnector",
    name = "Simple filter",
    description = "Keep rows matching exact field values (AND across fields, OR within a field)",
    image = "ui/images/connectors/filter.svg")
@Getter
@Setter
public class HSimpleFilterConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  @HWidgetElement(
      order = "10000-filterValues",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Filter values",
      toolTip = "Exact field/value pairs; AND across fields, OR within the same field")
  @HopMetadataProperty
  private List<SimpleFilterValue> filterValues;

  public HSimpleFilterConnector() {
    super("SimpleFilterConnector");
    filterValues = new ArrayList<>();
    finishedQueue = null;
  }

  public HSimpleFilterConnector(List<SimpleFilterValue> filterValues) {
    this();
    this.filterValues = filterValues;
  }

  public HSimpleFilterConnector(HSimpleFilterConnector c) {
    super(c);
    this.filterValues = new ArrayList<>();
    for (SimpleFilterValue value : c.filterValues) {
      this.filterValues.add(new SimpleFilterValue(value.getFieldName(), value.getFilterValue()));
    }
  }

  public HSimpleFilterConnector clone() {
    return new HSimpleFilterConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for simple filter connector");
    }
    return connector.getConnector().describeOutput(dataContext);
  }

  @Override
  public void startStreaming(final IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for simple filter connector");
    }

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    final IRowMeta inputRowMeta = connector.describeOutput(dataContext);
    final IVariables variables = dataContext.getVariables();

    Map<String, Set<String>> fieldFiltersMap = new HashMap<>();

    int[] valueIndexes = new int[filterValues.size()];
    for (int i = 0; i < valueIndexes.length; i++) {
      SimpleFilterValue filterValue = filterValues.get(i);
      valueIndexes[i] = inputRowMeta.indexOfValue(filterValue.getFieldName());
      if (valueIndexes[i] < 0) {
        throw new HException(
            "Unable to find filter field '"
                + filterValue.getFieldName()
                + "' in input of connector '"
                + getSourceConnectorName());
      }
      IValueMeta valueMeta = inputRowMeta.getValueMeta(valueIndexes[i]);
      String valueName = valueMeta.getName();

      Set<String> values = fieldFiltersMap.computeIfAbsent(valueName, e -> new HashSet<>());
      values.add(variables.resolve(filterValue.getFilterValue()));
    }

    IHRowListener listener =
        (rowMeta, rowData) -> {
          if (rowData == null) {
            outputDone();
            finishedQueue.add(new Object());
            return;
          }

          boolean pass = true;
          for (int i = 0; i < valueIndexes.length; i++) {
            int valueIndex = valueIndexes[i];
            IValueMeta valueMeta = inputRowMeta.getValueMeta(valueIndex);
            Set<String> allowed =
                fieldFiltersMap.computeIfAbsent(valueMeta.getName(), e -> new HashSet<>());

            try {
              String rowValue = valueMeta.getString(rowData[valueIndex]);
              if (!allowed.contains(rowValue)) {
                pass = false;
                break;
              }
            } catch (HopException e) {
              throw new HException(
                  "Unable to convert simple filter input row value '" + valueMeta, e);
            }
          }

          if (pass) {
            passToRowListeners(rowMeta, rowData);
          }
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
