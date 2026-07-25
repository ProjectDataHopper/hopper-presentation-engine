package org.hopper.presentation.component.types.label;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HGeometry;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.HTextGeometry;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.interaction.HInteractionLocationOption;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

@JsonDeserialize(as = HLabelComponent.class)
@HComponentPlugin(
    id = "HLabelComponent",
    name = "Label",
    description = "A Label to decorate your presentations",
    image = "ui/images/components/label.svg")
@Getter
@Setter
public class HLabelComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_TEXT_GEOMETRY = "Text Geometry";
  public static final String DATA_TEXT_STRING = "Text String";

  /** UI-only: labels are not data-bound; hide inherited input connector. */
  @HWidgetElement(
      id = "sourceConnectorName",
      type = HWidgetType.NONE,
      parentId = HGuiFormConstants.PARENT_BASE,
      ignored = true)
  @JsonIgnore
  private transient boolean hideSourceConnectorName;

  @HWidgetElement(
      order = "10000-label",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Label text",
      toolTip = "The label text (variables are resolved at render time)")
  @HopMetadataProperty
  private String label;

  @HWidgetElement(
      order = "10100-underline",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Underline?")
  @HopMetadataProperty
  private boolean underline;

  @HWidgetElement(
      order = "10200-horizontalAlignment",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Horizontal alignment")
  @HopMetadataProperty
  private HHorizontalAlignment horizontalAlignment;

  @HWidgetElement(
      order = "10300-verticalAlignment",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Vertical alignment")
  @HopMetadataProperty
  private HVerticalAlignment verticalAlignment;

  @HWidgetElement(
      order = "10400-customHtml",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Custom HTML",
      toolTip = "Optional custom HTML content",
      ignored = true)
  @HopMetadataProperty
  private String customHtml;

  public HLabelComponent() {
    super("HLabelComponent");
    horizontalAlignment = HHorizontalAlignment.LEFT;
    verticalAlignment = HVerticalAlignment.TOP;
    underline = false;
  }

  public HLabelComponent(HLabelComponent c) {
    super("HLabelComponent", c);
    this.label = c.label;
    this.horizontalAlignment = c.horizontalAlignment;
    this.verticalAlignment = c.verticalAlignment;
    this.customHtml = c.customHtml;
    this.underline = c.underline;
  }

  public HLabelComponent(String label) {
    this();
    this.label = label;
  }

  public HLabelComponent clone() {
    return new HLabelComponent(this);
  }

  @Override
  public List<HInteractionLocationOption> getPossibleInteractionLocations() {
    return List.of(
        HInteractionLocationOption.item("label", "Label text", DrawnItem.Category.Label));
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
    // Nothing to read, it's just a label
    //
    // Calculate the width and height of the text in the given font
    //
    HRenderPage currentRenderPage = results.getCurrentRenderPage(page);
    HopSvgGraphics2D gc = currentRenderPage.getGc();

    // Set the font, so we can calculate the correct text imageSize
    //
    enableFont(gc, lookupDefaultFont(renderContext));

    // Calculate the string
    //
    String text = dataContext.getVariables().resolve(label);

    HTextGeometry textGeometry = calculateTextGeometry(gc, text);

    // Don't calculate this twice...
    //
    results.addDataSet(component, DATA_TEXT_STRING, text);
    results.addDataSet(component, DATA_TEXT_GEOMETRY, textGeometry);
  }

  public HSize getExpectedSize(
      HPresentation hopperPresentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    HTextGeometry textGeometry =
        (HTextGeometry) results.getDataSet(component, DATA_TEXT_GEOMETRY);

    // Retain the location, adjust the width and Height
    //
    return new HSize(textGeometry.getWidth(), textGeometry.getHeight());
  }

  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {

    HGeometry componentGeometry = layoutResult.getGeometry();
    HComponent component = layoutResult.getComponent();

    // Remember the proper text geometry
    //
    HTextGeometry textGeometry =
        (HTextGeometry) results.getDataSet(component, DATA_TEXT_GEOMETRY);
    String text = (String) results.getDataSet(component, DATA_TEXT_STRING);

    if (StringUtils.isEmpty(text)) {
      text = " ";
    }

    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();

    // Draw background for the full imageSize of the component area
    //
    setBackgroundBorderFont(gc, componentGeometry, renderContext);

    float x;
    float y;

    if (horizontalAlignment == null) {
      throw new HException(
          "Don't know how to horizontally align label '"
              + layoutResult.getComponent().getName()
              + "'");
    }

    switch (horizontalAlignment) {
      case RIGHT:
        x = componentGeometry.getX() + componentGeometry.getWidth() - textGeometry.getWidth();
        break;
      case CENTER:
        x = componentGeometry.getX() + (componentGeometry.getWidth() - textGeometry.getWidth()) / 2;
        break;
      case LEFT:
      default:
        x = componentGeometry.getX();
        break;
    }

    if (verticalAlignment == null) {
      throw new HException(
          "Don't know how to vertically align label '"
              + layoutResult.getComponent().getName()
              + "'");
    }

    switch (verticalAlignment) {
      case BOTTOM:
        y = componentGeometry.getY() + componentGeometry.getHeight() - textGeometry.getHeight();
        break;
      case MIDDLE:
        y =
            componentGeometry.getY()
                + (componentGeometry.getHeight() - textGeometry.getHeight()) / 2;
        break;
      case TOP:
      default:
        y = componentGeometry.getY();
        break;
    }

    AttributedString attributedString = new AttributedString(text);
    if (underline) {
      attributedString.addAttribute( TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON );
    }
    gc.drawString(
        attributedString.getIterator(),
        x + textGeometry.getOffsetX(),
        y + textGeometry.getOffsetY());

    HGeometry labelGeometry =
        new HGeometry(
            Math.round(offSet.getX() + x + textGeometry.getOffsetX()),
            Math.round(offSet.getY() + y),
            textGeometry.getWidth(),
            textGeometry.getHeight());

    layoutResult
        .getRenderPage()
        .getDrawnItems()
        .add(
            new DrawnItem(
                component.getName(),
                component.getComponent().getPluginId(),
                layoutResult.getPartNumber(),
                DrawnItem.DrawnItemType.ComponentItem,
                DrawnItem.Category.Label.name(),
                0,
                0,
                labelGeometry,
                new DrawnContext(text)));
  }
}
