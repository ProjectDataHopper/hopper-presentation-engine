package org.hopper.presentation.component.types.pictorial;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
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
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

/**
 * Real/pictorial chart: like a bar chart with step images (or clipped fill layers) per category.
 *
 * <p>With a {@link #categoryColumn}, one image cell is drawn per connector row. Value labels sit
 * above images; optional category labels below. Images scale to fit the component geometry.
 */
@JsonDeserialize(as = HPictorialChartComponent.class)
@HComponentPlugin(
    id = "HPictorialChartComponent",
    name = "Pictorial Chart",
    description =
        "Bar-style pictorial chart: step image sequence or clipped layers per category (no axis)",
    image = "ui/images/components/pictorial-chart.svg")
@Getter
@Setter
public class HPictorialChartComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_PICTORIAL_DETAILS = "Pictorial Details";

  public enum RenderMode {
    STEP_IMAGES,
    CLIPPED_LAYERS
  }

  public enum StepQuantization {
    NEAREST,
    FLOOR,
    CEIL
  }

  public enum ClipDirection {
    BOTTOM_TO_TOP,
    LEFT_TO_RIGHT,
    TOP_TO_BOTTOM,
    RIGHT_TO_LEFT
  }

  @HWidgetElement(
      order = "09900-seriesName",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.METADATA,
      metadataKey = "pictorial-series",
      label = "Pictorial series",
      toolTip =
          "Reusable pictorial series metadata (step image sequences or empty/fill layer pairs)")
  @HopMetadataProperty
  private String seriesName;

  @HWidgetElement(
      order = "10000-renderMode",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Render mode",
      toolTip =
          "STEP_IMAGES for discrete multi-image mapping or CLIPPED_LAYERS for dynamic fill clipping")
  @HopMetadataProperty
  private RenderMode renderMode = RenderMode.STEP_IMAGES;

  @HWidgetElement(
      order = "10050-categoryColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      label = "Category column (horizontal)",
      toolTip =
          "Connector field for each pictorial cell (like a bar chart dimension). "
              + "Leave empty for a single value from the first row.")
  @HopMetadataProperty
  private String categoryColumn;

  @HWidgetElement(
      order = "10100-valueColumn",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.CONNECTOR_COLUMNS,
      label = "Value column",
      toolTip = "Connector field providing the metric value (e.g. pct_of_target 0–100)")
  @HopMetadataProperty
  private String valueColumn;

  @HWidgetElement(
      order = "10200-domainMin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Domain minimum",
      toolTip = "Minimum bound corresponding to 0% fill (default 0.0)")
  @HopMetadataProperty
  private String domainMin = "0.0";

  @HWidgetElement(
      order = "10300-domainMax",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Domain maximum",
      toolTip = "Maximum bound corresponding to 100% fill (default 100.0)")
  @HopMetadataProperty
  private String domainMax = "100.0";

  @HWidgetElement(
      order = "10400-stepQuantization",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Step quantization",
      toolTip = "Quantization strategy for STEP_IMAGES mode (NEAREST, FLOOR, CEIL)")
  @HopMetadataProperty
  private StepQuantization stepQuantization = StepQuantization.NEAREST;

  @HopMetadataProperty
  private Map<String, String> imageMap = new LinkedHashMap<>();

  @HWidgetElement(
      order = "10500-backgroundImage",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.FILENAME,
      label = "Background image (empty container)",
      toolTip = "Path to the empty/background container image for CLIPPED_LAYERS mode")
  @HopMetadataProperty
  private String backgroundImage;

  @HWidgetElement(
      order = "10600-fillImage",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.FILENAME,
      label = "Fill image (full container)",
      toolTip = "Path to the full fill container image for CLIPPED_LAYERS mode")
  @HopMetadataProperty
  private String fillImage;

  @HWidgetElement(
      order = "10700-clipDirection",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Clip direction",
      toolTip = "Fill direction for CLIPPED_LAYERS mode")
  @HopMetadataProperty
  private ClipDirection clipDirection = ClipDirection.BOTTOM_TO_TOP;

  @HWidgetElement(
      order = "10800-showValueLabel",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show value label?",
      toolTip = "Draw the metric value above each image")
  @HopMetadataProperty
  private boolean showValueLabel = true;

  @HWidgetElement(
      order = "10850-showCategoryLabel",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show category label?",
      toolTip = "Draw the category name below each image")
  @HopMetadataProperty
  private boolean showCategoryLabel = true;

  @HWidgetElement(
      order = "10900-labelFormat",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Value label format",
      toolTip = "Format string for the metric value (e.g. %.0f%%)")
  @HopMetadataProperty
  private String labelFormat = "%.0f%%";

  @HWidgetElement(
      order = "10950-itemGap",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Item gap (px)",
      toolTip = "Horizontal gap between pictorial cells")
  @HopMetadataProperty
  private String itemGap = "8";

  @HWidgetElement(
      order = "11000-scalePercent",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Scale percent",
      toolTip = "Additional scale cap after fit-to-bounds (100 = fit fully into cell)")
  @HopMetadataProperty
  private String scalePercent = "100.0";

  public HPictorialChartComponent() {
    super("HPictorialChartComponent");
  }

  public HPictorialChartComponent(HPictorialChartComponent c) {
    super("HPictorialChartComponent", c);
    this.seriesName = c.seriesName;
    this.renderMode = c.renderMode;
    this.categoryColumn = c.categoryColumn;
    this.valueColumn = c.valueColumn;
    this.domainMin = c.domainMin;
    this.domainMax = c.domainMax;
    this.stepQuantization = c.stepQuantization;
    if (c.imageMap != null) {
      this.imageMap = new LinkedHashMap<>(c.imageMap);
    }
    this.backgroundImage = c.backgroundImage;
    this.fillImage = c.fillImage;
    this.clipDirection = c.clipDirection;
    this.showValueLabel = c.showValueLabel;
    this.showCategoryLabel = c.showCategoryLabel;
    this.labelFormat = c.labelFormat;
    this.itemGap = c.itemGap;
    this.scalePercent = c.scalePercent;
  }

  @Override
  public HPictorialChartComponent clone() {
    return new HPictorialChartComponent(this);
  }

  /** One category cell in a multi-item pictorial chart. */
  public static class PictorialItem {
    public String category = "";
    public double rawValue;
    public double percentage;
    public BufferedImage primaryImage;
    public BufferedImage fillLayerImage;
  }

  public static class PictorialDetails {
    public List<PictorialItem> items = new ArrayList<>();
    public RenderMode effectiveMode;
    public ClipDirection effectiveClipDirection;
    /** Natural pixel size of source images (max over items). */
    public int naturalImageW = 100;
    public int naturalImageH = 100;
    /** Preferred size when layout does not pin edges. */
    public HSize imageSize;
    // Backward-compat fields used by older tests / single-item callers
    public double rawValue;
    public double percentage;
    public double scaleFactor = 1.0;
    public BufferedImage primaryImage;
    public BufferedImage fillLayerImage;
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

    double minVal = Const.toDouble(domainMin, 0.0);
    double maxVal = Const.toDouble(domainMax, 100.0);
    if (maxVal <= minVal) {
      maxVal = minVal + 100.0;
    }

    HPictorialSeries series = loadSeries(dataContext, renderContext);

    RenderMode mode =
        series != null && series.getRenderMode() != null
            ? series.getRenderMode()
            : (renderMode != null ? renderMode : RenderMode.STEP_IMAGES);

    ClipDirection effectiveClip =
        series != null && series.getClipDirection() != null
            ? series.getClipDirection()
            : (clipDirection != null ? clipDirection : ClipDirection.BOTTOM_TO_TOP);

    IVariables variables = dataContext != null ? dataContext.getVariables() : null;
    Map<String, BufferedImage> imageCache = new HashMap<>();

    List<RowMetaAndData> rows = retrieveRows(dataContext);
    List<PictorialItem> items = new ArrayList<>();

    IRowMeta sampleMeta =
        !rows.isEmpty() && rows.get(0).getRowMeta() != null ? rows.get(0).getRowMeta() : null;
    String effectiveCategory = resolveCategoryColumn(sampleMeta);
    String effectiveValue = resolveValueColumn(sampleMeta);

    // Multi-item (bar-style) when we have a category field and more than one row.
    boolean multi = StringUtils.isNotBlank(effectiveCategory) && rows.size() > 1;
    if (!multi && StringUtils.isNotBlank(effectiveCategory) && rows.size() == 1) {
      multi = true; // still one cell, but use category label
    }

    if (multi) {
      for (RowMetaAndData row : rows) {
        PictorialItem item =
            buildItem(
                row,
                series,
                mode,
                minVal,
                maxVal,
                variables,
                imageCache,
                effectiveCategory,
                effectiveValue);
        if (item != null) {
          items.add(item);
        }
      }
    } else if (!rows.isEmpty()) {
      PictorialItem item =
          buildItem(
              rows.get(0),
              series,
              mode,
              minVal,
              maxVal,
              variables,
              imageCache,
              effectiveCategory,
              effectiveValue);
      if (item != null) {
        items.add(item);
      }
    }

    if (items.isEmpty()) {
      // No data: still produce an empty details object so layout does not NPE
      PictorialDetails empty = new PictorialDetails();
      empty.effectiveMode = mode;
      empty.effectiveClipDirection = effectiveClip;
      empty.imageSize = new HSize(120, 160);
      results.addDataSet(component, DATA_PICTORIAL_DETAILS, empty);
      return;
    }

    int natW = 1;
    int natH = 1;
    for (PictorialItem it : items) {
      if (it.primaryImage != null) {
        natW = Math.max(natW, it.primaryImage.getWidth());
        natH = Math.max(natH, it.primaryImage.getHeight());
      }
      if (it.fillLayerImage != null) {
        natW = Math.max(natW, it.fillLayerImage.getWidth());
        natH = Math.max(natH, it.fillLayerImage.getHeight());
      }
    }

    double scaleCap = Const.toDouble(scalePercent, 100.0) / 100.0;
    int gap = Math.max(0, (int) Const.toDouble(itemGap, 8));
    int n = items.size();
    int labelBand = (showValueLabel ? 18 : 0) + (showCategoryLabel ? 16 : 0) + 8;
    // Cap natural contribution so continuous layout does not grow huge from large AI assets
    int cellW = Math.min(160, Math.max(48, (int) (natW * scaleCap)));
    int cellH = Math.min(220, Math.max(64, (int) (natH * scaleCap)));
    int preferredW = n * cellW + Math.max(0, n - 1) * gap;
    int preferredH = cellH + labelBand;

    PictorialDetails details = new PictorialDetails();
    details.items = items;
    details.effectiveMode = mode;
    details.effectiveClipDirection = effectiveClip;
    details.naturalImageW = natW;
    details.naturalImageH = natH;
    details.imageSize = new HSize(preferredW, preferredH);
    details.scaleFactor = scaleCap;
    // Compat: first item
    PictorialItem first = items.get(0);
    details.rawValue = first.rawValue;
    details.percentage = first.percentage;
    details.primaryImage = first.primaryImage;
    details.fillLayerImage = first.fillLayerImage;

    results.addDataSet(component, DATA_PICTORIAL_DETAILS, details);
  }

  private PictorialItem buildItem(
      RowMetaAndData row,
      HPictorialSeries series,
      RenderMode mode,
      double minVal,
      double maxVal,
      IVariables variables,
      Map<String, BufferedImage> imageCache,
      String effectiveCategory,
      String effectiveValue)
      throws HException {
    if (row == null || row.getRowMeta() == null) {
      return null;
    }
    IRowMeta meta = row.getRowMeta();
    Object[] data = row.getData();

    PictorialItem item = new PictorialItem();
    if (StringUtils.isNotBlank(effectiveCategory)) {
      int catIdx = meta.indexOfValue(effectiveCategory);
      if (catIdx >= 0) {
        try {
          item.category = Const.NVL(meta.getString(data, catIdx), "");
        } catch (Exception e) {
          item.category = "";
        }
      }
    }

    item.rawValue = readNumber(meta, data, effectiveValue);
    // Do not clamp to 0–100: negative = under-target (broken glass), >100 = over-target (overflow)
    item.percentage = ((item.rawValue - minVal) / (maxVal - minVal)) * 100.0;

    if (mode == RenderMode.STEP_IMAGES) {
      String path =
          series != null
              ? series.getImageForPercentage(item.percentage)
              : resolveStepImagePath(item.percentage);
      if (StringUtils.isEmpty(path)) {
        throw new HException(
            "No step image mapping resolved for percentage " + item.percentage);
      }
      item.primaryImage = loadImageCached(path, variables, imageCache);
    } else {
      String bgPath = series != null ? series.getBackgroundImage() : backgroundImage;
      String fillPath = series != null ? series.getFillImage() : fillImage;
      if (StringUtils.isNotEmpty(bgPath)) {
        item.primaryImage = loadImageCached(bgPath, variables, imageCache);
      }
      if (StringUtils.isNotEmpty(fillPath)) {
        item.fillLayerImage = loadImageCached(fillPath, variables, imageCache);
      }
      if (item.primaryImage == null && item.fillLayerImage == null) {
        throw new HException(
            "Neither background nor fill image specified for CLIPPED_LAYERS mode");
      }
      if (item.primaryImage == null) {
        item.primaryImage = item.fillLayerImage;
      }
    }
    return item;
  }

  private HPictorialSeries loadSeries(IDataContext dataContext, IRenderContext renderContext)
      throws HException {
    if (StringUtils.isBlank(seriesName)) {
      return null;
    }
    org.apache.hop.metadata.api.IHopMetadataProvider provider = null;
    if (dataContext != null) {
      provider = dataContext.getMetadataProvider();
    } else if (renderContext instanceof org.hopper.render.context.PresentationRenderContext) {
      provider =
          ((org.hopper.render.context.PresentationRenderContext) renderContext)
              .getMetadataProvider();
    }
    if (provider == null) {
      throw new HException(
          "Pictorial series '" + seriesName + "' specified but no metadata provider available");
    }
    try {
      org.apache.hop.metadata.api.IHopMetadataSerializer<HPictorialSeries> serializer =
          provider.getSerializer(HPictorialSeries.class);
      if (serializer != null && serializer.exists(seriesName)) {
        return serializer.load(seriesName);
      }
    } catch (Exception e) {
      throw new HException(
          "Unable to load pictorial series '" + seriesName + "': " + e.getMessage(), e);
    }
    throw new HException("Pictorial series not found: '" + seriesName + "'");
  }

  private List<RowMetaAndData> retrieveRows(IDataContext dataContext) throws HException {
    if (dataContext == null || StringUtils.isEmpty(sourceConnectorName)) {
      return List.of();
    }
    HConnector connector = dataContext.getConnector(sourceConnectorName);
    if (connector == null) {
      return List.of();
    }
    List<RowMetaAndData> rows = connector.retrieveRows(dataContext);
    return rows != null ? rows : List.of();
  }

  /**
   * Resolve category column: configured name, else first String field (for multi-row bar layout).
   */
  String resolveCategoryColumn(IRowMeta meta) {
    if (StringUtils.isNotBlank(categoryColumn)) {
      return categoryColumn.trim();
    }
    if (meta == null) {
      return null;
    }
    for (int i = 0; i < meta.size(); i++) {
      try {
        if (meta.getValueMeta(i).isString()) {
          return meta.getValueMeta(i).getName();
        }
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  /**
   * Resolve value column: configured name, else a percentage-like numeric field, never prefer
   * {@code year} when better options exist.
   */
  String resolveValueColumn(IRowMeta meta) {
    if (StringUtils.isNotBlank(valueColumn)) {
      return valueColumn.trim();
    }
    if (meta == null || meta.isEmpty()) {
      return null;
    }
    String[] preferred = {
      "pct_of_target", "percentage", "pct", "percent", "value", "metric", "amount"
    };
    for (String name : preferred) {
      int idx = meta.indexOfValue(name);
      if (idx >= 0) {
        return name;
      }
    }
    // Case-insensitive contains "pct" / "percent"
    for (int i = 0; i < meta.size(); i++) {
      try {
        String n = meta.getValueMeta(i).getName();
        if (n != null) {
          String lower = n.toLowerCase();
          if (lower.contains("pct") || lower.contains("percent")) {
            return n;
          }
        }
      } catch (Exception ignored) {
      }
    }
    // First numeric that is not "year"
    for (int i = 0; i < meta.size(); i++) {
      try {
        var vm = meta.getValueMeta(i);
        String n = vm.getName();
        if (n != null && n.equalsIgnoreCase("year")) {
          continue;
        }
        if (vm.isNumeric()) {
          return n;
        }
      } catch (Exception ignored) {
      }
    }
    // Last resort: first numeric including year
    for (int i = 0; i < meta.size(); i++) {
      try {
        if (meta.getValueMeta(i).isNumeric()) {
          return meta.getValueMeta(i).getName();
        }
      } catch (Exception ignored) {
      }
    }
    return meta.getValueMeta(0).getName();
  }

  private static double readNumber(IRowMeta meta, Object[] data, String column) {
    if (meta == null || meta.isEmpty()) {
      return 0.0;
    }
    int valIndex = -1;
    if (StringUtils.isNotEmpty(column)) {
      valIndex = meta.indexOfValue(column);
    }
    if (valIndex < 0) {
      // Prefer last numeric field over column 0 (often a year / id)
      for (int i = meta.size() - 1; i >= 0; i--) {
        try {
          if (meta.getValueMeta(i).isNumeric()) {
            valIndex = i;
            break;
          }
        } catch (Exception ignored) {
        }
      }
    }
    if (valIndex < 0) {
      valIndex = 0;
    }
    try {
      Double val = meta.getNumber(data, valIndex);
      return val != null ? val : 0.0;
    } catch (Exception e) {
      return 0.0;
    }
  }

  private BufferedImage loadImageCached(
      String path, IVariables variables, Map<String, BufferedImage> cache) throws HException {
    String key = resolveImagePath(path, variables);
    if (cache.containsKey(key)) {
      return cache.get(key);
    }
    BufferedImage img = loadImage(path, variables);
    cache.put(key, img);
    return img;
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
    PictorialDetails details =
        (PictorialDetails) results.getDataSet(component, DATA_PICTORIAL_DETAILS);
    if (details == null || details.imageSize == null) {
      return new HSize(200, 200);
    }
    return details.imageSize;
  }

  @Override
  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offset)
      throws HException {

    HGeometry geom = layoutResult.getGeometry();
    HComponent component = layoutResult.getComponent();
    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();

    setBackgroundBorderFont(gc, geom, renderContext);

    PictorialDetails details =
        (PictorialDetails) results.getDataSet(component, DATA_PICTORIAL_DETAILS);
    if (details == null || details.items == null || details.items.isEmpty()) {
      return;
    }

    List<PictorialItem> items = details.items;
    int n = items.size();
    int gap = Math.max(0, (int) Const.toDouble(itemGap, 8));
    double scaleCap = Const.toDouble(scalePercent, 100.0) / 100.0;

    Font font = gc.getFont();
    if (font == null) {
      font = new Font("SansSerif", Font.PLAIN, 12);
      gc.setFont(font);
    }
    FontRenderContext frc = gc.getFontRenderContext();

    int valueBand = 0;
    int categoryBand = 0;
    if (showValueLabel) {
      valueBand = Math.max(14, font.getSize() + 6);
    }
    if (showCategoryLabel) {
      categoryBand = Math.max(14, font.getSize() + 4);
    }

    int availW = Math.max(1, geom.getWidth());
    int availH = Math.max(1, geom.getHeight());
    int imgAreaH = Math.max(1, availH - valueBand - categoryBand);
    int totalGap = Math.max(0, n - 1) * gap;
    double cellW = (availW - totalGap) / (double) n;

    int natW = Math.max(1, details.naturalImageW);
    int natH = Math.max(1, details.naturalImageH);

    double fit =
        Math.min(cellW / natW, (double) imgAreaH / natH) * scaleCap;
    // Never upscale beyond natural size more than scaleCap allows; allow downscale freely
    if (fit > scaleCap) {
      fit = scaleCap;
    }
    int drawW = Math.max(1, (int) Math.round(natW * fit));
    int drawH = Math.max(1, (int) Math.round(natH * fit));

    enableColor(gc, lookupDefaultColor(renderContext));
    enableFont(gc, lookupDefaultFont(renderContext));

    ClipDirection dir =
        details.effectiveClipDirection != null
            ? details.effectiveClipDirection
            : ClipDirection.BOTTOM_TO_TOP;

    for (int i = 0; i < n; i++) {
      PictorialItem item = items.get(i);
      double cellLeft = geom.getX() + i * (cellW + gap);
      double imgX = cellLeft + (cellW - drawW) / 2.0;
      double imgY = geom.getY() + valueBand + (imgAreaH - drawH) / 2.0;

      // Value label above
      if (showValueLabel) {
        String text = formatValue(item.rawValue);
        TextLayout tl = new TextLayout(text, gc.getFont(), frc);
        Rectangle2D tb = tl.getBounds();
        float tx = (float) (cellLeft + (cellW - tb.getWidth()) / 2.0 - tb.getX());
        float ty = (float) (geom.getY() + valueBand - 4);
        tl.draw(gc, tx, ty);
      }

      // Image — scale via AffineTransform for Batik SVG embedding reliability
      if (item.primaryImage != null) {
        double sx = drawW / (double) item.primaryImage.getWidth();
        double sy = drawH / (double) item.primaryImage.getHeight();
        AffineTransform oldTx = gc.getTransform();
        gc.translate(imgX, imgY);
        gc.scale(sx, sy);
        if (details.effectiveMode == RenderMode.CLIPPED_LAYERS) {
          gc.drawImage(item.primaryImage, 0, 0, null);
          if (item.fillLayerImage != null && item.percentage > 0.0) {
            Shape oldClip = gc.getClip();
            Rectangle2D clipRect =
                computeClipBounds(
                    0,
                    0,
                    item.primaryImage.getWidth(),
                    item.primaryImage.getHeight(),
                    item.percentage,
                    dir);
            gc.clip(clipRect);
            gc.drawImage(item.fillLayerImage, 0, 0, null);
            gc.setClip(oldClip);
          }
        } else {
          gc.drawImage(item.primaryImage, 0, 0, null);
        }
        gc.setTransform(oldTx);
      }

      // Category label below
      if (showCategoryLabel && StringUtils.isNotBlank(item.category)) {
        TextLayout tl = new TextLayout(item.category, gc.getFont(), frc);
        Rectangle2D tb = tl.getBounds();
        float tx = (float) (cellLeft + (cellW - tb.getWidth()) / 2.0 - tb.getX());
        float ty = (float) (geom.getY() + valueBand + imgAreaH + categoryBand - 2);
        tl.draw(gc, tx, ty);
      }
    }

    if (isBorder()) {
      enableColor(gc, lookupBorderColor(renderContext));
      gc.drawRect(geom.getX(), geom.getY(), geom.getWidth(), geom.getHeight());
    }
  }

  private String formatValue(double raw) {
    try {
      return String.format(StringUtils.defaultIfEmpty(labelFormat, "%.0f%%"), raw);
    } catch (Exception e) {
      return String.valueOf(raw);
    }
  }

  private Rectangle2D computeClipBounds(
      int x, int y, int w, int h, double pct, ClipDirection direction) {
    double factor = pct / 100.0;
    ClipDirection d = direction != null ? direction : ClipDirection.BOTTOM_TO_TOP;

    switch (d) {
      case LEFT_TO_RIGHT:
        return new Rectangle2D.Double(x, y, w * factor, h);
      case RIGHT_TO_LEFT:
        double rw = w * factor;
        return new Rectangle2D.Double(x + w - rw, y, rw, h);
      case TOP_TO_BOTTOM:
        return new Rectangle2D.Double(x, y, w, h * factor);
      case BOTTOM_TO_TOP:
      default:
        double fh = h * factor;
        return new Rectangle2D.Double(x, y + h - fh, w, fh);
    }
  }

  private String resolveStepImagePath(double pct) {
    return HPictorialSeries.resolveStepPath(imageMap, pct, stepQuantization);
  }

  /**
   * Load an image from classpath, Hop VFS, or the local filesystem.
   *
   * <p>Paths may include Hop variables (e.g. {@code ${HOPPER_METADATA_PATH}/assets/...}). Legacy
   * admin HTTP asset URLs ({@code /hopper/api/assets/...}) are mapped back to the metadata assets
   * folder so series JSON generated before the path-contract fix still renders.
   */
  BufferedImage loadImage(String path, IVariables variables) throws HException {
    if (StringUtils.isEmpty(path)) {
      return null;
    }

    String resolved = resolveImagePath(path, variables);

    try {
      URL resource = this.getClass().getClassLoader().getResource(resolved);
      if (resource != null) {
        return ImageIO.read(resource);
      }

      if (HopVfs.fileExists(resolved)) {
        try (InputStream is = HopVfs.getInputStream(resolved)) {
          return ImageIO.read(is);
        }
      }

      File file = new File(resolved);
      if (file.exists()) {
        return ImageIO.read(file);
      }

      throw new HException(
          "Unable to find or load image resource '" + path + "' (resolved: '" + resolved + "')");
    } catch (HException he) {
      throw he;
    } catch (Exception e) {
      throw new HException(
          "Error loading image resource '" + path + "' (resolved: '" + resolved + "')", e);
    }
  }

  /**
   * Resolve variables and normalize asset paths used by pictorial series / component config.
   *
   * <p>Package-visible for unit tests.
   */
  static String resolveImagePath(String path, IVariables variables) {
    if (StringUtils.isEmpty(path)) {
      return path;
    }

    String resolved = path.trim();
    if (variables != null) {
      resolved = variables.resolve(resolved);
    } else {
      resolved = resolveSystemVariables(resolved);
    }

    String assetPrefix = "/hopper/api/assets/";
    if (resolved.startsWith(assetPrefix)) {
      String relative = resolved.substring(assetPrefix.length());
      String metadataRoot = metadataRootPath(variables);
      if (StringUtils.isNotBlank(metadataRoot)) {
        while (metadataRoot.endsWith("/") || metadataRoot.endsWith("\\")) {
          metadataRoot = metadataRoot.substring(0, metadataRoot.length() - 1);
        }
        resolved = metadataRoot + "/assets/" + relative;
      }
    }

    return resolved;
  }

  private static String resolveSystemVariables(String path) {
    if (path == null || !path.contains("${")) {
      return path;
    }
    String result = path;
    String meta = firstEnvOrProp("HOPPER_METADATA_PATH");
    if (StringUtils.isNotBlank(meta)) {
      result = result.replace("${HOPPER_METADATA_PATH}", meta);
    }
    String data = firstEnvOrProp("HOPPER_DATA_PATH");
    if (StringUtils.isNotBlank(data)) {
      result = result.replace("${HOPPER_DATA_PATH}", data);
    }
    return result;
  }

  private static String metadataRootPath(IVariables variables) {
    if (variables != null) {
      String v = variables.getVariable("HOPPER_METADATA_PATH");
      if (StringUtils.isNotBlank(v)) {
        return v;
      }
    }
    return firstEnvOrProp("HOPPER_METADATA_PATH");
  }

  private static String firstEnvOrProp(String key) {
    String v = System.getenv(key);
    if (StringUtils.isBlank(v)) {
      v = System.getProperty(key, "");
    }
    return StringUtils.isNotBlank(v) ? v.trim() : "";
  }
}
