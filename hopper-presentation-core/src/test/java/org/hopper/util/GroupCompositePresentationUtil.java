package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HAttachment;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HSortMethod;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.composite.HCompositeComponent;
import org.hopper.presentation.component.types.crosstab.HCrosstabComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

import java.util.Arrays;
import java.util.Collections;

public class GroupCompositePresentationUtil extends BasePresentationUtil {

  public static final String COMPONENT_NAME_COMPOSITE1 = "Composite1";
  public static final String COMPONENT_NAME_LABEL1 = "Label1";
  public static final String COMPONENT_NAME_CROSSTAB1 = "Crosstab1";

  public GroupCompositePresentationUtil(
      IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createGroupCompositePresentation(int nr) throws Exception {

    HPresentation presentation =
        createBasePresentation(
            "Group composite (" + nr + ")",
            "Group composite " + nr + " description",
            2000,
            "A group repeating a composite with a label and a crosstab, data filtering");

    HPage pageOne = presentation.getPages().get(0);

    HCompositeComponent compositeComponent = new HCompositeComponent();

    {
      // The Label to repeat in the group component, top of the composite
      //
      HLabelComponent labelComponent = new HLabelComponent();
      labelComponent.setLabel("Country: ${country}");
      labelComponent.setDefaultFont(new HFont("Courier", "48", false, false));
      labelComponent.setHorizontalAlignment(HHorizontalAlignment.CENTER);
      labelComponent.setVerticalAlignment(HVerticalAlignment.TOP);
      labelComponent.setBorder(true);
      labelComponent.setDefaultColor(new HColorRGB(0, 140, 194));
      labelComponent.setBorderColor(new HColorRGB(240, 240, 240));
      labelComponent.setBackGroundColor(new HColorRGB(200, 200, 200));

      HComponent label = new HComponent(COMPONENT_NAME_LABEL1, labelComponent);
      label.setLayout(new HLayoutBuilder().left().top().build());
      compositeComponent.getChildren().add(label);
    }

    {
      // Add a crosstab below the label
      //
      HCrosstabComponent ctc = new HCrosstabComponent(CONNECTOR_SAMPLE_ROWS);
      ctc.setHorizontalDimensions(
          Arrays.asList(
              new HDimension(
                  "color", "Color", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE),
              new HDimension(
                  "important", "?", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE)));
      ctc.setVerticalDimensions(
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
      ctc.setFacts(Arrays.asList(sumFact, countFact));
      ctc.setBackground(false);
      ctc.setBorder(false);
      ctc.setHorizontalMargin(3);
      ctc.setVerticalMargin(2);
      ctc.setEvenHeights(true);
      ctc.setHeaderOnEveryPage(true);
      ctc.setShowingVerticalTotals(true);
      ctc.setShowingHorizontalTotals(true);

      HComponent ct = new HComponent(COMPONENT_NAME_CROSSTAB1, ctc);
      HLayout layout = new HLayout();
      layout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT));
      layout.setTop(
          new HAttachment(COMPONENT_NAME_LABEL1, 0, 10, HAttachment.Alignment.BOTTOM));

      ct.setLayout(layout);

      compositeComponent.getChildren().add(ct);
    }

    HComponent composite = new HComponent(COMPONENT_NAME_COMPOSITE1, compositeComponent);
    HLayout compositeLayout = new HLayout();
    compositeLayout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT));
    compositeLayout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.TOP));
    composite.setLayout(compositeLayout);

    // Create a group and throw the composite in there.
    //
    HGroupComponent groupComponent =
        new HGroupComponent(
            CONNECTOR_SAMPLE_ROWS,
            Collections.singletonList(
                new HColumn(
                    "country", "Country", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP)),
            Collections.singletonList(new HSortMethod(HSortMethod.Type.NATIVE_VALUE, true)),
            true, // distinct values from all rows
            composite, // The component to repeat
            10 // Margin
            );
    HComponent hopperGroupComponent = new HComponent("Group", groupComponent);
    hopperGroupComponent.setLayout(new HLayoutBuilder().left().top().build());

    pageOne.getComponents().add(hopperGroupComponent);

    return presentation;
  }
}
