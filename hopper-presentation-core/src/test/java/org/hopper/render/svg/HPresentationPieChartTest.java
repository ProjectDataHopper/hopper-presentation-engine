package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.PieChartPresentationUtil;

public class HPresentationPieChartTest extends HPresentationTestBase {

  @Test
  public void testPieChartRender() throws Exception {
    HPresentation presentation =
        new PieChartPresentationUtil(metadataProvider, variables).createPieChartPresentation(4300);
    testRendering(presentation, "pie_chart_test");
  }

  @Test
  public void testDonutChartRender() throws Exception {
    HPresentation presentation =
        new PieChartPresentationUtil(metadataProvider, variables).createDonutChartPresentation(4301);
    testRendering(presentation, "donut_chart_test");
  }
}
