package org.hopper.presentation.interaction;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * A Hopper interaction method describes the way a user can interact with any part of a presentation.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class HInteractionMethod {

  public static final HInteractionMethod SingleClick = new HInteractionMethod(true, false);
  public static final HInteractionMethod DoubleClick = new HInteractionMethod(false, true);

  @HopMetadataProperty private boolean mouseClick;

  @HopMetadataProperty private boolean mouseDoubleClick;

  public HInteractionMethod(boolean mouseClick, boolean mouseDoubleClick) {
    this.mouseClick = mouseClick;
    this.mouseDoubleClick = mouseDoubleClick;
  }

  public HInteractionMethod(HInteractionMethod method) {
    this.mouseClick = method.mouseClick;
    this.mouseDoubleClick = method.mouseDoubleClick;
  }
}
