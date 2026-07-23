package org.hopper.presentation.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;

@HopMetadata(
    key = "connector",
    name = "Connector",
    description = "Connector between components and data sources")
@Getter
@Setter
public class HConnector extends HopMetadataBase implements IHopMetadata {

  /** The name of the connector */
  @JsonProperty @HopMetadataProperty private String name;

  @HopMetadataProperty @JsonProperty private IHConnector connector;

  public HConnector() {}

  public HConnector(String name, IHConnector connector) {
    this();
    this.name = name;
    this.connector = connector;
  }

  /**
   * Create a new connector by copying over the details of the given connector
   *
   * @param c
   */
  public HConnector(HConnector c) {
    this.name = c.name;
    this.connector = c.connector == null ? null : c.connector.clone();
  }

  /**
   * Uses addDataListener() to retrieve all the rows from the data stream...
   *
   * @return all the rows from the connector
   * @throws HException
   */
  public List<RowMetaAndData> retrieveRows(IDataContext dataContext) throws HException {
    try {
      final List<RowMetaAndData> rows = new ArrayList<>();

      // Add a listener to the connector data
      // Whenever we get a row, we add it to the list...
      //
      final ArrayBlockingQueue<Object> finishedQueue = new ArrayBlockingQueue<>(10);
      IHRowListener listener =
          (rowMeta, rowData) -> {
            if (rowData == null) {
              finishedQueue.add(new Object());
            } else {
              rows.add(new RowMetaAndData(rowMeta, rowData));
            }
          };
      connector.addRowListener(listener);

      // Start streaming data
      //
      connector.startStreaming(dataContext);

      // Wait for it to end.
      //
      while (finishedQueue.poll(1L, TimeUnit.DAYS) == null) {
        ;
      }

      connector.waitUntilFinished();

      connector.removeDataListener(listener);

      return rows;
    } catch (Exception e) {
      throw new HException("Error getting all rows from connector", e);
    }
  }

  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    return connector.describeOutput(dataContext);
  }
}
