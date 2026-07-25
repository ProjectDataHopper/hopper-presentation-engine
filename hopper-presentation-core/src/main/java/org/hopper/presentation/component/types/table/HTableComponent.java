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
import org.hopper.presentation.interaction.HInteractionLocationOption;
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
      label = "Header font",
      toolTip =
          "Optional. When empty, uses the active theme's header font (then the theme default font).")
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
      label = "Header background color",
      toolTip =
          "Optional. When empty, uses the active theme's header background color (if set).")
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
  public List<HInteractionLocationOption> getPossibleInteractionLocations() {
    List<String> cols = new ArrayList<>();
    if (columnSelection != null) {
      for (HColumn column : columnSelection) {
        if (column != null && StringUtils.isNotBlank(column.getColumnName())) {
          String name = column.getColumnName().trim();
          if (!cols.contains(name)) {
            cols.add(name);
          }
        }
      }
    }
    List<HInteractionLocationOption> options = new ArrayList<>();
    options.add(
        HInteractionLocationOption.item(
            "cell", "Table cell", DrawnItem.Category.Cell, cols, true));
    options.add(
        HInteractionLocationOption.item(
            "header", "Table header", DrawnItem.Category.Header, cols, true));
    return options;
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
            renderContext,
            presentation,
            page,
            results);

    // Total width?
    //
    for (int width : details.maxWidths) {
      details.totalWidth += width + 2 * horizontalMargin;
    }

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

        // Already on the last allowed render page: keep that part, drop remaining rows.
        // Do not use pagesTruncated from measure-phase here — that flag is set whenever the
        // connector has more rows than we measured, and would skip filling the last page.
        //
        if (results.isAtRenderPageLimit()) {
          results.markPagesTruncated();
          startLine = maxHeights.size();
          partHeight = 0;
          break;
        }

        // Open the next page and place the overflowing row there (including the last allowed page)
        //
        HRenderPage previousPage = renderPage;
        partNumber++;
        renderPage = results.addNewPage(page, renderPage);
        if (renderPage == previousPage) {
          // Cap refused a new sheet — stop without packing leftover rows onto the last page
          results.markPagesTruncated();
          startLine = maxHeights.size();
          partHeight = 0;
          break;
        }
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
    List<Integer> maxHeights = details.maxHeights;

    // Layout may assign a narrower width than content-based totalWidth (e.g. left+right
    // attachments). Scale column content widths so cells fill the allocated geometry without
    // spilling into each other or past the component box.
    List<Integer> paintWidths =
        fitColumnWidthsToGeometry(details.maxWidths, componentGeometry.getWidth());

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
              paintWidths,
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
              paintWidths,
              rowNr == 0,
              renderContext,
              component,
              layoutResult,
              offSet);
    }

    drawBorder(gc, componentGeometry, renderContext);
  }

  /**
   * Scale column content widths so {@code sum(width + 2*horizontalMargin)} fits in {@code
   * geometryWidth}. Only shrinks when overflowing; never expands (empty space stays on the right).
   * Returns a new list; the input is not modified.
   */
  List<Integer> fitColumnWidthsToGeometry(List<Integer> maxWidths, int geometryWidth) {
    if (maxWidths == null || maxWidths.isEmpty()) {
      return maxWidths == null ? List.of() : new ArrayList<>(maxWidths);
    }
    int colCount = maxWidths.size();
    int marginsTotal = colCount * 2 * Math.max(0, horizontalMargin);
    int contentOnly = 0;
    List<Integer> paintWidths = new ArrayList<>(colCount);
    for (int w : maxWidths) {
      int cw = Math.max(0, w);
      paintWidths.add(cw);
      contentOnly += cw;
    }
    int total = contentOnly + marginsTotal;
    if (geometryWidth <= 0 || contentOnly <= 0 || total <= geometryWidth) {
      return paintWidths;
    }
    int availableContent = Math.max(colCount, geometryWidth - marginsTotal);
    int used = 0;
    for (int i = 0; i < colCount; i++) {
      int w = paintWidths.get(i);
      int nw;
      if (i == colCount - 1) {
        nw = Math.max(1, availableContent - used);
      } else {
        nw = Math.max(1, (int) Math.round((double) w * availableContent / contentOnly));
        used += nw;
      }
      paintWidths.set(i, nw);
    }
    return paintWidths;
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

      boolean isHeaderRow = header && (rowNr == 0 || headerOnEveryPage && firstRow);
      if (isHeaderRow) {
        enableFont(gc, lookupHeaderFont(renderContext));
      } else {
        enableFont(gc, lookupDefaultFont(renderContext));
      }

      // Prefer live font metrics for alignment when geometry width was not measured (legacy
      // fixed-width path stored 0). Always fall back to stored width when available.
      int textWidth = textGeometry.getWidth();
      if (textWidth <= 0 && StringUtils.isNotEmpty(text)) {
        textWidth = gc.getFontMetrics().stringWidth(text);
      }

      int stringX;
      int stringY;
      int contentLeft = x + horizontalMargin;
      int cellWidth = maxWidth + horizontalMargin * 2;
      int cellHeight = maxHeight + verticalMargin * 2;

      switch (hopperColumn.getHorizontalAlignment()) {
        case LEFT:
          stringX = contentLeft + textGeometry.getOffsetX();
          stringY = y + textGeometry.getOffsetY() + verticalMargin;
          break;
        case RIGHT:
          // Right edge of content area: x + horizontalMargin + maxWidth
          stringX = contentLeft + maxWidth - textWidth;
          stringY = y + textGeometry.getOffsetY() + verticalMargin;
          break;
        case CENTER:
          stringX = x + (cellWidth - textWidth) / 2;
          stringY = y + textGeometry.getOffsetY() + verticalMargin;
          break;
        default:
          throw new HException(
              "Horizontal column alignment "
                  + hopperColumn.getHorizontalAlignment()
                  + " is not yet supported");
      }

      // Header: component/theme header background. Body: optional component background.
      //
      if (isHeaderRow) {
        HColorRGB headerBg = lookupHeaderBackGroundColor(renderContext);
        if (headerBg != null) {
          enableColor(gc, headerBg);
          gc.fillRect(x, y, cellWidth, cellHeight);
        }
      } else {
        HColorRGB bg = lookupBackgroundColor(renderContext);
        if (background && bg != null) {
          enableColor(gc, bg);
          gc.fillRect(x, y, cellWidth, cellHeight);
        }
      }

      // Header ink from theme headerColor (must contrast with header background); body uses
      // default color.
      if (isHeaderRow) {
        enableColor(gc, lookupHeaderColor(renderContext));
      } else {
        enableColor(gc, lookupDefaultColor(renderContext));
      }
      if (StringUtils.isNotEmpty(text)) {
        // Clip to the cell content box so long values/headers cannot paint over neighbors.
        java.awt.Shape oldClip = gc.getClip();
        gc.clipRect(contentLeft, y, Math.max(0, maxWidth), cellHeight);
        gc.drawString(text, stringX, stringY);
        gc.setClip(oldClip);
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

      x += cellWidth;
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
      IRenderContext renderContext,
      HPresentation presentation,
      HPage page,
      HLayoutResults results)
      throws HException {
    // No rows: all done
    //
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    if (columnSelection == null || columnSelection.isEmpty()) {
      return rows.get(0).getRowMeta();
    }

    IRowMeta rowMetaInput = rows.get(0).getRowMeta();
    IRowMeta rowMeta = new RowMeta();
    int[] columnIndexes = new int[columnSelection.size()];

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
      IValueMeta valueMeta = rowMeta.getValueMeta(i);
      HColumn hopperColumn = columnSelection.get(i);
      if (StringUtils.isNotEmpty(hopperColumn.getFormatMask())) {
        valueMeta.setConversionMask(hopperColumn.getFormatMask());
      }
    }

    // Which columns need content-based width measurement?
    boolean[] measureWidth = new boolean[columnSelection.size()];
    for (int i = 0; i < columnSelection.size(); i++) {
      HColumn col = columnSelection.get(i);
      if (col != null && col.getWidth() > 0) {
        maxWidths.add(col.getWidth());
        measureWidth[i] = false;
      } else {
        maxWidths.add(0);
        measureWidth[i] = true;
      }
    }

    // --- Header (one font setup) ---
    int headerHeight = 0;
    int headerOffsetY = 0;
    if (header) {
      enableFont(gc, lookupHeaderFont(renderContext));
      HTextGeometry probe = calculateTextGeometryFast(gc, "Hg");
      headerHeight = probe.getHeight();
      headerOffsetY = probe.getOffsetY();
      java.awt.FontMetrics headerFm = gc.getFontMetrics();
      List<HTextGeometry> columnSizes = new ArrayList<>(columnSelection.size());
      List<String> rowStrings = new ArrayList<>(columnSelection.size());
      for (int i = 0; i < columnSelection.size(); i++) {
        HColumn hopperColumn = columnSelection.get(i);
        String text =
            StringUtils.isNotEmpty(hopperColumn.getHeaderValue())
                ? hopperColumn.getHeaderValue()
                : hopperColumn.getColumnName();
        if (text == null) {
          text = "";
        }
        rowStrings.add(text);
        // Always measure text width for alignment (RIGHT/CENTER). Fixed column.width only
        // freezes maxWidths — never skip measuring the string itself.
        int w = text.isEmpty() ? 0 : headerFm.stringWidth(text);
        if (measureWidth[i] && w > maxWidths.get(i)) {
          maxWidths.set(i, w);
        }
        columnSizes.add(new HTextGeometry(w, headerHeight, 0, headerOffsetY));
      }
      // Shared baseline within the header row
      for (HTextGeometry g : columnSizes) {
        g.setOffsetY(headerOffsetY);
        g.setHeight(headerHeight);
      }
      columnSizesList.add(columnSizes);
      maxHeights.add(headerHeight);
      rowStringsList.add(rowStrings);
    }

    // --- Body font once ---
    enableFont(gc, lookupDefaultFont(renderContext));
    HTextGeometry bodyProbe = calculateTextGeometryFast(gc, "Hg");
    int bodyHeight = bodyProbe.getHeight();
    int bodyOffsetY = bodyProbe.getOffsetY();
    java.awt.FontMetrics bodyFm = gc.getFontMetrics();

    // Cap how many data rows we measure/paint: only enough to fill max render pages.
    // (Full connector scan + TextLayout per cell was ~4s for 5k rows.)
    int maxDataRows = estimateMaxDataRowsToMeasure(presentation, page, results, bodyHeight, headerHeight);
    int dataRowCount = Math.min(rows.size(), maxDataRows);
    if (rows.size() > dataRowCount && results != null) {
      results.markPagesTruncated();
    }

    // Pre-size lists for fewer reallocations
    int expectedLines = (header ? 1 : 0) + dataRowCount;
    if (columnSizesList instanceof ArrayList) {
      ((ArrayList<?>) columnSizesList).ensureCapacity(expectedLines);
    }
    if (rowStringsList instanceof ArrayList) {
      ((ArrayList<?>) rowStringsList).ensureCapacity(expectedLines);
    }
    if (maxHeights instanceof ArrayList) {
      ((ArrayList<?>) maxHeights).ensureCapacity(expectedLines);
    }

    for (int r = 0; r < dataRowCount; r++) {
      RowMetaAndData row = rows.get(r);
      List<HTextGeometry> columnSizes = new ArrayList<>(columnIndexes.length);
      List<String> rowStrings = new ArrayList<>(columnIndexes.length);
      Object[] data = row.getData();
      for (int i = 0; i < columnIndexes.length; i++) {
        IValueMeta valueMeta = rowMeta.getValueMeta(i);
        String text;
        try {
          text = valueMeta.getString(data[columnIndexes[i]]);
        } catch (HopValueException e) {
          text = e.getMessage();
        }
        if (text == null) {
          text = "";
        }
        rowStrings.add(text);

        // Always measure for alignment; only auto-width columns grow maxWidths from content.
        int w = text.isEmpty() ? 0 : bodyFm.stringWidth(text);
        if (measureWidth[i] && w > maxWidths.get(i)) {
          maxWidths.set(i, w);
        }
        // Constant body row height; per-cell width used for RIGHT/CENTER placement
        columnSizes.add(new HTextGeometry(w, bodyHeight, 0, bodyOffsetY));
      }
      columnSizesList.add(columnSizes);
      maxHeights.add(bodyHeight);
      rowStringsList.add(rowStrings);
    }

    // evenHeights: body rows already constant; optionally force header to body height
    if (evenHeights && header && !maxHeights.isEmpty()) {
      // leave header alone historically; body already uniform
    }

    // Explicit widths already applied; re-apply in case measure ran with width 0 then set
    for (int i = 0; i < columnSelection.size(); i++) {
      HColumn hopperColumn = columnSelection.get(i);
      if (hopperColumn != null && hopperColumn.getWidth() > 0) {
        maxWidths.set(i, hopperColumn.getWidth());
      }
    }

    return rowMeta;
  }

  /**
   * How many connector data rows to convert/measure for layout (not including optional header
   * line). Enough to fill {@code maxRenderPages} full body pages with a small safety margin.
   */
  private int estimateMaxDataRowsToMeasure(
      HPresentation presentation,
      HPage page,
      HLayoutResults results,
      int bodyTextHeight,
      int headerTextHeight) {
    int maxPages =
        results != null && results.getMaxRenderPages() > 0
            ? results.getMaxRenderPages()
            : org.hopper.presentation.layout.HLayoutPageLimitSettings.getMaxRenderPages();
    int usable =
        presentation != null && page != null
            ? Math.max(40, presentation.getUsableHeight(page))
            : 800;
    int bodyPitch = Math.max(1, bodyTextHeight + 2 * Math.max(0, verticalMargin));
    int headerPitch = header ? Math.max(1, headerTextHeight + 2 * Math.max(0, verticalMargin)) : 0;

    int rowsPerPageWithHeader =
        Math.max(1, (usable - (header && headerOnEveryPage ? headerPitch : 0)) / bodyPitch);
    int rowsPerPageNoHeader = Math.max(1, usable / bodyPitch);

    int budget;
    if (!header) {
      budget = maxPages * rowsPerPageNoHeader;
    } else if (headerOnEveryPage) {
      budget = maxPages * rowsPerPageWithHeader;
    } else {
      // Header only on first measured line of the table; first page has less body room
      int firstBody = Math.max(1, (usable - headerPitch) / bodyPitch);
      budget = firstBody + Math.max(0, maxPages - 1) * rowsPerPageNoHeader;
    }
    // Safety: a couple extra rows so pagination does not starve the last page
    return Math.max(1, budget + 2);
  }

  protected HColorRGB lookupGridColor(IRenderContext renderContext) throws HException {
    if (gridColor != null) {
      return gridColor;
    }
    // themeName blank/null → PresentationRenderContext uses the presentation default theme
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      try {
        return theme.lookupGridColor();
      } catch (HException e) {
        // Incomplete theme: try default ink next
      }
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    try {
      return lookupDefaultColor(renderContext);
    } catch (HException e) {
      throw new HException("No grid color nor default color defined (no theme used or found)", e);
    }
  }

  /**
   * Header font: component override, else theme {@code headerFont}, else default font. Blank
   * component {@code themeName} resolves via the presentation default theme.
   */
  protected HFont lookupHeaderFont(IRenderContext renderContext) throws HException {
    if (headerFont != null) {
      return headerFont;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      try {
        return theme.lookupHeaderFont();
      } catch (HException e) {
        // fall through to default font
      }
    }
    return lookupDefaultFont(renderContext);
  }

  /**
   * Header text (ink) color from the active theme ({@code headerColor}, else default ink). Blank
   * component {@code themeName} uses the presentation default theme.
   */
  protected HColorRGB lookupHeaderColor(IRenderContext renderContext) throws HException {
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      try {
        return theme.lookupHeaderColor();
      } catch (HException e) {
        // fall through
      }
    }
    return lookupDefaultColor(renderContext);
  }

  /**
   * Header cell background: component override, else theme {@code headerBackGroundColor}. {@code
   * null} means no header fill. Blank component {@code themeName} uses the presentation default
   * theme.
   */
  protected HColorRGB lookupHeaderBackGroundColor(IRenderContext renderContext) throws HException {
    if (headerBackGroundColor != null) {
      return headerBackGroundColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupHeaderBackGroundColor();
    }
    return null;
  }
}
