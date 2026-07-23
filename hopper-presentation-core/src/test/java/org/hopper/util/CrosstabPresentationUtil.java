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
import org.hopper.presentation.component.types.crosstab.HCrosstabComponent;
import org.hopper.presentation.layout.HLayout;

import java.util.Arrays;

public class CrosstabPresentationUtil extends BasePresentationUtil {

  public CrosstabPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createCrosstabPresentation(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Crosstab (" + nr + ")",
            "Crosstab " + nr + " description",
            100000,
            "A crosstab top left of the page");

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
                "name", "Customer", HHorizontalAlignment.RIGHT, HVerticalAlignment.MIDDLE)));
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
    crosstab.setBackground(true);
    crosstab.setBorder(false);
    crosstab.setHorizontalMargin(3);
    crosstab.setVerticalMargin(2);
    crosstab.setEvenHeights(true);
    crosstab.setHeaderOnEveryPage(true);
    crosstab.setShowingVerticalTotals(true);
    crosstab.setShowingHorizontalTotals(true);

    HComponent crosstabComponent = new HComponent("Table1", crosstab);
    crosstabComponent.setLayout(new HLayout(0, 0));

    // Add the table to the first page.
    //
    presentation.getPages().get(0).getComponents().add(crosstabComponent);

    return presentation;
  }

  public HPresentation createCrosstabPresentationOnlyVerticalDimensions(int nr)
      throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Crosstab only vertical" + nr,
            "Crosstab only vertical " + nr + " description",
            50,
            "Crosstab with only vertical dimensions");

    HCrosstabComponent crosstab = new HCrosstabComponent(CONNECTOR_SAMPLE_ROWS);
    crosstab.setHorizontalDimensions(Arrays.asList());

    crosstab.setVerticalDimensions(
        Arrays.asList(
            new HDimension("id", "ID", HHorizontalAlignment.RIGHT, HVerticalAlignment.TOP),
            new HDimension(
                "name", "Name", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP),
            new HDimension(
                "updated",
                "Time of update",
                HHorizontalAlignment.LEFT,
                HVerticalAlignment.TOP),
            new HDimension(
                "important", "Imp?", HHorizontalAlignment.CENTER, HVerticalAlignment.TOP)));
    HFact sumFact =
        new HFact(
            "random",
            "Sum",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.TOP,
            AggregationMethod.SUM,
            "#.000");
    sumFact.setHorizontalAggregation(true);
    sumFact.setHorizontalAggregationHeader("Total Sum");
    sumFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    sumFact.setHeaderVerticalAlignment(HVerticalAlignment.TOP);
    sumFact.setFormatMask("0.00");
    HFact countFact =
        new HFact(
            "name",
            "Count",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.TOP,
            AggregationMethod.COUNT,
            "#");
    countFact.setHorizontalAggregation(true);
    countFact.setHorizontalAggregationHeader("Total Count");
    countFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    countFact.setHeaderVerticalAlignment(HVerticalAlignment.TOP);
    crosstab.setFacts(Arrays.asList(sumFact, countFact));
    crosstab.setBackground(true);
    crosstab.setBorder(false);
    crosstab.setHorizontalMargin(6);
    crosstab.setVerticalMargin(3);
    crosstab.setEvenHeights(true);
    crosstab.setHeaderOnEveryPage(true);

    HComponent crosstabComponent = new HComponent("Table1", crosstab);
    crosstabComponent.setLayout(new HLayout(0, 0));

    // Add crosstab to first page
    //
    presentation.getPages().get(0).getComponents().add(crosstabComponent);

    return presentation;
  }

  public HPresentation createCrosstabPresentationOnlyHorizontalDimensions(int nr)
      throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Crosstab only horizontal (" + nr + ")",
            "Crosstab only horizontal " + nr + " description",
            100,
            "Crosstab with only horizontal dimensions");

    HCrosstabComponent crosstab = new HCrosstabComponent(CONNECTOR_SAMPLE_ROWS);
    crosstab.setHorizontalDimensions(
        Arrays.asList(
            new HDimension(
                "color", "Color", HHorizontalAlignment.CENTER, HVerticalAlignment.TOP),
            new HDimension(
                "important", "?", HHorizontalAlignment.CENTER, HVerticalAlignment.TOP)));

    HFact sumFact =
        new HFact(
            "random",
            "Sum",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.TOP,
            AggregationMethod.SUM,
            "#.000");
    sumFact.setHorizontalAggregation(true);
    sumFact.setHorizontalAggregationHeader("Total Sum");
    sumFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    sumFact.setHeaderVerticalAlignment(HVerticalAlignment.TOP);
    HFact countFact =
        new HFact(
            "name",
            "Count",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.TOP,
            AggregationMethod.COUNT,
            "#");
    countFact.setHorizontalAggregation(true);
    countFact.setHorizontalAggregationHeader("Total Count");
    countFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    countFact.setHeaderVerticalAlignment(HVerticalAlignment.TOP);
    crosstab.setFacts(Arrays.asList(sumFact, countFact));
    crosstab.setBackground(true);
    crosstab.setBorder(false);
    crosstab.setHorizontalMargin(6);
    crosstab.setVerticalMargin(3);
    crosstab.setEvenHeights(true);
    crosstab.setHeaderOnEveryPage(true);

    HComponent crosstabComponent = new HComponent("Table1", crosstab);
    crosstabComponent.setLayout(new HLayout(25, 5));

    presentation.getPages().get(0).getComponents().add(crosstabComponent);

    return presentation;
  }

  public HPresentation createCrosstabPresentationOnlyFacts(int nr) throws Exception {
    HPresentation presentation =
        createBasePresentation(
            "Crosstab only facts (" + nr + ")",
            "Crosstab only facts " + nr + " description",
            50,
            "Crosstab with only facts");

    HCrosstabComponent crosstab = new HCrosstabComponent(CONNECTOR_SAMPLE_ROWS);
    crosstab.setHorizontalDimensions(Arrays.asList());
    crosstab.setVerticalDimensions(Arrays.asList());
    HFact sumFact =
        new HFact(
            "random",
            "Sum",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.TOP,
            AggregationMethod.SUM,
            "#.000");
    sumFact.setHorizontalAggregation(true);
    sumFact.setHorizontalAggregationHeader("Total Sum");
    sumFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    sumFact.setHeaderVerticalAlignment(HVerticalAlignment.TOP);
    HFact countFact =
        new HFact(
            "name",
            "Count",
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.TOP,
            AggregationMethod.COUNT,
            "#");
    countFact.setHorizontalAggregation(true);
    countFact.setHorizontalAggregationHeader("Total Count");
    countFact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
    countFact.setHeaderVerticalAlignment(HVerticalAlignment.TOP);
    crosstab.setFacts(Arrays.asList(sumFact, countFact));
    crosstab.setBackground(true);
    crosstab.setBorder(false);
    crosstab.setHorizontalMargin(6);
    crosstab.setVerticalMargin(3);
    crosstab.setEvenHeights(true);
    crosstab.setHeaderOnEveryPage(true);

    HComponent crosstabComponent = new HComponent("Table1", crosstab);
    crosstabComponent.setLayout(new HLayout(25, 5));

    presentation.getPages().get(0).getComponents().add(crosstabComponent);

    return presentation;
  }
}
