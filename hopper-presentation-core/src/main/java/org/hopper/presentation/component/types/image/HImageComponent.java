package org.hopper.presentation.component.types.image;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
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
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

@JsonDeserialize(as = HImageComponent.class)
@HComponentPlugin(
    id = "HImageComponent",
    name = "Image",
    description = "An image component",
    image = "ui/images/components/image.svg")
@Getter
@Setter
public class HImageComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_IMAGE_DETAILS = "Image Details";

  @HWidgetElement(
      order = "10000-filename",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.FILENAME,
      label = "Image filename",
      toolTip = "Classpath or VFS path to the image")
  @HopMetadataProperty
  private String filename;

  @HWidgetElement(
      order = "10100-scalePercent",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Scale percent")
  @HopMetadataProperty
  private String scalePercent;

  public HImageComponent() {
    super("HSvgComponent");
  }

  public HImageComponent(String filename) {
    this();
    this.filename = filename;
  }

  public HImageComponent(HImageComponent c) {
    super("HSvgComponent", c);
    this.filename = c.filename;
    this.scalePercent = c.scalePercent;
  }

  public HImageComponent clone() {
    return new HImageComponent(this);
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

    ImageDetails details = new ImageDetails();

    // Get the width and height of the image
    //
    try {
      URL resource = this.getClass().getClassLoader().getResource(filename);
      if (resource == null) {
        throw new HException("Unable to find image file '" + filename + "'");
      }

      details.image = ImageIO.read(resource.openStream());
    } catch (IOException e) {
      throw new HException("Unable to load image file '" + filename + "'", e);
    }

    if (details.image == null) {
      // Probably unsupported image type
      //
      throw new HException("Unable to load file '" + filename + "' (Unsupported type?)");
    }

    details.scaleFactor = (double) Const.toDouble(scalePercent, 100.0) / 100;

    details.originalSize = new HSize(details.image.getWidth(), details.image.getHeight());
    details.imageSize =
        new HSize(
            (int) (details.image.getWidth() * details.scaleFactor),
            (int) (details.image.getHeight() * details.scaleFactor));

    // Don't calculate this twice...
    //
    results.addDataSet(component, DATA_IMAGE_DETAILS, details);
  }

  public HSize getExpectedSize(
      HPresentation hopperPresentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {
    ImageDetails details = (ImageDetails) results.getDataSet(component, DATA_IMAGE_DETAILS);
    return details.imageSize;
  }

  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {

    HGeometry componentGeometry = layoutResult.getGeometry();
    HComponent component = layoutResult.getComponent();

    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();

    // Draw background for the full imageSize of the component area
    //
    setBackgroundBorderFont(gc, componentGeometry, renderContext);

    // Remember the details
    //
    ImageDetails details = (ImageDetails) results.getDataSet(component, DATA_IMAGE_DETAILS);

    // This allow us to make the image smaller or larger
    //
    AffineTransform oldTransform = gc.getTransform();
    gc.scale(details.scaleFactor, details.scaleFactor);

    // Don't scale the location...
    int x = (int) (componentGeometry.getX() / details.scaleFactor);
    int y = (int) (componentGeometry.getY() / details.scaleFactor);
    gc.drawImage(details.image, x, y, null);

    // Set the drawing scale back to normal
    //
    gc.setTransform(oldTransform);

    if (isBorder()) {
      enableColor(gc, lookupBorderColor(renderContext));
      gc.drawRect(
          componentGeometry.getX(),
          componentGeometry.getY(),
          details.imageSize.getWidth(),
          details.imageSize.getHeight());
    }
  }
}
