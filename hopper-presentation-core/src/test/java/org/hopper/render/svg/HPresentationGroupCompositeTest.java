package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.GroupCompositePresentationUtil;

public class HPresentationGroupCompositeTest extends HPresentationTestBase {

  @Test
  public void testGroupCompositeRender() throws Exception {

    HPresentation presentation =
        new GroupCompositePresentationUtil(metadataProvider, variables)
            .createGroupCompositePresentation(8000);
    testRendering(presentation, "grouped_composite_test");
  }
}
