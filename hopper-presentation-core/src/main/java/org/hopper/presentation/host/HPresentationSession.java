package org.hopper.presentation.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.HColorMode;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.interaction.InteractionLookupResult;
import org.hopper.presentation.interaction.InteractionRegionIndex;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.render.context.PresentationRenderContext;

/**
 * Host-agnostic presentation lifecycle: load, layout, render, page bind, hit-test, and interaction
 * commands. SWT and REST both drive this type instead of duplicating {@code doLayout}/{@code
 * render} and {@code findInteraction}.
 *
 * <p>Zoom and toolbar chrome stay in the host. Page switches re-bind an already-rendered page and
 * do <em>not</em> run layout again.
 */
@Getter
public class HPresentationSession {

  private final ILoggingObject loggingObject;
  private final IHopMetadataProvider metadataProvider;

  private String homePresentationName;
  private String currentPresentationName;
  private HPresentation currentPresentation;
  private List<HParameter> currentParameters = new ArrayList<>();
  private HLayoutResults results;
  private int pageIndex;
  private HColorMode colorMode = HColorMode.LIGHT;
  private int viewportWidth;
  private boolean forceReload;

  public HPresentationSession(ILoggingObject loggingObject, IHopMetadataProvider metadataProvider) {
    this.loggingObject = Objects.requireNonNull(loggingObject, "loggingObject");
    this.metadataProvider = Objects.requireNonNull(metadataProvider, "metadataProvider");
  }

  public void setHomePresentationName(String homePresentationName) {
    this.homePresentationName = homePresentationName;
  }

  public void setColorMode(HColorMode colorMode) {
    this.colorMode = colorMode != null ? colorMode : HColorMode.LIGHT;
  }

  public void setViewportWidth(int viewportWidth) {
    this.viewportWidth = Math.max(0, viewportWidth);
  }

  public void setParameters(List<HParameter> parameters) {
    this.currentParameters =
        parameters != null ? new ArrayList<>(parameters) : new ArrayList<>();
  }

  /** Open a catalog presentation by name and make it the home if home is unset. */
  public void open(String presentationName) throws HException {
    open(presentationName, currentParameters, true);
  }

  public void open(String presentationName, List<HParameter> parameters) throws HException {
    open(presentationName, parameters, true);
  }

  /**
   * @param rememberHome when true and {@link #homePresentationName} is blank, this name becomes
   *     home
   */
  public void open(String presentationName, List<HParameter> parameters, boolean rememberHome)
      throws HException {
    if (StringUtils.isBlank(presentationName)) {
      throw new HException("Presentation name is required");
    }
    setParameters(parameters);
    HPresentation loaded = loadPresentation(presentationName);
    bindAndRender(loaded);
    if (rememberHome && StringUtils.isBlank(homePresentationName)) {
      homePresentationName = presentationName;
    }
  }

  /** Open an in-memory presentation (already in the metadata provider or fully self-contained). */
  public void open(HPresentation presentation, List<HParameter> parameters) throws HException {
    if (presentation == null || StringUtils.isBlank(presentation.getName())) {
      throw new HException("Presentation with a name is required");
    }
    setParameters(parameters);
    bindAndRender(presentation);
    if (StringUtils.isBlank(homePresentationName)) {
      homePresentationName = presentation.getName();
    }
  }

  public void goHome() throws HException {
    if (StringUtils.isBlank(homePresentationName)) {
      throw new HException("No home presentation is set");
    }
    open(homePresentationName, new ArrayList<>(), false);
  }

  /**
   * Re-layout and re-render the current presentation. {@code force} bypasses connector disk cache
   * and the component layout cache (full refresh).
   */
  public void reload(boolean force) throws HException {
    if (currentPresentation == null) {
      throw new HException("No presentation is open");
    }
    this.forceReload = force;
    if (force && StringUtils.isNotBlank(currentPresentationName)) {
      org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
          .invalidatePresentation(currentPresentationName);
    }
    int keepPage = pageIndex;
    bindAndRender(currentPresentation);
    setPage(keepPage);
    this.forceReload = false;
  }

  public void setPage(int page) {
    int n = pageCount();
    if (n <= 0) {
      pageIndex = 0;
      return;
    }
    pageIndex = Math.max(0, Math.min(page, n - 1));
  }

  public int pageCount() {
    return results != null && results.getRenderPages() != null
        ? results.getRenderPages().size()
        : 0;
  }

  public HRenderPage currentPage() {
    if (results == null || results.getRenderPages() == null || results.getRenderPages().isEmpty()) {
      return null;
    }
    int idx = Math.max(0, Math.min(pageIndex, results.getRenderPages().size() - 1));
    return results.getRenderPages().get(idx);
  }

  public String svgXml() throws HException {
    HRenderPage page = currentPage();
    return page != null ? page.getSvgXml() : null;
  }

  public boolean isContinuous() {
    return currentPresentation != null && currentPresentation.isContinuousLayout();
  }

  public Integer autoRefreshSeconds() {
    return currentPresentation != null ? currentPresentation.getAutoRefreshSeconds() : null;
  }

  public InteractionLookupResult lookup(int pageX, int pageY, HInteractionMethod method) {
    return InteractionRegionIndex.lookupAt(
        currentPresentation, currentPage(), pageX, pageY, method);
  }

  /**
   * Hit-test and convert matches to host commands. When {@code navigate} is true, {@code
   * OPEN_PRESENTATION} commands are applied on this session (last one wins).
   */
  public List<HHostCommand> applyPointer(
      HInteractionMethod method, int pageX, int pageY, boolean navigate) throws HException {
    HInteractionMethod filter = method != null ? method : HInteractionMethod.SINGLE_CLICK;
    InteractionLookupResult result = lookup(pageX, pageY, filter);
    if (result == null || !result.isFound()) {
      return List.of();
    }
    List<HHostCommand> commands = new ArrayList<>();
    List<InteractionLookupResult.InteractionMatch> matches = result.getMatches();
    if (matches == null || matches.isEmpty()) {
      matches =
          List.of(
              new InteractionLookupResult.InteractionMatch(result.getMethod(), result.getActions()));
    }
    for (InteractionLookupResult.InteractionMatch match : matches) {
      HInteractionMethod matchMethod =
          match.getMethod() != null ? match.getMethod() : HInteractionMethod.SINGLE_CLICK;
      if (filter.isClick() && matchMethod.isHover()) {
        continue;
      }
      if (filter.isHover() && matchMethod.isClick()) {
        continue;
      }
      List<HInteractionAction> actions =
          match.getActions() != null ? match.getActions() : Collections.emptyList();
      for (HInteractionAction action : actions) {
        if (action == null || action.getActionType() == null) {
          continue;
        }
        if (filter.isClick() && action.getActionType().isPopup()) {
          continue;
        }
        HHostCommand cmd = HHostCommand.fromAction(action, result.getDrawnItem());
        if (cmd != null) {
          commands.add(cmd);
        }
      }
    }
    if (navigate) {
      for (HHostCommand cmd : commands) {
        if (cmd.getType() == HHostCommand.Type.OPEN_PRESENTATION
            && StringUtils.isNotBlank(cmd.getObjectName())) {
          open(cmd.getObjectName(), cmd.getParameters(), false);
        }
      }
    }
    return commands;
  }

  private HPresentation loadPresentation(String presentationName) throws HException {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          metadataProvider.getSerializer(HPresentation.class);
      HPresentation loaded = serializer.load(presentationName);
      if (loaded == null) {
        throw new HException("Unable to find presentation '" + presentationName + "'");
      }
      return loaded;
    } catch (HopException e) {
      throw new HException("Error loading presentation '" + presentationName + "'", e);
    }
  }

  private void bindAndRender(HPresentation presentation) throws HException {
    this.currentPresentation = presentation;
    this.currentPresentationName = presentation.getName();
    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadataProvider);
    renderContext.setColorMode(colorMode);
    if (viewportWidth > 0) {
      renderContext.setViewportWidth(viewportWidth);
    }
    List<HParameter> params =
        currentParameters != null ? currentParameters : new ArrayList<>();
    results =
        presentation.doLayout(loggingObject, renderContext, metadataProvider, params, null, forceReload);
    if (results.getRenderPages() == null || results.getRenderPages().isEmpty()) {
      throw new HException(
          "There was no output after rendering (0 pages) of presentation "
              + presentation.getName());
    }
    presentation.render(results, metadataProvider, renderContext);
    if (pageIndex >= results.getRenderPages().size()) {
      pageIndex = results.getRenderPages().size() - 1;
    }
    if (pageIndex < 0) {
      pageIndex = 0;
    }
  }
}
