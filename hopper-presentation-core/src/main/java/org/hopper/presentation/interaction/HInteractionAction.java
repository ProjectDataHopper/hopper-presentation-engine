/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.presentation.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
     * clicked on as a parameter, and optionally map dimension columns from the hit context to
     * additional parameters.
     */
    OPEN_PRESENTATION,

    /** Open a web link in the same tab. */
    OPEN_LINK_SAME_TAB,

    /** Open a web link in a new tab */
    OPEN_LINK_NEW_TAB,
  }

  @HopMetadataProperty private ActionType actionType;
  @HopMetadataProperty private String objectName;
  /** Parameter name to receive the clicked item's primary value ({@code DrawnContext.value}). */
  @HopMetadataProperty private String valueParameter;

  /**
   * Map dimension columns from the click context (e.g. region, year) to parameters on the target
   * presentation. Values come from {@code DrawnContext.dimensionValues}.
   */
  @HopMetadataProperty private List<DimensionParameterMapping> dimensionParameters = new ArrayList<>();

  public HInteractionAction(ActionType actionType) {
    this(actionType, null);
  }

  public HInteractionAction(ActionType actionType, String objectName) {
    this.actionType = actionType;
    this.objectName = objectName;
    this.dimensionParameters = new ArrayList<>();
  }

  public HInteractionAction(HInteractionAction action) {
    this.actionType = action.actionType;
    this.objectName = action.objectName;
    this.valueParameter = action.valueParameter;
    this.dimensionParameters = new ArrayList<>();
    if (action.dimensionParameters != null) {
      for (DimensionParameterMapping m : action.dimensionParameters) {
        if (m != null) {
          this.dimensionParameters.add(new DimensionParameterMapping(m));
        }
      }
    }
  }

  public String toJsonString() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsString(this);
  }

  /** One dimension column name → target presentation parameter name. */
  @Getter
  @Setter
  @NoArgsConstructor
  public static class DimensionParameterMapping {
    @HopMetadataProperty private String dimensionColumn;
    @HopMetadataProperty private String parameterName;

    public DimensionParameterMapping(String dimensionColumn, String parameterName) {
      this.dimensionColumn = dimensionColumn;
      this.parameterName = parameterName;
    }

    public DimensionParameterMapping(DimensionParameterMapping m) {
      this.dimensionColumn = m.dimensionColumn;
      this.parameterName = m.parameterName;
    }
  }
}
