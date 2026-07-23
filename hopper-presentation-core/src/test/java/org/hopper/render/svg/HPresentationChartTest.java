package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.LineChartPresentationUtil;

public class HPresentationChartTest extends HPresentationTestBase {

  @Test
  public void testChartRender() throws Exception {

    HPresentation presentation =
        new LineChartPresentationUtil(metadataProvider, variables)
            .createLineChartPresentation(5000);
    testRendering(presentation, "chart_test");
  }

  @Test
  public void testChartNoLabelsRender() throws Exception {

    HPresentation presentation =
        new LineChartPresentationUtil(metadataProvider, variables)
            .createLineChartNoLabelsPresentation(5100);
    testRendering(presentation, "chart_trend_test");
  }
}
