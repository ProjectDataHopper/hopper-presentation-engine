package org.hopper.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Name/value pair stored on {@link HSystemVariables}. */
@Getter
@Setter
@NoArgsConstructor
public class HVariableEntry {

  @HopMetadataProperty private String name;
  @HopMetadataProperty private String value;

  public HVariableEntry(String name, String value) {
    this.name = name;
    this.value = value;
  }
}
