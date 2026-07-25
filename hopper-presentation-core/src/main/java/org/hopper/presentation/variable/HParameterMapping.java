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
package org.hopper.presentation.variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
@NoArgsConstructor
public class HParameterMapping {
  @HopMetadataProperty private String connectorName;
  @HopMetadataProperty private List<FieldToParameterMapping> mappings = new ArrayList<>();
  @HopMetadataProperty private String separator;

  public HParameterMapping(HParameterMapping m) {
    this();
    this.connectorName = m.connectorName;
    if (m.mappings != null) {
      m.mappings.forEach(f -> this.mappings.add(new FieldToParameterMapping(f)));
    }
    this.separator = m.separator;
  }

  /**
   * Apply {@link FieldToParameterMapping#getDefaultValue()} for each mapping whose parameter is
   * missing or empty on {@code variables}. Does not overwrite request/interaction values listed in
   * {@code explicitParameterNames}. Call before connector field mapping so defaults can seed SQL
   * filters and editor preview; caller re-applies request params afterward so they always win.
   *
   * @param variables presentation variables
   * @param explicitParameterNames names supplied to layout (request/interaction); never overwritten
   * @return number of parameters that were set from defaults
   */
  public int applyDefaults(IVariables variables, Set<String> explicitParameterNames) {
    if (variables == null || mappings == null || mappings.isEmpty()) {
      return 0;
    }
    Set<String> explicit =
        explicitParameterNames != null ? explicitParameterNames : Collections.emptySet();
    int set = 0;
    for (FieldToParameterMapping mapping : mappings) {
      if (mapping == null || StringUtils.isEmpty(mapping.getDefaultValue())) {
        continue;
      }
      String parameterName = variables.resolve(mapping.getParameterName());
      if (StringUtils.isEmpty(parameterName) || explicit.contains(parameterName)) {
        continue;
      }
      String existing = variables.getVariable(parameterName);
      if (StringUtils.isEmpty(existing)) {
        variables.setVariable(parameterName, variables.resolve(mapping.getDefaultValue()));
        set++;
      }
    }
    return set;
  }

  /** @see #applyDefaults(IVariables, Set) */
  public int applyDefaults(IVariables variables) {
    return applyDefaults(variables, Collections.emptySet());
  }

  @Data
  @NoArgsConstructor
  public static final class FieldToParameterMapping {
    @HopMetadataProperty private String fieldName;
    @HopMetadataProperty private String parameterName;
    /**
     * Optional preview value when the parameter is not provided by the layout caller. Used for
     * authoring (labels show a real value instead of {@code ${PARAM}}). Overwritten at layout when
     * request/interaction parameters are supplied; multi-row connector mapping with a blank
     * separator does not invent a concatenated value.
     */
    @HopMetadataProperty private String defaultValue;

    public FieldToParameterMapping(String fieldName, String parameterName) {
      this(fieldName, parameterName, null);
    }

    public FieldToParameterMapping(String fieldName, String parameterName, String defaultValue) {
      this.fieldName = fieldName;
      this.parameterName = parameterName;
      this.defaultValue = defaultValue;
    }

    public FieldToParameterMapping(FieldToParameterMapping m) {
      this.fieldName = m.fieldName;
      this.parameterName = m.parameterName;
      this.defaultValue = m.defaultValue;
    }
  }
}
