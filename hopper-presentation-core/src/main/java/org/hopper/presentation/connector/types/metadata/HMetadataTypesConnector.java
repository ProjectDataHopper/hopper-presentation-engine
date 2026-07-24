package org.hopper.presentation.connector.types.metadata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMetaBuilder;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

@JsonDeserialize(as = HMetadataTypesConnector.class)
@HConnectorPlugin(
    id = "MetadataTypesConnector",
    name = "Metadata types",
    description = "Lists the available metadata types",
    image = "ui/images/connectors/metadata-types.svg")
public class HMetadataTypesConnector extends HBaseConnector implements IHConnector {

  public HMetadataTypesConnector() {
    super("MetadataTypesConnector");
  }

  public HMetadataTypesConnector(HMetadataTypesConnector c) {
    super(c);
  }

  public HMetadataTypesConnector clone() {
    return new HMetadataTypesConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    return new RowMetaBuilder()
        .addString("key")
        .addString("description")
        .addInteger("elementCount")
        .build();
  }

  /**
   * We simply output the available metadata types: key, description and number of elements.
   *
   * @param dataContext the data context to reference
   * @throws HException
   */
  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    IRowMeta rowMeta = describeOutput(dataContext);

    try {
      IHopMetadataProvider provider = dataContext.getMetadataProvider();
      List<Class<IHopMetadata>> metadataClasses = provider.getMetadataClasses();
      for (Class<IHopMetadata> metadataClass : metadataClasses) {

        HopMetadata metadata = metadataClass.getAnnotation(HopMetadata.class);
        if (metadata==null) {
          continue;
        }
        IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(metadataClass);

        Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
        rowData[0] = metadata.key();
        rowData[1] = metadata.description();
        rowData[2] = (long) serializer.listObjectNames().size();

        passToRowListeners(rowMeta, rowData);
      }
    } catch (Exception e) {
      throw new HException("Error writing metadata types output", e);
    }

    // Signal to all row listeners that no more rows are forthcoming.
    //
    outputDone();
  }

  @Override
  public void waitUntilFinished() throws HException {
    // StartStreaming works synchronized, no need to get complicated about it
  }
}
