package org.hopper.presentation.datacontext;

import org.apache.hop.core.logging.ILogChannel;
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

  /**
   * Optional per-layout connector result cache. When non-null and enabled, named connectors loaded
   * via {@link #getConnector(String)} stream once and replay for later components in the same
   * layout. Default is {@code null} (no caching).
   */
  default HConnectorResultCache getConnectorResultCache() {
    return null;
  }

  /**
   * Presentation layout log channel (metrics-enabled). Used for connector START/STOP snaps so they
   * land on the same channel as layout. Default {@code null}.
   */
  default ILogChannel getLogChannel() {
    return null;
  }
}
