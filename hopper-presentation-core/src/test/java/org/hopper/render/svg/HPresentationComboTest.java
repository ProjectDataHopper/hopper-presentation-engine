package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.ComboPresentationUtil;

public class HPresentationComboTest extends HPresentationTestBase {

  @Test
  public void testComboRender() throws Exception {

    HPresentation presentation =
        new ComboPresentationUtil(metadataProvider, variables).createComboPresentation(3000);
    testRendering(presentation, "combo_test");
  }
}
