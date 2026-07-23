package org.hopper.presentation.variable;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
@NoArgsConstructor
public class HParameterMapping {
  @HopMetadataProperty private String connectorName;
  @HopMetadataProperty private List<FieldToParameterMapping> mappings = new ArrayList<>();
  @HopMetadataProperty private String separator;

  public HParameterMapping(HParameterMapping m) {
    this();
    this.connectorName = m.connectorName;
    if (m.mappings != null) {
      m.mappings.forEach(f -> this.mappings.add(new FieldToParameterMapping(f)));
    }
    this.separator = m.separator;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static final class FieldToParameterMapping {
    @HopMetadataProperty private String fieldName;
    @HopMetadataProperty private String parameterName;

    public FieldToParameterMapping(FieldToParameterMapping m) {
      this.fieldName = m.fieldName;
      this.parameterName = m.parameterName;
    }
  }
}
