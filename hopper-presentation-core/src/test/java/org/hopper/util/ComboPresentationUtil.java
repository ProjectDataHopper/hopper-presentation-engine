package org.hopper.util;

import java.util.Arrays;
import java.util.Collections;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HAttachment;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.component.types.crosstab.HCrosstabComponent;
import org.hopper.presentation.component.types.svg.HSvgComponent;
import org.hopper.presentation.component.types.svg.ScaleType;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

public class ComboPresentationUtil extends BasePresentationUtil {

  public ComboPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createComboPresentation(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Combo (" + nr + ")",
            "Combo " + nr + " description",
            1000,
            "Layout test with charts basing position off crosstab");

    HPage pageOne = presentation.getPages().get(0);

    // Add a crosstab at the top left of the page.
    // This component is dynamically sized
    //
    {
      HCrosstabComponent crosstab = new HCrosstabComponent(CONNECTOR_SAMPLE_ROWS);
      crosstab.setHorizontalDimensions(
          Arrays.asList(
              new HDimension(
                  "color", "Color", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE),
              new HDimension(
                  "important", "?", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE)));
      crosstab.setVerticalDimensions(
          Arrays.asList(
              new HDimension(
                  "name",
                  "Customer",
                  HHorizontalAlignment.RIGHT,
                  HVerticalAlignment.MIDDLE)));
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
      HFact countFact =
          new HFact(
              "name",
              "Count",
              HHorizontalAlignment.RIGHT,
              HVerticalAlignment.MIDDLE,
              AggregationMethod.COUNT,
              "0");
      countFact.setHorizontalAggregation(true);
      countFact.setHorizontalAggregationHeader("Total Count");
      countFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
      countFact.setHeaderVerticalAlignment(HVerticalAlignment.MIDDLE);
      crosstab.setFacts(Arrays.asList(sumFact, countFact));
      crosstab.setBackground(false);
      crosstab.setBorder(false);
      crosstab.setHorizontalMargin(3);
      crosstab.setVerticalMargin(2);
      crosstab.setEvenHeights(true);
      crosstab.setHeaderOnEveryPage(true);
      crosstab.setShowingVerticalTotals(true);
      crosstab.setShowingHorizontalTotals(true);
      HComponent crosstabComponent = new HComponent("Crosstab", crosstab);
      crosstabComponent.setLayout(new HLayout(0, 0));

      // Add the table to the first page.
      //
      pageOne.getComponents().add(crosstabComponent);
    }

    // Add a chart below the crosstab
    //
    {
      HLineChartComponent lineChart = LineChartPresentationUtil.createColorRandomLineChart();

      lineChart.setVerticalDimensions(
          Arrays.asList(
              new HDimension(
                  "country",
                  "Country",
                  HHorizontalAlignment.CENTER,
                  HVerticalAlignment.MIDDLE)));
      lineChart.setDrawingCurvedTrendLine(true);

      HComponent lineChartComponent = new HComponent("LineChart", lineChart);
      HLayout chartLayout =
          new HLayoutBuilder()
              .left()
              .topFromBottom("Crosstab", 0, 0)
              .bottom(0)
              .rightFromRight("Crosstab", 0, 0)
              .build();
      lineChartComponent.setLayout(chartLayout);

      // Add the table to the first page.
      //
      pageOne.getComponents().add(lineChartComponent);
    }

    // a detailed trend chart top-right
    {
      HLineChartComponent trendChart =
          LineChartPresentationUtil.createNoLabelsTrendChartDetailed();
      trendChart.setDrawingCurvedTrendLine(true);
      HComponent trendChartComponent = new HComponent("TrendChartDetailed", trendChart);
      // Setting imageSize forces chart on next page, comment out Right/Bottom attachments
      // lineChartComponent.setSize( new HSize( 600, 600 ) );
      HLayout trendChartLayout =
          new HLayoutBuilder()
              .beside("Crosstab", 5)
              .right()
              .bottom(new HAttachment("Crosstab", 0, 0, HAttachment.Alignment.BOTTOM))
              .build();
      trendChartComponent.setLayout(trendChartLayout);

      pageOne.getComponents().add(trendChartComponent);
    }

    // Add a rotated LEAN logo in the background
    //
    {
      HSvgComponent rotatedLabel = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MAX);
      rotatedLabel.setBorder(false);
      HComponent imageComponent = new HComponent("Logo", rotatedLabel);
      imageComponent.setLayout(new HLayoutBuilder().top().right().bottom().build());

      HComponent labelComponent = new HComponent("RotatedLabel", rotatedLabel);
      HLayout labelLayout =
          new HLayoutBuilder().leftFromRight("Crosstab", 0, 15).right(-15).bottom(-35).build();
      labelComponent.setLayout(labelLayout);
      labelComponent.setRotation("15");
      pageOne.getComponents().add(labelComponent);
    }

    // Add test interactions...
    //
    presentation
        .getInteractions()
        .add(
            new HInteraction(
                new HInteractionMethod(true, false),
                new HInteractionLocation(
                    "LineChart",
                    null,
                    DrawnItem.DrawnItemType.ComponentItem.name(),
                    DrawnItem.Category.ChartSeriesLabel.name(),
                    Collections.emptyList()),
                new HInteractionAction(
                    HInteractionAction.ActionType.OPEN_PRESENTATION, "Other presentation")));

    return presentation;
  }
}
