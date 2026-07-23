package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HAttachment;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;

import java.util.Arrays;

public class LineChartPresentationUtil extends BasePresentationUtil {

  public static final String LINE_CHART_NAME = "LineChart";

  public LineChartPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public static HLineChartComponent createColorRandomLineChart() {
    HLineChartComponent lineChart =
        new HLineChartComponent(BasePresentationUtil.CONNECTOR_SAMPLE_ROWS);
    lineChart.setHorizontalDimensions(
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
    lineChart.setFacts(Arrays.asList(sumFact));
    lineChart.setHorizontalMargin(10);
    lineChart.setVerticalMargin(10);
    lineChart.setBorder(true);
    lineChart.setBackground(false);
    // lineChart.setUsingZeroBaseline( true );

    lineChart.setTitle("Random by Color");

    return lineChart;
  }

  public static HLineChartComponent createNoLabelsTrendChart() {
    HLineChartComponent lineChart =
        new HLineChartComponent(BasePresentationUtil.CONNECTOR_SAMPLE_ROWS);

    HDimension nameDimension =
        new HDimension(
            "name", "Name", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE);
    lineChart.getHorizontalDimensions().add(nameDimension);
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
    lineChart.setFacts(Arrays.asList(sumFact));
    lineChart.setHorizontalMargin(10);
    lineChart.setVerticalMargin(10);
    lineChart.setBorder(true);
    lineChart.setBackground(false);
    lineChart.setShowingHorizontalLabels(false);
    lineChart.setShowingVerticalLabels(false);
    lineChart.setDotSize(0);
    lineChart.setShowingAxisTicks(false);
    lineChart.setHorizontalMargin(0);
    lineChart.setVerticalMargin(0);
    lineChart.setTitle("Trend");

    return lineChart;
  }

  public static HLineChartComponent createNoLabelsTrendChartDetailed() {
    HLineChartComponent lineChart = createNoLabelsTrendChart();
    HDimension importantDimension =
        new HDimension(
            "important", "Important", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE);
    HDimension colorDimension =
        new HDimension(
            "color", "Color", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE);

    lineChart.getHorizontalDimensions().add(importantDimension);
    lineChart.getHorizontalDimensions().add(colorDimension);

    lineChart.setTitle("Very detailed");

    return lineChart;
  }

  public HPresentation createLineChartPresentation(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Chart (" + nr + ")",
            "Chart " + nr + " description",
            1000,
            "Line chart filling the whole page");

    HLineChartComponent lineChart = createColorRandomLineChart();

    HComponent lineChartComponent = new HComponent(LINE_CHART_NAME, lineChart);
    lineChartComponent.setLayout(new HLayoutBuilder().all(5).build());

    // Add the table to the first page.
    //
    presentation.getPages().get(0).getComponents().add(lineChartComponent);

    return presentation;
  }

  public HPresentation createLineChartNoLabelsPresentation(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Chart no labels (" + nr + ")",
            "Chart no labels " + nr + " description",
            1000,
            "Line char without labels filling page");

    HLineChartComponent lineChart = createNoLabelsTrendChart();
    lineChart.setDrawingCurvedTrendLine(true);

    HComponent lineChartComponent = new HComponent(LINE_CHART_NAME, lineChart);
    lineChartComponent.setLayout(new HLayoutBuilder().all(5).build());

    // Add the table to the first page.
    //
    presentation.getPages().get(0).getComponents().add(lineChartComponent);

    return presentation;
  }

  public HPresentation createLineChartSeriesPresentation(int nr) throws Exception {
    HPresentation presentation = createLineChartPresentation(nr);

    HLineChartComponent lineChart =
        (HLineChartComponent)
            presentation.getPages().get(0).findComponent(LINE_CHART_NAME).getComponent();

    lineChart.setVerticalDimensions(
        Arrays.asList(
            new HDimension(
                "country",
                "Country",
                HHorizontalAlignment.CENTER,
                HVerticalAlignment.MIDDLE)));

    lineChart.setTitle("Random value by Country and Color");
    lineChart.setDrawingCurvedTrendLine(true);

    HLabelComponent label =
        (HLabelComponent)
            presentation
                .getHeader()
                .findComponent(BasePresentationUtil.HEADER_MESSAGE_LABEL)
                .getComponent();
    label.setLabel("Line chart with series filling whole page");
    return presentation;
  }
}
