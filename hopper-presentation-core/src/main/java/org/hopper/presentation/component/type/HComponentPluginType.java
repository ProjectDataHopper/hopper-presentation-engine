package org.hopper.presentation.component.type;

import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.plugins.BasePluginType;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginAnnotationType;
import org.apache.hop.core.plugins.PluginMainClassType;

import java.util.Map;

@PluginMainClassType(IHComponent.class)
@PluginAnnotationType(HComponentPlugin.class)
public class HComponentPluginType extends BasePluginType<HComponentPlugin>
    implements IPluginType<HComponentPlugin> {

  private static HComponentPluginType pluginType;

  protected HComponentPluginType() {
    super(HComponentPlugin.class, "HComponentType", "Component Type");
  }

  protected HComponentPluginType(Class<HComponentPlugin> pluginType, String id, String name) {
    super(pluginType, id, name);
  }

  public static HComponentPluginType getInstance() {
    if (pluginType == null) {
      pluginType = new HComponentPluginType();
    }
    return pluginType;
  }

  @Override
  protected void registerNatives() throws HopPluginException {
    super.registerNatives();
  }

  @Override
  protected void addExtraClasses(
      Map<Class<?>, String> arg0, Class<?> arg1, HComponentPlugin arg2) {}

  @Override
  protected String extractID(HComponentPlugin annotation) {
    return annotation.id();
  }

  @Override
  protected String extractName(HComponentPlugin annotation) {
    return annotation.name();
  }

  @Override
  protected String extractDesc(HComponentPlugin annotation) {
    return annotation.description();
  }

  @Override
  protected String extractImageFile(HComponentPlugin annotation) {
    return annotation.image();
  }
}
