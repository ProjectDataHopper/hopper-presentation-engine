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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.core.HJson;
import org.hopper.core.draw.DrawnContext;
import org.hopper.presentation.interaction.HInteractionAction.DimensionParameterMapping;
import org.junit.jupiter.api.Test;

class HInteractionActionDimensionTest {

  @Test
  void copyConstructorCopiesDimensionParameters() {
    HInteractionAction src =
        new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, "Region report");
    src.setValueParameter("CELL_VALUE");
    src.getDimensionParameters()
        .add(new DimensionParameterMapping("region", "PARAM_REGION"));
    src.getDimensionParameters().add(new DimensionParameterMapping("year", "PARAM_YEAR"));

    HInteractionAction copy = new HInteractionAction(src);
    assertEquals(2, copy.getDimensionParameters().size());
    assertEquals("region", copy.getDimensionParameters().get(0).getDimensionColumn());
    assertEquals("PARAM_REGION", copy.getDimensionParameters().get(0).getParameterName());
    assertEquals("CELL_VALUE", copy.getValueParameter());
  }

  @Test
  void jsonRoundTripPreservesDimensionParameters() throws Exception {
    HInteractionAction src =
        new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, "Region report");
    src.setValueParameter("FACT");
    src.setDimensionParameters(
        List.of(
            new DimensionParameterMapping("region", "PARAM_REGION"),
            new DimensionParameterMapping("year", "PARAM_YEAR")));

    String json = HJson.createMapper().writeValueAsString(src);
    HInteractionAction back = HJson.createMapper().readValue(json, HInteractionAction.class);
    assertNotNull(back.getDimensionParameters());
    assertEquals(2, back.getDimensionParameters().size());
    assertEquals("year", back.getDimensionParameters().get(1).getDimensionColumn());
    assertEquals("PARAM_YEAR", back.getDimensionParameters().get(1).getParameterName());
  }

  @Test
  void drawnContextStoresDimensionValues() throws Exception {
    Map<String, String> vals = new LinkedHashMap<>();
    vals.put("region", "EMEA");
    vals.put("year", "2024");
    DrawnContext ctx = new DrawnContext(List.of(), "123", vals);
    assertEquals("EMEA", ctx.getDimensionValue("region"));
    assertEquals("2024", ctx.getDimensionValue("year"));
    assertEquals("123", ctx.getValue());

    String json = HJson.createMapper().writeValueAsString(ctx);
    DrawnContext back = HJson.createMapper().readValue(json, DrawnContext.class);
    assertEquals("EMEA", back.getDimensionValue("region"));
  }

  @Test
  void drawnContextSeedsDimensionValuesForSingleDimension() {
    org.hopper.core.HColumn company = new org.hopper.core.HColumn("company_name");
    DrawnContext ctx = new DrawnContext(List.of(company), "Apex Maritime Global");
    assertEquals("Apex Maritime Global", ctx.getValue());
    assertEquals("Apex Maritime Global", ctx.getDimensionValue("company_name"));
  }

  @Test
  void mapDimensionValuesPairsColumnsToCombination() {
    org.hopper.core.HColumn a = new org.hopper.core.HColumn("company_name");
    org.hopper.core.HColumn b = new org.hopper.core.HColumn("region");
    Map<String, String> map =
        DrawnContext.mapDimensionValues(List.of(a, b), List.of("Apex", "EMEA"));
    assertEquals("Apex", map.get("company_name"));
    assertEquals("EMEA", map.get("region"));
  }
}
