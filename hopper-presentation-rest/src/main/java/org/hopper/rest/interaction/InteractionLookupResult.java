package org.hopper.rest.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionMethod;

public class InteractionLookupResult {
  /** Was any interaction found? */
  private boolean found;

  /**
   * Primary method for click clients: first click match if any, else first match.
   */
  private HInteractionMethod method;

  /** Actions for {@link #method}. */
  private List<HInteractionAction> actions;

  /** Outline / hit item for highlight. */
  private DrawnItem drawnItem;

  /** All method/action sets for this hit (hover + click can both be present). */
  private List<InteractionMatch> matches;

  public InteractionLookupResult() {
    this.actions = new ArrayList<>();
    this.matches = new ArrayList<>();
  }

  public String toJsonString() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsString(this);
  }

  public boolean isFound() {
    return found;
  }

  public void setFound(boolean found) {
    this.found = found;
  }

  public HInteractionMethod getMethod() {
    return method;
  }

  public void setMethod(HInteractionMethod method) {
    this.method = method;
  }

  public List<HInteractionAction> getActions() {
    return actions;
  }

  public void setActions(List<HInteractionAction> actions) {
    this.actions = actions;
  }

  public DrawnItem getDrawnItem() {
    return drawnItem;
  }

  public void setDrawnItem(DrawnItem drawnItem) {
    this.drawnItem = drawnItem;
  }

  public List<InteractionMatch> getMatches() {
    return matches;
  }

  public void setMatches(List<InteractionMatch> matches) {
    this.matches = matches;
  }

  public static class InteractionMatch {
    private HInteractionMethod method;
    private List<HInteractionAction> actions;

    public InteractionMatch() {
      this.actions = new ArrayList<>();
    }

    public InteractionMatch(HInteractionMethod method, List<HInteractionAction> actions) {
      this.method = method;
      this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public HInteractionMethod getMethod() {
      return method;
    }

    public void setMethod(HInteractionMethod method) {
      this.method = method;
    }

    public List<HInteractionAction> getActions() {
      return actions;
    }

    public void setActions(List<HInteractionAction> actions) {
      this.actions = actions;
    }
  }
}
