package org.hopper.presentation.connector.types.passthrough;

import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.type.IHConnector;

import java.util.concurrent.ArrayBlockingQueue;

public class PassthroughRowListener implements IHRowListener {

  protected IHConnector connector;
  protected ArrayBlockingQueue<Object> finishedQueue;

  public PassthroughRowListener(
      IHConnector connector, ArrayBlockingQueue<Object> finishedQueue) {
    this.connector = connector;
    this.finishedQueue = finishedQueue;
  }

  public void rowReceived(IRowMeta rowMeta, Object[] rowData) throws HException {
    if (rowData == null) {
      // Signal we're done
      //
      for (IHRowListener rowListener : connector.getRowListeners()) {
        rowListener.rowReceived(null, null);
      }
      if (finishedQueue != null) {
        finishedQueue.add(new Object());
      }
      return;
    }

    // Pass the data along
    //
    for (IHRowListener rowListener : connector.getRowListeners()) {
      rowListener.rowReceived(rowMeta, rowData);
    }
  }
}
