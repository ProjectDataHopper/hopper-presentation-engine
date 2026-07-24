package org.hopper.presentation.connector.types.metadata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMetaBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

@JsonDeserialize(as = HMetadataPresentationsConnector.class)
@HConnectorPlugin(
    id = "MetadataPresentationsConnector",
    name = "Presentations list",
    description = "Lists the available presentations",
    image = "ui/images/connectors/presentations.svg")
public class HMetadataPresentationsConnector extends HBaseConnector
    implements IHConnector {

  public HMetadataPresentationsConnector() {
    super("MetadataPresentationsConnector");
  }

  public HMetadataPresentationsConnector(HMetadataPresentationsConnector c) {
    super(c);
  }

  public HMetadataPresentationsConnector clone() {
    return new HMetadataPresentationsConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    return new RowMetaBuilder().addString("name").addString("description").build();
  }

  /**
   * Output the names of the elements for the given key
   *
   * @param dataContext the data context to reference
   * @throws HException
   */
  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    IRowMeta rowMeta = describeOutput(dataContext);

    try {
      IHopMetadataProvider provider = dataContext.getMetadataProvider();
      IHopMetadataSerializer<HPresentation> serializer =
          provider.getSerializer(HPresentation.class);

      List<String> names = serializer.listObjectNames();

      for (String name : names) {
        HPresentation presentation = serializer.load(name);

        Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
        rowData[0] = name;
        rowData[1] = presentation.getDescription();

        passToRowListeners(rowMeta, rowData);
      }
    } catch (Exception e) {
      throw new HException("Error writing presentation metadata output", e);
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
