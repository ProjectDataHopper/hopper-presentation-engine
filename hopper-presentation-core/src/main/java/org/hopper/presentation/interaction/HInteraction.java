package org.hopper.presentation.interaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.draw.DrawnItem;

/**
 * Describes an interaction: method: how the user interacts. location: where the interaction can
 * take place action: what needs to happen
 */
@Getter
@Setter
public class HInteraction {

  @HopMetadataProperty private HInteractionMethod method;
  @HopMetadataProperty private HInteractionLocation location;
  @HopMetadataProperty private List<HInteractionAction> actions;

  public HInteraction() {
    this.actions = new ArrayList<>();
    this.method = HInteractionMethod.SINGLE_CLICK;
  }

  public HInteraction(
      HInteractionMethod method,
      HInteractionLocation location,
      HInteractionAction... actions) {
    this.method = method != null ? method : HInteractionMethod.SINGLE_CLICK;
    this.location = location;
    this.actions = new ArrayList<>(Arrays.asList(actions));
  }

  public HInteraction(HInteraction interaction) {
    this();
    this.method =
        interaction.method != null ? interaction.method : HInteractionMethod.SINGLE_CLICK;
    this.location =
        interaction.location == null ? null : new HInteractionLocation(interaction.location);
    for (HInteractionAction action : interaction.actions) {
      actions.add(new HInteractionAction(action));
    }
  }

  /**
   * @param method required method, or {@code null} to match any method
   * @param drawnItem hit under the pointer
   */
  public boolean matches(HInteractionMethod method, DrawnItem drawnItem) {
    if (method != null) {
      HInteractionMethod mine = this.method != null ? this.method : HInteractionMethod.SINGLE_CLICK;
      if (mine != method) {
        return false;
      }
    }
    return location != null && location.matches(drawnItem);
  }
}
