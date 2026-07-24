package org.hopper.audit.lineage;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hopper.audit.HAuditOutcome;

/** Optional record that a component processed source data during layout. */
@Getter
@Setter
public class HComponentRun {
  private String componentName;
  private String pluginId;
  private int pageIndex;
  private String sourceConnectorName;
  private HAuditOutcome outcome = HAuditOutcome.SUCCESS;
  private String errorMessage;
  private long durationMs;

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("componentName", componentName);
    map.put("pluginId", pluginId);
    map.put("pageIndex", pageIndex);
    map.put("sourceConnectorName", sourceConnectorName);
    map.put("outcome", outcome != null ? outcome.name() : null);
    map.put("errorMessage", errorMessage);
    map.put("durationMs", durationMs);
    return map;
  }
}
