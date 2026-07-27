package org.hopper.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.variable.HParameter;
import org.hopper.presentation.variable.HParameterDefinition;

/**
 * Previews must seed presentation parameter defaults so filters like {@code ${SHIP_NAME}} resolve.
 */
class HPresentationParameterPreviewTest {

  @Test
  void applyParametersToDataContext_appliesDefinitionDefaults() throws Exception {
    HPresentation p = new HPresentation();
    p.setName("Ship Operational Status");
    p.setParameters(
        List.of(new HParameterDefinition("SHIP_NAME", "Vessel name filter", "Apex Voyager")));

    PresentationDataContext ctx =
        new PresentationDataContext(p, new MemoryMetadataProvider());
    p.applyParametersToDataContext(ctx, List.of());

    assertEquals("Apex Voyager", ctx.getVariables().getVariable("SHIP_NAME"));
  }

  @Test
  void applyParametersToDataContext_requestWinsOverDefault() throws Exception {
    HPresentation p = new HPresentation();
    p.setName("Ship Operational Status");
    p.setParameters(
        List.of(new HParameterDefinition("SHIP_NAME", "Vessel name filter", "Apex Voyager")));

    PresentationDataContext ctx =
        new PresentationDataContext(p, new MemoryMetadataProvider());
    p.applyParametersToDataContext(
        ctx, List.of(new HParameter("SHIP_NAME", "Oceanic Majesty")));

    assertEquals("Oceanic Majesty", ctx.getVariables().getVariable("SHIP_NAME"));
  }

  @Test
  void applyParametersToDataContext_emptyWhenNoDefinitions() throws Exception {
    HPresentation p = new HPresentation();
    p.setName("plain");
    PresentationDataContext ctx =
        new PresentationDataContext(p, new MemoryMetadataProvider());
    p.applyParametersToDataContext(ctx, null);
    String v = ctx.getVariables().getVariable("SHIP_NAME");
    assertTrue(v == null || v.isEmpty());
  }
}
