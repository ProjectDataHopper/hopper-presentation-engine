package org.hopper.presentation.connector.types.chain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.types.passthrough.PassthroughRowListener;
import org.hopper.presentation.datacontext.ChainDataContext;
import org.hopper.presentation.datacontext.IDataContext;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize(as = HChainConnector.class)
@HConnectorPlugin(
    id = "ChainConnector",
    name = "Chain connectors",
    description = "Chain multiple connectors, encapsulate in a single connector",
    image = "ui/images/connectors/chain.svg")
@Getter
@Setter
public class HChainConnector extends HBaseConnector implements IHConnector {

  public static final String STRING_LAST_CONNECTOR_NAME = "_RESULT_OF_CHAIN_";
  @JsonIgnore protected ArrayBlockingQueue<Object> finishedQueue;

  /**
   * Nested connector pipeline. Schema type is always {@code LIST} with {@code itemKind=connector}
   * (see {@code GuiFormSchemaBuilder}); the annotation {@code type} is ignored for {@link List}
   * fields. Web UI currently uses advanced JSON rows; a step editor + connector catalog is planned.
   */
  @HWidgetElement(
      order = "10000-connectors",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Chained connectors",
      toolTip =
          "Ordered nested connector steps (Filter, Select, Sort, …). "
              + "First step uses the chain Source connector; later steps wire automatically.")
  @HopMetadataProperty
  private List<IHConnector> connectors;

  public HChainConnector() {
    super("ChainConnector");
    finishedQueue = null;
    connectors = new ArrayList<>();
  }

  public HChainConnector(HChainConnector c) {
    super(c);
    connectors = new ArrayList<>();
    for (IHConnector connector : c.connectors) {
      connectors.add(connector.clone());
    }
  }

  public HChainConnector(String sourceConnectorName, List<IHConnector> connectors) {
    this();
    super.sourceConnectorName = sourceConnectorName;
    this.connectors = connectors;
  }

  public HChainConnector clone() {
    return new HChainConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    // Nested steps resolve each other via synthetic names (__ChainConnector_N /
    // _RESULT_OF_CHAIN_). describe must use the chain context, not the parent — otherwise
    // intermediate sources are missing and transforms fail describe while streaming still works.
    if (connectors == null || connectors.isEmpty()) {
      throw new HException("Chain connector has no nested steps to describe");
    }

    String outerSource = getSourceConnectorName();
    if (outerSource != null && !outerSource.isBlank()) {
      HConnector source = dataContext.getConnector(outerSource);
      if (source == null) {
        throw new HException(
            "Unable to find connector source '" + outerSource + "' for chain connector");
      }
    }

    ChainDataContext chainDataContext = createChainContext(dataContext);
    HConnector lastConnector = chainDataContext.getLastConnector();
    if (lastConnector == null || lastConnector.getConnector() == null) {
      throw new HException("Chain connector failed to resolve the last nested step");
    }
    return lastConnector.getConnector().describeOutput(chainDataContext);
  }

  public ChainDataContext createChainContext(IDataContext parentDataContext) {
    ChainDataContext chainDataContext = new ChainDataContext(parentDataContext);

    String previousName = null;
    for (int i = 0; i < connectors.size(); i++) {
      IHConnector connector = connectors.get(i);
      String connectorName;
      if (i == connectors.size() - 1) {
        connectorName = STRING_LAST_CONNECTOR_NAME;
      } else {
        connectorName = "__ChainConnector_" + i;
      }
      HConnector hopperConnector = new HConnector(connectorName, connector);
      if (i == 0) {
        connector.setSourceConnectorName(getSourceConnectorName());
      } else {
        connector.setSourceConnectorName(previousName);
      }
      chainDataContext.addConnector(hopperConnector);
      previousName = connectorName;
    }

    return chainDataContext;
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    HConnector sourceConnector = dataContext.getConnector(getSourceConnectorName());
    if (sourceConnector == null) {
      throw new HException(
          "Unable to find source '" + getSourceConnectorName() + "' for chain connector");
    }

    ChainDataContext chainDataContext = createChainContext(dataContext);
    HConnector lastConnector = chainDataContext.getLastConnector();

    if (finishedQueue != null) {
      throw new HException(
          "Please don't start streaming twice in your application, wait until the connector has finished sending rows");
    }
    finishedQueue = new ArrayBlockingQueue<>(10);

    IHConnector last = lastConnector.getConnector();
    attachToSource(last, new PassthroughRowListener(this, finishedQueue));
    last.startStreaming(chainDataContext);
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
