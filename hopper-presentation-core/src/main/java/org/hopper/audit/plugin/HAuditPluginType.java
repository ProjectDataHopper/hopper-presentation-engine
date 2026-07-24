package org.hopper.audit.plugin;

import java.util.Map;
import org.apache.hop.core.plugins.BasePluginType;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginAnnotationType;
import org.apache.hop.core.plugins.PluginMainClassType;

@PluginMainClassType(IAuditSink.class)
@PluginAnnotationType(HAuditPlugin.class)
public class HAuditPluginType extends BasePluginType<HAuditPlugin>
    implements IPluginType<HAuditPlugin> {

  private static HAuditPluginType pluginType;

  protected HAuditPluginType() {
    super(HAuditPlugin.class, "HAuditSink", "Audit Sink");
  }

  protected HAuditPluginType(Class<HAuditPlugin> pluginType, String id, String name) {
    super(pluginType, id, name);
  }

  public static HAuditPluginType getInstance() {
    if (pluginType == null) {
      pluginType = new HAuditPluginType();
    }
    return pluginType;
  }

  @Override
  protected void addExtraClasses(
      Map<Class<?>, String> classMap, Class<?> clazz, HAuditPlugin annotation) {}

  @Override
  protected String extractID(HAuditPlugin annotation) {
    return annotation.id();
  }

  @Override
  protected String extractName(HAuditPlugin annotation) {
    return annotation.name();
  }

  @Override
  protected String extractDesc(HAuditPlugin annotation) {
    return annotation.description();
  }
}
