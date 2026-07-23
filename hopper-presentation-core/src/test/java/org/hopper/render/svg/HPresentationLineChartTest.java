package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.LineChartPresentationUtil;

public class HPresentationLineChartTest extends HPresentationTestBase {

  @Test
  public void testLineChartRender() throws Exception {

    HPresentation presentation =
        new LineChartPresentationUtil(metadataProvider, variables)
            .createLineChartPresentation(4000);
    testRendering(presentation, "line_chart_test");
  }
}
