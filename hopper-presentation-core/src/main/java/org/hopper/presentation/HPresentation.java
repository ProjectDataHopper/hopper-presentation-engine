package org.hopper.presentation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.Metrics;
import org.apache.hop.core.metrics.MetricsSnapshotType;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.HColorRGB;
import org.hopper.core.HGeometry;
import org.hopper.core.HJson;
import org.hopper.core.HPosition;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.core.log.HMetricsUtil;
import org.hopper.core.metastore.IHasIdentity;
import org.hopper.core.HAttachment;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.datacontext.RenderPageDataContext;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.presentation.variable.HParameter;
import org.hopper.presentation.variable.HParameterMapping;
import org.hopper.render.IRenderContext;
import org.hopper.render.context.PresentationRenderContext;
import lombok.Getter;
import lombok.Setter;

@HopMetadata(
    key = "presentation",
    name = "Presentation",
    description = "Top level document of the presentation metadata")
@Getter
@Setter
public class HPresentation extends HopMetadataBase implements IHasIdentity, IHopMetadata {

  @HopMetadataProperty private String description;

  @HopMetadataProperty private List<HPage> pages;

  @HopMetadataProperty private HPage header;

  @HopMetadataProperty private HPage footer;

  /** Name of the default theme in the theme metadata catalog (not embedded on the presentation). */
  @HopMetadataProperty private String defaultThemeName;

  @HopMetadataProperty private List<HInteraction> interactions;
  @HopMetadataProperty private List<HParameterMapping> parameterMappings;

  /**
   * When set and positive, the view page re-renders on this interval (seconds). Useful for ops
   * dashboards (currently executing runs).
   */
  @HopMetadataProperty private Integer autoRefreshSeconds;

  public HPresentation() {
    pages = new ArrayList<>();
    interactions = new ArrayList<>();
    parameterMappings = new ArrayList<>();
  }

  /**
   * Create a copy of every page, component, interaction and parameter mapping.
   *
   * @param p
   */
  public HPresentation(HPresentation p) {
    this();
    this.name = p.name;
    this.description = p.description;
    this.defaultThemeName = p.defaultThemeName;
    this.autoRefreshSeconds = p.autoRefreshSeconds;
    this.header = p.header == null ? null : new HPage(p.header);
    this.footer = p.footer == null ? null : new HPage(p.footer);
    p.pages.forEach(page -> this.pages.add(new HPage(page)));
    p.interactions.forEach(i -> this.interactions.add(new HInteraction(i)));
    p.parameterMappings.forEach(m -> this.parameterMappings.add(new HParameterMapping(m)));
  }

  public static HPresentation fromJsonString(String jsonString) throws IOException {
    return HJson.createMapper().readValue(jsonString, HPresentation.class);
  }

  @Override
  public String toString() {
    return name != null ? name : super.toString();
  }

  public String toJsonString() throws JsonProcessingException {
    return toJsonString(false);
  }

  public String toJsonString(boolean indent) throws JsonProcessingException {
    ObjectMapper objectMapper = HJson.createMapper();
    if (indent) {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
    } else {
      return objectMapper.writeValueAsString(this);
    }
  }

  /**
   * Perform the layout of this presentation.
   *
   * @param parent the parent logging object
   * @param renderContext The rendering context
   * @param metadataProvider The metadata provider to reference external metadata with
   * @param parameters Parameter values that you might want to set in the presentation data context
   * @return The layout results
   * @throws HException
   */
  public HLayoutResults doLayout(
      ILoggingObject parent,
      IRenderContext renderContext,
      IHopMetadataProvider metadataProvider,
      List<HParameter> parameters)
      throws HException {
    return doLayout(parent, renderContext, metadataProvider, parameters, null);
  }

  /**
   * Perform layout with an optional execution lineage collector. When {@code executionTrace} is
   * null, a new active trace is created for this layout.
   */
  public HLayoutResults doLayout(
      ILoggingObject parent,
      IRenderContext renderContext,
      IHopMetadataProvider metadataProvider,
      List<HParameter> parameters,
      HExecutionTrace executionTrace)
      throws HException {

    ILogChannel log = new LogChannel(getName(), parent, true);

    log.logBasic("====> setting parameters: " + parameters.size());
    for (HParameter parameter : parameters) {
      log.logBasic("  ===> Setting parameter: " + parameter);
    }

    PresentationDataContext presentationDataContext =
        new PresentationDataContext(this, metadataProvider);
    HExecutionTrace trace = executionTrace != null ? executionTrace : HExecutionTrace.create();
    presentationDataContext.setExecutionTrace(trace);

    // Themes and connectors resolve from metadata via data/render contexts (not embedded here).

    HLayoutResults results = new HLayoutResults(log);
    results.setDataContext(presentationDataContext);
    results.setExecutionTrace(trace);

    log.logBasic("Started layout of presentation");
    long layoutStart = System.currentTimeMillis();
    log.snap(
        new Metrics(
            MetricsSnapshotType.START,
            HMetricsUtil.PRESENTATION_START_LAYOUT,
            "Presentation starts layout"));

    try {
      // Apply the given variable values to the data context...
      //
      applyParametersToContext(parameters, presentationDataContext);

      // See if more parameters need to be set using one or more connectors
      //
      applyParameterMappings(presentationDataContext);

      List<HPage> pagesCopy = new ArrayList<>(pages);

      // Loop over the components on every page, generate layout results...
      //
      for (HPage page : pagesCopy) {

        // At the very least, add an empty render page in case we have no components...
        //
        results.addNewPage(page, null);

        List<HComponent> sortedComponents = page.getSortedComponents();
        for (HComponent hopperComponent : sortedComponents) {
          layoutComponentSafely(
              log, page, hopperComponent, presentationDataContext, renderContext, results);
        }
      }

      return results;
    } catch (HException e) {
      if (trace != null && !trace.isNoop()) {
        trace.finishFailure(e);
      }
      throw e;
    } catch (RuntimeException e) {
      if (trace != null && !trace.isNoop()) {
        trace.finishFailure(e);
      }
      throw e;
    } finally {
      log.snap(
          new Metrics(
              MetricsSnapshotType.STOP,
              HMetricsUtil.PRESENTATION_FINISH_LAYOUT,
              "Presentation finished layout"));
      log.logBasic("Finished layout of presentation");
      if (trace != null && !trace.isNoop()) {
        trace.setLayoutMs(System.currentTimeMillis() - layoutStart);
        if (results.getRenderPages() != null) {
          trace.setPageCount(results.getRenderPages().size());
        }
      }
    }
  }

  private void applyParameterMappings(PresentationDataContext presentationDataContext)
      throws HException {
    IVariables variables = presentationDataContext.getVariables();

    for (HParameterMapping parameterMapping : parameterMappings) {
      // Read rows from the connector specified.  The data context has the metadata provider.
      //
      String connectorName = variables.resolve(parameterMapping.getConnectorName());
      if (StringUtils.isEmpty(connectorName)) {
        throw new HException(
            "Please specify a connector name to read rows of data from.  "
                + "These rows can be used to set parameters in the presentation.");
      }
      String separator = variables.resolve(parameterMapping.getSeparator());

      HConnector connector = presentationDataContext.getConnector(connectorName);
      List<RowMetaAndData> rows = connector.retrieveRows(presentationDataContext);

      Map<String, String> parametersMap = new HashMap<>();

      for (HParameterMapping.FieldToParameterMapping mapping : parameterMapping.getMappings()) {
        String fieldName = variables.resolve(mapping.getFieldName());
        if (StringUtils.isEmpty(fieldName)) {
          throw new HException(
              "Please specify a field name to map when reading from connector " + connectorName);
        }
        String parameterName = variables.resolve(mapping.getParameterName());
        if (StringUtils.isEmpty(parameterName)) {
          throw new HException(
              "Please specify a name for a parameter to set for field name " + fieldName);
        }

        // Concatenate all input rows to flatten to a single value per field.
        //
        for (RowMetaAndData row : rows) {
          try {
            String value = row.getString(fieldName, "");
            String totalValue = parametersMap.get(parameterName);
            if (totalValue == null) {
              totalValue = value;
            } else {
              totalValue = Const.NVL(separator, "") + value;
            }
            parametersMap.put(parameterName, totalValue);

          } catch (Exception e) {
            throw new HException(
                "Error converting an input row value to a string when mapping field "
                    + fieldName
                    + " to parameter "
                    + parameterName,
                e);
          }
        }
      }

      // Now that we have all the parameter values, set these in the data context.
      //
      parametersMap
          .keySet()
          .forEach(
              parameterName -> {
                String parameterValue = parametersMap.get(parameterName);
                presentationDataContext.getVariables().setVariable(parameterName, parameterValue);
              });
    }
  }

  private void applyParametersToContext(
      List<HParameter> parameters, PresentationDataContext presentationDataContext) {
    for (HParameter variable : parameters) {
      if (StringUtils.isNotEmpty(variable.getParameterName())) {
        String name = variable.getParameterName();
        String value = variable.getParameterValue();
        presentationDataContext.getVariables().setVariable(name, Const.NVL(value, ""));
      }
    }
  }

  /**
   * Render this presentation by rendering all the render pages in the layout results... At the end,
   * we'll have some stuff drawn on the Graphics Context of each render page...
   *
   * @param results Where to store rendering results
   * @param metadataProvider The metadata provider to reference external metadata with
   * @return The presentation rendering log channel
   * @throws HException in case something goes wrong
   */
  public ILogChannel render(HLayoutResults results, IHopMetadataProvider metadataProvider)
      throws HException {
    return render(results, metadataProvider, null);
  }

  /**
   * Render this presentation. When {@code sharedRenderContext} is non-null it is reused (including
   * its stable series-color maps) so a follow-up single-component preview can match full-page
   * colors.
   */
  public ILogChannel render(
      HLayoutResults results,
      IHopMetadataProvider metadataProvider,
      PresentationRenderContext sharedRenderContext)
      throws HException {

    ILogChannel log = results.getLog();
    PresentationDataContext presentationDataContext =
        new PresentationDataContext(this, metadataProvider);
    PresentationRenderContext presentationRenderContext =
        sharedRenderContext != null
            ? sharedRenderContext
            : new PresentationRenderContext(this, metadataProvider);
    if (sharedRenderContext != null) {
      // Keep theme lookup bound to this presentation instance
      presentationRenderContext.setPresentation(this);
    }

    log.logBasic("Started rendering presentation");
    log.snap(
        new Metrics(
            MetricsSnapshotType.START,
            HMetricsUtil.PRESENTATION_START_RENDER,
            "Presentation starts rendering"));

    try {
      // Now that we know the layout, we know the page numbers.
      //
      results.setRenderPageNumbers();

      // Loop over all the pages that were allocated
      //
      for (HRenderPage renderPage : results.getRenderPages()) {
        HPage page = renderPage.getPage();
        SVGGraphics2D gc = renderPage.getGc();

        // Fill the background with the default background color...
        //
        HTheme defaultTheme = resolveDefaultTheme(metadataProvider);
        HColorRGB bg = defaultTheme.lookupBackgroundColor();
        gc.setColor(new Color(bg.getR(), bg.getG(), bg.getB()));
        gc.fillRect(0, 0, page.getWidth(), page.getHeight());

        AffineTransform parentTransform = gc.getTransform();

        // First render header and footer if present
        //
        renderHeaderFooter(
            log, renderPage, parentTransform, presentationDataContext, presentationRenderContext);

        // Draw at top left of page
        //
        HPosition offSet =
            new HPosition(page.getLeftMargin(), page.getTopMargin() + getHeaderHeight());
        gc.translate(offSet.getX(), offSet.getY());

        // Loop over all the component layout results on the page...
        //
        List<HComponentLayoutResult> componentLayoutResults = renderPage.getLayoutResults();
        for (HComponentLayoutResult componentLayoutResult : componentLayoutResults) {
          HComponent hopperComponent = componentLayoutResult.getComponent();

          // Render the component...
          //
          AffineTransform beforeRotation = gc.getTransform();

          // Do we rotate?
          // If so, rotate around the center of the object
          //
          if (StringUtils.isNotEmpty(hopperComponent.getRotation())) {
            HGeometry geometry = componentLayoutResult.getGeometry();
            double angle = Math.toRadians(Const.toDouble(hopperComponent.getRotation(), 0));
            int originX = geometry.getX() + geometry.getWidth() / 2;
            int originY = geometry.getY() + geometry.getHeight() / 2;
            gc.rotate(angle, originX, originY);
          }

          // Transparency?
          //
          Composite beforeComposite = gc.getComposite();
          if (StringUtils.isNotEmpty(hopperComponent.getTransparency())) {
            double alpha = Const.toDouble(hopperComponent.getTransparency(), 0) / 100;
            if (alpha > 1.0f) {
              alpha = 1.0f;
            }
            if (alpha < 0.0f) {
              alpha = 0.0f;
            }
            gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
          }

          // Clipping of string drawing...
          boolean clip =
              hopperComponent.getClipSize() != null && hopperComponent.getClipSize().isDefined();
          Shape oldClip = gc.getClip();
          if (clip) {
            HGeometry lg = componentLayoutResult.getGeometry();
            gc.setClip(lg.getX(), lg.getY(), lg.getWidth(), lg.getHeight());
          }

          HComponent component = componentLayoutResult.getComponent();
          HRenderPage bodyPage = componentLayoutResult.getRenderPage();
          renderComponentSafely(
              log,
              gc,
              componentLayoutResult,
              results,
              presentationRenderContext,
              offSet,
              bodyPage);

          if (clip) {
            gc.setClip(oldClip);
          }

          // Remember where we've drawn this component on THIS render page.
          // Use the layout result geometry (per part / per page), not results.findGeometry(name)
          // which is a single map entry overwritten by multi-page components (table/crosstab).
          //
          HGeometry componentGeometry = componentLayoutResult.getGeometry();
          if (componentGeometry != null) {
            bodyPage.addComponentDrawnItem(component, componentGeometry, offSet);
          }

          gc.setComposite(beforeComposite);
          gc.setTransform(beforeRotation);
        }
      }

    } finally {
      log.snap(
          new Metrics(
              MetricsSnapshotType.STOP,
              HMetricsUtil.PRESENTATION_FINISH_RENDER,
              "Presentation finished rendering"));
      log.logBasic("Finished rendering presentation");
    }

    return log;
  }

  /** Render the header and footers on top of the render page... */
  private void renderHeaderFooter(
      ILogChannel log,
      HRenderPage renderPage,
      AffineTransform parentTransform,
      IDataContext presentationDataContext,
      IRenderContext renderContext)
      throws HException {
    HPage page = renderPage.getPage();
    HopSvgGraphics2D gc = renderPage.getGc();

    // What is the render context for header and footer?
    //
    RenderPageDataContext pageDataContext =
        new RenderPageDataContext(presentationDataContext, renderPage);

    if (header != null) {
      // Just making sure
      header.setHeader(true);

      // Do the layout of the header on every page again...
      //
      List<HComponent> sortedComponents = header.getSortedComponents();

      // Create a new results object which maps onto the existing render page
      //
      HLayoutResults headerResults = new HLayoutResults(log);

      for (HComponent component : sortedComponents) {
        layoutComponentSafely(
            log, header, component, pageDataContext, renderContext, headerResults);
      }

      // We did the layout and generated a new page for the header
      // It's contained in headerResults
      // We don't want to render on these RenderPages though, we want to render on the given
      // renderPage.
      //
      headerResults.replaceGCForHeaderFooter(gc);
      headerResults.replaceDrawnItemsForHeaderFooter(renderPage.getDrawnItems());

      // Empty header (just enabled, no components yet) has no layout results — skip draw
      if (!headerResults.getRenderPages().isEmpty()) {
        // Before rendering, position rendering at the top of the page, after the margin...
        //
        HPosition offSet = new HPosition(page.getLeftMargin(), page.getTopMargin());
        gc.translate(offSet.getX(), offSet.getY());

        // Now render the header onto the given render page GC
        // Only one header "page" is supported
        //
        List<HComponentLayoutResult> componentLayoutResults =
            headerResults.getRenderPages().get(0).getLayoutResults();
        for (HComponentLayoutResult componentLayoutResult : componentLayoutResults) {
          HComponent component = componentLayoutResult.getComponent();
          renderComponentSafely(
              log,
              gc,
              componentLayoutResult,
              headerResults,
              renderContext,
              offSet,
              renderPage);
          if (componentLayoutResult.getGeometry() != null) {
            renderPage.addComponentDrawnItem(
                component, componentLayoutResult.getGeometry(), offSet);
          }
        }

        // Reset the gc translation...
        //
        gc.setTransform(parentTransform);
      }
    }

    if (footer != null) {
      // Just making sure
      footer.setFooter(true);

      // Do the layout of the footer on every page again...
      //
      List<HComponent> sortedComponents = footer.getSortedComponents();

      // Create a new results object which maps onto the existing render page
      //
      HLayoutResults footerResults = new HLayoutResults(log);

      for (HComponent hopperComponent : sortedComponents) {
        layoutComponentSafely(
            log, footer, hopperComponent, pageDataContext, renderContext, footerResults);
      }

      // We did the layout and generated a new page for the footer
      // It's contained in footerResults
      // We don't want to render on these RenderPages though, we want to render on the given
      // renderPage.
      //
      footerResults.replaceGCForHeaderFooter(gc);
      footerResults.replaceDrawnItemsForHeaderFooter(renderPage.getDrawnItems());

      // Empty footer (just enabled, no components yet) has no layout results — skip draw
      if (!footerResults.getRenderPages().isEmpty()) {
        // Before rendering, position rendering at the bottom of the page.
        // The position is the page height minus bottom margin and footer height
        //
        HPosition offSet =
            new HPosition(
                page.getLeftMargin(),
                page.getHeight() - page.getBottomMargin() - getFooterHeight());
        gc.translate(offSet.getX(), offSet.getY());

        // Now render the footer onto the given render page GC
        // Only one footer "page" is supported
        //
        List<HComponentLayoutResult> componentLayoutResults =
            footerResults.getRenderPages().get(0).getLayoutResults();
        for (HComponentLayoutResult componentLayoutResult : componentLayoutResults) {
          HComponent component = componentLayoutResult.getComponent();
          renderComponentSafely(
              log,
              gc,
              componentLayoutResult,
              footerResults,
              renderContext,
              offSet,
              renderPage);
          if (componentLayoutResult.getGeometry() != null) {
            renderPage.addComponentDrawnItem(
                component, componentLayoutResult.getGeometry(), offSet);
          }
        }

        // Reset the gc translation...
        //
        gc.setTransform(parentTransform);
      }
    }
  }

  /** Data-map key when layout failed for a component (placeholder still drawn). */
  public static final String DATA_LAYOUT_ERROR = "layoutError";

  /** Full exception chain / stack for property-panel diagnostics. */
  public static final String DATA_LAYOUT_ERROR_DETAIL = "layoutErrorDetail";

  /**
   * Process source data + layout for one component. On failure (e.g. SQL table missing), log and
   * place a placeholder so the presentation editor can still open.
   */
  private void layoutComponentSafely(
      ILogChannel log,
      HPage page,
      HComponent hopperComponent,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results) {
    if (hopperComponent == null || hopperComponent.getComponent() == null) {
      return;
    }
    IHComponent component = hopperComponent.getComponent();
    try {
      // Treat blank like unset (forms often write "" for "use default theme")
      if (StringUtils.isEmpty(component.getThemeName())) {
        component.setThemeName(defaultThemeName);
      }
      component.setLogChannel(log);
      component.processSourceData(this, page, hopperComponent, dataContext, renderContext, results);
      component.doLayout(this, page, hopperComponent, dataContext, renderContext, results);
    } catch (Exception e) {
      String summary = summarizeException(e);
      String detail = formatExceptionDetail(e);
      log.logError(
          "Error laying out component '"
              + hopperComponent.getName()
              + "' (continuing with placeholder): "
              + summary,
          e);
      addFailedComponentPlaceholder(results, page, hopperComponent, summary, detail);
    }
  }

  /**
   * Render one component layout result; draw an error box if layout failed or render throws.
   *
   * @param targetPage the page receiving drawn items (body page; may differ from layout result page
   *     for header/footer). When non-null, layout errors are recorded for the editor.
   */
  private void renderComponentSafely(
      ILogChannel log,
      SVGGraphics2D gc,
      HComponentLayoutResult componentLayoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet,
      HRenderPage targetPage) {
    HComponent component = componentLayoutResult.getComponent();
    try {
      if (componentLayoutResult.getDataMap() != null
          && componentLayoutResult.getDataMap().containsKey(DATA_LAYOUT_ERROR)) {
        String summary =
            String.valueOf(componentLayoutResult.getDataMap().get(DATA_LAYOUT_ERROR));
        Object detailObj = componentLayoutResult.getDataMap().get(DATA_LAYOUT_ERROR_DETAIL);
        String detail = detailObj != null ? String.valueOf(detailObj) : summary;
        drawFailedComponentPlaceholder(gc, componentLayoutResult.getGeometry(), summary);
        recordComponentErrorOnPage(targetPage, component, summary, detail);
        return;
      }
      component
          .getComponent()
          .render(componentLayoutResult, results, renderContext, offSet);
    } catch (Exception renderEx) {
      String summary = summarizeException(renderEx);
      String detail = formatExceptionDetail(renderEx);
      log.logError(
          "Error rendering component '" + component.getName() + "': " + summary, renderEx);
      drawFailedComponentPlaceholder(gc, componentLayoutResult.getGeometry(), summary);
      if (componentLayoutResult.getDataMap() != null) {
        componentLayoutResult.getDataMap().put(DATA_LAYOUT_ERROR, summary);
        componentLayoutResult.getDataMap().put(DATA_LAYOUT_ERROR_DETAIL, detail);
      }
      recordComponentErrorOnPage(targetPage, component, summary, detail);
    }
  }

  private static void recordComponentErrorOnPage(
      HRenderPage targetPage, HComponent component, String summary, String detail) {
    if (targetPage == null || component == null) {
      return;
    }
    targetPage.recordComponentError(component.getName(), summary, detail);
  }

  /** Prefer the root cause message for short UI labels (canvas / list). */
  public static String summarizeException(Throwable e) {
    if (e == null) {
      return "Unknown error";
    }
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    if (root.getMessage() != null && !root.getMessage().isBlank()) {
      return root.getMessage().trim();
    }
    if (e.getMessage() != null && !e.getMessage().isBlank()) {
      return e.getMessage().trim();
    }
    return e.getClass().getSimpleName();
  }

  /** Full cause chain + simple stack for the property-panel diagnostics. */
  public static String formatExceptionDetail(Throwable e) {
    if (e == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    Throwable t = e;
    int depth = 0;
    while (t != null && depth < 20) {
      if (depth > 0) {
        sb.append("\nCaused by: ");
      }
      String msg = t.getMessage();
      if (msg != null && !msg.isBlank()) {
        sb.append(msg.trim());
      } else {
        sb.append(t.getClass().getName());
      }
      t = t.getCause();
      depth++;
    }
    try {
      String stack = Const.getSimpleStackTrace(e);
      if (stack != null && !stack.isBlank()) {
        sb.append("\n\n").append(stack.trim());
      }
    } catch (Exception ignored) {
      // Const may not be available in all environments
    }
    return sb.toString();
  }

  private void addFailedComponentPlaceholder(
      HLayoutResults results,
      HPage page,
      HComponent hopperComponent,
      String errorMessage,
      String errorDetail) {
    try {
      // Place error placeholders on the first body page so they stay visible next to
      // multi-page tables (same rule as non-flowing doLayout).
      HRenderPage renderPage = results.getFirstRenderPage(page);
      if (renderPage == null) {
        renderPage = results.addNewPage(page, null);
      }
      HGeometry geometry = geometryFromLayoutOrDefault(hopperComponent, page);
      HComponentLayoutResult result = new HComponentLayoutResult();
      result.setRenderPage(renderPage);
      result.setSourcePage(page);
      result.setComponent(hopperComponent);
      result.setGeometry(geometry);
      result.setPartNumber(1);
      result.getDataMap().put(DATA_LAYOUT_ERROR, errorMessage);
      if (errorDetail != null) {
        result.getDataMap().put(DATA_LAYOUT_ERROR_DETAIL, errorDetail);
      }
      renderPage.recordComponentError(hopperComponent.getName(), errorMessage, errorDetail);
      results.addComponentGeometry(hopperComponent.getName(), geometry);
      renderPage.getLayoutResults().add(result);
    } catch (Exception e) {
      // Last resort: ignore placeholder failure so layout can finish
      if (results != null && results.getLog() != null) {
        results
            .getLog()
            .logError(
                "Could not create placeholder for component '"
                    + hopperComponent.getName()
                    + "': "
                    + e.getMessage());
      }
    }
  }

  /**
   * Best-effort geometry from absolute layout offsets when processSourceData/doLayout failed
   * (cannot use component getExpectedGeometry — it often depends on data details).
   */
  private HGeometry geometryFromLayoutOrDefault(HComponent hopperComponent, HPage page) {
    int x = 0;
    int y = 0;
    int w = 400;
    int h = 200;
    HLayout layout = hopperComponent != null ? hopperComponent.getLayout() : null;
    if (layout != null) {
      if (layout.getLeft() != null) {
        x = layout.getLeft().getOffset();
      }
      if (layout.getTop() != null) {
        y = layout.getTop().getOffset();
      }
      if (layout.getRight() != null
          && layout.getRight().getAlignment() == HAttachment.Alignment.LEFT) {
        w = Math.max(40, layout.getRight().getOffset() - x);
      } else if (layout.getRight() != null
          && (layout.getRight().getAlignment() == HAttachment.Alignment.RIGHT
              || layout.getRight().getAlignment() == HAttachment.Alignment.DEFAULT)
          && page != null) {
        w = Math.max(40, page.getWidthBetweenMargins() - x + layout.getRight().getOffset());
      }
      if (layout.getBottom() != null
          && layout.getBottom().getAlignment() == HAttachment.Alignment.TOP) {
        h = Math.max(40, layout.getBottom().getOffset() - y);
      } else if (layout.getBottom() != null
          && (layout.getBottom().getAlignment() == HAttachment.Alignment.BOTTOM
              || layout.getBottom().getAlignment() == HAttachment.Alignment.DEFAULT)
          && page != null) {
        h = Math.max(40, getUsableHeight(page) - y + layout.getBottom().getOffset());
      }
    }
    return new HGeometry(x, y, w, h);
  }

  private static void drawFailedComponentPlaceholder(
      SVGGraphics2D gc, HGeometry geometry, String message) {
    if (gc == null || geometry == null) {
      return;
    }
    int x = geometry.getX();
    int y = geometry.getY();
    int w = Math.max(40, geometry.getWidth());
    int h = Math.max(24, geometry.getHeight());
    Color old = gc.getColor();
    java.awt.Font oldFont = gc.getFont();
    try {
      gc.setColor(new Color(255, 245, 245));
      gc.fillRect(x, y, w, h);
      gc.setColor(new Color(180, 40, 40));
      gc.drawRect(x, y, w - 1, h - 1);
      gc.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
      String text = message != null ? message : "Component error";
      // Keep first line short enough for the box
      if (text.length() > 120) {
        text = text.substring(0, 117) + "...";
      }
      gc.drawString(text, x + 6, y + Math.min(18, h - 4));
    } finally {
      gc.setColor(old);
      gc.setFont(oldFont);
    }
  }

  /**
   * Resolve the presentation default theme from metadata by {@link #defaultThemeName}, falling
   * back to {@link HTheme#getDefault()}.
   */
  public HTheme resolveDefaultTheme(IHopMetadataProvider metadataProvider) {
    if (StringUtils.isNotEmpty(defaultThemeName) && metadataProvider != null) {
      try {
        HTheme theme =
            metadataProvider.getSerializer(HTheme.class).load(defaultThemeName);
        if (theme != null) {
          return theme;
        }
      } catch (Exception e) {
        // fall through to built-in default
      }
    }
    return HTheme.getDefault();
  }

  /**
   * Calculate how much usable room is on the base. It's the height of the page minus the header
   * imageSize, the footer imageSize and the page margins
   *
   * @param page The page to render on.
   * @return The usable height on the page
   */
  public int getUsableHeight(HPage page) {
    int height = page.getHeight();
    height -= page.getTopMargin();
    height -= page.getBottomMargin();
    if (!page.isHeader() && !page.isFooter()) {
      height -= getHeaderHeight();
      height -= getFooterHeight();
    }
    return height;
  }

  @JsonIgnore
  public int getHeaderHeight() {
    if (header == null) {
      return 0;
    } else {
      return header.getHeight();
    }
  }

  @JsonIgnore
  public int getFooterHeight() {
    if (footer == null) {
      return 0;
    } else {
      return footer.getHeight();
    }
  }

  /**
   * Find the given interaction for the drawn item. Look in the list of defined interactions for
   * this presentation to see what needs to happen to the particular drawn item. We assumed it's
   * something
   *
   * @param method the method to look for or null for any method
   * @param drawnItem The drawn item
   * @return The first interaction found for this possibility.
   */
  public HInteraction findInteraction(HInteractionMethod method, DrawnItem drawnItem) {
    for (HInteraction interaction : interactions) {
      if (interaction.matches(method, drawnItem)) {
        return interaction;
      }
    }
    return null;
  }

  /**
   * Get the index of a logical page.
   *
   * @param page The page to index
   * @return The index (page number) of the page
   */
  public int getPageIndex(HPage page) {
    return pages.indexOf(page);
  }
}
