package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HAttachment;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.composite.HCompositeComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;

public class CompositePresentationUtil extends BasePresentationUtil {

  public static final String COMPONENT_NAME_COMPOSITE1 = "Composite1";
  public static final String COMPONENT_NAME_LABEL1 = "Label1";
  public static final String COMPONENT_NAME_LABEL2 = "Label2";

  public CompositePresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createSimpleCompositePresentation(int nr) throws Exception {

    HPresentation presentation =
        createBasePresentation(
            "Composite simple (" + nr + ")",
            "Composite simple " + nr + " description",
            100,
            "Simple composite with 2 labels, 2nd label below 1st, right aligned");

    HPage pageOne = presentation.getPages().get(0);

    HCompositeComponent compositeComponent = new HCompositeComponent();

    // Add 2 labels to the composite
    //
    {
      HLabelComponent label1Component = new HLabelComponent();
      label1Component.setLabel("One 1 One 1 One 1 One 1 One 1 One 1 One 1 One 1 One 1");
      label1Component.setDefaultFont(new HFont("Hack", "24", false, false));
      label1Component.setHorizontalAlignment(HHorizontalAlignment.CENTER);
      label1Component.setVerticalAlignment(HVerticalAlignment.TOP);
      label1Component.setBorder(true);
      HComponent label1 = new HComponent(COMPONENT_NAME_LABEL1, label1Component);
      HLayout label1Layout = new HLayout();
      // null below means: relative to parent (page or composite)
      //
      label1Layout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT));
      label1Layout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.TOP));
      label1.setLayout(label1Layout);

      compositeComponent.getChildren().add(label1);
    }

    {
      HLabelComponent label2Component = new HLabelComponent();
      label2Component.setLabel("Two 2 Two 2 Two 2");
      label2Component.setDefaultFont(new HFont("Hack", "18", false, false));
      label2Component.setHorizontalAlignment(HHorizontalAlignment.RIGHT);
      label2Component.setVerticalAlignment(HVerticalAlignment.TOP);
      label2Component.setBorder(true);
      HComponent label2 = new HComponent(COMPONENT_NAME_LABEL2, label2Component);
      HLayout label2Layout = new HLayout();
      // null below means: relative to parent (page or composite)
      //
      label2Layout.setRight(
          new HAttachment(COMPONENT_NAME_LABEL1, 0, 0, HAttachment.Alignment.RIGHT));
      label2Layout.setTop(
          new HAttachment(COMPONENT_NAME_LABEL1, 0, 5, HAttachment.Alignment.BOTTOM));
      label2.setLayout(label2Layout);

      compositeComponent.getChildren().add(label2);
    }

    HComponent composite = new HComponent(COMPONENT_NAME_COMPOSITE1, compositeComponent);
    HLayout compositeLayout = new HLayout();
    compositeLayout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT));
    compositeLayout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.TOP));
    composite.setLayout(compositeLayout);

    pageOne.getComponents().add(composite);

    return presentation;
  }
}
