package org.hopper.presentation.component.types.svg;

import static org.apache.batik.svggen.DOMGroupManager.DRAW;
import static org.apache.batik.svggen.DOMGroupManager.FILL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.geom.AffineTransform;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.util.SVGConstants;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.svg.SvgCache;
import org.apache.hop.core.svg.SvgCacheEntry;
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@JsonDeserialize(as = HSvgComponent.class)
@HComponentPlugin(
    id = "HSvgComponent",
    name = "SVG",
    description = "An SVG component",
    image = "ui/images/components/svg.svg")
@Getter
@Setter
public class HSvgComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_SVG_DETAILS = "SVG Details";

  /** UI-only: SVG is filename-based; hide unused inherited base widgets. */
  @HWidgetElement(
      id = "sourceConnectorName",
      type = HWidgetType.NONE,
      parentId = HGuiFormConstants.PARENT_BASE,
      ignored = true)
  @JsonIgnore
  private transient boolean hideSourceConnectorName;

  @HWidgetElement(
      id = "defaultFont",
      type = HWidgetType.NONE,
      parentId = HGuiFormConstants.PARENT_BASE,
      ignored = true)
  @JsonIgnore
  private transient boolean hideDefaultFont;

  @HWidgetElement(
      id = "defaultColor",
      type = HWidgetType.NONE,
      parentId = HGuiFormConstants.PARENT_BASE,
      ignored = true)
  @JsonIgnore
  private transient boolean hideDefaultColor;

  @HWidgetElement(
      order = "10000-filename",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.FILENAME,
      label = "SVG filename")
  @HopMetadataProperty
  private String filename;

  @HWidgetElement(
      order = "10100-scaleType",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Scale type")
  @HopMetadataProperty
  private ScaleType scaleType;

  public HSvgComponent() {
    super("HSvgComponent");
    scaleType = ScaleType.MIN;
  }

  public HSvgComponent(String filename, ScaleType scaleType) {
    this();
    this.filename = filename;
    this.scaleType = scaleType;
  }

  public HSvgComponent(HSvgComponent c) {
    super("HSvgComponent", c);
    this.filename = c.filename;
    this.scaleType = c.scaleType;
  }

  public HSvgComponent clone() {
    return new HSvgComponent(this);
  }

  @Override
  public void processSourceData(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    if (StringUtils.isEmpty(filename)) {
      throw new HException("No image file specified");
    }

    IVariables variables = dataContext.getVariables();

    SvgDetails details = new SvgDetails();

    // The real filename after variable substitution?
    //
    String realFilename = variables.resolve(filename);

    // Load the SVG XML document
    //
    try {
      SvgCacheEntry svgCacheEntry =
          SvgCache.loadSvg(new SvgFile(realFilename, getClass().getClassLoader()));
      details.imageGeometry =
          new HGeometry(
              svgCacheEntry.getX(),
              svgCacheEntry.getY(),
              (int) svgCacheEntry.getWidth(),
              (int) svgCacheEntry.getHeight());
      details.svgDocument = svgCacheEntry.getSvgDocument();
    } catch (Exception e) {
      throw new HException("Unable to load SVG file '" + realFilename + "'", e);
    }

    // Don't calculate this twice...
    //
    results.addDataSet(component, DATA_SVG_DETAILS, details);
  }

  @Override
  public HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {
    SvgDetails details = requireDetails(results, component);
    return new HSize(details.imageGeometry.getWidth(), details.imageGeometry.getHeight());
  }

  @Override
  public HGeometry getExpectedGeometry(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    SvgDetails details = requireDetails(results, component);

    // Calculate the boundaries of this image based on the layout
    //
    HGeometry geometry =
        super.getExpectedGeometry(
            presentation, page, component, dataContext, renderContext, results);

    // See if we need to scale the SVG to fit the target...
    //
    // Zoom in or out to make the image fit onto the parent page (it's the best use-case for now)
    //
    float xMagnification = (float) geometry.getWidth() / (float) details.imageGeometry.getWidth();
    float yMagnification = (float) geometry.getHeight() / (float) details.imageGeometry.getHeight();

    // Based on the scale type we calculate the magnifications...
    //
    switch (scaleType) {
      case NONE:
        xMagnification = 1.0f;
        yMagnification = 1.0f;
        break;
      case FILL:
        break;
      case FILL_HORIZONTAL:
        yMagnification = 1.0f;
        break;
      case FILL_VERTICAL:
        xMagnification = 1.0f;
        break;
      case MIN:
        float magnification = Math.min(xMagnification, yMagnification);
        xMagnification = magnification;
        yMagnification = magnification;
        break;
      case MAX:
        magnification = Math.max(xMagnification, yMagnification);
        xMagnification = magnification;
        yMagnification = magnification;
        break;
    }

    details.xMagnification = xMagnification;
    details.yMagnification = yMagnification;

    int width = Math.round(xMagnification * details.imageGeometry.getWidth());
    int xDifference = geometry.getWidth() - width;

    int height = Math.round(yMagnification * details.imageGeometry.getHeight());
    int yDifference = geometry.getHeight() - height;

    HLayout layout = component.getLayout();

    geometry.setWidth(width);
    geometry.setHeight(height);
    if (layout.hasRight()) {
      geometry.incX(xDifference);
    }
    if (layout.hasBottom()) {
      geometry.incY(yDifference);
    }

    // TODO: fix issue with calculating centered boundaries when magnification is involved

    // Update the stored geometry to make sure...
    //
    results.addComponentGeometry(component.getName(), geometry);

    return geometry;
  }

  @Override
  public void doLayout(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {
    super.doLayout(presentation, page, component, dataContext, renderContext, results);
  }

  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {

    HRenderPage renderPage = layoutResult.getRenderPage();
    HGeometry componentGeometry = layoutResult.getGeometry();
    HComponent component = layoutResult.getComponent();

    HopSvgGraphics2D gc = renderPage.getGc();

    // Draw background for the full imageSize of the component area
    //
    setBackgroundBorderFont(gc, componentGeometry, renderContext);

    // Loaded in processSourceData (or restored by layout-cache replay into results data sets)
    SvgDetails details = (SvgDetails) results.getDataSet(component, DATA_SVG_DETAILS);
    if (details == null || details.svgDocument == null || details.imageGeometry == null) {
      throw new HException(
          "SVG details missing for component '"
              + (component != null ? component.getName() : "?")
              + "' — processSourceData did not run or layout cache did not restore SvgDetails");
    }
    Node imageSvgNode = details.svgDocument.getRootElement();

    // Embed via Graphics2D transform so page/header/footer margin translates compose
    // correctly. HopSvgGraphics2D.embedSvg() sets a replace transform attribute and
    // drops the current GC transform when Batik has not already absorbed it into a
    // parent group — which is common for header/footer (no background/border style
    // group). That left icons shifted up/left by the margin (often half a small box).
    //
    embedSvgWithCurrentTransform(
        gc,
        imageSvgNode,
        filename,
        componentGeometry.getX(),
        componentGeometry.getY(),
        details.xMagnification,
        details.yMagnification);
  }

  /**
   * Embed an SVG document's children at {@code (x,y)} with the given scale, composing with
   * the current {@link HopSvgGraphics2D} transform (page margins, etc.).
   */
  /**
   * Public for external presentation plugins (e.g. pipeline/workflow diagram components) that
   * embed a child SVG and must compose with page-margin GC transforms.
   */
  public static void embedSvgWithCurrentTransform(
      HopSvgGraphics2D gc,
      Node svgRoot,
      String filename,
      int x,
      int y,
      float xMagnification,
      float yMagnification) {

    AffineTransform prior = gc.getTransform();
    try {
      gc.translate(x, y);
      gc.scale(xMagnification, yMagnification);

      Document domFactory = gc.getDOMFactory();
      Element svgG =
          domFactory.createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_G_TAG);
      // addElement captures the current GC transform onto this group (or its parent).
      // Do not set a replace "transform" attribute — that is what broke margin composition.
      gc.getDomGroupManager().addElement(svgG, (short) (DRAW | FILL));

      svgG.setAttributeNS(null, SVGConstants.SVG_STROKE_ATTRIBUTE, SVGConstants.SVG_NONE_VALUE);
      svgG.removeAttributeNS(null, SVGConstants.SVG_FILL_ATTRIBUTE);
      if (filename != null) {
        svgG.setAttributeNS(null, "filename", filename);
      }

      NodeList childNodes = svgRoot.getChildNodes();
      for (int c = 0; c < childNodes.getLength(); c++) {
        Node childNode = childNodes.item(c);
        svgG.appendChild(domFactory.importNode(childNode, true));
      }
    } finally {
      gc.setTransform(prior);
    }
  }

  private static SvgDetails requireDetails(HLayoutResults results, HComponent component)
      throws HException {
    SvgDetails details =
        results != null
            ? (SvgDetails) results.getDataSet(component, DATA_SVG_DETAILS)
            : null;
    if (details == null || details.imageGeometry == null) {
      throw new HException(
          "SVG details missing for component '"
              + (component != null ? component.getName() : "?")
              + "' — processSourceData must load the SVG before layout/render");
    }
    return details;
  }
}
