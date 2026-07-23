package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.GroupPresentationUtil;

public class HPresentationGroupTest extends HPresentationTestBase {

  @Test
  public void testGroupRender() throws Exception {

    HPresentation presentation =
        new GroupPresentationUtil(metadataProvider, variables)
            .createSimpleGroupedLabelPresentation(7000);
    testRendering(presentation, "group_test");
  }
}
