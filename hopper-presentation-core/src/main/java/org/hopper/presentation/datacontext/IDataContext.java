package org.hopper.presentation.datacontext;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.HConnector;

/** This describes the context by which components get data. */
public interface IDataContext {

  HConnector getConnector(String name) throws HException;

  IVariables getVariables();

  IHopMetadataProvider getMetadataProvider();

  /**
   * Optional execution lineage collector for the current layout/preview. Default is a no-op trace
   * so connectors always have a non-null object when checking {@link HExecutionTrace#isNoop()}.
   */
  default HExecutionTrace getExecutionTrace() {
    return HExecutionTrace.noop();
  }
}
