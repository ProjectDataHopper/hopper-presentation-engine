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
 * System-wide Hop variables managed from the admin panel.
 *
 * <p>Convention: a single document named {@link #DOCUMENT_NAME} holds the active set. All
 * presentations, connectors, and other variable spaces inherit these values (presentation
 * parameters may override them).
 *
 * <p>Secrets may be stored using Hop encoding ({@code Encrypted …}) via {@code
 * Encr.encryptPasswordIfNotUsingVariables()}; prefer {@code ${ENV}} or variable-resolver
 * expressions for production secrets.
 */
@HopMetadata(
    key = "system-variables",
    name = "System variables",
    description = "Server-wide variables inherited by presentations and connectors")
@Getter
@Setter
public class HSystemVariables extends HopMetadataBase implements IHopMetadata {

  /** Singleton document name for the active system variable set. */
  public static final String DOCUMENT_NAME = "runtime";

  @HopMetadataProperty private List<HVariableEntry> variables = new ArrayList<>();

  @HopMetadataProperty private String updatedAt;

  @HopMetadataProperty private String updatedBy;

  @HopMetadataProperty private long revision;

  public HSystemVariables() {
    this.name = DOCUMENT_NAME;
  }

  public Map<String, String> variablesAsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    if (variables != null) {
      for (HVariableEntry e : variables) {
        if (e != null && e.getName() != null && !e.getName().isBlank()) {
          map.put(e.getName().trim(), e.getValue() != null ? e.getValue() : "");
        }
      }
    }
    return map;
  }

  public void setVariablesFromMap(Map<String, String> map) {
    List<HVariableEntry> list = new ArrayList<>();
    if (map != null) {
      for (Map.Entry<String, String> e : map.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) {
          continue;
        }
        list.add(new HVariableEntry(e.getKey().trim(), e.getValue() != null ? e.getValue() : ""));
      }
    }
    this.variables = list;
  }

  public static HSystemVariables emptyRuntime() {
    HSystemVariables s = new HSystemVariables();
    s.setName(DOCUMENT_NAME);
    return s;
  }
}
