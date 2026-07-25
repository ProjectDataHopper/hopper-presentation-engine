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

  /**
   * Browser / guest session that owns this rendering. Lookups must only return a rendering when
   * this matches the caller's current session (multi-user isolation).
   */
  String getSessionId();

  void setSessionId(String sessionId);

  /**
   * Whether this rendering used continuous (browser scroll) layout. Default implementations return
   * false.
   */
  default boolean isContinuousScroll() {
    return false;
  }

  /** Viewport width (CSS px) used for continuous layout, or 0. */
  default int getViewportWidth() {
    return 0;
  }
}
