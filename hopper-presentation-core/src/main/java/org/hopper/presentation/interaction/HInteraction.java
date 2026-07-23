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
  }

  public HInteraction(
      HInteractionMethod method,
      HInteractionLocation location,
      HInteractionAction... actions) {
    this.method = method;
    this.location = location;
    this.actions = new ArrayList<>(Arrays.asList(actions));
  }

  public HInteraction(HInteraction interaction) {
    this();
    this.method = new HInteractionMethod(interaction.method);
    this.location = new HInteractionLocation(interaction.location);
    for (HInteractionAction action : interaction.actions) {
      actions.add(new HInteractionAction(action));
    }
  }

  public boolean matches(HInteractionMethod method, DrawnItem drawnItem) {
    if (method != null && !this.method.equals(method)) {
      return false;
    }
    return location.matches(drawnItem);
  }
}
