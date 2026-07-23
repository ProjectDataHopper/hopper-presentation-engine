package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.LineChartPresentationUtil;

public class HPresentationLineChartSeriesTest extends HPresentationTestBase {

  @Test
  public void testLineChartSeriesRender() throws Exception {

    HPresentation presentation =
        new LineChartPresentationUtil(metadataProvider, variables)
            .createLineChartSeriesPresentation(4100);
    testRendering(presentation, "line_chart_series_test");
  }
}
