package org.hopper.rest.resources;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.hopper.core.HAttachment;
import org.hopper.core.exception.HException;
import org.hopper.core.HJson;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionLocationOption;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.rest.history.PresentationSnapshot;
import org.hopper.rest.render.IRendering;
import org.hopper.rest.render.RenderFactory;
import org.hopper.rest.security.HRenderSession;
import org.json.simple.JSONObject;

/** Entry points for the WYSIWYG presentation editor (edit mode). */
@Path("edit/presentation")
public class EditPresentationResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Context private HttpHeaders httpHeaders;

  private void ensureRenderSession() {
    if (httpHeaders != null) {
      HRenderSession.resolve(httpHeaders);
    }
  }

  /**
   * Create a new empty presentation (one landscape page, no components) and save it to metadata.
   * Body: {@code { "name": "...", "description": "...", "width": 1123, "height": 794 }} — only
   * {@code name} is required.
   */
  @POST
  @Path("/create/")
  @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response createPresentation(Map<String, Object> body) {
    try {
      if (body == null || body.get("name") == null) {
        return getServerError("Request body must include a non-empty \"name\"", false);
      }
      String name = String.valueOf(body.get("name")).trim();
      if (name.isEmpty()) {
        return getServerError("Presentation name must not be empty", false);
      }
      String description =
          body.get("description") != null ? String.valueOf(body.get("description")) : "";
      int width = toInt(body.get("width"), 1123);
      int height = toInt(body.get("height"), 794);
      int margin = toInt(body.get("margin"), 25);

      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      if (serializer.exists(name)) {
        return getServerError("A presentation named '" + name + "' already exists", false);
      }

      HPresentation presentation = new HPresentation();
      presentation.setName(name);
      presentation.setDescription(description);
      presentation.setDefaultThemeName(
          body.get("defaultThemeName") != null
              ? String.valueOf(body.get("defaultThemeName"))
              : "Default");

      HPage page = new HPage(width, height, margin, margin, margin, margin);
      page.setHeader(false);
      page.setFooter(false);
      presentation.getPages().add(page);

      // Themes resolve from the catalog by defaultThemeName at layout/render time.

      serializer.save(presentation);
      hopperRest.getLog().logBasic("Created presentation '" + name + "'");
      return Response.ok(name).type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      return getServerError("Error creating presentation", e);
    }
  }

  private static int toInt(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * Layout results store a composite color-mode key (e.g. {@code light|noPeerBreak|maxPg=10}).
   * Client requests only send the base mode ({@code light}/{@code dark}).
   */
  public static boolean layoutColorModeMatches(String wantMode, String haveModeKey) {
    String want = wantMode != null ? wantMode.trim() : "light";
    if (want.isEmpty()) {
      want = "light";
    }
    String have = haveModeKey != null ? haveModeKey.trim() : "light";
    if (have.isEmpty()) {
      have = "light";
    }
    int pipe = have.indexOf('|');
    String base = pipe >= 0 ? have.substring(0, pipe).trim() : have;
    return want.equalsIgnoreCase(base);
  }

  private static int defaultDropWidth(String pluginId) {
    if (pluginId == null) {
      return 320;
    }
    if (pluginId.contains("Chart") || pluginId.contains("Crosstab")) {
      return 400;
    }
    if (pluginId.contains("Table")) {
      return 360;
    }
    if (pluginId.contains("Image") || pluginId.contains("Svg") || pluginId.contains("SVG")) {
      return 200;
    }
    return 320;
  }

  private static int defaultDropHeight(String pluginId) {
    if (pluginId == null) {
      return 200;
    }
    if (pluginId.contains("Chart") || pluginId.contains("Crosstab")) {
      return 260;
    }
    if (pluginId.contains("Table")) {
      return 220;
    }
    if (pluginId.contains("Image") || pluginId.contains("Svg") || pluginId.contains("SVG")) {
      return 150;
    }
    return 200;
  }

  /**
   * Open a presentation in edit mode: render it and return the editor HTML shell.
   *
   * @param name presentation metadata name
   * @param page 0-based render page index (default 0)
   * @param reload if true (default), force a fresh layout/render
   */
  @GET
  @Path("/{name}/")
  @Produces(MediaType.TEXT_HTML)
  public Response openEditor(
      @PathParam("name") String name,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("reload") @DefaultValue("true") boolean reload,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode) {
    try {
      return openEditorInternal(name, page, reload, colorMode);
    } catch (Exception e) {
      return getServerError("Error opening presentation editor for '" + name + "'", e);
    }
  }

  /** Same as {@link #openEditor} with page in the path: {@code /{name}/page/{page}/}. */
  @GET
  @Path("/{name}/page/{page}/")
  @Produces(MediaType.TEXT_HTML)
  public Response openEditorPage(
      @PathParam("name") String name,
      @PathParam("page") int page,
      @QueryParam("reload") @DefaultValue("false") boolean reload,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode) {
    try {
      return openEditorInternal(name, page, reload, colorMode);
    } catch (Exception e) {
      return getServerError("Error opening presentation editor for '" + name + "' page " + page, e);
    }
  }

  private Response openEditorInternal(String name, int page, boolean reload, String colorMode)
      throws Exception {
    ensureRenderSession();
    HPresentation presentation = hopperRest.loadPresentation(name);
    if (presentation == null) {
      return getServerError("Presentation not found: " + name, false);
    }

    org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);
    String wantMode = mode.wireValue();

    IRendering rendering = hopperRest.findRendering(name, Collections.emptyList());
    if (rendering != null && reload) {
      hopperRest.removeRendering(rendering);
      rendering = null;
    }
    // Reuse cached render only when color mode matches (avoids light flash → client soft-reload).
    // Layout results store a composite key (e.g. "light|noPeerBreak|maxPg=10") — compare base only.
    if (rendering != null) {
      String haveMode =
          rendering.getLayoutResults() != null
                  && rendering.getLayoutResults().getColorMode() != null
              ? rendering.getLayoutResults().getColorMode()
              : "light";
      if (!layoutColorModeMatches(wantMode, haveMode)) {
        hopperRest.removeRendering(rendering);
        rendering = null;
      }
    }
    if (rendering == null) {
      // Editor: do not silently push peer components (charts/labels) onto later render pages
      // reload=true → full refresh: bypass connector disk cache
      rendering =
          RenderFactory.renderPresentation(
              hopperRest.getLoggingObject(),
              hopperRest.getMetadataProvider(),
              presentation,
              Collections.<HParameter>emptyList(),
              mode,
              false,
              reload);
      String sid = HRenderSession.getCurrent();
      if (sid != null) {
        rendering.setSessionId(sid);
      }
      hopperRest.storeRendering(rendering);
    }

    List<HRenderPage> pages = rendering.getLayoutResults().getRenderPages();
    if (pages == null || pages.isEmpty()) {
      return getServerError("Presentation '" + name + "' rendered with no pages", false);
    }
    if (page < 0 || page >= pages.size()) {
      return getServerError(
          "Invalid page " + page + " for presentation '" + name + "' (count=" + pages.size() + ")",
          false);
    }

    Response pageResponse = RenderFactory.renderPageHtml(rendering, pages.get(page), "edit");
    NewCookie guest = HRenderSession.newGuestCookieIfCreated();
    if (guest != null) {
      return Response.fromResponse(pageResponse).cookie(guest).build();
    }
    return pageResponse;
  }

  /** Undo/redo stack status for a presentation: {@code canUndo}, {@code canRedo}, depths. */
  @GET
  @Path("/{name}/history/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response historyStatus(@PathParam("name") String name) {
    try {
      return Response.ok(MAPPER.writeValueAsString(undoService.status(name)))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error reading undo history for '" + name + "'", e);
    }
  }

  /**
   * Undo last presentation mutation (full JSON snapshot restore).
   *
   * @return {@code { ok, canUndo, canRedo, message? }}
   */
  @POST
  @Path("/{name}/history/undo/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response historyUndo(@PathParam("name") String name) {
    return applyHistoryRestore(name, true);
  }

  /** Redo previously undone presentation mutation. */
  @POST
  @Path("/{name}/history/redo/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response historyRedo(@PathParam("name") String name) {
    return applyHistoryRestore(name, false);
  }

  private Response applyHistoryRestore(String name, boolean undo) {
    try {
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      String currentJson;
      try {
        currentJson = PresentationSnapshot.loadJson(name, provider);
      } catch (Exception e) {
        return getServerError("Presentation not found: " + name, false);
      }
      String restore =
          undo ? undoService.undo(name, currentJson) : undoService.redo(name, currentJson);
      Map<String, Object> out = new LinkedHashMap<>(undoService.status(name));
      if (restore == null) {
        out.put("ok", false);
        out.put("message", undo ? "Nothing to undo" : "Nothing to redo");
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }
      PresentationSnapshot.saveJson(restore, provider);
      // Drop cached render + layout snapshots so soft-reload rebuilds from restored metadata
      org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
          .invalidatePresentation(name);
      IRendering existing = hopperRest.findRendering(name, Collections.emptyList());
      if (existing != null) {
        hopperRest.removeRendering(existing);
      }
      out = new LinkedHashMap<>(undoService.status(name));
      out.put("ok", true);
      hopperRest.getLog().logBasic((undo ? "Undo" : "Redo") + " presentation '" + name + "'");
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error applying " + (undo ? "undo" : "redo") + " for presentation '" + name + "'", e);
    }
  }

  /**
   * Header/footer presence and height for the editor rail.
   *
   * <pre>
   * { "header": { "enabled": true, "height": 50 }, "footer": { "enabled": false, "height": 25 },
   * "portrait": false }</pre>
   */
  @GET
  @Path("/{name}/header-footer/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getHeaderFooter(@PathParam("name") String name) {
    try {
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      return Response.ok(MAPPER.writeValueAsString(headerFooterState(presentation)))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error loading header/footer for presentation '" + name + "'", e);
    }
  }

  /**
   * Enable/disable header and footer and/or set their heights.
   *
   * <p>Body (all fields optional):
   *
   * <pre>
   * {
   *   "header": { "enabled": true, "height": 50 },
   *   "footer": { "enabled": false, "height": 25 }
   * }
   * </pre>
   *
   * Enabling creates a page via {@link HPage#getHeaderFooter(boolean, boolean, int)} sized to the
   * presentation's first body page orientation; width matches body usable width.
   */
  @POST
  @Path("/{name}/header-footer/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateHeaderFooter(@PathParam("name") String name, Map<String, Object> body) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);

      boolean portrait = isPortrait(presentation);
      int defaultHeaderH = 50;
      int defaultFooterH = 25;

      applyHeaderFooterToggle(
          presentation,
          true,
          body != null ? asMap(body.get("header")) : null,
          portrait,
          defaultHeaderH);
      applyHeaderFooterToggle(
          presentation,
          false,
          body != null ? asMap(body.get("footer")) : null,
          portrait,
          defaultFooterH);

      saveWithUndo(serializer, presentation, name, beforeJson);
      hopperRest.getLog().logBasic("Updated header/footer for presentation '" + name + "'");

      return Response.ok(MAPPER.writeValueAsString(headerFooterState(presentation)))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error updating header/footer for presentation '" + name + "'", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    return null;
  }

  private static Map<String, Object> headerFooterState(HPresentation presentation) {
    Map<String, Object> state = new LinkedHashMap<>();
    HPage header = presentation.getHeader();
    HPage footer = presentation.getFooter();
    Map<String, Object> h = new LinkedHashMap<>();
    h.put("enabled", header != null);
    h.put("height", header != null ? header.getHeight() : 50);
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("enabled", footer != null);
    f.put("height", footer != null ? footer.getHeight() : 25);
    state.put("header", h);
    state.put("footer", f);
    state.put("portrait", isPortrait(presentation));

    // Page metrics + region rectangles (page coordinates) for editor overlays
    HPage body =
        presentation.getPages() != null && !presentation.getPages().isEmpty()
            ? presentation.getPages().getFirst()
            : null;
    int pageW = body != null ? body.getWidth() : 1123;
    int pageH = body != null ? body.getHeight() : 794;
    int lm = body != null ? body.getLeftMargin() : 25;
    int rm = body != null ? body.getRightMargin() : 25;
    int tm = body != null ? body.getTopMargin() : 25;
    int bm = body != null ? body.getBottomMargin() : 25;
    int headerH = header != null ? header.getHeight() : 0;
    int footerH = footer != null ? footer.getHeight() : 0;
    int usableW = Math.max(0, pageW - lm - rm);

    Map<String, Object> page = new LinkedHashMap<>();
    page.put("width", pageW);
    page.put("height", pageH);
    page.put("leftMargin", lm);
    page.put("rightMargin", rm);
    page.put("topMargin", tm);
    page.put("bottomMargin", bm);
    state.put("page", page);

    // Regions match HPresentation.renderHeaderFooter / body offsets
    Map<String, Object> regions = new LinkedHashMap<>();
    regions.put("page", rectMap(0, 0, pageW, pageH));
    if (header != null) {
      regions.put("header", rectMap(lm, tm, usableW, headerH));
    } else {
      regions.put("header", null);
    }
    int contentY = tm + headerH;
    int contentH = Math.max(0, pageH - tm - bm - headerH - footerH);
    regions.put("content", rectMap(lm, contentY, usableW, contentH));
    if (footer != null) {
      regions.put("footer", rectMap(lm, pageH - bm - footerH, usableW, footerH));
    } else {
      regions.put("footer", null);
    }
    state.put("regions", regions);
    return state;
  }

  private static Map<String, Object> rectMap(int x, int y, int width, int height) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("x", x);
    r.put("y", y);
    r.put("width", width);
    r.put("height", height);
    return r;
  }

  private static boolean isPortrait(HPresentation presentation) {
    if (presentation.getPages() == null || presentation.getPages().isEmpty()) {
      return false;
    }
    HPage first = presentation.getPages().getFirst();
    return first.getHeight() >= first.getWidth();
  }

  private static void applyHeaderFooterToggle(
      HPresentation presentation,
      boolean isHeader,
      Map<String, Object> spec,
      boolean portrait,
      int defaultHeight) {
    if (spec == null) {
      return;
    }
    HPage current = isHeader ? presentation.getHeader() : presentation.getFooter();
    boolean enabled;
    if (spec.containsKey("enabled")) {
      Object en = spec.get("enabled");
      enabled = en instanceof Boolean ? (Boolean) en : Boolean.parseBoolean(String.valueOf(en));
    } else {
      enabled = current != null;
    }
    int height = current != null ? current.getHeight() : defaultHeight;
    if (spec.get("height") != null) {
      height = toInt(spec.get("height"), height);
      if (height < 1) {
        height = 1;
      }
    }

    if (!enabled) {
      if (isHeader) {
        presentation.setHeader(null);
      } else {
        presentation.setFooter(null);
      }
      return;
    }

    if (current == null) {
      // Create via HPage.getHeaderFooter (matches engine helpers / samples)
      HPage created = HPage.getHeaderFooter(isHeader, portrait, height);
      // Match usable width of first body page when available
      if (presentation.getPages() != null && !presentation.getPages().isEmpty()) {
        HPage body = presentation.getPages().getFirst();
        created.setWidth(Math.max(1, body.getWidthBetweenMargins()));
      }
      if (isHeader) {
        presentation.setHeader(created);
      } else {
        presentation.setFooter(created);
      }
    } else {
      current.setHeight(height);
      current.setHeader(isHeader);
      current.setFooter(!isHeader);
    }
  }

  /**
   * Offset-only drag end: add {@code dx}/{@code dy} to the component's left/top layout offsets (and
   * to right/bottom when they encode size via LEFT/TOP page anchors). Does not rewrite relative
   * {@code componentName}, alignment, or percentage.
   *
   * <p>Body: {@code { "dx": 10, "dy": -5 }} — either may be omitted (default 0). {@code
   * componentName} may be a metadata name or a synthetic drawn name (group/composite).
   */
  @POST
  @Path("/{name}/components/{componentName}/nudge/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response nudgeComponent(
      @PathParam("name") String name,
      @PathParam("componentName") String componentName,
      Map<String, Object> body) {
    try {
      int dx = toInt(body != null ? body.get("dx") : null, 0);
      int dy = toInt(body != null ? body.get("dy") : null, 0);
      if (dx == 0 && dy == 0) {
        Map<String, Object> noop = new LinkedHashMap<>();
        noop.put("name", componentName);
        noop.put("dx", 0);
        noop.put("dy", 0);
        noop.put("changed", false);
        return Response.ok(MAPPER.writeValueAsString(noop))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }

      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);

      ComponentLookup.Found found = ComponentLookup.find(presentation, null, componentName);
      if (found == null) {
        return getServerError(
            "Component '" + componentName + "' not found in presentation '" + name + "'", false);
      }

      applyOffsetNudge(found.component, dx, dy);
      saveWithUndo(serializer, presentation, name, beforeJson);

      hopperRest
          .getLog()
          .logBasic(
              "nudge: '"
                  + found.component.getName()
                  + "' in '"
                  + name
                  + "' by ("
                  + dx
                  + ","
                  + dy
                  + ")");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("name", found.component.getName());
      result.put("dx", dx);
      result.put("dy", dy);
      result.put("changed", true);
      result.put("pageRole", found.pageRole);
      result.put("logicalPageNumber", found.logicalPageNumber);
      return Response.ok(MAPPER.writeValueAsString(result))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error nudging component '" + componentName + "' in presentation '" + name + "'", e);
    }
  }

  /**
   * Move a top-level body-page component to the adjacent logical page. Creates a new page when the
   * target does not exist: {@code next} appends after the last page; {@code previous} inserts before
   * the first page.
   *
   * <p>Body: {@code { "direction": "next" | "previous" }}. Nested / header / footer components are
   * rejected.
   */
  @POST
  @Path("/{name}/components/{componentName}/move-page/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response moveComponentToAdjacentPage(
      @PathParam("name") String name,
      @PathParam("componentName") String componentName,
      Map<String, Object> body) {
    try {
      String direction =
          body != null && body.get("direction") != null
              ? String.valueOf(body.get("direction")).trim().toLowerCase()
              : "";
      if (!"next".equals(direction) && !"previous".equals(direction)) {
        return getServerError("Body must include \"direction\": \"next\" or \"previous\"", false);
      }

      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);

      ComponentLookup.Found found = ComponentLookup.find(presentation, null, componentName);
      if (found == null) {
        return getServerError(
            "Component '" + componentName + "' not found in presentation '" + name + "'", false);
      }
      if (found.parentComponent != null || found.groupTemplate) {
        return getServerError(
            "Only top-level page components can be moved between pages (not nested items)", false);
      }
      if (!"page".equals(found.pageRole) || found.logicalPageNumber < 0) {
        return getServerError(
            "Only body-page components can be moved between pages (not header/footer)", false);
      }

      List<HPage> pages = presentation.getPages();
      if (pages == null || pages.isEmpty()) {
        return getServerError("Presentation '" + name + "' has no body pages", false);
      }

      HPage sourcePage = found.page;
      int sourceIndex = found.logicalPageNumber;
      if (sourceIndex < 0 || sourceIndex >= pages.size() || pages.get(sourceIndex) != sourcePage) {
        // Re-resolve index by identity if metadata drifted
        sourceIndex = pages.indexOf(sourcePage);
      }
      if (sourceIndex < 0) {
        return getServerError("Could not resolve source page for component '" + componentName + "'", false);
      }

      boolean pageCreated = false;
      int targetIndex;
      if ("next".equals(direction)) {
        if (sourceIndex >= pages.size() - 1) {
          pages.add(newBlankBodyPage(pages.get(sourceIndex)));
          pageCreated = true;
        }
        targetIndex = sourceIndex + 1;
      } else {
        // previous
        if (sourceIndex <= 0) {
          pages.add(0, newBlankBodyPage(pages.get(0)));
          pageCreated = true;
          // Source page object is unchanged; it is now at index 1
          sourceIndex = 1;
        }
        targetIndex = sourceIndex - 1;
      }

      if (targetIndex < 0 || targetIndex >= pages.size()) {
        return getServerError("Invalid target page index " + targetIndex, false);
      }
      HPage targetPage = pages.get(targetIndex);

      List<HComponent> sourceList = sourcePage.getComponents();
      if (sourceList == null || !sourceList.remove(found.component)) {
        return getServerError(
            "Component '" + componentName + "' is not a top-level entry on its source page", false);
      }
      if (targetPage.getComponents() == null) {
        targetPage.setComponents(new ArrayList<>());
      }

      // Layout anchors that name other components break once the component leaves the page
      // (and leave others pointing at a missing name). Detach before/after the transfer.
      String movedName = found.component.getName();
      clearLayoutAnchorsToName(sourceList, movedName);
      clearLayoutAnchorsToName(targetPage.getComponents(), movedName);

      boolean toTop = "next".equals(direction);
      placeComponentForPageTransfer(found.component, targetPage, toTop);
      targetPage.getComponents().add(found.component);

      saveWithUndo(serializer, presentation, name, beforeJson);

      hopperRest
          .getLog()
          .logBasic(
              "move-page: '"
                  + found.component.getName()
                  + "' in '"
                  + name
                  + "' "
                  + direction
                  + " → page "
                  + targetIndex
                  + (pageCreated ? " (created)" : ""));

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("name", found.component.getName());
      result.put("direction", direction);
      result.put("fromLogicalPageNumber", sourceIndex);
      result.put("logicalPageNumber", targetIndex);
      result.put("pageCreated", pageCreated);
      result.put("pageCount", pages.size());
      result.put("changed", true);
      return Response.ok(MAPPER.writeValueAsString(result))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error moving component '" + componentName + "' between pages in '" + name + "'", e);
    }
  }

  /** New empty body page cloned from a template's size/margins. */
  private static HPage newBlankBodyPage(HPage template) {
    HPage t = template != null ? template : HPage.getA4(false);
    HPage neu =
        new HPage(
            t.getWidth(),
            t.getHeight(),
            t.getLeftMargin(),
            t.getRightMargin(),
            t.getTopMargin(),
            t.getBottomMargin());
    neu.setHeader(false);
    neu.setFooter(false);
    return neu;
  }

  /**
   * After transferring a component to another page, rewrite layout as <b>page-absolute</b> anchors
   * (no {@code componentName} references) and park near the top (next) or bottom (previous) of the
   * content area. Relative anchors to peers on the old page would throw on the new page.
   */
  private static void placeComponentForPageTransfer(
      HComponent component, HPage page, boolean toTop) {
    if (component == null || page == null) {
      return;
    }
    HLayout old = component.getLayout();
    int left = page.getLeftMargin();
    int top = page.getTopMargin() + 8;
    int width = 200;
    int height = 120;

    if (old != null) {
      // Prefer absolute left/top already on the layout
      if (old.getLeft() != null && StringUtils.isEmpty(old.getLeft().getComponentName())) {
        left = old.getLeft().getOffset();
      }
      if (old.getTop() != null
          && StringUtils.isEmpty(old.getTop().getComponentName())
          && old.getTop().getAlignment() != HAttachment.Alignment.BOTTOM) {
        top = old.getTop().getOffset();
      }
      if (old.getRight() != null
          && StringUtils.isEmpty(old.getRight().getComponentName())
          && old.getRight().getAlignment() == HAttachment.Alignment.LEFT
          && old.getLeft() != null
          && StringUtils.isEmpty(old.getLeft().getComponentName())) {
        width = Math.max(MIN_COMPONENT_TRANSFER_SIZE, old.getRight().getOffset() - old.getLeft().getOffset());
      }
      if (old.getBottom() != null
          && StringUtils.isEmpty(old.getBottom().getComponentName())
          && old.getBottom().getAlignment() == HAttachment.Alignment.TOP
          && old.getTop() != null
          && StringUtils.isEmpty(old.getTop().getComponentName())) {
        height =
            Math.max(
                MIN_COMPONENT_TRANSFER_SIZE, old.getBottom().getOffset() - old.getTop().getOffset());
      }
    }

    int contentTop = page.getTopMargin() + 8;
    int contentBottom = page.getHeight() - page.getBottomMargin() - 8;
    int contentLeft = page.getLeftMargin();
    int contentRight = page.getWidth() - page.getRightMargin();
    left = Math.max(contentLeft, Math.min(left, Math.max(contentLeft, contentRight - width)));
    int newTop = toTop ? contentTop : Math.max(contentTop, contentBottom - height);
    if (newTop + height > contentBottom) {
      height = Math.max(MIN_COMPONENT_TRANSFER_SIZE, contentBottom - newTop);
    }

    HLayout neu = new HLayout();
    neu.setLeft(new HAttachment(null, 0, left, HAttachment.Alignment.LEFT));
    neu.setTop(new HAttachment(null, 0, newTop, HAttachment.Alignment.TOP));
    neu.setRight(new HAttachment(null, 0, left + width, HAttachment.Alignment.LEFT));
    neu.setBottom(new HAttachment(null, 0, newTop + height, HAttachment.Alignment.TOP));
    component.setLayout(neu);
  }

  /**
   * Strip layout attachments that name {@code targetName} so remaining components on a page do not
   * reference a component that left (or will leave) the page.
   */
  private static void clearLayoutAnchorsToName(List<HComponent> components, String targetName) {
    if (components == null || StringUtils.isBlank(targetName)) {
      return;
    }
    for (HComponent c : components) {
      if (c == null || c.getLayout() == null) {
        continue;
      }
      clearAttachmentComponentName(c.getLayout().getLeft(), targetName);
      clearAttachmentComponentName(c.getLayout().getTop(), targetName);
      clearAttachmentComponentName(c.getLayout().getRight(), targetName);
      clearAttachmentComponentName(c.getLayout().getBottom(), targetName);
    }
  }

  private static void clearAttachmentComponentName(HAttachment attachment, String targetName) {
    if (attachment == null || StringUtils.isBlank(targetName)) {
      return;
    }
    if (!targetName.equals(attachment.getComponentName())) {
      return;
    }
    attachment.setComponentName(null);
    // Relative-to-peer alignments become page-edge alignments so layout still resolves
    if (attachment.getAlignment() == HAttachment.Alignment.BOTTOM) {
      attachment.setAlignment(HAttachment.Alignment.TOP);
    } else if (attachment.getAlignment() == HAttachment.Alignment.RIGHT) {
      attachment.setAlignment(HAttachment.Alignment.LEFT);
    }
  }

  private static final int MIN_COMPONENT_TRANSFER_SIZE = 10;

  /**
   * Place a component at absolute page coordinates, preserving width/height when the layout already
   * encodes size with LEFT/TOP anchors. Clears peer {@code componentName} anchors.
   */
  private static void placeComponentAtAbsolute(HComponent component, int x, int y) {
    if (component == null) {
      return;
    }
    HLayout old = component.getLayout();
    int width = 200;
    int height = 80;
    if (old != null) {
      if (old.getLeft() != null
          && old.getRight() != null
          && StringUtils.isEmpty(old.getLeft().getComponentName())
          && StringUtils.isEmpty(old.getRight().getComponentName())
          && old.getRight().getAlignment() == HAttachment.Alignment.LEFT) {
        width =
            Math.max(
                MIN_COMPONENT_TRANSFER_SIZE,
                old.getRight().getOffset() - old.getLeft().getOffset());
      }
      if (old.getTop() != null
          && old.getBottom() != null
          && StringUtils.isEmpty(old.getTop().getComponentName())
          && StringUtils.isEmpty(old.getBottom().getComponentName())
          && old.getBottom().getAlignment() == HAttachment.Alignment.TOP) {
        height =
            Math.max(
                MIN_COMPONENT_TRANSFER_SIZE,
                old.getBottom().getOffset() - old.getTop().getOffset());
      }
    }
    // Optional size hints from client paste payload (origin geometry)
    // kept via width/height fields if present on a future body — not required here.

    HLayout neu = new HLayout();
    neu.setLeft(new HAttachment(null, 0, x, HAttachment.Alignment.LEFT));
    neu.setTop(new HAttachment(null, 0, y, HAttachment.Alignment.TOP));
    neu.setRight(new HAttachment(null, 0, x + width, HAttachment.Alignment.LEFT));
    neu.setBottom(new HAttachment(null, 0, y + height, HAttachment.Alignment.TOP));
    component.setLayout(neu);
  }

  /**
   * Adjust layout offsets only. Relative anchors (componentName / percentage / alignment) are
   * preserved. When right/bottom encode absolute size (LEFT/TOP alignment on page), their offsets
   * move with the box so width/height stay constant.
   */
  private static void applyOffsetNudge(HComponent component, int dx, int dy) {
    if (component == null) {
      return;
    }
    HLayout layout = component.getLayout();
    if (layout == null) {
      layout = new HLayout();
      component.setLayout(layout);
    }
    if (dx != 0) {
      if (layout.getLeft() == null) {
        layout.setLeft(new HAttachment(null, 0, dx, HAttachment.Alignment.LEFT));
      } else {
        layout.getLeft().setOffset(layout.getLeft().getOffset() + dx);
      }
      // Keep absolute-size boxes (right with LEFT alignment) the same width
      if (layout.getRight() != null
          && layout.getRight().getAlignment() == HAttachment.Alignment.LEFT) {
        layout.getRight().setOffset(layout.getRight().getOffset() + dx);
      }
    }
    if (dy != 0) {
      if (layout.getTop() == null) {
        layout.setTop(new HAttachment(null, 0, dy, HAttachment.Alignment.TOP));
      } else {
        layout.getTop().setOffset(layout.getTop().getOffset() + dy);
      }
      if (layout.getBottom() != null
          && layout.getBottom().getAlignment() == HAttachment.Alignment.TOP) {
        layout.getBottom().setOffset(layout.getBottom().getOffset() + dy);
      }
    }
  }

  /**
   * Edge-resize end: move left/top/right/bottom layout edges by the given deltas (page pixels).
   * Positive {@code dRight}/{@code dBottom} expand the box; positive {@code dLeft}/{@code dTop}
   * move those edges toward higher coordinates (typically shrinking from that side).
   *
   * <p>Body example: {@code { "dLeft": 0, "dTop": 0, "dRight": 20, "dBottom": 10, "originX": 100,
   * "originY": 50, "originWidth": 200, "originHeight": 80 }}. Origin geometry is used when size
   * anchors (right/bottom) are missing so a natural-sized component (e.g. label) can gain an
   * explicit box. Relative {@code componentName}/percentage/alignment are preserved.
   */
  @POST
  @Path("/{name}/components/{componentName}/resize/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response resizeComponent(
      @PathParam("name") String name,
      @PathParam("componentName") String componentName,
      Map<String, Object> body) {
    try {
      int dLeft = toInt(body != null ? body.get("dLeft") : null, 0);
      int dTop = toInt(body != null ? body.get("dTop") : null, 0);
      int dRight = toInt(body != null ? body.get("dRight") : null, 0);
      int dBottom = toInt(body != null ? body.get("dBottom") : null, 0);
      int originX = toInt(body != null ? body.get("originX") : null, 0);
      int originY = toInt(body != null ? body.get("originY") : null, 0);
      int originWidth = toInt(body != null ? body.get("originWidth") : null, 0);
      int originHeight = toInt(body != null ? body.get("originHeight") : null, 0);

      if (dLeft == 0 && dTop == 0 && dRight == 0 && dBottom == 0) {
        Map<String, Object> noop = new LinkedHashMap<>();
        noop.put("name", componentName);
        noop.put("changed", false);
        return Response.ok(MAPPER.writeValueAsString(noop))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }

      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);

      ComponentLookup.Found found = ComponentLookup.find(presentation, null, componentName);
      if (found == null) {
        return getServerError(
            "Component '" + componentName + "' not found in presentation '" + name + "'", false);
      }

      applyEdgeResize(
          found.component,
          dLeft,
          dTop,
          dRight,
          dBottom,
          originX,
          originY,
          originWidth,
          originHeight);
      saveWithUndo(serializer, presentation, name, beforeJson);

      hopperRest
          .getLog()
          .logBasic(
              "resize: '"
                  + found.component.getName()
                  + "' in '"
                  + name
                  + "' edges (L,T,R,B)=("
                  + dLeft
                  + ","
                  + dTop
                  + ","
                  + dRight
                  + ","
                  + dBottom
                  + ")");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("name", found.component.getName());
      result.put("dLeft", dLeft);
      result.put("dTop", dTop);
      result.put("dRight", dRight);
      result.put("dBottom", dBottom);
      result.put("changed", true);
      result.put("pageRole", found.pageRole);
      result.put("logicalPageNumber", found.logicalPageNumber);
      return Response.ok(MAPPER.writeValueAsString(result))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error resizing component '" + componentName + "' in presentation '" + name + "'", e);
    }
  }

  /**
   * Adjust layout edges for a WYSIWYG resize. Unlike {@link #applyOffsetNudge}, left/top moves do
   * <em>not</em> shift right/bottom absolute-size anchors (so width/height change). Missing
   * right/bottom anchors are created from origin geometry when the corresponding edge is moved.
   */
  private static void applyEdgeResize(
      HComponent component,
      int dLeft,
      int dTop,
      int dRight,
      int dBottom,
      int originX,
      int originY,
      int originWidth,
      int originHeight) {
    if (component == null) {
      return;
    }
    HLayout layout = component.getLayout();
    if (layout == null) {
      layout = new HLayout();
      component.setLayout(layout);
    }

    // Clamp so the resulting box stays at least MIN_RESIZE_PX on each axis
    final int minPx = 10;
    int newW = Math.max(minPx, originWidth - dLeft + dRight);
    int newH = Math.max(minPx, originHeight - dTop + dBottom);
    // If clamping width, absorb into the edge that was moved
    int adjDLeft = dLeft;
    int adjDRight = dRight;
    int adjDTop = dTop;
    int adjDBottom = dBottom;
    int intendedW = originWidth - dLeft + dRight;
    int intendedH = originHeight - dTop + dBottom;
    if (intendedW < minPx) {
      int deficit = minPx - intendedW;
      if (dRight != 0 && dLeft == 0) {
        adjDRight += deficit;
      } else if (dLeft != 0 && dRight == 0) {
        adjDLeft -= deficit;
      } else if (dLeft != 0) {
        adjDLeft -= deficit;
      } else {
        adjDRight += deficit;
      }
    }
    if (intendedH < minPx) {
      int deficit = minPx - intendedH;
      if (dBottom != 0 && dTop == 0) {
        adjDBottom += deficit;
      } else if (dTop != 0 && dBottom == 0) {
        adjDTop -= deficit;
      } else if (dTop != 0) {
        adjDTop -= deficit;
      } else {
        adjDBottom += deficit;
      }
    }
    newW = Math.max(minPx, originWidth - adjDLeft + adjDRight);
    newH = Math.max(minPx, originHeight - adjDTop + adjDBottom);

    // Left edge
    if (adjDLeft != 0 || (layout.getLeft() == null && (adjDRight != 0 || originWidth > 0))) {
      if (layout.getLeft() == null) {
        layout.setLeft(new HAttachment(null, 0, originX + adjDLeft, HAttachment.Alignment.LEFT));
      } else {
        layout.getLeft().setOffset(layout.getLeft().getOffset() + adjDLeft);
      }
    }

    // Top edge
    if (adjDTop != 0 || (layout.getTop() == null && (adjDBottom != 0 || originHeight > 0))) {
      if (layout.getTop() == null) {
        layout.setTop(new HAttachment(null, 0, originY + adjDTop, HAttachment.Alignment.TOP));
      } else {
        layout.getTop().setOffset(layout.getTop().getOffset() + adjDTop);
      }
    }

    // Right edge — create absolute size box when missing (width = right.offset - x for LEFT align)
    if (adjDRight != 0 || adjDLeft != 0) {
      if (layout.getRight() == null) {
        layout.setRight(
            new HAttachment(null, 0, originX + adjDLeft + newW, HAttachment.Alignment.LEFT));
      } else if (adjDRight != 0) {
        // Page-space edge movement maps 1:1 onto offset for page anchors; relative
        // componentName anchors still receive the offset nudge.
        layout.getRight().setOffset(layout.getRight().getOffset() + adjDRight);
      }
    }

    // Bottom edge
    if (adjDBottom != 0 || adjDTop != 0) {
      if (layout.getBottom() == null) {
        layout.setBottom(
            new HAttachment(null, 0, originY + adjDTop + newH, HAttachment.Alignment.TOP));
      } else if (adjDBottom != 0) {
        layout.getBottom().setOffset(layout.getBottom().getOffset() + adjDBottom);
      }
    }
  }

  /**
   * Page geometry, presentation-level header/footer flags, and top-level components for the page
   * properties panel.
   *
   * @param name presentation metadata name
   * @param logicalIndex 0-based index into {@link HPresentation#getPages()}
   */
  @GET
  @Path("/{name}/pages/{logicalIndex}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPageProperties(
      @PathParam("name") String name, @PathParam("logicalIndex") int logicalIndex) {
    try {
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      HPage page = requireBodyPage(presentation, logicalIndex, name);
      if (page == null) {
        return getServerError(
            "Invalid logical page index " + logicalIndex + " for presentation '" + name + "'",
            false);
      }
      return Response.ok(
              MAPPER.writeValueAsString(pagePropertiesPayload(presentation, logicalIndex, page)))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error loading page " + logicalIndex + " for presentation '" + name + "'", e);
    }
  }

  /**
   * Update page width/height/margins and optional presentation header/footer. Does not replace the
   * page's component list.
   *
   * <p>Body fields (all optional except when changing size): {@code width}, {@code height}, {@code
   * leftMargin}, {@code rightMargin}, {@code topMargin}, {@code bottomMargin}, {@code
   * header}/{@code footer} as for the header-footer endpoint.
   */
  @POST
  @Path("/{name}/pages/{logicalIndex}/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updatePageProperties(
      @PathParam("name") String name,
      @PathParam("logicalIndex") int logicalIndex,
      Map<String, Object> body) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);
      HPage page = requireBodyPage(presentation, logicalIndex, name);
      if (page == null) {
        return getServerError(
            "Invalid logical page index " + logicalIndex + " for presentation '" + name + "'",
            false);
      }

      int width =
          body != null && body.get("width") != null
              ? toInt(body.get("width"), page.getWidth())
              : page.getWidth();
      int height =
          body != null && body.get("height") != null
              ? toInt(body.get("height"), page.getHeight())
              : page.getHeight();
      int lm =
          body != null && body.get("leftMargin") != null
              ? toInt(body.get("leftMargin"), page.getLeftMargin())
              : page.getLeftMargin();
      int rm =
          body != null && body.get("rightMargin") != null
              ? toInt(body.get("rightMargin"), page.getRightMargin())
              : page.getRightMargin();
      int tm =
          body != null && body.get("topMargin") != null
              ? toInt(body.get("topMargin"), page.getTopMargin())
              : page.getTopMargin();
      int bm =
          body != null && body.get("bottomMargin") != null
              ? toInt(body.get("bottomMargin"), page.getBottomMargin())
              : page.getBottomMargin();

      String geomError = validatePageGeometry(width, height, lm, rm, tm, bm);
      if (geomError != null) {
        return getServerError(geomError, false);
      }

      page.setWidth(width);
      page.setHeight(height);
      page.setLeftMargin(lm);
      page.setRightMargin(rm);
      page.setTopMargin(tm);
      page.setBottomMargin(bm);

      boolean portrait = height >= width;
      int defaultHeaderH = 50;
      int defaultFooterH = 25;
      if (body != null) {
        applyHeaderFooterToggle(
            presentation, true, asMap(body.get("header")), portrait, defaultHeaderH);
        applyHeaderFooterToggle(
            presentation, false, asMap(body.get("footer")), portrait, defaultFooterH);
      }

      saveWithUndo(serializer, presentation, name, beforeJson);
      hopperRest
          .getLog()
          .logBasic(
              "Updated page "
                  + logicalIndex
                  + " geometry for presentation '"
                  + name
                  + "' ("
                  + width
                  + "x"
                  + height
                  + ")");

      return Response.ok(
              MAPPER.writeValueAsString(pagePropertiesPayload(presentation, logicalIndex, page)))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error updating page " + logicalIndex + " for presentation '" + name + "'", e);
    }
  }

  /**
   * Append a new body page (copy size/margins from the last page, or A4 landscape defaults). Body
   * optional: {@code afterIndex} (0-based; insert after that page; omit to append), plus optional
   * width/height/margins.
   */
  @POST
  @Path("/{name}/pages/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response addPage(@PathParam("name") String name, Map<String, Object> body) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);
      List<HPage> pages = presentation.getPages();
      if (pages == null) {
        pages = new ArrayList<>();
        presentation.setPages(pages);
      }

      HPage template = !pages.isEmpty() ? pages.getLast() : HPage.getA4(false);
      int width =
          body != null && body.get("width") != null
              ? toInt(body.get("width"), template.getWidth())
              : template.getWidth();
      int height =
          body != null && body.get("height") != null
              ? toInt(body.get("height"), template.getHeight())
              : template.getHeight();
      int lm =
          body != null && body.get("leftMargin") != null
              ? toInt(body.get("leftMargin"), template.getLeftMargin())
              : template.getLeftMargin();
      int rm =
          body != null && body.get("rightMargin") != null
              ? toInt(body.get("rightMargin"), template.getRightMargin())
              : template.getRightMargin();
      int tm =
          body != null && body.get("topMargin") != null
              ? toInt(body.get("topMargin"), template.getTopMargin())
              : template.getTopMargin();
      int bm =
          body != null && body.get("bottomMargin") != null
              ? toInt(body.get("bottomMargin"), template.getBottomMargin())
              : template.getBottomMargin();

      String geomError = validatePageGeometry(width, height, lm, rm, tm, bm);
      if (geomError != null) {
        return getServerError(geomError, false);
      }

      HPage neu = new HPage(width, height, lm, rm, tm, bm);
      neu.setHeader(false);
      neu.setFooter(false);

      int insertAt = pages.size();
      if (body != null && body.get("afterIndex") != null) {
        int after = toInt(body.get("afterIndex"), pages.size() - 1);
        // insert after that page (or at end); clamp into [0, size]
        insertAt = Math.clamp(after + 1, 0, pages.size());
      }
      pages.add(insertAt, neu);
      saveWithUndo(serializer, presentation, name, beforeJson);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("logicalIndex", insertAt);
      out.put("pageCount", pages.size());
      out.put("label", "Page " + (insertAt + 1));
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error adding page to presentation '" + name + "'", e);
    }
  }

  /** Delete a body page. Refuses when it is the only page. */
  @DELETE
  @Path("/{name}/pages/{logicalIndex}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response deletePage(
      @PathParam("name") String name, @PathParam("logicalIndex") int logicalIndex) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);
      List<HPage> pages = presentation.getPages();
      if (pages == null || logicalIndex < 0 || logicalIndex >= pages.size()) {
        return getServerError(
            "Invalid logical page index " + logicalIndex + " for presentation '" + name + "'",
            false);
      }
      if (pages.size() <= 1) {
        return getServerError("Cannot delete the only page of a presentation", false);
      }
      pages.remove(logicalIndex);
      saveWithUndo(serializer, presentation, name, beforeJson);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("pageCount", pages.size());
      out.put("deletedIndex", logicalIndex);
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error deleting page " + logicalIndex + " from presentation '" + name + "'", e);
    }
  }

  /**
   * Move a body page up or down. Body: {@code { "direction": "up"|"down" }} or {@code { "delta":
   * -1|1 }}.
   */
  @POST
  @Path("/{name}/pages/{logicalIndex}/move/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response movePage(
      @PathParam("name") String name,
      @PathParam("logicalIndex") int logicalIndex,
      Map<String, Object> body) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);
      List<HPage> pages = presentation.getPages();
      if (pages == null || logicalIndex < 0 || logicalIndex >= pages.size()) {
        return getServerError(
            "Invalid logical page index " + logicalIndex + " for presentation '" + name + "'",
            false);
      }
      int delta = 0;
      if (body != null && body.get("delta") != null) {
        delta = toInt(body.get("delta"), 0);
      } else if (body != null && body.get("direction") != null) {
        String dir = String.valueOf(body.get("direction")).trim().toLowerCase();
        if ("up".equals(dir) || "earlier".equals(dir)) {
          delta = -1;
        } else if ("down".equals(dir) || "later".equals(dir)) {
          delta = 1;
        }
      }
      int target = logicalIndex + delta;
      if (delta == 0 || target < 0 || target >= pages.size()) {
        Map<String, Object> noop = new LinkedHashMap<>();
        noop.put("logicalIndex", logicalIndex);
        noop.put("pageCount", pages.size());
        noop.put("moved", false);
        return Response.ok(MAPPER.writeValueAsString(noop))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }
      HPage page = pages.remove(logicalIndex);
      pages.add(target, page);
      saveWithUndo(serializer, presentation, name, beforeJson);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("logicalIndex", target);
      out.put("pageCount", pages.size());
      out.put("moved", true);
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error moving page " + logicalIndex + " in presentation '" + name + "'", e);
    }
  }

  private static HPage requireBodyPage(HPresentation presentation, int logicalIndex, String name) {
    List<HPage> pages = presentation.getPages();
    if (pages == null || logicalIndex < 0 || logicalIndex >= pages.size()) {
      return null;
    }
    return pages.get(logicalIndex);
  }

  private static String validatePageGeometry(
      int width, int height, int lm, int rm, int tm, int bm) {
    if (width < 1 || height < 1) {
      return "Page width and height must be at least 1";
    }
    if (lm < 0 || rm < 0 || tm < 0 || bm < 0) {
      return "Margins must not be negative";
    }
    if (lm + rm >= width) {
      return "Left + right margins must be less than page width";
    }
    if (tm + bm >= height) {
      return "Top + bottom margins must be less than page height";
    }
    return null;
  }

  private Map<String, Object> pagePropertiesPayload(
      HPresentation presentation, int logicalIndex, HPage page) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<HPage> pages = presentation.getPages();
    int pageCount = pages != null ? pages.size() : 0;
    out.put("logicalIndex", logicalIndex);
    out.put("label", "Page " + (logicalIndex + 1));
    out.put("pageCount", pageCount);
    out.put("width", page.getWidth());
    out.put("height", page.getHeight());
    out.put("leftMargin", page.getLeftMargin());
    out.put("rightMargin", page.getRightMargin());
    out.put("topMargin", page.getTopMargin());
    out.put("bottomMargin", page.getBottomMargin());

    Map<String, Object> hf = headerFooterState(presentation);
    out.put("header", hf.get("header"));
    out.put("footer", hf.get("footer"));

    List<Map<String, Object>> rows = new ArrayList<>();
    if (page.getComponents() != null) {
      for (HComponent component : page.getComponents()) {
        rows.add(toComponentSummary(component));
      }
    }
    // Annotate layout problems from the live rendering when available
    try {
      String presName = presentation != null ? presentation.getName() : null;
      if (presName != null) {
        IRendering existing = hopperRest.findRendering(presName, Collections.emptyList());
        if (existing != null
            && existing.getLayoutResults() != null
            && existing.getLayoutResults().getRenderPages() != null) {
          enrichComponentsWithLayoutStatus(
              rows, existing.getLayoutResults().getRenderPages());
        }
      }
    } catch (Exception ignored) {
      // Layout status is best-effort; page properties still work without it
    }
    out.put("components", rows);
    return out;
  }

  /**
   * Components on a logical presentation page (name + plugin id/name) for the editor list.
   *
   * @param name presentation metadata name
   * @param logicalIndex 0-based index into {@link HPresentation#getPages()}
   */
  @GET
  @Path("/{name}/pages/{logicalIndex}/components/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listPageComponents(
      @PathParam("name") String name, @PathParam("logicalIndex") int logicalIndex) {
    try {
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      List<HPage> pages = presentation.getPages();
      if (pages == null || logicalIndex < 0 || logicalIndex >= pages.size()) {
        return getServerError(
            "Invalid logical page index " + logicalIndex + " for presentation '" + name + "'",
            false);
      }
      HPage page = pages.get(logicalIndex);
      List<Map<String, Object>> rows = new ArrayList<>();
      if (page.getComponents() != null) {
        for (HComponent component : page.getComponents()) {
          rows.add(toComponentSummary(component));
        }
      }
      return Response.ok(MAPPER.writeValueAsString(rows))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error listing components for presentation '" + name + "' page " + logicalIndex, e);
    }
  }

  /**
   * Add a new component on a logical page at page-absolute coordinates.
   *
   * <p>Body: {@code { "pluginId": "HLabelComponent", "x": 100, "y": 80, "name": "optional" }}.
   * Layout is page-absolute left/top attachments (offsets = drop x/y). Returns {@code { name,
   * pluginId, logicalPageNumber }}.
   */
  @POST
  @Path("/{name}/pages/{logicalIndex}/components/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response addComponentOnLogicalPage(
      @PathParam("name") String name,
      @PathParam("logicalIndex") int logicalIndex,
      Map<String, Object> body) {
    try {
      return addComponentInternal(name, logicalIndex, body);
    } catch (Exception e) {
      return getServerError(
          "Error adding component to presentation '" + name + "' page " + logicalIndex, e);
    }
  }

  /**
   * Move a top-level component up or down in the page's component list (z-order / editor order).
   * Body: {@code { "direction": "up"|"down" }} or {@code { "delta": -1|1 }}.
   */
  @POST
  @Path("/{name}/pages/{logicalIndex}/components/{componentName}/move/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response moveComponentOnPage(
      @PathParam("name") String name,
      @PathParam("logicalIndex") int logicalIndex,
      @PathParam("componentName") String componentName,
      Map<String, Object> body) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);
      HPage page = requireBodyPage(presentation, logicalIndex, name);
      if (page == null) {
        return getServerError(
            "Invalid logical page index " + logicalIndex + " for presentation '" + name + "'",
            false);
      }
      List<HComponent> components = page.getComponents();
      if (components == null || components.isEmpty()) {
        return getServerError("Page has no components", false);
      }
      int index = -1;
      for (int i = 0; i < components.size(); i++) {
        HComponent c = components.get(i);
        if (c != null && componentName.equalsIgnoreCase(c.getName())) {
          index = i;
          break;
        }
      }
      if (index < 0) {
        return getServerError(
            "Component '" + componentName + "' not found on page " + logicalIndex, false);
      }
      int delta = 0;
      if (body != null && body.get("delta") != null) {
        delta = toInt(body.get("delta"), 0);
      } else if (body != null && body.get("direction") != null) {
        String dir = String.valueOf(body.get("direction")).trim().toLowerCase();
        if ("up".equals(dir) || "earlier".equals(dir)) {
          delta = -1;
        } else if ("down".equals(dir) || "later".equals(dir)) {
          delta = 1;
        }
      }
      int target = index + delta;
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("name", components.get(index).getName());
      out.put("fromIndex", index);
      if (delta == 0 || target < 0 || target >= components.size()) {
        out.put("toIndex", index);
        out.put("moved", false);
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }
      HComponent moved = components.remove(index);
      components.add(target, moved);
      saveWithUndo(serializer, presentation, name, beforeJson);
      out.put("toIndex", target);
      out.put("moved", true);
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error moving component '"
              + componentName
              + "' on page "
              + logicalIndex
              + " of presentation '"
              + name
              + "'",
          e);
    }
  }

  /**
   * Add a component on the body page that corresponds to a render (physical) page. Preferred by the
   * palette drop handler when only {@code renderId} is known.
   */
  @POST
  @Path("/by-render/{renderId}/pages/{pageNumber}/components/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response addComponentOnRenderPage(
      @PathParam("renderId") String renderId,
      @PathParam("pageNumber") int pageNumber,
      Map<String, Object> body) {
    try {
      IRendering rendering = hopperRest.getRendering(renderId);
      if (rendering == null) {
        return getServerError("Unable to find rendering with ID " + renderId, false);
      }
      List<HRenderPage> renderPages = rendering.getLayoutResults().getRenderPages();
      if (pageNumber < 0 || pageNumber >= renderPages.size()) {
        return getServerError("Invalid render page " + pageNumber, false);
      }
      HPage renderBody = renderPages.get(pageNumber).getPage();
      HPresentation rendered = rendering.getPresentation();
      int logicalIndex = resolveLogicalPageIndex(rendered, renderBody, pageNumber);
      String presentationName = rendering.getPresentationName();
      if (StringUtils.isBlank(presentationName) && rendered != null) {
        presentationName = rendered.getName();
      }
      if (StringUtils.isBlank(presentationName)) {
        return getServerError("Rendering has no presentation name", false);
      }
      return addComponentInternal(presentationName, logicalIndex, body);
    } catch (Exception e) {
      return getServerError(
          "Error adding component on render " + renderId + " page " + pageNumber, e);
    }
  }

  /**
   * Paste (clone) a full component JSON onto a logical page. Body: {@code hopperComponentJson}
   * (string or object), optional {@code pageRole}, optional absolute {@code x}/{@code y} (page
   * pixels for the component top-left), or {@code dx}/{@code dy} offset from the cloned layout
   * (default 20 when x/y omitted).
   */
  @POST
  @Path("/{name}/pages/{logicalIndex}/components/paste/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response pasteComponentOnLogicalPage(
      @PathParam("name") String name,
      @PathParam("logicalIndex") int logicalIndex,
      Map<String, Object> body) {
    try {
      return pasteComponentInternal(name, logicalIndex, body);
    } catch (Exception e) {
      return getServerError(
          "Error pasting component into presentation '" + name + "' page " + logicalIndex, e);
    }
  }

  /**
   * Paste a component onto the body page for a render page (editor canvas path).
   */
  @POST
  @Path("/by-render/{renderId}/pages/{pageNumber}/components/paste/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response pasteComponentOnRenderPage(
      @PathParam("renderId") String renderId,
      @PathParam("pageNumber") int pageNumber,
      Map<String, Object> body) {
    try {
      IRendering rendering = hopperRest.getRendering(renderId);
      if (rendering == null) {
        return getServerError("Unable to find rendering with ID " + renderId, false);
      }
      List<HRenderPage> renderPages = rendering.getLayoutResults().getRenderPages();
      if (pageNumber < 0 || pageNumber >= renderPages.size()) {
        return getServerError("Invalid render page " + pageNumber, false);
      }
      HPage renderBody = renderPages.get(pageNumber).getPage();
      HPresentation rendered = rendering.getPresentation();
      int logicalIndex = resolveLogicalPageIndex(rendered, renderBody, pageNumber);
      String presentationName = rendering.getPresentationName();
      if (StringUtils.isBlank(presentationName) && rendered != null) {
        presentationName = rendered.getName();
      }
      if (StringUtils.isBlank(presentationName)) {
        return getServerError("Rendering has no presentation name", false);
      }
      return pasteComponentInternal(presentationName, logicalIndex, body);
    } catch (Exception e) {
      return getServerError(
          "Error pasting component on render " + renderId + " page " + pageNumber, e);
    }
  }

  private Response pasteComponentInternal(String name, int logicalIndex, Map<String, Object> body)
      throws Exception {
    if (body == null || body.get("hopperComponentJson") == null) {
      return getServerError("Request body must include \"hopperComponentJson\"", false);
    }
    Object rawJson = body.get("hopperComponentJson");
    String componentJson;
    if (rawJson instanceof String s) {
      componentJson = s;
    } else {
      componentJson = MAPPER.writeValueAsString(rawJson);
    }
    if (StringUtils.isBlank(componentJson)) {
      return getServerError("hopperComponentJson is empty", false);
    }
    boolean hasAbsPos = body.get("x") != null && body.get("y") != null;
    int absX = hasAbsPos ? toInt(body.get("x"), 0) : 0;
    int absY = hasAbsPos ? toInt(body.get("y"), 0) : 0;
    int dx = toInt(body.get("dx"), 20);
    int dy = toInt(body.get("dy"), 20);
    String pageRole =
        body.get("pageRole") != null
            ? String.valueOf(body.get("pageRole")).trim().toLowerCase()
            : "page";

    IHopMetadataSerializer<HPresentation> serializer =
        hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
    HPresentation presentation = serializer.load(name);
    if (presentation == null) {
      return getServerError("Presentation not found: " + name, false);
    }
    String beforeJson = snapshotPresentation(presentation);
    List<HPage> pages = presentation.getPages();
    if (pages == null || pages.isEmpty()) {
      return getServerError("Presentation '" + name + "' has no body pages", false);
    }
    if (logicalIndex < 0 || logicalIndex >= pages.size()) {
      logicalIndex = 0;
    }
    HPage bodyPage = pages.get(logicalIndex);
    HPage page;
    if ("header".equals(pageRole)) {
      page = presentation.getHeader();
      if (page == null) {
        return getServerError("Presentation has no header — enable it first", false);
      }
    } else if ("footer".equals(pageRole)) {
      page = presentation.getFooter();
      if (page == null) {
        return getServerError("Presentation has no footer — enable it first", false);
      }
    } else {
      page = bodyPage;
      pageRole = "page";
    }

    JsonMetadataParser<HComponent> parser =
        new JsonMetadataParser<>(HComponent.class, hopperRest.getMetadataProvider());
    HComponent hopperComponent =
        parser.loadJsonObject(HComponent.class, new JsonFactory().createParser(componentJson));
    if (hopperComponent == null) {
      return getServerError("Could not parse hopperComponentJson", false);
    }

    String baseName =
        StringUtils.isNotBlank(hopperComponent.getName())
            ? hopperComponent.getName()
            : "Component";
    String componentName = uniqueComponentName(presentation, baseName);
    hopperComponent.setName(componentName);
    if (hasAbsPos) {
      // Ctrl+V at cursor: absolute page position (top-left); drop peer anchors
      placeComponentAtAbsolute(hopperComponent, absX, absY);
    } else {
      applyOffsetNudge(hopperComponent, dx, dy);
    }

    if (page.getComponents() == null) {
      page.setComponents(new ArrayList<>());
    }
    page.getComponents().add(hopperComponent);
    saveWithUndo(serializer, presentation, name, beforeJson);

    hopperRest
        .getLog()
        .logBasic(
            "paste component: '"
                + componentName
                + "' role="
                + pageRole
                + (hasAbsPos
                    ? " at=(" + absX + "," + absY + ")"
                    : " offset=(" + dx + "," + dy + ")")
                + " into '"
                + name
                + "'");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", componentName);
    String pluginId = null;
    if (hopperComponent.getComponent() != null) {
      pluginId = hopperComponent.getComponent().getPluginId();
    }
    result.put("pluginId", pluginId != null ? pluginId : "");
    result.put("logicalPageNumber", "page".equals(pageRole) ? logicalIndex : -1);
    result.put("pageRole", pageRole);
    result.put("dx", dx);
    result.put("dy", dy);
    return Response.ok(MAPPER.writeValueAsString(result))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .encoding("UTF-8")
        .build();
  }

  private Response addComponentInternal(String name, int logicalIndex, Map<String, Object> body)
      throws Exception {
    if (body == null
        || body.get("pluginId") == null
        || StringUtils.isBlank(String.valueOf(body.get("pluginId")))) {
      return getServerError("Request body must include non-empty \"pluginId\"", false);
    }
    String pluginId = String.valueOf(body.get("pluginId")).trim();
    int x = toInt(body.get("x"), 50);
    int y = toInt(body.get("y"), 50);
    String requestedName =
        body.get("name") != null ? String.valueOf(body.get("name")).trim() : null;
    String pageRole =
        body.get("pageRole") != null
            ? String.valueOf(body.get("pageRole")).trim().toLowerCase()
            : "page";

    IHopMetadataSerializer<HPresentation> serializer =
        hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
    HPresentation presentation = serializer.load(name);
    if (presentation == null) {
      return getServerError("Presentation not found: " + name, false);
    }
    String beforeJson = snapshotPresentation(presentation);
    List<HPage> pages = presentation.getPages();
    if (pages == null || pages.isEmpty()) {
      return getServerError("Presentation '" + name + "' has no body pages", false);
    }
    if (logicalIndex < 0 || logicalIndex >= pages.size()) {
      logicalIndex = 0;
    }
    HPage bodyPage = pages.get(logicalIndex);
    HPage page;
    if ("header".equals(pageRole)) {
      page = presentation.getHeader();
      if (page == null) {
        return getServerError("Presentation has no header — enable it first", false);
      }
      // Drop coordinates are page-absolute; convert to header-local
      int lm = bodyPage.getLeftMargin();
      int tm = bodyPage.getTopMargin();
      x = Math.max(0, x - lm);
      y = Math.max(0, y - tm);
    } else if ("footer".equals(pageRole)) {
      page = presentation.getFooter();
      if (page == null) {
        return getServerError("Presentation has no footer — enable it first", false);
      }
      int lm = bodyPage.getLeftMargin();
      int footerTop = bodyPage.getHeight() - bodyPage.getBottomMargin() - page.getHeight();
      x = Math.max(0, x - lm);
      y = Math.max(0, y - footerTop);
    } else {
      page = bodyPage;
      pageRole = "page";
    }

    PluginRegistry registry = PluginRegistry.getInstance();
    IPlugin plugin = registry.findPluginWithId(HComponentPluginType.class, pluginId);
    if (plugin == null) {
      return getServerError("Unknown component plugin id: " + pluginId, false);
    }
    IHComponent iComponent = (IHComponent) registry.loadClass(plugin);
    if (iComponent == null) {
      return getServerError("Could not instantiate component plugin: " + pluginId, false);
    }
    if (StringUtils.isBlank(iComponent.getPluginId())) {
      iComponent.setPluginId(pluginId);
    }
    // Sensible defaults for empty label text
    if (iComponent instanceof HLabelComponent label) {
      if (StringUtils.isBlank(label.getLabel())) {
        label.setLabel(plugin.getName() != null ? plugin.getName() : "Label");
      }
    }
    // Leave themeName blank so render picks presentation light/dark by colorMode.
    // Stamping defaultThemeName would lock new components to the light catalog theme.

    String baseName =
        StringUtils.isNotBlank(requestedName)
            ? requestedName
            : (plugin.getName() != null ? plugin.getName() : pluginId);
    String componentName = uniqueComponentName(presentation, baseName);

    HComponent hopperComponent = new HComponent(componentName, iComponent);
    HLayout layout = new HLayout();
    layout.setLeft(new HAttachment(null, 0, x, HAttachment.Alignment.LEFT));
    layout.setTop(new HAttachment(null, 0, y, HAttachment.Alignment.TOP));
    // Charts/tables/etc. have no natural size without data — give a default box so they are visible
    // after drop. Labels compute size from text and stay point-sized (left/top only).
    if (!(iComponent instanceof HLabelComponent)) {
      int defaultW = defaultDropWidth(pluginId);
      int defaultH = defaultDropHeight(pluginId);
      // right attachment with LEFT alignment: width = offset - x  (page origin geometry)
      layout.setRight(new HAttachment(null, 0, x + defaultW, HAttachment.Alignment.LEFT));
      layout.setBottom(new HAttachment(null, 0, y + defaultH, HAttachment.Alignment.TOP));
    }
    hopperComponent.setLayout(layout);

    if (page.getComponents() == null) {
      page.setComponents(new ArrayList<>());
    }
    page.getComponents().add(hopperComponent);
    saveWithUndo(serializer, presentation, name, beforeJson);

    hopperRest
        .getLog()
        .logBasic(
            "add component: '"
                + componentName
                + "' ("
                + pluginId
                + ") at ("
                + x
                + ","
                + y
                + ") role="
                + pageRole
                + " of '"
                + name
                + "'");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", componentName);
    result.put("pluginId", pluginId);
    result.put("logicalPageNumber", "page".equals(pageRole) ? logicalIndex : -1);
    result.put("pageRole", pageRole);
    result.put("x", x);
    result.put("y", y);
    return Response.ok(MAPPER.writeValueAsString(result))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .encoding("UTF-8")
        .build();
  }

  /** Prefer identity match on the rendered presentation; fall back to clamped page number. */
  private static int resolveLogicalPageIndex(
      HPresentation rendered, HPage renderBody, int pageNumber) {
    if (rendered != null && rendered.getPages() != null && renderBody != null) {
      for (int i = 0; i < rendered.getPages().size(); i++) {
        if (rendered.getPages().get(i) == renderBody) {
          return i;
        }
      }
    }
    if (rendered == null || rendered.getPages() == null || rendered.getPages().isEmpty()) {
      return 0;
    }
    return Math.clamp(pageNumber, 0, rendered.getPages().size() - 1);
  }

  /**
   * Unique component name across all body pages, header, and footer. Uses {@code base}, {@code base
   * 2}, {@code base 3}, …
   */
  private static String uniqueComponentName(HPresentation presentation, String base) {
    String seed = StringUtils.isBlank(base) ? "Component" : base.trim();
    Set<String> used = collectComponentNames(presentation);
    if (!usedContainsIgnoreCase(used, seed)) {
      return seed;
    }
    int n = 2;
    while (usedContainsIgnoreCase(used, seed + " " + n)) {
      n++;
    }
    return seed + " " + n;
  }

  private static boolean usedContainsIgnoreCase(Set<String> used, String name) {
    for (String u : used) {
      if (u != null && u.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> collectComponentNames(HPresentation presentation) {
    Set<String> names = new HashSet<>();
    if (presentation.getPages() != null) {
      for (HPage p : presentation.getPages()) {
        addPageComponentNames(names, p);
      }
    }
    addPageComponentNames(names, presentation.getHeader());
    addPageComponentNames(names, presentation.getFooter());
    return names;
  }

  private static void addPageComponentNames(Set<String> names, HPage page) {
    if (page == null || page.getComponents() == null) {
      return;
    }
    for (HComponent c : page.getComponents()) {
      if (c != null && c.getName() != null) {
        names.add(c.getName());
      }
    }
  }

  /**
   * Components on the logical page that corresponds to a given render (physical) page, plus
   * optional header/footer markers. Preferred by the editor when only {@code renderId} is known.
   *
   * <p>Each row is annotated with layout status so the editor can list components that failed to
   * place (no layout result, zero-size result, or recorded layout/render error).
   */
  @GET
  @Path("/by-render/{renderId}/pages/{pageNumber}/components/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listComponentsForRenderPage(
      @PathParam("renderId") String renderId, @PathParam("pageNumber") int pageNumber) {
    try {
      IRendering rendering = hopperRest.getRendering(renderId);
      if (rendering == null) {
        return getServerError("Unable to find rendering with ID " + renderId, false);
      }
      List<HRenderPage> renderPages =
          rendering.getLayoutResults() != null
              ? rendering.getLayoutResults().getRenderPages()
              : null;
      if (renderPages == null || pageNumber < 0 || pageNumber >= renderPages.size()) {
        return getServerError("Invalid render page " + pageNumber, false);
      }
      HPage hopperPage = renderPages.get(pageNumber).getPage();
      HPresentation presentation = rendering.getPresentation();
      List<Map<String, Object>> rows = new ArrayList<>();

      // Presentation header components (drawn on every physical page)
      if (presentation != null
          && presentation.getHeader() != null
          && presentation.getHeader().getComponents() != null) {
        for (HComponent component : presentation.getHeader().getComponents()) {
          Map<String, Object> row = toComponentSummary(component);
          row.put("pageRole", "header");
          rows.add(row);
        }
      }

      // Body page components for this render page
      if (hopperPage != null && hopperPage.getComponents() != null) {
        String role = "page";
        if (hopperPage.isHeader()) {
          role = "header";
        } else if (hopperPage.isFooter()) {
          role = "footer";
        }
        for (HComponent component : hopperPage.getComponents()) {
          Map<String, Object> row = toComponentSummary(component);
          row.put("pageRole", role);
          rows.add(row);
        }
      }

      // Presentation footer components
      if (presentation != null
          && presentation.getFooter() != null
          && presentation.getFooter().getComponents() != null) {
        for (HComponent component : presentation.getFooter().getComponents()) {
          Map<String, Object> row = toComponentSummary(component);
          row.put("pageRole", "footer");
          rows.add(row);
        }
      }

      enrichComponentsWithLayoutStatus(rows, renderPages);

      return Response.ok(MAPPER.writeValueAsString(rows))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error listing components for render " + renderId + " page " + pageNumber, e);
    }
  }

  /**
   * Annotate component summary rows with layout placement status from the live rendering.
   *
   * <p>Flags {@code layoutProblem} when the component has a recorded layout/render error, never
   * produced a layout result (or drawn item) on any render page, or only has zero-area geometry
   * (invisible / not selectable on the canvas).
   *
   * <p><b>Header/footer note:</b> header and footer components are laid out into a temporary
   * {@code HLayoutResults} and painted onto each body page; those layout results are not retained
   * on {@link HRenderPage#getLayoutResults()}. Successful header/footer draws still produce
   * {@link org.hopper.core.draw.DrawnItem}s on the body page — we treat those as presence.
   */
  private static void enrichComponentsWithLayoutStatus(
      List<Map<String, Object>> rows, List<HRenderPage> renderPages) {
    if (rows == null || rows.isEmpty() || renderPages == null) {
      return;
    }
    Set<String> present = new HashSet<>();
    Set<String> usable = new HashSet<>();
    Map<String, String> errors = new LinkedHashMap<>();
    Map<String, String> errorDetails = new LinkedHashMap<>();
    Map<String, List<Integer>> pagesByName = new LinkedHashMap<>();

    for (int i = 0; i < renderPages.size(); i++) {
      HRenderPage rp = renderPages.get(i);
      if (rp == null) {
        continue;
      }
      if (rp.getComponentLayoutErrors() != null) {
        for (Map.Entry<String, String> e : rp.getComponentLayoutErrors().entrySet()) {
          if (e.getKey() == null || e.getKey().isBlank()) {
            continue;
          }
          errors.putIfAbsent(e.getKey(), e.getValue());
        }
      }
      if (rp.getComponentLayoutErrorDetails() != null) {
        for (Map.Entry<String, String> e : rp.getComponentLayoutErrorDetails().entrySet()) {
          if (e.getKey() == null || e.getKey().isBlank()) {
            continue;
          }
          errorDetails.putIfAbsent(e.getKey(), e.getValue());
        }
      }
      if (rp.getLayoutResults() != null) {
        for (org.hopper.presentation.HComponentLayoutResult lr : rp.getLayoutResults()) {
          if (lr == null || lr.getComponent() == null || lr.getComponent().getName() == null) {
            continue;
          }
          String name = lr.getComponent().getName();
          noteComponentPresent(name, i, present, pagesByName);
          // Layout-error data map on the result
          if (lr.getDataMap() != null
              && lr.getDataMap().containsKey(HPresentation.DATA_LAYOUT_ERROR)) {
            Object s = lr.getDataMap().get(HPresentation.DATA_LAYOUT_ERROR);
            if (s != null) {
              errors.putIfAbsent(name, String.valueOf(s));
            }
            Object d = lr.getDataMap().get(HPresentation.DATA_LAYOUT_ERROR_DETAIL);
            if (d != null) {
              errorDetails.putIfAbsent(name, String.valueOf(d));
            }
          }
          if (geometryUsable(lr.getGeometry())) {
            usable.add(name);
          }
        }
      }
      // Drawn items: body layout results + header/footer painted onto the body page
      if (rp.getDrawnItems() != null) {
        for (org.hopper.core.draw.DrawnItem item : rp.getDrawnItems()) {
          if (item == null || item.getComponentName() == null || item.getComponentName().isBlank()) {
            continue;
          }
          // Only component envelope / ink counts as placement (skip guides etc. if any)
          org.hopper.core.draw.DrawnItem.DrawnItemType type = item.getType();
          if (type != org.hopper.core.draw.DrawnItem.DrawnItemType.Component
              && type != org.hopper.core.draw.DrawnItem.DrawnItemType.ComponentItem) {
            continue;
          }
          String name = item.getComponentName();
          noteComponentPresent(name, i, present, pagesByName);
          if (geometryUsable(item.getGeometry())) {
            usable.add(name);
          }
        }
      }
    }

    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      Object nameObj = row.get("name");
      if (!(nameObj instanceof String name) || name.isBlank()) {
        continue;
      }
      List<Integer> pages = pagesByName.getOrDefault(name, List.of());
      row.put("presentOnPages", pages);
      row.put("pageCount", renderPages.size());
      String err = errors.get(name);
      String detail = errorDetails.get(name);
      if (err != null) {
        row.put("layoutProblem", true);
        row.put("layoutError", err);
        if (detail != null) {
          row.put("layoutErrorDetail", detail);
        }
        row.put("layoutProblemReason", "error");
      } else if (!present.contains(name)) {
        row.put("layoutProblem", true);
        row.put(
            "layoutError",
            "No layout result on any page (check attachments, size, or empty data).");
        row.put("layoutProblemReason", "missing");
      } else if (!usable.contains(name)) {
        row.put("layoutProblem", true);
        row.put("layoutError", "Layout result has zero width and height (invisible on canvas).");
        row.put("layoutProblemReason", "zero-size");
      } else {
        row.put("layoutProblem", false);
      }
    }
  }

  private static void noteComponentPresent(
      String name,
      int pageIndex,
      Set<String> present,
      Map<String, List<Integer>> pagesByName) {
    present.add(name);
    pagesByName.computeIfAbsent(name, k -> new ArrayList<>());
    List<Integer> pages = pagesByName.get(name);
    if (!pages.contains(pageIndex)) {
      pages.add(pageIndex);
    }
  }

  private static boolean geometryUsable(org.hopper.core.HGeometry geo) {
    return geo != null && (geo.getWidth() > 0 || geo.getHeight() > 0);
  }

  /**
   * Clear layout-result cache for this presentation and drop any in-memory rendering so the next
   * re-render recomputes everything.
   */
  @POST
  @Path("/{name}/cache/clear/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response clearLayoutCache(@PathParam("name") String name) {
    try {
      org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
          .invalidatePresentation(name);
      IRendering existing = hopperRest.findRendering(name, Collections.emptyList());
      if (existing != null) {
        hopperRest.removeRendering(existing);
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("ok", true);
      body.put("presentation", name);
      hopperRest.getLog().logBasic("Cleared layout cache for presentation '" + name + "'");
      return Response.ok(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error clearing layout cache for presentation '" + name + "'", e);
    }
  }

  /**
   * Re-layout and re-render a presentation after metadata mutations. Returns the new render id and
   * page count so the editor can soft-refresh the canvas without a full navigation.
   */
  @POST
  @Path("/{name}/render/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response reRender(
      @PathParam("name") String name,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("debugTimings") @DefaultValue("false") boolean debugTimings,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("includePageSvg") @DefaultValue("true") boolean includePageSvg) {
    try {
      ensureRenderSession();
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);
      IRendering existing = hopperRest.findRendering(name, Collections.emptyList());
      if (existing != null) {
        hopperRest.removeRendering(existing);
      }
      int cacheHitsBefore =
          org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().getHits();
      int cacheMissesBefore =
          org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().getMisses();
      long wallStart = System.currentTimeMillis();
      IRendering rendering =
          RenderFactory.renderPresentation(
              hopperRest.getLoggingObject(),
              hopperRest.getMetadataProvider(),
              presentation,
              Collections.<HParameter>emptyList(),
              mode,
              false);
      long wallMs = System.currentTimeMillis() - wallStart;
      if (HRenderSession.getCurrent() != null) {
        rendering.setSessionId(HRenderSession.getCurrent());
      }
      hopperRest.storeRendering(rendering);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("renderId", rendering.getId());
      body.put("colorMode", mode.wireValue());
      java.util.List<org.hopper.presentation.layout.HRenderPage> pages =
          rendering.getLayoutResults() != null
              ? rendering.getLayoutResults().getRenderPages()
              : null;
      int pageCount = pages != null ? pages.size() : 0;
      body.put("pageCount", pageCount);
      boolean pagesTruncated =
          rendering.getLayoutResults() != null
              && rendering.getLayoutResults().isPagesTruncated();
      body.put("pagesTruncated", pagesTruncated);
      boolean continuous =
          rendering.isContinuousScroll()
              || (rendering.getLayoutResults() != null
                  && rendering.getLayoutResults().isContinuousScroll())
              || presentation.isContinuousLayout();
      body.put("continuousScroll", continuous);
      body.put(
          "contentWidth",
          rendering.getLayoutResults() != null
              ? rendering.getLayoutResults().getContentWidth()
              : 0);
      body.put(
          "contentHeight",
          rendering.getLayoutResults() != null
              ? rendering.getLayoutResults().getContentHeight()
              : 0);
      body.put(
          "contentTruncated",
          rendering.getLayoutResults() != null
              && (rendering.getLayoutResults().isContentTruncated()
                  || rendering.getLayoutResults().isPagesTruncated()));
      body.put(
          "maxRenderPages",
          rendering.getLayoutResults() != null
              ? rendering.getLayoutResults().getMaxRenderPages()
              : org.hopper.presentation.layout.HLayoutPageLimitSettings.getMaxRenderPages());
      body.put(
          "logicalPageCount",
          presentation.getPages() != null ? presentation.getPages().size() : 0);

      // Inline current page SVG so the editor skips a second GET (was ~1.4s of perceived time)
      int page0 = page;
      if (page0 < 0) {
        page0 = 0;
      }
      if (pageCount > 0 && page0 >= pageCount) {
        page0 = pageCount - 1;
      }
      body.put("pageNumber0", page0);
      // Logical vs rendered page map for designer chrome
      putRenderPageIdentity(body, presentation, pages, page0);
      putComponentRenderPageMap(body, pages);
      Long pngMs = null;
      Integer pagePngBytes = null;
      if (includePageSvg && pages != null && page0 >= 0 && page0 < pages.size()) {
        String svg = pages.get(page0).getSvgXml();
        if (svg != null) {
          body.put("pageSvgChars", svg.length());
          // Prefer PNG for soft-reload: Chromium can take ~1s+ to rasterize dark SVGs via
          // HTMLImageElement/drawImage; PNG decode is ~ms in light and dark. Keep SVG on the
          // page GET API for full fidelity / export.
          long pngStart = System.currentTimeMillis();
          try {
            float pngScale = org.hopper.render.svg.HSvgToPng.DEFAULT_PIXEL_SCALE;
            byte[] png = org.hopper.render.svg.HSvgToPng.toPngBytes(svg, pngScale);
            body.put("pagePngBase64", Base64.getEncoder().encodeToString(png));
            body.put("pagePngScale", pngScale);
            pagePngBytes = png.length;
            body.put("pagePngBytes", pagePngBytes);
            pngMs = System.currentTimeMillis() - pngStart;
          } catch (Exception pngEx) {
            hopperRest
                .getLog()
                .logError("SVG→PNG for soft-reload failed, falling back to SVG", pngEx);
            body.put("pageSvg", svg);
          }
        }
      }

      // Timings: always include a bounded span list for the Gantt panel; richer top table
      // when debugTimings is on.
      boolean wantDetail =
          debugTimings
              || "true".equalsIgnoreCase(System.getProperty("hopper.debug.timings", "false"));
      org.apache.hop.core.logging.ILogChannel layoutLog =
          rendering.getLayoutResults() != null ? rendering.getLayoutResults().getLog() : null;
      // topN > 0 populates both "top" and "spans" (cycle-filtered)
      Map<String, Object> timings =
          org.hopper.core.log.HMetricsUtil.buildTimingsSummary(
              layoutLog, wantDetail ? 40 : 30);
      timings.put("wallMs", wallMs);
      if (pngMs != null) {
        timings.put("pngMs", pngMs);
      }
      if (pagePngBytes != null) {
        timings.put("pagePngBytes", pagePngBytes);
      }
      Map<String, Object> cache = new LinkedHashMap<>();
      cache.put(
          "hits",
          org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().getHits()
              - cacheHitsBefore);
      cache.put(
          "misses",
          org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().getMisses()
              - cacheMissesBefore);
      timings.put("cache", cache);
      // Always keep "spans" for the timings Gantt panel. Drop the heavy "top" table unless debug.
      if (!wantDetail) {
        timings.remove("top");
      }
      body.put("timings", timings);

      // Optional on-disk capture of timing spans as Hop binary rows
      try {
        boolean captured =
            org.hopper.core.log.HTimingsCapture.captureQuietly(name, timings, null, 40);
        if (captured) {
          body.put(
              "timingsFile",
              org.hopper.config.HPresentationDataPaths.timingsLatestFile(name));
        }
      } catch (Exception captureEx) {
        hopperRest
            .getLog()
            .logError("Timings capture failed for '" + name + "': " + captureEx.getMessage());
      }

      return Response.ok(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error re-rendering presentation '" + name + "'", e);
    }
  }

  /**
   * SVG Gantt of the last soft-reload / render timings for a presentation (ephemeral presentation,
   * never saved). Optional POST body may include client phases ({@code xhrMs}, {@code paintMs}, …)
   * and/or an override {@code spans} list.
   */
  @POST
  @Path("/{name}/timings/gantt.svg")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces("image/svg+xml")
  public Response timingsGanttSvg(
      @PathParam("name") String name,
      @QueryParam("width") @DefaultValue("936") int width,
      @QueryParam("height") @DefaultValue("468") int height,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("renderId") String renderId,
      Map<String, Object> body) {
    try {
      return Response.ok(buildTimingsGanttSvg(name, width, height, colorMode, renderId, body))
          .type("image/svg+xml")
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error rendering timings Gantt for '" + name + "'", e);
    }
  }

  /** Same as POST but without client phases (server spans only). */
  @GET
  @Path("/{name}/timings/gantt.svg")
  @Produces("image/svg+xml")
  public Response timingsGanttSvgGet(
      @PathParam("name") String name,
      @QueryParam("width") @DefaultValue("936") int width,
      @QueryParam("height") @DefaultValue("468") int height,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      @QueryParam("renderId") String renderId) {
    try {
      return Response.ok(buildTimingsGanttSvg(name, width, height, colorMode, renderId, null))
          .type("image/svg+xml")
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error rendering timings Gantt for '" + name + "'", e);
    }
  }

  /**
   * JSON summary + Gantt task rows for the timings panel (same data as the SVG endpoint).
   */
  @POST
  @Path("/{name}/timings/summary")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response timingsSummary(
      @PathParam("name") String name,
      @QueryParam("renderId") String renderId,
      Map<String, Object> body) {
    try {
      TimingsGanttBuilt built = buildTimingsGanttModel(name, renderId, body);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("ok", true);
      out.put("presentationName", name);
      out.put("chips", org.hopper.core.log.HTimingsGanttModel.summaryChips(
          built.timings, built.clientPhases));
      out.put("timings", built.timings);
      out.put("taskCount", built.tasks.size());
      List<Map<String, Object>> taskRows = new ArrayList<>();
      for (org.hopper.presentation.component.types.chart.GanttTask t : built.tasks) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", t.getLabel());
        row.put("start", t.getStart());
        row.put("end", t.getEnd());
        row.put("ms", t.duration());
        row.put("group", t.getGroup());
        taskRows.add(row);
      }
      out.put("tasks", taskRows);
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error building timings summary for '" + name + "'", e);
    }
  }

  /**
   * Snapshot the current refresh timings as a full presentation under virtual path {@code System},
   * named {@code {name} - Gantt}. Overwrites if it already exists. Returns view/edit URLs.
   */
  @POST
  @Path("/{name}/timings/save-gantt/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response saveTimingsGanttPresentation(
      @PathParam("name") String name,
      @QueryParam("renderId") String renderId,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode,
      Map<String, Object> body) {
    try {
      HPresentation source = hopperRest.loadPresentation(name);
      if (source == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      TimingsGanttBuilt built = buildTimingsGanttModel(name, renderId, body);
      String ganttName = name + " - Gantt";
      String virtualPath = "System";

      int pageW = toInt(body != null ? body.get("width") : null, 1123);
      int pageH = toInt(body != null ? body.get("height") : null, 794);
      pageW = Math.max(640, pageW);
      pageH = Math.max(400, pageH);

      org.hopper.presentation.component.types.chart.HGanttChartComponent gantt =
          new org.hopper.presentation.component.types.chart.HGanttChartComponent();
      gantt.setTitle("Refresh timings — " + name);
      gantt.setShowingTitle(true);
      gantt.setShowingAxisTicks(true);
      gantt.setShowingDurationLabels(true);
      gantt.setRowHeight(22);
      gantt.setLabelColumnWidth(Math.min(280, pageW / 3));
      if (StringUtils.isNotBlank(source.getDefaultThemeName())) {
        gantt.setThemeName(source.getDefaultThemeName());
      }

      // Prefer Binary Hop rows connector + duration columns when data path is available
      String connectorName = null;
      boolean wiredToFile = false;
      if (org.hopper.config.HPresentationDataPaths.isConfigured()) {
        try {
          org.hopper.core.log.HTimingsCapture.writeTasks(name, built.tasks);
          String hopPath =
              org.hopper.config.HPresentationDataPaths.timingsLatestFile(name);
          connectorName = "System Timings · " + name;
          org.hopper.presentation.connector.types.binary.HBinaryRowsConnector binary =
              new org.hopper.presentation.connector.types.binary.HBinaryRowsConnector();
          // Prefer variable so path follows admin data-path moves
          binary.setFilename(
              "${HOPPER_DATA_PATH}/timings/"
                  + org.hopper.config.HPresentationDataPaths.safeName(name)
                  + "/latest.hoprows");
          org.hopper.presentation.connector.HConnector hConn =
              new org.hopper.presentation.connector.HConnector(connectorName, binary);
          hopperRest
              .getMetadataProvider()
              .getSerializer(org.hopper.presentation.connector.HConnector.class)
              .save(hConn);
          gantt.setSourceConnectorName(connectorName);
          gantt.setTaskColumn(org.hopper.core.log.HTimingsCapture.COL_LABEL);
          gantt.setDurationColumn(org.hopper.core.log.HTimingsCapture.COL_MS);
          gantt.setGroupColumn(org.hopper.core.log.HTimingsCapture.COL_GROUP);
          gantt.setColorKeyColumn(org.hopper.core.log.HTimingsCapture.COL_COLOR_KEY);
          // No embeddedTasks: prefer live BinaryRows file so re-capture updates the Gantt
          gantt.setEmbeddedTasks(null);
          wiredToFile = true;
          hopperRest
              .getLog()
              .logBasic(
                  "Timings Gantt wired to BinaryRows connector '"
                      + connectorName
                      + "' → "
                      + hopPath);
        } catch (Exception wireEx) {
          hopperRest
              .getLog()
              .logError(
                  "Could not wire BinaryRows timings connector, using embedded tasks: "
                      + wireEx.getMessage());
          gantt.setEmbeddedTasks(new ArrayList<>(built.tasks));
        }
      } else {
        // Persistable snapshot when no data path
        gantt.setEmbeddedTasks(new ArrayList<>(built.tasks));
      }

      HComponent wrapper = new HComponent("RefreshTimingsGantt", gantt);
      wrapper.setLayout(org.hopper.presentation.layout.HLayout.fullPage());

      HPage page = new HPage(pageW, pageH, 20, 20, 20, 20);
      page.setHeader(false);
      page.setFooter(false);
      page.getComponents().add(wrapper);

      HPresentation ganttPres = new HPresentation();
      ganttPres.setName(ganttName);
      ganttPres.setVirtualPath(virtualPath);
      ganttPres.setDescription(
          "Refresh timings Gantt snapshot for '"
              + name
              + "' at "
              + java.time.Instant.now());
      ganttPres.setDefaultThemeName(
          StringUtils.isNotBlank(source.getDefaultThemeName())
              ? source.getDefaultThemeName()
              : "Default");
      if (StringUtils.isNotBlank(source.getDarkThemeName())) {
        ganttPres.setDarkThemeName(source.getDarkThemeName());
      }
      ganttPres.setPages(new ArrayList<>());
      ganttPres.getPages().add(page);
      ganttPres.setHeader(null);
      ganttPres.setFooter(null);
      ganttPres.setInteractions(new ArrayList<>());

      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      // Drop any previous live rendering for this gantt name so view is fresh
      IRendering old = hopperRest.findRendering(ganttName, Collections.emptyList());
      if (old != null) {
        hopperRest.removeRendering(old);
      }
      serializer.save(ganttPres);
      hopperRest
          .getLog()
          .logBasic(
              "Saved timings Gantt presentation '"
                  + ganttName
                  + "' (virtualPath="
                  + virtualPath
                  + ", tasks="
                  + built.tasks.size()
                  + ", binaryConnector="
                  + wiredToFile
                  + ")");

      org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);
      ensureRenderSession();
      IRendering rendering =
          RenderFactory.renderPresentation(
              hopperRest.getLoggingObject(),
              hopperRest.getMetadataProvider(),
              ganttPres,
              Collections.emptyList(),
              mode,
              true);
      if (HRenderSession.getCurrent() != null) {
        rendering.setSessionId(HRenderSession.getCurrent());
      }
      hopperRest.storeRendering(rendering);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("ok", true);
      out.put("name", ganttName);
      out.put("virtualPath", virtualPath);
      out.put("taskCount", built.tasks.size());
      out.put("renderId", rendering.getId());
      out.put(
          "viewUrl",
          "/hopper/api/render/p/"
              + java.net.URLEncoder.encode(ganttName, java.nio.charset.StandardCharsets.UTF_8)
                  .replace("+", "%20")
              + "/HTML/0/?colorMode="
              + java.net.URLEncoder.encode(
                  mode.wireValue(), java.nio.charset.StandardCharsets.UTF_8));
      out.put(
          "editUrl",
          "/hopper/api/edit/presentation/"
              + java.net.URLEncoder.encode(ganttName, java.nio.charset.StandardCharsets.UTF_8)
              + "/?colorMode="
              + java.net.URLEncoder.encode(
                  mode.wireValue(), java.nio.charset.StandardCharsets.UTF_8));
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error saving timings Gantt presentation for '" + name + "'", e);
    }
  }

  private String buildTimingsGanttSvg(
      String name,
      int width,
      int height,
      String colorMode,
      String renderId,
      Map<String, Object> body)
      throws Exception {
    TimingsGanttBuilt built = buildTimingsGanttModel(name, renderId, body);
    int pageW = Math.max(200, width > 0 ? width : 936);
    int pageH = Math.max(120, height > 0 ? height : 468);

    org.hopper.presentation.component.types.chart.HGanttChartComponent gantt =
        new org.hopper.presentation.component.types.chart.HGanttChartComponent();
    gantt.setTitle("Refresh timings — " + name);
    gantt.setShowingTitle(true);
    gantt.setShowingAxisTicks(true);
    gantt.setShowingDurationLabels(true);
    gantt.setInlineTasks(built.tasks);
    gantt.setRowHeight(20);
    gantt.setLabelColumnWidth(Math.min(220, pageW / 3));

    HComponent wrapper = new HComponent("RefreshTimingsGantt", gantt);
    wrapper.setLayout(org.hopper.presentation.layout.HLayout.fullPage());

    org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);
    HPresentation source = hopperRest.loadPresentation(name);
    return wrapper.getSvgXml(
        pageW, pageH, hopperRest.getMetadataProvider(), source, mode);
  }

  private TimingsGanttBuilt buildTimingsGanttModel(
      String name, String renderId, Map<String, Object> body) {
    IRendering rendering = null;
    if (StringUtils.isNotBlank(renderId)) {
      rendering = hopperRest.getRendering(renderId);
    }
    if (rendering == null) {
      rendering = hopperRest.findRendering(name, Collections.emptyList());
    }
    Map<String, Object> timings = new LinkedHashMap<>();
    if (rendering != null
        && rendering.getLayoutResults() != null
        && rendering.getLayoutResults().getLog() != null) {
      timings =
          org.hopper.core.log.HMetricsUtil.buildTimingsSummary(
              rendering.getLayoutResults().getLog(), 40);
    }
    // Optional override spans from client
    if (body != null && body.get("spans") instanceof List<?>) {
      timings.put("spans", body.get("spans"));
    }
    Map<String, Object> clientPhases = null;
    if (body != null && body.get("client") instanceof Map<?, ?> m) {
      clientPhases = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (e.getKey() != null) {
          clientPhases.put(String.valueOf(e.getKey()), e.getValue());
        }
      }
    } else if (body != null) {
      // Flat client keys on body
      clientPhases = new LinkedHashMap<>();
      for (String k :
          new String[] {
            "xhrMs", "pngMs", "svgLoadMs", "geometriesMs", "paintMs", "refreshMs", "perceivedMs"
          }) {
        if (body.containsKey(k)) {
          clientPhases.put(k, body.get(k));
        }
      }
      if (clientPhases.isEmpty()) {
        clientPhases = null;
      }
    }
    List<org.hopper.presentation.component.types.chart.GanttTask> tasks =
        org.hopper.core.log.HTimingsGanttModel.fromTimings(timings, clientPhases, 40);
    if (tasks.isEmpty()) {
      // Placeholder so the panel still shows something
      tasks =
          List.of(
              new org.hopper.presentation.component.types.chart.GanttTask(
                  "No timing spans recorded", 0, 1, "Other", "empty"));
    }
    return new TimingsGanttBuilt(timings, clientPhases, tasks);
  }

  private record TimingsGanttBuilt(
      Map<String, Object> timings,
      Map<String, Object> clientPhases,
      List<org.hopper.presentation.component.types.chart.GanttTask> tasks) {}

  /**
   * Render a single component in isolation (SVG) for the property editor preview pane.
   *
   * <p>Builds a fresh in-memory presentation: one page (no header/footer), size from {@code width}
   * / {@code height} (component geometry on the page when provided by the client), hooks up
   * presentation + shared metadata connectors and themes, then renders only that component.
   */
  @GET
  @Path("/{name}/components/{componentName}/preview.svg")
  @Produces("image/svg+xml")
  public Response previewComponent(
      @PathParam("name") String name,
      @PathParam("componentName") String componentName,
      @QueryParam("width") @DefaultValue("0") int width,
      @QueryParam("height") @DefaultValue("0") int height,
      @QueryParam("colorMode") @DefaultValue("light") String colorMode) {
    try {
      HPresentation source = hopperRest.loadPresentation(name);
      if (source == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      FoundComponent found = findComponentAnywhere(source, componentName);
      if (found == null) {
        return getServerError(
            "Component '" + componentName + "' not found in presentation '" + name + "'", false);
      }

      int pageW = width > 0 ? width : 400;
      int pageH = height > 0 ? height : 300;
      // Minimal padding — fullPage layout should nearly fill the frame
      pageW = Math.max(40, pageW + 4);
      pageH = Math.max(40, pageH + 4);

      var metadataProvider = hopperRest.getMetadataProvider();
      org.hopper.core.HColorMode mode = org.hopper.core.HColorMode.fromString(colorMode);

      // Pass source presentation so series colors (getStableColor) match full-page order
      String svg =
          found.component.getSvgXml(pageW, pageH, metadataProvider, source, mode);
      return Response.ok(svg).type("image/svg+xml").encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError(
          "Error rendering preview for component '"
              + componentName
              + "' in presentation '"
              + name
              + "'",
          e);
    }
  }

  /** Load a component by name for the property form (name-based, no canvas hit-test required). */
  @SuppressWarnings("unchecked")
  @GET
  @Path("/{name}/components/{componentName}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getComponentByName(
      @PathParam("name") String name, @PathParam("componentName") String componentName) {
    try {
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      ComponentLookup.Found found = ComponentLookup.find(presentation, null, componentName);
      if (found == null) {
        return getServerError(
            "Component '" + componentName + "' not found in presentation '" + name + "'", false);
      }
      JsonMetadataParser<HComponent> parser =
          new JsonMetadataParser<>(HComponent.class, hopperRest.getMetadataProvider());
      JSONObject componentJson = parser.getJsonObject(found.component);
      JSONObject wrapper = new JSONObject();
      wrapper.put("logicalPageNumber", found.logicalPageNumber);
      wrapper.put("pageRole", found.pageRole);
      wrapper.put("component", componentJson);
      wrapper.put("metadataName", found.component.getName());
      if (found.parentComponent != null) {
        wrapper.put("nested", true);
        wrapper.put("parentName", found.parentComponent.getName());
      } else {
        wrapper.put("nested", false);
      }
      wrapper.put("breadcrumb", ComponentBreadcrumb.buildJson(presentation, found));

      // Prefer layout errors already captured on the cached presentation render
      attachCachedLayoutError(wrapper, name, found.component.getName());

      return Response.ok(wrapper.toJSONString())
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error loading component '" + componentName + "' from presentation '" + name + "'", e);
    }
  }

  /**
   * Possible interaction hit targets for a component (whole-component first, then plugin-specific
   * options from {@link IHComponent#getPossibleInteractionLocations()}).
   */
  @GET
  @Path("/{name}/components/{componentName}/interaction-locations/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getComponentInteractionLocations(
      @PathParam("name") String name, @PathParam("componentName") String componentName) {
    try {
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      ComponentLookup.Found found = ComponentLookup.find(presentation, null, componentName);
      if (found == null) {
        return getServerError(
            "Component '" + componentName + "' not found in presentation '" + name + "'", false);
      }
      HComponent component = found.component;
      IHComponent plugin = component.getComponent();
      String pluginId = plugin != null ? plugin.getPluginId() : null;
      String resolvedName = component.getName() != null ? component.getName() : componentName;

      List<HInteractionLocationOption> locations = new ArrayList<>();
      locations.add(HInteractionLocationOption.wholeComponent());
      if (plugin != null) {
        List<HInteractionLocationOption> fromPlugin = plugin.getPossibleInteractionLocations();
        if (fromPlugin != null) {
          locations.addAll(fromPlugin);
        }
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("componentName", resolvedName);
      out.put("componentPluginId", pluginId);
      out.put("locations", locations);
      return Response.ok(HJson.createMapper().writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError(
          "Error loading interaction locations for component '"
              + componentName
              + "' in presentation '"
              + name
              + "'",
          e);
    }
  }

  /**
   * List presentation interactions, optionally filtered by {@code componentName} (location match).
   */
  @GET
  @Path("/{name}/interactions/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listPresentationInteractions(
      @PathParam("name") String name, @QueryParam("componentName") String componentName) {
    try {
      HPresentation presentation = hopperRest.loadPresentation(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      List<HInteraction> all =
          presentation.getInteractions() != null
              ? presentation.getInteractions()
              : Collections.emptyList();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (int i = 0; i < all.size(); i++) {
        HInteraction ix = all.get(i);
        if (ix == null) {
          continue;
        }
        if (StringUtils.isNotBlank(componentName)
            && (ix.getLocation() == null
                || !componentName.equals(ix.getLocation().getComponentName()))) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", i);
        row.put("interaction", ix);
        rows.add(row);
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("presentationName", presentation.getName());
      out.put("interactions", rows);
      return Response.ok(HJson.createMapper().writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error listing interactions for presentation '" + name + "'", e);
    }
  }

  /**
   * Append or replace a presentation interaction.
   *
   * <p>Body: {@code { "interaction": { ... }, "index": optional non-negative replace index }}. When
   * {@code index} is omitted or negative, the interaction is appended.
   */
  @POST
  @Path("/{name}/interactions/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response savePresentationInteraction(@PathParam("name") String name, String bodyJson) {
    try {
      if (StringUtils.isBlank(bodyJson)) {
        return getServerError("Request body is required", false);
      }
      ObjectMapper mapper = HJson.createMapper();
      @SuppressWarnings("unchecked")
      Map<String, Object> body = mapper.readValue(bodyJson, Map.class);
      Object interactionNode = body.get("interaction");
      if (interactionNode == null) {
        // Allow posting a bare HInteraction JSON
        interactionNode = body;
      }
      HInteraction interaction =
          mapper.convertValue(interactionNode, HInteraction.class);
      if (interaction == null) {
        return getServerError("Could not parse interaction from body", false);
      }
      if (interaction.getLocation() == null
          || StringUtils.isBlank(interaction.getLocation().getComponentName())) {
        return getServerError("Interaction location.componentName is required", false);
      }
      if (interaction.getMethod() == null) {
        return getServerError("Interaction method is required", false);
      }
      if (interaction.getActions() == null) {
        interaction.setActions(new ArrayList<>());
      }

      int replaceIndex = -1;
      if (body.get("index") != null) {
        replaceIndex = toInt(body.get("index"), -1);
      }

      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      String beforeJson = snapshotPresentation(presentation);

      if (presentation.getInteractions() == null) {
        presentation.setInteractions(new ArrayList<>());
      }
      List<HInteraction> list = presentation.getInteractions();
      int savedIndex;
      if (replaceIndex >= 0 && replaceIndex < list.size()) {
        list.set(replaceIndex, interaction);
        savedIndex = replaceIndex;
      } else {
        list.add(interaction);
        savedIndex = list.size() - 1;
      }

      saveWithUndo(serializer, presentation, name, beforeJson);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("presentationName", presentation.getName());
      out.put("index", savedIndex);
      out.put("interaction", interaction);
      out.put("count", list.size());
      return Response.ok(mapper.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error saving interaction on presentation '" + name + "'", e);
    }
  }

  /**
   * Delete a presentation interaction by list index.
   *
   * <p>Body: {@code { "index": n }}.
   */
  @POST
  @Path("/{name}/interactions/delete/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response deletePresentationInteraction(
      @PathParam("name") String name, Map<String, Object> body) {
    try {
      int index = toInt(body != null ? body.get("index") : null, -1);
      if (index < 0) {
        return getServerError("Body must include a non-negative \"index\"", false);
      }
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        return getServerError("Presentation not found: " + name, false);
      }
      List<HInteraction> list = presentation.getInteractions();
      if (list == null || index >= list.size()) {
        return getServerError("Interaction index out of range: " + index, false);
      }
      String beforeJson = snapshotPresentation(presentation);
      list.remove(index);
      saveWithUndo(serializer, presentation, name, beforeJson);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("presentationName", presentation.getName());
      out.put("removedIndex", index);
      out.put("count", list.size());
      return Response.ok(HJson.createMapper().writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error deleting interaction on presentation '" + name + "'", e);
    }
  }

  /**
   * Layout feedback for the property panel: attachment summaries, resolved geometry, and pages
   * where the component appears after full presentation layout.
   */
  @GET
  @Path("/{name}/components/{componentName}/layout-info")
  @Produces(MediaType.APPLICATION_JSON)
  public Response componentLayoutInfo(
      @PathParam("name") String name, @PathParam("componentName") String componentName) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("componentName", componentName);
    out.put("presentationName", name);
    List<String> warnings = new ArrayList<>();
    try {
      HPresentation source = hopperRest.loadPresentation(name);
      if (source == null) {
        out.put("ok", false);
        out.put("warnings", List.of("Presentation not found: " + name));
        return Response.ok(MAPPER.writeValueAsString(out))
            .type("application/json; charset=UTF-8")
            .build();
      }
      FoundComponent found = findComponentAnywhere(source, componentName);
      if (found == null || found.component == null) {
        out.put("ok", false);
        out.put("warnings", List.of("Component not found: " + componentName));
        return Response.ok(MAPPER.writeValueAsString(out))
            .type("application/json; charset=UTF-8")
            .build();
      }
      HComponent component = found.component;
      HLayout layout = component.getLayout();
      Map<String, Object> attachments = new LinkedHashMap<>();
      for (String side : new String[] {"left", "right", "top", "bottom"}) {
        HAttachment att =
            layout == null
                ? null
                : switch (side) {
                  case "left" -> layout.getLeft();
                  case "right" -> layout.getRight();
                  case "top" -> layout.getTop();
                  case "bottom" -> layout.getBottom();
                  default -> null;
                };
        if (att == null) {
          continue;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("enabled", true);
        String rel = att.getComponentName();
        a.put("relativeTo", rel != null && !rel.isBlank() ? rel : null);
        a.put("alignment", att.getAlignment() != null ? att.getAlignment().name() : null);
        a.put("offset", att.getOffset());
        a.put("percentage", att.getPercentage());
        a.put("summary", summarizeAttachment(side, att));
        attachments.put(side, a);
        if (rel != null && !rel.isBlank() && rel.equals(component.getName())) {
          warnings.add(capitalize(side) + " attachment references this component itself.");
        }
      }
      out.put("attachments", attachments);

      // Prefer live rendering if present; otherwise layout once
      IRendering existing = hopperRest.findRendering(name, Collections.emptyList());
      org.hopper.presentation.layout.HLayoutResults layoutResults =
          existing != null ? existing.getLayoutResults() : null;
      if (layoutResults == null) {
        var metadataProvider = hopperRest.getMetadataProvider();
        org.apache.hop.core.logging.LoggingObject loggingObject =
            new org.apache.hop.core.logging.LoggingObject("layoutInfo");
        org.hopper.render.context.PresentationRenderContext renderContext =
            new org.hopper.render.context.PresentationRenderContext(source, metadataProvider);
        layoutResults =
            source.doLayout(
                loggingObject, renderContext, metadataProvider, Collections.emptyList());
      }

      List<Integer> pages = new ArrayList<>();
      org.hopper.core.HGeometry firstGeo = null;
      if (layoutResults != null && layoutResults.getRenderPages() != null) {
        List<HRenderPage> rps = layoutResults.getRenderPages();
        out.put("pageCount", rps.size());
        for (int i = 0; i < rps.size(); i++) {
          HRenderPage rp = rps.get(i);
          if (rp.getLayoutResults() == null) {
            continue;
          }
          for (var lr : rp.getLayoutResults()) {
            if (lr.getComponent() != null
                && componentName.equals(lr.getComponent().getName())
                && lr.getGeometry() != null) {
              pages.add(i);
              if (firstGeo == null) {
                firstGeo = lr.getGeometry();
              }
              break;
            }
          }
        }
      }
      out.put("pages", pages);
      if (firstGeo != null) {
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("x", firstGeo.getX());
        geo.put("y", firstGeo.getY());
        geo.put("width", firstGeo.getWidth());
        geo.put("height", firstGeo.getHeight());
        out.put("resolved", geo);
        if (firstGeo.getWidth() <= 0 || firstGeo.getHeight() <= 0) {
          warnings.add(
              "Resolved size is "
                  + firstGeo.getWidth()
                  + "x"
                  + firstGeo.getHeight()
                  + " px (zero width/height usually means conflicting left/right or top/bottom).");
        }
      } else {
        warnings.add("Component has no layout result on any page (check relative references).");
      }
      int pageCount = out.get("pageCount") instanceof Integer ? (Integer) out.get("pageCount") : 0;
      if (pages.size() == 1 && pageCount > 1 && pages.getFirst() == pageCount - 1) {
        warnings.add(
            "Component is only on the last page ("
                + pageCount
                + "). A multi-page table may have pushed it there — "
                + "relative layout should place non-flowing siblings on page 1.");
      } else if (!pages.isEmpty() && pages.getFirst() > 0) {
        warnings.add(
            "Component first appears on page "
                + (pages.getFirst() + 1)
                + " of "
                + pageCount
                + " (not page 1).");
      }
      // Relative targets that span many pages
      for (Object attObj : attachments.values()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) attObj;
        Object rel = a.get("relativeTo");
        if (rel instanceof String relName && !relName.isBlank() && layoutResults != null) {
          int targetPages = 0;
          for (HRenderPage rp : layoutResults.getRenderPages()) {
            if (rp.getLayoutResults() == null) {
              continue;
            }
            for (var lr : rp.getLayoutResults()) {
              if (lr.getComponent() != null && relName.equals(lr.getComponent().getName())) {
                targetPages++;
                break;
              }
            }
          }
          if (targetPages > 1) {
            warnings.add(
                "Reference \""
                    + relName
                    + "\" spans "
                    + targetPages
                    + " pages. Relative layout uses the first part's geometry.");
          }
        }
      }
      out.put("warnings", warnings);
      out.put("ok", true);
      return Response.ok(MAPPER.writeValueAsString(out))
          .type("application/json; charset=UTF-8")
          .build();
    } catch (Exception e) {
      warnings.add("layout-info failed: " + e.getMessage());
      out.put("ok", false);
      out.put("warnings", warnings);
      try {
        return Response.ok(MAPPER.writeValueAsString(out))
            .type("application/json; charset=UTF-8")
            .build();
      } catch (Exception e2) {
        return getServerError("layout-info failed", e);
      }
    }
  }

  /**
   * Put logical/render page identity for the current soft-reload page into the response body.
   */
  private static void putRenderPageIdentity(
      Map<String, Object> body,
      HPresentation presentation,
      java.util.List<HRenderPage> renderPages,
      int renderPage0) {
    if (body == null) {
      return;
    }
    body.put("renderPageNumber0", renderPage0);
    int logical0 = 0;
    if (presentation != null
        && presentation.getPages() != null
        && renderPages != null
        && renderPage0 >= 0
        && renderPage0 < renderPages.size()) {
      HPage source = renderPages.get(renderPage0).getPage();
      int idx = presentation.getPages().indexOf(source);
      if (idx >= 0) {
        logical0 = idx;
      }
    }
    body.put("logicalPageNumber0", logical0);
  }

  /**
   * Map component metadata name → first render page index (0-based) for auto-navigation after
   * soft-reload.
   */
  private static void putComponentRenderPageMap(
      Map<String, Object> body, java.util.List<HRenderPage> renderPages) {
    if (body == null || renderPages == null) {
      return;
    }
    Map<String, Integer> map = new LinkedHashMap<>();
    for (int i = 0; i < renderPages.size(); i++) {
      HRenderPage rp = renderPages.get(i);
      if (rp.getLayoutResults() == null) {
        continue;
      }
      for (org.hopper.presentation.HComponentLayoutResult lr : rp.getLayoutResults()) {
        if (lr.getComponent() == null || StringUtils.isBlank(lr.getComponent().getName())) {
          continue;
        }
        map.putIfAbsent(lr.getComponent().getName(), i);
      }
    }
    body.put("componentRenderPages", map);
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String summarizeAttachment(String side, HAttachment att) {
    if (att == null) {
      return capitalize(side) + ": (not set)";
    }
    String edge = att.getAlignment() != null ? att.getAlignment().name().toLowerCase() : "default";
    String target =
        att.getComponentName() != null && !att.getComponentName().isBlank()
            ? "\"" + att.getComponentName() + "\""
            : "page";
    StringBuilder sb = new StringBuilder();
    sb.append(capitalize(side)).append(": ").append(edge).append(" edge of ").append(target);
    if (att.getOffset() != 0) {
      sb.append(att.getOffset() > 0 ? " + " : " - ")
          .append(Math.abs(att.getOffset()))
          .append(" px");
    }
    if (att.getPercentage() != 0) {
      sb.append(" + ").append(att.getPercentage()).append("%");
    }
    return sb.toString();
  }

  /**
   * Diagnose layout/render for a single component: re-runs isolated layout and returns any error
   * with full cause chain (for the property-panel error box). Always 200 with JSON.
   */
  @GET
  @Path("/{name}/components/{componentName}/diagnostics")
  @Produces(MediaType.APPLICATION_JSON)
  public Response diagnoseComponent(
      @PathParam("name") String name, @PathParam("componentName") String componentName) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("componentName", componentName);
    out.put("presentationName", name);
    try {
      HPresentation source = hopperRest.loadPresentation(name);
      if (source == null) {
        out.put("ok", false);
        out.put("summary", "Presentation not found: " + name);
        out.put("detail", out.get("summary"));
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }
      FoundComponent found = findComponentAnywhere(source, componentName);
      if (found == null) {
        out.put("ok", false);
        out.put("summary", "Component not found: " + componentName);
        out.put("detail", out.get("summary"));
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }

      // 1) Cached render of the full presentation (same state as the canvas)
      String cachedSummary = null;
      String cachedDetail = null;
      IRendering existing = hopperRest.findRendering(name, Collections.emptyList());
      if (existing != null && existing.getLayoutResults() != null) {
        for (HRenderPage rp : existing.getLayoutResults().getRenderPages()) {
          if (rp == null) {
            continue;
          }
          String s = lookupPageError(rp, found.component.getName(), componentName, false);
          String d = lookupPageError(rp, found.component.getName(), componentName, true);
          if (s != null) {
            cachedSummary = s;
            cachedDetail = d != null ? d : s;
            break;
          }
        }
      }

      // 2) Isolated layout (same path as component preview) for a fresh diagnosis
      var metadataProvider = hopperRest.getMetadataProvider();

      String isolatedSummary = null;
      String isolatedDetail = null;
      try {
        // Reuse getSvgXml path: layout+render; inspect placeholder errors via a small layout run
        org.apache.hop.core.logging.LoggingObject loggingObject =
            new org.apache.hop.core.logging.LoggingObject("componentDiagnostics");
        HPresentation mini = new HPresentation();
        mini.setName("diagnostics:" + componentName);
        mini.setHeader(null);
        mini.setFooter(null);
        mini.setPages(new ArrayList<>());
        mini.setDefaultThemeName(
            source.getDefaultThemeName() != null
                ? source.getDefaultThemeName()
                : org.hopper.core.Constants.DEFAULT_THEME_NAME);
        HPage page = new HPage(400, 300, 0, 0, 0, 0);
        page.setHeader(false);
        page.setFooter(false);
        HComponent previewComponent = new HComponent(found.component);
        previewComponent.setLayout(HLayout.fullPage());
        page.getComponents().add(previewComponent);
        mini.getPages().add(page);

        org.hopper.render.context.PresentationRenderContext renderContext =
            new org.hopper.render.context.PresentationRenderContext(mini, metadataProvider);
        org.hopper.presentation.layout.HLayoutResults results =
            mini.doLayout(loggingObject, renderContext, metadataProvider, Collections.emptyList());
        mini.render(results, metadataProvider, renderContext);

        if (results.getRenderPages() != null) {
          for (HRenderPage rp : results.getRenderPages()) {
            String s = lookupPageError(rp, previewComponent.getName(), componentName, false);
            String d = lookupPageError(rp, previewComponent.getName(), componentName, true);
            if (s != null) {
              isolatedSummary = s;
              isolatedDetail = d != null ? d : s;
              break;
            }
          }
        }
      } catch (Exception isoEx) {
        isolatedSummary = HPresentation.summarizeException(isoEx);
        isolatedDetail = HPresentation.formatExceptionDetail(isoEx);
      }

      String summary = isolatedSummary != null ? isolatedSummary : cachedSummary;
      String detail = isolatedDetail != null ? isolatedDetail : cachedDetail;
      boolean ok = summary == null || summary.isBlank();
      out.put("ok", ok);
      if (!ok) {
        out.put("summary", summary);
        out.put("detail", detail);
      } else {
        out.put("summary", null);
        out.put("detail", null);
      }
      // Source connector for quick context
      if (found.component.getComponent() != null) {
        out.put("sourceConnectorName", found.component.getComponent().getSourceConnectorName());
      }
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      try {
        out.put("ok", false);
        out.put("summary", HPresentation.summarizeException(e));
        out.put("detail", HPresentation.formatExceptionDetail(e));
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      } catch (Exception encodeEx) {
        return getServerError("Error diagnosing component '" + componentName + "'", e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void attachCachedLayoutError(
      JSONObject wrapper, String presentationName, String componentName) {
    try {
      IRendering existing = hopperRest.findRendering(presentationName, Collections.emptyList());
      if (existing == null || existing.getLayoutResults() == null) {
        return;
      }
      for (HRenderPage rp : existing.getLayoutResults().getRenderPages()) {
        String s = lookupPageError(rp, componentName, componentName, false);
        String d = lookupPageError(rp, componentName, componentName, true);
        if (s != null) {
          wrapper.put("layoutError", s);
          wrapper.put("layoutErrorDetail", d != null ? d : s);
          return;
        }
      }
    } catch (Exception ignored) {
      // diagnostics is best-effort
    }
  }

  private static String lookupPageError(
      HRenderPage page, String metadataName, String drawnName, boolean detail) {
    if (page == null) {
      return null;
    }
    Map<String, String> map =
        detail ? page.getComponentLayoutErrorDetails() : page.getComponentLayoutErrors();
    if (map != null) {
      if (metadataName != null && map.containsKey(metadataName)) {
        return map.get(metadataName);
      }
      if (drawnName != null && map.containsKey(drawnName)) {
        return map.get(drawnName);
      }
    }
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

  /**
   * Delete a component from a presentation by name. Removes from the page (or header/footer) where
   * it is found, saves metadata, and returns the presentation name.
   */
  @DELETE
  @Path("/{name}/components/{componentName}/")
  @Produces(MediaType.TEXT_PLAIN)
  public Response deleteComponent(
      @PathParam("name") String name, @PathParam("componentName") String componentName) {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          hopperRest.getMetadataProvider().getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        throw new HException("Presentation not found: " + name);
      }
      String beforeJson = snapshotPresentation(presentation);
      FoundComponent found = findComponentAnywhere(presentation, componentName);
      if (found == null) {
        throw new HException(
            "Component '" + componentName + "' not found in presentation '" + name + "'");
      }
      boolean removed = found.page.getComponents().remove(found.component);
      if (!removed) {
        // try by name match if instance identity differs
        found.page.getComponents().removeIf(c -> componentName.equalsIgnoreCase(c.getName()));
      }
      // Drop layout references to the deleted component from siblings
      for (HComponent sibling : new ArrayList<>(found.page.getComponents())) {
        if (sibling.getLayout() != null) {
          sibling.getLayout().replaceReferences(componentName, null);
        }
      }
      saveWithUndo(serializer, presentation, name, beforeJson);
      hopperRest
          .getLog()
          .logBasic(
              "delete component: removed '" + componentName + "' from presentation '" + name + "'");
      return Response.ok().entity(name).type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      return getServerError(
          "Error deleting component '" + componentName + "' from presentation '" + name + "'", e);
    }
  }

  private Map<String, Object> toComponentSummary(HComponent component) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", component.getName());
    String pluginId = null;
    if (component.getComponent() != null) {
      pluginId = component.getComponent().getPluginId();
    }
    row.put("pluginId", pluginId != null ? pluginId : "");
    row.put("pluginName", resolvePluginName(pluginId));
    return row;
  }

  private String resolvePluginName(String pluginId) {
    if (pluginId == null || pluginId.isBlank()) {
      return "";
    }
    try {
      IPlugin plugin =
          PluginRegistry.getInstance().findPluginWithId(HComponentPluginType.class, pluginId);
      if (plugin != null && plugin.getName() != null) {
        return plugin.getName();
      }
    } catch (Exception ignored) {
      // fall through
    }
    return pluginId;
  }

  private static final class FoundComponent {
    final HPage page;
    final HComponent component;
    final int logicalPageNumber;
    final String pageRole;

    FoundComponent(HPage page, HComponent component, int logicalPageNumber, String pageRole) {
      this.page = page;
      this.component = component;
      this.logicalPageNumber = logicalPageNumber;
      this.pageRole = pageRole;
    }
  }

  private FoundComponent findComponentAnywhere(HPresentation presentation, String componentName)
      throws HException {
    ComponentLookup.Found found = ComponentLookup.find(presentation, null, componentName);
    if (found == null) {
      return null;
    }
    return new FoundComponent(found.page, found.component, found.logicalPageNumber, found.pageRole);
  }

  /** Save presentation and record undo snapshot taken before the mutation. */
  private void saveWithUndo(
      IHopMetadataSerializer<HPresentation> serializer,
      HPresentation presentation,
      String presentationName,
      String beforeJson)
      throws Exception {
    serializer.save(presentation);
    recordPresentationUndo(
        presentationName != null ? presentationName : presentation.getName(), beforeJson);
  }
}
