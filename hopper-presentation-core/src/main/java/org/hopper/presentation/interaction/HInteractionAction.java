package org.hopper.presentation.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** This describes an action that can be taken by a user on a presentation. */
@Getter
@Setter
@NoArgsConstructor
public class HInteractionAction {

  public enum ActionType {
    /**
     * Open the presentation with the name either in the object name (static value) or take the name
     * from the value clicked on. In either case you can also set this string value where you
     * clicked on as a parameter.
     */
    OPEN_PRESENTATION,

    /** Open a web link in the same tab. */
    OPEN_LINK_SAME_TAB,

    /** Open a web link in a new tab */
    OPEN_LINK_NEW_TAB,
  }

  @HopMetadataProperty private ActionType actionType;
  @HopMetadataProperty private String objectName;
  @HopMetadataProperty private String valueParameter;

  public HInteractionAction(ActionType actionType) {
    this(actionType, null);
  }

  public HInteractionAction(ActionType actionType, String objectName) {
    this.actionType = actionType;
    this.objectName = objectName;
  }

  public HInteractionAction(HInteractionAction action) {
    this.actionType = action.actionType;
    this.objectName = action.objectName;
    this.valueParameter = action.valueParameter;
  }

  public String toJsonString() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsString(this);
  }
}
