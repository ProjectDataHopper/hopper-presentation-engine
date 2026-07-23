package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

public class LabelPresentationUtil extends BasePresentationUtil {

  public LabelPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createLabelPresentation(int nr) throws HException {
    HPresentation presentation =
        createBasePresentation(
            "Label (" + nr + ")",
            "Label " + nr + " description",
            1,
            "A single label top/left of the page",
            false);

    HLabelComponent label = new HLabelComponent();
    label.setLabel("<_ö gpĨ\"dsfsdf\nsdfljsldfsldjf\n   ewioruwero>");
    label.setDefaultFont(new HFont("Courier", "40", true, true));
    label.setHorizontalAlignment(HHorizontalAlignment.LEFT);
    label.setVerticalAlignment(HVerticalAlignment.TOP);
    label.setBorder(true);
    label.setDefaultColor(new HColorRGB(0, 140, 194));
    label.setBorderColor(new HColorRGB(80, 80, 80));
    label.setBackGroundColor(new HColorRGB(200, 200, 200));

    HComponent label1 = new HComponent("Label1", label);
    label1.setLayout(new HLayout(25, 5));

    presentation.getPages().get(0).getComponents().add(label1);

    return presentation;
  }

  public HPresentation createLabelsPresentation(int nr) throws HException {
    HPresentation presentation =
        createBasePresentation(
            "Labels (" + nr + ")",
            "Labels " + nr + " description",
            1,
            "Labels placed all over the page",
            false);

    // Remove the header and footer, not needed here...
    //
    presentation.setHeader(null);
    presentation.setFooter(null);

    HPage pageOne = presentation.getPages().get(0);
    pageOne.setTopMargin(25);
    pageOne.setLeftMargin(25);
    pageOne.setBottomMargin(25);
    pageOne.setRightMargin(25);

    // Label at the top center...
    //
    {
      HLabelComponent label = new HLabelComponent("top-center");
      HComponent labelComponent = new HComponent("label-top-center", label);
      labelComponent.setLayout(new HLayoutBuilder().top().left(50, 0).build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the bottom center...
    //
    {
      HLabelComponent label = new HLabelComponent("bottom-center");
      HComponent labelComponent = new HComponent("label-bottom-center", label);
      labelComponent.setLayout(new HLayoutBuilder().bottom().left(50, 0).build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the left center...
    //
    {
      HLabelComponent label = new HLabelComponent("left-center");
      HComponent labelComponent = new HComponent("label-left-center", label);
      labelComponent.setLayout(new HLayoutBuilder().top(50, 0).left().build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the right center...
    //
    {
      HLabelComponent label = new HLabelComponent("right-center");
      HComponent labelComponent = new HComponent("label-right-center", label);
      labelComponent.setLayout(new HLayoutBuilder().top(50, 0).right().build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the center...
    //
    {
      HLabelComponent label = new HLabelComponent("center");
      HComponent labelComponent = new HComponent("label-center", label);
      labelComponent.setLayout(new HLayoutBuilder().left(50, 0).top(50, 0).build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the top left...
    //
    {
      HLabelComponent label = new HLabelComponent("top-left");
      HComponent labelComponent = new HComponent("label-top-left", label);
      labelComponent.setLayout(new HLayoutBuilder().top().left().build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the top right...
    //
    {
      HLabelComponent label = new HLabelComponent("top-right");
      HComponent labelComponent = new HComponent("label-top-right", label);
      labelComponent.setLayout(new HLayoutBuilder().top().right().build());
      pageOne.getComponents().add(labelComponent);
    }

    // Label at the bottom left...
    //
    {
      HLabelComponent label = new HLabelComponent("bottom-left");
      HComponent labelComponent = new HComponent("label-bottom-left", label);
      labelComponent.setLayout(new HLayoutBuilder().bottom().left().build());
      pageOne.getComponents().add(labelComponent);
    }

    // Logo at the bottom right...
    //
    {
      HLabelComponent label = new HLabelComponent("bottom-right");
      HComponent labelComponent = new HComponent("label-bottom-right", label);
      labelComponent.setLayout(new HLayoutBuilder().bottom().right().build());
      pageOne.getComponents().add(labelComponent);
    }

    return presentation;
  }
}
