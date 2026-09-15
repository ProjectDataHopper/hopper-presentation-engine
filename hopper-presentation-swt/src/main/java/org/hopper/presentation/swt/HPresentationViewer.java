package org.hopper.presentation.swt;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.core.SwtUniversalImageSvg;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.svg.SvgImage;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.core.gui.plugin.GuiRegistry;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.hopper.core.plugin.HPluginIndexSupport;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.hopper.core.HColorMode;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.theme.HTheme;
import org.hopper.presentation.host.HHostCommand;
import org.hopper.presentation.host.HPresentationHost;
import org.hopper.presentation.host.HPresentationSession;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.render.pdf.HSvgPdfExporter;
import org.w3c.dom.svg.SVGDocument;

/**
 * SWT composite that layouts and renders a Hopper presentation as SVG and supports click
 * interactions. Uses {@link HPresentationSession} so REST and other hosts share hit-test and
 * OPEN_PRESENTATION behaviour.
 */
@GuiPlugin
public class HPresentationViewer extends Composite
    implements PaintListener, MouseListener, MouseMoveListener {

  public static final Class<?> PKG = HPresentationViewer.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HPresentationViewer.ToolBar";
  public static final String ID_TOOLBAR_ITEM_HOME = "10000-presentation-home";
  public static final String ID_TOOLBAR_ITEM_ZOOM_IN = "11000-zoom-in";
  public static final String ID_TOOLBAR_ITEM_ZOOM_OUT = "11100-zoom-out";
  public static final String ID_TOOLBAR_ITEM_ZOOM_100 = "11200-zoom-100%";
  public static final String ID_TOOLBAR_ITEM_ZOOM_PAGE = "11300-zoom-page";
  public static final String ID_TOOLBAR_ITEM_ZOOM_WIDTH = "11400-zoom-page-width";
  public static final String ID_TOOLBAR_ITEM_ZOOM_HEIGHT = "11450-zoom-page-height";
  public static final String ID_TOOLBAR_ITEM_ZOOM_LABEL = "11480-zoom-label";
  public static final String ID_TOOLBAR_ITEM_REFRESH_RATE_LABEL = "11484-refresh-rate-label";
  public static final String ID_TOOLBAR_ITEM_REFRESH_RATE = "11485-refresh-rate";
  public static final String ID_TOOLBAR_ITEM_EXPORT_SVG = "11490-export-svg";
  public static final String ID_TOOLBAR_ITEM_EXPORT_PDF = "11495-export-pdf";
  public static final String ID_TOOLBAR_ITEM_REFRESH = "11500-refresh";
  public static final String ID_TOOLBAR_ITEM_FIRST_PAGE = "12000-first-page";
  public static final String ID_TOOLBAR_ITEM_PREV_PAGE = "12100-previous-page";
  public static final String ID_TOOLBAR_ITEM_PAGE_LABEL = "12150-page-label";
  public static final String ID_TOOLBAR_ITEM_NEXT_PAGE = "12200-next-page";
  public static final String ID_TOOLBAR_ITEM_LAST_PAGE = "12300-last-page";

  static final int[] LIVE_REFRESH_SECONDS = {0, 1, 2, 5, 10, 15, 30};

  private final HPresentationSession session;
  private final HPresentationChrome chrome;
  private final HPresentationHost host;
  private final boolean webMode;

  private Control toolBarControl;
  private GuiToolbarWidgets toolBarWidgets;
  private ScrolledComposite scrolled;
  private Canvas wCanvas;
  private Composite webSurface;
  private Browser wBrowser;

  private int offsetX;
  private int offsetY;
  private float zoom = 1.0f;
  private HPresentationZoom zoomMode = HPresentationZoom.PAGE;
  private boolean applyingZoom;
  private Image cachedImage;
  private String cachedSvg;
  private int cachedWidth;
  private int cachedHeight;
  private Runnable autoRefreshRunnable;
  private Runnable liveRefresh;
  private Runnable liveRefreshTick;
  private int liveRefreshMs = 1000;
  private boolean webShellReady;
  private boolean webReplacePending;
  private boolean pointerBridgeInstalled;
  private Runnable webResizeZoom;
  private HColorMode appliedColorMode;

  public HPresentationViewer(
      Composite parent,
      ILoggingObject loggingObject,
      IHopMetadataProvider metadataProvider,
      String presentationName)
      throws HException {
    this(
        parent,
        loggingObject,
        metadataProvider,
        presentationName,
        HPresentationChrome.FULL,
        null);
  }

  public HPresentationViewer(
      Composite parent,
      ILoggingObject loggingObject,
      IHopMetadataProvider metadataProvider,
      String presentationName,
      HPresentationChrome chrome,
      HPresentationHost host)
      throws HException {
    super(parent, SWT.NO_BACKGROUND | SWT.NO_FOCUS | SWT.NO_MERGE_PAINTS | SWT.NO_RADIO_GROUP);
    this.chrome = chrome != null ? chrome : HPresentationChrome.FULL;
    this.host = host;
    this.session = new HPresentationSession(loggingObject, metadataProvider);
    this.webMode = detectWeb();

    setLayout(new FormLayout());
    Control top = createChrome();
    createSurface(top);

    addDisposeListener(
        e -> {
          autoRefreshRunnable = null;
          cancelLiveRefresh();
          cancelWebResizeZoom();
          webReplacePending = false;
          disposeCachedImage();
        });

    applySystemColorMode();
    session.open(presentationName);
    afterSessionChanged();
  }

  public HPresentationViewer(
      Composite parent,
      ILoggingObject loggingObject,
      IHopMetadataProvider metadataProvider,
      HPresentation presentation,
      HPresentationChrome chrome,
      HPresentationHost host)
      throws HException {
    super(parent, SWT.NO_BACKGROUND | SWT.NO_FOCUS | SWT.NO_MERGE_PAINTS | SWT.NO_RADIO_GROUP);
    this.chrome = chrome != null ? chrome : HPresentationChrome.FULL;
    this.host = host;
    this.session = new HPresentationSession(loggingObject, metadataProvider);
    this.webMode = detectWeb();

    setLayout(new FormLayout());
    Control top = createChrome();
    createSurface(top);
    addDisposeListener(
        e -> {
          autoRefreshRunnable = null;
          cancelLiveRefresh();
          cancelWebResizeZoom();
          webReplacePending = false;
          disposeCachedImage();
        });
    applySystemColorMode();
    session.open(presentation, java.util.List.of());
    afterSessionChanged();
  }

  public HPresentationSession getSession() {
    return session;
  }

  /** Replace the open presentation (same isolated catalog) and redraw. */
  public void open(HPresentation presentation) {
    try {
      session.open(presentation, java.util.List.of());
      afterSessionChanged();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Open"),
          e);
    }
  }

  /** Fit-width/page/height without showing chrome (results-pane embeds). */
  public void setFitMode(HPresentationZoom mode) {
    setZoomMode(mode);
  }

  /**
   * Re-layout the current presentation (mutated in-memory rows/tasks) and redraw. Live-refresh
   * callers use this so Hop Web replaces SVG in place instead of rebuilding chrome / the Browser
   * document. Hop Web keeps the current zoom: recomputing Fit width every tick resizes the HTML
   * slot and flickers iframe scrollbars.
   */
  public void reloadSurface() {
    try {
      applySystemColorMode();
      session.reload(true);
      disposeCachedImage();
      if (HPresentationViewerSupport.liveReloadRecomputesZoom(webMode)) {
        applyZoomFromMode(true);
      } else {
        redrawSurface();
      }
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Reload"),
          e);
    }
  }

  public void open(String presentationName, List<HParameter> parameters) {
    try {
      session.open(presentationName, parameters, false);
      afterSessionChanged();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Open"),
          e);
    }
  }

  private Control createChrome() {
    if (chrome == HPresentationChrome.NONE) {
      return null;
    }
    if (chrome == HPresentationChrome.MINIMAL) {
      Composite bar = new Composite(this, SWT.NONE);
      PropsUi.setLook(bar, Props.WIDGET_STYLE_TOOLBAR);
      bar.setLayout(new RowLayout(SWT.HORIZONTAL));
      Button refresh = new Button(bar, SWT.PUSH);
      refresh.setText(BaseMessages.getString(PKG, "HPresentationViewer.Refresh.Label"));
      refresh.setToolTipText(BaseMessages.getString(PKG, "HPresentationViewer.Refresh.Tooltip"));
      refresh.addListener(SWT.Selection, e -> refresh());
      Button prev = new Button(bar, SWT.PUSH);
      prev.setText("<");
      prev.addListener(SWT.Selection, e -> previousPage());
      Button next = new Button(bar, SWT.PUSH);
      next.setText(">");
      next.addListener(SWT.Selection, e -> nextPage());
      FormData fd = new FormData();
      fd.left = new FormAttachment(0, 0);
      fd.right = new FormAttachment(100, 0);
      fd.top = new FormAttachment(0, 0);
      bar.setLayoutData(fd);
      toolBarControl = bar;
      return bar;
    }

    IToolbarContainer toolBarContainer =
        ToolbarFacade.createToolbarContainer(this, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
    toolBarControl = toolBarContainer.getControl();
    toolBarWidgets = new GuiToolbarWidgets();
    toolBarWidgets.registerGuiPluginObject(this);
    // HPresentationViewer lives in plugin lib/; Hop GUI never indexed its @GuiToolbarElement
    // methods. Register them into GuiRegistry before building the toolbar.
    if (GuiRegistry.getInstance().findGuiToolbarItems(GUI_PLUGIN_TOOLBAR_PARENT_ID).isEmpty()) {
      HPluginIndexSupport.registerGuiElements(HPresentationViewer.class);
    }
    List<String> hide = new ArrayList<>();
    if (chrome == HPresentationChrome.ZOOM) {
      hide.add(ID_TOOLBAR_ITEM_HOME);
      hide.add(ID_TOOLBAR_ITEM_REFRESH);
      hide.add(ID_TOOLBAR_ITEM_FIRST_PAGE);
      hide.add(ID_TOOLBAR_ITEM_PREV_PAGE);
      hide.add(ID_TOOLBAR_ITEM_PAGE_LABEL);
      hide.add(ID_TOOLBAR_ITEM_NEXT_PAGE);
      hide.add(ID_TOOLBAR_ITEM_LAST_PAGE);
    } else {
      hide.add(ID_TOOLBAR_ITEM_REFRESH_RATE_LABEL);
      hide.add(ID_TOOLBAR_ITEM_REFRESH_RATE);
    }
    toolBarWidgets.createToolbarWidgets(toolBarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID, hide);
    if (chrome == HPresentationChrome.ZOOM) {
      toolBarWidgets.selectComboItem(
          ID_TOOLBAR_ITEM_REFRESH_RATE, refreshRateLabel(liveRefreshMs));
    }
    FormData fdToolBar = new FormData();
    fdToolBar.left = new FormAttachment(0, 0);
    fdToolBar.right = new FormAttachment(100, 0);
    fdToolBar.top = new FormAttachment(0, 0);
    toolBarControl.setLayoutData(fdToolBar);
    toolBarControl.pack();
    PropsUi.setLook(toolBarControl, Props.WIDGET_STYLE_TOOLBAR);
    return toolBarControl;
  }

  private void createSurface(Control top) {
    ControlAdapter onResize =
        new ControlAdapter() {
          @Override
          public void controlResized(ControlEvent e) {
            scheduleZoomFromResize();
          }
        };

    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = top != null ? new FormAttachment(top, 0) : new FormAttachment(0, 0);
    fd.bottom = new FormAttachment(100, 0);

    if (webMode) {
      // RAP Browser in a ScrolledComposite stays at HTML content size (CSS scale does not
      // affect layout) and shows inner iframe scrollbars. Fill the viewer instead; the
      // document slots the zoomed SVG and scrolls only when the scaled page is larger than
      // the pane (manual zoom). Do not listen on the Browser: content-size changes must not
      // recompute Fit width.
      webSurface = new Composite(this, SWT.BORDER);
      webSurface.setLayout(new FormLayout());
      webSurface.setLayoutData(fd);
      PropsUi.setLook(webSurface);
      wBrowser = new Browser(webSurface, SWT.NONE);
      FormData fdBrowser = new FormData();
      fdBrowser.left = new FormAttachment(0, 0);
      fdBrowser.right = new FormAttachment(100, 0);
      fdBrowser.top = new FormAttachment(0, 0);
      fdBrowser.bottom = new FormAttachment(100, 0);
      wBrowser.setLayoutData(fdBrowser);
      installPointerBridge();
      wBrowser.addProgressListener(
          new ProgressAdapter() {
            @Override
            public void changed(ProgressEvent event) {
              // RAP setText fires changed, not completed.
              webShellReady = true;
            }

            @Override
            public void completed(ProgressEvent event) {
              webShellReady = true;
            }
          });
      webSurface.addControlListener(onResize);
    } else {
      scrolled = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
      scrolled.setLayoutData(fd);
      scrolled.setExpandHorizontal(true);
      scrolled.setExpandVertical(true);
      PropsUi.setLook(scrolled);
      wCanvas = new Canvas(scrolled, SWT.NONE);
      scrolled.setContent(wCanvas);
      wCanvas.addPaintListener(this);
      wCanvas.addMouseListener(this);
      wCanvas.addMouseMoveListener(this);
      wCanvas.addListener(SWT.MouseWheel, this::handleMouseWheel);
      scrolled.addControlListener(onResize);
      addControlListener(onResize);
    }
    Display display = getDisplay();
    if (display != null) {
      display.asyncExec(
          () -> {
            if (!isDisposed()) {
              applyZoomFromMode(true);
            }
          });
    }
  }

  private static HInteractionMethod methodFromJs(String raw) {
    if (raw == null) {
      return HInteractionMethod.SINGLE_CLICK;
    }
    return switch (raw.toUpperCase()) {
      case "DOUBLE_CLICK", "DBLCLICK" -> HInteractionMethod.DOUBLE_CLICK;
      case "MOUSE_HOVER", "HOVER" -> HInteractionMethod.MOUSE_HOVER;
      default -> HInteractionMethod.SINGLE_CLICK;
    };
  }

  private void afterSessionChanged() {
    disposeCachedImage();
    scheduleAutoRefresh();
    applyZoomFromMode(true);
    enableButtons();
  }

  private void scheduleAutoRefresh() {
    autoRefreshRunnable = null;
    Integer seconds = session.autoRefreshSeconds();
    if (seconds == null || seconds < 5 || isDisposed()) {
      return;
    }
    int ms = Math.min(seconds, 3600) * 1000;
    autoRefreshRunnable =
        () -> {
          if (isDisposed() || autoRefreshRunnable == null) {
            return;
          }
          refresh();
          Display display = getDisplay();
          if (display != null && !display.isDisposed()) {
            display.timerExec(ms, autoRefreshRunnable);
          }
        };
    getDisplay().timerExec(ms, autoRefreshRunnable);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_HOME,
      image = "ui/images/home.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.Home.Tooltip")
  public void home() {
    try {
      session.goHome();
      afterSessionChanged();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Home"),
          e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_100,
      image = "ui/images/zoom-100.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.Zoom100.Tooltip")
  public void zoom100pct() {
    setZoomMode(HPresentationZoom.ACTUAL);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_IN,
      image = "ui/images/zoom-in.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ZoomIn.Tooltip")
  public void zoomIn() {
    zoomMode = HPresentationZoom.MANUAL;
    zoom = HPresentationZoom.zoomIn(zoom);
    applyZoomFromMode(true);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_OUT,
      image = "ui/images/zoom-out.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ZoomOut.Tooltip")
  public void zoomOut() {
    zoomMode = HPresentationZoom.MANUAL;
    zoom = HPresentationZoom.zoomOut(zoom);
    applyZoomFromMode(true);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_WIDTH,
      image = "ui/images/zoom-width.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ZoomWidth.Tooltip")
  public void zoomWidth() {
    setZoomMode(HPresentationZoom.WIDTH);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_PAGE,
      image = "ui/images/zoom-page.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ZoomPage.Tooltip")
  public void zoomPage() {
    setZoomMode(HPresentationZoom.PAGE);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_HEIGHT,
      image = "ui/images/zoom-height.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ZoomHeight.Tooltip")
  public void zoomHeight() {
    setZoomMode(HPresentationZoom.HEIGHT);
  }

  @GuiToolbarElement(
      type = GuiToolbarElementType.LABEL,
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ZOOM_LABEL,
      label = "Fit page",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ZoomLabel.Tooltip")
  public void zoomLabel() {
    // Label only
  }

  @GuiToolbarElement(
      type = GuiToolbarElementType.LABEL,
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_REFRESH_RATE_LABEL,
      label = "i18n::HPresentationViewer.RefreshRate.Label",
      toolTip = "i18n::HPresentationViewer.RefreshRate.Tooltip",
      separator = true)
  public void refreshRateLabel() {
    // Label only
  }

  @GuiToolbarElement(
      type = GuiToolbarElementType.COMBO,
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_REFRESH_RATE,
      comboValuesMethod = "getRefreshRateLabels",
      extraWidth = 24,
      readOnly = true,
      toolTip = "i18n::HPresentationViewer.RefreshRate.Tooltip")
  public void refreshRateSelected() {
    if (toolBarWidgets == null) {
      return;
    }
    Control control = toolBarWidgets.getWidgetsMap().get(ID_TOOLBAR_ITEM_REFRESH_RATE);
    if (control instanceof Combo combo) {
      setLiveRefreshMs(parseRefreshRateMs(combo.getText()));
    }
  }

  public List<String> getRefreshRateLabels() {
    List<String> labels = new ArrayList<>();
    for (int seconds : LIVE_REFRESH_SECONDS) {
      labels.add(refreshRateLabel(seconds * 1000));
    }
    return labels;
  }

  /**
   * Poll {@code liveRefresh} on the toolbar interval (default 1s). {@code null} stops polling.
   * Used by results-pane charts that mutate in-memory data.
   */
  public void setLiveRefresh(Runnable liveRefresh) {
    this.liveRefresh = liveRefresh;
    rescheduleLiveRefresh();
  }

  void setLiveRefreshMs(int ms) {
    liveRefreshMs = Math.max(0, ms);
    rescheduleLiveRefresh();
  }

  private void rescheduleLiveRefresh() {
    cancelLiveRefresh();
    if (liveRefresh == null || liveRefreshMs <= 0 || isDisposed()) {
      return;
    }
    Display display = getDisplay();
    if (display == null || display.isDisposed()) {
      return;
    }
    liveRefreshTick =
        () -> {
          if (isDisposed() || liveRefresh == null || liveRefreshMs <= 0) {
            return;
          }
          liveRefresh.run();
          if (!isDisposed() && liveRefreshMs > 0) {
            Display d = getDisplay();
            if (d != null && !d.isDisposed()) {
              d.timerExec(liveRefreshMs, liveRefreshTick);
            }
          }
        };
    display.timerExec(liveRefreshMs, liveRefreshTick);
  }

  private void cancelLiveRefresh() {
    Display display = getDisplay();
    if (liveRefreshTick != null && display != null && !display.isDisposed()) {
      display.timerExec(-1, liveRefreshTick);
    }
    liveRefreshTick = null;
  }

  static String refreshRateLabel(int ms) {
    if (ms <= 0) {
      return BaseMessages.getString(PKG, "HPresentationViewer.RefreshRate.Paused");
    }
    return BaseMessages.getString(PKG, "HPresentationViewer.RefreshRate.Seconds", ms / 1000);
  }

  static int parseRefreshRateMs(String text) {
    return HPresentationViewerSupport.parseRefreshRateMs(text);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_EXPORT_SVG,
      image = "ui/images/svg.svg",
      toolTip =
          "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ExportSvg.Tooltip",
      separator = true)
  public void exportSvg() {
    exportCurrentPage("svg");
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_EXPORT_PDF,
      image = "ui/images/pdf.svg",
      toolTip =
          "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.ExportPdf.Tooltip")
  public void exportPdf() {
    exportCurrentPage("pdf");
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_REFRESH,
      image = "ui/images/refresh.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.Refresh.Tooltip")
  public void refresh() {
    try {
      applySystemColorMode();
      session.reload(true);
      afterSessionChanged();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Reload"),
          e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_FIRST_PAGE,
      image = "ui/images/first-page.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.FirstPage.Tooltip",
      separator = true)
  public void firstPage() {
    showPage(0);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_PREV_PAGE,
      image = "ui/images/prev-page.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.PrevPage.Tooltip")
  public void previousPage() {
    if (session.getPageIndex() > 0) {
      showPage(session.getPageIndex() - 1);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_NEXT_PAGE,
      image = "ui/images/next-page.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.NextPage.Tooltip")
  public void nextPage() {
    if (session.getPageIndex() < session.pageCount() - 1) {
      showPage(session.getPageIndex() + 1);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_LAST_PAGE,
      image = "ui/images/last-page.svg",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.LastPage.Tooltip")
  public void lastPage() {
    showPage(session.pageCount() - 1);
  }

  @GuiToolbarElement(
      type = GuiToolbarElementType.LABEL,
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_PAGE_LABEL,
      label = "Page 1/1",
      toolTip = "i18n:org.hopper.presentation.swt.HPresentationViewer:HPresentationViewer.PageLabel.Tooltip")
  public void pageLabel() {
    // Label only
  }

  private void showPage(int pageNr) {
    session.setPage(pageNr);
    afterSessionChanged();
  }

  private void exportCurrentPage(String format) {
    boolean pdf = "pdf".equalsIgnoreCase(format);
    String ext = pdf ? ".pdf" : ".svg";
    try {
      byte[] bytes = exportBytes(pdf);
      String downloadName =
          HPresentationViewerSupport.safeDownloadFilename(
              session.getCurrentPresentationName(), ext);
      if (webMode
          && downloadInBrowser(
              downloadName, HPresentationViewerSupport.contentType(pdf), bytes)) {
        return;
      }
      saveExportToVfs(pdf, ext, bytes, downloadName);
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(
              PKG,
              pdf
                  ? "HPresentationViewer.Error.ExportPdf"
                  : "HPresentationViewer.Error.ExportSvg"),
          e);
    }
  }

  private byte[] exportBytes(boolean pdf) throws Exception {
    if (pdf) {
      return HSvgPdfExporter.fromLayoutResults(session.getResults());
    }
    String svg = session.svgXml();
    if (StringUtils.isBlank(svg)) {
      throw new HException("No SVG to export");
    }
    return svg.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Hop Web has no client filesystem. Prefer RAP's download service (same pattern as File → User →
   * Export to SVG); fall back to a Blob download inside the viewer Browser.
   */
  private boolean downloadInBrowser(String filename, String mime, byte[] bytes) {
    if (HRapDownload.start(getShell(), filename, mime, bytes)) {
      return true;
    }
    if (wBrowser == null || wBrowser.isDisposed()) {
      return false;
    }
    String script = HWebSvgDocument.downloadScript(filename, mime, bytes);
    try {
      return wBrowser.execute(script);
    } catch (Exception e) {
      return false;
    }
  }

  private void saveExportToVfs(boolean pdf, String ext, byte[] bytes, String suggestedName)
      throws Exception {
    IVariables variables = hopVariables();
    String filter = pdf ? "*.pdf" : "*.svg";
    String filterName =
        pdf
            ? BaseMessages.getString(PKG, "HPresentationViewer.ExportPdf.Filter")
            : BaseMessages.getString(PKG, "HPresentationViewer.ExportSvg.Filter");
    FileObject proposed =
        HopVfs.getFileObject(
            HPresentationViewerSupport.proposedExportPath(variables, suggestedName), variables);
    String filename =
        BaseDialog.presentFileDialog(
            true,
            getShell(),
            null,
            variables,
            proposed,
            new String[] {filter},
            new String[] {filterName},
            false);
    if (StringUtils.isEmpty(filename)) {
      return;
    }
    filename = HPresentationViewerSupport.resolveExportFilename(variables, filename);
    filename = withExtension(filename, ext);
    FileObject file = HopVfs.getFileObject(filename, variables);
    if (file.exists()) {
      MessageBox box = new MessageBox(getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
      box.setText(BaseMessages.getString(PKG, "HPresentationViewer.Export.Exists.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HPresentationViewer.Export.Exists.Message"));
      if ((box.open() & SWT.YES) == 0) {
        return;
      }
    }
    try (OutputStream out = HopVfs.getOutputStream(file, false)) {
      out.write(bytes);
    }
  }

  private IVariables hopVariables() {
    try {
      HopGui hopGui = HopGui.getInstance();
      if (hopGui != null && hopGui.getVariables() != null) {
        return hopGui.getVariables();
      }
    } catch (Exception ignored) {
      // Standalone test viewer / headless hosts have no HopGui.
    }
    return Variables.getADefaultVariableSpace();
  }

  static String withExtension(String filename, String extension) {
    return HPresentationViewerSupport.withExtension(filename, extension);
  }

  private void setZoomMode(HPresentationZoom mode) {
    zoomMode = mode != null ? mode : HPresentationZoom.PAGE;
    applyZoomFromMode(true);
  }

  private void scheduleZoomFromResize() {
    if (!webMode) {
      applyZoomFromMode(true);
      return;
    }
    Display display = getDisplay();
    if (display == null || display.isDisposed()) {
      return;
    }
    cancelWebResizeZoom();
    webResizeZoom =
        () -> {
          webResizeZoom = null;
          if (!isDisposed()) {
            applyZoomFromMode(true);
          }
        };
    display.timerExec(50, webResizeZoom);
  }

  private void cancelWebResizeZoom() {
    Display display = getDisplay();
    if (webResizeZoom != null && display != null && !display.isDisposed()) {
      display.timerExec(-1, webResizeZoom);
    }
    webResizeZoom = null;
  }

  private void applyZoomFromMode(boolean redraw) {
    if (applyingZoom || isDisposed()) {
      return;
    }
    applyingZoom = true;
    try {
      Rectangle client = viewportBounds();
      HRenderPage renderPage = session.currentPage();
      HPage page = renderPage != null ? renderPage.getPage() : null;
      int pageW = page != null ? Math.max(1, page.getWidth()) : 1;
      int pageH = page != null ? Math.max(1, page.getHeight()) : 1;
      int margin = webMode ? HPresentationZoom.WEB_MARGIN : HPresentationZoom.MARGIN;
      zoom =
          HPresentationZoom.compute(
              zoomMode, client.width, client.height, pageW, pageH, zoom, margin);
      if (webMode) {
        zoom =
            HPresentationZoom.clampFitToPane(
                zoomMode, zoom, pageW, pageH, client.width, client.height, margin);
      }
      updateScrollMinSize(pageW, pageH);
      updateZoomAndPageLabels();
      if (redraw) {
        redrawSurface();
      }
    } finally {
      applyingZoom = false;
    }
  }

  private void updateScrollMinSize(int pageW, int pageH) {
    if (scrolled == null || scrolled.isDisposed()) {
      return;
    }
    int imageWidth = Math.max(1, Math.round(zoom * pageW));
    int imageHeight = Math.max(1, Math.round(zoom * pageH));
    scrolled.setMinSize(imageWidth, imageHeight);
  }

  /** Usable canvas / RAP Browser size (results-pane embeds size the page from this). */
  public Rectangle getViewportBounds() {
    return viewportBounds();
  }

  private Rectangle viewportBounds() {
    if (scrolled != null && !scrolled.isDisposed()) {
      Rectangle client = scrolled.getClientArea();
      if (client.width > 0 && client.height > 0) {
        return client;
      }
    }
    if (wCanvas != null && !wCanvas.isDisposed() && wCanvas.getBounds().width > 0) {
      return wCanvas.getBounds();
    }
    // Prefer the FormLayout parent, not the RAP Browser: iframe client area can follow
    // HTML content size and feed Fit-width zoom back into a scrollbar loop.
    if (webSurface != null && !webSurface.isDisposed()) {
      Rectangle client = webSurface.getClientArea();
      if (client.width > 0 && client.height > 0) {
        return client;
      }
      if (webSurface.getBounds().width > 0) {
        return webSurface.getBounds();
      }
    }
    if (wBrowser != null && !wBrowser.isDisposed()) {
      Rectangle client = wBrowser.getClientArea();
      if (client.width > 0 && client.height > 0) {
        return client;
      }
      if (wBrowser.getBounds().width > 0) {
        return wBrowser.getBounds();
      }
    }
    return getClientArea();
  }

  private void handleMouseWheel(Event event) {
    if ((event.stateMask & SWT.MOD1) == 0) {
      return;
    }
    event.doit = false;
    if (event.count > 0) {
      zoomIn();
    } else {
      zoomOut();
    }
  }

  private void enableButtons() {
    updateZoomAndPageLabels();
    if (toolBarWidgets == null) {
      return;
    }
    int nrPages = Math.max(1, session.pageCount());
    int page = session.getPageIndex();
    boolean continuous = session.isContinuous();
    boolean multi = !continuous && nrPages > 1;
    toolBarWidgets.enableToolbarItem(ID_TOOLBAR_ITEM_NEXT_PAGE, multi && page < nrPages - 1);
    toolBarWidgets.enableToolbarItem(ID_TOOLBAR_ITEM_PREV_PAGE, multi && page > 0);
    toolBarWidgets.enableToolbarItem(ID_TOOLBAR_ITEM_FIRST_PAGE, multi && page > 0);
    toolBarWidgets.enableToolbarItem(ID_TOOLBAR_ITEM_LAST_PAGE, multi && page < nrPages - 1);
  }

  private void updateZoomAndPageLabels() {
    int nrPages = Math.max(1, session.pageCount());
    int page = session.getPageIndex() + 1;
    String pageText =
        session.isContinuous() && nrPages <= 1
            ? BaseMessages.getString(PKG, "HPresentationViewer.PageLabel.Continuous")
            : BaseMessages.getString(PKG, "HPresentationViewer.PageLabel.Format", page, nrPages);
    setToolbarLabel(ID_TOOLBAR_ITEM_PAGE_LABEL, pageText);
    setToolbarLabel(ID_TOOLBAR_ITEM_ZOOM_LABEL, zoomMode.label(zoom));
  }

  private void setToolbarLabel(String id, String text) {
    if (toolBarWidgets == null) {
      return;
    }
    String value = Const.NVL(text, "");
    Control control = toolBarWidgets.getWidgetsMap().get(id);
    if (control instanceof CLabel label && !label.isDisposed()) {
      if (HPresentationViewerSupport.toolbarTextUnchanged(label.getText(), value)) {
        return;
      }
      label.setText(value);
      if (webMode) {
        // pack() + RowLayout wrap on Hop Web reflows the results sash every live tick.
        return;
      }
      label.pack();
      if (label.getParent() instanceof ToolBar bar) {
        for (ToolItem item : bar.getItems()) {
          if (item.getControl() == label) {
            item.setWidth(Math.max(label.getSize().x + 12, 64));
            break;
          }
        }
      }
      return;
    }
    toolBarWidgets.setToolbarItemText(id, text);
  }

  public void redrawSurface() {
    disposeCachedImage();
    if (webMode) {
      loadBrowserSvg();
    } else if (wCanvas != null && !wCanvas.isDisposed()) {
      wCanvas.redraw();
    }
  }

  @Override
  public void paintControl(PaintEvent paintEvent) {
    if (wCanvas == null) {
      return;
    }
    Rectangle canvasBounds = wCanvas.getBounds();
    try {
      HRenderPage renderPage = session.currentPage();
      if (renderPage == null || renderPage.getPage() == null) {
        return;
      }
      HPage page = renderPage.getPage();
      int imageWidth = Math.max(1, (int) (zoom * page.getWidth()));
      int imageHeight = Math.max(1, (int) (zoom * page.getHeight()));
      Image image = rasterSvg(session.svgXml(), imageWidth, imageHeight);

      paintEvent.gc.setBackground(chromeBackground());
      paintEvent.gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_BLACK));
      paintEvent.gc.fillRectangle(0, 0, canvasBounds.width, canvasBounds.height);

      offsetX = Math.max(0, (canvasBounds.width - imageWidth) / 2);
      offsetY = Math.max(0, (canvasBounds.height - imageHeight) / 2);
      if (image != null && !image.isDisposed()) {
        paintEvent.gc.drawImage(image, offsetX, offsetY);
      }
    } catch (Exception e) {
      throw new RuntimeException(BaseMessages.getString(PKG, "HPresentationViewer.Error.Paint"), e);
    }
  }

  private Image rasterSvg(String svgXml, int imageWidth, int imageHeight) throws IOException {
    if (svgXml == null) {
      return null;
    }
    if (cachedImage != null
        && !cachedImage.isDisposed()
        && svgXml.equals(cachedSvg)
        && cachedWidth == imageWidth
        && cachedHeight == imageHeight) {
      return cachedImage;
    }
    disposeCachedImage();
    String parser = XMLResourceDescriptor.getXMLParserClassName();
    SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
    SVGDocument document = factory.createSVGDocument("", new StringReader(svgXml));
    SvgImage svgImage = new SvgImage(document);
    cachedImage =
        new SwtUniversalImageSvg(svgImage, false)
            .getAsBitmapForSize(getDisplay(), imageWidth, imageHeight);
    cachedSvg = svgXml;
    cachedWidth = imageWidth;
    cachedHeight = imageHeight;
    return cachedImage;
  }

  private void disposeCachedImage() {
    if (cachedImage != null && !cachedImage.isDisposed()) {
      cachedImage.dispose();
    }
    cachedImage = null;
    cachedSvg = null;
  }

  private void loadBrowserSvg() {
    if (wBrowser == null || wBrowser.isDisposed()) {
      return;
    }
    String svg;
    try {
      svg = session.svgXml();
    } catch (HException e) {
      svg = "";
    }
    if (svg == null) {
      svg = "";
    }
    int[] pageSize = currentPageSize();
    if (!HPresentationViewerSupport.rebuildWebDocument(webShellReady)) {
      tryReplaceSvg(svg, pageSize[0], pageSize[1]);
      return;
    }
    wBrowser.setText(
        HWebSvgDocument.html(
            svg, zoom, chromeBackgroundHex(), pageSize[0], pageSize[1], zoomMode));
    // RAP never delivers ProgressListener.completed for setText; mark the shell ready so
    // live refresh replaces SVG in the existing iframe instead of navigating again.
    webShellReady = true;
  }

  private int[] currentPageSize() {
    HRenderPage renderPage = session.currentPage();
    HPage page = renderPage != null ? renderPage.getPage() : null;
    int pageW = page != null ? Math.max(1, page.getWidth()) : 1;
    int pageH = page != null ? Math.max(1, page.getHeight()) : 1;
    return new int[] {pageW, pageH};
  }

  private boolean tryReplaceSvg(String svg, int pageW, int pageH) {
    String script = HWebSvgDocument.replaceSvgScript(svg, zoom, pageW, pageH, zoomMode);
    if (tryRapEvaluateAsync(script)) {
      return true;
    }
    try {
      return wBrowser.execute(script);
    } catch (IllegalStateException pending) {
      // RAP: another script is still in flight; skip this tick, keep the shell.
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * RAP {@code Browser.execute} blocks the UI thread waiting for the client. Live chart ticks must
   * use the non-blocking {@code evaluate(script, BrowserCallback)} when it exists.
   */
  private boolean tryRapEvaluateAsync(String script) {
    if (!webMode || wBrowser == null || wBrowser.isDisposed()) {
      return false;
    }
    if (webReplacePending) {
      return true;
    }
    try {
      Class<?> callbackType = Class.forName("org.eclipse.rap.rwt.widgets.BrowserCallback");
      Method evaluate = Browser.class.getMethod("evaluate", String.class, callbackType);
      Object callback =
          Proxy.newProxyInstance(
              callbackType.getClassLoader(),
              new Class<?>[] {callbackType},
              (proxy, method, args) -> {
                webReplacePending = false;
                return null;
              });
      webReplacePending = true;
      evaluate.invoke(wBrowser, script, callback);
      return true;
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      return false;
    } catch (InvocationTargetException e) {
      webReplacePending = false;
      return e.getCause() instanceof IllegalStateException;
    } catch (Exception e) {
      webReplacePending = false;
      return false;
    }
  }

  private void installPointerBridge() {
    if (pointerBridgeInstalled || wBrowser == null || wBrowser.isDisposed()) {
      return;
    }
    try {
      new BrowserFunction(wBrowser, "hopPointer") {
        @Override
        public Object function(Object[] arguments) {
          if (arguments == null || arguments.length < 3) {
            return null;
          }
          int x = ((Number) arguments[0]).intValue();
          int y = ((Number) arguments[1]).intValue();
          String method = String.valueOf(arguments[2]);
          handlePointer(methodFromJs(method), x, y);
          return null;
        }
      };
      pointerBridgeInstalled = true;
    } catch (Exception ignored) {
      // RAP/Browser builds without BrowserFunction still render SVG.
    }
  }

  @Override
  public void mouseDoubleClick(MouseEvent e) {
    handleCanvasPointer(e, HInteractionMethod.DOUBLE_CLICK);
  }

  @Override
  public void mouseMove(MouseEvent e) {
    try {
      int[] xy = toPage(e.x, e.y);
      var result = session.lookup(xy[0], xy[1], null);
      if (result != null && result.isFound()) {
        setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        return;
      }
    } catch (Exception ignored) {
      // keep default cursor
    }
    setCursor(getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
  }

  @Override
  public void mouseDown(MouseEvent e) {}

  @Override
  public void mouseUp(MouseEvent e) {
    handleCanvasPointer(e, HInteractionMethod.SINGLE_CLICK);
  }

  private void handleCanvasPointer(MouseEvent e, HInteractionMethod method) {
    int[] xy = toPage(e.x, e.y);
    handlePointer(method, xy[0], xy[1]);
  }

  private int[] toPage(int x, int y) {
    int pageX = (int) ((float) (x - offsetX) / zoom);
    int pageY = (int) ((float) (y - offsetY) / zoom);
    return new int[] {pageX, pageY};
  }

  private void handlePointer(HInteractionMethod method, int pageX, int pageY) {
    try {
      List<HHostCommand> commands = session.applyPointer(method, pageX, pageY, true);
      afterSessionChanged();
      dispatchHostCommands(commands);
    } catch (Exception ex) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationViewer.Error.Open"),
          ex);
    }
  }

  private void dispatchHostCommands(List<HHostCommand> commands) {
    if (commands == null) {
      return;
    }
    for (HHostCommand cmd : commands) {
      if (cmd == null || cmd.getType() == null) {
        continue;
      }
      switch (cmd.getType()) {
        case OPEN_PRESENTATION -> {
          if (host != null && StringUtils.isNotBlank(cmd.getObjectName())) {
            host.openPresentation(cmd.getObjectName(), cmd.getParameters());
          }
        }
        case OPEN_LINK, OPEN_LINK_NEW_TAB -> {
          boolean newTab = cmd.getType() == HHostCommand.Type.OPEN_LINK_NEW_TAB;
          if (host != null) {
            host.openLink(cmd.getObjectName(), newTab);
          } else if (StringUtils.isNotBlank(cmd.getObjectName())) {
            Program.launch(cmd.getObjectName());
          }
        }
        case POPUP_CONTEXT_INFORMATION -> {
          if (host != null) {
            host.showTooltip(cmd.getTitle(), cmd.getText(), cmd.getDrawnItem());
          }
        }
        case POPUP_PRESENTATION -> {
          if (host != null) {
            host.popupPresentation(cmd.getObjectName(), cmd.getParameters(), cmd.getDrawnItem());
          }
        }
      }
    }
  }

  private void applySystemColorMode() {
    HColorMode mode = systemColorMode();
    if (appliedColorMode != mode) {
      webShellReady = false;
      appliedColorMode = mode;
    }
    session.setColorMode(mode);
  }

  static HColorMode systemColorMode() {
    try {
      if (PropsUi.getInstance() != null && PropsUi.getInstance().isDarkMode()) {
        return HColorMode.DARK;
      }
    } catch (Throwable ignored) {
      // Headless tests and hosts without Hop GUI props.
    }
    return HColorMode.LIGHT;
  }

  private Color chromeBackground() {
    try {
      Color bg = GuiResource.getInstance().getWidgetBackGroundColor();
      if (bg != null && !bg.isDisposed()) {
        return bg;
      }
    } catch (Exception ignored) {
      // fall through
    }
    return GuiResource.getInstance().getColorLightGray();
  }

  private String chromeBackgroundHex() {
    try {
      HPresentation presentation = session.getCurrentPresentation();
      if (presentation != null) {
        HTheme theme =
            presentation.resolveDefaultTheme(session.getMetadataProvider(), session.getColorMode());
        if (theme != null && theme.getBackgroundColor() != null) {
          return theme.getBackgroundColor().getHexColor();
        }
      }
    } catch (Exception ignored) {
      // fall through
    }
    return systemColorMode() == HColorMode.DARK ? "#3c3f41" : "#e6e6e6";
  }

  private static boolean detectWeb() {
    try {
      return EnvironmentUtils.getInstance().isWeb();
    } catch (Exception e) {
      return false;
    }
  }
}
