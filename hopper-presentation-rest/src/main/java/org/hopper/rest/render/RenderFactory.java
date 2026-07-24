package org.hopper.rest.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.audit.lineage.HUsageAudit;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.GuiFormHtmlRenderer;
import org.hopper.core.gui.form.GuiFormSchema;
import org.hopper.core.gui.form.GuiFormSchemaBuilder;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.rest.HRest;
import org.hopper.rest.render.svg.PresentationSvgRendering;

public class RenderFactory {
  public static final IRendering renderPresentation(
      ILoggingObject parent,
      IHopMetadataProvider metadataProvider,
      HPresentation presentation,
      List<HParameter> parameters)
      throws HException {
    return renderPresentation(parent, metadataProvider, presentation, parameters, null);
  }

  public static final IRendering renderPresentation(
      ILoggingObject parent,
      IHopMetadataProvider metadataProvider,
      HPresentation presentation,
      List<HParameter> parameters,
      org.hopper.core.HColorMode colorMode)
      throws HException {

    HExecutionTrace trace = HExecutionTrace.create();
    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadataProvider);
    if (colorMode != null) {
      renderContext.setColorMode(colorMode);
    }
    long renderStart = 0L;
    try {
      HLayoutResults layoutResults =
          presentation.doLayout(parent, renderContext, metadataProvider, parameters, trace);
      renderStart = System.currentTimeMillis();
      presentation.render(layoutResults, metadataProvider, renderContext);
      if (!trace.isNoop()) {
        trace.setRenderMs(System.currentTimeMillis() - renderStart);
        if (layoutResults.getRenderPages() != null) {
          trace.setPageCount(layoutResults.getRenderPages().size());
        }
        trace.finishSuccess();
      }

      PresentationSvgRendering rendering = new PresentationSvgRendering();
      rendering.setPresentation(presentation);
      rendering.setLayoutResults(layoutResults);
      rendering.setParameters(parameters);
      rendering.setPresentationName(presentation.getName());

      HAuditEmitter.getInstance()
          .emitSafely(
              HUsageAudit.presentationRender(
                  presentation, parameters, trace, rendering.getId()));
      return rendering;
    } catch (HException | RuntimeException e) {
      if (!trace.isNoop() && trace.getFinishedAt() == null) {
        if (renderStart > 0) {
          trace.setRenderMs(System.currentTimeMillis() - renderStart);
        }
        trace.finishFailure(e);
        HAuditEmitter.getInstance()
            .emitSafely(HUsageAudit.presentationRender(presentation, parameters, trace, null));
      }
      throw e;
    }
  }

  public static Response renderPage(IRendering rendering, HRenderPage page, String renderType)
      throws HException {
    switch (renderType.toUpperCase()) {
      case "SVG":
        return renderPageSvg(rendering, page);
      case "HTML":
        return renderPageHtml(rendering, page);
      default:
        return Response.serverError()
            .entity("Render type " + renderType + " is not supported yet.")
            .build();
    }
  }

  private static Response renderPageSvg(IRendering rendering, HRenderPage page)
      throws HException {
    HRest.getInstance()
        .getLog()
        .logBasic(
            "SVG image rendering page "
                + page.getPageNumber()
                + " of rendering "
                + rendering.getId());
    return Response.ok().entity(page.getSvgXml()).encoding("UTF-8").type("image/svg+xml").build();
  }

  private static Response renderPageHtml(IRendering rendering, HRenderPage renderPage) {
    return renderPageHtml(rendering, renderPage, "view");
  }

  /**
   * Render the HTML shell for a presentation page.
   *
   * @param mode {@code view} or {@code edit} — selects template and client mode constant
   */
  public static Response renderPageHtml(
      IRendering rendering, HRenderPage renderPage, String mode) {
    HRest.getInstance()
        .getLog()
        .logBasic(
            "HTML rendering page "
                + renderPage.getPageNumber()
                + " of rendering "
                + rendering.getId()
                + " mode="
                + mode);

    HLayoutResults layoutResults = rendering.getLayoutResults();
    boolean edit = mode != null && mode.equalsIgnoreCase("edit");
    String templateFileName = edit ? "handle-presentation-edit.html" : "handle-presentation.html";
    String html = "";

    try (InputStream inputStream =
        rendering.getClass().getClassLoader().getResourceAsStream(templateFileName)) {
      if (inputStream == null) {
        throw new HException("Template not found on classpath: " + templateFileName);
      }
      html = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

      // Page number is 1-based in layout results.
      html = html.replace("%PAGE_COUNT%", "" + layoutResults.getRenderPages().size());
      html = html.replace("%PAGE_NUMBER_0%", "" + (renderPage.getPageNumber() - 1));
      html = html.replace("%PAGE_NUMBER%", "" + renderPage.getPageNumber());
      html = html.replace("%PRESENTATION_NAME%", rendering.getPresentationName());
      html = html.replace("%RENDER_ID%", rendering.getId());
      html = html.replace("%HOPPER_MODE%", edit ? "edit" : "view");

      int autoRefresh = 0;
      if (rendering.getPresentation() != null
          && rendering.getPresentation().getAutoRefreshSeconds() != null
          && rendering.getPresentation().getAutoRefreshSeconds() > 0) {
        autoRefresh = rendering.getPresentation().getAutoRefreshSeconds();
      }
      html = html.replace("%AUTO_REFRESH_SECONDS%", Integer.toString(autoRefresh));

      String parametersJson = new ObjectMapper().writeValueAsString(rendering.getParameters());
      html = html.replace("%PARAMETER_VALUES%", "" + parametersJson);

      // charset on Content-Type (not Response.encoding, which is Content-Encoding)
      return Response.ok().entity(html).type("text/html; charset=UTF-8").build();
    } catch (Exception e) {
      String errorMessage = "Error reading HTML template file " + templateFileName;
      HRest.getInstance().getLog().logError(errorMessage, e);
      return Response.serverError().entity(errorMessage + "\n" + Const.getStackTracker(e)).build();
    }
  }

  public static Response getMainPage(Object object) throws HException {
    String filename = "home-page.html";
    try {
      try (InputStream inputStream =
          object.getClass().getClassLoader().getResourceAsStream(filename)) {
        if (inputStream == null) {
          throw new HException("Unable to find file " + filename);
        }
        String html = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return Response.ok().entity(html).type("text/html; charset=UTF-8").build();
      }
    } catch (Exception e) {
      String errorMessage = "Error reading home page HTML file: " + filename;
      HRest.getInstance().getLog().logError(errorMessage, e);
      return Response.serverError().entity(errorMessage + "\n" + Const.getStackTracker(e)).build();
    }
  }

  /**
   * HTML editor page for a component plugin, generated from {@code @HWidgetElement} annotations.
   */
  public static Response getComponentPluginPage(Object object, String componentId)
      throws HException {
    try {
      GuiFormSchema schema = new GuiFormSchemaBuilder().buildComponentSchema(componentId);
      String html = new GuiFormHtmlRenderer().render(schema);
      return Response.ok().entity(html).type("text/html; charset=UTF-8").build();
    } catch (Exception e) {
      String errorMessage = "Error building form for component plugin: " + componentId;
      HRest.getInstance().getLog().logError(errorMessage, e);
      return Response.serverError().entity(errorMessage + "\n" + Const.getStackTracker(e)).build();
    }
  }

  /** JSON form schema for a component plugin (annotation-driven). */
  public static Response getComponentPluginSchema(String componentId) throws HException {
    try {
      GuiFormSchema schema = new GuiFormSchemaBuilder().buildComponentSchema(componentId);
      String json = new ObjectMapper().writeValueAsString(schema);
      return Response.ok().entity(json).type("application/json; charset=UTF-8").build();
    } catch (Exception e) {
      String errorMessage = "Error building form schema for component: " + componentId;
      HRest.getInstance().getLog().logError(errorMessage, e);
      return Response.serverError().entity(errorMessage + "\n" + Const.getStackTracker(e)).build();
    }
  }

  /** JSON form schema for a connector plugin (annotation-driven). */
  public static Response getConnectorPluginSchema(String connectorId) throws HException {
    try {
      GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema(connectorId);
      String json = new ObjectMapper().writeValueAsString(schema);
      return Response.ok().entity(json).type("application/json; charset=UTF-8").build();
    } catch (Exception e) {
      String errorMessage = "Error building form schema for connector: " + connectorId;
      HRest.getInstance().getLog().logError(errorMessage, e);
      return Response.serverError().entity(errorMessage + "\n" + Const.getStackTracker(e)).build();
    }
  }

  /** HTML editor page for a connector plugin type (annotation-driven). */
  public static Response getConnectorPluginPage(String connectorPluginId) throws HException {
    try {
      GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema(connectorPluginId);
      String html = new GuiFormHtmlRenderer().renderConnector(schema);
      return Response.ok().entity(html).type("text/html; charset=UTF-8").build();
    } catch (Exception e) {
      String errorMessage =
          "Error building connector edit HTML for plugin: " + connectorPluginId;
      HRest.getInstance().getLog().logError(errorMessage, e);
      return Response.serverError().entity(errorMessage + "\n" + Const.getStackTracker(e)).build();
    }
  }
}
