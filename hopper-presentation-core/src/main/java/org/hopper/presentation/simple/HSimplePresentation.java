package org.hopper.presentation.simple;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.Constants;
import org.hopper.core.HColumn;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.GanttTask;
import org.hopper.presentation.component.types.chart.HBarChartComponent;
import org.hopper.presentation.component.types.chart.HGanttChartComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.presentation.component.types.textblock.HTextBlockComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.memory.HInMemoryRowsConnector;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.layout.HLayoutMode;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;

/**
 * Fluent builder for generated trend / comparison / dashboard presentations. Hosts inject rows;
 * the catalog is isolated (memory) and does not use Hop GUI project metadata.
 */
public final class HSimplePresentation {

  private final String name;
  private String description = "";
  private boolean continuous = true;
  private int designWidth = Constants.DEFAULT_CONTINUOUS_DESIGN_WIDTH;
  private int trendTileHeight = 280;
  private final List<Tile> tiles = new ArrayList<>();
  private int connectorSeq;

  private HSimplePresentation(String name) {
    this.name = name;
  }

  public static HSimplePresentation dashboard(String name) {
    return new HSimplePresentation(name);
  }

  public HSimplePresentation description(String description) {
    this.description = description == null ? "" : description;
    return this;
  }

  public HSimplePresentation continuous() {
    this.continuous = true;
    return this;
  }

  public HSimplePresentation paginated() {
    this.continuous = false;
    return this;
  }

  public HSimplePresentation designWidth(int designWidth) {
    this.designWidth = designWidth;
    return this;
  }

  /** Pixel height of each trend tile (default 280). */
  public HSimplePresentation trendTileHeight(int pixels) {
    this.trendTileHeight = Math.max(80, pixels);
    return this;
  }

  public HSimplePresentation addTitle(String text) {
    tiles.add(Tile.title(text));
    return this;
  }

  public HSimplePresentation addNote(String text) {
    tiles.add(Tile.note(text));
    return this;
  }

  public HSimplePresentation addTrend(
      String title, List<RowMetaAndData> rows, String categoryColumn, String valueColumn) {
    return addTrend(title, rows, categoryColumn, null, valueColumn);
  }

  /**
   * Multi-series line chart. {@code seriesColumn} is the legend dimension (e.g. transform name).
   */
  public HSimplePresentation addTrend(
      String title,
      List<RowMetaAndData> rows,
      String categoryColumn,
      String seriesColumn,
      String valueColumn) {
    tiles.add(Tile.trend(title, rows, categoryColumn, seriesColumn, valueColumn));
    return this;
  }

  /** Horizontal Gantt from inline tasks (no connector). */
  public HSimplePresentation addGantt(String title, List<GanttTask> tasks) {
    return addGantt(title, tasks, 0);
  }

  /**
   * Horizontal Gantt. {@code reservedRows} sizes the page for that many rows even when {@code
   * tasks} is still empty (e.g. known workflow action count).
   */
  public HSimplePresentation addGantt(String title, List<GanttTask> tasks, int reservedRows) {
    tiles.add(Tile.gantt(title, tasks, reservedRows));
    return this;
  }

  /** Page/tile height for a Gantt with {@code rows} task rows (fixed 28px pitch, not viewport). */
  public static int ganttPixelHeight(int rows) {
    // title 24 + axis 18 + vertical margins 12 + n * rowHeight 28
    return Math.min(4000, 54 + Math.max(rows, 1) * 28);
  }

  public HSimplePresentation addComparison(
      String title,
      List<RowMetaAndData> rows,
      String categoryColumn,
      String seriesColumn,
      String valueColumn) {
    tiles.add(Tile.comparison(title, rows, categoryColumn, seriesColumn, valueColumn));
    return this;
  }

  public HSimplePresentation addTable(
      String title, List<RowMetaAndData> rows, String... columns) {
    tiles.add(Tile.table(title, rows, columns));
    return this;
  }

  public HGeneratedCatalog build() throws HException {
    if (StringUtils.isBlank(name)) {
      throw new HException("Generated presentation name is required");
    }
    if (tiles.isEmpty()) {
      addNote("No content");
    }

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    try {
      ensureThemes(provider);

      HPresentation presentation = new HPresentation();
      presentation.setName(name);
      presentation.setDescription(description);
      presentation.setDefaultThemeName(Constants.GENERATED_THEME_NAME);
      presentation.setDarkThemeName(Constants.GENERATED_DARK_THEME_NAME);
      presentation.setLayoutMode(
          continuous ? HLayoutMode.CONTINUOUS.wireValue() : HLayoutMode.PAGINATED.wireValue());
      presentation.setDesignWidth(designWidth);

      int y = 16;
      int gap = 12;
      int stackedHeight = 16;
      for (Tile tile : tiles) {
        stackedHeight += tileHeight(tile) + gap;
      }
      HPage page =
          continuous
              ? new HPage(designWidth, Math.max(stackedHeight + 16, 120), 16, 16, 16, 16)
              : HPage.getA4(false);
      presentation.getPages().add(page);

      for (int i = 0; i < tiles.size(); i++) {
        int height = tileHeight(tiles.get(i));
        addTile(provider, page, tiles.get(i), i, y, y + height);
        y += height + gap;
      }
      if (continuous) {
        page.setHeight(Math.max(y + 16, 120));
      }

      provider.getSerializer(HPresentation.class).save(presentation);
      return new HGeneratedCatalog(provider, presentation);
    } catch (HopException e) {
      throw new HException("Unable to build generated presentation " + name, e);
    }
  }

  private int tileHeight(Tile tile) {
    return switch (tile.kind) {
      case TITLE -> 44;
      case NOTE -> 96;
      case TREND -> trendTileHeight;
      case COMPARISON -> 280;
      case TABLE -> 320;
      case GANTT -> ganttHeight(tile);
    };
  }

  private void addTile(
      IHopMetadataProvider provider, HPage page, Tile tile, int index, int topPx, int bottomPx)
      throws HopException, HException {
    String componentName = "tile-" + index;
    HLayoutBuilder layout =
        new HLayoutBuilder().left(16).right(-16).top(0, topPx).bottomFromTop(0, bottomPx);

    HComponent component;
    switch (tile.kind) {
      case TITLE -> {
        HLabelComponent label = new HLabelComponent(ConstNvl(tile.title));
        label.setDefaultFont(new HFont("Arial", "20", true, false));
        component = new HComponent(componentName, label);
      }
      case NOTE -> component =
          new HComponent(componentName, new HTextBlockComponent(ConstNvl(tile.title)));
      case TREND -> {
        String connectorName = nextConnectorName("trend");
        saveRows(provider, connectorName, tile.rows, chartSchema(tile));
        HLineChartComponent chart = new HLineChartComponent(connectorName);
        configureChart(chart, tile);
        component = new HComponent(componentName, chart);
      }
      case COMPARISON -> {
        String connectorName = nextConnectorName("compare");
        saveRows(provider, connectorName, tile.rows, chartSchema(tile));
        HBarChartComponent chart = new HBarChartComponent(connectorName);
        configureChart(chart, tile);
        component = new HComponent(componentName, chart);
      }
      case TABLE -> {
        String connectorName = nextConnectorName("table");
        saveRows(provider, connectorName, tile.rows, null);
        HTableComponent table = new HTableComponent(connectorName, tableColumns(tile));
        table.setHeader(true);
        table.setEvenHeights(true);
        component = new HComponent(componentName, table);
      }
      case GANTT -> {
        HGanttChartComponent gantt = new HGanttChartComponent();
        gantt.setTitle(ConstNvl(tile.title));
        gantt.setShowingTitle(true);
        gantt.setShowingAxisTicks(true);
        gantt.setShowingDurationLabels(true);
        gantt.setRowHeight(28);
        gantt.setInlineTasks(tile.tasks);
        component = new HComponent(componentName, gantt);
      }
      default -> throw new HException("Unknown tile kind " + tile.kind);
    }
    component.setLayout(layout.build());
    page.getComponents().add(component);
  }

  private void configureChart(org.hopper.presentation.component.types.chart.HBaseChartComponent chart, Tile tile) {
    chart.setTitle(ConstNvl(tile.title));
    chart.setHorizontalDimensions(
        List.of(
            new HDimension(
                tile.categoryColumn,
                humanize(tile.categoryColumn),
                HHorizontalAlignment.CENTER,
                HVerticalAlignment.MIDDLE)));
    if (StringUtils.isNotBlank(tile.seriesColumn)) {
      chart.setVerticalDimensions(
          List.of(
              new HDimension(
                  tile.seriesColumn,
                  humanize(tile.seriesColumn),
                  HHorizontalAlignment.CENTER,
                  HVerticalAlignment.MIDDLE)));
    }
    HFact fact =
        new HFact(
            tile.valueColumn,
            humanize(tile.valueColumn),
            HHorizontalAlignment.RIGHT,
            HVerticalAlignment.MIDDLE,
            AggregationMethod.SUM,
            null);
    chart.setFacts(List.of(fact));
    chart.setShowingHorizontalLabels(true);
    chart.setShowingVerticalLabels(true);
    chart.setShowingLegend(StringUtils.isNotBlank(tile.seriesColumn));
    chart.setUsingZeroBaseline(true);
    chart.setHorizontalMargin(8);
    chart.setVerticalMargin(8);
    chart.setBorder(false);
    chart.setBackground(false);
  }

  private List<HColumn> tableColumns(Tile tile) {
    List<HColumn> columns = new ArrayList<>();
    if (tile.columns != null && tile.columns.length > 0) {
      for (String column : tile.columns) {
        if (StringUtils.isNotBlank(column)) {
          columns.add(new HColumn(column, humanize(column), HHorizontalAlignment.LEFT, HVerticalAlignment.MIDDLE));
        }
      }
      return columns;
    }
    IRowMeta meta = firstMeta(tile.rows);
    if (meta != null) {
      for (int i = 0; i < meta.size(); i++) {
        String col = meta.getValueMeta(i).getName();
        columns.add(new HColumn(col, humanize(col), HHorizontalAlignment.LEFT, HVerticalAlignment.MIDDLE));
      }
    }
    return columns;
  }

  private void saveRows(
      IHopMetadataProvider provider,
      String connectorName,
      List<RowMetaAndData> rows,
      IRowMeta schema)
      throws HopException {
    HInMemoryRowsConnector inMemory = new HInMemoryRowsConnector(rows);
    if ((rows == null || rows.isEmpty()) && schema != null) {
      inMemory.setOutputRowMeta(schema);
    }
    HConnector connector = new HConnector(connectorName, inMemory);
    provider.getSerializer(HConnector.class).save(connector);
  }

  private static IRowMeta chartSchema(Tile tile) {
    RowMeta meta = new RowMeta();
    if (StringUtils.isNotBlank(tile.categoryColumn)) {
      meta.addValueMeta(new ValueMetaString(tile.categoryColumn));
    }
    if (StringUtils.isNotBlank(tile.seriesColumn)) {
      meta.addValueMeta(new ValueMetaString(tile.seriesColumn));
    }
    if (StringUtils.isNotBlank(tile.valueColumn)) {
      meta.addValueMeta(new ValueMetaNumber(tile.valueColumn));
    }
    return meta.size() == 0 ? null : meta;
  }

  private String nextConnectorName(String prefix) {
    connectorSeq++;
    return name.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + prefix + "-" + connectorSeq;
  }

  private static void ensureThemes(IHopMetadataProvider provider) throws HopException {
    var themes = provider.getSerializer(HTheme.class);
    if (themes.load(Constants.DEFAULT_THEME_NAME) == null) {
      themes.save(HTheme.getDefault());
    }
    if (themes.load(Constants.DEFAULT_DARK_THEME_NAME) == null) {
      themes.save(HTheme.getDefaultDark());
    }
    if (themes.load(Constants.GENERATED_THEME_NAME) == null) {
      themes.save(HGeneratedThemes.light());
    }
    if (themes.load(Constants.GENERATED_DARK_THEME_NAME) == null) {
      themes.save(HGeneratedThemes.dark());
    }
  }

  private static IRowMeta firstMeta(List<RowMetaAndData> rows) {
    if (rows == null) {
      return null;
    }
    for (RowMetaAndData row : rows) {
      if (row != null && row.getRowMeta() != null) {
        return row.getRowMeta();
      }
    }
    return null;
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }

  private static int ganttHeight(Tile tile) {
    int n = tile.tasks == null ? 0 : tile.tasks.size();
    n = Math.max(n, tile.reservedRows);
    return ganttPixelHeight(n);
  }

  static String humanize(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return "";
    }
    String spaced = fieldName.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private enum Kind {
    TITLE,
    NOTE,
    TREND,
    COMPARISON,
    TABLE,
    GANTT
  }

  private static final class Tile {
    private final Kind kind;
    private final String title;
    private final List<RowMetaAndData> rows;
    private final String categoryColumn;
    private final String seriesColumn;
    private final String valueColumn;
    private final String[] columns;
    private final List<GanttTask> tasks;
    private final int reservedRows;

    private Tile(
        Kind kind,
        String title,
        List<RowMetaAndData> rows,
        String categoryColumn,
        String seriesColumn,
        String valueColumn,
        String[] columns,
        List<GanttTask> tasks,
        int reservedRows) {
      this.kind = kind;
      this.title = title;
      this.rows = rows == null ? List.of() : rows;
      this.categoryColumn = categoryColumn;
      this.seriesColumn = seriesColumn;
      this.valueColumn = valueColumn;
      this.columns = columns;
      this.tasks = tasks == null ? List.of() : tasks;
      this.reservedRows = Math.max(0, reservedRows);
    }

    static Tile title(String text) {
      return new Tile(Kind.TITLE, text, null, null, null, null, null, null, 0);
    }

    static Tile note(String text) {
      return new Tile(Kind.NOTE, text, null, null, null, null, null, null, 0);
    }

    static Tile trend(
        String title, List<RowMetaAndData> rows, String category, String series, String value) {
      return new Tile(Kind.TREND, title, rows, category, series, value, null, null, 0);
    }

    static Tile comparison(
        String title, List<RowMetaAndData> rows, String category, String series, String value) {
      return new Tile(Kind.COMPARISON, title, rows, category, series, value, null, null, 0);
    }

    static Tile table(String title, List<RowMetaAndData> rows, String[] columns) {
      return new Tile(Kind.TABLE, title, rows, null, null, null, columns, null, 0);
    }

    static Tile gantt(String title, List<GanttTask> tasks, int reservedRows) {
      return new Tile(Kind.GANTT, title, null, null, null, null, null, tasks, reservedRows);
    }
  }
}
