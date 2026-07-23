package org.hopper.presentation.connector.types.passthrough;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

@JsonDeserialize(as = HPassthroughConnector.class)
@HConnectorPlugin(
    id = "PassthroughConnector",
    name = "A passthrough connector",
    description = "Simply passes all the rows of the selected data source",
    image = "ui/images/connectors/passthrough.svg")
public class HPassthroughConnector extends HBaseConnector implements IHConnector {

  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  public HPassthroughConnector() {
    super("PassthroughConnector");
    finishedQueue = null;
  }

  public HPassthroughConnector(String sourceConnector) {
    this();
    setSourceConnectorName(sourceConnector);
  }

  public HPassthroughConnector(HPassthroughConnector c) {
    super(c);
    // Beyond base connector no other metadata
  }

  public HPassthroughConnector clone() {
    return new HPassthroughConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnector connector = dataContext.getConnector(getSourceConnectorName());
    if (connector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for passthrough connector");
    }
    return connector.getConnector().describeOutput(dataContext);
  }

  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    HConnector sourceConnector = dataContext.getConnector(getSourceConnectorName());
    if (sourceConnector == null) {
      throw new HException(
          "Unable to find connector source '"
              + getSourceConnectorName()
              + "' for passthrough connector");
    }

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    IHConnector source = sourceConnector.getConnector();
    attachToSource(source, new PassthroughRowListener(this, finishedQueue));
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
