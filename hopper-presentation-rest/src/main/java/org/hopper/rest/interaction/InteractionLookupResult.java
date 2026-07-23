package org.hopper.rest.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionMethod;

public class InteractionLookupResult {
  /** Was the lookup found? */
  private boolean found;

  /** The possible method that is required. */
  private HInteractionMethod method;

  /** The actions that need to happen */
  private List<HInteractionAction> actions;

  /** The drawn item that was found on a particular location */
  private DrawnItem drawnItem;

  public InteractionLookupResult() {
    this.actions = new ArrayList<>();
  }

  public String toJsonString() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsString(this);
  }

  /**
   * Gets found
   *
   * @return value of found
   */
  public boolean isFound() {
    return found;
  }

  /**
   * Sets found
   *
   * @param found value of found
   */
  public void setFound(boolean found) {
    this.found = found;
  }

  /**
   * Gets method
   *
   * @return value of method
   */
  public HInteractionMethod getMethod() {
    return method;
  }

  /**
   * Sets method
   *
   * @param method value of method
   */
  public void setMethod(HInteractionMethod method) {
    this.method = method;
  }

  /**
   * Gets actions
   *
   * @return value of actions
   */
  public List<HInteractionAction> getActions() {
    return actions;
  }

  /**
   * Sets actions
   *
   * @param actions value of actions
   */
  public void setActions(List<HInteractionAction> actions) {
    this.actions = actions;
  }

  /**
   * Gets drawnItem
   *
   * @return value of drawnItem
   */
  public DrawnItem getDrawnItem() {
    return drawnItem;
  }

  /**
   * Sets drawnItem
   *
   * @param drawnItem value of drawnItem
   */
  public void setDrawnItem(DrawnItem drawnItem) {
    this.drawnItem = drawnItem;
  }
}
