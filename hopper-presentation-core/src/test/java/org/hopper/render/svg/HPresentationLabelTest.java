package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.LabelPresentationUtil;

public class HPresentationLabelTest extends HPresentationTestBase {

  @Test
  public void testLabelRender() throws Exception {

    HPresentation presentation =
        new LabelPresentationUtil(metadataProvider, variables).createLabelPresentation(1000);
    testRendering(presentation, "label_test");
  }
}
