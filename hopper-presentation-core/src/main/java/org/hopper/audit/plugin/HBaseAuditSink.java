package org.hopper.audit.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.variables.IVariables;
import org.hopper.core.exception.HException;

/** Base class for audit sinks with init property storage. */
public abstract class HBaseAuditSink implements IAuditSink {

  private Map<String, String> properties = Map.of();
  private IVariables variables;

  @Override
  public void init(Map<String, String> properties, IVariables variables) throws HException {
    this.properties =
        properties == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    this.variables = variables;
  }

  protected Map<String, String> getProperties() {
    return properties;
  }

  protected IVariables getVariables() {
    return variables;
  }

  protected String property(String key, String defaultValue) {
    if (properties == null) {
      return defaultValue;
    }
    String value = properties.get(key);
    return value != null ? value : defaultValue;
  }
}
