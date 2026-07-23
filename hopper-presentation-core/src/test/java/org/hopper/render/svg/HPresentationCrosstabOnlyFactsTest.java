package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.CrosstabPresentationUtil;

public class HPresentationCrosstabOnlyFactsTest extends HPresentationTestBase {

  @Test
  public void testCrosstabRenderOnlyFacts() throws Exception {

    HPresentation presentation =
        new CrosstabPresentationUtil(metadataProvider, variables)
            .createCrosstabPresentationOnlyFacts(3300);
    testRendering(presentation, "crosstab_only_facts");
  }
}
