package org.hopper.util;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.svg.HSvgComponent;
import org.hopper.presentation.component.types.svg.ScaleType;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

public class SvgPresentationUtil extends BasePresentationUtil {

  public SvgPresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public HPresentation createSvgPresentation(int nr) throws Exception {
    // Landscape A4 presentation
    //
    HPresentation presentation =
        createBasePresentation(
            "SVG (" + nr + ")",
            "SVG " + nr + " description",
            1,
            "A few SVG images on specific locations on the page",
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
      HLabelComponent label = new HLabelComponent();
      label.setLabel("SVG layout test");
      HComponent labelComponent = new HComponent("label-top-center", label);
      labelComponent.setLayout(new HLayoutBuilder().top().left(50, 0).build());
      pageOne.getComponents().add(labelComponent);
    }

    // Scale label to 50 high to the top right
    //
    {
      HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
      svg.setBorder(true);
      HComponent svgComponent = new HComponent("logo-top-right-100", svg);
      svgComponent.setLayout(new HLayoutBuilder().top().right().bottomFromTop(0, 50).build());
      pageOne.getComponents().add(svgComponent);
    }

    // Logo at the top right...
    //
    {
      HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
      svg.setBorder(true);
      HComponent svgComponent = new HComponent("logo-top-right", svg);
      svgComponent.setLayout(
          new HLayoutBuilder().topFromBottom("logo-top-right-100", 0, 5).right(0, 0).build());
      pageOne.getComponents().add(svgComponent);
    }

    // Logo at the top left...
    //
    {
      HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
      svg.setBorder(true);
      HComponent svgComponent = new HComponent("logo-top-left", svg);
      svgComponent.setLayout(new HLayoutBuilder().top().left().rightFromLeft(0, 100).build());
      pageOne.getComponents().add(svgComponent);
    }

    // 5 small Logos across (limit scale horizontally)
    //
    {
      String referenceComponent = "logo-top-left";
      for (int i = 0; i < 5; i++) {
        HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
        svg.setBorder(true);
        String name = "logo-across-" + i;
        HComponent svgComponent = new HComponent(name, svg);
        HLayout layout =
            new HLayoutBuilder()
                .leftFromRight(referenceComponent, 0, 0)
                .topFromBottom(referenceComponent, 0, 0)
                .rightFromRight(referenceComponent, 0, 100)
                .build();
        svgComponent.setLayout(layout);
        pageOne.getComponents().add(svgComponent);
        referenceComponent = name;
      }
    }

        // Logo at the bottom left...
        //
        {
          HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
          HComponent svgComponent = new HComponent("logo-bottom-left", svg);
          svgComponent.setLayout(new HLayoutBuilder().bottom().left().build());
          pageOne.getComponents().add(svgComponent);
        }

        // Logo at the bottom right...
        //
        {
          HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
          HComponent svgComponent = new HComponent("logo-bottom-right", svg);
          svgComponent.setLayout(new HLayoutBuilder().topFromBottom(0,
     -50).bottom().right().build());
          pageOne.getComponents().add(svgComponent);
        }

        // 5 tiny Logos edging to the center left from the bottom right corner
        //
        {
          String referenceComponent = "logo-bottom-right";
          for (int i = 0; i < 5; i++) {
            HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
            svg.setBorder(true);
            String name = "logo-bottom-across-" + i;
            HComponent svgComponent = new HComponent(name, svg);
            HLayout layout =
                new HLayoutBuilder()
                    .topFromTop(referenceComponent, 0, -50)
                    .rightFromLeft(referenceComponent, 0, 50)
                    .bottomFromTop(referenceComponent, 0, 0)
                    .build();
            svgComponent.setLayout(layout);
            pageOne.getComponents().add(svgComponent);
            referenceComponent = name;
          }
        }

    return presentation;
  }
}
