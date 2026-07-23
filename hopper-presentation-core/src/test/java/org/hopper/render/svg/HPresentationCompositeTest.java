package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.CompositePresentationUtil;

public class HPresentationCompositeTest extends HPresentationTestBase {

  @Test
  public void testCompositeRender() throws Exception {

    HPresentation presentation =
        new CompositePresentationUtil(metadataProvider, variables)
            .createSimpleCompositePresentation(8000);
    testRendering(presentation, "composite_test");
  }
}
