package org.hopper.presentation.datacontext;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.HConnector;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChainDataContext implements IDataContext {

  private IDataContext parentDataContext;

  private Map<String, HConnector> connectorsMap;

  private HConnector lastConnector;

  public ChainDataContext() {
    connectorsMap = new HashMap<>();
  }

  public ChainDataContext(IDataContext parentDataContext) {
    this.parentDataContext = parentDataContext;
    connectorsMap = new HashMap<>();
  }

  @Override
  public HConnector getConnector(String name) throws HException {
    HConnector connector = parentDataContext.getConnector(name);
    if (connector == null) {
      connector = connectorsMap.get(name);
    }
    if (connector != null) {
      connector = new HConnector(connector);
    }
    return connector;
  }

  @Override
  public IVariables getVariables() {
    return parentDataContext.getVariables();
  }

  public void addConnector(HConnector hopperConnector) {
    this.lastConnector = hopperConnector;
    connectorsMap.put(hopperConnector.getName(), hopperConnector);
  }


  @Override
  public IHopMetadataProvider getMetadataProvider() {
    return parentDataContext.getMetadataProvider();
  }

  @Override
  public HExecutionTrace getExecutionTrace() {
    return parentDataContext != null
        ? parentDataContext.getExecutionTrace()
        : HExecutionTrace.noop();
  }

  @Override
  public HConnectorResultCache getConnectorResultCache() {
    return parentDataContext != null ? parentDataContext.getConnectorResultCache() : null;
  }

  @Override
  public org.apache.hop.core.logging.ILogChannel getLogChannel() {
    return parentDataContext != null ? parentDataContext.getLogChannel() : null;
  }
}
