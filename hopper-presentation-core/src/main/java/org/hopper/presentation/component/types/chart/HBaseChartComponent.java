package org.hopper.presentation.component.types.chart;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HGeometry;
import org.hopper.core.HSize;
import org.hopper.core.HTextGeometry;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.types.crosstab.HBaseAggregatingComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

@Getter
@Setter
public abstract class HBaseChartComponent extends HBaseAggregatingComponent
    implements IHComponent {

  @HWidgetElement(
      order = "10000-title",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Title")
  @HopMetadataProperty
  protected String title;

  @HWidgetElement(
      order = "10100-horizontalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal margin")
  @HopMetadataProperty
  protected int horizontalMargin;

  @HWidgetElement(
      order = "10200-verticalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical margin")
  @HopMetadataProperty
  protected int verticalMargin;

  @HWidgetElement(
      order = "10300-showingHorizontalLabels",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show horizontal labels?")
  @HopMetadataProperty
  protected boolean showingHorizontalLabels;

  @HWidgetElement(
      order = "10400-showingVerticalLabels",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show vertical labels?")
  @HopMetadataProperty
  protected boolean showingVerticalLabels;

  @HWidgetElement(
      order = "10500-showingAxisTicks",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show axis ticks?")
  @HopMetadataProperty
  protected boolean showingAxisTicks;

  @HWidgetElement(
      order = "10600-dotSize",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Dot size")
  @HopMetadataProperty
  protected int dotSize;

  @HWidgetElement(
      order = "10700-lineWidth",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Line width")
  @HopMetadataProperty
  protected String lineWidth;

  @HWidgetElement(
      order = "10800-usingZeroBaseline",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Use zero baseline?")
  @HopMetadataProperty
  protected boolean usingZeroBaseline;

  @HWidgetElement(
      order = "10900-showingLegend",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show legend?")
  @HopMetadataProperty
  protected boolean showingLegend;

  @HWidgetElement(
      order = "11000-horizontalLabelInterval",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal label interval")
  @HopMetadataProperty
  protected String horizontalLabelInterval;

  // Calculated at runtime
  //
  @JsonIgnore protected transient String titleText;
  @JsonIgnore protected transient boolean usingTotalHeights;
  @JsonIgnore protected transient int actualHorizontalLabelInterval;

  public HBaseChartComponent(String pluginId, String connectorName) {
    super(pluginId);
    this.sourceConnectorName = connectorName;
    horizontalDimensions = new ArrayList<>();
    verticalDimensions = new ArrayList<>();
    facts = new ArrayList<>();
    showingHorizontalLabels = true;
    showingVerticalLabels = true;
    showingAxisTicks = true;
    dotSize = 6;
  }

  public HBaseChartComponent(String pluginId, HBaseChartComponent c) {
    super(pluginId, c);
    this.horizontalMargin = c.horizontalMargin;
    this.verticalMargin = c.verticalMargin;
    this.showingHorizontalLabels = c.showingHorizontalLabels;
    this.showingVerticalLabels = c.showingVerticalLabels;
    this.showingAxisTicks = c.showingAxisTicks;
    this.dotSize = c.dotSize;
    this.title = c.title;
    this.themeName = c.themeName;
    this.lineWidth = c.lineWidth;
  }

  public void processSourceData(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {
    // Calculate the title based on data context (even when data is not yet configured)
    //
    titleText =
        dataContext != null && dataContext.getVariables() != null
            ? dataContext.getVariables().resolve(title)
            : title;
    actualHorizontalLabelInterval =
        Const.toInt(
            dataContext != null && dataContext.getVariables() != null
                ? dataContext.getVariables().resolve(horizontalLabelInterval)
                : horizontalLabelInterval,
            0);

    // Newly dropped / incomplete charts: no connector yet — stay empty, still layout/render
    if (StringUtils.isBlank(sourceConnectorName)) {
      return;
    }

    // Read the data
    //
    HConnector connector = dataContext.getConnector(sourceConnectorName);
    if (connector == null) {
      // Keep presentation renderable; properties form can fix the connector name
      return;
    }

    // Incomplete dimensions/facts (fresh palette drop) — do not fail the whole page
    try {
      validateSettings();
    } catch (HException e) {
      return;
    }

    // Get the rows
    //
    connector
        .getConnector()
        .addRowListener(
            (rowMeta, rowData) -> {
              if (rowData != null) {
                // Pivot the row data...
                //
                pivotRow(rowMeta, rowData);
              }
            });

    var trace = dataContext.getExecutionTrace();
    if (trace != null && !trace.isNoop() && connector.getName() != null) {
      trace.pushConnectorName(connector.getName());
    }
    try {
      connector.getConnector().startStreaming(dataContext);
      connector.getConnector().waitUntilFinished();
    } finally {
      if (trace != null && !trace.isNoop() && connector.getName() != null) {
        trace.popConnectorName();
      }
    }
  }

  protected void validateSettings() throws HException {

    // Validate some metadata...
    //
    for (HFact fact : facts) {
      if (StringUtils.isEmpty(fact.getColumnName())) {
        throw new HException("No column name given for a fact");
      }
      if (fact.getAggregationMethod() == null) {
        throw new HException(
            "No aggregation method specified for fact column '" + fact.getColumnName() + "'");
      }
    }
    for (HDimension dimension : horizontalDimensions) {
      if (StringUtils.isEmpty(dimension.getColumnName())) {
        throw new HException("No column name given for a horizontal dimension");
      }
    }
    for (HDimension dimension : verticalDimensions) {
      if (StringUtils.isEmpty(dimension.getColumnName())) {
        throw new HException("No column name given for a vertical dimension");
      }
    }
  }

  /**
   * 1. First
   *
   * <p>Calculate the imageSize of the table, pretty much calculating the sizes of each element in
   * the data grid We store all the information in the Results data set
   *
   * @param presentation
   * @param page
   * @param component
   * @param dataContext
   * @param results
   * @return
   */
  public HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results) {

    // Incomplete / palette-dropped charts have no natural size from data; provide a default
    // so layout (and hover/selection rects) get a real width/height when only left/top are set.
    if (isIncompleteChartConfig()) {
      return new HSize(400, 260);
    }

    // Otherwise size is driven by layout attachments (left/top/right/bottom)
    //
    return null;
  }

  protected String getCombinationString(List<String> combinationList) {
    StringBuilder combo = new StringBuilder();
    for (String combination : combinationList) {
      if (combo.length() > 0) {
        combo.append("-");
      }
      combo.append(combination);
    }
    return combo.toString();
  }

  /** True when the chart has no usable data configuration yet (palette drop / incomplete form). */
  protected boolean isIncompleteChartConfig() {
    return StringUtils.isBlank(sourceConnectorName) || facts == null || facts.isEmpty();
  }

  /**
   * Draw an empty chart frame when connector/facts are not configured yet so palette-drop does not
   * break the presentation render.
   */
  protected void renderIncompletePlaceholder(
      SVGGraphics2D gc, HGeometry componentGeometry, IRenderContext renderContext)
      throws HException {
    drawBackGround(gc, componentGeometry, renderContext);
    // Force a visible border for empty components even when border flag is off
    boolean oldBorder = border;
    border = true;
    try {
      drawBorder(gc, componentGeometry, renderContext);
    } finally {
      border = oldBorder;
    }
    enableColor(gc, lookupDefaultColor(renderContext));
    enableFont(gc, lookupDefaultFont(renderContext));
    String msg =
        StringUtils.isNotBlank(titleText)
            ? titleText
            : (StringUtils.isNotBlank(title) ? title : "Chart");
    String hint =
        StringUtils.isBlank(sourceConnectorName)
            ? msg + " (set connector)"
            : msg + " (configure dimensions/facts)";
    int tx = componentGeometry.getX() + 8;
    int ty = componentGeometry.getY() + 20;
    gc.drawString(hint, tx, ty);
  }

  protected ChartDetails calculateDetails(SVGGraphics2D gc, int x, int y, int width, int height)
      throws HException {
    ChartDetails details = new ChartDetails(x, y, width, height);
    calculateDistinctValues(details.horizontalValues, details.verticalValues);

    getCombinations(details.horizontalValues, 0, details.horizontalCombinations, new ArrayList<>());
    List<List<String>> sortedHorizontalCombinations =
        sortCombinations(details.horizontalCombinations);

    getCombinations(details.verticalValues, 0, details.verticalCombinations, new ArrayList<>());
    List<List<String>> sortedVerticalCombinations = sortCombinations(details.verticalCombinations);

    // Calculate the title geometry
    //
    if (StringUtils.isNotEmpty(titleText)) {
      details.titleGeometry = calculateTextGeometry(gc, titleText);
      details.titleHeight = details.titleGeometry.getHeight() + verticalMargin;
    } else {
      details.titleHeight = 0;
    }

    // How many series do we have in the chart?
    //
    List<List<String>> verticalCombinations = new ArrayList<>(details.verticalCombinations);
    if (verticalCombinations.isEmpty()) {
      // At least perform once without vertical dimensions...
      //
      verticalCombinations.add(new ArrayList<>());
    }

    if (facts == null || facts.isEmpty()) {
      throw new HException("We need at least 1 fact to work with");
    }
    if (facts.size() > 1) {
      throw new HException("Only 1 fact is supported at this time");
    }

    // Some information about the fact
    //
    int factIndex = 0;
    HFact fact = facts.get(factIndex);
    ValueMetaNumber factValueMeta = new ValueMetaNumber(fact.getColumnName());
    factValueMeta.setConversionMask(fact.getFormatMask());

    // Calculate labels and get their sizes, fact values...
    // Also calculate the maximal height to see how much room we need at the bottom.
    //
    details.minValue = Double.MAX_VALUE;
    if (usingZeroBaseline) {
      details.minValue = 0.0d;
      try {
        details.minLabel = factValueMeta.getString(details.minValue);
      } catch (HopValueException e) {
        throw new HException("Unexpected error converting number to string", e);
      }
      details.minLabelGeometry = calculateTextGeometry(gc, details.minLabel);
    }
    details.maxValue = Double.MIN_VALUE;
    details.maxFactWidth = 0;

    // How many combinations do we have?
    //
    int nrCombinations = sortedHorizontalCombinations.size();

    for (int series = 0; series < verticalCombinations.size(); series++) {
      details.factLabels.add(new ArrayList<>());
      details.factValues.add(new ArrayList<>());
      details.factValueMetas.add(new ArrayList<>());
    }

    for (int part = 0; part < nrCombinations; part++) {
      List<String> combinationList = sortedHorizontalCombinations.get(part);
      String axisLabel = getCombinationString(combinationList);
      details.labels.add(axisLabel);

      // What's the geometry of the label?
      //
      HTextGeometry labelGeometry = calculateTextGeometry(gc, axisLabel);
      details.labelGeometries.add(labelGeometry);

      // Do we have any facts to look up: only 1 fact supported for now.
      //
      IValueMeta valueMeta = inputRowMeta.getValueMeta(factIndexes.get(factIndex));

      double totalValue = 0;

      for (int series = 0; series < verticalCombinations.size(); series++) {

        List<String> verticalCombination = verticalCombinations.get(series);

        List<String> factLabels = details.factLabels.get(series);
        List<Object> factValues = details.factValues.get(series);
        List<IValueMeta> factValueMetas = details.factValueMetas.get(series);

        // The lookup key for the combination of horizontal and vertical dimensions...
        //
        List<String> factLookupKey = new ArrayList<>(verticalCombination);
        factLookupKey.addAll(combinationList);

        // Lookup the values...
        //
        Object valueData = pivotMapList.get(factIndex).get(factLookupKey);

        factValues.add(valueData);
        factValueMetas.add(valueMeta);

        try {
          if (fact.getFormatMask() != null) {
            valueMeta.setConversionMask(fact.getFormatMask());
          }
          String factString = Const.NVL(valueMeta.getString(valueData), "-");
          factLabels.add(factString);
          HTextGeometry factGeometry = calculateTextGeometry(gc, factString);
          if (factGeometry.getWidth() > details.maxFactWidth) {
            details.maxFactWidth = factGeometry.getWidth();
          }
        } catch (HopValueException e) {
          throw new HException("Error formatting value '" + valueData + "' : ", e);
        }

        try {
          Double factValueDouble = valueMeta.getNumber(valueData);
          double factValue;
          if (factValueDouble == null) {
            factValue = 0.0d;
          } else {
            factValue = factValueDouble.doubleValue();
          }
          totalValue += factValue;
          if (factValue < details.minValue) {
            details.minValue = factValue;
            details.minLabel = factLabels.get(factLabels.size() - 1);
            details.minLabelGeometry = calculateTextGeometry(gc, details.minLabel);
          }
          if (factValue > details.maxValue) {
            details.maxValue = factValue;
            details.maxLabel = factLabels.get(factLabels.size() - 1);
            details.maxLabelGeometry = calculateTextGeometry(gc, details.maxLabel);
          }
          if (usingTotalHeights && totalValue > details.maxValue) {
            details.maxValue = totalValue;
            details.maxLabel = factValueMeta.getString(totalValue);
            details.maxLabelGeometry = calculateTextGeometry(gc, details.maxLabel);
          }
        } catch (HopException e) {
          throw new HException("Data conversion error", e);
        }
      }

      if (labelGeometry.getHeight() > details.maxLabelHeight) {
        details.maxLabelHeight = labelGeometry.getHeight();
      }
    }

    if (nrCombinations == 0) {
      details.partWidth = details.width - horizontalMargin * 2 - details.maxFactWidth;
    } else {
      // Split the graph in equal parts
      //
      details.partWidth =
          (details.width - horizontalMargin * 3 - details.maxFactWidth) / (double) nrCombinations;
    }

    // Do some calculations for the legend.
    // Let's assume it's placed at the bottom
    //
    // So we need to calculate the max widt of a vertical combination string.
    // Then we need to figure out how many of those we can fit onto the width
    // Then we know how many columns and rows we can make
    //
    List<String> legendLabels = new ArrayList<>();
    List<HTextGeometry> legendLabelGeos = new ArrayList<>();
    int maxLegendLabelWidth = 0;
    int maxLegendLabelHeight = 0;
    int nrLabels = 0;
    if (showingLegend) {
      nrLabels = sortedHorizontalCombinations.size();
      for (List<String> verticalCombination : sortedVerticalCombinations) {
        String legendLabel = getCombinationLabel(verticalCombination);
        legendLabels.add(legendLabel);
        HTextGeometry labelGeo = calculateTextGeometry(gc, legendLabel);
        legendLabelGeos.add(labelGeo);

        if (labelGeo.getWidth() > maxLegendLabelWidth) {
          maxLegendLabelWidth = labelGeo.getWidth();
        }
        if (labelGeo.getHeight() > maxLegendLabelHeight) {
          maxLegendLabelHeight = labelGeo.getHeight();
        }
      }
    }

    // How can we fit all legend labels?
    // Calculate how many columns and rows we need
    //
    details.legendLabels = legendLabels;
    details.legendLabelGeos = legendLabelGeos;
    details.maxLegendLabelWidth = maxLegendLabelWidth;
    details.maxLegendLabelHeight = maxLegendLabelHeight;
    details.legendMarkerSize = maxLegendLabelHeight * 2 / 3;

    details.legendWidth = width - 2 * horizontalMargin;
    details.maxNrLegendColumns =
        (int)
            Math.floor(
                (double) details.legendWidth
                    / (maxLegendLabelWidth + 2 * horizontalMargin + details.legendMarkerSize));
    details.nrLegendColumns = Math.min(details.legendLabels.size(), details.maxNrLegendColumns);
    if (details.nrLegendColumns > 0) {
      details.nrLegendRows = 1 + (int) Math.floor((double) nrLabels / details.nrLegendColumns);
    } else {
      details.nrLegendRows = 0;
    }
    details.legendHeight = (details.maxLegendLabelHeight + verticalMargin) * details.nrLegendRows;

    // OK, now we continue...
    //
    details.overshoot = (double) height / 20;
    details.partHeight =
        (height
            - verticalMargin * 3
            - details.maxLabelHeight
            - details.overshoot * 2
            - details.titleHeight
            - details.legendHeight);
    if (usingZeroBaseline) {
      details.partHeight += details.overshoot;
    }
    details.valueRange = details.maxValue - details.minValue;
    if (details.valueRange == 0.0) {
      details.valueFactor = 0.0;
    } else {
      details.valueFactor = details.partHeight / details.valueRange;
    }

    return details;
  }

  protected String getCombinationLabel(List<String> combination) {
    StringBuilder label = new StringBuilder();
    for (String string : combination) {
      if (label.length() > 0) {
        label.append('-');
      }
      label.append(string);
    }
    return label.toString();
  }
}
