package org.hopper.presentation.interaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HInteractionParameter {

  @HopMetadataProperty private ParameterSourceType sourceType;
  @HopMetadataProperty private String parameterName;

  public HInteractionParameter(HInteractionParameter p) {
    this.sourceType = p.sourceType;
    this.parameterName = p.parameterName;
  }

  public enum ParameterSourceType {
    PresentationName,
    ComponentName,
    ComponentPluginId,
    ItemType,
    ItemCategory,
    ItemValue,
  }
}
