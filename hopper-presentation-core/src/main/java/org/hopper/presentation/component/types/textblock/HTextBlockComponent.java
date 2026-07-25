package org.hopper.presentation.component.types.textblock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.awt.FontMetrics;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HGeometry;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.interaction.HInteractionLocationOption;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

/**
 * Multi-line text block: hard newlines, optional soft word-wrap, dynamic height, optional
 * pagination by whole lines.
 *
 * <p>Final wrap width is taken from geometry (left+right attachments) or {@link #maxWidth}. Soft
 * wrap is recomputed in {@link #doLayout} after geometry is known.
 */
@JsonDeserialize(as = HTextBlockComponent.class)
@HComponentPlugin(
    id = "HTextBlockComponent",
    name = "Text block",
    description = "Multi-line text with word wrap and optional pagination",
    image = "ui/images/components/text-block.svg")
@Getter
@Setter
public class HTextBlockComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_TEXT_BLOCK = "TextBlockDetails";
  private static final String DATA_START_LINE = "DATA_START_LINE";
  private static final String DATA_END_LINE = "DATA_END_LINE";

  /** UI-only: static / variable text; hide inherited input connector for now. */
  @HWidgetElement(
      id = "sourceConnectorName",
      type = HWidgetType.NONE,
      parentId = HGuiFormConstants.PARENT_BASE,
      ignored = true)
  @JsonIgnore
  private transient boolean hideSourceConnectorName;

  @HWidgetElement(
      order = "10000-text",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.MULTI_LINE_TEXT,
      multiLineTextHeight = 8,
      label = "Text",
      toolTip = "Multi-line text (variables are resolved at layout time)")
  @HopMetadataProperty
  private String text;

  @HWidgetElement(
      order = "10100-wrap",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Word wrap?")
  @HopMetadataProperty
  private boolean wrap;

  @HWidgetElement(
      order = "10200-maxWidth",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Max width",
      toolTip = "Optional wrap width when left+right attachments do not fix the width (0 = unset)")
  @HopMetadataProperty
  private int maxWidth;

  @HWidgetElement(
      order = "10300-horizontalAlignment",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Horizontal alignment")
  @HopMetadataProperty
  private HHorizontalAlignment horizontalAlignment;

  @HWidgetElement(
      order = "10400-verticalAlignment",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Vertical alignment")
  @HopMetadataProperty
  private HVerticalAlignment verticalAlignment;

  @HWidgetElement(
      order = "10500-lineSpacing",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Line spacing",
      toolTip = "Multiplier on font height (default 1.0)")
  @HopMetadataProperty
  private String lineSpacing;

  @HWidgetElement(
      order = "10600-horizontalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal margin")
  @HopMetadataProperty
  private int horizontalMargin;

  @HWidgetElement(
      order = "10700-verticalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical margin")
  @HopMetadataProperty
  private int verticalMargin;

  @HWidgetElement(
      order = "10800-paginate",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Paginate?",
      toolTip = "When content exceeds remaining page height, continue on following pages by whole lines")
  @HopMetadataProperty
  private boolean paginate;

  public HTextBlockComponent() {
    super("HTextBlockComponent");
    wrap = true;
    horizontalAlignment = HHorizontalAlignment.LEFT;
    verticalAlignment = HVerticalAlignment.TOP;
    lineSpacing = "1.0";
    horizontalMargin = 0;
    verticalMargin = 0;
    maxWidth = 0;
    paginate = false;
  }

  public HTextBlockComponent(HTextBlockComponent c) {
    super("HTextBlockComponent", c);
    this.text = c.text;
    this.wrap = c.wrap;
    this.maxWidth = c.maxWidth;
    this.horizontalAlignment = c.horizontalAlignment;
    this.verticalAlignment = c.verticalAlignment;
    this.lineSpacing = c.lineSpacing;
    this.horizontalMargin = c.horizontalMargin;
    this.verticalMargin = c.verticalMargin;
    this.paginate = c.paginate;
  }

  public HTextBlockComponent(String text) {
    this();
    this.text = text;
  }

  @Override
  public HTextBlockComponent clone() {
    return new HTextBlockComponent(this);
  }

  @Override
  public List<HInteractionLocationOption> getPossibleInteractionLocations() {
    return List.of(
        HInteractionLocationOption.item("text", "Text block content", DrawnItem.Category.Text));
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
    HRenderPage currentRenderPage = results.getCurrentRenderPage(page);
    HopSvgGraphics2D gc = currentRenderPage.getGc();
    enableFont(gc, lookupDefaultFont(renderContext));

    String resolved =
        dataContext.getVariables().resolve(text == null ? "" : text);

    TextBlockDetails details = new TextBlockDetails();
    details.setText(resolved);

    int provisionalWidth = resolveProvisionalWrapWidth(component);
    details.setWrapWidth(provisionalWidth);
    details.setLayout(layoutText(gc, resolved, provisionalWidth));

    results.addDataSet(component, DATA_TEXT_BLOCK, details);
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
    TextBlockDetails details = (TextBlockDetails) results.getDataSet(component, DATA_TEXT_BLOCK);
    if (details == null || details.getLayout() == null) {
      return new HSize(0, 0);
    }
    HTextLayout.Result layout = details.getLayout();
    return new HSize(layout.getWidth(), layout.getHeight());
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

    TextBlockDetails details = (TextBlockDetails) results.getDataSet(component, DATA_TEXT_BLOCK);
    if (details == null) {
      details = new TextBlockDetails();
      details.setText("");
      results.addDataSet(component, DATA_TEXT_BLOCK, details);
    }

    boolean sequentialBelow = isSequentialBelowLayout(component.getLayout());
    HRenderPage renderPage =
        sequentialBelow ? results.getCurrentRenderPage(page) : results.getFirstRenderPage(page);

    HGeometry expectedGeometry =
        getExpectedGeometry(presentation, page, component, dataContext, renderContext, results);

    // Re-wrap to final geometry width when wrapping is enabled
    SVGGraphics2D gc = renderPage.getGc();
    enableFont(gc, lookupDefaultFont(renderContext));

    int wrapWidth = expectedGeometry.getWidth();
    if (wrapWidth <= 0 && maxWidth > 0) {
      wrapWidth = maxWidth;
    }
    if (wrapWidth <= 0) {
      wrapWidth = Integer.MAX_VALUE;
    }

    HTextLayout.Result layout = layoutText(gc, details.getText(), wrapWidth);
    details.setLayout(layout);
    details.setWrapWidth(wrapWidth);

    HLayout layoutMeta = component.getLayout();
    boolean heightFixed =
        layoutMeta != null && layoutMeta.getTop() != null && layoutMeta.getBottom() != null;
    if (!heightFixed) {
      expectedGeometry.setHeight(layout.getHeight());
    }

    int usablePageHeight = presentation.getUsableHeight(page);

    if (paginate && !heightFixed) {
      paginateLines(
          results,
          renderPage,
          page,
          component,
          expectedGeometry,
          layout,
          usablePageHeight);
      return;
    }

    // Single part: optional peer page-break / overflow flag (same policy as base)
    int bottomOfComponent = expectedGeometry.getY() + expectedGeometry.getHeight();
    boolean overflowsPage = false;
    if (bottomOfComponent > usablePageHeight) {
      if (sequentialBelow) {
        renderPage = results.addNewPage(page, renderPage);
        expectedGeometry.setY(page.getTopMargin());
      } else if (allowPeerPageBreak(renderContext)) {
        HRenderPage last = results.getCurrentRenderPage(page);
        renderPage = results.addNewPage(page, last);
        expectedGeometry.setY(page.getTopMargin());
      } else {
        overflowsPage = true;
      }
    }

    addPart(
        results,
        renderPage,
        page,
        component,
        expectedGeometry,
        1,
        0,
        layout.getLines().size(),
        overflowsPage);
  }

  private void paginateLines(
      HLayoutResults results,
      HRenderPage renderPage,
      HPage page,
      HComponent component,
      HGeometry expectedGeometry,
      HTextLayout.Result layout,
      int usablePageHeight) {

    List<HTextLayout.Line> lines = layout.getLines();
    int lineHeight = layout.getLineHeight();
    int vMargin = Math.max(0, verticalMargin);
    int partNumber = 1;
    int startLine = 0;
    int y = expectedGeometry.getY();
    int remainingHeight = usablePageHeight - y;

    while (startLine < lines.size()) {
      int available = remainingHeight - 2 * vMargin;
      if (available < lineHeight) {
        // Not enough room for a single line — new page
        if (results.isAtRenderPageLimit()) {
          results.markPagesTruncated();
          break;
        }
        HRenderPage previous = renderPage;
        renderPage = results.addNewPage(page, renderPage);
        if (renderPage == previous) {
          results.markPagesTruncated();
          break;
        }
        partNumber++;
        y = page.getTopMargin();
        remainingHeight = usablePageHeight - y;
        available = remainingHeight - 2 * vMargin;
        if (available < lineHeight) {
          // Pathological: page shorter than one line
          available = lineHeight;
        }
      }

      int linesThatFit = Math.max(1, available / lineHeight);
      int endLine = Math.min(lines.size(), startLine + linesThatFit);
      int partHeight = (endLine - startLine) * lineHeight + 2 * vMargin;

      HGeometry partGeometry = expectedGeometry.clone();
      partGeometry.setY(y);
      partGeometry.setHeight(partHeight);

      addPart(
          results, renderPage, page, component, partGeometry, partNumber, startLine, endLine, false);

      startLine = endLine;
      if (startLine >= lines.size()) {
        break;
      }

      if (results.isAtRenderPageLimit()) {
        results.markPagesTruncated();
        break;
      }
      HRenderPage previous = renderPage;
      renderPage = results.addNewPage(page, renderPage);
      if (renderPage == previous) {
        results.markPagesTruncated();
        break;
      }
      partNumber++;
      y = page.getTopMargin();
      remainingHeight = usablePageHeight - y;
    }
  }

  private void addPart(
      HLayoutResults results,
      HRenderPage renderPage,
      HPage page,
      HComponent component,
      HGeometry partGeometry,
      int partNumber,
      int startLine,
      int endLine,
      boolean overflowsPage) {
    HComponentLayoutResult result = new HComponentLayoutResult();
    result.setRenderPage(renderPage);
    result.setSourcePage(page);
    result.setComponent(component);
    result.setGeometry(partGeometry);
    result.setPartNumber(partNumber);
    result.setOverflowsPage(overflowsPage);
    result.getDataMap().put(DATA_START_LINE, startLine);
    result.getDataMap().put(DATA_END_LINE, endLine);

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

    HGeometry componentGeometry = layoutResult.getGeometry();
    HComponent component = layoutResult.getComponent();
    TextBlockDetails details =
        (TextBlockDetails) results.getDataSet(component, DATA_TEXT_BLOCK);

    SVGGraphics2D gc = layoutResult.getRenderPage().getGc();
    setBackgroundBorderFont(gc, componentGeometry, renderContext);

    if (details == null || details.getLayout() == null) {
      return;
    }

    HTextLayout.Result layout = details.getLayout();
    List<HTextLayout.Line> allLines = layout.getLines();

    int startLine = 0;
    int endLine = allLines.size();
    Object startObj = layoutResult.getDataMap().get(DATA_START_LINE);
    Object endObj = layoutResult.getDataMap().get(DATA_END_LINE);
    if (startObj instanceof Integer) {
      startLine = (Integer) startObj;
    }
    if (endObj instanceof Integer) {
      endLine = (Integer) endObj;
    }
    startLine = Math.max(0, Math.min(startLine, allLines.size()));
    endLine = Math.max(startLine, Math.min(endLine, allLines.size()));

    int hMargin = Math.max(0, horizontalMargin);
    int vMargin = Math.max(0, verticalMargin);
    int lineHeight = layout.getLineHeight();
    int ascent = layout.getAscent();
    int contentLines = endLine - startLine;
    int blockHeight = contentLines * lineHeight;

    // Vertical alignment of the drawn block inside the part geometry (inside margins)
    int innerTop = componentGeometry.getY() + vMargin;
    int innerHeight = Math.max(0, componentGeometry.getHeight() - 2 * vMargin);
    int blockY = innerTop;
    HVerticalAlignment vAlign =
        verticalAlignment == null ? HVerticalAlignment.TOP : verticalAlignment;
    switch (vAlign) {
      case BOTTOM:
        blockY = innerTop + Math.max(0, innerHeight - blockHeight);
        break;
      case MIDDLE:
        blockY = innerTop + Math.max(0, (innerHeight - blockHeight) / 2);
        break;
      case TOP:
      default:
        break;
    }

    HHorizontalAlignment hAlign =
        horizontalAlignment == null ? HHorizontalAlignment.LEFT : horizontalAlignment;
    int contentAreaLeft = componentGeometry.getX() + hMargin;
    int contentAreaWidth =
        Math.max(0, componentGeometry.getWidth() - 2 * hMargin);

    // Clip to component geometry when height is constrained
    java.awt.Shape previousClip = gc.getClip();
    gc.setClip(
        componentGeometry.getX(),
        componentGeometry.getY(),
        componentGeometry.getWidth(),
        componentGeometry.getHeight());

    try {
      for (int i = startLine; i < endLine; i++) {
        HTextLayout.Line line = allLines.get(i);
        String lineText = line.getText();
        int lineWidth = line.getWidth();
        int lineIndex = i - startLine;

        int x;
        switch (hAlign) {
          case RIGHT:
            x = contentAreaLeft + Math.max(0, contentAreaWidth - lineWidth);
            break;
          case CENTER:
            x = contentAreaLeft + Math.max(0, (contentAreaWidth - lineWidth) / 2);
            break;
          case LEFT:
          default:
            x = contentAreaLeft;
            break;
        }

        int baseline = blockY + lineIndex * lineHeight + ascent;
        if (StringUtils.isEmpty(lineText)) {
          continue;
        }
        gc.drawString(lineText, x, baseline);
      }
    } finally {
      gc.setClip(previousClip);
    }

    String fullText = details.getText() == null ? "" : details.getText();
    HGeometry hitGeometry =
        new HGeometry(
            offSet.getX() + componentGeometry.getX(),
            offSet.getY() + componentGeometry.getY(),
            componentGeometry.getWidth(),
            componentGeometry.getHeight());

    layoutResult
        .getRenderPage()
        .getDrawnItems()
        .add(
            new DrawnItem(
                component.getName(),
                component.getComponent().getPluginId(),
                layoutResult.getPartNumber(),
                DrawnItem.DrawnItemType.ComponentItem,
                DrawnItem.Category.Text.name(),
                0,
                0,
                hitGeometry,
                new DrawnContext(fullText)));
  }

  private HTextLayout.Result layoutText(SVGGraphics2D gc, String resolved, int wrapWidth) {
    FontMetrics fm = gc.getFontMetrics();
    return HTextLayout.layout(
        resolved,
        fm,
        wrapWidth,
        wrap,
        parseLineSpacing(),
        horizontalMargin,
        verticalMargin);
  }

  private int resolveProvisionalWrapWidth(HComponent component) {
    if (component != null
        && component.getClipSize() != null
        && component.getClipSize().getWidth() > 0) {
      return component.getClipSize().getWidth();
    }
    if (maxWidth > 0) {
      return maxWidth;
    }
    HLayout layout = component == null ? null : component.getLayout();
    // Left+right will set width later; until then use unconstrained for provisional size
    if (layout != null && layout.getLeft() != null && layout.getRight() != null) {
      // Unknown page width here; use maxWidth if any, else unconstrained
      return maxWidth > 0 ? maxWidth : Integer.MAX_VALUE;
    }
    return Integer.MAX_VALUE;
  }

  private float parseLineSpacing() {
    String raw = Const.NVL(lineSpacing, "1.0").trim();
    try {
      float v = Float.parseFloat(raw);
      return v < 0.5f ? 1.0f : v;
    } catch (NumberFormatException e) {
      return 1.0f;
    }
  }

  private static boolean isSequentialBelowLayout(HLayout layout) {
    if (layout == null || layout.getTop() == null) {
      return false;
    }
    return layout.getTop().getAlignment() == org.hopper.core.HAttachment.Alignment.BOTTOM
        && StringUtils.isNotEmpty(layout.getTop().getComponentName());
  }

  private static boolean allowPeerPageBreak(IRenderContext renderContext) {
    if (renderContext instanceof org.hopper.render.context.SimpleRenderContext simple) {
      return simple.isAllowPeerPageBreak();
    }
    return true;
  }
}
