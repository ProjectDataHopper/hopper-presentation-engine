package org.hopper.presentation.connector.types.aggregate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HColumn;
import org.hopper.core.HFact;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Transform connector: group rows from a source by one or more columns and compute aggregates
 * (SUM / COUNT / AVERAGE) on fact columns — same aggregation model as charts/crosstabs.
 *
 * <p>All rows are buffered in memory. Optionally sort the aggregated output on the group fields.
 */
@JsonDeserialize(as = HAggregateConnector.class)
@HConnectorPlugin(
    id = "AggregateConnector",
    name = "Aggregate",
    description =
        "Groups rows by columns and aggregates fact columns (sum, count, average); optional sort",
    image = "ui/images/connectors/aggregate.svg")
@Getter
@Setter
public class HAggregateConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  @HWidgetElement(
      order = "10000-groupColumns",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Group columns",
      toolTip = "Columns that define the aggregation groups (like dimensions on a chart)")
  @HopMetadataProperty
  private List<HColumn> groupColumns;

  @HWidgetElement(
      order = "10100-aggregates",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Aggregate columns",
      toolTip =
          "Fact columns with aggregation method (SUM, COUNT, AVERAGE) — same idea as chart facts")
  @HopMetadataProperty
  private List<HFact> aggregates;

  @HWidgetElement(
      order = "10200-sortByGroupFields",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Sort by group fields",
      toolTip = "When checked, sort the aggregated rows in memory by the group column values")
  @HopMetadataProperty
  private boolean sortByGroupFields = true;

  public HAggregateConnector() {
    super("AggregateConnector");
    finishedQueue = null;
    groupColumns = new ArrayList<>();
    aggregates = new ArrayList<>();
  }

  public HAggregateConnector(HAggregateConnector c) {
    super(c);
    this.sortByGroupFields = c.sortByGroupFields;
    this.groupColumns = new ArrayList<>();
    if (c.groupColumns != null) {
      for (HColumn col : c.groupColumns) {
        this.groupColumns.add(new HColumn(col));
      }
    }
    this.aggregates = new ArrayList<>();
    if (c.aggregates != null) {
      for (HFact fact : c.aggregates) {
        this.aggregates.add(new HFact(fact));
      }
    }
  }

  @Override
  public HAggregateConnector clone() {
    return new HAggregateConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnector source = requireSource(dataContext);
    IRowMeta input = source.getConnector().describeOutput(dataContext);
    return buildOutputRowMeta(input);
  }

  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    HConnector sourceWrapper = requireSource(dataContext);
    IHConnector source = sourceWrapper.getConnector();

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    if (groupColumns == null || groupColumns.isEmpty()) {
      throw new HException("Aggregate connector needs at least one group column");
    }
    if (aggregates == null || aggregates.isEmpty()) {
      throw new HException("Aggregate connector needs at least one aggregate column");
    }

    final IRowMeta inputRowMeta = source.describeOutput(dataContext);
    final IRowMeta outputRowMeta = buildOutputRowMeta(inputRowMeta);

    final int[] groupIndexes = resolveGroupIndexes(inputRowMeta);
    final int[] factIndexes = resolveFactIndexes(inputRowMeta);

    // Preserve first-seen group order (LinkedHashMap) unless sorted at the end
    final Map<List<Object>, AggregateBucket> buckets = new LinkedHashMap<>();

    IHRowListener listener =
        (rowMeta, rowData) -> {
          if (rowData == null) {
            List<Object[]> outRows = new ArrayList<>(buckets.size());
            for (Map.Entry<List<Object>, AggregateBucket> entry : buckets.entrySet()) {
              outRows.add(entry.getValue().toRow(entry.getKey(), outputRowMeta, aggregates));
            }
            if (sortByGroupFields && !outRows.isEmpty()) {
              final int[] sortIndexes = new int[groupIndexes.length];
              for (int i = 0; i < sortIndexes.length; i++) {
                sortIndexes[i] = i; // group fields are leading columns in output
              }
              Collections.sort(
                  outRows,
                  (a, b) -> {
                    try {
                      return outputRowMeta.compare(a, b, sortIndexes);
                    } catch (HopValueException e) {
                      throw new RuntimeException("Error sorting aggregated rows", e);
                    }
                  });
            }
            for (Object[] row : outRows) {
              passToRowListeners(outputRowMeta, row);
            }
            buckets.clear();
            outputDone();
            finishedQueue.add(new Object());
            return;
          }

          try {
            List<Object> key = new ArrayList<>(groupIndexes.length);
            for (int idx : groupIndexes) {
              // Keep typed values for stable compare/sort (not string conversion)
              key.add(rowData[idx]);
            }

            AggregateBucket bucket = buckets.get(key);
            if (bucket == null) {
              bucket = new AggregateBucket(aggregates.size());
              buckets.put(key, bucket);
            }
            bucket.accumulate(rowMeta, rowData, factIndexes, aggregates);
          } catch (Exception e) {
            throw new HException("Error aggregating row", e);
          }
        };

    attachToSource(source, listener);
    source.startStreaming(dataContext);
  }

  @Override
  public void waitUntilFinished() throws HException {
    try {
      while (finishedQueue != null && finishedQueue.poll(1, TimeUnit.DAYS) == null) {
        // wait for end-of-stream
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HException("Interrupted while waiting for aggregate connector", e);
    } finally {
      detachFromSource();
      finishedQueue = null;
    }
  }

  private HConnector requireSource(IDataContext dataContext) throws HException {
    if (StringUtils.isBlank(getSourceConnectorName())) {
      throw new HException("Aggregate connector needs a source connector");
    }
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find source '" + getSourceConnectorName() + "' for aggregate connector");
    }
    return connector;
  }

  private int[] resolveGroupIndexes(IRowMeta input) throws HException {
    int[] indexes = new int[groupColumns.size()];
    for (int i = 0; i < groupColumns.size(); i++) {
      String name = groupColumns.get(i).getColumnName();
      int idx = input.indexOfValue(name);
      if (idx < 0) {
        throw new HException("Group column '" + name + "' not found in source");
      }
      indexes[i] = idx;
    }
    return indexes;
  }

  private int[] resolveFactIndexes(IRowMeta input) throws HException {
    int[] indexes = new int[aggregates.size()];
    for (int i = 0; i < aggregates.size(); i++) {
      HFact fact = aggregates.get(i);
      if (fact.getAggregationMethod() == null) {
        throw new HException(
            "No aggregation method for fact column '" + fact.getColumnName() + "'");
      }
      String name = fact.getColumnName();
      int idx = input.indexOfValue(name);
      if (idx < 0) {
        throw new HException("Aggregate column '" + name + "' not found in source");
      }
      indexes[i] = idx;
    }
    return indexes;
  }

  IRowMeta buildOutputRowMeta(IRowMeta input) throws HException {
    try {
      IRowMeta output = new RowMeta();
      for (HColumn group : groupColumns) {
        int idx = input.indexOfValue(group.getColumnName());
        if (idx < 0) {
          throw new HException("Group column '" + group.getColumnName() + "' not found in source");
        }
        IValueMeta vm = input.getValueMeta(idx).clone();
        if (StringUtils.isNotEmpty(group.getHeaderValue())) {
          vm.setName(group.getHeaderValue());
        }
        if (StringUtils.isNotEmpty(group.getFormatMask())) {
          vm.setConversionMask(group.getFormatMask());
        }
        output.addValueMeta(vm);
      }
      for (HFact fact : aggregates) {
        output.addValueMeta(factOutputValueMeta(input, fact));
      }
      return output;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Unable to describe aggregate output", e);
    }
  }

  private static IValueMeta factOutputValueMeta(IRowMeta input, HFact fact) throws Exception {
    String outName =
        StringUtils.isNotEmpty(fact.getHeaderValue())
            ? fact.getHeaderValue()
            : fact.getColumnName();
    AggregationMethod method =
        fact.getAggregationMethod() != null ? fact.getAggregationMethod() : AggregationMethod.SUM;

    if (method == AggregationMethod.COUNT) {
      ValueMetaInteger countMeta = new ValueMetaInteger(outName);
      if (StringUtils.isNotEmpty(fact.getFormatMask())) {
        countMeta.setConversionMask(fact.getFormatMask());
      }
      return countMeta;
    }

    int idx = input.indexOfValue(fact.getColumnName());
    if (idx < 0) {
      throw new HException("Aggregate column '" + fact.getColumnName() + "' not found in source");
    }
    IValueMeta source = input.getValueMeta(idx);
    IValueMeta out;
    if (method == AggregationMethod.AVERAGE) {
      // Average is always a floating-point result
      out = new ValueMetaNumber(outName);
    } else {
      out = source.clone();
      out.setName(outName);
    }
    if (StringUtils.isNotEmpty(fact.getFormatMask())) {
      out.setConversionMask(fact.getFormatMask());
    }
    return out;
  }

  /** Per-group accumulators for each fact. */
  static final class AggregateBucket {
    private final Object[] sums;
    private final long[] counts;

    AggregateBucket(int factCount) {
      this.sums = new Object[factCount];
      this.counts = new long[factCount];
    }

    void accumulate(IRowMeta rowMeta, Object[] rowData, int[] factIndexes, List<HFact> facts)
        throws Exception {
      for (int i = 0; i < factIndexes.length; i++) {
        HFact fact = facts.get(i);
        AggregationMethod method = fact.getAggregationMethod();
        if (method == null) {
          method = AggregationMethod.SUM;
        }
        int idx = factIndexes[i];
        IValueMeta valueMeta = rowMeta.getValueMeta(idx);
        Object valueData = rowData[idx];

        if (valueMeta.isNull(valueData)) {
          continue;
        }

        counts[i]++;

        if (method == AggregationMethod.COUNT) {
          continue;
        }

        switch (valueMeta.getType()) {
          case IValueMeta.TYPE_NUMBER -> {
            Double numberValue = valueMeta.getNumber(valueData);
            Double previous = (Double) sums[i];
            sums[i] = previous == null ? numberValue : previous + numberValue;
          }
          case IValueMeta.TYPE_INTEGER -> {
            Long integerValue = valueMeta.getInteger(valueData);
            if (method == AggregationMethod.AVERAGE) {
              // Keep double sum for average
              Double previous = (Double) sums[i];
              double v = integerValue.doubleValue();
              sums[i] = previous == null ? v : previous + v;
            } else {
              Long previous = (Long) sums[i];
              sums[i] = previous == null ? integerValue : previous + integerValue;
            }
          }
          case IValueMeta.TYPE_BIGNUMBER -> {
            BigDecimal bigValue = valueMeta.getBigNumber(valueData);
            BigDecimal previous = (BigDecimal) sums[i];
            sums[i] = previous == null ? bigValue : previous.add(bigValue);
          }
          default -> {
            if (method != AggregationMethod.COUNT) {
              throw new HException(
                  "Unsupported data type for aggregation on field '"
                      + valueMeta.getName()
                      + "': "
                      + valueMeta.getTypeDesc());
            }
          }
        }
      }
    }

    Object[] toRow(List<Object> groupKey, IRowMeta outputRowMeta, List<HFact> facts) {
      Object[] row = RowDataUtil.allocateRowData(outputRowMeta.size());
      int pos = 0;
      for (Object g : groupKey) {
        row[pos++] = g;
      }
      for (int i = 0; i < facts.size(); i++) {
        AggregationMethod method =
            facts.get(i).getAggregationMethod() != null
                ? facts.get(i).getAggregationMethod()
                : AggregationMethod.SUM;
        switch (method) {
          case COUNT -> row[pos++] = counts[i];
          case AVERAGE -> {
            if (counts[i] == 0 || sums[i] == null) {
              row[pos++] = null;
            } else if (sums[i] instanceof BigDecimal bd) {
              row[pos++] =
                  bd.divide(BigDecimal.valueOf(counts[i]), MathContext.DECIMAL64).doubleValue();
            } else if (sums[i] instanceof Double d) {
              row[pos++] = d / counts[i];
            } else if (sums[i] instanceof Long l) {
              row[pos++] = l.doubleValue() / counts[i];
            } else {
              row[pos++] = null;
            }
          }
          case SUM -> row[pos++] = sums[i];
          default -> row[pos++] = sums[i];
        }
      }
      return row;
    }
  }
}
