package org.hopper.presentation.component.types.chart;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.BasicStroke;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
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
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.interaction.HInteractionLocationOption;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;

@JsonDeserialize(as = HLineChartComponent.class)
@HComponentPlugin(
    id = "HLineChartComponent",
    name = "Line chart",
    description = "A line chart component",
    image = "ui/images/components/line-chart.svg")
@Getter
@Setter
public class HLineChartComponent extends HBaseChartComponent implements IHComponent {

  @HWidgetElement(
      order = "12000-drawingCurvedTrendLine",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Draw curved trend line?")
  @HopMetadataProperty
  protected boolean drawingCurvedTrendLine;

  /**
   * When true, horizontal axis labels are drawn at {@link #horizontalLabelAngle} degrees,
   * left-aligned to each category position (just below the X axis). Reduces overlap when there are
   * many categories or multi-field labels. The plot area shrinks so the angled text fits.
   */
  @HWidgetElement(
      order = "12300-usingAngledHorizontalLabels",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Angle horizontal labels?",
      toolTip =
          "Draw category labels at an angle under the axis (left-aligned to each category). "
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

  public HLineChartComponent() {
    this((String) null);
  }

  public HLineChartComponent(String connectorName) {
    super("HLineChartComponent", connectorName);
    this.horizontalLabelAngle = "45";
  }

  public HLineChartComponent(HLineChartComponent c) {
    super("HLineChartComponent", c);
    drawingCurvedTrendLine = c.drawingCurvedTrendLine;
    this.usingAngledHorizontalLabels = c.usingAngledHorizontalLabels;
    this.horizontalLabelAngle = c.horizontalLabelAngle;
  }

  public HLineChartComponent clone() {
    return new HLineChartComponent(this);
  }

  @Override
  public List<HInteractionLocationOption> getPossibleInteractionLocations() {
    List<String> hDims = horizontalDimensionColumnNames();
    List<String> vDims = verticalDimensionColumnNames();
    List<HInteractionLocationOption> options = new ArrayList<>();
    options.add(
        HInteractionLocationOption.item(
            "series",
            "Series label",
            DrawnItem.Category.ChartSeriesLabel,
            vDims.isEmpty() ? hDims : vDims,
            true));
    options.add(
        HInteractionLocationOption.item(
            "x-axis",
            "X-axis label",
            DrawnItem.Category.XAxisLabel,
            hDims,
            true));
    options.add(
        HInteractionLocationOption.item(
            "y-axis",
            "Y-axis label",
            DrawnItem.Category.YAxisLabel,
            List.of(),
            false));
    options.add(HInteractionLocationOption.item("title", "Title", DrawnItem.Category.Title));
    return options;
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
    List<DrawnItem> drawnItems = layoutResult.getRenderPage().getDrawnItems();

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

      drawnItems.add(
          new DrawnItem(
              component.getName(),
              component.getComponent().getPluginId(),
              layoutResult.getPartNumber(),
              DrawnItem.DrawnItemType.ComponentItem,
              DrawnItem.Category.Title.name(),
              0,
              0,
              new HGeometry(
                  offSet.getX() + titleX,
                  offSet.getY() + titleY - details.titleGeometry.getHeight(),
                  details.titleGeometry.getWidth(),
                  details.titleGeometry.getHeight()),
              new DrawnContext(titleText)));
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
            - (showingHorizontalLabels ? details.maxLabelHeight + verticalMargin : 0);

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
    if (!usingZeroBaseline) {
      minY -= details.overshoot;
    }
    HTextGeometry minGeo = details.minLabelGeometry;
    if (showingAxisTicks) {
      gc.drawLine(
          (int) (minX - tickSize / 2), (int) (minY), (int) (minX + tickSize / 2), (int) minY);
    }
    if (showingVerticalLabels && StringUtils.isNotEmpty(details.minLabel)) {
      enableFont(gc, lookupVerticalDimensionsFont(renderContext));
      int labelX = x + horizontalMargin;
      int labelY = (int) (minY + minGeo.getHeight() / 2);
      gc.drawString(details.minLabel, labelX, labelY);

      drawnItems.add(
          new DrawnItem(
              component.getName(),
              component.getComponent().getPluginId(),
              layoutResult.getPartNumber(),
              DrawnItem.DrawnItemType.ComponentItem,
              DrawnItem.Category.YAxisLabel.name(),
              0,
              0,
              new HGeometry(
                  offSet.getX() + labelX,
                  offSet.getY() + labelY - details.minLabelGeometry.getHeight(),
                  details.minLabelGeometry.getWidth(),
                  details.minLabelGeometry.getHeight()),
              new DrawnContext(details.minLabel)));
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
    if (showingVerticalLabels && StringUtils.isNotEmpty(details.maxLabel)) {
      enableFont(gc, lookupVerticalDimensionsFont(renderContext));
      int labelX = x + horizontalMargin;
      int labelY = (int) (maxY + maxGeo.getHeight() / 2);
      gc.drawString(details.maxLabel, labelX, labelY);

      drawnItems.add(
          new DrawnItem(
              component.getName(),
              component.getComponent().getPluginId(),
              layoutResult.getPartNumber(),
              DrawnItem.DrawnItemType.ComponentItem,
              DrawnItem.Category.YAxisLabel.name(),
              0,
              0,
              new HGeometry(
                  offSet.getX() + labelX,
                  offSet.getY() + labelY - details.maxLabelGeometry.getHeight(),
                  details.maxLabelGeometry.getWidth(),
                  details.maxLabelGeometry.getHeight()),
              new DrawnContext(details.maxLabel)));
    }

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

    // Keep the location for the label...
    //
    Point2D.Double labelPoint = null;
    int horizontalLabelsDrawn = 1;

    for (int series = 0; series < verticalCombinations.size(); series++) {
      List<String> verticalCombination = verticalCombinations.get(series);

      List<Double> xCoordinates = new ArrayList<>();
      List<Double> yCoordinates = new ArrayList<>();

      // Draw the parts: one for every bottom horizontal label
      //
      double lastX = -1;
      double lastY = -1;
      for (int part = 0; part < details.labels.size(); part++) {
        // Only draw this label once
        if (series == 0) {
          String label = details.labels.get(part);
          HTextGeometry geometry = details.labelGeometries.get(part);

          if (showingHorizontalLabels) {
            if (actualHorizontalLabelInterval <= 0
                || (horizontalLabelsDrawn % actualHorizontalLabelInterval == 0)) {
              enableColor(gc, lookupDefaultColor(renderContext));
              enableFont(gc, lookupHorizontalDimensionsFont(renderContext));

              double labelX;
              double labelY;
              if (usingAngledHorizontalLabels) {
                // Left-aligned to category position (center of slot = data point), under axis
                labelX = bottomLeftX + part * details.partWidth + details.partWidth / 2;
                labelY = bottomLeftY + Math.max(2, verticalMargin);
                drawAngledHorizontalLabel(gc, label, geometry, labelX, labelY);
              } else {
                // Centered under the category slot
                labelX =
                    bottomLeftX
                        + part * details.partWidth
                        + (details.partWidth - geometry.getWidth()) / 2
                        + geometry.getOffsetX();
                labelY = bottomLeftY + verticalMargin + geometry.getOffsetY();
                gc.drawString(label, (int) labelX, (int) labelY);
              }

              drawnItems.add(
                  new DrawnItem(
                      component.getName(),
                      component.getComponent().getPluginId(),
                      layoutResult.getPartNumber(),
                      DrawnItem.DrawnItemType.ComponentItem,
                      DrawnItem.Category.XAxisLabel.name(),
                      0,
                      0,
                      new HGeometry(
                          (int) (offSet.getX() + labelX),
                          (int) (offSet.getY() + labelY - geometry.getHeight()),
                          geometry.getWidth(),
                          geometry.getHeight()),
                      new DrawnContext(label)));
            }
            horizontalLabelsDrawn++;
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
        // List<String> factLabels = details.factLabels.get(series);
        List<Object> factValues = details.factValues.get(series);
        List<IValueMeta> factValueMetas = details.factValueMetas.get(series);

        Object valueData = factValues.get(part);
        IValueMeta valueMeta = factValueMetas.get(part);
        double factValue = 0;
        try {
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
        double factY =
            topLeftY + details.overshoot + ((details.maxValue - factValue) * details.valueFactor);

        // See if we need to set a specific theme scheme for the line...
        //
        if (theme == null) {
          enableColor(gc, lookupDefaultColor(renderContext));
        } else {
          // Color is depending on the series we're drawing...
          //
          String labelValue = getCombinationString(verticalCombination);
          HColorRGB color = renderContext.getStableColor(theme.getName(), labelValue);
          enableColor(gc, color);
        }

        if (dotSize > 0) {
          gc.drawRect(
              (int) (factX - dotSize / 2),
              (int) (factY - dotSize / 2),
              (int) dotSize,
              (int) dotSize);
        }

        xCoordinates.add(factX);
        yCoordinates.add(factY);

        // Keep the location of the last dot in a series to put the label on it...
        //
        if (!verticalCombination.isEmpty() && part == details.labels.size() - 1) {

          labelPoint = new Point2D.Double(factX, factY);
        }
      }

      Stroke stroke = gc.getStroke();
      float lw = (float) Const.toDouble(Const.NVL(lineWidth, "1.0"), 1.0d);

      if (isDrawingCurvedTrendLine() && xCoordinates.size() > 2) {
        // Quarter of the line width, it's a trend...
        //
        gc.setStroke(new BasicStroke(lw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

        // SplineInterpolator requires strictly increasing X. Preview/layout can produce
        // out-of-order or duplicate abscissae (e.g. after re-layout at different size).
        double[][] sorted = sortStrictlyIncreasingX(xCoordinates, yCoordinates);
        double[] xs = sorted[0];
        double[] ys = sorted[1];

        if (xs.length < 3) {
          // Not enough distinct X values for a cubic spline — fall back to polylines
          drawStraightSegments(gc, xCoordinates, yCoordinates);
        } else {
          try {
            SplineInterpolator splineInterpolator = new SplineInterpolator();
            PolynomialSplineFunction function = splineInterpolator.interpolate(xs, ys);

            // TODO: make the number of curve points configurable...
            //
            int nrValues = xs.length * 30;
            double[] xi = new double[nrValues];
            xi[0] = xs[0];
            xi[xi.length - 1] = xs[xs.length - 1];
            double diff = xs[xs.length - 1] - xi[0];
            double delta = diff / nrValues;
            for (int i = 1; i < xi.length - 1; i++) {
              xi[i] = xs[0] + delta * i;
            }

            double lx = xs[0];
            double ly = ys[0];

            Path2D path = new Path2D.Double();
            path.moveTo(lx, ly);

            for (int i = 1; i < xi.length; i++) {
              // Clamp into the spline domain [xs[0], xs[last]]
              double xq = Math.min(xs[xs.length - 1], Math.max(xs[0], xi[i]));
              double yi = function.value(xq);
              path.lineTo(xq, yi);
              lx = xq;
              ly = yi;
            }
            gc.draw(path);
          } catch (Exception e) {
            // Any interpolation failure: still show the series as straight segments
            drawStraightSegments(gc, xCoordinates, yCoordinates);
          }
        }
        gc.setStroke(stroke);

      } else {

        // Draw the actual line
        //
        gc.setStroke(new BasicStroke(lw, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        drawStraightSegments(gc, xCoordinates, yCoordinates);
        gc.setStroke(stroke);
      }

      if (labelPoint != null) {
        // At the end, draw the series name...
        //
        double factX = labelPoint.x - dotSize - 2;
        double factY = labelPoint.y - dotSize - 2;

        String seriesLabel = getCombinationString(verticalCombination);

        gc.drawString(seriesLabel, (int) factX, (int) factY);

        HTextGeometry seriesGeometry = calculateTextGeometry(gc, seriesLabel);

        drawnItems.add(
            new DrawnItem(
                component.getName(),
                component.getComponent().getPluginId(),
                layoutResult.getPartNumber(),
                DrawnItem.DrawnItemType.ComponentItem,
                DrawnItem.Category.ChartSeriesLabel.name(),
                0,
                0,
                new HGeometry(
                    (int) (offSet.getX() + factX),
                    (int) (offSet.getY() + factY - seriesGeometry.getHeight()),
                    seriesGeometry.getWidth(),
                    seriesGeometry.getHeight()),
                new DrawnContext(
                    dimensionColumnsForNames(
                        verticalDimensionColumnNames().isEmpty()
                            ? horizontalDimensionColumnNames()
                            : verticalDimensionColumnNames()),
                    seriesLabel)));
      }
    }
  }

  /**
   * Sort series points by ascending X and collapse duplicate X values (average Y) so that {@link
   * SplineInterpolator} does not throw {@code NonMonotonicSequenceException}.
   *
   * @return {@code [xs, ys]} with length &gt;= 0 and strictly increasing {@code xs}
   */
  private static double[][] sortStrictlyIncreasingX(
      List<Double> xCoordinates, List<Double> yCoordinates) {
    int n = xCoordinates.size();
    Integer[] order = new Integer[n];
    for (int i = 0; i < n; i++) {
      order[i] = i;
    }
    java.util.Arrays.sort(
        order, (a, b) -> Double.compare(xCoordinates.get(a), xCoordinates.get(b)));

    List<Double> xs = new ArrayList<>();
    List<Double> ys = new ArrayList<>();
    double lastX = Double.NaN;
    for (int i = 0; i < order.length; i++) {
      int idx = order[i];
      double x = xCoordinates.get(idx);
      double y = yCoordinates.get(idx);
      if (xs.isEmpty()) {
        xs.add(x);
        ys.add(y);
        lastX = x;
      } else if (x > lastX) {
        xs.add(x);
        ys.add(y);
        lastX = x;
      } else if (x == lastX) {
        // Keep a single knot for this X (average Y)
        int last = ys.size() - 1;
        ys.set(last, (ys.get(last) + y) / 2.0);
      }
    }
    double[] xa = new double[xs.size()];
    double[] ya = new double[ys.size()];
    for (int i = 0; i < xs.size(); i++) {
      xa[i] = xs.get(i);
      ya[i] = ys.get(i);
    }
    return new double[][] {xa, ya};
  }

  private static void drawStraightSegments(
      SVGGraphics2D gc, List<Double> xCoordinates, List<Double> yCoordinates) {
    if (xCoordinates == null || xCoordinates.size() < 2) {
      return;
    }
    int[] xs = new int[xCoordinates.size()];
    int[] ys = new int[yCoordinates.size()];
    for (int i = 0; i < xs.length; i++) {
      xs[i] = xCoordinates.get(i).intValue();
      ys[i] = yCoordinates.get(i).intValue();
    }
    gc.drawPolyline(xs, ys, xs.length);
  }

  /**
   * Resolved label tilt in degrees (absolute), clamped so geometry stays finite. Default 45 when
   * unset / invalid.
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
   * Enlarge the bottom label band for rotated text and recompute {@link ChartDetails#partHeight} /
   * {@link ChartDetails#valueFactor} so the plot stays inside the component.
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
   * Draw one category label at an angle, left-aligned at the category position, just below the
   * horizontal axis (sloping down to the right).
   *
   * <p>Java2D: a positive rotation moves the +x axis toward +y. With screen y increasing downward,
   * that is a clockwise visual turn — labels slope down under the chart.
   */
  private void drawAngledHorizontalLabel(
      SVGGraphics2D gc,
      String label,
      HTextGeometry geometry,
      double anchorX,
      double anchorY) {
    if (label == null) {
      label = "";
    }
    double rotRad = Math.toRadians(resolvedHorizontalLabelAngleDegrees());

    AffineTransform previous = gc.getTransform();
    try {
      gc.translate(anchorX, anchorY);
      gc.rotate(rotRad);
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
