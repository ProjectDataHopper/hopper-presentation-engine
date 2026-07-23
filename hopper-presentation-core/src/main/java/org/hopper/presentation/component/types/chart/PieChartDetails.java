package org.hopper.presentation.component.types.chart;

import java.util.ArrayList;
import java.util.List;
import org.hopper.core.HTextGeometry;

/**
 * Runtime geometry and slice data for {@link HPieChartComponent}. Not persisted in metadata.
 */
public class PieChartDetails {

  public int x;
  public int y;
  public int width;
  public int height;

  public HTextGeometry titleGeometry;
  public int titleHeight;

  public double total;
  public final List<PieSlice> slices = new ArrayList<>();

  public double cx;
  public double cy;
  public double outerRadius;
  public double innerRadius;

  // Legend layout
  public List<String> legendLabels = new ArrayList<>();
  public List<HTextGeometry> legendLabelGeos = new ArrayList<>();
  public int maxLegendLabelWidth;
  public int maxLegendLabelHeight;
  public int legendMarkerSize;
  public int legendWidth;
  public int legendHeight;
  public int nrLegendColumns;
  public int nrLegendRows;
  public int maxNrLegendColumns;
  public double legendAreaX;
  public double legendAreaY;

  public PieChartDetails(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  /** One pie/donut slice after aggregation. */
  public static class PieSlice {
    public String label;
    public double value;
    public double percentage;
    public String formattedValue;
    public String displayLabel;
    /** Arc2D start angle in degrees (0 = east, positive = counter-clockwise). */
    public double startAngleDegrees;
    /** Arc2D extent in degrees (negative = clockwise). */
    public double extentDegrees;
    public HTextGeometry labelGeometry;
  }
}
