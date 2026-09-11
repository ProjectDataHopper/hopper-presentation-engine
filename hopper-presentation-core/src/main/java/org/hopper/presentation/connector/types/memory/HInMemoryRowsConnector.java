package org.hopper.presentation.connector.types.memory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Source connector that streams a host-supplied row list. Not intended as catalog JSON — rows are
 * {@link JsonIgnore}d so small-chart embeds can inject {@link RowMetaAndData} without a metadata
 * round-trip.
 */
@JsonDeserialize(as = HInMemoryRowsConnector.class)
@HConnectorPlugin(
    id = "HInMemoryRowsConnector",
    name = "In-memory rows",
    description = "Streams rows already loaded by the host (small charts, tests, pane embeds)",
    image = "ui/images/connectors/list.svg")
@Getter
@Setter
public class HInMemoryRowsConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore private transient List<RowMetaAndData> rows = new ArrayList<>();

  /** Schema used when {@link #rows} is empty so charts can still bind columns. */
  @JsonIgnore private transient IRowMeta outputRowMeta;

  public HInMemoryRowsConnector() {
    super("HInMemoryRowsConnector");
  }

  public HInMemoryRowsConnector(List<RowMetaAndData> rows) {
    this();
    setRows(rows);
  }

  public HInMemoryRowsConnector(HInMemoryRowsConnector c) {
    super(c);
    setRows(c.rows);
    if (this.outputRowMeta == null && c.outputRowMeta != null) {
      setOutputRowMeta(c.outputRowMeta);
    }
  }

  @Override
  public HInMemoryRowsConnector clone() {
    return new HInMemoryRowsConnector(this);
  }

  public void setRows(List<RowMetaAndData> rows) {
    this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
    IRowMeta fromRows = firstMeta(this.rows);
    if (fromRows != null) {
      this.outputRowMeta = fromRows.clone();
    }
  }

  public void setOutputRowMeta(IRowMeta outputRowMeta) {
    this.outputRowMeta = outputRowMeta != null ? outputRowMeta.clone() : null;
  }

  public List<RowMetaAndData> getRows() {
    return rows != null ? rows : List.of();
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) {
    IRowMeta fromRows = firstMeta(getRows());
    if (fromRows != null) {
      return fromRows.clone();
    }
    if (outputRowMeta != null) {
      return outputRowMeta.clone();
    }
    return new RowMeta();
  }

  private static IRowMeta firstMeta(List<RowMetaAndData> list) {
    if (list == null) {
      return null;
    }
    for (RowMetaAndData row : list) {
      if (row != null && row.getRowMeta() != null) {
        return row.getRowMeta();
      }
    }
    return null;
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    IRowMeta rowMeta = describeOutput(dataContext);
    for (RowMetaAndData row : getRows()) {
      if (row == null) {
        continue;
      }
      IRowMeta meta = row.getRowMeta() != null ? row.getRowMeta() : rowMeta;
      passToRowListeners(meta, row.getData());
    }
    outputDone();
  }

  @Override
  public void waitUntilFinished() throws HException {
    // Synchronous stream in doStartStreaming.
  }
}
