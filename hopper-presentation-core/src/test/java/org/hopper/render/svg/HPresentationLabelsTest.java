package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.LabelPresentationUtil;

public class HPresentationLabelsTest extends HPresentationTestBase {

  @Test
  public void testLabelsRender() throws Exception {
    HPresentation presentation =
        new LabelPresentationUtil(metadataProvider, variables).createLabelsPresentation(1100);
    testRendering(presentation, "labels_test");
  }
}
