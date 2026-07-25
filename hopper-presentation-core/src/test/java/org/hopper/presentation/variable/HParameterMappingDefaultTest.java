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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import org.apache.hop.core.variables.Variables;
import org.hopper.core.HJson;
import org.hopper.presentation.variable.HParameterMapping.FieldToParameterMapping;
import org.junit.jupiter.api.Test;

class HParameterMappingDefaultTest {

  @Test
  void applyDefaultsSetsWhenMissing() {
    HParameterMapping mapping = new HParameterMapping();
    mapping.setMappings(List.of(new FieldToParameterMapping("region", "REGION", "EMEA")));
    Variables vars = new Variables();
    assertEquals(1, mapping.applyDefaults(vars));
    assertEquals("EMEA", vars.getVariable("REGION"));
  }

  @Test
  void applyDefaultsDoesNotOverwriteExisting() {
    HParameterMapping mapping = new HParameterMapping();
    mapping.setMappings(List.of(new FieldToParameterMapping("region", "REGION", "EMEA")));
    Variables vars = new Variables();
    vars.setVariable("REGION", "APAC");
    assertEquals(0, mapping.applyDefaults(vars));
    assertEquals("APAC", vars.getVariable("REGION"));
  }

  @Test
  void applyDefaultsDoesNotOverwriteExplicitRequestParameter() {
    HParameterMapping mapping = new HParameterMapping();
    mapping.setMappings(List.of(new FieldToParameterMapping("region", "REGION", "EMEA")));
    Variables vars = new Variables();
    // Empty existing, but name is explicit from the layout caller
    assertEquals(0, mapping.applyDefaults(vars, Set.of("REGION")));
    assertNull(vars.getVariable("REGION"));
  }

  @Test
  void applyDefaultsSkipsBlankDefault() {
    HParameterMapping mapping = new HParameterMapping();
    mapping.setMappings(List.of(new FieldToParameterMapping("region", "REGION", "")));
    Variables vars = new Variables();
    assertEquals(0, mapping.applyDefaults(vars));
    assertNull(vars.getVariable("REGION"));
  }

  @Test
  void jsonRoundTripPreservesDefaultValue() throws Exception {
    HParameterMapping mapping = new HParameterMapping();
    mapping.setConnectorName("EDW Inventory PIT");
    mapping.setMappings(List.of(new FieldToParameterMapping("region", "REGION", "EMEA")));
    String json = HJson.createMapper().writeValueAsString(mapping);
    HParameterMapping back = HJson.createMapper().readValue(json, HParameterMapping.class);
    assertEquals("EMEA", back.getMappings().get(0).getDefaultValue());
    assertEquals("region", back.getMappings().get(0).getFieldName());
    assertEquals("REGION", back.getMappings().get(0).getParameterName());
  }

  @Test
  void copyConstructorCopiesDefaultValue() {
    FieldToParameterMapping src = new FieldToParameterMapping("f", "P", "dv");
    FieldToParameterMapping copy = new FieldToParameterMapping(src);
    assertEquals("dv", copy.getDefaultValue());
  }
}
