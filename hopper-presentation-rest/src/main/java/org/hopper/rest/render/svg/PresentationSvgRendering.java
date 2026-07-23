package org.hopper.rest.render.svg;

import java.util.List;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.variable.HParameter;
import org.hopper.rest.render.Rendering;

public class PresentationSvgRendering extends Rendering {
  private HPresentation presentation;

  public PresentationSvgRendering() {
    super();
  }

  public PresentationSvgRendering(HPresentation presentation, List<HParameter> parameters) {
    this();
    this.presentation = presentation;
    this.parameters = parameters;
  }

  /**
   * Gets presentation
   *
   * @return value of presentation
   */
  public HPresentation getPresentation() {
    return presentation;
  }

  /**
   * Sets presentation
   *
   * @param presentation value of presentation
   */
  public void setPresentation(HPresentation presentation) {
    this.presentation = presentation;
  }
}
