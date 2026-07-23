package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.CrosstabPresentationUtil;

public class HPresentationCrosstabTest extends HPresentationTestBase {

  @Test
  public void testCrosstabRender() throws Exception {

    HPresentation presentation =
        new CrosstabPresentationUtil(metadataProvider, variables).createCrosstabPresentation(3000);
    testRendering(presentation, "crosstab_test");
  }
}
