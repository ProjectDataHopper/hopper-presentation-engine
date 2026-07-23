package org.hopper.core;

import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.exception.HException;

public interface IHRowListener {

  public void rowReceived(IRowMeta rowMeta, Object[] rowData) throws HException;
}
