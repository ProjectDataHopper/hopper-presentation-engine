package org.hopper.core;

import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.exception.HException;
import org.hopper.presentation.datacontext.IDataContext;

public interface IHDataStreaming {

  /**
   * Add a data listener which keeps on the lookout for new rows of data
   *
   * @param rowListener
   * @throws HException
   */
  public void addRowListener(IHRowListener rowListener) throws HException;

  /**
   * Start streaming data, pick up the rows with addDataListener();
   *
   * @param dataContext the data context in which the connector needs to work (other connectors to
   *     use...)
   * @throws HException
   */
  public void startStreaming(IDataContext dataContext) throws HException;

  /**
   * End streaming of data
   *
   * @throws HException
   */
  public void waitUntilFinished() throws HException;

  /**
   * Remove the data listener in case it's no longer needed
   *
   * @param rowListener the listener to remove
   */
  public void removeDataListener(IHRowListener rowListener);

  /**
   * Describes all the fields that the connector produces at runtime without actually running
   * anything.
   *
   * @return The row metadata
   * @param dataContext the data context
   * @throws HException
   */
  public IRowMeta describeOutput(IDataContext dataContext) throws HException;
}
