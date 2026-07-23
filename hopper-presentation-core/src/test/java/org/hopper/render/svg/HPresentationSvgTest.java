package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.ImagesPresentationUtil;
import org.hopper.util.SvgPresentationUtil;

public class HPresentationSvgTest extends HPresentationTestBase {

  @Test
  public void testSvgRender() throws Exception {

    HPresentation presentation =
        new SvgPresentationUtil(metadataProvider, variables).createSvgPresentation(10500);
    testRendering(presentation, "svg_test");
  }
}
