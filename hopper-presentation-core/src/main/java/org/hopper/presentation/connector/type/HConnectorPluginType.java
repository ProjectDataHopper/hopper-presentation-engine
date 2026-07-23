package org.hopper.presentation.connector.type;

import org.apache.hop.core.plugins.BasePluginType;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginAnnotationType;
import org.apache.hop.core.plugins.PluginMainClassType;

import java.util.Map;

@PluginMainClassType(IHConnector.class)
@PluginAnnotationType(HConnectorPlugin.class)
public class HConnectorPluginType extends BasePluginType<HConnectorPlugin>
    implements IPluginType<HConnectorPlugin> {

  private static HConnectorPluginType pluginType;

  protected HConnectorPluginType() {
    super(HConnectorPlugin.class, "HConnector", "Connector");
  }

  protected HConnectorPluginType(Class<HConnectorPlugin> pluginType, String id, String name) {
    super(pluginType, id, name);
  }

  public static HConnectorPluginType getInstance() {
    if (pluginType == null) {
      pluginType = new HConnectorPluginType();
    }
    return pluginType;
  }

  @Override
  protected void addExtraClasses(
      Map<Class<?>, String> arg0, Class<?> arg1, HConnectorPlugin hopperConnectorPlugin) {}

  @Override
  protected String extractID(HConnectorPlugin annotation) {
    return annotation.id();
  }

  @Override
  protected String extractName(HConnectorPlugin annotation) {
    return annotation.name();
  }

  @Override
  protected String extractDesc(HConnectorPlugin annotation) {
    return annotation.description();
  }

  @Override
  protected String extractImageFile(HConnectorPlugin annotation) {
    return annotation.image();
  }
}
