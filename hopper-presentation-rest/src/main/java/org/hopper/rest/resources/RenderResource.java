package org.hopper.rest.resources;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.rest.interaction.InteractionLookupResult;
import org.hopper.rest.render.IRendering;
import org.hopper.rest.render.RenderFactory;
import org.hopper.rest.resources.requests.ActionsRequest;
import org.hopper.rest.resources.requests.ConnectorDescriptionRequest;
import org.hopper.rest.resources.requests.PdfExportRequest;
import org.hopper.rest.resources.requests.RenderPresentationRequest;
import org.hopper.rest.resources.responses.RowMetaResponse;
import org.hopper.render.pdf.HPdfPaper;
import org.hopper.render.pdf.HSvgPdfExporter;
import org.hopper.rest.security.HRenderSession;
import org.hopper.core.history.UserHistoryUtil;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HPrincipal;
import org.hopper.security.HResourceRef;
import org.hopper.security.HSecurityContext;
import org.json.simple.JSONObject;

@Path("render/")
public class RenderResource extends BaseResource {

  @Context private HttpHeaders httpHeaders;
  @Context private UriInfo uriInfo;
  /**
   * Conveniently renders a main presentation
   *
   * @return The min page of this application
   */
  @GET
  @Path("/main/")
  public Response getMainPage() {
    try {
      return RenderFactory.getMainPage(this);
    } catch (Exception e) {
      String errorMessage = "Unexpected error retrieving the main page";
      return getServerError(errorMessage, e);
    }
  }

  /**
   * Render returns a unique ID to a rendering. The rendering contains the layout results, the SVG,
   * the drawn areas and everything else that is needed to visualize a presentation for the
   * requested render type.
   *
   * @param request The rendering details like parameters, and so on.
   * @return The response in the form of the UUID of the rendering.
   */
  @POST
  @Path("/presentation")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response renderPresentation(RenderPresentationRequest request) {
    try {
      String sessionId = HRenderSession.resolve(httpHeaders);
      String presentationName = request != null ? request.getPresentationName() : null;
      if (presentationName != null && !presentationName.isBlank()) {
        HSecurityContext.checkResource(
            HAction.PRESENTATION_RENDER, HResourceRef.presentation(presentationName));
      }

      org.hopper.core.HColorMode mode =
          org.hopper.core.HColorMode.fromString(
              request != null ? request.getColorMode() : null);
      List<org.hopper.presentation.variable.HParameter> params =
          request != null && request.getParameters() != null
              ? request.getParameters()
              : Collections.emptyList();
      boolean reload = request != null && request.isReload();
      org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions contOpts =
          continuousOptionsFromRequest(request);

      IRendering rendering =
          hopperRest.resolveOrBuildForSession(
              sessionId, presentationName, params, mode, reload, contOpts);
      if (rendering == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("Presentation not found: " + presentationName)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }

      // UX recent-history (not compliance audit)
      try {
        HPrincipal principal = HSecurityContext.getPrincipal();
        String user =
            principal != null && !principal.isAnonymous()
                ? principal.getUsername()
                : "anonymous";
        if (presentationName != null && !presentationName.isBlank()) {
          UserHistoryUtil.addUserHistoryAction(
              hopperRest.getMetadataProvider(), user, "Presentation", presentationName);
        }
      } catch (Exception historyError) {
        hopperRest
            .getLog()
            .logBasic("Could not update user history: " + historyError.getMessage());
      }

      Response.ResponseBuilder rb =
          Response.ok().entity(rendering.getId()).type(MediaType.TEXT_PLAIN);
      return withGuestCookie(rb).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error rendering presentation '"
              + (request != null ? request.getPresentationName() : "?")
              + "'";
      return getServerError(errorMessage, e);
    }
  }

  /**
   * Bookmarkable view URL by presentation name. Resolves or rebuilds a session-owned rendering
   * (survives cache clear / restart for that browser session). Missing presentation → main page.
   */
  @GET
  @Path("/p/{presentationName}/{renderType}/{pageNumber}/")
  public Response getRenderPageByName(
      @PathParam("presentationName") String presentationName,
      @PathParam("renderType") String renderType,
      @PathParam("pageNumber") int pageNumber,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("reload") @DefaultValue("false") boolean reload,
      @QueryParam("viewportWidth") Integer viewportWidth,
      @QueryParam("layoutMode") String layoutMode) {
    try {
      String sessionId = HRenderSession.resolve(httpHeaders);
      String name = presentationName != null ? presentationName.trim() : "";
      if (name.isEmpty()) {
        return redirectToMain();
      }

      HSecurityContext.checkResource(
          HAction.PRESENTATION_RENDER, HResourceRef.presentation(name));

      org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);
      org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions contOpts =
          continuousOptionsFrom(layoutMode, viewportWidth);
      IRendering rendering =
          hopperRest.resolveOrBuildForSession(
              sessionId, name, Collections.emptyList(), mode, reload, contOpts);
      if (rendering == null) {
        return redirectToMain();
      }

      List<HRenderPage> pages = rendering.getLayoutResults().getRenderPages();
      if (pages == null || pages.isEmpty()) {
        return redirectToMain();
      }
      int page0 = pageNumber;
      if (page0 < 0) {
        page0 = 0;
      }
      if (page0 >= pages.size()) {
        page0 = pages.size() - 1;
      }

      // UX recent-history
      try {
        HPrincipal principal = HSecurityContext.getPrincipal();
        String user =
            principal != null && !principal.isAnonymous()
                ? principal.getUsername()
                : "anonymous";
        UserHistoryUtil.addUserHistoryAction(
            hopperRest.getMetadataProvider(), user, "Presentation", name);
      } catch (Exception ignored) {
        // best effort
      }

      Response pageResponse =
          RenderFactory.renderPage(rendering, pages.get(page0), renderType);
      return attachGuestCookie(pageResponse);
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      hopperRest
          .getLog()
          .logError("Error serving name-based render for '" + presentationName + "'", e);
      return redirectToMain();
    }
  }

  private Response redirectToMain() {
    URI main =
        uriInfo != null
            ? uriInfo.getBaseUriBuilder().path("render/main/").build()
            : URI.create("/hopper/api/render/main/");
    Response.ResponseBuilder rb = Response.seeOther(main);
    return withGuestCookie(rb).build();
  }

  private Response.ResponseBuilder withGuestCookie(Response.ResponseBuilder rb) {
    NewCookie cookie = HRenderSession.newGuestCookieIfCreated();
    if (cookie != null) {
      rb.cookie(cookie);
    }
    return rb;
  }

  private Response attachGuestCookie(Response response) {
    NewCookie cookie = HRenderSession.newGuestCookieIfCreated();
    if (cookie == null) {
      return response;
    }
    return Response.fromResponse(response).cookie(cookie).build();
  }

  /**
   * Get the amount of rendered pages.
   *
   * @param renderId The rendering ID
   * @return The page count response
   */
  @GET
  @Path("/info/pages/{renderId}")
  public Response getPageCount(
      @PathParam("renderId") String renderId,
      @QueryParam("presentationName") String presentationName,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("layoutMode") String layoutMode,
      @QueryParam("viewportWidth") Integer viewportWidth) {
    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering =
          findOrRebuildRendering(renderId, presentationName, colorMode, layoutMode, viewportWidth);
      if (rendering == null) {
        return renderingGone(renderId, "page count");
      }
      int pageCount = rendering.getLayoutResults().getRenderPages().size();
      return withRenderIdHeader(Response.ok().entity(pageCount), rendering).build();
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error retrieving the number of pages for render ID " + renderId;
      return getServerError(errorMessage, e);
    }
  }

  /**
   * Soft re-render for view mode (continuous viewport resize, auto-refresh, theme switch). Returns
   * JSON with new renderId, continuous metrics, and optional inline page PNG — no full HTML
   * navigation.
   */
  @POST
  @Path("/presentation/soft")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response softRenderPresentation(RenderPresentationRequest request) {
    try {
      String sessionId = HRenderSession.resolve(httpHeaders);
      String presentationName = request != null ? request.getPresentationName() : null;
      if (presentationName == null || presentationName.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"presentationName required\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
      HSecurityContext.checkResource(
          HAction.PRESENTATION_RENDER, HResourceRef.presentation(presentationName));

      org.hopper.core.HColorMode mode =
          org.hopper.core.HColorMode.fromString(
              request != null ? request.getColorMode() : null);
      List<org.hopper.presentation.variable.HParameter> params =
          request != null && request.getParameters() != null
              ? request.getParameters()
              : Collections.emptyList();
      org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions contOpts =
          continuousOptionsFromRequest(request);

      // Always rebuild so viewport/layoutMode changes take effect
      IRendering existing =
          hopperRest.findRenderingForSession(
              sessionId,
              presentationName,
              params,
              mode != null ? mode.wireValue() : "light",
              contOpts != null ? contOpts.continuousScroll() : null,
              contOpts != null ? contOpts.viewportWidth() : 0);
      if (existing != null) {
        hopperRest.removeRendering(existing);
      }

      IRendering rendering =
          hopperRest.resolveOrBuildForSession(
              sessionId, presentationName, params, mode, true, contOpts);
      if (rendering == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("{\"error\":\"Presentation not found: " + presentationName + "\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();
      }

      Map<String, Object> body = softRenderBody(rendering, 0, true);
      Response.ResponseBuilder rb =
          Response.ok().entity(body).type(MediaType.APPLICATION_JSON);
      return withGuestCookie(rb).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error soft-rendering presentation '"
              + (request != null ? request.getPresentationName() : "?")
              + "'";
      return getServerError(errorMessage, e);
    }
  }

  /**
   * Layout/info snapshot for a render id (page count, continuous metrics). Used by the continuous
   * view shell after soft-reload.
   */
  @GET
  @Path("/info/layout/{renderId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getLayoutInfo(
      @PathParam("renderId") String renderId,
      @QueryParam("presentationName") String presentationName,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("layoutMode") String layoutMode,
      @QueryParam("viewportWidth") Integer viewportWidth) {
    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering =
          findOrRebuildRendering(renderId, presentationName, colorMode, layoutMode, viewportWidth);
      if (rendering == null) {
        return renderingGone(renderId, "layout info");
      }
      return withRenderIdHeader(Response.ok().entity(softRenderBody(rendering, 0, false)), rendering)
          .build();
    } catch (Exception e) {
      return getServerError(
          "Unexpected error retrieving layout info for render ID " + renderId, e);
    }
  }

  /**
   * Export presentation as multi-page PDF.
   *
   * <p>Paginated session: pass {@code renderId} + {@code useSessionLayout:true} to export current
   * pages. Continuous (or re-paper): re-layout as paginated for the chosen paper size, then export.
   */
  @POST
  @Path("/export/pdf")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces("application/pdf")
  public Response exportPdf(PdfExportRequest request) {
    try {
      HRenderSession.resolve(httpHeaders);
      if (request == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Request body required")
            .type(MediaType.TEXT_PLAIN)
            .build();
      }

      String presentationName = request.getPresentationName();
      if ((presentationName == null || presentationName.isBlank())
          && request.getRenderId() != null
          && !request.getRenderId().isBlank()) {
        IRendering session = findRenderingOrNull(request.getRenderId());
        if (session != null) {
          presentationName = session.getPresentationName();
        }
      }
      if (presentationName == null || presentationName.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("presentationName or renderId required")
            .type(MediaType.TEXT_PLAIN)
            .build();
      }

      HSecurityContext.checkResource(
          HAction.PRESENTATION_RENDER, HResourceRef.presentation(presentationName));

      byte[] pdf;
      String filenameBase = sanitizeFilename(presentationName);

      // Fast path: reuse session paginated layout
      if (request.isUseSessionLayout()
          && request.getRenderId() != null
          && !request.getRenderId().isBlank()) {
        IRendering session = findRenderingOrNull(request.getRenderId());
        if (session != null
            && session.getLayoutResults() != null
            && !session.isContinuousScroll()
            && (session.getLayoutResults().isContinuousScroll() == false)
            && isCurrentPaper(request)) {
          pdf = HSvgPdfExporter.fromLayoutResults(session.getLayoutResults());
          return pdfAttachmentResponse(pdf, filenameBase);
        }
      }

      // Re-layout as paginated for paper size (always for continuous / paper override)
      HPresentation source = hopperRest.loadPresentation(presentationName);
      if (source == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("Presentation not found: " + presentationName)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      HPresentation export = new HPresentation(source);
      export.setName(source.getName());
      export.setLayoutMode(
          org.hopper.presentation.layout.HLayoutMode.PAGINATED.wireValue());

      HPage paper = resolveExportPaper(request, source);
      HPdfPaper.applyToPresentationPages(export, paper);

      org.hopper.core.HColorMode mode =
          org.hopper.core.HColorMode.fromString(request.getColorMode());
      List<org.hopper.presentation.variable.HParameter> params =
          request.getParameters() != null ? request.getParameters() : Collections.emptyList();

      // Export is always paginated: continuousScroll false, peer page breaks allowed
      IRendering rendering =
          RenderFactory.renderPresentation(
              hopperRest.getLoggingObject(),
              hopperRest.getMetadataProvider(),
              export,
              params,
              mode != null ? mode : org.hopper.core.HColorMode.LIGHT,
              true,
              false,
              RenderFactory.ContinuousLayoutOptions.of(false, null));

      pdf = HSvgPdfExporter.fromLayoutResults(rendering.getLayoutResults());
      return pdfAttachmentResponse(pdf, filenameBase);
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Unexpected error exporting PDF", e);
    }
  }

  /**
   * Convenience GET: export PDF for an existing session render id (paginated pages only).
   * Continuous sessions should use POST /export/pdf with a paper preset.
   */
  @GET
  @Path("/export/pdf/{renderId}")
  @Produces("application/pdf")
  public Response exportPdfFromSession(@PathParam("renderId") String renderId) {
    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering = findRenderingOrNull(renderId);
      if (rendering == null) {
        return renderingGone(renderId, "PDF export");
      }
      String name = rendering.getPresentationName();
      if (name != null && !name.isBlank()) {
        HSecurityContext.checkResource(
            HAction.PRESENTATION_RENDER, HResourceRef.presentation(name));
      }
      if (rendering.isContinuousScroll()
          || (rendering.getLayoutResults() != null
              && rendering.getLayoutResults().isContinuousScroll())) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                "Continuous layouts must be re-paginated for PDF. "
                    + "POST /render/export/pdf with a paper preset.")
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      byte[] pdf = HSvgPdfExporter.fromLayoutResults(rendering.getLayoutResults());
      return pdfAttachmentResponse(pdf, sanitizeFilename(name != null ? name : "presentation"));
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Unexpected error exporting PDF for render " + renderId, e);
    }
  }

  private static boolean isCurrentPaper(PdfExportRequest request) {
    if (request == null || request.getPaperPreset() == null) {
      return true;
    }
    String p = request.getPaperPreset().trim().toLowerCase();
    return p.isEmpty() || "current".equals(p) || "session".equals(p);
  }

  private static HPage resolveExportPaper(PdfExportRequest request, HPresentation source) {
    String preset =
        request.getPaperPreset() != null ? request.getPaperPreset().trim().toLowerCase() : "a4";
    // "current" → first body page size when available; else A4 landscape for continuous sources
    if ("current".equals(preset) || "session".equals(preset)) {
      if (source.getPages() != null && !source.getPages().isEmpty()) {
        HPage first = source.getPages().get(0);
        if (first != null && first.getWidth() > 0 && first.getHeight() > 0) {
          return new HPage(
              first.getWidth(),
              first.getHeight(),
              first.getLeftMargin(),
              first.getRightMargin(),
              first.getTopMargin(),
              first.getBottomMargin());
        }
      }
      // Continuous-authored pages may already be mutated to viewport size; prefer A4 landscape
      return HPdfPaper.toPage("a4", false, null, null, request.getMargin());
    }
    boolean portrait = request.getPortrait() == null || request.getPortrait().booleanValue();
    // Continuous dashboards: if client omitted orientation, landscape A4 is a better default
    if (request.getPortrait() == null && source.isContinuousLayout() && "a4".equals(preset)) {
      portrait = false;
    }
    return HPdfPaper.toPage(
        preset, portrait, request.getWidth(), request.getHeight(), request.getMargin());
  }

  private static String sanitizeFilename(String name) {
    if (name == null || name.isBlank()) {
      return "presentation";
    }
    String s = name.trim().replaceAll("[\\\\/:*?\"<>|]+", "-");
    s = s.replaceAll("\\s+", " ").trim();
    if (s.isEmpty()) {
      return "presentation";
    }
    if (s.length() > 80) {
      s = s.substring(0, 80).trim();
    }
    return s;
  }

  private static Response pdfAttachmentResponse(byte[] pdf, String filenameBase) {
    String filename = filenameBase + ".pdf";
    return Response.ok(pdf)
        .type("application/pdf")
        .header(
            "Content-Disposition",
            "attachment; filename=\"" + filename.replace("\"", "") + "\"")
        .header("Content-Length", pdf.length)
        .build();
  }

  private static org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions
      continuousOptionsFromRequest(RenderPresentationRequest request) {
    if (request == null) {
      return null;
    }
    return continuousOptionsFrom(request.getLayoutMode(), request.getViewportWidth());
  }

  private static org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions continuousOptionsFrom(
      String layoutMode, Integer viewportWidth) {
    Boolean continuous = null;
    if (layoutMode != null && !layoutMode.isBlank()) {
      continuous =
          org.hopper.presentation.layout.HLayoutMode.fromString(layoutMode).isContinuous();
    }
    if (continuous == null && (viewportWidth == null || viewportWidth <= 0)) {
      return null;
    }
    return org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions.of(
        continuous, viewportWidth);
  }

  private static Map<String, Object> softRenderBody(
      IRendering rendering, int page0, boolean includePageImage) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("renderId", rendering.getId());
    var layout = rendering.getLayoutResults();
    var pages = layout != null ? layout.getRenderPages() : null;
    int pageCount = pages != null ? pages.size() : 0;
    body.put("pageCount", pageCount);
    boolean continuous =
        rendering.isContinuousScroll()
            || (layout != null && layout.isContinuousScroll())
            || (rendering.getPresentation() != null
                && rendering.getPresentation().isContinuousLayout());
    body.put("continuousScroll", continuous);
    body.put(
        "contentWidth",
        layout != null && layout.getContentWidth() > 0
            ? layout.getContentWidth()
            : rendering.getViewportWidth());
    body.put(
        "contentHeight", layout != null ? layout.getContentHeight() : 0);
    body.put(
        "contentTruncated",
        layout != null && (layout.isContentTruncated() || layout.isPagesTruncated()));
    body.put("pagesTruncated", layout != null && layout.isPagesTruncated());
    if (page0 < 0) {
      page0 = 0;
    }
    if (pageCount > 0 && page0 >= pageCount) {
      page0 = pageCount - 1;
    }
    body.put("pageNumber0", page0);
    if (includePageImage && pages != null && page0 >= 0 && page0 < pages.size()) {
      try {
        String svg = pages.get(page0).getSvgXml();
        if (svg != null) {
          body.put("pageSvgChars", svg.length());
          try {
            float pngScale = org.hopper.render.svg.HSvgToPng.DEFAULT_PIXEL_SCALE;
            byte[] png = org.hopper.render.svg.HSvgToPng.toPngBytes(svg, pngScale);
            body.put("pagePngBase64", Base64.getEncoder().encodeToString(png));
            body.put("pagePngScale", pngScale);
            body.put("pagePngBytes", png.length);
          } catch (Exception pngEx) {
            body.put("pageSvg", svg);
          }
        }
      } catch (HException svgEx) {
        // leave body without page image; client falls back to GET SVG
      }
    }
    return body;
  }

  /**
   * Expose a page of a rendering in a certain way
   *
   * @param renderId The rendering ID
   * @param renderType The type of rendering to do: SVG, HTML, PDF, ...
   * @param pageNumber The page number
   * @return The rendered page SVG
   */
  @GET
  @Path("/page/{renderId}/{renderType}/{pageNumber}/")
  public Response getRenderPageSvg(
      @PathParam("renderId") String renderId,
      @PathParam("renderType") String renderType,
      @PathParam("pageNumber") int pageNumber,
      @QueryParam("presentationName") String presentationName,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("layoutMode") String layoutMode,
      @QueryParam("viewportWidth") Integer viewportWidth) {

    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering =
          findOrRebuildRendering(renderId, presentationName, colorMode, layoutMode, viewportWidth);
      if (rendering == null) {
        // Expected after restart / cache clear / foreign session — not a server fault
        if (renderType != null && "HTML".equalsIgnoreCase(renderType)) {
          // Prefer name-based view bookmark when we know the presentation
          if (presentationName != null && !presentationName.isBlank()) {
            return Response.seeOther(
                    URI.create(
                        "/hopper/api/render/p/"
                            + java.net.URLEncoder.encode(presentationName, java.nio.charset.StandardCharsets.UTF_8)
                                .replace("+", "%20")
                            + "/HTML/"
                            + Math.max(0, pageNumber)
                            + "/?colorMode="
                            + java.net.URLEncoder.encode(colorMode, java.nio.charset.StandardCharsets.UTF_8)))
                .build();
          }
          hopperRest
              .getLog()
              .logBasic(
                  "Render UUID unavailable ("
                      + renderId
                      + "); redirecting to main (use /render/p/{name}/… bookmarks)");
          return redirectToMain();
        }
        return renderingGone(renderId, "page " + pageNumber + " " + renderType);
      }
      HRenderPage page = lookupRenderPage(rendering, pageNumber);
      Response pageResponse = RenderFactory.renderPage(rendering, page, renderType);
      return withRenderIdHeader(Response.fromResponse(pageResponse), rendering).build();
    } catch (Exception e) {
      if (isMissingRendering(e)) {
        if (renderType != null && "HTML".equalsIgnoreCase(renderType)) {
          hopperRest
              .getLog()
              .logBasic("Render UUID unavailable (" + renderId + "); redirecting to main");
          return redirectToMain();
        }
        return renderingGone(renderId, "page " + pageNumber + " " + renderType);
      }
      String errorMessage =
          "Unexpected error retrieving page " + pageNumber + " for render ID " + renderId;
      return getServerError(errorMessage, e);
    }
  }

  /** Quiet 404 when a render id expired or belongs to another session (no ERROR stack). */
  private Response renderingGone(String renderId, String what) {
    hopperRest
        .getLog()
        .logBasic(
            "Rendering not in cache for "
                + what
                + " (id="
                + renderId
                + ") — client should open /render/p/{presentationName}/…");
    return Response.status(Response.Status.NOT_FOUND)
        .entity("Rendering not found or expired: " + renderId)
        .type(MediaType.TEXT_PLAIN)
        .build();
  }

  private static boolean isMissingRendering(Exception e) {
    if (e == null) {
      return false;
    }
    String msg = e.getMessage();
    if (msg != null && msg.contains("Unable to find rendering")) {
      return true;
    }
    Throwable c = e.getCause();
    return c instanceof Exception && isMissingRendering((Exception) c);
  }

  private HRenderPage lookupRenderPage(IRendering rendering, int pageNumber) throws HException {
    List<HRenderPage> renderPages = rendering.getLayoutResults().getRenderPages();
    if (pageNumber < 0 || pageNumber >= renderPages.size()) {
      throw new HException(
          "Invalid page number requested: "
              + pageNumber
              + ".  Available pages: "
              + renderPages.size());
    }
    return renderPages.get(pageNumber);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/lookupActions/")
  public Response lookupActions(ActionsRequest request) {
    try {
      HRenderSession.resolve(httpHeaders);
      String pname = request != null ? request.getPresentationName() : null;
      String cm = request != null ? request.getColorMode() : null;
      IRendering rendering =
          findOrRebuildRendering(
              request != null ? request.getRenderId() : null,
              pname,
              cm != null ? cm : "light",
              request != null ? request.getLayoutMode() : null,
              request != null ? request.getViewportWidth() : null);
      if (rendering == null) {
        return renderingGone(
            request != null ? request.getRenderId() : null, "lookupActions");
      }
      HRenderPage page = lookupRenderPage(rendering, request.getPageNumber());
      HPresentation presentation = rendering.getPresentation();

      org.hopper.presentation.interaction.HInteractionMethod methodFilter = null;
      if (request.getMethod() != null && !request.getMethod().isBlank()) {
        methodFilter =
            org.hopper.presentation.interaction.HInteractionMethod.fromString(request.getMethod());
      }

      InteractionLookupResult result =
          org.hopper.rest.interaction.InteractionRegionIndex.lookupAt(
              presentation, page, request.getX(), request.getY(), methodFilter);

      return withRenderIdHeader(
              Response.ok()
                  .entity(result.toJsonString())
                  .type("application/json; charset=UTF-8"),
              rendering)
          .build();
    } catch (Exception e) {
      // Don't log on the server, it can be tedious to see all the failed lookups.
      //
      String errorMessage =
          "Unexpected error retrieving the possible actions for request: " + request;
      return Response.serverError()
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(errorMessage + "\n" + Const.getSimpleStackTrace(e))
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  /**
   * All interactive hit regions for a render page (geometry + actions + hit context). Viewers
   * prefetch this once on open/page-switch for client-side hover highlight and click resolution,
   * avoiding per-mousemove {@code lookupActions} round-trips.
   */
  @GET
  @Path("/info/interaction-regions/{renderId}/{pageNumber}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getInteractionRegions(
      @PathParam("renderId") String renderId,
      @PathParam("pageNumber") int pageNumber,
      @QueryParam("presentationName") String presentationName,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("layoutMode") String layoutMode,
      @QueryParam("viewportWidth") Integer viewportWidth) {
    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering =
          findOrRebuildRendering(renderId, presentationName, colorMode, layoutMode, viewportWidth);
      if (rendering == null) {
        return renderingGone(renderId, "interaction regions");
      }
      HRenderPage page = lookupRenderPage(rendering, pageNumber);
      Map<String, Object> body =
          org.hopper.rest.interaction.InteractionRegionIndex.build(
              rendering.getPresentation(), page);
      body.put("renderId", rendering.getId());
      body.put("pageNumber0", pageNumber);
      String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
      return withRenderIdHeader(
              Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8"),
              rendering)
          .build();
    } catch (Exception e) {
      if (isMissingRendering(e)) {
        return renderingGone(renderId, "interaction regions");
      }
      return getServerError(
          "Error listing interaction regions for render " + renderId + " page " + pageNumber, e);
    }
  }

  @SuppressWarnings("unchecked")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/getComponent/")
  public Response getComponent(ActionsRequest request) {
    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering =
          findOrRebuildRendering(
              request != null ? request.getRenderId() : null,
              request != null ? request.getPresentationName() : null,
              request != null && request.getColorMode() != null
                  ? request.getColorMode()
                  : "light",
              request != null ? request.getLayoutMode() : null,
              request != null ? request.getViewportWidth() : null);
      if (rendering == null) {
        throw new HException(
            "Unable to find rendering with ID "
                + (request != null ? request.getRenderId() : null));
      }
      HRenderPage page = lookupRenderPage(rendering, request.getPageNumber());
      // Prefer the top-most Component drawn item at this point (last in drawn order).
      // lookupComponentName returns all hits from bottom→top; take the last name.
      String componentName = null;
      if (page.getDrawnItems() != null) {
        for (int i = page.getDrawnItems().size() - 1; i >= 0; i--) {
          DrawnItem item = page.getDrawnItems().get(i);
          if (item.getType() != DrawnItem.DrawnItemType.Component) {
            continue;
          }
          if (item.getGeometry() != null
              && item.getGeometry().contains(request.getX(), request.getY())) {
            componentName = item.getComponentName();
            break;
          }
        }
      }
      if (componentName == null) {
        List<String> names = page.lookupComponentName(request.getX(), request.getY());
        if (!names.isEmpty()) {
          componentName = names.getLast();
        }
      }
      if (componentName == null) {
        // Empty click (no component under the cursor) — not an error
        return Response.ok("{\"empty\":true}")
            .type(MediaType.APPLICATION_JSON)
            .encoding("UTF-8")
            .build();
      }
      HPresentation presentation = rendering.getPresentation();

      // Drawn names may be synthetic (Group-group#1:Composite-child(Label)); resolve to template
      ComponentLookup.Found found =
          ComponentLookup.find(presentation, page.getPage(), componentName);
      if (found == null) {
        // Drawn item name could not be resolved — treat as empty click for the editor
        return Response.ok("{\"empty\":true}")
            .type(MediaType.APPLICATION_JSON)
            .encoding("UTF-8")
            .build();
      }

      // Serialize component to Hop metadata JSON...
      //
      JsonMetadataParser<HComponent> parser =
          new JsonMetadataParser<>(HComponent.class, hopperRest.getMetadataProvider());
      JSONObject componentJson = parser.getJsonObject(found.component);

      JSONObject wrapper = new JSONObject();
      wrapper.put("logicalPageNumber", found.logicalPageNumber);
      wrapper.put("pageRole", found.pageRole);
      wrapper.put("component", componentJson);
      // Help the client label nested edits
      wrapper.put("drawnName", componentName);
      wrapper.put("metadataName", found.component.getName());
      if (found.parentComponent != null) {
        wrapper.put("nested", true);
        wrapper.put("parentName", found.parentComponent.getName());
      } else {
        wrapper.put("nested", false);
      }
      // Presentation › Page › Group › Composite › Component
      wrapper.put("breadcrumb", ComponentBreadcrumb.buildJson(presentation, found));

      // Attach layout/render error from the current render page when present
      String metaName = found.component.getName();
      String layoutError = lookupLayoutError(page, metaName, componentName, false);
      String layoutErrorDetail = lookupLayoutError(page, metaName, componentName, true);
      if (layoutError != null) {
        wrapper.put("layoutError", layoutError);
      }
      if (layoutErrorDetail != null) {
        wrapper.put("layoutErrorDetail", layoutErrorDetail);
      }

      return Response.ok()
          .entity(wrapper.toJSONString())
          .encoding("UTF-8")
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (Exception e) {
      // Don't log on the server, it can be tedious to see all the failed lookups.
      //
      String errorMessage =
          "Unexpected error retrieving the JSON of a component for request: " + request;
      return Response.serverError()
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(errorMessage + "\n" + Const.getSimpleStackTrace(e))
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  /**
   * Look up a short or detailed layout error for a component on a render page (by metadata or drawn
   * name).
   */
  private static String lookupLayoutError(
      HRenderPage page, String metadataName, String drawnName, boolean detail) {
    if (page == null) {
      return null;
    }
    java.util.Map<String, String> map =
        detail ? page.getComponentLayoutErrorDetails() : page.getComponentLayoutErrors();
    if (map == null || map.isEmpty()) {
      // Fall back to layout-result data maps (body components)
      if (page.getLayoutResults() != null) {
        for (org.hopper.presentation.HComponentLayoutResult lr : page.getLayoutResults()) {
          if (lr == null || lr.getComponent() == null || lr.getDataMap() == null) {
            continue;
          }
          String n = lr.getComponent().getName();
          if (n == null) {
            continue;
          }
          if (!n.equals(metadataName) && !n.equals(drawnName)) {
            continue;
          }
          Object key =
              detail
                  ? lr.getDataMap().get(HPresentation.DATA_LAYOUT_ERROR_DETAIL)
                  : lr.getDataMap().get(HPresentation.DATA_LAYOUT_ERROR);
          if (key == null && detail) {
            key = lr.getDataMap().get(HPresentation.DATA_LAYOUT_ERROR);
          }
          if (key != null) {
            return String.valueOf(key);
          }
        }
      }
      return null;
    }
    if (metadataName != null && map.containsKey(metadataName)) {
      return map.get(metadataName);
    }
    if (drawnName != null && map.containsKey(drawnName)) {
      return map.get(drawnName);
    }
    return null;
  }

  /**
   * Locate a component by name: prefer the render page's body page, then other body pages, then
   * presentation header/footer (including nested group/composite templates).
   */
  private static ComponentLookup.Found findComponentOnPresentation(
      HPresentation presentation, HPage renderHopperPage, String componentName) throws HException {
    return ComponentLookup.find(presentation, renderHopperPage, componentName);
  }

  /**
   * Get the component names for a presentation.
   *
   * @param renderId The rendering ID
   * @param pageNumber The rendered page number
   * @return The page component names
   */
  @GET
  @Path("/info/components/{renderId}/{pageNumber}")
  public Response getPageComponentNames(
      @PathParam("renderId") String renderId, @PathParam("pageNumber") int pageNumber) {
    try {
      IRendering rendering = lookupRendering(renderId);
      HRenderPage renderPage = lookupRenderPage(rendering, pageNumber);
      HPage hopperPage = renderPage.getPage();
      HPresentation presentation = rendering.getPresentation();

      List<String> names = new ArrayList<>();
      if (hopperPage != null && hopperPage.getComponents() != null) {
        for (HComponent component : hopperPage.getComponents()) {
          names.add(component.getName());
        }
      }
      // Header/footer components are drawn on every render page but stored on the presentation
      if (presentation != null) {
        if (presentation.getHeader() != null && presentation.getHeader().getComponents() != null) {
          for (HComponent component : presentation.getHeader().getComponents()) {
            if (component.getName() != null && !names.contains(component.getName())) {
              names.add(component.getName());
            }
          }
        }
        if (presentation.getFooter() != null && presentation.getFooter().getComponents() != null) {
          for (HComponent component : presentation.getFooter().getComponents()) {
            if (component.getName() != null && !names.contains(component.getName())) {
              names.add(component.getName());
            }
          }
        }
      }
      String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(names);
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error getting the components for presentation rendering ID " + renderId;
      return getServerError(errorMessage, e);
    }
  }

  /**
   * Component-level drawn geometries for the WYSIWYG editor overlay (hover / selection borders).
   *
   * <p>Built from {@link DrawnItem}s on the requested <b>render page</b> (multi-page tables have a
   * different part geometry per page). Uses {@link DrawnItem.DrawnItemType#Component} layout
   * bounds when present so charts and other layout-filled components are selectable over their
   * full box; falls back to the union of {@code ComponentItem} ink only when the envelope is
   * missing. Also resolves {@code pageRole} (page / header / footer) for edit routing.
   */
  @GET
  @Path("/info/component-geometries/{renderId}/{pageNumber}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getComponentGeometries(
      @PathParam("renderId") String renderId,
      @PathParam("pageNumber") int pageNumber,
      @QueryParam("presentationName") String presentationName,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("layoutMode") String layoutMode,
      @QueryParam("viewportWidth") Integer viewportWidth) {
    try {
      HRenderSession.resolve(httpHeaders);
      IRendering rendering =
          findOrRebuildRendering(renderId, presentationName, colorMode, layoutMode, viewportWidth);
      if (rendering == null) {
        throw new HException("Unable to find rendering with ID " + renderId);
      }
      HRenderPage renderPage = lookupRenderPage(rendering, pageNumber);
      HPresentation presentation = rendering.getPresentation();

      // Accumulate per-component component bounds and component-item ink on this render page
      java.util.Map<String, org.hopper.core.HGeometry> componentBounds =
          new java.util.LinkedHashMap<>();
      java.util.Map<String, org.hopper.core.HGeometry> itemUnion = new java.util.LinkedHashMap<>();
      java.util.Map<String, String> pluginByName = new java.util.LinkedHashMap<>();

      if (renderPage.getDrawnItems() != null) {
        for (DrawnItem item : renderPage.getDrawnItems()) {
          if (item.getGeometry() == null || item.getComponentName() == null) {
            continue;
          }
          String name = item.getComponentName();
          if (item.getComponentPluginId() != null) {
            pluginByName.putIfAbsent(name, item.getComponentPluginId());
          }
          if (item.getType() == DrawnItem.DrawnItemType.Component) {
            // Last Component item for this name on this page (should be one after layout fix)
            componentBounds.put(name, item.getGeometry());
          } else if (item.getType() == DrawnItem.DrawnItemType.ComponentItem) {
            itemUnion.put(name, unionGeometry(itemUnion.get(name), item.getGeometry()));
          }
        }
      }

      List<java.util.Map<String, Object>> rows = new ArrayList<>();
      java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
      names.addAll(componentBounds.keySet());
      names.addAll(itemUnion.keySet());

      for (String name : names) {
        org.hopper.core.HGeometry geo =
            chooseEditorGeometry(componentBounds.get(name), itemUnion.get(name));
        if (geo == null) {
          continue;
        }
        // Skip zero-area highlights (unusable for hover)
        if (geo.getWidth() <= 0 && geo.getHeight() <= 0) {
          continue;
        }

        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("componentName", name);
        row.put("pluginId", pluginByName.getOrDefault(name, ""));
        java.util.Map<String, Object> geoMap = new java.util.LinkedHashMap<>();
        geoMap.put("x", geo.getX());
        geoMap.put("y", geo.getY());
        geoMap.put("width", Math.max(0, geo.getWidth()));
        geoMap.put("height", Math.max(0, geo.getHeight()));
        row.put("geometry", geoMap);

        ComponentLookup.Found found =
            findComponentOnPresentation(presentation, renderPage.getPage(), name);
        if (found != null) {
          row.put("pageRole", found.pageRole);
          row.put("logicalPageNumber", found.logicalPageNumber);
          row.put("metadataName", found.component.getName());
        } else {
          row.put("pageRole", "page");
          row.put("logicalPageNumber", -1);
        }
        row.put("renderPageNumber0", pageNumber);

        // Surface layout/render failures for the property panel / hover diagnostics
        String errorName =
            found != null && found.component.getName() != null ? found.component.getName() : name;
        String layoutError = lookupLayoutError(renderPage, errorName, name, false);
        String layoutErrorDetail = lookupLayoutError(renderPage, errorName, name, true);
        if (layoutError != null) {
          row.put("layoutError", layoutError);
        }
        if (layoutErrorDetail != null) {
          row.put("layoutErrorDetail", layoutErrorDetail);
        }
        // Editor peer-overflow flag from layout result (if present on this page)
        if (renderPage.getLayoutResults() != null) {
          for (org.hopper.presentation.HComponentLayoutResult lr : renderPage.getLayoutResults()) {
            if (lr.getComponent() != null
                && name.equals(lr.getComponent().getName())
                && lr.isOverflowsPage()) {
              row.put("overflowsPage", true);
              break;
            }
          }
        }
        rows.add(row);
      }

      String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows);
      return withRenderIdHeader(
              Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8"),
              rendering)
          .build();
    } catch (Exception e) {
      // Quiet 404 when the render id expired and rebuild is impossible (no presentationName)
      if (isMissingRendering(e)) {
        return renderingGone(renderId, "component geometries");
      }
      return getServerError(
          "Error listing component geometries for render " + renderId + " page " + pageNumber, e);
    }
  }

  /** Axis-aligned union of two geometries (null-safe). */
  private static org.hopper.core.HGeometry unionGeometry(
      org.hopper.core.HGeometry a, org.hopper.core.HGeometry b) {
    if (a == null) {
      return b == null ? null : new org.hopper.core.HGeometry(b);
    }
    if (b == null) {
      return new org.hopper.core.HGeometry(a);
    }
    int x1 = Math.min(a.getX(), b.getX());
    int y1 = Math.min(a.getY(), b.getY());
    int x2 = Math.max(a.getX() + a.getWidth(), b.getX() + b.getWidth());
    int y2 = Math.max(a.getY() + a.getHeight(), b.getY() + b.getHeight());
    return new org.hopper.core.HGeometry(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
  }

  /**
   * Pick the geometry used for WYSIWYG hover/selection of a whole component.
   *
   * <p>Always prefer the layout envelope ({@link DrawnItem.DrawnItemType#Component}) when it has
   * positive size. That matches the attached layout box the user sees for charts (axes, margins,
   * title area) and other components. Shrinking to the union of {@code ComponentItem} ink was
   * wrong for bar/line charts (only the bars/title were selectable) even when that ink looked
   * "substantial".
   *
   * <p>Ink is used only as a fallback when the envelope is missing or zero-sized.
   *
   * @param envelope component layout bounds (may be null)
   * @param inkUnion union of ComponentItem geometries (may be null)
   * @return geometry for editor hit-testing, or null if neither is usable
   */
  static org.hopper.core.HGeometry chooseEditorGeometry(
      org.hopper.core.HGeometry envelope, org.hopper.core.HGeometry inkUnion) {
    boolean envelopeOk =
        envelope != null && envelope.getWidth() > 0 && envelope.getHeight() > 0;
    boolean inkOk =
        inkUnion != null && inkUnion.getWidth() > 0 && inkUnion.getHeight() > 0;

    if (envelopeOk) {
      return envelope;
    }
    return inkOk ? inkUnion : null;
  }

  /** Connector names available to a presentation rendering: shared metadata catalog only. */
  @GET
  @Path("/info/connectors/{renderId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPresentationConnectorNames(@PathParam("renderId") String renderId) {
    try {
      // Validate renderId exists (keeps URL semantics for clients)
      lookupRendering(renderId);
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      IHopMetadataSerializer<HConnector> serializer = provider.getSerializer(HConnector.class);
      List<String> names = new ArrayList<>(serializer.listObjectNames());
      String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(names);
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError("Error listing connectors for rendering " + renderId, e);
    }
  }

  /**
   * Describe the output of a connector in a presentation rendering.
   *
   * @param request the request object which contains all the contextual information needed.
   * @return The row metadata describing the connector output.
   */
  @POST
  @Path("/connector/describe/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response describeConnectorOutput(ConnectorDescriptionRequest request) {
    try {
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      IHopMetadataSerializer<HConnector> serializer = provider.getSerializer(HConnector.class);

      IRendering rendering = null;
      if (request.getRenderId() != null && !request.getRenderId().isBlank()) {
        rendering = hopperRest.getRendering(request.getRenderId());
      }

      HConnector connector = null;
      IDataContext dataContext = null;

      if (rendering != null && rendering.getLayoutResults() != null) {
        dataContext = rendering.getLayoutResults().getDataContext();
      }

      connector = serializer.load(request.getConnectorName());
      if (connector == null) {
        throw new HException(
            "Connector '" + request.getConnectorName() + "' couldn't be found in the metadata");
      }

      // Fallback when renderId is missing/expired: describe from metadata alone
      if (dataContext == null) {
        HPresentation presentation = rendering != null ? rendering.getPresentation() : null;
        if (presentation == null) {
          presentation = new HPresentation();
          presentation.setName("_describe");
        }
        dataContext = new PresentationDataContext(presentation, provider);
      }

      IRowMeta rowMeta = connector.describeOutput(dataContext);
      String json =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .writeValueAsString(new RowMetaResponse(rowMeta).getValueMetaList());
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError(
          "Error getting row metadata from connector " + request.getConnectorName(), e);
    }
  }

  /** Response header so the browser can refresh a stale {@code renderId} after rebuild. */
  public static final String HEADER_RENDER_ID = "X-Hopper-Render-Id";

  private Response.ResponseBuilder withRenderIdHeader(
      Response.ResponseBuilder rb, IRendering rendering) {
    if (rb != null && rendering != null && rendering.getId() != null) {
      rb.header(HEADER_RENDER_ID, rendering.getId());
    }
    return rb;
  }

  private IRendering findRenderingOrNull(String renderId) {
    if (HRenderSession.getCurrent() == null && httpHeaders != null) {
      HRenderSession.resolve(httpHeaders);
    }
    return hopperRest.getRendering(renderId);
  }

  /**
   * Session-scoped lookup by render UUID; on miss rebuild from presentation name (short TTL safe).
   */
  private IRendering findOrRebuildRendering(
      String renderId,
      String presentationName,
      String colorMode,
      String layoutMode,
      Integer viewportWidth)
      throws Exception {
    if (HRenderSession.getCurrent() == null && httpHeaders != null) {
      HRenderSession.resolve(httpHeaders);
    }
    org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);
    org.hopper.rest.render.RenderFactory.ContinuousLayoutOptions cont =
        continuousOptionsFrom(layoutMode, viewportWidth);
    // Prefer empty params for rebuild (editor path). View with interaction params should soft-reload
    // by name via the client when a full parameter-preserving rebuild is required.
    List<org.hopper.presentation.variable.HParameter> params = Collections.emptyList();
    return hopperRest.getOrRebuildRendering(
        renderId, presentationName, mode, cont, params);
  }

  private IRendering lookupRendering(String renderId) throws HException {
    IRendering rendering = findRenderingOrNull(renderId);
    if (rendering == null) {
      throw new HException("Unable to find rendering with ID " + renderId);
    }
    return rendering;
  }
}
