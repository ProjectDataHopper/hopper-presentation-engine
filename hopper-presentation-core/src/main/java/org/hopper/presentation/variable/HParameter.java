package org.hopper.presentation.variable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Defines a parameter for a Hopper presentation.
 *
 * <p>TODO: Add different types of parameters: Static value (current), from connector field, from
 * prompt, ...
 *
 * <p>TODO: add prompts as a source type TODO: move the source type to a separate plugin type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HParameter {

  @HopMetadataProperty private String parameterName;
  @HopMetadataProperty private String parameterValue;

  public HParameter(HParameter v) {
    this.parameterName = v.parameterName;
    this.parameterValue = v.parameterValue;
  }
}
