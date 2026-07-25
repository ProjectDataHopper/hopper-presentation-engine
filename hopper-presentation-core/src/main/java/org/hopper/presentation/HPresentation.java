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
import org.hopper.presentation.variable.HParameterDefinition;
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

  /** Name of the default (light) theme in the theme metadata catalog. */
  @HopMetadataProperty private String defaultThemeName;

  /**
   * Optional catalog theme name for dark color mode. When blank, a dark variant is derived from
   * {@link #defaultThemeName} at render time.
   */
  @HopMetadataProperty private String darkThemeName;

  @HopMetadataProperty private List<HInteraction> interactions;
  @HopMetadataProperty private List<HParameterMapping> parameterMappings;

  /**
   * Declared presentation parameters (Hop pipeline/workflow style): name, description, default
   * value. Used for editor lists, interaction mapping, future prompts, and layout defaults.
   *
   * <p>Variable hierarchy at layout: system variables → these defaults → connector parameter
   * mappings → request/interaction values passed to {@link #doLayout} (always win).
   */
  @HopMetadataProperty private List<HParameterDefinition> parameters;

  /**
   * When set and positive, the view page re-renders on this interval (seconds). Useful for ops
   * dashboards (currently executing runs).
   */
  @HopMetadataProperty private Integer autoRefreshSeconds;

  public HPresentation() {
    pages = new ArrayList<>();
    interactions = new ArrayList<>();
    parameterMappings = new ArrayList<>();
    parameters = new ArrayList<>();
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
    this.darkThemeName = p.darkThemeName;
    this.autoRefreshSeconds = p.autoRefreshSeconds;
    this.header = p.header == null ? null : new HPage(p.header);
    this.footer = p.footer == null ? null : new HPage(p.footer);
    p.pages.forEach(page -> this.pages.add(new HPage(page)));
    p.interactions.forEach(i -> this.interactions.add(new HInteraction(i)));
    p.parameterMappings.forEach(m -> this.parameterMappings.add(new HParameterMapping(m)));
    if (p.parameters != null) {
      p.parameters.forEach(d -> this.parameters.add(new HParameterDefinition(d)));
    }
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
    return doLayout(parent, renderContext, metadataProvider, parameters, null, false);
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
    return doLayout(parent, renderContext, metadataProvider, parameters, executionTrace, false);
  }

  /**
   * Perform layout with optional lineage and full-refresh flag (bypasses connector disk-cache
   * reads).
   */
  public HLayoutResults doLayout(
      ILoggingObject parent,
      IRenderContext renderContext,
      IHopMetadataProvider metadataProvider,
      List<HParameter> parameters,
      HExecutionTrace executionTrace,
      boolean forceReload)
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
    presentationDataContext.setLogChannel(log);
    presentationDataContext.setForceReload(forceReload);

    // Themes and connectors resolve from metadata via data/render contexts (not embedded here).

    HLayoutResults results = new HLayoutResults(log);
    results.setDataContext(presentationDataContext);
    results.setExecutionTrace(trace);
    results.setPresentationName(getName());
    results.setParametersFingerprint(
        org.hopper.presentation.layout.HLayoutFingerprint.parameters(parameters));
    if (renderContext instanceof org.hopper.render.context.SimpleRenderContext src) {
      org.hopper.core.HColorMode mode = src.getColorMode();
      // Include peer-break policy so editor (no silent push) and view/export caches never collide
      String modeKey = mode != null ? mode.wireValue() : "light";
      if (!src.isAllowPeerPageBreak()) {
        modeKey = modeKey + "|noPeerBreak";
      }
      // Include page cap so cache entries from a different admin limit never collide
      int maxPages = src.getMaxRenderPages();
      if (maxPages <= 0) {
        maxPages = org.hopper.presentation.layout.HLayoutPageLimitSettings.getMaxRenderPages();
        src.setMaxRenderPages(maxPages);
      }
      results.setMaxRenderPages(maxPages);
      modeKey = modeKey + "|maxPg=" + maxPages;
      results.setColorMode(modeKey);
    } else {
      results.setMaxRenderPages(
          org.hopper.presentation.layout.HLayoutPageLimitSettings.getMaxRenderPages());
      results.setColorMode("light");
    }

    log.logBasic("Started layout of presentation");
    long layoutStart = System.currentTimeMillis();
    // Legacy dual-code snaps (tests / older readers)
    log.snap(
        new Metrics(
            MetricsSnapshotType.START,
            HMetricsUtil.PRESENTATION_START_LAYOUT,
            "Presentation starts layout"));
    // Hop-style pair (Gantt / MetricsUtil.getLastDuration)
    HMetricsUtil.start(
        log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");

    try {
      // Variable hierarchy:
      //   1) system / parent variables (already copied into PresentationDataContext)
      //   2) presentation parameter definition defaults
      //   3) parameter-mapping defaults + connector field mapping
      //   4) request / interaction parameters (always win)
      //
      java.util.Set<String> explicitNames =
          explicitParameterNames(parameters, presentationDataContext.getVariables());
      applyPresentationParameterDefaults(
          presentationDataContext.getVariables(), explicitNames);
      applyParameterMappings(presentationDataContext, parameters);
      applyParametersToContext(parameters, presentationDataContext);

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
      HMetricsUtil.stop(
          log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
      log.logBasic("Finished layout of presentation");
      if (trace != null && !trace.isNoop()) {
        trace.setLayoutMs(System.currentTimeMillis() - layoutStart);
        if (results.getRenderPages() != null) {
          trace.setPageCount(results.getRenderPages().size());
        }
      }
    }
  }

  /**
   * Apply declared presentation parameter defaults when the name is not supplied by the layout
   * caller and the variable is still empty.
   *
   * @return number of variables set from definitions
   */
  public int applyPresentationParameterDefaults(
      IVariables variables, java.util.Set<String> explicitParameterNames) {
    if (variables == null || parameters == null || parameters.isEmpty()) {
      return 0;
    }
    java.util.Set<String> explicit =
        explicitParameterNames != null
            ? explicitParameterNames
            : java.util.Collections.emptySet();
    int set = 0;
    for (HParameterDefinition def : parameters) {
      if (def == null || StringUtils.isEmpty(def.getName())) {
        continue;
      }
      String name = variables.resolve(def.getName());
      if (StringUtils.isEmpty(name) || explicit.contains(name)) {
        continue;
      }
      String existing = variables.getVariable(name);
      if (StringUtils.isNotEmpty(existing)) {
        continue;
      }
      String defVal = def.getDefaultValue();
      if (defVal == null) {
        defVal = "";
      }
      variables.setVariable(name, variables.resolve(defVal));
      set++;
    }
    return set;
  }

  /** @see #applyPresentationParameterDefaults(IVariables, java.util.Set) */
  public int applyPresentationParameterDefaults(IVariables variables) {
    return applyPresentationParameterDefaults(variables, java.util.Collections.emptySet());
  }

  /**
   * Parameter definition names in declaration order (for editors / prompts).
   */
  @JsonIgnore
  public List<String> listParameterDefinitionNames() {
    List<String> names = new ArrayList<>();
    if (parameters == null) {
      return names;
    }
    for (HParameterDefinition def : parameters) {
      if (def != null && StringUtils.isNotEmpty(def.getName())) {
        names.add(def.getName());
      }
    }
    return names;
  }

  /**
   * Apply mapping defaults (preview) and connector field→parameter values.
   *
   * <p>Does not overwrite request/interaction parameter names. Caller applies request parameters
   * after this method so they always win.
   *
   * <ol>
   *   <li>Mapping {@code defaultValue} when still empty (editor preview)
   *   <li>Connector field values — may overwrite mapping defaults; multi-row with a blank separator
   *       is skipped so unresolved {@code ${PARAM}} stays visible when no default was set
   * </ol>
   */
  private void applyParameterMappings(
      PresentationDataContext presentationDataContext, List<HParameter> requestParameters)
      throws HException {
    IVariables variables = presentationDataContext.getVariables();
    java.util.Set<String> explicitNames = explicitParameterNames(requestParameters, variables);

    for (HParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping == null) {
        continue;
      }
      // Mapping-level defaults when the parameter was not provided by the caller.
      parameterMapping.applyDefaults(variables, explicitNames);

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

        // Never clobber request/interaction parameters with connector field data.
        if (explicitNames.contains(parameterName)) {
          continue;
        }

        // Multi-row result with no join separator: do not invent a concatenated value.
        // Leave the parameter alone so either the default stays (preview) or ${PARAM} remains
        // unresolved when neither a request value nor a default was provided.
        if ((rows == null || rows.size() > 1) && StringUtils.isEmpty(separator)) {
          continue;
        }

        // Flatten rows: single row, or multi-row joined with separator.
        for (RowMetaAndData row : rows) {
          try {
            String value = row.getString(fieldName, "");
            String totalValue = parametersMap.get(parameterName);
            if (totalValue == null) {
              totalValue = value;
            } else {
              totalValue = totalValue + Const.NVL(separator, "") + value;
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

      // Connector field values overwrite mapping defaults (not request params).
      parametersMap
          .keySet()
          .forEach(
              parameterName -> {
                String parameterValue = parametersMap.get(parameterName);
                presentationDataContext.getVariables().setVariable(parameterName, parameterValue);
              });
    }
  }

  /** Parameter names supplied to layout (request/interaction), after variable resolution. */
  private static java.util.Set<String> explicitParameterNames(
      List<HParameter> parameters, IVariables variables) {
    java.util.Set<String> names = new java.util.HashSet<>();
    if (parameters == null || variables == null) {
      return names;
    }
    for (HParameter p : parameters) {
      if (p == null || StringUtils.isEmpty(p.getParameterName())) {
        continue;
      }
      String name = variables.resolve(p.getParameterName());
      if (StringUtils.isNotEmpty(name)) {
        names.add(name);
      }
    }
    return names;
  }

  private void applyParametersToContext(
      List<HParameter> parameters, PresentationDataContext presentationDataContext) {
    if (parameters == null) {
      return;
    }
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
    HMetricsUtil.start(
        log, HMetricsUtil.CODE_PRESENTATION_RENDER, "Presentation render");

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
        HTheme defaultTheme =
            resolveDefaultTheme(metadataProvider, presentationRenderContext.getColorMode());
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
      HMetricsUtil.stop(
          log, HMetricsUtil.CODE_PRESENTATION_RENDER, "Presentation render");
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
   *
   * <p>When the layout cache is enabled and a matching single-part snapshot exists (same component
   * metadata, page frame, parameters, dependency geometries, and connector data fingerprint),
   * skips process/layout and replays the snapshot.
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
    String compName = hopperComponent.getName();
    HMetricsUtil.start(
        log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Layout component", compName);
    try {
      // Leave blank/empty themeName alone so renderContext.lookupTheme uses the presentation
      // theme for the active color mode (defaultThemeName vs darkThemeName). Stamping the light
      // defaultThemeName here forced light ink/lines while only the page background went dark.
      component.setLogChannel(log);

      if (tryReplayLayoutCache(log, page, hopperComponent, dataContext, results)) {
        return;
      }

      // Count layout results before so we can capture only this component's parts
      int beforeCount = countLayoutResultsFor(results, hopperComponent.getName());

      component.processSourceData(this, page, hopperComponent, dataContext, renderContext, results);
      component.doLayout(this, page, hopperComponent, dataContext, renderContext, results);

      maybeStoreLayoutCache(page, hopperComponent, dataContext, results, beforeCount);
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
    } finally {
      HMetricsUtil.stop(
          log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Layout component", compName);
    }
  }

  private boolean tryReplayLayoutCache(
      ILogChannel log,
      HPage page,
      HComponent hopperComponent,
      IDataContext dataContext,
      HLayoutResults results) {
    if (!org.hopper.presentation.layout.HLayoutCacheSettings.isEnabled()) {
      return false;
    }
    String compName = hopperComponent != null ? hopperComponent.getName() : null;
    HMetricsUtil.start(
        log, HMetricsUtil.CODE_LAYOUT_CACHE_LOOKUP, "Layout cache lookup", compName);
    try {
      String fingerprint = buildLayoutFingerprint(page, hopperComponent, dataContext, results);
      if (fingerprint == null) {
        return false;
      }
      String presName =
          results.getPresentationName() != null ? results.getPresentationName() : getName();
      org.hopper.presentation.layout.HComponentLayoutSnapshot snap =
          org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
              .get(presName, hopperComponent.getName(), fingerprint);
      if (snap == null) {
        return false;
      }
      try {
        snap.replay(results, page, hopperComponent);
        if (log != null && log.isDetailed()) {
          log.logDetailed(
              "layout-cache HIT component='" + hopperComponent.getName() + "' presentation='"
                  + presName + "'");
        }
        return true;
      } catch (Exception e) {
        if (log != null) {
          log.logError(
              "layout-cache replay failed for '" + hopperComponent.getName() + "', recomputing", e);
        }
        return false;
      }
    } finally {
      HMetricsUtil.stop(
          log, HMetricsUtil.CODE_LAYOUT_CACHE_LOOKUP, "Layout cache lookup", compName);
    }
  }

  private void maybeStoreLayoutCache(
      HPage page,
      HComponent hopperComponent,
      IDataContext dataContext,
      HLayoutResults results,
      int beforeCount) {
    if (!org.hopper.presentation.layout.HLayoutCacheSettings.isEnabled()) {
      return;
    }
    List<org.hopper.presentation.HComponentLayoutResult> parts =
        layoutResultsAfter(results, hopperComponent.getName(), beforeCount);
    // v1: single-part only (no multi-page tables/crosstabs)
    if (parts.size() != 1) {
      return;
    }
    String fingerprint = buildLayoutFingerprint(page, hopperComponent, dataContext, results);
    if (fingerprint == null) {
      return;
    }
    String dataFp = computeDataFingerprint(hopperComponent, dataContext);
    org.hopper.presentation.layout.HComponentLayoutSnapshot snap =
        org.hopper.presentation.layout.HComponentLayoutSnapshot.capture(
            fingerprint, dataFp, results, hopperComponent, parts);
    if (snap == null) {
      return;
    }
    String presName =
        results.getPresentationName() != null ? results.getPresentationName() : getName();
    org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
        .put(presName, hopperComponent.getName(), fingerprint, snap);
  }

  private String buildLayoutFingerprint(
      HPage page,
      HComponent hopperComponent,
      IDataContext dataContext,
      HLayoutResults results) {
    try {
      String content =
          org.hopper.presentation.layout.HLayoutFingerprint.componentContent(
              hopperComponent, dataContext != null ? dataContext.getLogChannel() : null);
      String pageFrame = org.hopper.presentation.layout.HLayoutFingerprint.pageFrame(page);
      String params =
          results.getParametersFingerprint() != null
              ? results.getParametersFingerprint()
              : "no-params";
      String theme = defaultThemeName != null ? defaultThemeName : "";
      String darkTheme = darkThemeName != null ? darkThemeName : "";
      String colorMode =
          results.getColorMode() != null ? results.getColorMode() : "light";
      String dataFp = computeDataFingerprint(hopperComponent, dataContext);
      java.util.LinkedHashMap<String, org.hopper.core.HGeometry> deps =
          new java.util.LinkedHashMap<>();
      if (hopperComponent.getLayout() != null) {
        for (String ref :
            hopperComponent.getLayout().getReferencedLayoutComponentNames()) {
          if (ref == null) {
            continue;
          }
          org.hopper.core.HGeometry g = results.findFirstGeometry(ref);
          if (g != null) {
            deps.put(ref, g);
          } else {
            deps.put(ref, null);
          }
        }
      }
      String depsFp = org.hopper.presentation.layout.HLayoutFingerprint.dependencies(deps);
      return org.hopper.presentation.layout.HLayoutFingerprint.combine(
          content, pageFrame, params, theme, darkTheme, colorMode, dataFp, depsFp);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Data-side key for the layout cache. Must be cheap and stable: it runs for every component on
   * every soft-reload, and lookup/store must use the same key whether connector rows are in memory
   * yet or not.
   *
   * <p>Never streams or hashes connector rows here. Previously this re-ran SQL and hashed every
   * cell for each component, which made move/nudge multi-second on large sources. Data changes are
   * covered by connector/theme/DB metadata saves (layout-cache invalidate-all) and Refresh.
   */
  private String computeDataFingerprint(HComponent hopperComponent, IDataContext dataContext) {
    IHComponent plugin = hopperComponent.getComponent();
    if (plugin == null) {
      return "static";
    }
    String source = plugin.getSourceConnectorName();
    if (StringUtils.isBlank(source)) {
      return "static";
    }
    return "source:" + source.trim();
  }

  private static int countLayoutResultsFor(HLayoutResults results, String componentName) {
    if (results == null || results.getRenderPages() == null || componentName == null) {
      return 0;
    }
    int n = 0;
    for (org.hopper.presentation.layout.HRenderPage rp : results.getRenderPages()) {
      if (rp.getLayoutResults() == null) {
        continue;
      }
      for (org.hopper.presentation.HComponentLayoutResult lr : rp.getLayoutResults()) {
        if (lr.getComponent() != null && componentName.equals(lr.getComponent().getName())) {
          n++;
        }
      }
    }
    return n;
  }

  private static List<org.hopper.presentation.HComponentLayoutResult> layoutResultsAfter(
      HLayoutResults results, String componentName, int beforeCount) {
    List<org.hopper.presentation.HComponentLayoutResult> all = new ArrayList<>();
    if (results == null || results.getRenderPages() == null || componentName == null) {
      return all;
    }
    for (org.hopper.presentation.layout.HRenderPage rp : results.getRenderPages()) {
      if (rp.getLayoutResults() == null) {
        continue;
      }
      for (org.hopper.presentation.HComponentLayoutResult lr : rp.getLayoutResults()) {
        if (lr.getComponent() != null && componentName.equals(lr.getComponent().getName())) {
          all.add(lr);
        }
      }
    }
    if (beforeCount <= 0 || beforeCount >= all.size()) {
      return all;
    }
    return new ArrayList<>(all.subList(beforeCount, all.size()));
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
    return resolveDefaultTheme(metadataProvider, org.hopper.core.HColorMode.LIGHT);
  }

  /**
   * Resolve the presentation's theme for the given color mode (light catalog theme, dark catalog
   * theme, or auto-derived dark variant).
   *
   * <p>Dark mode resolution order:
   *
   * <ol>
   *   <li>Presentation {@code darkThemeName} if set and loadable
   *   <li>Catalog {@link org.hopper.core.Constants#DEFAULT_DARK_THEME_NAME} ("Default Dark") when
   *       present — so theme-editor changes to Default Dark apply without every presentation
   *       setting {@code darkThemeName}
   *   <li>{@code defaultThemeName + " Dark"} companion name if present
   *   <li>Auto-derived dark variant of the light theme ({@link
   *       org.hopper.presentation.theme.HThemeAdapt#forDarkMode})
   * </ol>
   */
  public HTheme resolveDefaultTheme(
      IHopMetadataProvider metadataProvider, org.hopper.core.HColorMode colorMode) {
    org.hopper.core.HColorMode mode =
        colorMode != null ? colorMode : org.hopper.core.HColorMode.LIGHT;
    if (mode == org.hopper.core.HColorMode.DARK) {
      if (StringUtils.isNotEmpty(darkThemeName) && metadataProvider != null) {
        HTheme theme = tryLoadTheme(metadataProvider, darkThemeName);
        if (theme != null) {
          return theme;
        }
      }
      // Prefer authored catalog dark theme over auto-derive (header bg / fonts live there)
      if (metadataProvider != null) {
        HTheme catalogDark =
            tryLoadTheme(metadataProvider, org.hopper.core.Constants.DEFAULT_DARK_THEME_NAME);
        if (catalogDark != null) {
          return catalogDark;
        }
        if (StringUtils.isNotEmpty(defaultThemeName)
            && !org.hopper.core.Constants.DEFAULT_THEME_NAME.equalsIgnoreCase(defaultThemeName)) {
          HTheme companion =
              tryLoadTheme(metadataProvider, defaultThemeName.trim() + " Dark");
          if (companion != null) {
            return companion;
          }
        }
      }
      // Derive from light theme when no catalog dark theme is available
      HTheme light = resolveDefaultTheme(metadataProvider, org.hopper.core.HColorMode.LIGHT);
      return org.hopper.presentation.theme.HThemeAdapt.forDarkMode(light);
    }
    if (StringUtils.isNotEmpty(defaultThemeName) && metadataProvider != null) {
      HTheme theme = tryLoadTheme(metadataProvider, defaultThemeName);
      if (theme != null) {
        return theme;
      }
    }
    return HTheme.getDefault();
  }

  /** Load a theme by catalog name; null if missing or not renderable. */
  private static HTheme tryLoadTheme(IHopMetadataProvider metadataProvider, String name) {
    if (metadataProvider == null || StringUtils.isBlank(name)) {
      return null;
    }
    try {
      HTheme theme = metadataProvider.getSerializer(HTheme.class).load(name.trim());
      theme = normalizeLoadedTheme(theme, name.trim());
      if (theme != null && theme.isRenderable()) {
        return theme;
      }
    } catch (Exception e) {
      // missing / unreadable
    }
    return null;
  }

  /**
   * Hop JSON load does not always stamp the catalog key onto {@code name}. Empty/half-parsed
   * objects are treated as unusable by callers via {@link HTheme#isRenderable()}.
   */
  private static HTheme normalizeLoadedTheme(HTheme theme, String catalogName) {
    if (theme == null) {
      return null;
    }
    if (StringUtils.isBlank(theme.getName()) && StringUtils.isNotBlank(catalogName)) {
      theme.setName(catalogName);
    }
    return theme;
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
