package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.BarChartPresentationUtil;

public class HPresentationStackedBarChartTest extends HPresentationTestBase {

  @Test
  public void testBarChartRender() throws Exception {

    HPresentation presentation =
        new BarChartPresentationUtil(metadataProvider, variables)
            .createStackedBarChartPresentation(4300);
    testRendering(presentation, "bar_chart_stacked_test");
  }
}
