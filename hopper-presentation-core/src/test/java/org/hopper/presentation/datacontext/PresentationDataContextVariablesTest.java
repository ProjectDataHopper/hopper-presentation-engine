package org.hopper.presentation.datacontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;

class PresentationDataContextVariablesTest {

  @AfterEach
  void clearGlobal() {
    HGlobalVariables.clear();
  }

  @Test
  void inheritsExplicitParentVariables() {
    IVariables parent = new Variables();
    parent.setVariable("SYS_FOO", "from-parent");
    HPresentation presentation = new HPresentation();
    presentation.setName("p1");
    presentation.setDescription("d");

    PresentationDataContext ctx =
        new PresentationDataContext(presentation, new MemoryMetadataProvider(), parent);

    assertEquals("from-parent", ctx.getVariables().getVariable("SYS_FOO"));
    assertEquals(
        "p1",
        ctx.getVariables().getVariable(org.hopper.core.Constants.VARIABLE_PRESENTATION_NAME));
  }

  @Test
  void inheritsGlobalSystemVariablesWhenNoParentPassed() {
    IVariables shared = new Variables();
    shared.setVariable("GLOBAL_BAR", "shared-value");
    HGlobalVariables.set(shared);

    HPresentation presentation = new HPresentation();
    presentation.setName("p2");
    PresentationDataContext ctx =
        new PresentationDataContext(presentation, new MemoryMetadataProvider());

    assertEquals("shared-value", ctx.getVariables().getVariable("GLOBAL_BAR"));
  }

  @Test
  void noParentMeansNoSystemVar() {
    HGlobalVariables.clear();
    HPresentation presentation = new HPresentation();
    presentation.setName("p3");
    PresentationDataContext ctx =
        new PresentationDataContext(presentation, new MemoryMetadataProvider());
    assertNull(ctx.getVariables().getVariable("MISSING_SYS"));
  }
}
