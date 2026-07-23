package org.hopper.presentation.component.types.chart;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopValueException;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HColorRGB;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HTextGeometry;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;

@JsonDeserialize(as = HBarChartComponent.class)
@HComponentPlugin(
    id = "HBarChartComponent",
    name = "Bar Chart",
    description = "A bar chart component",
    image = "ui/images/components/bar-chart.svg")
@Getter
@Setter
public class HBarChartComponent extends HBaseChartComponent implements IHComponent {

  /** % of the width allocated for the horizontal value */
  @HWidgetElement(
      order = "12000-widthPercentage",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Width percentage")
  @HopMetadataProperty
  protected String widthPercentage;

  @HWidgetElement(
      order = "12100-showingFactValues",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show fact values?")
  @HopMetadataProperty
  protected boolean showingFactValues;

  /**
   * When true, each bar (horizontal category / part) is colored with {@link
   * IRenderContext#getStableColor} using the category label — the same key pie charts use for
   * slices. Enables matching colors across charts that share a category column/connector.
   */
  @HWidgetElement(
      order = "12200-usingStableCategoryColors",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Color bars by category (stable theme colors)",
      toolTip =
          "Use theme stable colors. With only a horizontal dimension, the category label is the "
              + "color key (matches pie slices). With horizontal + vertical dimensions (stacked "
              + "bars), each segment uses the horizontal-vertical combination as the color key.")
  @HopMetadataProperty
  protected boolean usingStableCategoryColors;

  /**
   * When true, horizontal axis labels are drawn at {@link #horizontalLabelAngle} degrees, left
   * aligned to the base of each bar (just below the X axis). Reduces overlap when there are many
   * categories or multi-field labels. The plot area shrinks so the angled text fits.
   */
  @HWidgetElement(
      order = "12300-usingAngledHorizontalLabels",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Angle horizontal labels?",
      toolTip =
          "Draw category labels at an angle under the axis (left-aligned to each bar base). "
              + "Useful when many labels would otherwise overlap.")
  @HopMetadataProperty
  protected boolean usingAngledHorizontalLabels;

  /**
   * Tilt of horizontal labels in degrees when {@link #usingAngledHorizontalLabels} is true.
   * Positive values slope down to the right (typical 30–60). Default 45.
   */
  @HWidgetElement(
      order = "12400-horizontalLabelAngle",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal label angle (degrees)",
      toolTip =
          "Angle in degrees for horizontal axis labels when angling is enabled (e.g. 45). "
              + "Larger angles need more vertical space under the chart.")
  @HopMetadataProperty
  protected String horizontalLabelAngle;

  public HBarChartComponent() {
    this((String) null);
  }

  public HBarChartComponent(String connectorName) {
    super("HBarChartComponent", connectorName);
    this.horizontalLabelAngle = "45";
  }

  public HBarChartComponent(HBarChartComponent c) {
    super("HBarChartComponent", c);
    this.widthPercentage = c.widthPercentage;
    this.showingFactValues = c.showingFactValues;
    this.usingStableCategoryColors = c.usingStableCategoryColors;
    this.usingAngledHorizontalLabels = c.usingAngledHorizontalLabels;
    this.horizontalLabelAngle = c.horizontalLabelAngle;
  }

  public HBarChartComponent clone() {
    return new HBarChartComponent(this);
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

    // We need totals in the bar chart, always on a zero baseline
    //
    usingZeroBaseline = true;
    usingTotalHeights = true;

    super.processSourceData(presentation, page, component, dataContext, renderContext, results);
  }

  @Override
  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {
    HGeometry componentGeometry = layoutResult.getGeometry();
    HComponent component = layoutResult.getComponent();
    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();

    // Get the theme...
    //
    HTheme theme = renderContext.lookupTheme(themeName);

    int x = componentGeometry.getX();
    int y = componentGeometry.getY();
    int width = componentGeometry.getWidth();
    int height = componentGeometry.getHeight();
    int tickSize = 4;

    if (isIncompleteChartConfig()) {
      renderIncompletePlaceholder(gc, componentGeometry, renderContext);
      return;
    }

    // Now get the horizontal dimension combinations
    //
    // Get all dimension combinations horizontally
    // Then sort this list of lists...
    //
    ChartDetails details = calculateDetails(gc, x, y, width, height);
    // Angled labels need a taller band under the axis → recompute plot scale
    if (showingHorizontalLabels && usingAngledHorizontalLabels) {
      adjustDetailsForAngledHorizontalLabels(details);
    }

    // Render the background and border to get started
    //
    drawBackGround(gc, componentGeometry, renderContext);
    drawBorder(gc, componentGeometry, renderContext);

    // Draw the title
    //
    if (StringUtils.isNotEmpty(titleText)) {
      int titleX = x + (width - details.titleGeometry.getWidth()) / 2;
      int titleY = y + verticalMargin + details.titleGeometry.getHeight();
      enableColor(gc, lookupTitleColor(renderContext));
      enableFont(gc, lookupTitleFont(renderContext));
      gc.drawString(titleText, titleX, titleY);
    }

    // Draw the X and Y axis
    //
    enableColor(gc, lookupAxisColor(renderContext));

    // top left X and Y
    //
    double topLeftX =
        x
            + horizontalMargin
            + (showingVerticalLabels ? details.maxFactWidth + horizontalMargin : 0);
    double topLeftY = y + verticalMargin + details.titleHeight;

    // bottom left X and Y
    //
    double bottomLeftX = topLeftX;
    double bottomLeftY =
        y
            + height
            - verticalMargin
            - (showingHorizontalLabels ? details.maxLabelHeight + verticalMargin : 0)
            - details.legendHeight;

    // Bar width used for label alignment (left edge of bar) and drawing
    double barWidth = details.partWidth * Const.toDouble(widthPercentage, 50) / 100;

    // bottom right X and Y
    //
    double bottomRightX = x + width - horizontalMargin;
    double bottomRightY = bottomLeftY;

    // X axis
    //
    gc.drawLine((int) bottomLeftX, (int) bottomLeftY, (int) bottomRightX, (int) bottomRightY);

    // Y axis
    //
    gc.drawLine((int) topLeftX, (int) topLeftY, (int) bottomLeftX, (int) bottomLeftY);

    // Draw the min value
    //
    double minX = topLeftX;
    double minY = bottomLeftY;

    HTextGeometry minGeo = details.minLabelGeometry;
    if (showingAxisTicks) {
      gc.drawLine(
          (int) (minX - tickSize / 2), (int) (minY), (int) (minX + tickSize / 2), (int) minY);
    }
    if (showingVerticalLabels) {
      enableFont(gc, lookupVerticalDimensionsFont(renderContext));
      gc.drawString(
          details.minLabel, (int) (x + horizontalMargin), (int) (minY + minGeo.getHeight() / 2));
    }

    // Draw the max value
    //
    double maxX = topLeftX;
    double maxY = topLeftY + details.overshoot;
    HTextGeometry maxGeo = details.maxLabelGeometry;
    if (showingAxisTicks) {
      gc.drawLine(
          (int) (maxX - tickSize / 2), (int) (maxY), (int) (maxX + tickSize / 2), (int) maxY);
    }
    if (showingHorizontalLabels) {
      enableFont(gc, lookupVerticalDimensionsFont(renderContext));
      gc.drawString(
          details.maxLabel, (int) (x + horizontalMargin), (int) (maxY + maxGeo.getHeight() / 2));
    }

    List<List<Double>> seriesXCoordinates = new ArrayList<>();
    List<List<Double>> seriesFactValues = new ArrayList<>();
    List<String> seriesLabels = new ArrayList<>();

    // Loop over the vertical dimension value combinations
    // This is the chart series...
    //
    List<List<String>> verticalCombinations = new ArrayList<>();
    verticalCombinations.addAll(details.verticalCombinations);
    if (verticalCombinations.isEmpty()) {
      // At least perform once without vertical dimensions...
      //
      verticalCombinations.add(new ArrayList<>());
    }

    int nrParts = details.labels.size();
    int nrSeries = verticalCombinations.size();

    for (int series = 0; series < verticalCombinations.size(); series++) {
      List<String> verticalCombination = verticalCombinations.get(series);

      List<Double> sXCoordinates = new ArrayList<>();
      List<Double> sFactValues = new ArrayList<>();

      // Draw the parts: one for every bottom horizontal label
      //
      double lastX = -1;
      double lastY = -1;
      for (int part = 0; part < nrParts; part++) {
        // Only draw this label once
        if (series == 0) {
          String label = details.labels.get(part);
          HTextGeometry geometry = details.labelGeometries.get(part);

          if (showingHorizontalLabels) {
            enableColor(gc, lookupDefaultColor(renderContext));
            enableFont(gc, lookupHorizontalDimensionsFont(renderContext));
            if (usingAngledHorizontalLabels) {
              drawAngledHorizontalLabel(
                  gc, label, geometry, bottomLeftX, bottomLeftY, part, details.partWidth, barWidth);
            } else {
              // Centered under the category slot
              double labelX =
                  bottomLeftX
                      + part * details.partWidth
                      + (details.partWidth - geometry.getWidth()) / 2
                      + geometry.getOffsetX();
              double labelY = bottomLeftY + verticalMargin + geometry.getOffsetY();
              gc.drawString(label, (int) labelX, (int) labelY);
            }
          }

          // Draw a small tick at the end of the part
          //
          if (showingAxisTicks) {
            double tickX = bottomLeftX + part * details.partWidth + details.partWidth;
            double tickY = bottomLeftY - tickSize / 2;
            enableColor(gc, lookupAxisColor(renderContext));
            gc.drawLine((int) tickX, (int) tickY, (int) tickX, (int) (tickY + tickSize));
          }
        }

        // Draw the fact series...
        //
        List<String> factLabels = details.factLabels.get(series);
        List<Object> factValues = details.factValues.get(series);
        List<IValueMeta> factValueMetas = details.factValueMetas.get(series);

        Object valueData = factValues.get(part);
        IValueMeta valueMeta = factValueMetas.get(part);
        double factValue = 0;
        try {
          // Missing pivot cell (e.g. multi-dimension combo with nulls) or null fact → 0
          Double factValueDouble = valueMeta.getNumber(valueData);
          if (factValueDouble == null) {
            factValue = 0.0d;
          } else {
            factValue = factValueDouble.doubleValue();
          }
        } catch (HopValueException e) {
          throw new HException("Fact data conversion error", e);
        }
        double factX = bottomLeftX + part * details.partWidth + details.partWidth / 2;

        sXCoordinates.add(factX);
        sFactValues.add(factValue);
      }

      seriesXCoordinates.add(sXCoordinates);
      seriesFactValues.add(sFactValues);
      seriesLabels.add(getCombinationLabel(verticalCombination));
    }

    // Now draw the stacked charts...
    // We do this by this time going over the parts..
    //

    for (int part = 0; part < nrParts; part++) {

      double lowY = bottomLeftY;

      for (int series = 0; series < nrSeries; series++) {

        double factValue = seriesFactValues.get(series).get(part);
        double factX = seriesXCoordinates.get(series).get(part);

        double valueHeigth = factValue * details.valueFactor;
        double topY = lowY - valueHeigth;
        double leftX = factX - barWidth / 2;

        // Theme colors:
        // - stable category + only horizontal dims: key = horizontal label (match pie slices)
        // - stable category + stacked (horiz + vert dims): key = horizontal + vertical combination
        // - otherwise: key = vertical series label
        //
        if (theme == null) {
          enableColor(gc, lookupDefaultColor(renderContext));
        } else {
          String horizontalLabel =
              part < details.labels.size() ? details.labels.get(part) : "";
          String seriesLabel = series < seriesLabels.size() ? seriesLabels.get(series) : "";
          String labelValue =
              resolveBarStableColorKey(usingStableCategoryColors, horizontalLabel, seriesLabel);
          HColorRGB color = renderContext.getStableColor(theme.getName(), labelValue);
          enableColor(gc, color);
        }

        gc.drawRect(
            (int) Math.round(leftX),
            (int) Math.round(topY),
            (int) Math.round(barWidth),
            (int) Math.round(valueHeigth));
        gc.fillRect(
            (int) Math.round(leftX),
            (int) Math.round(topY),
            (int) Math.round(barWidth),
            (int) Math.round(valueHeigth));

        // shift the low level
        //
        lowY = topY;
      }
    }

    // Legend: category labels when coloring by category, otherwise vertical series
    //
    if (!showingLegend) {
      return;
    }

    double legendX = x + horizontalMargin;
    double legendY = bottomLeftY + details.maxLabelHeight + 2 * verticalMargin;
    double legendEntryWidth =
        details.maxLegendLabelWidth + 2 * horizontalMargin + details.legendMarkerSize;

    // Do we have fewer columns than we can fit?  In that case, center the legend
    //
    if (details.maxNrLegendColumns > details.nrLegendColumns) {
      double emptySpace = (details.maxNrLegendColumns - details.nrLegendColumns) * legendEntryWidth;
      legendX += emptySpace / 2;
    }

    String themeName = null;
    if (theme != null) {
      themeName = theme.getName();
    }

    // Legend keys must match resolveBarStableColorKey() so swatches match stack segments
    List<String> legendLabels =
        buildBarLegendColorKeys(usingStableCategoryColors, details.labels, seriesLabels);

    int colNr = 0;
    int rowNr = 0;
    for (String legendLabel : legendLabels) {
      if (legendLabel == null) {
        legendLabel = "";
      }

      double labelX = legendX + colNr * (legendEntryWidth);
      double labelY = legendY + rowNr * (details.maxLegendLabelHeight + verticalMargin);

      HColorRGB color = renderContext.getStableColor(themeName, legendLabel);
      if (color == null) {
        color = lookupDefaultColor(renderContext);
      }
      if (color == null) {
        color = HColorRGB.BLACK;
      }

      // This is the legend color...
      //
      enableColor(gc, color);

      // Let's fill a small circle
      //
      gc.fillOval(
          (int) labelX,
          (int) labelY + (details.maxLegendLabelHeight - details.legendMarkerSize) / 2,
          (int) details.legendMarkerSize,
          (int) details.legendMarkerSize);

      // Print the label in the default color
      //
      enableColor(gc, lookupDefaultColor(renderContext));

      gc.drawString(
          legendLabel,
          (int) (labelX + details.legendMarkerSize + horizontalMargin / 2),
          (int) (labelY + details.maxLegendLabelHeight));

      // Switch to the next position
      //
      colNr++;
      if (colNr >= details.nrLegendColumns) {
        colNr = 0;
        rowNr++;
      }
    }
  }

  /**
   * Stable-color lookup key for a bar (segment).
   *
   * <ul>
   *   <li>Stable colors + horizontal only → horizontal label (aligns with pie categories)
   *   <li>Stable colors + stacked (horiz + vert) → {@code horizontal-vertical} combination
   *   <li>Stable colors off → vertical series label
   * </ul>
   */
  private String resolveBarStableColorKey(
      boolean stableCategoryColors, String horizontalLabel, String seriesLabel) {
    String h = horizontalLabel != null ? horizontalLabel : "";
    String v = seriesLabel != null ? seriesLabel : "";
    if (!stableCategoryColors) {
      return v;
    }
    // Stacked: both a category on the axis and a series within the bar
    if (StringUtils.isNotEmpty(h) && StringUtils.isNotEmpty(v)) {
      return getCombinationString(java.util.Arrays.asList(h, v));
    }
    if (StringUtils.isNotEmpty(h)) {
      return h;
    }
    return v;
  }

  /**
   * Legend entries use the same keys as stack segment fills. For stacked + stable category colors,
   * one entry per horizontal×vertical combination; otherwise horizontal labels or series labels.
   */
  private List<String> buildBarLegendColorKeys(
      boolean stableCategoryColors, List<String> horizontalLabels, List<String> seriesLabels) {
    List<String> horiz = horizontalLabels != null ? horizontalLabels : java.util.Collections.emptyList();
    List<String> series = seriesLabels != null ? seriesLabels : java.util.Collections.emptyList();
    boolean hasSeries =
        series.stream().anyMatch(s -> s != null && !s.isEmpty());

    if (!stableCategoryColors) {
      return new ArrayList<>(series);
    }
    if (hasSeries && !horiz.isEmpty()) {
      List<String> keys = new ArrayList<>();
      for (String h : horiz) {
        for (String v : series) {
          keys.add(resolveBarStableColorKey(true, h, v));
        }
      }
      return keys;
    }
    if (!horiz.isEmpty()) {
      return new ArrayList<>(horiz);
    }
    return new ArrayList<>(series);
  }

  /**
   * Resolved label tilt in degrees (absolute), clamped so geometry stays finite.
   * Default 45 when unset / invalid.
   */
  private double resolvedHorizontalLabelAngleDegrees() {
    double angle = Math.abs(Const.toDouble(horizontalLabelAngle, 45.0));
    if (angle < 1.0) {
      angle = 1.0;
    }
    if (angle > 89.0) {
      angle = 89.0;
    }
    return angle;
  }

  /**
   * Enlarge the bottom label band for rotated text and recompute {@link ChartDetails#partHeight}
   * / {@link ChartDetails#valueFactor} so bars stay inside the plot area.
   */
  private void adjustDetailsForAngledHorizontalLabels(ChartDetails details) {
    double angleDeg = resolvedHorizontalLabelAngleDegrees();
    double theta = Math.toRadians(angleDeg);
    double sin = Math.abs(Math.sin(theta));
    double cos = Math.abs(Math.cos(theta));

    double maxProjected = 0;
    if (details.labelGeometries != null) {
      for (HTextGeometry g : details.labelGeometries) {
        if (g == null) {
          continue;
        }
        // Axis-aligned bounding box height of a W×H rectangle rotated by θ
        double projected = g.getWidth() * sin + g.getHeight() * cos;
        if (projected > maxProjected) {
          maxProjected = projected;
        }
      }
    }
    // Always at least as tall as the unrotated band
    if (maxProjected < details.maxLabelHeight) {
      maxProjected = details.maxLabelHeight;
    }
    details.maxLabelHeight = maxProjected;

    // Mirror HBaseChartComponent.calculateDetails plot-height math
    details.partHeight =
        (details.height
            - verticalMargin * 3
            - details.maxLabelHeight
            - details.overshoot * 2
            - details.titleHeight
            - details.legendHeight);
    if (usingZeroBaseline) {
      details.partHeight += details.overshoot;
    }
    if (details.partHeight < 1) {
      details.partHeight = 1;
    }
    details.valueRange = details.maxValue - details.minValue;
    if (details.valueRange == 0.0) {
      details.valueFactor = 0.0;
    } else {
      details.valueFactor = details.partHeight / details.valueRange;
    }
  }

  /**
   * Draw one category label at an angle, left-aligned to the left edge of the bar base, just below
   * the horizontal axis (sloping down to the right).
   *
   * <p>Java2D: a positive rotation moves the +x axis toward +y. With screen y increasing downward,
   * that is a clockwise visual turn — labels slope down under the chart, not up into the bars.
   */
  private void drawAngledHorizontalLabel(
      SVGGraphics2D gc,
      String label,
      HTextGeometry geometry,
      double bottomLeftX,
      double bottomLeftY,
      int part,
      double partWidth,
      double barWidth) {
    if (label == null) {
      label = "";
    }
    // Left edge of the bar (same formula as fillRect later)
    double barLeftX = bottomLeftX + part * partWidth + (partWidth - barWidth) / 2;
    // Just under the axis — attachment is the top-left of the label band
    double baseY = bottomLeftY + Math.max(2, verticalMargin);

    // Positive degrees → slope down to the right (see class comment above)
    double rotRad = Math.toRadians(resolvedHorizontalLabelAngleDegrees());

    AffineTransform previous = gc.getTransform();
    try {
      gc.translate(barLeftX, baseY);
      gc.rotate(rotRad);
      // Left-aligned: baseline offset so the top of the glyphs sits near the axis attachment
      int textY = geometry != null ? geometry.getOffsetY() : 0;
      if (textY <= 0 && geometry != null) {
        textY = geometry.getHeight();
      }
      gc.drawString(label, 0, textY);
    } finally {
      gc.setTransform(previous);
    }
  }
}
