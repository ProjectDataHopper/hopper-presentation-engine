package org.hopper.presentation.component.types.table;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.BasicStroke;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopValueException;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HFont;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.HTextGeometry;
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
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;

@JsonDeserialize(as = HTableComponent.class)
@HComponentPlugin(
    id = "HTableComponent",
    name = "Table",
    description = "A table component",
    image = "ui/images/components/table.svg")
@Getter
@Setter
public class HTableComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_TABLE_DETAILS = "table_details";
  private static final String DATA_START_ROW = "DATA_START_ROW";
  private static final String DATA_END_ROW = "DATA_END_ROW";

  @HWidgetElement(
      order = "10000-columnSelection",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Column selection")
  @HopMetadataProperty
  private List<HColumn> columnSelection;

  @HWidgetElement(
      order = "10100-horizontalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal margin")
  @HopMetadataProperty
  private int horizontalMargin;

  @HWidgetElement(
      order = "10200-verticalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical margin")
  @HopMetadataProperty
  private int verticalMargin;

  @HWidgetElement(
      order = "10300-evenHeights",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Even heights?")
  @HopMetadataProperty
  private boolean evenHeights;

  @HWidgetElement(
      order = "10400-header",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show header?")
  @HopMetadataProperty
  private boolean header;

  @HWidgetElement(
      order = "10500-headerOnEveryPage",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Header on every page?")
  @HopMetadataProperty
  private boolean headerOnEveryPage;

  @HWidgetElement(
      order = "10600-gridLineWidth",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Grid line width")
  @HopMetadataProperty
  private String gridLineWidth;

  @HWidgetElement(
      order = "10700-headerFont",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Header font")
  @HopMetadataProperty
  private HFont headerFont;

  @HWidgetElement(
      order = "10800-gridColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Grid color")
  @HopMetadataProperty
  private HColorRGB gridColor;

  @HWidgetElement(
      order = "10900-headerBackGroundColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Header background color")
  @HopMetadataProperty
  private HColorRGB headerBackGroundColor;

  public HTableComponent() {
    super("HTableComponent");
    columnSelection = new ArrayList<>();
    // Defaults for newly created tables (palette drop / form defaults)
    horizontalMargin = 4;
    verticalMargin = 2;
    evenHeights = true;
    header = true;
    headerOnEveryPage = true;
  }

  public HTableComponent(String connectorName, List<HColumn> columnSelection) {
    this();
    super.sourceConnectorName = connectorName;
    this.columnSelection = columnSelection;
  }

  public HTableComponent(HTableComponent c) {
    super("HTableComponent", c);
    this.columnSelection = new ArrayList<>();
    for (HColumn lc : c.columnSelection) {
      this.columnSelection.add(new HColumn(lc));
    }
    this.gridColor = c.gridColor == null ? null : new HColorRGB(c.gridColor);
    this.headerBackGroundColor =
        c.headerBackGroundColor == null ? null : new HColorRGB(c.headerBackGroundColor);
    this.horizontalMargin = c.horizontalMargin;
    this.verticalMargin = c.verticalMargin;
    this.evenHeights = c.evenHeights;
    this.headerOnEveryPage = c.headerOnEveryPage;
    this.header = c.header;
    this.headerFont = c.headerFont == null ? null : new HFont(c.headerFont);
  }

  public HTableComponent clone() {
    return new HTableComponent(this);
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
    TableDetails details = new TableDetails();

    // Palette-dropped / incomplete tables: leave empty so layout/render still succeed
    if (org.apache.commons.lang3.StringUtils.isBlank(sourceConnectorName)) {
      results.addDataSet(component, DATA_TABLE_DETAILS, details);
      return;
    }
    HConnector connector = dataContext.getConnector(sourceConnectorName);
    if (connector == null) {
      results.addDataSet(component, DATA_TABLE_DETAILS, details);
      return;
    }

    // Get the rows
    //
    details.rows = connector.retrieveRows(dataContext);

    // Calculate the width and height of the text in the given font
    //
    SVGGraphics2D gc = HopSvgGraphics2D.newDocument();

    // Get sizes and string values
    //
    details.columnSizesList = new ArrayList<>();
    details.rowStringsList = new ArrayList<>();
    details.maxWidths = new ArrayList<>();
    details.maxHeights = new ArrayList<>();

    details.rowMeta =
        getRowsAndFieldInformation(
            gc,
            details.rows,
            details.columnSizesList,
            details.rowStringsList,
            details.maxWidths,
            details.maxHeights,
            renderContext);

    // Total width?
    //
    for (int width : details.maxWidths) {
      details.totalWidth += width + 2 * horizontalMargin;
    }

    int totalHeight = 0;
    for (int height : details.maxHeights) {
      details.totalHeight += height + 2 * verticalMargin;
    }

    // Before we go, store all the information we already collected so we don't have to calculate it
    // all again...
    //
    results.addDataSet(component, DATA_TABLE_DETAILS, details);
  }

  /**
   * Calculate the imageSize of the table, pretty much calculating the sizes of each element in the
   * data grid We store all the information in the Results data set
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

    // Get data information back
    //
    TableDetails details = (TableDetails) results.getDataSet(component, DATA_TABLE_DETAILS);

    // Retain the location, adjust the width and Height
    //
    return new HSize(details.totalWidth, details.totalHeight);
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

    // Get data information back
    //
    TableDetails details = (TableDetails) results.getDataSet(component, DATA_TABLE_DETAILS);

    // In case we have no specified columns, we take all the input data...
    //
    if (columnSelection.size() == 0
        && org.apache.commons.lang3.StringUtils.isNotBlank(sourceConnectorName)) {
      HConnector connector = dataContext.getConnector(sourceConnectorName);
      if (connector != null) {
        IRowMeta inputFields = connector.getConnector().describeOutput(dataContext);
        for (IValueMeta inputField : inputFields.getValueMetaList()) {
          HColumn column = new HColumn(inputField.getName());
          columnSelection.add(column);
        }
      }
    }

    // Get the current page on which we're rendering...
    // Create a new one if we need to move on to a next page
    //
    HRenderPage renderPage = results.getCurrentRenderPage(page);

    // Calculate the expected geometry for this component
    //
    HGeometry expectedGeometry =
        getExpectedGeometry(presentation, page, component, dataContext, renderContext, results);

    // Calculate the height until the end of the page...
    // How much more can we fit onto the page?
    //
    boolean addFragment = true;
    int partNumber = 1;

    int remainingHeight = presentation.getUsableHeight(page) - expectedGeometry.getY();

    List<List<HTextGeometry>> columnSizesList = details.columnSizesList;
    List<List<String>> rowStringsList = details.rowStringsList;
    List<Integer> maxWidths = details.maxWidths;
    List<Integer> maxHeights = details.maxHeights;
    IRowMeta rowMeta = details.rowMeta;

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

        if (header && headerOnEveryPage && partNumber > 1) {
          // The part is actually a bit taller...
          //
          partHeight += maxHeights.get(0) + 2 * verticalMargin;
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

        if (header && headerOnEveryPage) {
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

    // Let's not forget the dangling part on the last page...
    //
    if (partHeight > 0) {
      HGeometry partGeometry = expectedGeometry.clone();
      if (header && headerOnEveryPage && partNumber > 1) {
        // The part is actually a bit taller...
        //
        partHeight += maxHeights.get(0) + 2 * verticalMargin;
      }
      partGeometry.setHeight(partHeight);

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
    TableDetails details = (TableDetails) results.getDataSet(component, DATA_TABLE_DETAILS);

    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();

    setBackgroundBorderFont(gc, componentGeometry, renderContext);

    // Get sizes and string values from data set...
    //
    List<List<HTextGeometry>> columnSizesList = details.columnSizesList;
    List<List<String>> rowStringsList = details.rowStringsList;
    List<Integer> maxWidths = details.maxWidths;
    List<Integer> maxHeights = details.maxHeights;

    int startRow = (int) layoutResult.getDataMap().get(DATA_START_ROW);
    int endRow = (int) layoutResult.getDataMap().get(DATA_END_ROW);

    // Now start drawing the table...
    //
    int y = componentGeometry.getY();

    if (header && headerOnEveryPage && startRow > 0) {
      // Render the header, data is on row 0
      //
      int maxHeight = maxHeights.get(0);
      y =
          renderLine(
              gc,
              y,
              0,
              maxHeight,
              rowStringsList,
              columnSizesList,
              componentGeometry,
              maxWidths,
              true,
              renderContext,
              component,
              layoutResult,
              offSet);
    }

    for (int rowNr = startRow; rowNr < endRow; rowNr++) {
      int maxHeight = maxHeights.get(rowNr);
      y =
          renderLine(
              gc,
              y,
              rowNr,
              maxHeight,
              rowStringsList,
              columnSizesList,
              componentGeometry,
              maxWidths,
              rowNr == 0,
              renderContext,
              component,
              layoutResult,
              offSet);
    }

    drawBorder(gc, componentGeometry, renderContext);
  }

  private int renderLine(
      SVGGraphics2D gc,
      int y,
      int rowNr,
      int maxHeight,
      List<List<String>> rowStringsList,
      List<List<HTextGeometry>> columnSizesList,
      HGeometry componentGeometry,
      List<Integer> maxWidths,
      boolean firstRow,
      IRenderContext renderContext,
      HComponent component,
      HComponentLayoutResult layoutResult,
      HPosition offSet)
      throws HException {
    List<HTextGeometry> columnSizes = columnSizesList.get(rowNr);
    List<String> rowStrings = rowStringsList.get(rowNr);
    List<DrawnItem> drawnItems = layoutResult.getRenderPage().getDrawnItems();

    int x = componentGeometry.getX();

    for (int c = 0; c < columnSizes.size(); c++) {
      HColumn hopperColumn = columnSelection.get(c);
      int maxWidth = maxWidths.get(c);
      HTextGeometry textGeometry = columnSizes.get(c);
      String text = rowStrings.get(c);

      enableColor(gc, lookupDefaultColor(renderContext));
      if (header && (rowNr == 0 || headerOnEveryPage && firstRow)) {
        enableFont(gc, headerFont);
      } else {
        enableFont(gc, lookupDefaultFont(renderContext));
      }

      int stringX;
      int stringY;

      switch (hopperColumn.getHorizontalAlignment()) {
        case LEFT:
          stringX = x + textGeometry.getOffsetX() + horizontalMargin;
          stringY = y + textGeometry.getOffsetY() + verticalMargin;
          break;
        case RIGHT:
          stringX = x + maxWidth + horizontalMargin - textGeometry.getWidth();
          stringY = y + textGeometry.getOffsetY() + verticalMargin;
          break;
        case CENTER:
          stringX = x + ((maxWidth + horizontalMargin * 2) - textGeometry.getWidth()) / 2;
          stringY = y + textGeometry.getOffsetY() + verticalMargin;
          break;
        default:
          throw new HException(
              "Horizontal column alignment "
                  + hopperColumn.getHorizontalAlignment()
                  + " is not yet supported");
      }

      int cellWidth = maxWidth + horizontalMargin * 2;
      int cellHeight = maxHeight + verticalMargin * 2;

      // Do we need a specific background color for the header?
      //
      if (header && (rowNr == 0 || headerOnEveryPage && firstRow)) {
        if (headerBackGroundColor != null) {
          enableColor(gc, headerBackGroundColor);
          gc.fillRect(x, y, cellWidth, cellHeight);
        }
      } else {
        HColorRGB bg = lookupBackgroundColor(renderContext);
        if (background && bg != null) {
          enableColor(gc, bg);
          gc.fillRect(x, y, cellWidth, cellHeight);
        }
      }

      enableColor(gc, lookupDefaultColor(renderContext));
      if (StringUtils.isNotEmpty(text)) {
        gc.drawString(text, stringX, stringY);
      }

      DrawnContext drawnContext = new DrawnContext(text);
      drawnContext.getDimensions().add(hopperColumn);

      // Add this to the drawn areas.
      DrawnItem drawnItem =
          new DrawnItem(
              component.getName(),
              component.getComponent().getPluginId(),
              layoutResult.getPartNumber(),
              DrawnItem.DrawnItemType.ComponentItem,
              firstRow ? DrawnItem.Category.Header.name() : DrawnItem.Category.Cell.name(),
              0,
              0,
              new HGeometry(offSet.getX() + x, offSet.getY() + y, cellWidth, cellHeight),
              drawnContext);
      drawnItems.add(drawnItem);

      enableColor(gc, lookupGridColor(renderContext));
      Stroke oldStroke = gc.getStroke();
      if (StringUtils.isNotEmpty(gridLineWidth)) {
        gc.setStroke(new BasicStroke(Float.parseFloat(gridLineWidth)));
      }
      gc.drawRect(x, y, cellWidth, cellHeight);
      if (StringUtils.isNotEmpty(gridLineWidth)) {
        gc.setStroke(oldStroke);
      }

      x += maxWidth + horizontalMargin * 2;
    }
    y += maxHeight + verticalMargin * 2;

    return y;
  }

  public HColumn findColumn(String columnName) {
    for (HColumn column : columnSelection) {
      if (column.getColumnName().equalsIgnoreCase(columnName)) {
        return column;
      }
    }
    return null;
  }

  private IRowMeta getRowsAndFieldInformation(
      SVGGraphics2D gc,
      List<RowMetaAndData> rows,
      List<List<HTextGeometry>> columnSizesList,
      List<List<String>> rowStringsList,
      List<Integer> maxWidths,
      List<Integer> maxHeights,
      IRenderContext renderContext)
      throws HException {
    // No rows: all done
    //
    if (rows.size() == 0) {
      return null;
    }

    IRowMeta rowMetaInput = rows.get(0).getRowMeta();
    IRowMeta rowMeta = new RowMeta();
    int columnIndexes[] = new int[columnSelection.size()];

    for (int i = 0; i < columnSelection.size(); i++) {
      HColumn hopperColumn = columnSelection.get(i);
      int valueMetaIndex = rowMetaInput.indexOfValue(hopperColumn.getColumnName());
      if (valueMetaIndex >= 0) {
        IValueMeta valueMeta = rowMetaInput.getValueMeta(valueMetaIndex).clone();
        columnIndexes[i] = valueMetaIndex;
        rowMeta.addValueMeta(valueMeta);
      } else {
        throw new HException(
            "Unable to find column '" + hopperColumn.getColumnName() + "' in the connector input");
      }
    }

    for (int i = 0; i < columnIndexes.length; i++) {
      IValueMeta valueMeta = rowMetaInput.getValueMeta(columnIndexes[i]);
      HColumn hopperColumn = columnSelection.get(i);
      if (StringUtils.isNotEmpty(hopperColumn.getFormatMask())) {
        valueMeta.setConversionMask(hopperColumn.getFormatMask());
      }
    }

    // Set length min values
    //
    for (int i = 0; i < columnSelection.size(); i++) {
      maxWidths.add(0);
    }

    // Calculate header sizes...
    //
    if (header) {
      // One header font for all values
      //
      enableFont(gc, headerFont);
      List<HTextGeometry> columnSizes = new ArrayList<>();
      List<String> rowStrings = new ArrayList<>();
      int maxHeight = 0;
      for (int i = 0; i < columnSelection.size(); i++) {
        HColumn hopperColumn = columnSelection.get(i);
        IValueMeta valueMeta = rowMeta.getValueMeta(columnIndexes[i]);

        String text;
        if (StringUtils.isNotEmpty(hopperColumn.getHeaderValue())) {
          text = hopperColumn.getHeaderValue();
        } else {
          text = hopperColumn.getColumnName();
        }

        // We print the name in the header...
        //
        rowStrings.add(text);

        HTextGeometry textGeometry = calculateTextGeometry(gc, text);

        columnSizes.add(textGeometry);

        maxWidths.set(i, textGeometry.getWidth());
        if (textGeometry.getHeight() > maxHeight) {
          maxHeight = textGeometry.getHeight();
        }
      }
      columnSizesList.add(columnSizes);
      maxHeights.add(maxHeight);
      rowStringsList.add(rowStrings);
    }

    // First determine field string sizes...
    //
    int globalMaxHeight = 0;
    for (RowMetaAndData row : rows) {
      List<HTextGeometry> columnSizes = new ArrayList<>();
      List<String> rowStrings = new ArrayList<>();
      int maxHeight = 0;
      for (int i = 0; i < columnIndexes.length; i++) {
        HColumn hopperColumn = columnSelection.get(i);
        IValueMeta valueMeta = rowMeta.getValueMeta(i);

        String text;
        try {
          text = valueMeta.getString(row.getData()[columnIndexes[i]]);
        } catch (HopValueException e) {
          text = e.getMessage();
        }
        rowStrings.add(text);

        enableFont(gc, lookupDefaultFont(renderContext));
        HTextGeometry textGeometry = calculateTextGeometry(gc, text);

        columnSizes.add(textGeometry);

        if (textGeometry.getWidth() > maxWidths.get(i)) {
          maxWidths.set(i, textGeometry.getWidth());
        }
        if (textGeometry.getHeight() > maxHeight) {
          maxHeight = textGeometry.getHeight();
        }
      }
      columnSizesList.add(columnSizes);
      maxHeights.add(maxHeight);
      rowStringsList.add(rowStrings);
      if (maxHeight > globalMaxHeight) {
        globalMaxHeight = maxHeight;
      }
    }

    // The text geometry can be different for each string on a line.
    // Therefor we need to calculate and get the maximum offsets
    //
    for (List<HTextGeometry> columnSizes : columnSizesList) {
      int maxOffSetY = 0;
      for (HTextGeometry columnSize : columnSizes) {
        if (columnSize.getOffsetY() > maxOffSetY) {
          maxOffSetY = columnSize.getOffsetY();
        }
      }
      for (HTextGeometry columnSize : columnSizes) {
        columnSize.setOffsetY(maxOffSetY);
      }
    }

    // If evenHeights set highest row for all rows
    //
    if (evenHeights) {
      // If we have a header, leave that one alone.
      //
      for (int i = 0; i < maxHeights.size(); i++) {
        maxHeights.set(i, globalMaxHeight);
      }
    }

    // Explicit column width (> 0) overrides content-based auto width; 0 keeps auto-detect.
    //
    for (int i = 0; i < columnSelection.size(); i++) {
      HColumn hopperColumn = columnSelection.get(i);
      if (hopperColumn != null && hopperColumn.getWidth() > 0) {
        maxWidths.set(i, hopperColumn.getWidth());
      }
    }

    return rowMeta;
  }

  protected HColorRGB lookupGridColor(IRenderContext renderContext) throws HException {
    if (gridColor != null) {
      return gridColor;
    }
    HColorRGB color = null;
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupGridColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException("No grid color nor default color defined (no theme used or found)");
  }
}
