package org.hopper.presentation.connector.types.distinct;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Suppresses rows that are equal to the immediately previous row (adjacent-duplicate removal).
 *
 * <p>This is not a full-set DISTINCT: non-adjacent duplicates still pass. For global uniqueness,
 * sort first (e.g. via {@code SortConnector}) so equal rows are adjacent.
 */
@JsonDeserialize(as = HDistinctConnector.class)
@HConnectorPlugin(
    id = "DistinctConnector",
    name = "Select distinct rows",
    description =
        "Drops rows equal to the previous row (adjacent distinct); sort first for full uniqueness",
    image = "ui/images/connectors/distinct.svg")
public class HDistinctConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  public HDistinctConnector() {
    super("DistinctConnector");
    finishedQueue = null;
  }

  public HDistinctConnector(HDistinctConnector c) {
    super(c);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for distinct connector");
    }
    return connector.getConnector().describeOutput(dataContext);
  }

  public HDistinctConnector clone() {
    return new HDistinctConnector(this);
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for distinct connector");
    }

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    AtomicBoolean firstRow = new AtomicBoolean(true);
    IHRowListener listener =
        new IHRowListener() {
          private Object[] previousRow = null;

          @Override
          public void rowReceived(IRowMeta rowMeta, Object[] rowData) throws HException {
            if (rowData == null) {
              outputDone();
              finishedQueue.add(new Object());
              return;
            }

            if (firstRow.get()) {
              passToRowListeners(rowMeta, rowData);
              firstRow.set(false);
            } else {
              int result;
              try {
                result = rowMeta.compare(rowData, previousRow);
              } catch (HopValueException e) {
                throw new HException("Error comparing rows of data", e);
              }
              if (result != 0) {
                passToRowListeners(rowMeta, rowData);
              }
            }

            previousRow = rowData;
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
