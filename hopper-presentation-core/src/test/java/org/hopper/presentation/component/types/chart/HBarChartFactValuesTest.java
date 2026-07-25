package org.hopper.presentation.component.types.chart;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.BarChartPresentationUtil;
import org.hopper.util.BasePresentationUtil;

/**
 * {@code showingFactValues} must paint formatted fact strings on/above bars — the checkbox was
 * previously stored but never applied during bar render.
 */
class HBarChartFactValuesTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void showingFactValues_drawsFactLabelsInSvg() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    BarChartPresentationUtil util =
        new BarChartPresentationUtil(provider, Variables.getADefaultVariableSpace());
    HPresentation presentation = util.createBarChartPresentation(50);

    HComponent chartWrapper = presentation.getPages().get(0).findComponent("BarChart");
    HBarChartComponent chart = (HBarChartComponent) chartWrapper.getComponent();
    assertTrue(chart.isShowingFactValues(), "fixture should enable showingFactValues");

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, provider);
    HLayoutResults layout =
        presentation.doLayout(
            new LoggingObject("bar-fact-values"), renderContext, provider, List.of());
    presentation.render(layout, provider);

    assertFalse(layout.getRenderPages().isEmpty());
    HRenderPage page = layout.getRenderPages().get(0);
    String svg = page.getSvgXml();
    assertTrue(svg != null && !svg.isBlank());

    // Formatted fact labels use mask "0.00". Category ChartLabel items are color names only.
    long factLikeLabels =
        page.getDrawnItems().stream()
            .filter(d -> "ChartLabel".equals(d.getCategory()))
            .filter(d -> d.getContext() != null && d.getContext().getValue() != null)
            .filter(d -> d.getContext().getValue().matches(".*\\d+\\.\\d+.*"))
            .count();
    assertTrue(
        factLikeLabels > 0,
        "expected ChartLabel drawn items with formatted fact values when showingFactValues=true");

    assertTrue(
        svg.matches("(?s).*\\d+\\.\\d+.*"),
        "SVG should contain formatted fact values (0.00 mask)");
  }

  @Test
  void showingFactValues_false_doesNotDrawFactChartLabels() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    BarChartPresentationUtil util =
        new BarChartPresentationUtil(provider, Variables.getADefaultVariableSpace());
    HPresentation presentation = util.createBarChartPresentation(30);

    HComponent chartWrapper = presentation.getPages().get(0).findComponent("BarChart");
    HBarChartComponent chart = (HBarChartComponent) chartWrapper.getComponent();
    chart.setShowingFactValues(false);

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, provider);
    HLayoutResults layout =
        presentation.doLayout(
            new LoggingObject("bar-fact-values-off"), renderContext, provider, List.of());
    presentation.render(layout, provider);

    HRenderPage page = layout.getRenderPages().get(0);
    long factLikeLabels =
        page.getDrawnItems().stream()
            .filter(d -> "ChartLabel".equals(d.getCategory()))
            .filter(d -> d.getContext() != null && d.getContext().getValue() != null)
            .filter(d -> d.getContext().getValue().matches(".*\\d+\\.\\d+.*"))
            .count();
    assertTrue(
        factLikeLabels == 0,
        "with showingFactValues=false, ChartLabel items should be category keys only, not fact numbers");
  }
}
