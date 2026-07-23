package org.hopper.presentation.connector.types.metadata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMetaBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize(as = HMetadataElementsConnector.class)
@HConnectorPlugin(
    id = "MetadataElementsConnector",
    name = "Metadata elements",
    description = "Lists the available metadata elements",
    image = "ui/images/connectors/metadata.svg")
@Getter
@Setter
public class HMetadataElementsConnector extends HBaseConnector implements IHConnector {

  @HWidgetElement(
      order = "10000-elementKey",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Metadata type key",
      toolTip = "Hop metadata key (e.g. presentation, theme, hopper-database-connection)")
  @HopMetadataProperty
  private String elementKey;

  public HMetadataElementsConnector() {
    super("MetadataElementsConnector");
  }

  public HMetadataElementsConnector(String elementKey) {
    this();
    this.elementKey = elementKey;
  }

  public HMetadataElementsConnector(HMetadataElementsConnector c) {
    super(c);
    this.elementKey = c.elementKey;
  }

  public HMetadataElementsConnector clone() {
    return new HMetadataElementsConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    return new RowMetaBuilder().addString("name").build();
  }

  /**
   * Output the names of the elements for the given key
   *
   * @param dataContext the data context to reference
   * @throws HException
   */
  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    IRowMeta rowMeta = describeOutput(dataContext);

    if (StringUtils.isEmpty(elementKey)) {
      throw new HException("Please specify the key of the metadata element type to list");
    }

    try {
      IHopMetadataProvider provider = dataContext.getMetadataProvider();
      Class<IHopMetadata> hopMetadataClass = provider.getMetadataClassForKey(elementKey);
      IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(hopMetadataClass);

      List<String> names = serializer.listObjectNames();

      for (String name : names) {

        Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
        rowData[0] = name;

        for (IHRowListener rowListener : rowListeners) {
          rowListener.rowReceived(rowMeta, rowData);
        }
      }
    } catch (Exception e) {
      throw new HException("Error writing metadata elements output", e);
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
