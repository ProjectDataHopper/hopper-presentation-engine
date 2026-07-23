package org.hopper.core;

import lombok.Getter;
import lombok.Setter;

/** Represents a piece of SVG on a certain location in another document */
@Getter
@Setter
public class HSvg {

  private String svgXml;

  private HGeometry geometry;

  public HSvg() {}

  public HSvg(String svgXml, HGeometry geometry) {
    this.svgXml = svgXml;
    this.geometry = geometry;
  }
}
