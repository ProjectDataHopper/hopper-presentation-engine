package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.CrosstabPresentationUtil;

public class HPresentationCrosstabOnlyHorizontalDimensionsTest extends HPresentationTestBase {

  @Test
  public void testCrosstabRenderOnlyHorizontalDimensions() throws Exception {

    HPresentation presentation =
        new CrosstabPresentationUtil(metadataProvider, variables)
            .createCrosstabPresentationOnlyHorizontalDimensions(3200);
    testRendering(presentation, "crosstab_horizontals_test");
  }
}
