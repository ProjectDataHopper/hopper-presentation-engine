package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.CrosstabPresentationUtil;

public class HPresentationCrosstabOnlyVerticalDimensionsTest extends HPresentationTestBase {

  @Test
  public void testCrosstabRenderOnlyVerticalDimensions() throws Exception {

    HPresentation presentation =
        new CrosstabPresentationUtil(metadataProvider, variables)
            .createCrosstabPresentationOnlyVerticalDimensions(3100);
    testRendering(presentation, "crosstab_verticals_test");
  }
}
