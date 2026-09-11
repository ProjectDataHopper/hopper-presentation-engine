package org.hopper.presentation.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.core.HColumn;
import org.hopper.core.draw.DrawnContext;
import org.hopper.presentation.interaction.HInteractionAction.DimensionParameterMapping;
import org.hopper.presentation.variable.HParameter;
import org.junit.jupiter.api.Test;

class HInteractionActionCollectParametersTest {

  @Test
  void collectParametersMapsValueAndDimensions() {
    HInteractionAction action =
        new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, "Region report");
    action.setValueParameter("CELL");
    action.setDimensionParameters(
        List.of(
            new DimensionParameterMapping("region", "PARAM_REGION"),
            new DimensionParameterMapping("year", "PARAM_YEAR")));
    Map<String, String> vals = new LinkedHashMap<>();
    vals.put("region", "EMEA");
    vals.put("year", "2024");
    DrawnContext ctx =
        new DrawnContext(List.of(new HColumn("region"), new HColumn("year")), "123", vals);

    List<HParameter> params = action.collectParameters(ctx);
    assertEquals(3, params.size());
    assertEquals("CELL", params.get(0).getParameterName());
    assertEquals("123", params.get(0).getParameterValue());
    assertEquals("PARAM_REGION", params.get(1).getParameterName());
    assertEquals("EMEA", params.get(1).getParameterValue());
    assertEquals("2024", params.get(2).getParameterValue());
  }

  @Test
  void blankObjectNameUsesClickedValue() {
    HInteractionAction action =
        new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, null);
    DrawnContext ctx = new DrawnContext("Pipeline A");
    assertEquals("Pipeline A", action.resolveObjectName(ctx));
  }

  @Test
  void missingContextYieldsNoParameters() {
    HInteractionAction action =
        new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, "X");
    action.setValueParameter("CELL");
    assertTrue(action.collectParameters(null).isEmpty());
  }
}
