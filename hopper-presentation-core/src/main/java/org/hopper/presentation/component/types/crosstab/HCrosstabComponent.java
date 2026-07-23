package org.hopper.presentation.component.types.crosstab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.hop.core.exception.HopValueException;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HFact;
import org.hopper.core.HGeometry;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.HTextGeometry;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.draw.DrawnItem.DrawnItemType;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

@JsonDeserialize(as = HCrosstabComponent.class)
@HComponentPlugin(
    id = "HCrosstabComponent",
    name = "Crosstab",
    description = "A crosstab component",
    image = "ui/images/components/crosstab.svg")
@Getter
@Setter
public class HCrosstabComponent extends HBaseAggregatingComponent implements IHComponent {

  // TODO Implement sub-totals, multiple totals
  // TODO Allow sorting by all sorts of values, probably also on aggregates
  //
  public static final String DATA_CROSSTAB_DETAILS = "crosstab_details";
  public static final String DATA_START_ROW = "start_row";
  public static final String DATA_END_ROW = "end_row";

  /**
   * Form-only action (not metadata): sits between horizontal and vertical dimension lists
   * ({@code order} 09050). Handled by hopper-presentation-rest {@code hopperFormButtonClick} /
   * {@code swapCrosstabHorizontalVerticalDimensions}.
   */
  @JsonIgnore
  @HWidgetElement(
      order = "09050-swapHorizontalVerticalDimensions",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.BUTTON,
      label = "Swap horizontal and vertical columns",
      toolTip =
          "Exchange the horizontal and vertical dimension lists (transpose the crosstab axes)")
  private transient String swapHorizontalVerticalDimensions;

  @HWidgetElement(
      order = "10000-horizontalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal margin")
  @HopMetadataProperty
  private int horizontalMargin;

  @HWidgetElement(
      order = "10100-verticalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical margin")
  @HopMetadataProperty
  private int verticalMargin;

  @HWidgetElement(
      order = "10200-evenHeights",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Even heights?")
  @HopMetadataProperty
  private boolean evenHeights;

  @HWidgetElement(
      order = "10300-headerOnEveryPage",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Header on every page?")
  @HopMetadataProperty
  private boolean headerOnEveryPage;

  @HWidgetElement(
      order = "10400-showingHorizontalSubtotals",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show horizontal subtotals?")
  @HopMetadataProperty
  private boolean showingHorizontalSubtotals;

  @HWidgetElement(
      order = "10500-showingVerticalSubtotals",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show vertical subtotals?")
  @HopMetadataProperty
  private boolean showingVerticalSubtotals;

  public HCrosstabComponent() {
    super("HCrosstabComponent");
    horizontalDimensions = new ArrayList<>();
    verticalDimensions = new ArrayList<>();
    facts = new ArrayList<>();
    // Defaults for newly created crosstabs (palette drop / form defaults)
    horizontalMargin = 4;
    verticalMargin = 2;
    evenHeights = true;
    headerOnEveryPage = true;
  }

  public HCrosstabComponent(String connectorName) {
    this();
    this.sourceConnectorName = connectorName;
  }

  public HCrosstabComponent(HCrosstabComponent c) {
    super("HCrosstabComponent", c);
    this.sourceConnectorName = c.sourceConnectorName;
    this.horizontalMargin = c.horizontalMargin;
    this.verticalMargin = c.verticalMargin;
    this.evenHeights = c.evenHeights;
    this.headerOnEveryPage = c.headerOnEveryPage;
    this.showingHorizontalSubtotals = c.showingHorizontalSubtotals;
    this.showingVerticalSubtotals = c.showingVerticalSubtotals;
  }

  public HCrosstabComponent clone() {
    return new HCrosstabComponent(this);
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

    // Palette-dropped / incomplete crosstabs: skip data load so the page still renders
    if (org.apache.commons.lang3.StringUtils.isBlank(sourceConnectorName)) {
      results.addDataSet(component, DATA_CROSSTAB_DETAILS, new CrosstabDetails());
      return;
    }
    HConnector connector = dataContext.getConnector(sourceConnectorName);
    if (connector == null) {
      results.addDataSet(component, DATA_CROSSTAB_DETAILS, new CrosstabDetails());
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

    connector.getConnector().startStreaming(dataContext);
    connector.getConnector().waitUntilFinished();

    // Now all the rows have been pivoted, we can render the data...
    // The vertical dimension columns are on the left.
    // The horizontal dimension values are the next columns.
    // We need every horizontal dimension value combined with every other with every fact
    //

    // Keep the calculated details around for later during layout and rendering.
    //
    CrosstabDetails details = new CrosstabDetails();

    // To calculate the width and height of the text in the given font we need a GC
    //
    SVGGraphics2D gc = HopSvgGraphics2D.newDocument();
    enableFont(gc, lookupDefaultFont(renderContext));

    // Things to remember for every rendered row...
    //
    if (horizontalDimensions.size() == 0 && verticalDimensions.size() == 0) {
      // No dimensions
      //
      details.sortedVerticalCombinations = new ArrayList<>();
      details.sortedHorizontalCombinations = Arrays.asList(Arrays.asList("-"));
    } else if (horizontalDimensions.size() == 0) {
      // Any keySet is fine, all facts have the same vertical dimensions
      //
      details.sortedVerticalCombinations = new ArrayList(pivotMapList.get(0).keySet());
      sortListOfListOfStrings(details.sortedVerticalCombinations);
      details.sortedHorizontalCombinations = new ArrayList<>();
    } else if (verticalDimensions.size() == 0) {
      details.sortedVerticalCombinations = new ArrayList<>();
      details.sortedHorizontalCombinations = new ArrayList<>(pivotMapList.get(0).keySet());
      sortListOfListOfStrings(details.sortedHorizontalCombinations);
    } else {
      // Generic case with both horizontal and vertical dimensions
      //
      List<Set<String>> horizontalValues = new ArrayList<>();
      List<Set<String>> verticalValues = new ArrayList<>();
      calculateDistinctValues(horizontalValues, verticalValues);

      // Get all dimension combinations horizontally
      // Then sort this list of lists...
      //
      Set<List<String>> horizontalCombinations = new HashSet<>();
      getCombinations(horizontalValues, 0, horizontalCombinations, new ArrayList<>());
      details.sortedHorizontalCombinations = sortCombinations(horizontalCombinations);

      // Get the vertical dimension combinations
      // Then sort this list of lists...
      //
      Set<List<String>> verticalCombinations = new HashSet<>();
      getCombinations(verticalValues, 0, verticalCombinations, new ArrayList<>());
      details.sortedVerticalCombinations = sortCombinations(verticalCombinations);
    }

    // PREPROCESSING IS DONE HERE, CALCULATE THE GRID
    //

    // There are a number of rows above the grid:
    // One row for every horizontal dimension
    // Then one row with the vertical headers and the facts
    //
    enableFont(gc, lookupHorizontalDimensionsFont(renderContext));

    for (int rowNr = 0; rowNr < horizontalDimensions.size(); rowNr++) {
      details.nrHeaderLines++;

      List<CellInfo> cellInfos = new ArrayList<>();

      // Add the blanks for the vertical dimensions...
      //
      for (int dimNr = 0; dimNr < verticalDimensions.size(); dimNr++) {
        String columnName = " ";
        HTextGeometry geometry = calculateTextGeometry(gc, columnName);
        cellInfos.add(
            new CellInfo(
                geometry,
                columnName,
                new HColumn("blank"),
                HVerticalAlignment.TOP,
                HHorizontalAlignment.LEFT));
      }
      // Add the horizontal values for this row
      //
      for (int colNr = 0; colNr < details.sortedHorizontalCombinations.size(); colNr++) {
        List<String> horizontalCombination = details.sortedHorizontalCombinations.get(colNr);
        String headerValue = horizontalCombination.get(rowNr);
        HTextGeometry geometry = calculateTextGeometry(gc, headerValue);
        HColumn column = horizontalDimensions.get(rowNr);
        for (HFact fact : facts) {
          cellInfos.add(
              new CellInfo(
                  geometry,
                  headerValue,
                  column,
                  column.getVerticalAlignment(),
                  column.getHorizontalAlignment()));
        }
      }
      // Finally, see if we need to add columns for the horizontal aggregations
      //
      if (showingVerticalTotals) {
        // We need to show a grand total over the vertical dimensions...
        //
        for (HFact fact : facts) {
          String headerValue = " "; // Empty space
          HTextGeometry geometry = calculateTextGeometry(gc, headerValue);
          cellInfos.add(
              new CellInfo(
                  geometry,
                  headerValue,
                  fact,
                  fact.getHeaderVerticalAlignment(),
                  fact.getHeaderHorizontalAlignment()));
        }
      }
      details.cellInfosList.add(cellInfos);
      details.headerRowFlags.add(true);
    }

    // Now add one row with the vertical dimension headers and facts
    // First the vertical dimension headers
    //
    // TODO: make this optional?
    {
      details.nrHeaderLines++;

      List<CellInfo> cellInfos = new ArrayList<>();

      for (int dimNr = 0; dimNr < verticalDimensions.size(); dimNr++) {
        HColumn dimension = verticalDimensions.get(dimNr);
        String headerName = dimension.getHeaderValue();
        if (headerName == null) {
          headerName = dimension.getColumnName();
        }
        HTextGeometry geometry = calculateTextGeometry(gc, headerName);
        cellInfos.add(
            new CellInfo(
                geometry,
                headerName,
                dimension,
                dimension.getVerticalAlignment(),
                dimension.getHorizontalAlignment()));
      }
      // Now add the facts, loop over all the horizontal combinations.
      // If there are no horizontal dimensions, only execute once
      //
      for (int colNr = 0;
          colNr < details.sortedHorizontalCombinations.size()
              || colNr == 0 && details.sortedHorizontalCombinations.size() == 0;
          colNr++) {
        for (HFact fact : facts) {
          // For facts we always need to display the header value
          //
          String headerValue = fact.getHeaderValue();
          if (headerValue == null) {
            headerValue = fact.getColumnName();
          }
          HTextGeometry geometry = calculateTextGeometry(gc, headerValue);
          cellInfos.add(
              new CellInfo(
                  geometry,
                  headerValue,
                  fact,
                  fact.getVerticalAlignment(),
                  fact.getHorizontalAlignment()));
        }
      }
      if (showingVerticalTotals) {
        // We need to show a grand total over the vertical dimensions...
        //
        for (HFact fact : facts) {
          String headerValue = "Total"; // Empty space
          HTextGeometry geometry = calculateTextGeometry(gc, headerValue);
          cellInfos.add(
              new CellInfo(
                  geometry,
                  headerValue,
                  fact,
                  fact.getHeaderVerticalAlignment(),
                  fact.getHeaderHorizontalAlignment()));
        }
      }
      details.cellInfosList.add(cellInfos);
      details.headerRowFlags.add(true);
    }

    // Now we can simply loop over the horizontal and vertical combinations...
    //
    if (horizontalDimensions.size() == 0 && verticalDimensions.size() == 0) {
      // Only the facts please
      //
      List<String> keys = Arrays.asList("-");
      List<CellInfo> cellInfos = new ArrayList<>();

      List<String> verticalCombination = Arrays.asList("-");
      List<String> horizontalCombination = new ArrayList<>();

      addFacts(gc, keys, cellInfos);

      details.cellInfosList.add(cellInfos);
      details.headerRowFlags.add(false);

    } else {
      // The generic case.
      //
      for (int rowNr = 0;
          rowNr < details.sortedVerticalCombinations.size()
              || rowNr == 0 && details.sortedVerticalCombinations.size() == 0;
          rowNr++) {

        List<String> verticalCombination;
        if (details.sortedVerticalCombinations.size() > 0) {
          verticalCombination = details.sortedVerticalCombinations.get(rowNr);
        } else {
          verticalCombination = new ArrayList<>();
        }

        // This is the current row we're painting...
        //
        List<CellInfo> cellInfos = new ArrayList<>();

        // Now we can add the vertical dimensions without too much of an issue
        //
        enableFont(gc, lookupVerticalDimensionsFont(renderContext));
        for (int i = 0; i < verticalCombination.size(); i++) {
          String verticalValue = verticalCombination.get(i);
          HColumn dimension = verticalDimensions.get(i);
          HTextGeometry geometry = calculateTextGeometry(gc, verticalValue);
          HColumn column = verticalDimensions.get(i);
          cellInfos.add(
              new CellInfo(
                  geometry,
                  verticalValue,
                  column,
                  column.getVerticalAlignment(),
                  column.getHorizontalAlignment()));
        }

        // Loop to get the combinations...
        //
        enableFont(gc, lookupFactsFont(renderContext));
        for (int colNr = 0;
            colNr < details.sortedHorizontalCombinations.size()
                || colNr == 0 && details.sortedHorizontalCombinations.size() == 0;
            colNr++) {
          List<String> horizontalCombination;
          if (details.sortedHorizontalCombinations.size() > 0) {
            horizontalCombination = details.sortedHorizontalCombinations.get(colNr);
          } else {
            horizontalCombination = new ArrayList<>();
          }
          // Create a key : horizontal values, then vertical
          //
          List<String> keys = new ArrayList();
          keys.addAll(verticalCombination);
          keys.addAll(horizontalCombination);

          // Every combination is an extra column...
          // but we need a column for every fact
          //
          addFacts(gc, keys, cellInfos);
        }

        // Now add vertical aggregations
        //
        if (showingVerticalTotals) {
          // We need to show a grand total over the vertical dimensions...
          //
          addFacts(gc, verticalCombination, cellInfos);
        }

        // Here we processed all the facts for the given vertical and horizontal dimensions
        // We can add the rowStrings to the grid
        //
        details.cellInfosList.add(cellInfos);
        details.headerRowFlags.add(false);
      }

      if (showingHorizontalTotals) {
        // Add totals for all the horizontal combinations...
        //
        List<CellInfo> cellInfos = new ArrayList<>();

        // Put total in the first column, blanks in the rest
        //
        for (int i = 0; i < verticalDimensions.size(); i++) {
          String text;
          if (i == 0) {
            text = "Total"; // TODO Configure this
          } else {
            text = " ";
          }
          HColumn dimension = verticalDimensions.get(i);
          HTextGeometry geometry = calculateTextGeometry(gc, text);
          HColumn column = verticalDimensions.get(i);

          cellInfos.add(
              new CellInfo(
                  geometry,
                  text,
                  dimension,
                  dimension.getVerticalAlignment(),
                  dimension.getHorizontalAlignment()));
        }

        // Now loop over all the horizontal combinations and add the fact aggregates
        //
        for (int colNr = 0;
            colNr < details.sortedHorizontalCombinations.size()
                || colNr == 0 && details.sortedHorizontalCombinations.size() == 0;
            colNr++) {
          List<String> horizontalCombination;
          if (details.sortedHorizontalCombinations.size() > 0) {
            horizontalCombination = details.sortedHorizontalCombinations.get(colNr);
          } else {
            horizontalCombination = new ArrayList<>();
          }
          // Create a key : horizontal values, then vertical
          //
          List<String> keys = new ArrayList();
          keys.addAll(horizontalCombination);

          // Every combination is an extra column...
          // but we need a column for every fact
          //
          addFacts(gc, keys, cellInfos);
        }
        if (showingVerticalTotals) {
          // Add the grand total
          //
          addFacts(gc, Arrays.asList(GRANT_TOTAL_STRING), cellInfos);
        }

        // Add the new line to the list
        //
        details.cellInfosList.add(cellInfos);
        details.headerRowFlags.add(false);
      }
    }

    // POST PROCESSING
    //

    // Calculate global min and max Y offsets
    //
    for (List<CellInfo> cellInfos : details.cellInfosList) {
      for (CellInfo cellInfo : cellInfos) {
        if (details.globalMaxYOffset < cellInfo.geometry.getOffsetY()) {
          details.globalMaxYOffset = cellInfo.geometry.getOffsetY();
        }
        if (details.globalMinYOffset > cellInfo.geometry.getOffsetY()) {
          details.globalMinYOffset = cellInfo.geometry.getOffsetY();
        }
      }
    }

    // Now we calculate the maximum column widths so that we can render correctly from top to bottom
    //
    int nrRows = details.cellInfosList.size();
    int nrColumns = details.cellInfosList.get(0).size();

    int globalMaxHeight = 0;

    if (details.cellInfosList.size() > 0) {
      // Get the maximum width of every column
      // Get the maximum Y offset for every column
      //
      for (int colNr = 0; colNr < nrColumns; colNr++) {
        details.maxWidths.add(0);
      }
      for (int colNr = 0; colNr < nrColumns; colNr++) {
        for (int rowNr = 0; rowNr < nrRows; rowNr++) {
          List<CellInfo> cellInfos = details.cellInfosList.get(rowNr);
          HTextGeometry geometry = cellInfos.get(colNr).geometry;
          if (details.maxWidths.get(colNr) < geometry.getWidth()) {
            details.maxWidths.set(colNr, geometry.getWidth());
          }
        }
      }

      // Get the maximum height of every row
      //
      for (int rowNr = 0; rowNr < nrRows; rowNr++) {
        int maxHeight = 0;
        for (int colNr = 0; colNr < nrColumns; colNr++) {
          HTextGeometry geometry = details.cellInfosList.get(rowNr).get(colNr).geometry;
          if (maxHeight < geometry.getHeight()) {
            maxHeight = geometry.getHeight();
          }
        }
        details.maxHeights.add(maxHeight);
        if (maxHeight > globalMaxHeight) {
          globalMaxHeight = maxHeight;
        }
      }
    }

    // If evenHeights set highest row for all rows
    //
    if (evenHeights) {
      // If we have a header, leave that one alone.
      //
      for (int i = 0; i < details.maxHeights.size(); i++) {
        details.maxHeights.set(i, globalMaxHeight);
      }
    }

    // Total width?
    //
    details.totalWidth = 0;
    for (int width : details.maxWidths) {
      details.totalWidth += width + 2 * horizontalMargin;
    }

    details.totalHeight = 0;
    for (int height : details.maxHeights) {
      details.totalHeight += height + 2 * verticalMargin;
    }

    // Save the details for later
    //
    results.addDataSet(component, DATA_CROSSTAB_DETAILS, details);
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
   * @throws HException
   */
  public HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    CrosstabDetails details =
        (CrosstabDetails) results.getDataSet(component, DATA_CROSSTAB_DETAILS);
    return new HSize(details.totalWidth, details.totalHeight);
  }

  private void addFacts(SVGGraphics2D gc, List<String> keys, List<CellInfo> cellInfos)
      throws HException {
    for (int factNr = 0; factNr < pivotMapList.size(); factNr++) {
      HFact fact = facts.get(factNr);
      Map<List<String>, Object> pivotMap = pivotMapList.get(factNr);
      Map<List<String>, Long> countMap = countMapList.get(factNr);

      // Make a copy so we can change format masks
      // TODO: cache this clone somewhere for performance
      //
      IValueMeta valueMeta = inputRowMeta.getValueMeta(factIndexes.get(factNr)).clone();
      if (fact.getFormatMask() != null) {
        valueMeta.setConversionMask(fact.getFormatMask());
      }

      Long count = countMap.get(keys);
      Object object = pivotMap.get(keys);
      String factString;
      try {
        switch (fact.getAggregationMethod()) {
          case SUM:
            if (valueMeta.isNull(object)) {
              factString = " ";
            } else {
              factString = valueMeta.getString(object);
            }
            break;
          case AVERAGE:
            if (valueMeta.isNull(object)) {
              factString = " ";
            } else {
              Double sum = (Double) object;
              sum /= count;
              factString = valueMeta.getString(sum);
            }
            break;
          case COUNT:
            if (count == null) {
              factString = " ";
            } else {
              ValueMetaInteger intValueMeta = new ValueMetaInteger(valueMeta.getName());
              intValueMeta.setConversionMask(valueMeta.getConversionMask());
              factString = intValueMeta.getString(count);
            }
            break;
          default:
            throw new HException(
                "Unsupported aggregation exception : " + fact.getAggregationMethod());
        }
      } catch (HopValueException e) {
        factString = "!?";
      }

      HTextGeometry geometry = calculateTextGeometry(gc, factString);
      CellInfo cellInfo =
          new CellInfo(
              geometry,
              factString,
              fact,
              fact.getVerticalAlignment(),
              fact.getHorizontalAlignment());
      cellInfos.add(cellInfo);
    }
  }

  private void processVertical(
      List<List<String>> sortedVerticalValues,
      int columnNr,
      int verticalIndex,
      List<List<String>> rowStringsList,
      List<String> currentRow) {}

  @SuppressWarnings("unchecked")
  @Override
  public void doLayout(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    // Get the current page on which we're rendering...
    // Create a new one if we need to move on to a next page
    //
    HRenderPage renderPage = results.getCurrentRenderPage(page);

    // Calculate the expected geometry for this component
    //
    HGeometry expectedGeometry =
        getExpectedGeometry(presentation, page, component, dataContext, renderContext, results);

    // Get the details back
    //
    CrosstabDetails details =
        (CrosstabDetails) results.getDataSet(component, DATA_CROSSTAB_DETAILS);

    // Calculate the height until the end of the page...
    // How much more can we fit onto the page?
    //
    boolean addFragment = true;
    int partNumber = 1;

    int remainingHeight = presentation.getUsableHeight(page) - expectedGeometry.getY();

    List<List<CellInfo>> cellInfosList = details.cellInfosList;
    List<Integer> maxWidths = details.maxWidths;
    List<Integer> maxHeights = details.maxHeights;
    IRowMeta rowMeta = inputRowMeta;

    // Loop over all the rows, see how many we can fit onto this page, then create another one.
    //
    int startLine = 0;
    int partHeight = 0;

    int rowNr = 0;
    for (; rowNr < maxHeights.size(); rowNr++) {
      int maxHeight = maxHeights.get(rowNr);
      int rowHeight = maxHeight + 2 * verticalMargin;
      partHeight += rowHeight;

      // Did we leave the page at the bottom?
      //
      if (partHeight > remainingHeight) {

        // Save previous until the previous row...
        //
        HGeometry partGeometry = expectedGeometry.clone();
        partHeight -= rowHeight;

        if (headerOnEveryPage && partNumber > 1) {
          // The part is actually a bit taller...
          // Add the header lines * 2*margin per line
          //
          partHeight += details.nrHeaderLines * (maxHeights.get(0) + 2 * verticalMargin);
        }
        partGeometry.setHeight(partHeight);

        // Add this as a new component part
        //
        addPartLayoutResult(
            results, renderPage, page, component, partGeometry, partNumber, startLine, rowNr);

        // Create a new page
        //
        partNumber++;
        renderPage = results.addNewPage(page, renderPage);
        remainingHeight = presentation.getUsableHeight(page);

        if (headerOnEveryPage) {
          // Reserve room for a header on the new page...
          //
          remainingHeight -= maxHeights.get(0);
        }

        // keep track for the new part...
        //
        startLine = rowNr;
        partHeight = rowHeight;
        expectedGeometry.setY(0);
      }
    }

    // Let's not forget the top part on the last page...
    //
    if (partHeight > 0) {
      HGeometry partGeometry = expectedGeometry.clone();
      // Only a part of the total height!
      //
      partGeometry.setHeight(partHeight);

      // Extra for the new table header?
      //
      if (headerOnEveryPage && partNumber > 1) {
        // The part is actually a bit taller...
        //
        partGeometry.incHeight(details.nrHeaderLines * (maxHeights.get(0) + 2 * verticalMargin));
      }

      addPartLayoutResult(
          results, renderPage, page, component, partGeometry, partNumber, startLine, rowNr);
    }
  }

  private void addPartLayoutResult(
      HLayoutResults results,
      HRenderPage renderPage,
      HPage page,
      HComponent component,
      HGeometry partGeometry,
      int partNumber,
      int startLine,
      int endLine) {
    HComponentLayoutResult result = new HComponentLayoutResult();
    result.setRenderPage(renderPage);
    result.setSourcePage(page);
    result.setComponent(component);
    result.setGeometry(partGeometry);
    result.setPartNumber(partNumber);
    result.getDataMap().put(DATA_START_ROW, startLine);
    result.getDataMap().put(DATA_END_ROW, endLine);

    // Store the geometry also in the results for layout purposes...
    //
    results.addComponentGeometry(component.getName(), partGeometry);

    renderPage.getLayoutResults().add(result);

    // renderPage.addDrawnItem( component.getName(), partNumber, DrawnItemType.ComponentPart, null,
    // 0, 0, partGeometry );
  }

  @Override
  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {

    HComponent component = layoutResult.getComponent();
    HGeometry componentGeometry = layoutResult.getGeometry();
    CrosstabDetails details =
        (CrosstabDetails) results.getDataSet(component, DATA_CROSSTAB_DETAILS);

    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();
    List<DrawnItem> drawnItems = layoutResult.getRenderPage().getDrawnItems();

    // Get sizes and string values from data set...
    //
    List<List<CellInfo>> cellInfosList = details.cellInfosList;
    List<Integer> maxWidths = details.maxWidths;
    List<Integer> maxHeights = details.maxHeights;
    int globalMaxYOffset = details.globalMaxYOffset;
    int globalMinYOffset = details.globalMinYOffset;

    int avgYOffset = (globalMaxYOffset + globalMinYOffset) / 2;

    int startRow = (int) layoutResult.getDataMap().get(DATA_START_ROW);
    int endRow = (int) layoutResult.getDataMap().get(DATA_END_ROW);

    // Now start drawing the table...
    //
    int y = componentGeometry.getY();
    int nrHeaderRows = horizontalDimensions.size() + 1;

    if (headerOnEveryPage && startRow > 0) {
      // Render the header, data is on rows 0-dimensions.imageSize()+1
      // Plus one for the vertical dimension headers and the facts
      //
      int maxHeight = maxHeights.get(0);
      for (int i = 0; i < nrHeaderRows; i++) {
        y =
            renderLine(
                gc,
                drawnItems,
                layoutResult,
                y,
                i,
                maxHeight,
                cellInfosList,
                componentGeometry,
                maxWidths,
                true,
                nrHeaderRows,
                avgYOffset,
                renderContext,
                offSet);
      }
    }

    for (int rowNr = startRow; rowNr < endRow; rowNr++) {
      int maxHeight = maxHeights.get(rowNr);
      y =
          renderLine(
              gc,
              drawnItems,
              layoutResult,
              y,
              rowNr,
              maxHeight,
              cellInfosList,
              componentGeometry,
              maxWidths,
              rowNr < nrHeaderRows,
              nrHeaderRows,
              avgYOffset,
              renderContext,
              offSet);
    }

    drawBorder(gc, componentGeometry, renderContext);
  }

  private int renderLine(
      SVGGraphics2D gc,
      List<DrawnItem> drawnItems,
      HComponentLayoutResult layoutResult,
      int y,
      int rowNr,
      int maxHeight,
      List<List<CellInfo>> cellInfosList,
      HGeometry componentGeometry,
      List<Integer> maxWidths,
      boolean headerRow,
      int nrHeaderRows,
      int yOffset,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {

    List<CellInfo> cellInfos = cellInfosList.get(rowNr);

    int x = componentGeometry.getX();

    if (maxWidths.size() != cellInfos.size()) {
      throw new RuntimeException("Grid calculation error!");
    }

    // Grouping information
    //
    String groupText = null;
    int groupStartX = x;
    int groupWidth = 0;
    HColumn groupColumn = null;
    HTextGeometry groupTextGeometry = null;
    int groupColNr = 0;
    HHorizontalAlignment groupHorizontalAlignment = null;
    HVerticalAlignment groupVerticalAlignment = null;

    for (int columnNr = 0; columnNr < cellInfos.size(); columnNr++) {
      int maxWidth = maxWidths.get(columnNr);
      HTextGeometry textGeometry = cellInfos.get(columnNr).geometry;
      HColumn hopperColumn = cellInfos.get(columnNr).column;
      String text = cellInfos.get(columnNr).text;
      HHorizontalAlignment horizontalAlignment = cellInfos.get(columnNr).horizontalAlignment;
      HVerticalAlignment verticalAlignment = cellInfos.get(columnNr).verticalAlignment;

      if (rowNr < nrHeaderRows || headerOnEveryPage && headerRow) {
        // The header rows block...
        // Get the column from the headers...
        //
        enableFont(gc, lookupHorizontalDimensionsFont(renderContext));
        enableColor(gc, lookupHorizontalDimensionsColor(renderContext));
      } else {
        if (columnNr < verticalDimensions.size()) {
          enableFont(gc, lookupVerticalDimensionsFont(renderContext));
          enableColor(gc, lookupVerticalDimensionsColor(renderContext));
        } else {
          enableFont(gc, lookupFactsFont(renderContext));
          enableColor(gc, lookupFactsColor(renderContext));
        }
      }

      // See if we need to group values together in the header
      //
      if (rowNr < nrHeaderRows || headerOnEveryPage && headerRow) {

        if (text.equals(groupText)) {
          // Extend the active group
          //
          groupWidth += maxWidth + horizontalMargin * 2;

        } else {
          // Draw the previous group if there is one, start a new one
          //
          if (groupText != null) {
            renderLineCell(
                gc,
                drawnItems,
                layoutResult,
                groupStartX,
                y,
                rowNr,
                groupColNr,
                groupText,
                nrHeaderRows,
                groupWidth,
                maxHeight,
                yOffset,
                groupColumn,
                groupTextGeometry,
                groupHorizontalAlignment,
                groupVerticalAlignment,
                renderContext,
                offSet);
          }
          groupStartX = x;
          groupText = text;
          groupWidth = maxWidth;
          groupTextGeometry = textGeometry;
          groupColumn = hopperColumn;
          groupColNr = columnNr;
          groupHorizontalAlignment = horizontalAlignment;
          groupVerticalAlignment = verticalAlignment;
        }
      } else {
        renderLineCell(
            gc,
            drawnItems,
            layoutResult,
            x,
            y,
            rowNr,
            columnNr,
            text,
            nrHeaderRows,
            maxWidth,
            maxHeight,
            yOffset,
            hopperColumn,
            textGeometry,
            horizontalAlignment,
            verticalAlignment,
            renderContext,
            offSet);
      }
      x += maxWidth + horizontalMargin * 2;
    }
    // See if we need to draw the last group
    //
    if (groupText != null) {
      renderLineCell(
          gc,
          drawnItems,
          layoutResult,
          groupStartX,
          y,
          rowNr,
          groupColNr,
          groupText,
          nrHeaderRows,
          groupWidth,
          maxHeight,
          yOffset,
          groupColumn,
          groupTextGeometry,
          groupHorizontalAlignment,
          groupVerticalAlignment,
          renderContext,
          offSet);
    }
    y += maxHeight + verticalMargin * 2;

    return y;
  }

  private void renderLineCell(
      SVGGraphics2D gc,
      List<DrawnItem> drawnItems,
      HComponentLayoutResult layoutResult,
      int x,
      int y,
      int rowNr,
      int c,
      String text,
      int nrHeaderRows,
      int maxWidth,
      int maxHeight,
      int yOffset,
      HColumn hopperColumn,
      HTextGeometry textGeometry,
      HHorizontalAlignment horizontalAlignment,
      HVerticalAlignment verticalAlignment,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {
    // Don't theme in the top left cell
    //
    boolean emptyCell = c < verticalDimensions.size() && rowNr < nrHeaderRows - 1;
    if (!emptyCell) {
      // Fill the background of the cell
      //
      if (isBackground()) {
        enableColor(gc, lookupBackgroundColor(renderContext));
        gc.fillRect(x, y, maxWidth + horizontalMargin * 2, maxHeight + verticalMargin * 2);
      }
    }

    enableColor(gc, lookupDefaultColor(renderContext));

    int cellWidth = maxWidth + horizontalMargin * 2;
    int cellHeight = maxHeight + verticalMargin * 2;
    int positionX;
    int positionY;

    // Null-safe: incomplete form/metadata saves can leave alignments unset
    HVerticalAlignment vAlign =
        verticalAlignment != null ? verticalAlignment : HVerticalAlignment.TOP;
    HHorizontalAlignment hAlign =
        horizontalAlignment != null ? horizontalAlignment : HHorizontalAlignment.LEFT;

    switch (vAlign) {
      case TOP:
        positionY = y + textGeometry.getHeight() + verticalMargin;
        break;
      case BOTTOM:
        positionY = y + cellHeight - verticalMargin;
        break;
      case MIDDLE:
        positionY = y + cellHeight / 2 + textGeometry.getHeight() / 2;
        break;
      default:
        throw new HException("Unsupported vertical alignment : " + vAlign);
    }

    switch (hAlign) {
      case LEFT:
        positionX = x + textGeometry.getOffsetX() + horizontalMargin;
        break;
      case RIGHT:
        positionX = x + cellWidth - horizontalMargin - textGeometry.getWidth();
        break;
      case CENTER:
        positionX = x + (cellWidth - textGeometry.getWidth()) / 2;
        break;
      default:
        throw new HException("Unsupported horizontal alignment : " + hAlign);
    }

    Stroke baseStroke = gc.getStroke();

    // gc.setStroke(new BasicStroke(0.1f));
    // gc.drawRect( positionX, positionY-textGeometry.getHeight(), textGeometry.getWidth(),
    // textGeometry.getHeight() );
    // gc.setStroke( baseStroke );

    gc.drawString(text, positionX, positionY);

    // Don't draw a rectangle around the top left cell
    //
    if (!emptyCell) {
      // draw the rectangle
      //
      HColorRGB oldColor = enableColor(gc, lookupGridColor(renderContext));
      gc.setStroke(new BasicStroke(0.5f));
      gc.drawRect(x, y, cellWidth, cellHeight);
      gc.setStroke(baseStroke);
      enableColor(gc, oldColor);

      // Add the drawn item for this cell...
      // x/y are already absolute page coordinates (start at componentGeometry origin),
      // same convention as HTableComponent — do not add componentX/Y again.
      DrawnItem drawnItem =
          new DrawnItem(
              layoutResult.getComponent().getName(),
              layoutResult.getComponent().getComponent().getPluginId(),
              layoutResult.getPartNumber(),
              DrawnItemType.ComponentItem,
              DrawnItem.Category.Cell.name(),
              rowNr,
              c,
              new HGeometry(
                  offSet.getX() + x,
                  offSet.getY() + y,
                  cellWidth,
                  cellHeight),
              new DrawnContext(text));
      drawnItems.add(drawnItem);
    }
  }
}
