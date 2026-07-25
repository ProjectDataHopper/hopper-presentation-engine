package org.hopper.presentation.component.types.chart;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.interaction.HInteractionLocationOption;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

/**
 * Horizontal Gantt chart: one row per task, bars from start→end on a shared time axis.
 *
 * <p>Data comes from a connector ({@code taskColumn}, {@code startColumn}, {@code endColumn} or
 * {@code durationColumn}) or from pre-seeded {@link GanttDetails} (timings panel / tests).
 */
@JsonDeserialize(as = HGanttChartComponent.class)
@HComponentPlugin(
    id = "HGanttChartComponent",
    name = "Gantt Chart",
    description = "A horizontal Gantt chart for task timelines and refresh timings",
    image = "ui/images/components/gantt-chart.svg")
@Getter
@Setter
public class HGanttChartComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_GANTT_DETAILS = "Gantt Details";

  @HWidgetElement(
      order = "10000-title",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Title")
  @HopMetadataProperty
  private String title;

  @HWidgetElement(
      order = "10100-taskColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "Task column",
      toolTip = "Column used as the row label")
  @HopMetadataProperty
  private String taskColumn;

  @HWidgetElement(
      order = "10200-startColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "Start column",
      toolTip =
          "Numeric start time (ms). Leave empty with duration column only for sequential "
              + "waterfall (name + time in ms).")
  @HopMetadataProperty
  private String startColumn;

  @HWidgetElement(
      order = "10300-endColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "End column",
      toolTip = "Numeric end time; leave empty when using duration column")
  @HopMetadataProperty
  private String endColumn;

  @HWidgetElement(
      order = "10400-durationColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "Duration / time (ms) column",
      toolTip =
          "Duration in ms. With start: end = start + duration. Without start: sequential waterfall "
              + "bars (each phase after the previous).")
  @HopMetadataProperty
  private String durationColumn;

  @HWidgetElement(
      order = "10500-groupColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "Group column (optional)")
  @HopMetadataProperty
  private String groupColumn;

  @HWidgetElement(
      order = "10600-colorKeyColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      dependsOn = "sourceConnectorName",
      label = "Color key column (optional)",
      toolTip = "Stable theme color key; defaults to task label")
  @HopMetadataProperty
  private String colorKeyColumn;

  @HWidgetElement(
      order = "10700-horizontalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal margin")
  @HopMetadataProperty
  private int horizontalMargin = 8;

  @HWidgetElement(
      order = "10800-verticalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical margin")
  @HopMetadataProperty
  private int verticalMargin = 6;

  @HWidgetElement(
      order = "10900-labelColumnWidth",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Label column width (px)",
      toolTip = "Width reserved for task names on the left (0 = auto ~28%)")
  @HopMetadataProperty
  private int labelColumnWidth = 0;

  @HWidgetElement(
      order = "11000-rowHeight",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Row height (px)",
      toolTip = "Preferred row pitch; rows scale down if needed to fit")
  @HopMetadataProperty
  private int rowHeight = 22;

  @HWidgetElement(
      order = "11100-showingAxisTicks",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show axis ticks?")
  @HopMetadataProperty
  private boolean showingAxisTicks = true;

  @HWidgetElement(
      order = "11200-showingDurationLabels",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show duration on bars?")
  @HopMetadataProperty
  private boolean showingDurationLabels = true;

  @HWidgetElement(
      order = "11300-showingTitle",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show title?")
  @HopMetadataProperty
  private boolean showingTitle = true;

  /**
   * Persisted task snapshot (e.g. System timings Gantt). Preferred over connector when non-empty.
   */
  @HopMetadataProperty private List<GanttTask> embeddedTasks;

  /**
   * Runtime-only tasks for timings panel / tests (not serialized). Preferred over connector when
   * non-empty; {@link #embeddedTasks} still wins for saved presentations.
   */
  @JsonIgnore private transient List<GanttTask> inlineTasks;

  public HGanttChartComponent() {
    super("HGanttChartComponent");
  }

  public HGanttChartComponent(String sourceConnectorName) {
    this();
    this.sourceConnectorName = sourceConnectorName;
  }

  public HGanttChartComponent(HGanttChartComponent c) {
    super("HGanttChartComponent", c);
    this.title = c.title;
    this.taskColumn = c.taskColumn;
    this.startColumn = c.startColumn;
    this.endColumn = c.endColumn;
    this.durationColumn = c.durationColumn;
    this.groupColumn = c.groupColumn;
    this.colorKeyColumn = c.colorKeyColumn;
    this.horizontalMargin = c.horizontalMargin;
    this.verticalMargin = c.verticalMargin;
    this.labelColumnWidth = c.labelColumnWidth;
    this.rowHeight = c.rowHeight;
    this.showingAxisTicks = c.showingAxisTicks;
    this.showingDurationLabels = c.showingDurationLabels;
    this.showingTitle = c.showingTitle;
    if (c.embeddedTasks != null) {
      this.embeddedTasks = new ArrayList<>(c.embeddedTasks);
    }
    if (c.inlineTasks != null) {
      this.inlineTasks = new ArrayList<>(c.inlineTasks);
    }
  }

  @Override
  public HGanttChartComponent clone() {
    return new HGanttChartComponent(this);
  }

  @Override
  public List<HInteractionLocationOption> getPossibleInteractionLocations() {
    List<String> dims = new ArrayList<>();
    if (StringUtils.isNotBlank(taskColumn)) {
      dims.add(taskColumn.trim());
    }
    List<HInteractionLocationOption> options = new ArrayList<>();
    options.add(
        HInteractionLocationOption.item(
            "bar",
            "Gantt bar",
            DrawnItem.Category.GanttBar,
            dims,
            StringUtils.isNotBlank(taskColumn)));
    if (showingTitle && StringUtils.isNotBlank(title)) {
      options.add(HInteractionLocationOption.item("title", "Title", DrawnItem.Category.Title));
    }
    return options;
  }

  /** Seed tasks without a connector (ephemeral timings presentation). */
  public void setInlineTasks(List<GanttTask> tasks) {
    this.inlineTasks = tasks == null ? null : new ArrayList<>(tasks);
  }

  /** Persistable snapshot used by System Gantt presentations. */
  public void setEmbeddedTasks(List<GanttTask> tasks) {
    this.embeddedTasks = tasks == null ? null : new ArrayList<>(tasks);
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
    GanttDetails details = new GanttDetails();

    // 1) Persisted snapshot  2) Runtime inline  3) Connector
    if (embeddedTasks != null && !embeddedTasks.isEmpty()) {
      details.tasks.addAll(embeddedTasks);
      details.recomputeBounds();
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }
    if (inlineTasks != null && !inlineTasks.isEmpty()) {
      details.tasks.addAll(inlineTasks);
      details.recomputeBounds();
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }

    if (StringUtils.isBlank(sourceConnectorName)) {
      details.emptyMessage = "No input connector";
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }
    HConnector connector = dataContext.getConnector(sourceConnectorName);
    if (connector == null) {
      details.emptyMessage = "Connector not found: " + sourceConnectorName;
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }
    if (StringUtils.isBlank(taskColumn)) {
      details.emptyMessage = "Configure task column";
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }
    boolean durationOnly =
        StringUtils.isBlank(startColumn) && StringUtils.isNotBlank(durationColumn);
    if (!durationOnly && StringUtils.isBlank(startColumn)) {
      details.emptyMessage = "Configure task and start columns, or task and duration (ms) only";
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }
    if (!durationOnly
        && StringUtils.isBlank(endColumn)
        && StringUtils.isBlank(durationColumn)) {
      details.emptyMessage = "Configure end or duration column";
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }

    List<RowMetaAndData> rows = connector.retrieveRows(dataContext);
    if (rows == null || rows.isEmpty()) {
      details.emptyMessage = "No rows";
      results.addDataSet(component, DATA_GANTT_DETAILS, details);
      return;
    }

    long waterfallCursor = 0L;
    for (RowMetaAndData row : rows) {
      if (row == null || row.getRowMeta() == null) {
        continue;
      }
      try {
        String label = stringValue(row, taskColumn);
        if (StringUtils.isBlank(label)) {
          label = "(unnamed)";
        }
        long start;
        long end;
        if (durationOnly) {
          Double dur = numericValue(row, durationColumn);
          if (dur == null) {
            continue;
          }
          long ms = Math.max(0L, Math.round(dur));
          start = waterfallCursor;
          end = waterfallCursor + ms;
          waterfallCursor = end;
        } else {
          Double startNum = numericValue(row, startColumn);
          if (startNum == null) {
            continue;
          }
          start = Math.round(startNum);
          if (StringUtils.isNotBlank(endColumn)) {
            Double endVal = numericValue(row, endColumn);
            if (endVal == null) {
              continue;
            }
            end = Math.round(endVal);
          } else {
            Double dur = numericValue(row, durationColumn);
            if (dur == null) {
              continue;
            }
            end = start + Math.max(0L, Math.round(dur));
          }
          if (end < start) {
            long tmp = start;
            start = end;
            end = tmp;
          }
        }
        String group =
            StringUtils.isNotBlank(groupColumn) ? stringValue(row, groupColumn) : null;
        String colorKey =
            StringUtils.isNotBlank(colorKeyColumn) ? stringValue(row, colorKeyColumn) : label;
        details.tasks.add(new GanttTask(label, start, end, group, colorKey));
      } catch (HopValueException e) {
        throw new HException("Error reading Gantt row: " + e.getMessage(), e);
      }
    }

    // Duration-only keeps row order (waterfall). Start/end mode sorts by start then label.
    if (!durationOnly) {
      details.tasks.sort(
          (a, b) -> {
            int c = Long.compare(a.getStart(), b.getStart());
            if (c != 0) {
              return c;
            }
            return String.valueOf(a.getLabel()).compareToIgnoreCase(String.valueOf(b.getLabel()));
          });
    }
    details.recomputeBounds();
    if (details.isEmpty()) {
      details.emptyMessage = "No valid Gantt rows";
    }
    results.addDataSet(component, DATA_GANTT_DETAILS, details);
  }

  @Override
  public HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {
    GanttDetails details = (GanttDetails) results.getDataSet(component, DATA_GANTT_DETAILS);
    int n = details != null && details.tasks != null ? Math.max(1, details.tasks.size()) : 1;
    int rh = rowHeight > 0 ? rowHeight : 22;
    int titleH = showingTitle && StringUtils.isNotBlank(title) ? 28 : 0;
    int axisH = showingAxisTicks ? 22 : 8;
    int h = titleH + axisH + n * rh + 2 * Math.max(0, verticalMargin) + 16;
    return new HSize(480, Math.max(120, h));
  }

  @Override
  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {
    HComponent component = layoutResult.getComponent();
    String pluginId =
        component != null && component.getComponent() != null
            ? component.getComponent().getPluginId()
            : getPluginId();
    HGeometry geo = layoutResult.getGeometry();
    if (geo == null) {
      return;
    }
    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();
    setBackgroundBorderFont(gc, geo, renderContext);

    GanttDetails details = (GanttDetails) results.getDataSet(component, DATA_GANTT_DETAILS);
    if (details == null) {
      details = new GanttDetails();
      details.emptyMessage = "No Gantt data";
    }

    int x0 = geo.getX();
    int y0 = geo.getY();
    int w = Math.max(1, geo.getWidth());
    int h = Math.max(1, geo.getHeight());
    int hm = Math.max(0, horizontalMargin);
    int vm = Math.max(0, verticalMargin);

    Color ink = toAwt(lookupDefaultColor(renderContext), Color.DARK_GRAY);
    Color muted = new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 120);
    Color grid = new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 40);

    int cursorY = y0 + vm;
    Font baseFont = gc.getFont();
    if (baseFont == null) {
      baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      gc.setFont(baseFont);
    }

    if (showingTitle && StringUtils.isNotBlank(title)) {
      gc.setColor(ink);
      gc.setFont(baseFont.deriveFont(Font.BOLD, Math.max(12f, baseFont.getSize2D() + 1f)));
      int titleX = x0 + hm;
      int titleY = cursorY + 14;
      gc.drawString(title, titleX, titleY);
      int titleW = Math.max(40, gc.getFontMetrics().stringWidth(title));
      int titleH = Math.max(14, gc.getFontMetrics().getHeight());
      layoutResult
          .getRenderPage()
          .addDrawnItem(
              component.getName(),
              pluginId,
              layoutResult.getPartNumber(),
              DrawnItem.DrawnItemType.ComponentItem,
              DrawnItem.Category.Title.name(),
              0,
              0,
              new HGeometry(
                  (int) (offSet.getX() + titleX),
                  (int) (offSet.getY() + titleY - titleH),
                  titleW,
                  titleH),
              new DrawnContext(title));
      cursorY += 24;
      gc.setFont(baseFont);
    }

    int plotBottom = y0 + h - vm - (showingAxisTicks ? 18 : 4);
    int plotTop = cursorY;
    int plotH = Math.max(20, plotBottom - plotTop);

    int labelW = labelColumnWidth > 0 ? labelColumnWidth : Math.max(80, (int) (w * 0.28));
    labelW = Math.min(labelW, w - 2 * hm - 40);
    int plotLeft = x0 + hm + labelW + 6;
    int plotRight = x0 + w - hm;
    int plotW = Math.max(20, plotRight - plotLeft);

    if (details.isEmpty()) {
      gc.setColor(muted);
      String msg =
          StringUtils.isNotBlank(details.emptyMessage) ? details.emptyMessage : "No data";
      gc.drawString(msg, plotLeft, plotTop + 16);
      return;
    }

    int n = details.tasks.size();
    int preferredRh = rowHeight > 0 ? rowHeight : 22;
    int rh = Math.max(12, Math.min(preferredRh, plotH / Math.max(1, n)));
    // Center rows vertically when fewer than fit
    int usedH = n * rh;
    int rowStartY = plotTop + Math.max(0, (plotH - usedH) / 2);

    long minT = details.minStart;
    long span = details.span();
    if (span <= 0) {
      span = 1;
    }

    // Vertical grid / ticks
    if (showingAxisTicks) {
      gc.setColor(grid);
      gc.setStroke(new BasicStroke(1f));
      int tickCount = chooseTickCount(plotW);
      for (int i = 0; i <= tickCount; i++) {
        long t = minT + span * i / tickCount;
        int x = plotLeft + (int) Math.round(plotW * (double) (t - minT) / span);
        gc.drawLine(x, plotTop, x, plotBottom);
        gc.setColor(muted);
        String tickLabel = formatDuration(t - minT);
        gc.drawString(tickLabel, x - 8, plotBottom + 14);
        gc.setColor(grid);
      }
    }

    // Axis baseline
    gc.setColor(ink);
    gc.setStroke(new BasicStroke(1.2f));
    gc.drawLine(plotLeft, plotBottom, plotRight, plotBottom);
    gc.drawLine(plotLeft, plotTop, plotLeft, plotBottom);

    Font small = baseFont.deriveFont(Math.max(9f, baseFont.getSize2D() - 1f));
    gc.setFont(small);

    for (int i = 0; i < n; i++) {
      GanttTask task = details.tasks.get(i);
      int rowY = rowStartY + i * rh;
      int barH = Math.max(6, rh - 8);
      int barY = rowY + (rh - barH) / 2;

      // Label
      gc.setColor(ink);
      String label = task.getLabel() != null ? task.getLabel() : "";
      String drawLabel = truncateToWidth(gc, label, labelW - 4);
      gc.drawString(drawLabel, x0 + hm, rowY + rh / 2 + 4);

      // Bar
      long s = task.getStart();
      long e = task.getEnd();
      int bx = plotLeft + (int) Math.round(plotW * (double) (s - minT) / span);
      int be = plotLeft + (int) Math.round(plotW * (double) (e - minT) / span);
      int bw = Math.max(2, be - bx);

      String colorKey =
          StringUtils.isNotBlank(task.getColorKey()) ? task.getColorKey() : label;
      Color barColor = resolveBarColor(renderContext, colorKey, i);
      gc.setColor(barColor);
      gc.fillRoundRect(bx, barY, bw, barH, 3, 3);
      gc.setColor(ink);
      gc.setStroke(new BasicStroke(0.8f));
      gc.drawRoundRect(bx, barY, bw, barH, 3, 3);

      if (showingDurationLabels && bw > 28) {
        String dur = formatDuration(task.duration());
        gc.setColor(contrastInk(barColor));
        int tw = gc.getFontMetrics().stringWidth(dur);
        if (tw + 4 < bw) {
          gc.drawString(dur, bx + (bw - tw) / 2, barY + barH - 3);
        }
      }

      // Hit region for interactions (task label as click value)
      if (layoutResult.getRenderPage() != null && component != null) {
        List<HColumn> dims = new ArrayList<>();
        if (StringUtils.isNotBlank(taskColumn)) {
          dims.add(new HColumn(taskColumn.trim()));
        }
        layoutResult
            .getRenderPage()
            .addDrawnItem(
                component.getName(),
                pluginId,
                0,
                DrawnItem.DrawnItemType.ComponentItem,
                DrawnItem.Category.GanttBar.name(),
                i,
                0,
                new HGeometry(
                    (int) (offSet.getX() + bx),
                    (int) (offSet.getY() + barY),
                    bw,
                    barH),
                new DrawnContext(dims, label));
      }
    }

    gc.setFont(baseFont);
  }

  private Color resolveBarColor(IRenderContext renderContext, String key, int index)
      throws HException {
    try {
      if (renderContext != null && StringUtils.isNotBlank(key)) {
        org.hopper.presentation.theme.HTheme theme = renderContext.lookupTheme(themeName);
        String themeKey = theme != null ? theme.getName() : themeName;
        HColorRGB rgb = renderContext.getStableColor(themeKey, key);
        if (rgb != null) {
          return toAwt(rgb, null);
        }
      }
    } catch (Exception ignored) {
      // fall through to default palette
    }
    // Fallback palette
    float hue = (index * 0.17f) % 1f;
    return Color.getHSBColor(hue, 0.55f, 0.78f);
  }

  private static Color contrastInk(Color bg) {
    if (bg == null) {
      return Color.BLACK;
    }
    double lum = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
    return lum > 0.55 ? Color.DARK_GRAY : Color.WHITE;
  }

  private static Color toAwt(HColorRGB rgb, Color fallback) {
    if (rgb == null) {
      return fallback != null ? fallback : Color.DARK_GRAY;
    }
    return new Color(
        clampByte(rgb.getR()), clampByte(rgb.getG()), clampByte(rgb.getB()));
  }

  private static int clampByte(int v) {
    return Math.max(0, Math.min(255, v));
  }

  private static int chooseTickCount(int plotW) {
    if (plotW < 120) {
      return 2;
    }
    if (plotW < 240) {
      return 4;
    }
    if (plotW < 480) {
      return 5;
    }
    return 8;
  }

  /** Format a duration in ms as a short label. */
  static String formatDuration(double ms) {
    if (ms < 0) {
      ms = 0;
    }
    if (ms < 1000) {
      return Math.round(ms) + "ms";
    }
    if (ms < 10_000) {
      return String.format("%.1fs", ms / 1000d);
    }
    if (ms < 60_000) {
      return Math.round(ms / 1000d) + "s";
    }
    double min = ms / 60_000d;
    if (min < 10) {
      return String.format("%.1fm", min);
    }
    return Math.round(min) + "m";
  }

  private static String truncateToWidth(SVGGraphics2D gc, String text, int maxW) {
    if (text == null) {
      return "";
    }
    if (gc.getFontMetrics().stringWidth(text) <= maxW) {
      return text;
    }
    String ell = "…";
    int ellW = gc.getFontMetrics().stringWidth(ell);
    if (ellW >= maxW) {
      return ell;
    }
    StringBuilder sb = new StringBuilder(text);
    while (sb.length() > 0
        && gc.getFontMetrics().stringWidth(sb.toString()) + ellW > maxW) {
      sb.setLength(sb.length() - 1);
    }
    return sb + ell;
  }

  private static String stringValue(RowMetaAndData row, String column) throws HopValueException {
    if (row == null || StringUtils.isBlank(column)) {
      return null;
    }
    int idx = row.getRowMeta().indexOfValue(column);
    if (idx < 0) {
      return null;
    }
    IValueMeta vm = row.getRowMeta().getValueMeta(idx);
    Object data = row.getData()[idx];
    if (data == null) {
      return null;
    }
    return vm.getString(data);
  }

  private static Double numericValue(RowMetaAndData row, String column) throws HopValueException {
    if (row == null || StringUtils.isBlank(column)) {
      return null;
    }
    int idx = row.getRowMeta().indexOfValue(column);
    if (idx < 0) {
      return null;
    }
    IValueMeta vm = row.getRowMeta().getValueMeta(idx);
    Object data = row.getData()[idx];
    if (data == null) {
      return null;
    }
    if (vm.isNumeric()) {
      return vm.getNumber(data);
    }
    // Try parse string / date millis
    if (vm.isDate()) {
      java.util.Date d = vm.getDate(data);
      return d != null ? (double) d.getTime() : null;
    }
    String s = vm.getString(data);
    if (StringUtils.isBlank(s)) {
      return null;
    }
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
