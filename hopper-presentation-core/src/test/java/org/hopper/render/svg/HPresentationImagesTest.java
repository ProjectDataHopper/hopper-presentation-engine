package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.ImagesPresentationUtil;

public class HPresentationImagesTest extends HPresentationTestBase {

  @Test
  public void testImagesRender() throws Exception {

    HPresentation presentation =
        new ImagesPresentationUtil(metadataProvider, variables).createImagesPresentation(10000);
    testRendering(presentation, "images_test");
  }
}
