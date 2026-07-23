package org.hopper.presentation.connector;

import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.core.HEnvironment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

/** Shared helpers for connector streaming tests. */
public final class ConnectorTestSupport {

  private ConnectorTestSupport() {}

  public static void initEnvironment() throws HException {
    HEnvironment.init();
  }

  public static PresentationDataContext dataContext(HConnector... connectors) {
    HPresentation presentation = new HPresentation();
    presentation.setName("connector-test");
    presentation.setDescription("unit test presentation");
    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();
    try {
      for (HConnector connector : connectors) {
        if (connector != null) {
          metadataProvider.getSerializer(HConnector.class).save(connector);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to save test connectors to metadata", e);
    }
    return new PresentationDataContext(presentation, metadataProvider);
  }

  public static HConnector wrap(String name, IHConnector connector) {
    return new HConnector(name, connector);
  }

  public static List<RowMetaAndData> retrieve(HConnector connector, PresentationDataContext ctx)
      throws HException {
    return connector.retrieveRows(ctx);
  }
}
