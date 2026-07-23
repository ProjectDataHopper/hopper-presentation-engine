package org.hopper.presentation.component.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HAttachment;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.HTextGeometry;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;

@Getter
@Setter
public abstract class HBaseComponent implements IHComponent {

  @HopMetadataProperty @JsonProperty protected String pluginId;

  @HWidgetElement(
      order = "01000-sourceConnectorName",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.COMBO,
      comboSource = org.hopper.core.gui.plugin.HComboSource.CONNECTORS,
      label = "Input connector",
      toolTip = "Optional data connector feeding this component")
  @HopMetadataProperty
  @JsonProperty
  protected String sourceConnectorName;

  @HopMetadataProperty @JsonProperty protected boolean background;
  @HopMetadataProperty @JsonProperty protected boolean border;

  @HWidgetElement(
      order = "01100-themeName",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.COMBO,
      comboSource = org.hopper.core.gui.plugin.HComboSource.THEMES,
      label = "Theme name")
  @HopMetadataProperty
  @JsonProperty
  protected String themeName;

  @HWidgetElement(
      order = "01400-defaultFont",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.TEXT,
      label = "Default font")
  @HopMetadataProperty
  @JsonProperty
  protected HFont defaultFont;

  @HWidgetElement(
      order = "01500-defaultColor",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.TEXT,
      label = "Default color")
  @HopMetadataProperty
  @JsonProperty
  protected HColorRGB defaultColor;

  @HWidgetElement(
      order = "01300-backGroundColor",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.TEXT,
      label = "Background color")
  @HopMetadataProperty
  @JsonProperty
  protected HColorRGB backGroundColor;

  @HWidgetElement(
      order = "01200-borderColor",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.TEXT,
      label = "Border color")
  @HopMetadataProperty
  @JsonProperty
  protected HColorRGB borderColor;

  // Fields below are not serialized
  //
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  protected transient ILogChannel log;

  public HBaseComponent() {}

  public HBaseComponent(String pluginId) {
    this.pluginId = pluginId;
  }

  public HBaseComponent(String pluginId, HBaseComponent c) {
    this(c.pluginId);
    this.sourceConnectorName = c.sourceConnectorName;
    this.defaultFont = c.defaultFont == null ? null : new HFont(c.defaultFont);
    this.defaultColor = c.defaultColor == null ? null : new HColorRGB(c.defaultColor);
    this.background = c.background;
    this.backGroundColor = c.backGroundColor == null ? null : new HColorRGB(c.backGroundColor);
    this.border = c.border;
    this.borderColor = c.borderColor == null ? null : new HColorRGB(c.borderColor);
    this.themeName = c.themeName;
  }

  public abstract HBaseComponent clone();

  /**
   * @return Null if the dialog class is determined automatically. Otherwise returns the dialog
   *     class name.
   */
  @JsonIgnore
  public String getDialogClassname() {
    return null;
  }

  // First
  public abstract HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException;

  /**
   * Third Calculate expected geometry of the component based on relative positioning and
   * everything.
   *
   * @param presentation
   * @param page
   * @param component
   * @param results
   */
  public HGeometry getExpectedGeometry(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    // Get the natural imageSize of this component
    //
    HSize expectedSize =
        getExpectedSize(presentation, page, component, dataContext, renderContext, results);
    if (expectedSize == null) {
      expectedSize = new HSize(0, 0);
    }

    int x = 0;
    int y = 0;
    int width = expectedSize.getWidth();
    int height = expectedSize.getHeight();

    HLayout layout = component.getLayout();

    // Validate some basic static information.
    layout.validate(component);

    HAttachment left = layout.getLeft();
    HAttachment top = layout.getTop();
    HAttachment right = layout.getRight();
    HAttachment bottom = layout.getBottom();

    // First calculate left and top : (x,y)

    // Left means x coordinate
    //
    if (left != null) {
      // Horizontal attachments prefer the first multi-page part (stable page-1 box).
      HGeometry geometry =
          lookupGeometry(left.getComponentName(), page, results, presentation, true);

      switch (left.getAlignment()) {
        case DEFAULT:
        case LEFT:
          x =
              geometry.getX()
                  + calcPct(geometry.getWidth(), left.getPercentage())
                  + left.getOffset();
          break;
        case RIGHT:
          x =
              geometry.getX()
                  + geometry.getWidth()
                  - calcPct(geometry.getWidth(), left.getPercentage())
                  + left.getOffset();
          break;
        case CENTER:
          x =
              (geometry.getWidth() - width) / 2
                  + calcPct(geometry.getWidth(), left.getPercentage())
                  + left.getOffset();
          break;
        case TOP:
        case BOTTOM:
          throw new HException(
              "Setting a TOP or BOTTOM alignment makes no sense for left attachments on component "
                  + component.getName());
      }
    }

    // top means y coordinate
    //
    if (top != null) {
      // BOTTOM of a reference = sequential stack (group rows): use last geometry so Group's
      // verticalMargin (incHeight on the last envelope) is visible. TOP/CENTER use first part.
      boolean preferFirst = top.getAlignment() != HAttachment.Alignment.BOTTOM;
      HGeometry geometry =
          lookupGeometry(top.getComponentName(), page, results, presentation, preferFirst);
      switch (top.getAlignment()) {
        case DEFAULT:
        case TOP:
          y =
              geometry.getY()
                  + calcPct(geometry.getHeight(), top.getPercentage())
                  + top.getOffset();
          break;
        case BOTTOM:
          y =
              geometry.getY()
                  + geometry.getHeight()
                  - calcPct(geometry.getHeight(), top.getPercentage())
                  + top.getOffset();
          break;
        case CENTER:
          y =
              geometry.getY()
                  + geometry.getHeight() / 2
                  + calcPct(geometry.getHeight(), top.getPercentage())
                  + top.getOffset();
          break;
      }
    }

    // We calculated the coordinates.
    // Now see if we need to adjust the width and height.

    // Right attachment : width

    if (right != null) {
      HGeometry geometry =
          lookupGeometry(right.getComponentName(), page, results, presentation, true);
      if (left == null) {
        // We're calculating the x-boundary, not the width
        //
        switch (right.getAlignment()) {
          case LEFT:
            x =
                geometry.getX()
                    - width
                    + calcPct(geometry.getHeight(), right.getPercentage())
                    + right.getOffset();
            break;
          case DEFAULT:
          case RIGHT:
            x =
                geometry.getX()
                    + geometry.getWidth()
                    - width // hug the right
                    - calcPct(geometry.getHeight(), right.getPercentage())
                    + right.getOffset();
            break;
          case CENTER:
            x =
                geometry.getX()
                    + geometry.getWidth() / 2
                    - geometry.getWidth()
                    + calcPct(geometry.getHeight(), right.getPercentage())
                    + right.getOffset();
            break;
        }
      } else {
        // We have a left and right boundary, so we can calculate the width
        //
        switch (right.getAlignment()) {
          case LEFT:
            width =
                geometry.getX()
                    - x
                    + calcPct(geometry.getWidth(), right.getPercentage())
                    + right.getOffset();
            break;
          case DEFAULT:
          case RIGHT:
            // We calculate the width, stretch or shrink the component area
            // So we're asked to take the right boundary of the referenced geometry
            // Then we're subtracting the width of the geometry.
            // Which is to say that this is the same as the x location
            //
            width =
                geometry.getX()
                    + geometry.getWidth()
                    - x
                    - calcPct(geometry.getWidth(), right.getPercentage())
                    + right.getOffset();
            break;
          case CENTER:
            width =
                geometry.getX()
                    + geometry.getWidth() / 2
                    - x
                    + calcPct(geometry.getWidth(), right.getPercentage())
                    + right.getOffset();
            break;
        }
      }
    }

    // bottom means height
    //
    if (bottom != null) {
      // Stretching/hugging to BOTTOM of a reference should see last geometry (group envelopes).
      boolean preferFirst = bottom.getAlignment() != HAttachment.Alignment.BOTTOM;
      HGeometry geometry =
          lookupGeometry(bottom.getComponentName(), page, results, presentation, preferFirst);

      if (top == null) {
        // We're calculating the y-location, not the height
        //
        switch (bottom.getAlignment()) {
          case TOP:
            y =
                geometry.getY()
                    - height
                    + calcPct(geometry.getHeight(), bottom.getPercentage())
                    + bottom.getOffset();
            break;
          case DEFAULT:
          case BOTTOM:
            y =
                geometry.getY()
                    + geometry.getHeight()
                    - height // hug the bottom
                    + calcPct(geometry.getHeight(), bottom.getPercentage())
                    + bottom.getOffset();
            break;
          case CENTER:
            y =
                geometry.getY()
                    + geometry.getHeight() / 2
                    - geometry.getHeight()
                    + bottom.getOffset();
            break;
        }
      } else {
        // We calculate the width
        //
        switch (bottom.getAlignment()) {
          case TOP:
            height =
                geometry.getY()
                    - y
                    + calcPct(geometry.getWidth(), bottom.getPercentage())
                    + bottom.getOffset();
            break;
          case DEFAULT:
          case BOTTOM:
            height =
                geometry.getY()
                    + geometry.getHeight()
                    - y
                    - calcPct(geometry.getWidth(), bottom.getPercentage())
                    + bottom.getOffset();
            break;
          case CENTER:
            height =
                geometry.getY()
                    + geometry.getHeight() / 2
                    - y
                    - calcPct(geometry.getWidth(), bottom.getPercentage())
                    + bottom.getOffset();
            break;
        }
      }
    }

    // Let's not do negative width/height.
    // It's a misconfiguration
    //
    width = Math.max(0, width);
    height = Math.max(0, height);

    // Now we have the actual position and imageSize of the component
    //
    return new HGeometry(x, y, width, height);
  }

  /**
   * Resolve geometry for a relative attachment.
   *
   * @param preferFirstPart when true, use the first multi-page part (stable page-1 box for peers
   *     beside tables). When false, use the last registered geometry (group envelopes, sequential
   *     stacks below a previous row — includes Group verticalMargin mutations).
   */
  private HGeometry lookupGeometry(
      String componentName,
      HPage page,
      HLayoutResults results,
      HPresentation presentation,
      boolean preferFirstPart)
      throws HException {
    if (StringUtils.isEmpty(componentName)) {
      // Use the geometry of the page...
      //
      int width = page.getWidthBetweenMargins();
      int height = presentation.getUsableHeight(page);
      HGeometry geometry = new HGeometry(0, 0, width, height);
      return geometry;
    } else {
      HGeometry geometry =
          preferFirstPart
              ? results.findFirstGeometry(componentName)
              : results.findGeometry(componentName);
      if (geometry == null) {
        throw new HException(
            "Unable to find the geometry of component "
                + componentName
                + " on page "
                + presentation.getPages().indexOf(page));
      }
      return geometry;
    }
  }

  /**
   * True when this component stacks below another component (top attached to reference BOTTOM).
   * Used for group/composite sequential rows that must continue on the current render page.
   */
  private static boolean isSequentialBelowAttachment(HLayout layout) {
    if (layout == null) {
      return false;
    }
    HAttachment top = layout.getTop();
    return top != null
        && StringUtils.isNotEmpty(top.getComponentName())
        && top.getAlignment() == HAttachment.Alignment.BOTTOM;
  }

  private int calcPct(int height, int percentage) {
    return (int) ((double) height * (double) percentage / 100);
  }

  /**
   * Fourth Now we do the layout of the component. In case the component doesn't fit on the page,
   * move it to a next page...
   *
   * @param presentation
   * @param page
   * @param component
   * @param dataContext
   * @param renderContext
   * @param results
   */
  public void doLayout(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    // Sequential stacks (group row N below row N-1: top = previous BOTTOM) continue on the
    // current render page after multi-page tables/crosstabs. Peer components (charts/labels
    // beside a multi-page table, top = page TOP) stay on the first body page.
    boolean sequentialBelow = isSequentialBelowAttachment(component.getLayout());
    HRenderPage renderPage =
        sequentialBelow ? results.getCurrentRenderPage(page) : results.getFirstRenderPage(page);

    // Calculate the expected geometry for this component
    //
    HGeometry expectedGeometry =
        getExpectedGeometry(presentation, page, component, dataContext, renderContext, results);

    int bottomOfComponent = expectedGeometry.getY() + expectedGeometry.getHeight();
    int usablePageHeight = presentation.getUsableHeight(page);

    // Check if the component fits on the chosen page (height only for now).
    //
    if (bottomOfComponent > usablePageHeight) {
      if (sequentialBelow) {
        // Continue after the current page chain
        renderPage = results.addNewPage(page, renderPage);
      } else {
        // Peer overflow: continue after the full body page chain (not only page 1)
        HRenderPage last = results.getCurrentRenderPage(page);
        renderPage = results.addNewPage(page, last);
      }

      // OK, now we render on this page...
      // We'll have to re-calculate the y-coordinate of the component to be at the very top of the
      // page...
      //
      expectedGeometry.setY(page.getTopMargin());
    }

    // Now create a layout result to remember during rendering...
    //
    HComponentLayoutResult result = new HComponentLayoutResult();
    result.setRenderPage(renderPage);
    result.setSourcePage(page);
    result.setComponent(component);
    result.setGeometry(expectedGeometry);
    result.setPartNumber(1); // Only one part ever for a label, perhaps later more

    // Store the geometry also in the results for layout purposes...
    //
    results.addComponentGeometry(component.getName(), expectedGeometry);

    renderPage.getLayoutResults().add(result);
  }

  /** Finally... Render the component using the layout results after having done the layout. */
  public abstract void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException;

  protected Font createFont(HFont hopperFont) {
    int size = 10;
    int style = Font.PLAIN;

    if (StringUtils.isNotEmpty(hopperFont.getFontSize())) {
      size = Const.toInt(hopperFont.getFontSize(), 10);
    }
    if (hopperFont.isBold()) {
      style |= Font.BOLD;
    }
    if (hopperFont.isItalic()) {
      style |= Font.ITALIC;
    }

    return new Font(hopperFont.getFontName(), style, size);
  }

  /**
   * If a background color is set, use that. If a theme is set for this component, take the
   * background color from that theme.
   *
   * @param renderContext the render context to look up the color with
   * @return The background color of this component or the one from the defined theme (if any is
   *     set).
   * @throws HException in case the requested color isn't defined anywhere
   */
  protected HColorRGB lookupBackgroundColor(IRenderContext renderContext) throws HException {
    if (backGroundColor != null) {
      return backGroundColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupBackgroundColor();
    }
    if (defaultColor != null) {
      return defaultColor;
    }
    throw new HException("No background color nor default color found (no theme used or found");
  }

  /**
   * If a default color is set, use that. If a theme is set for this component, take the default
   * color from that scheme.
   *
   * @param renderContext the render context to look up the color with
   * @return The default color of this component or the one from the defined theme (if any is set).
   *     It returns null otherwise.
   */
  protected HColorRGB lookupDefaultColor(IRenderContext renderContext) throws HException {
    if (defaultColor != null) {
      return defaultColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.getDefaultColor();
    }
    throw new HException("There is no default color set (no theme found)");
  }

  /**
   * If a border color is set, use that. If a theme is set for this component, take the border color
   * from there.
   *
   * @param renderContext the render context to look up the color with
   * @return The border color of this component or the one from the defined theme (if any is set)
   * @throws HException in case the requested color isn't defined anywhere
   */
  protected HColorRGB lookupBorderColor(IRenderContext renderContext) throws HException {
    if (borderColor != null) {
      return borderColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupBorderColor();
    }
    if (defaultColor != null) {
      return defaultColor;
    }
    throw new HException("No background color nor default color found (no theme used or found");
  }

  /**
   * Look up the default font from component settings or from the active theme
   *
   * @param renderContext The context to lookup a theme in
   * @return The default font or null if no font is found
   */
  protected HFont lookupDefaultFont(IRenderContext renderContext) throws HException {
    if (defaultFont != null) {
      return defaultFont;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.getDefaultFont();
    }
    throw new HException("There is no default font set (no theme found)");
  }

  protected void drawBackGround(
      SVGGraphics2D gc, HGeometry componentGeometry, IRenderContext renderContext)
      throws HException {
    // Is there a background?
    //
    if (background) {
      HColorRGB actualBackgroundColor = lookupBackgroundColor(renderContext);
      if (actualBackgroundColor != null) {
        Color oldColor = gc.getColor();
        Color bg =
            new Color(
                actualBackgroundColor.getR(),
                actualBackgroundColor.getG(),
                actualBackgroundColor.getB());
        gc.setColor(bg);
        gc.fillRect(
            componentGeometry.getX(),
            componentGeometry.getY(),
            componentGeometry.getWidth(),
            componentGeometry.getHeight());
        gc.setBackground(bg);
        gc.setColor(oldColor);
      }
    }
  }

  /**
   * Draw a border around the component in the selected theme
   *
   * @param gc
   * @param componentGeometry
   */
  protected void drawBorder(
      SVGGraphics2D gc, HGeometry componentGeometry, IRenderContext renderContext)
      throws HException {
    // Should we draw a border?
    //
    if (isBorder()) {
      HColorRGB realBorderColor = lookupBorderColor(renderContext);
      if (realBorderColor != null) {
        HColorRGB oldColor = enableColor(gc, realBorderColor);
        gc.drawRect(
            componentGeometry.getX(),
            componentGeometry.getY(),
            componentGeometry.getWidth(),
            componentGeometry.getHeight());
        enableColor(gc, oldColor);
      }
    }
  }

  /**
   * Enable to specified color, get the old color back
   *
   * @param gc the graphical context
   * @param hopperColor the color to set
   * @return The old color on the gc
   */
  protected HColorRGB enableColor(SVGGraphics2D gc, HColorRGB hopperColor) {
    // The label color...
    //
    Color oldColor = gc.getColor();

    if (hopperColor != null) {
      gc.setColor(new Color(hopperColor.getR(), hopperColor.getG(), hopperColor.getB()));
    }

    return new HColorRGB(oldColor.getRed(), oldColor.getGreen(), oldColor.getBlue());
  }

  /**
   * Enable to specified background color, get the old background color back
   *
   * @param gc the graphical context
   * @param hopperColor the background color to set
   * @return The old color on the gc
   */
  protected HColorRGB enableBackgroundColor(SVGGraphics2D gc, HColorRGB hopperColor) {
    // The label color...
    //
    Color oldColor = gc.getBackground();

    if (hopperColor != null) {
      gc.setBackground(new Color(hopperColor.getR(), hopperColor.getG(), hopperColor.getB()));
    }

    return new HColorRGB(oldColor.getRed(), oldColor.getGreen(), oldColor.getBlue());
  }

  /**
   * Set all the default in terms of background, border, color and font
   *
   * @param gc
   * @param componentGeometry
   */
  protected void setBackgroundBorderFont(
      SVGGraphics2D gc, HGeometry componentGeometry, IRenderContext renderContext)
      throws HException {
    drawBackGround(gc, componentGeometry, renderContext);
    drawBorder(gc, componentGeometry, renderContext);
    enableColor(gc, lookupDefaultColor(renderContext));
    enableFont(gc, lookupDefaultFont(renderContext));
  }

  /**
   * Calculate the correct width and height or a string on a gc. Also return the descent of the
   * string
   *
   * @param gc
   * @param string The string to calculate the HTextGeometry for
   * @return The string geometry
   */
  protected HTextGeometry calculateTextGeometry(SVGGraphics2D gc, String string) {
    // Calculate the proper imageSize of the string...
    //
    boolean emptyString = StringUtils.isEmpty(string);
    TextLayout textLayout =
        new TextLayout(
            emptyString ? "Apache Hop" : string, gc.getFont(), gc.getFontRenderContext());
    Rectangle2D bounds = textLayout.getBounds();

    // Height is negative, don't like it.
    // I would rather have the label start at upper left, not lower left
    // Descent: The part below the text baseline (lower part of g,p,f,y, ...)
    //
    int descent = (int) textLayout.getDescent();
    int textWidth = (int) textLayout.getVisibleAdvance();
    int textHeight = (int) (bounds.getHeight() + descent);

    return new HTextGeometry(
        emptyString ? 0 : textWidth,
        textHeight,
        -(int) bounds.getX(),
        (int) (-bounds.getY() + textLayout.getDescent()));
  }

  /**
   * Enable the specified font on the gc
   *
   * @param gc
   * @param fontChoice The font to set
   */
  protected void enableFont(SVGGraphics2D gc, HFont fontChoice) {
    HFont font = fontChoice;
    if (font == null) {
      font = defaultFont;
    }

    if (font != null && StringUtils.isNotEmpty(font.getFontName())) {
      gc.setFont(createFont(font));
    }
  }

  /**
   * Gets log
   *
   * @return value of log
   */
  @JsonIgnore
  public ILogChannel getLogChannel() {
    return log;
  }

  /**
   * @param log The log to set
   */
  @JsonIgnore
  public void setLogChannel(ILogChannel log) {
    this.log = log;
  }
}
