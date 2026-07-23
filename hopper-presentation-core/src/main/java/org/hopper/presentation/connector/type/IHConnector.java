package org.hopper.presentation.connector.type;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.hopper.core.IHDataStreaming;
import org.hopper.core.IHRowListener;

@JsonDeserialize(using = IHConnectorDeserializer.class)
@HopMetadataObject(objectFactory = IHConnector.HConnectorObjectFactory.class)
public interface IHConnector extends IHDataStreaming, Cloneable {

  /**
   * @return The ID of the component type plugin
   */
  String getPluginId();

  /**
   * Set the ID of the component type
   *
   * @param pluginId The ID to set.
   */
  void setPluginId(String pluginId);

  /**
   * Gets rowListeners
   *
   * @return value of rowListeners
   */
  List<IHRowListener> getRowListeners();

  /**
   * @param rowListeners The rowListeners to set
   */
  void setRowListeners(List<IHRowListener> rowListeners);

  /**
   * @return The source connector for this connector, if any
   */
  String getSourceConnectorName();

  /**
   * @param sourceConnectorName the source connector to set
   */
  void setSourceConnectorName(String sourceConnectorName);

  /**
   * @return a copy of the metadata of this connector
   */
  IHConnector clone();

  /**
   * @return Null if the dialog class is determined automatically. Otherwise returns the dialog
   *     class name.
   */
  String getDialogClassname();

  final class HConnectorObjectFactory implements IHopMetadataObjectFactory {

    public HConnectorObjectFactory() {}

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (id == null) {
        return null;
      }
      String resolved = org.hopper.core.plugin.HPluginIds.resolve(id);
      return PluginRegistry.getInstance()
          .loadClass(HConnectorPluginType.class, resolved, IHConnector.class);
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (object == null) {
        return null;
      }
      if (!(object instanceof IHConnector)) {
        throw new HopException(
            "Invalid class to get a Hopper Connector plugin ID from: " + object.getClass());
      }
      return ((IHConnector) object).getPluginId();
    }
  }
}
