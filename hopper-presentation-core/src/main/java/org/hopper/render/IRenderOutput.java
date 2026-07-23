package org.hopper.render;

import org.hopper.core.HRenderType;

/** This contains all sorts of rendering outputs */
public interface IRenderOutput {

  /**
   * The render type of the output
   *
   * @return
   */
  public HRenderType getRenderType();

  /*
   * Apply the render context (sizes, default colors, ...)
   *
   * @param renderType
   * @param page the page to initialize
   * @param renderContext the render context to apply
   *
  void applyRenderContext( HRenderType renderType, HPage page, IRenderContext renderContext ) throws HException;
  */
}
