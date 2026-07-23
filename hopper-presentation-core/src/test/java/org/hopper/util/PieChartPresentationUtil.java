package org.hopper.util;

import java.util.Arrays;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HPieChartComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayoutBuilder;

public class PieChartPresentationUtil extends BasePresentationUtil {

  public static final String PIE_CHART_NAME = "PieChart";

  public PieChartPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createPieChartPresentation(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "PieChart (" + nr + ")",
            "PieChart " + nr + " description",
            1000,
            "Pie chart filling whole page");

    HPieChartComponent pieChart = createColorRandomPieChart();

    HComponent pieChartComponent = new HComponent(PIE_CHART_NAME, pieChart);
    pieChartComponent.setLayout(new HLayoutBuilder().all(5).build());

    presentation.getPages().get(0).getComponents().add(pieChartComponent);

    return presentation;
  }

  public HPresentation createDonutChartPresentation(int nr) throws Exception {
    HPresentation presentation = createPieChartPresentation(nr);

    HPieChartComponent chart =
        (HPieChartComponent)
            presentation.getPages().get(0).findComponent(PIE_CHART_NAME).getComponent();

    chart.setInnerRadiusPercent("50");
    chart.setLegendPosition("BOTTOM");
    chart.setTitle("Random value by Color (donut)");
    chart.setShowingFactValues(true);

    HLabelComponent label =
        (HLabelComponent)
            presentation.getHeader().findComponent(HEADER_MESSAGE_LABEL).getComponent();
    label.setLabel("Donut chart filling the whole page");
    return presentation;
  }

  public HPieChartComponent createColorRandomPieChart() {
    HPieChartComponent chart = new HPieChartComponent(CONNECTOR_SAMPLE_ROWS);
    chart.setHorizontalDimensions(
        Arrays.asList(
            new HDimension(
                "color", "Color", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE)));
    HFact sumFact =
        new HFact(
            "random",
            "Sum",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.MIDDLE,
            AggregationMethod.SUM,
            "0.000");
    sumFact.setFormatMask("0.00");
    chart.setFacts(Arrays.asList(sumFact));
    chart.setHorizontalMargin(10);
    chart.setVerticalMargin(10);
    chart.setBorder(true);
    chart.setBackground(false);
    chart.setTitle("Random by Color");
    chart.setShowingLegend(true);
    chart.setLegendPosition("RIGHT");
    chart.setShowingSliceLabels(true);
    chart.setShowingPercentages(true);
    chart.setShowingFactValues(false);
    chart.setInnerRadiusPercent("0");
    chart.setStartAngleDegrees("-90");
    chart.setClockwise(true);

    return chart;
  }
}
