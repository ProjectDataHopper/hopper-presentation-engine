package org.hopper.presentation;

import org.hopper.core.HDataSet;
import org.hopper.core.HGeometry;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** The result of doing a layout on a component with all the data read */
@Getter
@Setter
public class HComponentLayoutResult {
  /** The component for which we did the layout */
  private HComponent component;

  /** The part number for components split into multiple parts over multiple pages */
  private int partNumber;

  /** The page from which this result originally came */
  private HPage sourcePage;

  /** The page on which we render */
  private HRenderPage renderPage;

  /** All the data read by the component, cached in memory */
  private HDataSet dataSet;

  /** The resulting location and imageSize after the layout */
  private HGeometry geometry;

  /**
   * All extra data a component might want to store between doing a layout and the actual rendering
   * of the component
   */
  private Map<String, Object> dataMap;

  public HComponentLayoutResult() {
    dataMap = new HashMap<>();
  }

  public HComponentLayoutResult(HComponentLayoutResult layoutResult) {
    this.component = layoutResult.component;
    this.partNumber = layoutResult.partNumber;
    this.sourcePage = layoutResult.sourcePage;
    this.renderPage = layoutResult.renderPage;
    this.dataSet = layoutResult.dataSet;
    this.geometry = layoutResult.geometry;
    this.dataMap = layoutResult.dataMap;
  }
}
