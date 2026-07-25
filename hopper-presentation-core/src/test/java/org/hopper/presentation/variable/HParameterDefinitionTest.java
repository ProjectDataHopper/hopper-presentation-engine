package org.hopper.presentation.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;
import org.hopper.core.HJson;
import org.hopper.presentation.HPresentation;

class HParameterDefinitionTest {

  @Test
  void applyDefaultsSetsWhenMissing() {
    HPresentation p = new HPresentation();
    p.setParameters(
        List.of(new HParameterDefinition("REGION", "Sales region code", "EMEA")));
    Variables vars = new Variables();
    assertEquals(1, p.applyPresentationParameterDefaults(vars));
    assertEquals("EMEA", vars.getVariable("REGION"));
  }

  @Test
  void applyDefaultsDoesNotOverwriteExisting() {
    HPresentation p = new HPresentation();
    p.setParameters(List.of(new HParameterDefinition("REGION", null, "EMEA")));
    Variables vars = new Variables();
    vars.setVariable("REGION", "APAC");
    assertEquals(0, p.applyPresentationParameterDefaults(vars));
    assertEquals("APAC", vars.getVariable("REGION"));
  }

  @Test
  void applyDefaultsSkipsExplicitRequestNames() {
    HPresentation p = new HPresentation();
    p.setParameters(List.of(new HParameterDefinition("REGION", null, "EMEA")));
    Variables vars = new Variables();
    assertEquals(0, p.applyPresentationParameterDefaults(vars, Set.of("REGION")));
    assertEquals(null, vars.getVariable("REGION"));
  }

  @Test
  void applyDefaultsResolvesVariablesInDefault() {
    HPresentation p = new HPresentation();
    p.setParameters(
        List.of(new HParameterDefinition("FULL", null, "prefix-${BASE}")));
    Variables vars = new Variables();
    vars.setVariable("BASE", "x");
    assertEquals(1, p.applyPresentationParameterDefaults(vars));
    assertEquals("prefix-x", vars.getVariable("FULL"));
  }

  @Test
  void jsonRoundTrip() throws Exception {
    HPresentation p = new HPresentation();
    p.setName("param-demo");
    p.setParameters(
        List.of(new HParameterDefinition("REGION", "Region filter", "EMEA")));
    String json = p.toJsonString(true);
    HPresentation back = HPresentation.fromJsonString(json);
    assertEquals(1, back.getParameters().size());
    assertEquals("REGION", back.getParameters().get(0).getName());
    assertEquals("Region filter", back.getParameters().get(0).getDescription());
    assertEquals("EMEA", back.getParameters().get(0).getDefaultValue());
    assertTrue(back.listParameterDefinitionNames().contains("REGION"));
  }

  @Test
  void copyConstructorCopiesDefinitions() {
    HPresentation p = new HPresentation();
    p.setParameters(List.of(new HParameterDefinition("A", "desc", "1")));
    HPresentation copy = new HPresentation(p);
    assertEquals(1, copy.getParameters().size());
    assertEquals("A", copy.getParameters().get(0).getName());
    assertEquals("desc", copy.getParameters().get(0).getDescription());
  }

  @Test
  void jacksonRoundTripDefinition() throws Exception {
    HParameterDefinition def = new HParameterDefinition("X", "help", "dv");
    String json = HJson.createMapper().writeValueAsString(def);
    HParameterDefinition back =
        HJson.createMapper().readValue(json, HParameterDefinition.class);
    assertEquals("X", back.getName());
    assertEquals("help", back.getDescription());
    assertEquals("dv", back.getDefaultValue());
  }
}
