package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.BarChartPresentationUtil;

public class HPresentationBarChartTest extends HPresentationTestBase {

  @Test
  public void testBarChartRender() throws Exception {

    HPresentation presentation =
        new BarChartPresentationUtil(metadataProvider, variables).createBarChartPresentation(4200);
    testRendering(presentation, "bar_chart_test");
  }
}
