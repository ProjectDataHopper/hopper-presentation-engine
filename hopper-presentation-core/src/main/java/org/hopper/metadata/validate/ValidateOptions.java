package org.hopper.metadata.validate;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.presentation.variable.HParameter;

/** Options for {@link HMetadataValidator}. */
@Getter
@Builder
public class ValidateOptions {
  /** When true, run a layout smoke test (may be slower). Default false. */
  @Builder.Default private final boolean includeSmokeLayout = false;

  /** When true, unknown interaction categories are ERROR; otherwise WARNING. */
  @Builder.Default private final boolean strictInteractions = false;

  /** Optional provider for shared connectors / smoke layout. */
  private final IHopMetadataProvider metadataProvider;

  /** Parameters for smoke layout. */
  @Builder.Default private final List<HParameter> parameters = Collections.emptyList();
}
