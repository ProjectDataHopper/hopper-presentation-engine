package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HBarChartComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;

import java.util.Arrays;

public class BarChartPresentationUtil extends BasePresentationUtil {

  public static final String LINE_CHART_NAME = "BarChart";

  public BarChartPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createBarChartPresentation(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "BarChart (" + nr + ")",
            "BarChart " + nr + " description",
            1000,
            "Bar chart filling whole page");

    HBarChartComponent lineChart = createColorRandomBarChart();

    HComponent lineChartComponent = new HComponent(LINE_CHART_NAME, lineChart);
    lineChartComponent.setLayout(new HLayoutBuilder().all(5).build());

    // Add the chart to the first page.
    //
    presentation.getPages().get(0).getComponents().add(lineChartComponent);

    return presentation;
  }

  public HPresentation createStackedBarChartPresentation(int nr) throws Exception {
    HPresentation presentation = createBarChartPresentation(nr);

    HBarChartComponent chart =
        (HBarChartComponent)
            presentation.getPages().get(0).findComponent(LINE_CHART_NAME).getComponent();

    chart.setVerticalDimensions(
        Arrays.asList(
            new HDimension(
                "country",
                "Country",
                HHorizontalAlignment.CENTER,
                HVerticalAlignment.MIDDLE)));
    chart.setTitle("Random value by Country and Color");

    // Change the message label in the header
    //
    HLabelComponent label =
        (HLabelComponent)
            presentation.getHeader().findComponent(HEADER_MESSAGE_LABEL).getComponent();
    label.setLabel("Stacked bar chart filling the whole page");
    return presentation;
  }

  public HBarChartComponent createColorRandomBarChart() {
    HBarChartComponent chart = new HBarChartComponent(CONNECTOR_SAMPLE_ROWS);
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
    sumFact.setHorizontalAggregation(true);
    sumFact.setHorizontalAggregationHeader("Total Sum");
    sumFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    sumFact.setHeaderVerticalAlignment(HVerticalAlignment.MIDDLE);
    sumFact.setFormatMask("0.00");
    chart.setFacts(Arrays.asList(sumFact));
    chart.setHorizontalMargin(10);
    chart.setVerticalMargin(10);
    chart.setBorder(true);
    chart.setBackground(false);
    chart.setTitle("Random by Color");
    chart.setShowingLegend(true);
    chart.setWidthPercentage("60"); // % of the width allocated for the horizontal value
    chart.setShowingFactValues(true);

    return chart;
  }
}
