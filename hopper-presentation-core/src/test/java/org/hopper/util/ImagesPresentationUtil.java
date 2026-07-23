package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HAttachment;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.svg.HSvgComponent;
import org.hopper.presentation.component.types.svg.ScaleType;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

public class ImagesPresentationUtil extends BasePresentationUtil {

  public ImagesPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createImagesPresentation(int nr) throws Exception {
    // Landscape A4 presentation
    //
    HPresentation presentation =
        createBasePresentation(
            "Images (" + nr + ")",
            "Images " + nr + " description",
            10,
            "A few static SVG images with labels",
            false);

    // Remove the header and footer, not needed here...
    //
    presentation.setHeader(null);
    presentation.setFooter(null);

    HPage pageOne = presentation.getPages().get(0);
    pageOne.setWidth(800);
    pageOne.setHeight(250);

    // Add the tap image...
    //
    HSvgComponent hand = new HSvgComponent("pointing_hand_cursor_vector.svg", ScaleType.MIN);
    HComponent handComponent = new HComponent("Hand", hand);
    handComponent.setLayout(new HLayoutBuilder().top().left().bottom().build() );
    pageOne.getComponents().add(handComponent);

    // Tap or click anywhere
    //
    HLabelComponent tap = new HLabelComponent();
    tap.setLabel("Tap or click anywhere!");
    tap.setHorizontalAlignment(HHorizontalAlignment.CENTER);
    tap.setDefaultFont(new HFont("Arial", "40", true, false));

    HComponent tapComponent = new HComponent("Tap", tap);
    HLayout tapLayout =
        new HLayout(
            new HAttachment("Hand", 0, 0, HAttachment.Alignment.RIGHT), // LEFT
            null,
            new HAttachment("Hand", 0, 0, HAttachment.Alignment.CENTER),
            null);
    tapComponent.setLayout(tapLayout);
    pageOne.getComponents().add(tapComponent);

    return presentation;
  }
}
