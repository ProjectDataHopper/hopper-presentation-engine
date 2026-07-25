package org.hopper.presentation.variable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Declares a presentation parameter (Hop pipeline/workflow style): name, description, and default
 * value.
 *
 * <p>Definitions live on {@link org.hopper.presentation.HPresentation#getParameters()}. At layout
 * time defaults are applied after system variables and before request/interaction values. Runtime
 * values use {@link HParameter} ({@code name}/{@code value}) when calling {@code doLayout}.
 */
@Getter
@Setter
@NoArgsConstructor
public class HParameterDefinition {

  /** Parameter / variable name (e.g. {@code REGION}). */
  @HopMetadataProperty private String name;

  /** Human-readable help for editors, interaction mapping, and future prompts. */
  @HopMetadataProperty private String description;

  /**
   * Default value applied when the presentation is laid out and the parameter was not supplied by
   * the request/interaction. Variables in the default are resolved against the presentation context
   * (so system variables can be referenced).
   */
  @HopMetadataProperty private String defaultValue;

  public HParameterDefinition(String name, String description, String defaultValue) {
    this.name = name;
    this.description = description;
    this.defaultValue = defaultValue;
  }

  public HParameterDefinition(HParameterDefinition other) {
    if (other != null) {
      this.name = other.name;
      this.description = other.description;
      this.defaultValue = other.defaultValue;
    }
  }
}
