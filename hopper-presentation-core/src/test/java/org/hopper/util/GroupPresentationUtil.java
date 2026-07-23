package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HSortMethod;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

import java.util.Collections;

public class GroupPresentationUtil extends BasePresentationUtil {

  public static final String COMPONENT_NAME_LABEL = "Label1";

  public GroupPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createSimpleGroupedLabelPresentation(int nr) throws Exception {

    HPresentation presentation =
        createBasePresentation(
            "Group label (" + nr + ")",
            "Group label " + nr + " description",
            100,
            "A group repeating labels with country names");

    HPage pageOne = presentation.getPages().get(0);

    // The Label to repeat in the group component
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

    HComponent label = new HComponent(COMPONENT_NAME_LABEL, labelComponent);
    label.setLayout(new HLayoutBuilder().left().top().build());

    // Read the distinct countries from connector called BasePresentationUtil.CONNECTOR_SAMPLE_ROWS
    //
    HGroupComponent groupComponent =
        new HGroupComponent(
            CONNECTOR_SAMPLE_ROWS,
            Collections.singletonList(
                new HColumn(
                    "country", "Country", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP)),
            Collections.singletonList(new HSortMethod(HSortMethod.Type.NATIVE_VALUE, true)),
            true, // distinct values from all rows
            label, // The component to repeat
            5 // Margin
            );
    HComponent hopperGroupComponent = new HComponent("Group", groupComponent);
    hopperGroupComponent.setLayout(new HLayoutBuilder().left().top().build());

    pageOne.getComponents().add(hopperGroupComponent);

    return presentation;
  }
}
