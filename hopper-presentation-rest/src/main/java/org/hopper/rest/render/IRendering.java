package org.hopper.rest.render;

import java.util.Date;
import java.util.List;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.variable.HParameter;

/**
 * A rendering is given a unique ID so that we can interrogate the rendering with web services. We
 * can cache the rendering this way, keep it around for a bit and so on.
 */
public interface IRendering {
  String getId();

  HPresentation getPresentation();

  String getPresentationName();

  Date getRenderDate();

  List<HParameter> getParameters();

  HLayoutResults getLayoutResults();
}
