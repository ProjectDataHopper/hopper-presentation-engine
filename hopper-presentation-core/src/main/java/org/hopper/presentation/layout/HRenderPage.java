package org.hopper.presentation.layout;

import java.awt.Dimension;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.draw.DrawnItem.DrawnItemType;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.page.HPage;

import static org.hopper.core.draw.DrawnItem.Category;
import lombok.Getter;
import lombok.Setter;

/** A page on which we can render... */
@Getter
@Setter
public class HRenderPage {
  /** The original page */
  private HPage page;

  /** The render page number (1 based) */
  private int pageNumber;

  /** The graphics context */
  private HopSvgGraphics2D gc;

  /** All the component fragments on this page */
  private List<HComponentLayoutResult> layoutResults;

  private List<DrawnItem> drawnItems;

  private String svgXml;

  /**
   * Short layout/render error per component name (drawn on this page, including header/footer).
   * Populated when a component fails but a placeholder is still painted.
   */
  private Map<String, String> componentLayoutErrors;

  /** Full cause-chain / stack detail per component name (for the property editor). */
  private Map<String, String> componentLayoutErrorDetails;

  public HRenderPage() {
    layoutResults = new ArrayList<>();
    componentLayoutErrors = new HashMap<>();
    componentLayoutErrorDetails = new HashMap<>();
  }

  public HRenderPage(HPage page) {
    this();
    this.page = page;

    gc = HopSvgGraphics2D.newDocument();

    // Set the imageSize to be the imageSize of the page...
    //
    gc.setSVGCanvasSize(new Dimension(page.getWidth(), page.getHeight()));

    this.drawnItems = new ArrayList<>();
  }

  /** Record a layout/render failure for a component drawn on this page. */
  public void recordComponentError(String componentName, String summary, String detail) {
    if (componentName == null || componentName.isBlank()) {
      return;
    }
    if (componentLayoutErrors == null) {
      componentLayoutErrors = new HashMap<>();
    }
    if (componentLayoutErrorDetails == null) {
      componentLayoutErrorDetails = new HashMap<>();
    }
    if (summary != null) {
      componentLayoutErrors.put(componentName, summary);
    }
    if (detail != null) {
      componentLayoutErrorDetails.put(componentName, detail);
    }
  }

  @Override
  public String toString() {
    return "HRenderPage(#" + pageNumber + ")";
  }

  public String getSvgXml() throws HException {
    if (svgXml != null) {
      return svgXml;
    }
    try {
      StringWriter stringWriter = new StringWriter();
      gc.stream(stringWriter, true);
      svgXml = stringWriter.toString();
      return svgXml;
    } catch (Exception e) {
      throw new HException("Error converting SVG to XML", e);
    }
  }

  public void addDrawnItem(
      String componentName,
      String componentPluginId,
      int partNumber,
      DrawnItemType type,
      String category,
      int rowNr,
      int colNr,
      HGeometry geometry) {
    drawnItems.add(
        new DrawnItem(
            componentName, componentPluginId, partNumber, type, category, rowNr, colNr, geometry));
  }

  public void addComponentDrawnItem(
      HComponent component, HGeometry componentGeometry, HPosition offSet) {

    HGeometry geometry = new HGeometry(componentGeometry);
    geometry.translate(offSet);

    addDrawnItem(
        component.getName(),
        component.getComponent().getPluginId(),
        0,
        DrawnItemType.Component,
        Category.ComponentArea.name(),
        0,
        0,
        geometry);
  }

  /**
   * Lookup the component names given a location on the page in the order they were drawn
   *
   * @param x
   * @param y
   * @return
   */
  public List<String> lookupComponentName(int x, int y) {
    List<String> componentNames = new ArrayList<>();
    for (DrawnItem item : drawnItems) {
      if (item.getGeometry().contains(x, y)) {
        componentNames.add(item.getComponentName());
      }
    }
    return new ArrayList<>(componentNames);
  }

  /**
   * Lookup the last drawn item given a location on the page in the order they were drawn
   *
   * @param x
   * @param y
   * @return The last drawn item or null if nothing was found
   */
  public DrawnItem lookupDrawnItem(int x, int y) {
    return lookupDrawnItem(x, y, false);
  }

  /**
   * Lookup the last drawn item given a location on the page in the order they were drawn
   *
   * @param x
   * @param y
   * @param excludeComponents
   * @return The last drawn item or null if nothing was found
   */
  public DrawnItem lookupDrawnItem(int x, int y, boolean excludeComponents) {
    for (int i = drawnItems.size() - 1; i >= 0; i--) {
      DrawnItem item = drawnItems.get(i);
      if (!excludeComponents || item.getType() != DrawnItemType.Component) {
        if (item.getGeometry().contains(x, y)) {
          return item;
        }
      }
    }
    return null;
  }

  public HGeometry lookupComponentGeometry(String componentName) {
    DrawnItem item = lookupComponentDrawnItem(componentName);
    return item != null ? item.getGeometry() : null;
  }

  /**
   * The envelope {@link DrawnItemType#Component} for a named component, or {@code null}.
   * Used for interaction outlines when the hit item is a smaller {@code ComponentItem}.
   */
  public DrawnItem lookupComponentDrawnItem(String componentName) {
    if (componentName == null) {
      return null;
    }
    for (DrawnItem item : drawnItems) {
      if (item.getType() == DrawnItemType.Component
          && componentName.equals(item.getComponentName())) {
        return item;
      }
    }
    return null;
  }

  /**
   * All drawn items under (x,y), top-most first (reverse draw order).
   */
  public List<DrawnItem> lookupDrawnItems(int x, int y) {
    List<DrawnItem> hits = new ArrayList<>();
    for (int i = drawnItems.size() - 1; i >= 0; i--) {
      DrawnItem item = drawnItems.get(i);
      if (item.getGeometry() != null && item.getGeometry().contains(x, y)) {
        hits.add(item);
      }
    }
    return hits;
  }
}
