package org.hopper.audit;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Name/value property for an {@link HAuditSinkMeta} configuration. */
@Getter
@Setter
@NoArgsConstructor
public class HAuditSinkProperty {
  @HopMetadataProperty private String name;
  @HopMetadataProperty private String value;

  public HAuditSinkProperty(String name, String value) {
    this.name = name;
    this.value = value;
  }
}
