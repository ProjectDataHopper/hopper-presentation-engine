package org.hopper.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Metadata configuration for an audit sink plugin instance (destination for usage/security events).
 *
 * <p>Stored under metadata key {@code audit-sink}. Example JSON:
 *
 * <pre>
 * {
 *   "name": "local-jsonl",
 *   "pluginId": "JsonlFileAuditSink",
 *   "enabled": true,
 *   "eventTypes": ["PRESENTATION_RENDER", "METADATA_DELETE"],
 *   "properties": [ { "name": "path", "value": "/var/log/hopper/audit.jsonl" } ]
 * }
 * </pre>
 */
@HopMetadata(
    key = "audit-sink",
    name = "Audit Sink",
    description = "Configures a destination for Hopper usage and security audit events")
@Getter
@Setter
public class HAuditSinkMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String pluginId;

  @HopMetadataProperty private boolean enabled = true;

  /** Empty or null = accept all event types. Values match {@link HAuditEventType#name()}. */
  @HopMetadataProperty private List<String> eventTypes = new ArrayList<>();

  @HopMetadataProperty private List<HAuditSinkProperty> properties = new ArrayList<>();

  public HAuditSinkMeta() {}

  public HAuditSinkMeta(String name, String pluginId) {
    this.name = name;
    this.pluginId = pluginId;
  }

  public Map<String, String> propertiesAsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    if (properties != null) {
      for (HAuditSinkProperty p : properties) {
        if (p != null && p.getName() != null) {
          map.put(p.getName(), p.getValue());
        }
      }
    }
    return map;
  }
}
