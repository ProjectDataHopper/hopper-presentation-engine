package org.hopper.config;

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
 * Runtime (L1) configuration overrides managed from the admin panel.
 *
 * <p>Convention: a single document named {@link #DOCUMENT_NAME} holds the active overrides.
 * Secrets must be stored only as env references (e.g. {@code ${GOOGLE_OAUTH_CLIENT_SECRET}}), never
 * as raw secret material when avoidable.
 */
@HopMetadata(
    key = "server-settings",
    name = "Server settings",
    description = "Runtime configuration overrides for Hopper Presentation (admin panel)")
@Getter
@Setter
public class HServerSettings extends HopMetadataBase implements IHopMetadata {

  /** Singleton document name for the active runtime override set. */
  public static final String DOCUMENT_NAME = "runtime";

  @HopMetadataProperty private List<HSettingProperty> properties = new ArrayList<>();

  @HopMetadataProperty private String updatedAt;

  @HopMetadataProperty private String updatedBy;

  @HopMetadataProperty private long revision;

  public HServerSettings() {
    this.name = DOCUMENT_NAME;
  }

  public Map<String, String> propertiesAsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    if (properties != null) {
      for (HSettingProperty p : properties) {
        if (p != null && p.getName() != null && !p.getName().isBlank()) {
          map.put(p.getName().trim(), p.getValue() != null ? p.getValue() : "");
        }
      }
    }
    return map;
  }

  public void setPropertiesFromMap(Map<String, String> map) {
    List<HSettingProperty> list = new ArrayList<>();
    if (map != null) {
      for (Map.Entry<String, String> e : map.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) {
          continue;
        }
        list.add(new HSettingProperty(e.getKey().trim(), e.getValue() != null ? e.getValue() : ""));
      }
    }
    this.properties = list;
  }

  public static HServerSettings emptyRuntime() {
    HServerSettings s = new HServerSettings();
    s.setName(DOCUMENT_NAME);
    return s;
  }
}
