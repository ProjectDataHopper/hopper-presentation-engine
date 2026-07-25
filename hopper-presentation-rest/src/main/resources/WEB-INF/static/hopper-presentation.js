const API_BASE = '/hopper/api/';

/**
 * Bookmarkable view URL by presentation name (server rebuilds if cache empty).
 * @param {string} name presentation metadata name
 * @param {number} [page0=0] 0-based render page
 * @param {string} [colorMode]
 */
function viewPresentationUrl(name, page0, colorMode) {
    let p = page0 != null && !isNaN(page0) ? parseInt(page0, 10) : 0;
    if (p < 0) {
        p = 0;
    }
    let cm = colorMode || (typeof currentColorMode === "function" ? currentColorMode() : "light");
    return API_BASE + "render/p/" + encodeURIComponent(name)
        + "/HTML/" + p + "/?colorMode=" + encodeURIComponent(cm);
}

let canvas;
let gc;
let rect;
let image;
let lookupResults = [];
let scale;
let zoom = 1.0;
let numberOfPages;
let offset = {
    "x": 0,
    "y": 0
};

/**
 * Active page-view pan (middle-button drag or Ctrl/Meta + left drag).
 * @type {null|{startClientX:number,startClientY:number,originOffsetX:number,originOffsetY:number}}
 */
let panState = null;

/** Nested wait-cursor depth for open/refresh/page-load work. */
let _presentationBusyDepth = 0;

/**
 * Show the system wait cursor while a presentation is opening or refreshing.
 * Nested calls are reference-counted; always pair with {@link endPresentationBusy}.
 */
function beginPresentationBusy() {
    _presentationBusyDepth++;
    if (_presentationBusyDepth === 1) {
        try {
            if (document.body) {
                document.body.classList.add("hopper-busy");
                document.body.style.cursor = "wait";
            }
            if (typeof canvas !== "undefined" && canvas) {
                canvas.style.cursor = "wait";
            }
        } catch (e) { /* ignore */ }
    }
}

function endPresentationBusy() {
    if (_presentationBusyDepth <= 0) {
        _presentationBusyDepth = 0;
        return;
    }
    _presentationBusyDepth--;
    if (_presentationBusyDepth === 0) {
        try {
            if (document.body) {
                document.body.classList.remove("hopper-busy");
                document.body.style.cursor = "";
            }
            if (typeof canvas !== "undefined" && canvas) {
                canvas.style.cursor = "";
            }
        } catch (e) { /* ignore */ }
    }
}

/**
 * Error UI: implemented in hopper-metadata-list.js (shared with home page).
 * Fallbacks if that script is not loaded.
 */
function showErrorDialog(title, detail) {
    if (window.HMetadataList && typeof window.HMetadataList.showErrorDialog === "function") {
        window.HMetadataList.showErrorDialog(title, detail);
        return;
    }
    window.alert((title || "Error") + "\n\n" + (detail != null ? String(detail) : ""));
}

function showAjaxError(title, xhr, status, error) {
    if (window.HMetadataList && typeof window.HMetadataList.showAjaxError === "function") {
        window.HMetadataList.showAjaxError(title, xhr, status, error);
        return;
    }
    let body = (xhr && xhr.responseText) ? xhr.responseText : (xhr && xhr.status) || "";
    window.alert((title || "Request failed") + "\n\n" + body);
}
/**
 * jQuery side panel — resolve on each use so admin host can inject #editSidePanel after
 * this script loads (or re-inject on tab switch).
 */
function getSidePanel() {
    return $("#editSidePanel");
}

/** Admin SPA hosts connector/DB/theme UIs without a presentation canvas. */
function isAdminMetadataHost() {
    return !!(document.body && document.body.classList.contains("admin-metadata-host"));
}

/**
 * True when the admin host is showing a catalog list (not an item editor form).
 * List Close should leave the tab; form Close returns to the list.
 */
function isAdminMetadataCatalogListOpen() {
    return !!(document.getElementById("closeConnectorListBtn")
        || document.getElementById("closeDatabaseConnectionListBtn")
        || document.getElementById("closeThemeListBtn"));
}

/**
 * Leave admin connectors/DB/themes host and return to the admin overview (or a given page).
 */
function exitAdminMetadataCatalog(targetPage) {
    let page = targetPage || "overview";
    if (window.HAdmin && typeof window.HAdmin.showPage === "function") {
        window.HAdmin.showPage(page);
        return;
    }
    // Fallback if shell is missing
    if (window.HAdminMetadataHost && typeof window.HAdminMetadataHost.clearHostClass === "function") {
        window.HAdminMetadataHost.clearHostClass();
    } else if (document.body) {
        document.body.classList.remove("admin-metadata-host");
        document.body.classList.remove("property-panel-open");
        document.body.classList.remove("chain-editor-open");
    }
    let content = document.getElementById("adminContent");
    if (content) {
        content.innerHTML = "<p class=\"admin-muted\">Closed. Choose a section from the navigation.</p>";
    }
}

/** 'view' | 'edit' — set by page template before this script loads. */
function isEditMode() {
    return typeof hopperMode !== "undefined" && hopperMode === "edit";
}
function isViewMode() {
    return !isEditMode();
}

/**
 * Open catalog admin UIs (same as presentation toolbar). Used by the admin panel host.
 * @param {"connectors"|"database"|"themes"} kind
 */
function openMetadataAdmin(kind) {
    if (document.body) {
        document.body.dataset.adminMetadataKind = kind || "connectors";
        document.body.classList.add("admin-metadata-host");
    }
    if (kind === "database" || kind === "connections") {
        editDatabaseConnectionsList();
    } else if (kind === "themes") {
        editThemesList();
    } else {
        editConnectorsList();
    }
}

/**
 * Open/close the property (or connector) side panel.
 * In edit mode, collapses the left component rail so it cannot cover Apply/Close or form fields.
 * In admin metadata host, the panel fills the content area; close returns to the catalog list.
 */
function setSidePanelOpen(open, options) {
    options = options || {};
    let sidePanel = getSidePanel();
    if (!sidePanel || !sidePanel.length) {
        return;
    }
    if (isAdminMetadataHost()) {
        if (open) {
            document.body.classList.add("property-panel-open");
            sidePanel.css({ width: "100%", display: "block" });
            setPropertyPreviewVisible(!!options.withPreview && !!options.componentName);
            if (options.withPreview && options.componentName) {
                loadComponentPreview(options.componentName, options.geometry || null);
            }
        } else {
            document.body.classList.remove("chain-editor-open");
            if (typeof HopperChainEditor !== "undefined" && HopperChainEditor.onStudioClosed) {
                HopperChainEditor.onStudioClosed();
            }
            setPropertyPreviewVisible(false);
            clearComponentPreview();
            // Do not collapse to empty — re-open the list for the current admin tab
            let kind = (document.body && document.body.dataset.adminMetadataKind) || "connectors";
            setTimeout(function () {
                if (!isAdminMetadataHost()) {
                    return;
                }
                openMetadataAdmin(kind);
            }, 0);
        }
        return;
    }
    if (open) {
        if (isEditMode()) {
            document.body.classList.add("property-panel-open");
            // Form ~70% of the page; with preview the panel spans the viewport so the
            // remaining ~30% is the preview column (see .property-form-column CSS).
            let withPreview = options.withPreview === true;
            let connectorStudio = options.connectorStudio === true;
            let chainEditor = options.chainEditor === true
                || document.body.classList.contains("chain-editor-open");
            let w;
            if (withPreview) {
                w = Math.floor(window.innerWidth * 0.98);
            } else if (chainEditor) {
                w = Math.min(1100, Math.floor(window.innerWidth * 0.88));
            } else if (connectorStudio) {
                w = Math.min(1000, Math.floor(window.innerWidth * 0.75));
            } else {
                w = Math.floor(window.innerWidth * 0.70);
            }
            sidePanel.width(w);
            setPropertyPreviewVisible(!!options.withPreview && !!options.componentName);
            if (options.withPreview && options.componentName) {
                loadComponentPreview(options.componentName, options.geometry || null);
            }
            // Floating component/page menus sit over the canvas and the preview strip
            if (typeof window.hopperEdit !== "undefined") {
                if (typeof window.hopperEdit.hideSelectionToolbar === "function") {
                    window.hopperEdit.hideSelectionToolbar();
                }
                if (typeof window.hopperEdit.hideBackgroundToolbar === "function") {
                    window.hopperEdit.hideBackgroundToolbar();
                }
            }
        } else {
            sidePanel.width("95%");
            setPropertyPreviewVisible(false);
        }
    } else {
        document.body.classList.remove("property-panel-open");
        document.body.classList.remove("chain-editor-open");
        if (typeof HopperChainEditor !== "undefined" && HopperChainEditor.onStudioClosed) {
            HopperChainEditor.onStudioClosed();
        }
        sidePanel.width(0);
        setPropertyPreviewVisible(false);
        clearComponentPreview();
        // Reposition selection toolbar if a component is still selected after close
        if (typeof window.hopperEdit !== "undefined"
            && typeof window.hopperEdit.updateSelectionToolbar === "function") {
            window.hopperEdit.updateSelectionToolbar();
        }
    }
    // Title sits after canvas toolbar icons — re-measure after panel layout changes
    if (typeof positionPresentationTitleBar === "function") {
        requestAnimationFrame(function () {
            positionPresentationTitleBar();
        });
    }
}

function setPropertyPreviewVisible(visible) {
    let col = document.getElementById("propertyPreviewColumn");
    if (!col) {
        return;
    }
    if (visible) {
        col.removeAttribute("hidden");
    } else {
        col.setAttribute("hidden", "hidden");
    }
}

function clearComponentPreview() {
    let img = document.getElementById("componentPreviewImg");
    let empty = document.getElementById("componentPreviewEmpty");
    let meta = document.getElementById("componentPreviewMeta");
    if (img) {
        img.removeAttribute("src");
        img.classList.remove("is-visible");
    }
    if (empty) {
        empty.style.display = "";
        empty.textContent = "No preview";
    }
    if (meta) {
        meta.textContent = "";
    }
    if (typeof clearComponentErrorPanel === "function") {
        clearComponentErrorPanel();
    }
}

/**
 * Load an isolated SVG preview of a component into the property panel.
 * Uses geometry from the page when available so proportions match the presentation.
 */
function loadComponentPreview(componentName, geometry) {
    let img = document.getElementById("componentPreviewImg");
    let empty = document.getElementById("componentPreviewEmpty");
    let meta = document.getElementById("componentPreviewMeta");
    if (!img || typeof presentationName === "undefined") {
        return;
    }
    setPropertyPreviewVisible(true);
    if (empty) {
        empty.style.display = "";
        empty.textContent = "Rendering preview...";
    }
    img.classList.remove("is-visible");

    let w = 0;
    let h = 0;
    if (geometry && geometry.width > 0 && geometry.height > 0) {
        w = Math.round(geometry.width);
        h = Math.round(geometry.height);
    } else if (typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.getGeometries === "function") {
        let geos = window.hopperEdit.getGeometries() || [];
        for (let i = 0; i < geos.length; i++) {
            if (geos[i].componentName === componentName && geos[i].geometry) {
                w = Math.round(geos[i].geometry.width);
                h = Math.round(geos[i].geometry.height);
                break;
            }
        }
    }
    if (w <= 0) {
        w = 320;
    }
    if (h <= 0) {
        h = 200;
    }

    let url = API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
        + "/components/" + encodeURIComponent(componentName)
        + "/preview.svg?width=" + encodeURIComponent(w)
        + "&height=" + encodeURIComponent(h)
        + "&colorMode=" + encodeURIComponent(currentColorMode())
        + "&_=" + Date.now();

    img.onload = function () {
        img.classList.add("is-visible");
        if (empty) {
            empty.style.display = "none";
        }
        if (meta) {
            meta.textContent = componentName + " | " + w + "x" + h + " px (page size)";
        }
    };
    img.onerror = function () {
        img.classList.remove("is-visible");
        if (empty) {
            empty.style.display = "";
            empty.textContent = "Preview failed to render.";
        }
        if (meta) {
            meta.textContent = componentName;
        }
    };
    img.src = url;
}

let componentJson = {};
let presentationJson = {};
/** Active connector metadata while editing in the side panel. */
let connectorJson = null;
let connectorPluginId = null;
let oldConnectorName = null;
/** JSON snapshot of connector after load / last Save (dirty detection). */
let connectorFormBaseline = null;
/**
 * 0-based index into HPresentation.pages for the component being edited.
 * Set when opening the editor from getComponent (or fallback from render page).
 */
let editLogicalPageNumber = 0;
/** "page" | "header" | "footer" — where the component lives on the presentation. */
let editPageRole = "page";

let componentNames = null;
let connectorNames = null;
let themeNames = null;
// Content cell alignment (HHorizontalAlignment / HVerticalAlignment)
const HORIZONTAL_ALIGNMENTS = ["LEFT", "RIGHT", "CENTER"];
const VERTICAL_ALIGNMENTS = ["TOP", "BOTTOM", "MIDDLE"];
// Layout attachment alignment (HAttachment.Alignment) — uses CENTER, not MIDDLE
const LAYOUT_HORIZONTAL_ALIGNMENTS = ["DEFAULT", "LEFT", "RIGHT", "CENTER"];
const LAYOUT_VERTICAL_ALIGNMENTS = ["DEFAULT", "TOP", "BOTTOM", "CENTER"];
const AGGREGATION_METHODS = ["SUM", "COUNT", "AVERAGE"]
let oldComponentName = null;
let componentPluginId = null;
let rowIdNumber = 1;
/** Canvas toolbar strip height / icon slot size (~30% smaller than the original 32). */
/** Canvas toolbar icon strip height / default icon slot (px). */
const ICON_SIZE = 28;

/** Current 0-based page index (presentation pages). */
function currentPageIndex0() {
    return parseInt(typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0, 10) || 0;
}

/** Total page count for navigation (prefers live numberOfPages when known). */
function totalPageCount() {
    if (typeof numberOfPages !== "undefined" && numberOfPages) {
        return parseInt(numberOfPages, 10) || 1;
    }
    return parseInt(typeof renderPageCount !== "undefined" ? renderPageCount : 1, 10) || 1;
}

/** "N / M" or "N / M+" when layout hit the server page cap (1-based rendered page number). */
function toolbarPageLabel() {
    let cur = currentPageIndex0() + 1;
    let total = totalPageCount();
    if (typeof pagesTruncated !== "undefined" && pagesTruncated) {
        return cur + " / " + total + "+";
    }
    return cur + " / " + total;
}

function toolbarPageTitle() {
    if (typeof pagesTruncated !== "undefined" && pagesTruncated) {
        return "Current page (layout stopped at " + totalPageCount()
            + " pages — more content was truncated)";
    }
    return "Current page";
}

/**
 * 1-based logical page for designer chrome (falls back to rendered page index).
 */
function currentLogicalPage1() {
    let l0 = typeof editLogicalPageNumber !== "undefined" ? parseInt(editLogicalPageNumber, 10) : NaN;
    if (isNaN(l0) || l0 < 0) {
        return currentPageIndex0() + 1;
    }
    return l0 + 1;
}

/**
 * Label drawn at top-right of the page frame: "Logical page N · Rendered page M"
 */
function pageIdentityLabelText() {
    return "Logical page " + currentLogicalPage1()
        + " · Rendered page " + (currentPageIndex0() + 1);
}

/**
 * Draw logical/rendered page identity at the top-right of the page outline.
 * @param {CanvasRenderingContext2D} gcCtx
 * @param {number} sc scale
 * @param {{x:number,y:number}} off pan offset (page units)
 * @param {{x:number,y:number,width:number,height:number}} pageRect page box in page units
 */
function drawPageIdentityLabel(gcCtx, sc, off, pageRect) {
    if (!gcCtx || !pageRect || !sc) {
        return;
    }
    let text = pageIdentityLabelText();
    let pad = 8;
    let x = (pageRect.x - off.x) * sc + pageRect.width * sc - pad;
    let y = (pageRect.y - off.y) * sc + pad;
    gcCtx.save();
    gcCtx.font = "11px system-ui, -apple-system, Segoe UI, sans-serif";
    gcCtx.textAlign = "right";
    gcCtx.textBaseline = "top";
    // Subtle halo for contrast on light and dark page backgrounds
    gcCtx.lineWidth = 3;
    gcCtx.strokeStyle = isUiDarkMode()
        ? "rgba(11, 18, 32, 0.75)"
        : "rgba(255, 255, 255, 0.85)";
    gcCtx.strokeText(text, x, y);
    gcCtx.fillStyle = isUiDarkMode()
        ? "rgba(232, 238, 249, 0.92)"
        : "rgba(30, 40, 60, 0.78)";
    gcCtx.fillText(text, x, y);
    gcCtx.restore();
}

/**
 * URL for a monochrome chrome icon (light vs pre-generated dark asset).
 * @param {string} iconName e.g. "home.svg"
 * @returns {string}
 */
function resolveUiIcon(iconName) {
    if (typeof uiIconUrl === "function") {
        return uiIconUrl(iconName);
    }
    if (typeof window !== "undefined" && window.HThemeMode && window.HThemeMode.uiIconUrl) {
        return window.HThemeMode.uiIconUrl(iconName);
    }
    let bare = String(iconName || "").replace(/^.*\//, "");
    return "/hopper/api/static/images/" + bare;
}

/** Toolbar icon entry with dual-asset URL resolved for the current color mode. */
function toolbarIconEntry(iconName, action, enabled, title) {
    return {
        iconName: iconName,
        file: resolveUiIcon(iconName),
        action: action,
        enabled: enabled,
        title: title
    };
}

/**
 * Shared navigation / zoom icons for view and edit.
 * Layout: Home · Zoom · First · Previous · [page N / M] · Next · Last
 * Page pan is via middle-button drag or Ctrl/Meta + left drag (not toolbar arrows).
 */
function buildBaseToolbarIcons() {
    return [
        toolbarIconEntry("home.svg", () => openUrl("/hopper/api/render/main/"), () => true, "Home"),
        toolbarIconEntry("zoom-in.svg", () => zoomIn(), () => true, "Zoom in"),
        toolbarIconEntry("zoom-out.svg", () => zoomOut(), () => true, "Zoom out"),
        toolbarIconEntry("zoom-100.svg", () => zoom100(), () => true, "Zoom 100%"),
        toolbarIconEntry("arrow-first.svg", () => firstPage(), () => currentPageIndex0() > 0, "First page"),
        toolbarIconEntry("arrow-left.svg", () => previousPage(), () => currentPageIndex0() > 0, "Previous page"),
        {
            // Text slot between Previous and Next — not clickable
            "type": "label",
            "label": () => toolbarPageLabel(),
            "width": 68,
            "action": () => {},
            "enabled": () => true,
            "title": () => toolbarPageTitle()
        },
        toolbarIconEntry(
            "arrow-right.svg",
            () => nextPage(),
            () => currentPageIndex0() < totalPageCount() - 1,
            "Next page"
        ),
        toolbarIconEntry(
            "arrow-last.svg",
            () => lastPage(),
            () => currentPageIndex0() < totalPageCount() - 1,
            "Last page"
        )
    ];
}

/** View-only: open editor for this presentation. */
function openEditorForCurrentPresentation() {
    if (typeof presentationName === "undefined" || !presentationName) {
        return;
    }
    if (typeof beginPresentationBusy === "function") {
        beginPresentationBusy();
    }
    let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
    window.open(
        API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/?colorMode=" + encodeURIComponent(cm),
        "_self"
    );
}

/**
 * Edit mode: open the presentation in read-only view in a new browser tab.
 * Uses a name-based URL so the view rebuilds after restart for that browser session.
 */
function openViewForCurrentPresentation() {
    if (typeof presentationName === "undefined" || !presentationName) {
        return;
    }
    if (typeof beginPresentationBusy === "function") {
        beginPresentationBusy();
    }
    let page0 = typeof currentPageIndex0 === "function" ? currentPageIndex0() : 0;
    if (typeof renderPageNumber0 !== "undefined" && renderPageNumber0 !== null && renderPageNumber0 !== "") {
        let parsed = parseInt(renderPageNumber0, 10);
        if (!isNaN(parsed) && parsed >= 0) {
            page0 = parsed;
        }
    }

    let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
    window.open(
        viewPresentationUrl(presentationName, page0, cm),
        "_blank",
        "noopener,noreferrer"
    );
    if (typeof endPresentationBusy === "function") {
        endPresentationBusy();
    }
}

/** Undo/redo toolbar enablement from last GET history/ call. */
let hopperUndoState = {canUndo: false, canRedo: false};

/**
 * Toolbar depends on hopperMode:
 * - view: navigation + optional "open editor"
 * - edit: navigation + view (new tab) + undo/redo + connectors + database admin
 */
function buildToolbarIcons() {
    let icons = buildBaseToolbarIcons();
    if (isViewMode()) {
        icons.push(toolbarIconEntry(
            "refresh.svg",
            () => forceRefreshPresentation(),
            () => !!(typeof presentationName !== "undefined" && presentationName),
            "Refresh (clear layout cache and re-render)"
        ));
        icons.push(toolbarIconEntry(
            "edit.svg",
            () => openEditorForCurrentPresentation(),
            () => true,
            "Edit presentation"
        ));
    } else {
        icons.push(toolbarIconEntry(
            "view.svg",
            () => openViewForCurrentPresentation(),
            () => !!(typeof presentationName !== "undefined" && presentationName),
            "View presentation (read-only, new tab)"
        ));
        icons.push(toolbarIconEntry(
            "undo.svg",
            () => presentationUndo(),
            () => !!(hopperUndoState && hopperUndoState.canUndo),
            "Undo (Ctrl+Z)"
        ));
        icons.push(toolbarIconEntry(
            "redo.svg",
            () => presentationRedo(),
            () => !!(hopperUndoState && hopperUndoState.canRedo),
            "Redo (Ctrl+Y)"
        ));
        icons.push(toolbarIconEntry(
            "refresh.svg",
            () => forceRefreshPresentation(),
            () => !!(typeof presentationName !== "undefined" && presentationName),
            "Refresh (clear layout cache and re-render)"
        ));
        if (typeof hopperTimingsToolbarVisible === "undefined" || hopperTimingsToolbarVisible) {
        icons.push(toolbarIconEntry(
            "stopwatch.svg",
            () => {
                try {
                    localStorage.setItem("hopperTimingsPanel", "1");
                } catch (e) { /* ignore */ }
                if (typeof showRefreshTimingsPanel === "function") {
                    showRefreshTimingsPanel({});
                }
            },
            () => !!(typeof presentationName !== "undefined" && presentationName),
            "Show refresh timings (Gantt)"
        ));
        }
        icons.push(toolbarIconEntry(
            "connector.svg",
            () => editConnectorsList(),
            () => true,
            "Connectors"
        ));
        icons.push(toolbarIconEntry(
            "database.svg",
            () => editDatabaseConnectionsList(),
            () => true,
            "Database connections"
        ));
        icons.push(toolbarIconEntry(
            "theme.svg",
            () => editThemesList(),
            () => true,
            "Themes"
        ));
    }
    return icons;
}

/**
 * Refresh undo/redo button enablement from the server.
 */
function refreshUndoRedoState(done) {
    if (!isEditMode() || typeof presentationName === "undefined" || !presentationName) {
        hopperUndoState = {canUndo: false, canRedo: false};
        if (typeof done === "function") {
            done();
        }
        return;
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName) + "/history/",
        type: "GET",
        dataType: "json",
        success: function (st) {
            hopperUndoState = {
                canUndo: !!(st && st.canUndo),
                canRedo: !!(st && st.canRedo)
            };
            // Undo/redo enablement only affects toolbar icons — do not re-blit the page
            if (typeof scheduleCanvasRedraw === "function") {
                scheduleCanvasRedraw();
            }
            if (typeof done === "function") {
                done();
            }
        },
        error: function () {
            hopperUndoState = {canUndo: false, canRedo: false};
            if (typeof done === "function") {
                done();
            }
        }
    });
}

function presentationUndo() {
    presentationHistoryAction("undo");
}

function presentationRedo() {
    presentationHistoryAction("redo");
}

function presentationHistoryAction(which) {
    if (!isEditMode() || typeof presentationName === "undefined" || !presentationName) {
        return;
    }
    let path = which === "redo" ? "redo" : "undo";
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/history/" + path + "/",
        type: "POST",
        dataType: "json",
        success: function (res) {
            if (res && res.ok === false) {
                return;
            }
            hopperUndoState = {
                canUndo: !!(res && res.canUndo),
                canRedo: !!(res && res.canRedo)
            };
            // Close open property form — component tree may have changed
            if (typeof setSidePanelOpen === "function") {
                setSidePanelOpen(false);
            }
            if (typeof window.hopperEdit !== "undefined"
                && typeof window.hopperEdit.clearSelection === "function") {
                window.hopperEdit.clearSelection();
            }
            if (typeof softReloadEditor === "function") {
                softReloadEditor(null);
            } else if (typeof reloadPresentation === "function") {
                reloadPresentation();
            }
            refreshUndoRedoState();
        },
        error: function (xhr, status, error) {
            showAjaxError(
                (which === "redo" ? "Redo" : "Undo") + " failed",
                xhr,
                status,
                error
            );
        }
    });
}

let toolbarIcons = buildToolbarIcons();
/** Avoid re-entrant soft-reload on first paint */
let _hopperInitialColorSyncDone = false;

$(document).ready(function () {
    installHandlers();
});

function installHandlers() {
    canvas = document.getElementById("svgCanvas");
    // Admin metadata host (and any page without a canvas) loads this script for catalog UIs only
    if (!canvas) {
        return;
    }
    canvas.width = document.body.clientWidth;
    canvas.height = document.body.clientHeight;
    gc = canvas.getContext("2d");
    rect = canvas.getBoundingClientRect();

    initialize();
    loadIcons();
    checkPages();
    loadDrawSvgPage();
    installPresentationTitleBar();
    if (isEditMode()) {
        refreshUndoRedoState();
    }
    // Server shell often renders with light theme; re-render once to match UI color mode
    syncPresentationColorModeOnLoad();

    // Track the mouse movements and clicks
    //
    let element = $("#svgCanvas");

    element.mousemove((e) => {
        updateToolbarTooltip(e);
        handleMouseMoveActions(e);
    });
    element.on("mouseleave", function () {
        hideToolbarTooltip();
    });
    element.mousedown((e) => {
        hideToolbarTooltip();
        // Middle button, or Ctrl/Meta + left: pan the page view (esp. when zoomed)
        if (e.button === 1 || (e.button === 0 && (e.ctrlKey || e.metaKey))) {
            // Ignore pan starts on the toolbar icon strip
            if (typeof ICON_SIZE === "number" && e.offsetY < ICON_SIZE) {
                return;
            }
            startPagePan(e);
            return;
        }
        if (e.button === 0) {
            handleMouseLeftClickActions(e);
        }
    });
    element.on("dblclick", function (e) {
        if (!isEditMode()) {
            return;
        }
        // Ignore toolbar strip
        if (typeof ICON_SIZE === "number" && e.offsetY < ICON_SIZE) {
            return;
        }
        if (e.ctrlKey || e.metaKey) {
            return;
        }
        let x = correctX(e.offsetX);
        let y = correctY(e.offsetY);
        if (invalidMouseLocation(x, y)) {
            return;
        }
        let requestData = {
            renderId: renderId,
            pageNumber: renderPageNumber0,
            x: x,
            y: y
        };
        if (typeof window.hopperEdit !== "undefined"
            && typeof window.hopperEdit.handleCanvasDoubleClick === "function") {
            e.preventDefault();
            window.hopperEdit.handleCanvasDoubleClick(e, x, y, requestData);
        }
    });
    // Avoid browser auto-scroll / tab behaviors on middle-click
    element.on("auxclick", function (e) {
        if (e.button === 1) {
            e.preventDefault();
        }
    });
    element.on("contextmenu", function (e) {
        // Middle-button pan should not open a context menu mid-gesture on some platforms
        if (panState) {
            e.preventDefault();
        }
    });
    // mouseup may land outside the canvas while dragging a component or panning
    $(document).mouseup((e) => {
        if (panState) {
            endPagePan(e);
            return;
        }
        if (isEditMode()
            && typeof window.hopperEdit !== "undefined"
            && typeof window.hopperEdit.handleCanvasMouseUp === "function") {
            window.hopperEdit.handleCanvasMouseUp(e);
        }
    });
    // Keep panning smooth if the pointer leaves the canvas
    $(document).mousemove((e) => {
        if (panState) {
            updatePagePan(e);
        }
    });
    // Wheel: page prev/next; Ctrl/Meta+wheel zooms under the cursor
    canvas.addEventListener("wheel", handleCanvasWheel, {passive: false});
}

/** Zoom limits for wheel / toolbar zoom. */
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 20;
const ZOOM_STEP = 1.1;

/**
 * Effective draw scale for a given zoom level (mirrors {@link computePageDrawScale}).
 */
function computeScaleForZoom(z) {
    if (!image || !canvas) {
        return z;
    }
    let logical = typeof pageLogicalSize === "function"
        ? pageLogicalSize(image)
        : {width: image.width, height: image.height};
    if (!(logical.width > 0) || !(logical.height > 0)) {
        return z;
    }
    let css = typeof canvasCssSize === "function"
        ? canvasCssSize()
        : {width: canvas.clientWidth || canvas.width, height: canvas.clientHeight || canvas.height};
    let contentH = Math.max(1, (css.height || 0) - ICON_SIZE);
    let contentW = Math.max(1, css.width || 1);
    let scaleX = z * contentW / logical.width;
    let scaleY = z * contentH / logical.height;
    return Math.min(scaleX, scaleY, z);
}

/**
 * Zoom so the page point under canvas pixel (canvasX, canvasY) stays fixed on screen.
 * Coordinates are CSS/canvas event space (same as offsetX/offsetY); y includes the toolbar strip.
 */
function zoomAtCanvasPoint(canvasX, canvasY, newZoom) {
    if (!image) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        return;
    }
    newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
    let oldScale = (typeof scale === "number" && scale > 0)
        ? scale
        : computeScaleForZoom(zoom);
    if (!(oldScale > 0)) {
        oldScale = 1;
    }

    // Page coordinates under the cursor before the zoom change
    let contentY = canvasY - ICON_SIZE;
    // If the pointer is over the toolbar, anchor to the top of the page content
    if (contentY < 0) {
        contentY = 0;
    }
    let pageX = offset.x + canvasX / oldScale;
    let pageY = offset.y + contentY / oldScale;

    zoom = newZoom;
    let newScale = computeScaleForZoom(zoom);
    if (!(newScale > 0)) {
        newScale = 1;
    }

    // Keep the same page point under the cursor after scale changes.
    // Offset may go negative so the anchor stays fixed when zooming out near the origin.
    offset.x = pageX - canvasX / newScale;
    offset.y = pageY - contentY / newScale;

    // scale is refreshed inside drawSvg; redraw page + component outlines
    invalidatePageBaseCache();
    drawSvg();
}

/**
 * Multiply current zoom by factor, anchored at a canvas point (default: content center).
 */
function zoomByFactor(factor, canvasX, canvasY) {
    if (canvasX === undefined || canvasY === undefined) {
        // Center of the page content area (CSS pixels; canvas is DPR-scaled for drawing)
        let cssW = canvas ? (canvas.clientWidth || rect.width || canvas.width) : 0;
        let cssH = canvas ? (canvas.clientHeight || rect.height || canvas.height) : 0;
        canvasX = cssW / 2;
        canvasY = ICON_SIZE + Math.max(0, (cssH - ICON_SIZE) / 2);
    }
    zoomAtCanvasPoint(canvasX, canvasY, zoom * factor);
}

function zoomIn() {
    zoomByFactor(ZOOM_STEP);
}

function zoomOut() {
    zoomByFactor(1 / ZOOM_STEP);
}

function zoom100() {
    zoom = 1.0;
    offset.x = 0;
    offset.y = 0;
    invalidatePageBaseCache();
    drawSvg();
}

/** Cooldown so trackpad flicks don't skip many pages when wheel navigates pages. */
let _wheelPageNavCooldownUntil = 0;
const WHEEL_PAGE_NAV_COOLDOWN_MS = 280;

/**
 * Mouse / trackpad wheel on the canvas:
 * <ul>
 *   <li>plain wheel → previous / next page (scroll up = previous)</li>
 *   <li>Ctrl or Meta + wheel → zoom under the cursor</li>
 * </ul>
 */
function handleCanvasWheel(e) {
    if (!canvas) {
        return;
    }
    // Don't hijack while middle/Ctrl-panning
    if (panState) {
        e.preventDefault();
        return;
    }
    e.preventDefault();

    let ctrlZoom = !!(e.ctrlKey || e.metaKey);
    if (ctrlZoom) {
        handleWheelZoom(e);
        return;
    }

    // Page navigation (no modifier)
    let now = Date.now();
    if (now < _wheelPageNavCooldownUntil) {
        return;
    }
    let delta = e.deltaY;
    if (e.deltaMode === 1) {
        delta *= 16;
    } else if (e.deltaMode === 2) {
        delta *= 400;
    }
    // Ignore tiny trackpad jitter
    if (Math.abs(delta) < 4) {
        return;
    }
    _wheelPageNavCooldownUntil = now + WHEEL_PAGE_NAV_COOLDOWN_MS;
    if (delta > 0) {
        if (typeof nextPage === "function") {
            nextPage();
        }
    } else if (typeof previousPage === "function") {
        previousPage();
    }
}

/**
 * Ctrl/Meta+wheel: zoom in/out with the presentation anchored under the cursor.
 * @param {WheelEvent} e
 */
function handleWheelZoom(e) {
    if (!image || !canvas) {
        return;
    }

    let canvasX = e.offsetX;
    let canvasY = e.offsetY;
    // Fallback if offsetX is unavailable (some browsers on the canvas)
    if (canvasX === undefined || canvasY === undefined
        || (canvasX === 0 && canvasY === 0 && e.clientX)) {
        let r = canvas.getBoundingClientRect();
        canvasX = e.clientX - r.left;
        canvasY = e.clientY - r.top;
    }

    // Normalize delta across wheel / lines / pages and trackpads
    let delta = e.deltaY;
    if (e.deltaMode === 1) {
        // DOM_DELTA_LINE
        delta *= 16;
    } else if (e.deltaMode === 2) {
        // DOM_DELTA_PAGE
        delta *= 400;
    }

    // Scroll up / trackpad pinch-out (negative deltaY) → zoom in
    // Use a smooth exponential for fine trackpad steps; still feels stepped for mouse wheels
    let factor = Math.exp(-delta * 0.0018);
    // Clamp one event so a huge trackpad flick doesn't jump too far
    factor = Math.max(1 / (ZOOM_STEP * ZOOM_STEP), Math.min(ZOOM_STEP * ZOOM_STEP, factor));

    zoomAtCanvasPoint(canvasX, canvasY, zoom * factor);
}

function openUrl(url) {
    window.open(url, "_self");
}

function newPresentation() {
    openUrl("/hopper/api/render/main/");
}


/**
 * Pixel width of a toolbar slot (icons default to ICON_SIZE; labels may be wider).
 */
function toolbarSlotWidth(toolbarIcon) {
    if (toolbarIcon && typeof toolbarIcon.width === "number" && toolbarIcon.width > 0) {
        return toolbarIcon.width;
    }
    return ICON_SIZE;
}

/** Total CSS-pixel width of the canvas-drawn toolbar icon strip. */
function toolbarIconsTotalWidth() {
    let w = 0;
    let icons = (typeof toolbarIcons !== "undefined" && toolbarIcons) ? toolbarIcons : [];
    for (let i = 0; i < icons.length; i++) {
        w += toolbarSlotWidth(icons[i]);
    }
    return w;
}

/**
 * Place the presentation name immediately after the canvas toolbar icons (same strip).
 */
function positionPresentationTitleBar() {
    let bar = document.getElementById("presentationTitleBar");
    let canvasEl = document.getElementById("svgCanvas");
    if (!bar || !canvasEl) {
        return;
    }
    let r = canvasEl.getBoundingClientRect();
    let iconsW = toolbarIconsTotalWidth();
    let gap = 10;
    let left = Math.round(r.left + iconsW + gap);
    let top = Math.round(r.top);
    // Leave a little room on the right for theme toggle / auth chip
    let maxW = Math.max(60, Math.round(r.right - left - 8));
    bar.style.left = left + "px";
    bar.style.right = "auto";
    bar.style.top = top + "px";
    bar.style.height = (typeof ICON_SIZE === "number" ? ICON_SIZE : 28) + "px";
    bar.style.maxWidth = maxW + "px";
}

/** Coalesce toolbar strip redraws (never re-blits the full page SVG). */
let _toolbarRedrawRaf = 0;

/**
 * CSS-pixel width of the canvas content (drawing is scaled by devicePixelRatio).
 */
function canvasCssWidth() {
    if (!canvas) {
        return 0;
    }
    return canvas.clientWidth || rect && rect.width || Math.round(canvas.width / (devicePixelRatio || 1));
}

/**
 * Schedule a canvas refresh after toolbar icon loads / enablement changes.
 * Invalidates the page base cache (toolbar is part of the base layer) and uses
 * drawSvg when the page image is ready so selection overlays stay correct.
 */
function scheduleCanvasRedraw() {
    if (_toolbarRedrawRaf) {
        return;
    }
    _toolbarRedrawRaf = requestAnimationFrame(function () {
        _toolbarRedrawRaf = 0;
        if (typeof gc === "undefined" || !gc || !canvas) {
            return;
        }
        if (typeof invalidatePageBaseCache === "function") {
            invalidatePageBaseCache();
        }
        if (typeof drawSvg === "function"
            && typeof image !== "undefined"
            && isPageImageReady(image)) {
            drawSvg();
        } else {
            ensureCanvasDprTransform();
            drawIcons(gc, canvasCssWidth());
        }
    });
}

/**
 * Point toolbar slots at light or dark static icons for the current mode.
 * Does not load images — call {@link loadIcons} afterward (optionally force).
 */
function refreshToolbarIconUrls() {
    for (let i = 0; i < toolbarIcons.length; i++) {
        let toolbarIcon = toolbarIcons[i];
        if (!toolbarIcon || toolbarIcon.type === "label" || !toolbarIcon.iconName) {
            continue;
        }
        toolbarIcon.file = resolveUiIcon(toolbarIcon.iconName);
    }
}

/**
 * Load the toolbar icons (skips label slots).
 * Waits until all pending loads finish, then paints the strip once.
 * @param {boolean} [force] re-fetch even if already complete (theme toggle)
 */
function loadIcons(force) {
    let pending = 0;
    function onIconSettled() {
        pending--;
        if (pending <= 0) {
            if (typeof invalidatePageBaseCache === "function") {
                invalidatePageBaseCache();
            }
            scheduleCanvasRedraw();
        }
    }
    for (let i = 0; i < toolbarIcons.length; i++) {
        let toolbarIcon = toolbarIcons[i];
        toolbarIcon.index = i;
        if (toolbarIcon.type === "label") {
            continue;
        }
        if (toolbarIcon.iconName) {
            toolbarIcon.file = resolveUiIcon(toolbarIcon.iconName);
        }
        if (!toolbarIcon.file) {
            continue;
        }
        // Already loaded (e.g. soft-reload) — do not re-fetch / re-bind
        if (!force && toolbarIcon.icon && toolbarIcon.icon.complete
            && toolbarIcon.icon.src && toolbarIcon.icon.src.indexOf(toolbarIcon.file) >= 0) {
            continue;
        }
        toolbarIcon.icon = null;
        pending++;
        let icon = new Image();
        icon.onload = function () {
            toolbarIcon.icon = icon;
            onIconSettled();
        };
        icon.onerror = function () {
            console.warn("Toolbar icon failed to load: " + toolbarIcon["file"]);
            onIconSettled();
        };
        icon.src = toolbarIcon["file"];
    }
    // Nothing to fetch (all cached / labels only) — still refresh enablement strip once
    if (pending === 0) {
        scheduleCanvasRedraw();
    }
}

function drawIcons(gcCtx, width) {
    let x = 0;
    let pad = 1;
    let drawSize = Math.max(12, ICON_SIZE - 2 * pad);
    let dark = isUiDarkMode();
    // Never leave a CSS filter on the page canvas (expensive on full-frame drawImage)
    if (gcCtx.filter && gcCtx.filter !== "none") {
        gcCtx.filter = "none";
    }
    // Toolbar strip background (matches canvas chrome)
    gcCtx.fillStyle = canvasThemeColor(
        "--hopper-canvas-chrome",
        dark ? "#0b1220" : "#f4f4f8"
    );
    gcCtx.fillRect(0, 0, width, ICON_SIZE);

    for (let i = 0; i < toolbarIcons.length; i++) {
        let toolbarIcon = toolbarIcons[i];
        let slotW = toolbarSlotWidth(toolbarIcon);
        let isEnabled = typeof toolbarIcon.enabled === "function"
            ? toolbarIcon.enabled.call(null) : true;

        if (toolbarIcon.type === "label") {
            let text = typeof toolbarIcon.label === "function"
                ? toolbarIcon.label.call(null) : (toolbarIcon.label || "");
            gcCtx.save();
            gcCtx.globalAlpha = isEnabled ? 1.0 : 0.3;
            // Dark icons/text on light chrome; light text on dark chrome
            gcCtx.fillStyle = dark ? "#e8eef9" : "#0e3a5a";
            gcCtx.font = "11px system-ui, -apple-system, Segoe UI, sans-serif";
            gcCtx.textAlign = "center";
            gcCtx.textBaseline = "middle";
            gcCtx.fillText(text, x + slotW / 2, ICON_SIZE / 2);
            gcCtx.restore();
            x += slotW;
            continue;
        }

        let icon = toolbarIcon.icon;
        // Icons load async; skip until available to avoid drawImage throwing and breaking the UI
        if (!icon || !icon.complete) {
            x += slotW;
            continue;
        }
        // Some SVGs have no intrinsic width/height until attributes are set — fall back to ICON_SIZE
        let srcW = icon.naturalWidth || icon.width || ICON_SIZE;
        let srcH = icon.naturalHeight || icon.height || ICON_SIZE;
        if (srcW <= 0 || srcH <= 0) {
            x += slotW;
            continue;
        }
        gcCtx.save();
        if (!isEnabled) {
            gcCtx.globalAlpha = 0.3;
        }
        // Center icon in the slot (slot may be wider than ICON_SIZE for future items)
        // Dual static assets (images/dark/*) — never canvas filter invert
        let ix = x + Math.max(pad, (Math.min(slotW, ICON_SIZE) - drawSize) / 2);
        let iy = pad;
        gcCtx.drawImage(icon, 0, 0, srcW, srcH, ix, iy, drawSize, drawSize);
        gcCtx.restore();
        x += slotW;
    }
    // Belt-and-suspenders: page drawImage must not inherit a filter
    gcCtx.filter = "none";
    gcCtx.strokeStyle = canvasThemeColor("--hopper-canvas-toolbar-line", dark ? "rgba(148,163,184,0.4)" : "#555555");
    gcCtx.lineWidth = 1;
    gcCtx.beginPath();
    gcCtx.moveTo(0, ICON_SIZE - 1);
    gcCtx.lineTo(width, ICON_SIZE - 1);
    gcCtx.stroke();
    // Keep the HTML presentation name aligned after the icons
    if (typeof positionPresentationTitleBar === "function") {
        positionPresentationTitleBar();
    }
}

/** Read a CSS variable for canvas chrome (falls back if not set). */
function canvasThemeColor(cssVar, fallback) {
    try {
        let v = getComputedStyle(document.documentElement).getPropertyValue(cssVar);
        v = (v || "").trim();
        return v || fallback;
    } catch (e) {
        return fallback;
    }
}

function isUiDarkMode() {
    return typeof currentColorMode === "function" && currentColorMode() === "dark";
}

/**
 * Toolbar slot under canvas coordinates (includes label slots for tooltips).
 * Uses canvas offsetX/offsetY, not page-space image bounds.
 */
function getToolbarSlotAt(canvasX, canvasY) {
    if (canvasY < 0 || canvasY > ICON_SIZE || canvasX < 0) {
        return null;
    }
    let cursor = 0;
    for (let i = 0; i < toolbarIcons.length; i++) {
        let toolbarIcon = toolbarIcons[i];
        let slotW = toolbarSlotWidth(toolbarIcon);
        if (canvasX >= cursor && canvasX < cursor + slotW) {
            return toolbarIcon;
        }
        cursor += slotW;
    }
    return null;
}

function getToolbarIcon(event) {
    let slot = getToolbarSlotAt(event.offsetX, event.offsetY);
    // Label slots are display-only
    if (!slot || slot.type === "label") {
        return null;
    }
    return slot;
}

function handleToolbarIconClick(event) {
    let icon = getToolbarIcon(event);
    if (icon !== null && icon !== undefined) {
        let isEnabled = icon.enabled.call(null);
        if (isEnabled) {
            icon.action.call(event);
            return true;
        }
    }
    return false;
}

// ── Canvas toolbar tooltips (icons are drawn, so native title= does not apply) ──

function ensureToolbarTooltipEl() {
    let el = document.getElementById("canvasToolbarTooltip");
    if (!el) {
        el = document.createElement("div");
        el.id = "canvasToolbarTooltip";
        el.className = "canvas-toolbar-tooltip";
        el.setAttribute("role", "tooltip");
        el.hidden = true;
        document.body.appendChild(el);
    }
    return el;
}

function hideToolbarTooltip() {
    let el = document.getElementById("canvasToolbarTooltip");
    if (el) {
        el.hidden = true;
        el.textContent = "";
    }
    if (typeof canvas !== "undefined" && canvas
        && canvas.dataset && canvas.dataset.toolbarCursor === "1") {
        canvas.style.cursor = "";
        delete canvas.dataset.toolbarCursor;
    }
}

/**
 * Show a floating tooltip when the pointer is over a toolbar icon/label.
 */
function updateToolbarTooltip(event) {
    let slot = getToolbarSlotAt(event.offsetX, event.offsetY);
    if (!slot) {
        hideToolbarTooltip();
        return;
    }
    let titleText = typeof slot.title === "function" ? slot.title.call(null) : (slot.title || "");
    let text = titleText;
    if (slot.type === "label" && typeof slot.label === "function") {
        // Prefer dynamic page text when no fixed title, or append it
        let pageText = slot.label.call(null);
        text = titleText ? (titleText + ": " + pageText) : pageText;
    }
    if (!text) {
        hideToolbarTooltip();
        return;
    }
    let tip = ensureToolbarTooltipEl();
    tip.textContent = text;
    tip.hidden = false;
    // Keep tooltip inside the viewport
    let pad = 10;
    let left = event.clientX + 12;
    let top = event.clientY + 18;
    tip.style.left = "0px";
    tip.style.top = "0px";
    // Measure after content set
    let tw = tip.offsetWidth || 120;
    let th = tip.offsetHeight || 24;
    if (left + tw + pad > window.innerWidth) {
        left = Math.max(pad, event.clientX - tw - 12);
    }
    if (top + th + pad > window.innerHeight) {
        top = Math.max(pad, event.clientY - th - 10);
    }
    tip.style.left = left + "px";
    tip.style.top = top + "px";

    // Pointer cursor for clickable enabled icons
    if (typeof canvas !== "undefined" && canvas) {
        let clickable = slot.type !== "label"
            && typeof slot.enabled === "function" && slot.enabled.call(null);
        canvas.style.cursor = clickable ? "pointer" : "default";
        canvas.dataset.toolbarCursor = "1";
    }
}

// Initialize the hopper canvas, make sure it's set up for full resolution
//
function initialize() {
    // Reduce the size of the canvas to always fit on screen and never scroll
    //
    let w = window.innerHeight;
    let y = canvas.getBoundingClientRect().y;
    rect.height = w - y;

    // Scale to full resolution, not the 72dpi stuff
    //
    canvas.width = rect.width * devicePixelRatio;
    canvas.height = rect.height * devicePixelRatio;
    // Prefer absolute DPR transform (drawSvg resets this each paint)
    gc.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
    canvas.style.width = rect.width + "px";
    canvas.style.height = rect.height + "px";
    invalidatePageBaseCache();
    console.log("canvas size: " + canvas.width + "x" + canvas.height + ", DP-Ratio=" + devicePixelRatio);
}

function checkPages() {
    // Look up number of pages...
    //
    $.get(API_BASE + "render/info/pages/" + renderId + "/", function (result, status) {
        if (status === "success") {
            numberOfPages = parseInt(result);
            console.log("Number of available pages: " + numberOfPages);
        } else {
            numberOfPages = 1;
        }
        // Page label / enablement only — toolbar strip, not full page paint
        if (typeof scheduleCanvasRedraw === "function") {
            scheduleCanvasRedraw();
        } else if (typeof gc !== "undefined" && gc && canvas) {
            drawIcons(gc, canvasCssWidth ? canvasCssWidth() : (canvas.clientWidth || canvas.width));
        }
    });
}


/** Optional callback after the page SVG is painted (soft-reload client timing). */
let _onPageSvgPainted = null;
/** Revoke previous blob URL from inlined soft-reload SVG. */
let _pageSvgObjectUrl = null;
/**
 * Pixels per presentation unit for the current page image.
 * Soft-reload PNG is often 2× (HiDPI); SVG GETs are 1× (browser rasterizes at draw size).
 */
let _pageImagePixelRatio = 1;

/** True if {@code img} can be drawn (HTMLImageElement or ImageBitmap). */
function isPageImageReady(img) {
    if (!img) {
        return false;
    }
    // ImageBitmap from createImageBitmap
    if (typeof ImageBitmap !== "undefined" && img instanceof ImageBitmap) {
        return img.width > 0 && img.height > 0;
    }
    return !!(img.complete && (img.naturalWidth > 0 || img.width > 0));
}

/**
 * Logical page size in presentation units (matches layout geometries).
 * Bitmap may be larger when {@link _pageImagePixelRatio} &gt; 1.
 */
function pageLogicalSize(img) {
    img = img || image;
    if (!img) {
        return {width: 0, height: 0};
    }
    let pxW = img.naturalWidth || img.width || 0;
    let pxH = img.naturalHeight || img.height || 0;
    let pr = (_pageImagePixelRatio > 0) ? _pageImagePixelRatio : 1;
    return {width: pxW / pr, height: pxH / pr, pixelRatio: pr, pxW: pxW, pxH: pxH};
}

function finishPageSvgLoad(nextImage, tSvg0, meta) {
    let tLoaded = (typeof performance !== "undefined" && performance.now) ? performance.now() : Date.now();
    meta = meta || {};
    // Multi-sampled soft-reload PNG; SVG paths stay 1 presentation unit per user unit
    if (meta.inlinePng && typeof meta.pagePngScale === "number" && meta.pagePngScale > 0) {
        _pageImagePixelRatio = meta.pagePngScale;
    } else {
        _pageImagePixelRatio = 1;
    }
    image = nextImage;
    invalidatePageBaseCache();
    let tPaint0 = tLoaded;
    drawSvg();
    let tPainted = (typeof performance !== "undefined" && performance.now) ? performance.now() : Date.now();
    if (typeof _onPageSvgPainted === "function") {
        try {
            _onPageSvgPainted({
                svgLoadMs: Math.round(tLoaded - tSvg0),
                paintMs: Math.round(tPainted - tPaint0),
                svgAndPaintMs: Math.round(tPainted - tSvg0),
                inlineSvg: !!meta.inlineSvg,
                inlinePng: !!meta.inlinePng,
                pagePngScale: _pageImagePixelRatio
            });
        } catch (e) { /* ignore */ }
        _onPageSvgPainted = null;
    }
    if (typeof endPresentationBusy === "function") {
        endPresentationBusy();
    }
}

/**
 * Load the current page into the canvas {@code image}.
 * @param {string|null|undefined} inlineSvgXml optional SVG markup (fallback)
 * @param {string|null|undefined} inlinePngBase64 optional PNG (preferred for soft-reload —
 *   Chromium is very slow rasterizing some dark-themed SVGs into canvas)
 * @param {number|null|undefined} pagePngScale pixels per presentation unit for the PNG (default 1)
 */
function loadDrawSvgPage(inlineSvgXml, inlinePngBase64, pagePngScale) {
    let tSvg0 = (typeof performance !== "undefined" && performance.now) ? performance.now() : Date.now();
    if (typeof beginPresentationBusy === "function") {
        beginPresentationBusy();
    }
    // Keep the previous {@code image} until the next one is ready so the canvas
    // stays interactive (optimistic geometry on top of old pixels) during decode.
    if (_pageSvgObjectUrl) {
        try {
            URL.revokeObjectURL(_pageSvgObjectUrl);
        } catch (e) { /* ignore */ }
        _pageSvgObjectUrl = null;
    }

    function loadViaHtmlImage(src, meta) {
        let nextImage = new Image();
        nextImage.onload = function () {
            finishPageSvgLoad(nextImage, tSvg0, meta);
        };
        nextImage.onerror = function () {
            if (typeof _onPageSvgPainted === "function") {
                try {
                    _onPageSvgPainted({svgLoadMs: -1, paintMs: -1, error: true});
                } catch (e) { /* ignore */ }
                _onPageSvgPainted = null;
            }
            if (meta && (meta.inlineSvg || meta.inlinePng)) {
                // Retry network SVG; this call will beginBusy again — end this attempt first
                if (typeof endPresentationBusy === "function") {
                    endPresentationBusy();
                }
                loadDrawSvgPage(null, null);
                return;
            }
            // Stale renderId after restart/cache clear: rebuild via name-based view URL
            if (typeof presentationName !== "undefined" && presentationName
                && typeof isViewMode === "function" && isViewMode()
                && typeof viewPresentationUrl === "function") {
                if (typeof endPresentationBusy === "function") {
                    endPresentationBusy();
                }
                if (typeof beginPresentationBusy === "function") {
                    beginPresentationBusy();
                }
                let p0 = typeof currentPageIndex0 === "function" ? currentPageIndex0() : 0;
                let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
                window.open(viewPresentationUrl(presentationName, p0, cm), "_self");
                return;
            }
            if (typeof endPresentationBusy === "function") {
                endPresentationBusy();
            }
        };
        nextImage.src = src;
    }

    // 1) PNG (fast decode light + dark; often 2× pixels for HiDPI)
    if (inlinePngBase64 && typeof inlinePngBase64 === "string" && inlinePngBase64.length > 0) {
        let pr = (typeof pagePngScale === "number" && pagePngScale > 0) ? pagePngScale : 1;
        loadViaHtmlImage(
            "data:image/png;base64," + inlinePngBase64,
            {inlinePng: true, inlineSvg: false, pagePngScale: pr}
        );
        return;
    }

    // 2) Inline SVG blob (slow for some dark SVGs in Chromium — last resort before GET)
    if (inlineSvgXml && typeof inlineSvgXml === "string" && inlineSvgXml.length > 0) {
        try {
            let blob = new Blob([inlineSvgXml], {type: "image/svg+xml;charset=utf-8"});
            _pageSvgObjectUrl = URL.createObjectURL(blob);
            loadViaHtmlImage(_pageSvgObjectUrl, {inlineSvg: true, inlinePng: false, pagePngScale: 1});
            return;
        } catch (e) {
            console.warn("inline pageSvg failed, falling back to GET", e);
        }
    }

    // 3) Network GET SVG — browser rasterizes at device destination size (sharp)
    loadViaHtmlImage(
        API_BASE + "render/page/" + renderId + "/SVG/" + renderPageNumber0 + "/",
        {inlineSvg: false, inlinePng: false, pagePngScale: 1}
    );
}

/**
 * Offscreen cache of the expensive layer: chrome + toolbar + page SVG + static region outlines.
 * Selection / hover / drag ghosts are redrawn each frame on top via blit + overlays only.
 *
 * Always paint base into the offscreen canvas (never read back from the main canvas — that
 * was ~700ms). After the first paint for a given key, hover/select only blits the bitmap
 * (~1ms) instead of re-drawImage of the full page SVG (~0.5s lag to "find" components).
 */
let _pageBaseCanvas = null;
let _pageBaseKey = "";

function invalidatePageBaseCache() {
    _pageBaseKey = "";
}

/** CSS pixel size of the drawing surface (context is scaled by devicePixelRatio). */
function canvasCssSize() {
    let w = canvas ? (canvas.clientWidth || (rect && rect.width) || 0) : 0;
    let h = canvas ? (canvas.clientHeight || (rect && rect.height) || 0) : 0;
    if ((!w || !h) && canvas && devicePixelRatio > 0) {
        w = w || Math.round(canvas.width / devicePixelRatio);
        h = h || Math.round(canvas.height / devicePixelRatio);
    }
    return {width: w, height: h};
}

function ensureCanvasDprTransform(ctx) {
    let c = ctx || gc;
    if (!c) {
        return;
    }
    let dpr = devicePixelRatio || 1;
    c.setTransform(dpr, 0, 0, dpr, 0, 0);
}

function computePageDrawScale() {
    if (!isPageImageReady(image) || !canvas) {
        return 1;
    }
    let logical = pageLogicalSize(image);
    if (!(logical.width > 0) || !(logical.height > 0)) {
        return 1;
    }
    let css = canvasCssSize();
    let contentH = Math.max(1, css.height - ICON_SIZE);
    let contentW = Math.max(1, css.width);
    // Fit logical page units into CSS content area (bitmap may be multi-sampled)
    let scaleX = zoom * contentW / logical.width;
    let scaleY = zoom * contentH / logical.height;
    return Math.min(scaleX, scaleY, zoom);
}

/**
 * Cache key for the static page layer. Changes when pan/zoom/page/toolbar chrome would look different.
 */
function pageBaseCacheKey() {
    let undo = (typeof hopperUndoState !== "undefined" && hopperUndoState) ? hopperUndoState : {};
    return [
        typeof renderId !== "undefined" ? renderId : "",
        typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : "",
        image && (image.src || image.width + "x" + image.height) || "",
        canvas ? canvas.width + "x" + canvas.height : "",
        zoom,
        offset.x,
        offset.y,
        scale,
        _pageImagePixelRatio || 1,
        isUiDarkMode() ? "d" : "l",
        typeof numberOfPages !== "undefined" ? numberOfPages : "",
        typeof editLogicalPageNumber !== "undefined" ? editLogicalPageNumber : "",
        typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : "",
        undo.canUndo ? 1 : 0,
        undo.canRedo ? 1 : 0
    ].join("|");
}

function ensurePageBaseCanvas() {
    if (!_pageBaseCanvas) {
        _pageBaseCanvas = document.createElement("canvas");
    }
    if (_pageBaseCanvas.width !== canvas.width || _pageBaseCanvas.height !== canvas.height) {
        _pageBaseCanvas.width = canvas.width;
        _pageBaseCanvas.height = canvas.height;
    }
    return _pageBaseCanvas;
}

/** Blit cached base onto the main canvas and restore DPR transform for overlay drawing. */
function blitPageBaseToMain() {
    ensurePageBaseCanvas();
    gc.setTransform(1, 0, 0, 1, 0, 0);
    gc.drawImage(_pageBaseCanvas, 0, 0);
    ensureCanvasDprTransform(gc);
}

/**
 * Paint chrome + toolbar + page image + static region outlines into {@code ctx}
 * (defaults to main {@code gc}). Uses DPR transform; no pending translate when done.
 */
function paintPageBaseLayer(ctx) {
    let c = ctx || gc;
    if (!c) {
        return;
    }
    let css = canvasCssSize();
    ensureCanvasDprTransform(c);

    c.fillStyle = canvasThemeColor(
        "--hopper-canvas-chrome",
        isUiDarkMode() ? "#0b1220" : "#f4f4f8"
    );
    c.fillRect(0, 0, css.width, css.height);

    drawIcons(c, css.width);

    if (!isPageImageReady(image)) {
        return;
    }

    // Critical: never draw the page bitmap while a CSS filter is active (dark toolbar
    // used invert filters; a leaked filter made full-page drawImage multi-hundred-ms).
    c.filter = "none";

    let logical = pageLogicalSize(image);
    let pr = logical.pixelRatio || 1;
    let pageW = logical.width;
    let pageH = logical.height;
    // Source rect is in bitmap pixels; offset/pan are presentation units
    let srcX = offset.x * pr;
    let srcY = offset.y * pr;
    let srcW = pageW * pr;
    let srcH = pageH * pr;
    c.translate(0, ICON_SIZE);
    c.drawImage(
        image,
        srcX,
        srcY,
        srcW,
        srcH,
        0,
        0,
        pageW * scale,
        pageH * scale
    );
    // Static region outlines only — active drop band is drawn each frame on top
    drawPageRegions(c, scale, offset, pageW, pageH, false);
    c.translate(0, -ICON_SIZE);
}

/**
 * Build offscreen base by painting into it (no main-canvas readback).
 * Used when drag needs multi-frame blits; not on soft-reload first paint.
 */
function rebuildPageBaseCache() {
    if (!canvas || !isPageImageReady(image)) {
        return false;
    }
    scale = computePageDrawScale();
    let off = ensurePageBaseCanvas();
    let bctx = off.getContext("2d");
    // Clear in device pixels
    bctx.setTransform(1, 0, 0, 1, 0, 0);
    bctx.clearRect(0, 0, off.width, off.height);
    paintPageBaseLayer(bctx);
    _pageBaseKey = pageBaseCacheKey();
    return true;
}

function pageBaseCacheValid() {
    let key = pageBaseCacheKey();
    return !!(
        _pageBaseCanvas
        && _pageBaseKey === key
        && _pageBaseCanvas.width === canvas.width
        && _pageBaseCanvas.height === canvas.height
    );
}

function drawSvg() {
    if (!canvas || !gc) {
        return;
    }
    if (!isPageImageReady(image)) {
        // Still paint toolbar chrome if possible
        ensureCanvasDprTransform(gc);
        let css0 = canvasCssSize();
        gc.fillStyle = canvasThemeColor(
            "--hopper-canvas-chrome",
            isUiDarkMode() ? "#0b1220" : "#f4f4f8"
        );
        gc.fillRect(0, 0, css0.width, css0.height);
        drawIcons(gc, css0.width);
        return;
    }

    scale = computePageDrawScale();

    // Always keep a valid offscreen base so hover/select only blits + draws outlines
    // (re-drawImage of the full page SVG on every hover change felt like ~0.5s lag).
    if (!pageBaseCacheValid()) {
        rebuildPageBaseCache();
    }
    blitPageBaseToMain();

    // Dynamic layer: active drop band + selection/hover/drag ghosts
    ensureCanvasDprTransform(gc);
    gc.translate(0, ICON_SIZE);
    let pageSize = pageLogicalSize(image);
    drawPageRegions(gc, scale, offset, pageSize.width, pageSize.height, true);
    if (typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.drawOverlays === "function") {
        window.hopperEdit.drawOverlays(gc, scale, offset);
    }
    gc.translate(0, -ICON_SIZE);
}

/**
 * Light gray outlines for the full page and (when present) header / content / footer bands.
 * Active drop/drag target region is drawn with a thicker border.
 *
 * @param {boolean|undefined} activeOnly when true, only paints the active drop-band highlight
 *   (dynamic layer). When false, paints static outlines only (base layer). When undefined,
 *   paints both (legacy).
 */
function drawPageRegions(gcCtx, sc, off, pageW, pageH, activeOnly) {
    if (!gcCtx || !pageW || !pageH || !sc) {
        return;
    }
    let regions = null;
    let active = null;
    if (typeof window.hopperEdit !== "undefined") {
        if (typeof window.hopperEdit.getPageRegions === "function") {
            regions = window.hopperEdit.getPageRegions();
        }
        if (activeOnly !== false
            && typeof window.hopperEdit.getActiveDropRegion === "function") {
            active = window.hopperEdit.getActiveDropRegion();
        }
    }

    // Fallback: full page only
    if (!regions || !regions.page) {
        regions = {
            page: {x: 0, y: 0, width: pageW, height: pageH},
            content: null,
            header: null,
            footer: null
        };
    }

    function strokeRegion(rect, isActive) {
        if (!rect || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        // activeOnly mode: skip non-active strokes (already in base cache)
        if (activeOnly === true && !isActive) {
            return;
        }
        if (activeOnly === false && isActive) {
            return;
        }
        let x = (rect.x - off.x) * sc;
        let y = (rect.y - off.y) * sc;
        let w = rect.width * sc;
        let h = rect.height * sc;
        let lineW = isActive ? 2 : 1;
        gcCtx.save();
        gcCtx.setLineDash([]);
        gcCtx.lineWidth = lineW;
        if (isActive) {
            gcCtx.strokeStyle = isUiDarkMode()
                ? "rgba(59, 130, 246, 0.95)"
                : "rgba(120, 150, 190, 0.95)";
            gcCtx.fillStyle = isUiDarkMode()
                ? "rgba(59, 130, 246, 0.12)"
                : "rgba(160, 190, 230, 0.08)";
            gcCtx.fillRect(x, y, w, h);
            gcCtx.strokeRect(x + 0.5, y + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));
        } else {
            gcCtx.strokeStyle = canvasThemeColor(
                "--hopper-page-outline",
                isUiDarkMode() ? "rgba(148, 163, 184, 0.45)" : "rgba(190, 190, 190, 0.75)"
            );
            gcCtx.strokeRect(x + 0.5, y + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));
        }
        gcCtx.restore();
    }

    if (activeOnly === true) {
        // Only the highlighted band
        if (regions.header && active === "header") {
            strokeRegion(regions.header, true);
        }
        if (regions.content && active === "content") {
            strokeRegion(regions.content, true);
        }
        if (regions.footer && active === "footer") {
            strokeRegion(regions.footer, true);
        }
        return;
    }

    // (static outlines continue below)

    // Outer page contour, then header / content / footer bands (static outlines)
    strokeRegion(regions.page, false);
    if (regions.header) {
        strokeRegion(regions.header, false);
    }
    if (regions.content) {
        strokeRegion(regions.content, false);
    }
    if (regions.footer) {
        strokeRegion(regions.footer, false);
    }
    // Designer: logical vs rendered page identity (top-right of page frame)
    if (activeOnly !== true && regions.page) {
        drawPageIdentityLabel(gcCtx, sc, off, regions.page);
    }
}

function indicateClickPossibility(event, result) {
    // Clear the canvas first
    //
    gc.fillStyle = '#ffffff';
    gc.strokeStyle = '#ff0000';
    gc.lineWidth = 2;
    gc.fillRect(0, 0, canvas.width, canvas.height);

    // Redraw the image
    //
    drawSvg();

    if (result
        && result["found"]
        && result["drawnItem"] != null
        && result["drawnItem"]["geometry"] != null) {

        let geo = result["drawnItem"]["geometry"];
        // Draw a blue outline + light fill over the interactive subject
        // (component envelope, cell, series label, …)
        setClickableRegion((geo.x - offset.x) * scale,
            (geo.y - offset.y) * scale,
            Math.max(2, geo.width * scale),
            Math.max(2, geo.height * scale),
            ICON_SIZE);
        return true;
    }

    // Show the default cursor
    $("#svgCanvas").css("cursor", "default");

    return false;
}

function setClickableRegion(x, y, width, height, yTranslation) {
    if (yTranslation > 0) {
        gc.translate(0, yTranslation);
    }
    gc.save();
    // Light fill so the region is obvious without hiding the chart
    gc.fillStyle = "rgba(30, 90, 200, 0.18)";
    gc.strokeStyle = "rgba(20, 70, 180, 0.95)";
    gc.lineWidth = 2;
    gc.setLineDash([6, 4]);
    gc.fillRect(x, y, width, height);
    gc.strokeRect(x + 0.5, y + 0.5, Math.max(0, width - 1), Math.max(0, height - 1));
    gc.setLineDash([]);
    gc.restore();
    if (yTranslation > 0) {
        gc.translate(0, -yTranslation);
    }
    // Show a hand cursor
    $("#svgCanvas").css("cursor", "pointer");
}

function checkPreviousLookup(x, y) {
    for (let i = 0; i < lookupResults.length; i++) {
        let result = lookupResults[i];
        // See if x,y falls in a geometry
        //
        let geo = result["drawnItem"]["geometry"];
        if (x >= geo.x && y >= geo.y && x <= geo.x + geo.width && y <= geo.y + geo.height) {
            return result;
        }
    }
    return null;
}

function invalidMouseLocation(x, y) {
    // Presentation-space bounds (not bitmap pixels when PNG is multi-sampled)
    let pageSize = typeof pageLogicalSize === "function"
        ? pageLogicalSize(image)
        : {width: image && image.width, height: image && image.height};
    return x < 0 || y < 0 || x > pageSize.width || y > pageSize.height;
}

function handleMouseMoveActions(event) {
    // Page pan is driven by the document-level mousemove (works outside the canvas)
    if (panState) {
        return true;
    }

    let x = correctX(event.offsetX);
    let y = correctY(event.offsetY);

    // Always track pointer for Ctrl+V paste-at-cursor (including outside page bounds)
    if (isEditMode()
        && typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.notePagePointer === "function") {
        window.hopperEdit.notePagePointer(x, y);
    }

    // Edit drag may continue outside the page plane; still forward moves while dragging
    if (isEditMode()
        && typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.onCanvasMouseMove === "function"
        && window.hopperEdit.onCanvasMouseMove(event, x, y)) {
        return true;
    }

    if (invalidMouseLocation(x, y)) {
        if (isEditMode()
            && typeof window.hopperEdit !== "undefined"
            && typeof window.hopperEdit.onPageMouseMove === "function") {
            window.hopperEdit.onPageMouseMove(null);
        }
        return false;
    }

    // Edit mode: client-side hit-test against component geometries (no per-move server calls)
    if (isEditMode()
        && typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.onPageMouseMove === "function") {
        window.hopperEdit.onPageMouseMove(x, y);
        return true;
    }

    // View mode: interaction hover via server lookup (cached)
    let result = checkPreviousLookup(x, y);
    if (result != null) {
        return indicateClickPossibility(event, result);
    }

    $.ajax({
            url: API_BASE + "render/lookupActions/",
            type: "POST",
            data: JSON.stringify({
                "renderId": renderId,
                "pageNumber": renderPageNumber0,
                "x": x,
                "y": y
            }),
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (result) {
                indicateClickPossibility(event, result);
                if (result["found"] && result["drawnItem"] != null && result["drawnItem"]["geometry"] != null) {
                    lookupResults.push(result);
                    return true;
                }
            }
        }
    );
    return false;
}


function onLeftClick(requestData) {
    $.ajax({
        url: API_BASE + "render/lookupActions/",
        type: "POST",
        data: JSON.stringify(requestData),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (result) {
            if (!result || !result.found) {
                return;
            }
            const method = result.method;
            // Default to single-click if method is missing (older payloads)
            const isClick = !method || method.mouseClick || (!method.mouseDoubleClick);
            if (!isClick) {
                return;
            }
            $("#svgCanvas").css("cursor", "default");

            const actions = result.actions || [];
            for (let i = 0; i < actions.length; i++) {
                let action = actions[i];
                if (!action) {
                    continue;
                }
                if (action.actionType === "OPEN_PRESENTATION") {
                    let targetName = action.objectName;
                    let ctx = (result.drawnItem && result.drawnItem.context) ? result.drawnItem.context : null;
                    let cellValue = ctx ? ctx.value : null;
                    // Empty target => presentation name is the clicked cell value
                    if (targetName === null || targetName === undefined || targetName === "") {
                        targetName = cellValue;
                    }
                    let params = collectInteractionActionParameters(action, ctx);
                    if (targetName) {
                        console.log("Open presentation: " + targetName
                            + (params.length ? (", params=" + JSON.stringify(params)) : ""));
                        openPresentation(targetName, params);
                    }
                } else if (action.actionType === "OPEN_LINK_SAME_TAB" && action.objectName) {
                    window.open(action.objectName, "_self");
                } else if (action.actionType === "OPEN_LINK_NEW_TAB" && action.objectName) {
                    window.open(action.objectName, "_blank");
                }
            }
        },
        error: function (request, status, error) {
            console.warn("lookupActions failed:", request && request.responseText, status, error);
        }
    });
}

/**
 * Run initScript + loadScript after the form HTML is injected.
 * Deferred so synchronous XHR inside those scripts is not nested inside an async AJAX
 * success callback (which can freeze the browser UI thread).
 */
function runFormScripts(contextLabel) {
    let label = contextLabel || "form";
    try {
        let initScript = document.getElementById("initScript");
        if (initScript) {
            eval(initScript.innerHTML);
        }
    } catch (e) {
        alert("Error initializing the " + label + " form: " + e);
        throw e;
    }
    try {
        let loadScript = document.getElementById("loadScript");
        if (loadScript) {
            eval(loadScript.innerHTML);
        }
    } catch (e) {
        alert("Error loading the " + label + " values: " + e);
        throw e;
    }
}

/**
 * Breadcrumb trail above the component property form:
 * Presentation › Page › Group (Group) › Composite (Composite) › Label (Label)
 * @param {Array} crumbs from API payload.breadcrumb
 */
function installComponentBreadcrumb(crumbs) {
    let editArea = document.getElementById("editArea");
    if (!editArea) {
        return;
    }
    let existing = document.getElementById("componentBreadcrumb");
    if (existing) {
        existing.remove();
    }
    if (!crumbs || !crumbs.length) {
        return;
    }
    let nav = document.createElement("nav");
    nav.id = "componentBreadcrumb";
    nav.className = "component-breadcrumb";
    nav.setAttribute("aria-label", "Component location");
    let html = "";
    for (let i = 0; i < crumbs.length; i++) {
        if (i > 0) {
            html += '<span class="component-breadcrumb-sep" aria-hidden="true">›</span>';
        }
        let c = crumbs[i] || {};
        let kind = c.kind || "";
        let label = c.label != null ? String(c.label) : (c.name || kind || "");
        let safeLabel = label.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
        let isCurrent = c.current === true || (kind === "component" && i === crumbs.length - 1);
        if (isCurrent) {
            html += '<span class="component-breadcrumb-item component-breadcrumb-current"'
                + ' aria-current="page">' + safeLabel + "</span>";
        } else if (kind === "presentation") {
            html += '<button type="button" class="component-breadcrumb-item component-breadcrumb-link"'
                + ' data-kind="presentation" title="Edit presentation properties">'
                + safeLabel + "</button>";
        } else if (kind === "page") {
            let li = c.logicalPageNumber != null ? parseInt(c.logicalPageNumber, 10) : 0;
            if (isNaN(li) || li < 0) {
                li = 0;
            }
            html += '<button type="button" class="component-breadcrumb-item component-breadcrumb-link"'
                + ' data-kind="page" data-logical-index="' + li + '"'
                + ' title="Edit page properties">'
                + safeLabel + "</button>";
        } else if (kind === "component" && c.name) {
            let safeName = String(c.name).replace(/&/g, "&amp;").replace(/"/g, "&quot;");
            html += '<button type="button" class="component-breadcrumb-item component-breadcrumb-link"'
                + ' data-kind="component" data-name="' + safeName + '"'
                + ' title="Edit ' + safeLabel + '">'
                + safeLabel + "</button>";
        } else {
            html += '<span class="component-breadcrumb-item component-breadcrumb-static">'
                + safeLabel + "</span>";
        }
    }
    nav.innerHTML = html;
    // Always first in the form column
    editArea.insertBefore(nav, editArea.firstChild);

    nav.addEventListener("click", function (ev) {
        let btn = ev.target.closest(".component-breadcrumb-link");
        if (!btn || !nav.contains(btn)) {
            return;
        }
        ev.preventDefault();
        let kind = btn.getAttribute("data-kind");
        if (kind === "presentation") {
            if (typeof openPresentationProperties === "function") {
                openPresentationProperties();
            }
            return;
        }
        if (kind === "page") {
            let idx = parseInt(btn.getAttribute("data-logical-index"), 10);
            if (isNaN(idx) || idx < 0) {
                idx = 0;
            }
            if (typeof openPageProperties === "function") {
                openPageProperties(idx);
            }
            return;
        }
        if (kind === "component") {
            let name = btn.getAttribute("data-name");
            if (!name) {
                return;
            }
            // Sync canvas selection when a drawn geometry exists for this metadata name
            if (typeof window.hopperEdit !== "undefined"
                && typeof window.hopperEdit.selectComponent === "function") {
                try {
                    window.hopperEdit.selectComponent(name, false);
                } catch (e) {
                    // ignore — nested template may not have a top-level geometry entry
                }
            }
            openComponentPropertiesByName(name);
        }
    });
}

/**
 * Ensure a layout/render error panel exists at the top of the property form.
 * @returns {HTMLElement|null}
 */
function ensureComponentErrorPanel() {
    let editArea = document.getElementById("editArea");
    if (!editArea) {
        return null;
    }
    let panel = document.getElementById("componentErrorPanel");
    if (panel) {
        return panel;
    }
    panel = document.createElement("div");
    panel.id = "componentErrorPanel";
    panel.className = "component-error-panel";
    panel.setAttribute("hidden", "hidden");
    panel.innerHTML =
        '<div class="component-error-header">'
        + '<span class="component-error-title">Component error</span>'
        + '<span class="component-error-actions">'
        + '<button type="button" class="component-error-toggle-detail" title="Show or hide full details">Details</button>'
        + '<button type="button" class="component-error-copy" title="Copy full error">Copy</button>'
        + '</span></div>'
        + '<p class="component-error-summary" id="componentErrorSummary"></p>'
        + '<textarea class="component-error-detail" id="componentErrorDetail" readonly rows="8"'
        + ' spellcheck="false" hidden></textarea>';
    // Keep breadcrumb above the error panel
    let crumb = document.getElementById("componentBreadcrumb");
    if (crumb && crumb.parentElement === editArea) {
        if (crumb.nextSibling) {
            editArea.insertBefore(panel, crumb.nextSibling);
        } else {
            editArea.appendChild(panel);
        }
    } else {
        editArea.insertBefore(panel, editArea.firstChild);
    }

    let toggle = panel.querySelector(".component-error-toggle-detail");
    let copyBtn = panel.querySelector(".component-error-copy");
    let detail = panel.querySelector("#componentErrorDetail");
    if (toggle && detail) {
        toggle.onclick = function () {
            if (detail.hasAttribute("hidden")) {
                detail.removeAttribute("hidden");
                toggle.textContent = "Hide details";
            } else {
                detail.setAttribute("hidden", "hidden");
                toggle.textContent = "Details";
            }
        };
    }
    if (copyBtn) {
        copyBtn.onclick = function () {
            let summaryEl = document.getElementById("componentErrorSummary");
            let detailEl = document.getElementById("componentErrorDetail");
            let text = "";
            if (summaryEl && summaryEl.textContent) {
                text += summaryEl.textContent;
            }
            if (detailEl && detailEl.value) {
                text += (text ? "\n\n" : "") + detailEl.value;
            }
            if (!text) {
                return;
            }
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).catch(function () {
                    // fallback below
                    detailEl.removeAttribute("hidden");
                    detailEl.focus();
                    detailEl.select();
                    try {
                        document.execCommand("copy");
                    } catch (e) { /* ignore */ }
                });
            } else if (detailEl) {
                detailEl.removeAttribute("hidden");
                detailEl.focus();
                detailEl.select();
                try {
                    document.execCommand("copy");
                } catch (e) { /* ignore */ }
            }
        };
    }
    return panel;
}

/** Hide the component error panel (no error or form closed). */
function clearComponentErrorPanel() {
    let panel = document.getElementById("componentErrorPanel");
    if (!panel) {
        return;
    }
    panel.classList.remove("is-visible");
    panel.setAttribute("hidden", "hidden");
    let summary = document.getElementById("componentErrorSummary");
    let detail = document.getElementById("componentErrorDetail");
    if (summary) {
        summary.textContent = "";
    }
    if (detail) {
        detail.value = "";
        detail.setAttribute("hidden", "hidden");
    }
    let toggle = panel.querySelector(".component-error-toggle-detail");
    if (toggle) {
        toggle.textContent = "Details";
    }
}

/**
 * Show layout/render failure details in the property form.
 * @param {string} summary short message (root cause preferred)
 * @param {string} [detail] full cause chain / stack
 */
function showComponentErrorPanel(summary, detail) {
    if (!summary && !detail) {
        clearComponentErrorPanel();
        return;
    }
    let panel = ensureComponentErrorPanel();
    if (!panel) {
        return;
    }
    let summaryEl = document.getElementById("componentErrorSummary");
    let detailEl = document.getElementById("componentErrorDetail");
    let textSummary = summary || "Component layout or render failed";
    let textDetail = detail || summary || "";
    if (summaryEl) {
        summaryEl.textContent = textSummary;
    }
    if (detailEl) {
        detailEl.value = textDetail;
        // Auto-expand details when chain is longer than the summary
        if (textDetail && textDetail !== textSummary && textDetail.indexOf("\n") >= 0) {
            detailEl.removeAttribute("hidden");
            let toggle = panel.querySelector(".component-error-toggle-detail");
            if (toggle) {
                toggle.textContent = "Hide details";
            }
        }
    }
    panel.classList.add("is-visible");
    panel.removeAttribute("hidden");
}

/**
 * Load diagnostics for the open component and update the error panel.
 * Uses cached layout error when provided, then refreshes from the server.
 */
function loadComponentDiagnostics(componentName, cachedSummary, cachedDetail) {
    if (!componentName || typeof presentationName === "undefined") {
        return;
    }
    if (cachedSummary) {
        showComponentErrorPanel(cachedSummary, cachedDetail || cachedSummary);
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/components/" + encodeURIComponent(componentName) + "/diagnostics",
        type: "GET",
        dataType: "json",
        success: function (data) {
            if (!data || data.ok === true || !data.summary) {
                // Clear only if we did not have a cached error; otherwise keep until ok
                if (!cachedSummary) {
                    clearComponentErrorPanel();
                } else if (data && data.ok === true) {
                    clearComponentErrorPanel();
                }
                return;
            }
            showComponentErrorPanel(data.summary, data.detail || data.summary);
        },
        error: function (xhr) {
            // Fall back to cached or a generic note
            if (!cachedSummary) {
                let body = (xhr && xhr.responseText) ? xhr.responseText : "Diagnostics request failed";
                showComponentErrorPanel("Could not load component diagnostics", body);
            }
        }
    });
}

/**
 * Resolve the collapsible "Layout options" content div (contains left/right/top/bottom).
 * Falls back to null when layout fields are missing.
 */
function findLayoutSectionContent() {
    let anchor = document.getElementById("leftEnabled")
        || document.getElementById("topEnabled")
        || document.getElementById("rightEnabled")
        || document.getElementById("bottomEnabled");
    if (!anchor) {
        return null;
    }
    let el = anchor;
    let editArea = document.getElementById("editArea");
    while (el && el !== editArea) {
        if (el.classList && el.classList.contains("content")) {
            return el;
        }
        el = el.parentElement;
    }
    // Fallback: collapsible button titled "Layout options"
    if (editArea) {
        let buttons = editArea.querySelectorAll("button.collapsible");
        for (let i = 0; i < buttons.length; i++) {
            let title = (buttons[i].textContent || "").trim().toLowerCase();
            if (title === "layout options" || title === "layout") {
                let next = buttons[i].nextElementSibling;
                if (next && next.classList && next.classList.contains("content")) {
                    return next;
                }
            }
        }
    }
    return null;
}

/**
 * Layout feedback: human attachment lines + resolved geometry/pages from the server.
 * Placed at the bottom of the Layout options section. Refreshed on Apply (see softReloadEditor).
 * Also wires live per-side hints while editing LAYOUT_SIDE fields.
 */
function installLayoutFeedbackPanel(componentName) {
    let editArea = document.getElementById("editArea");
    if (!editArea || !componentName) {
        return;
    }
    // Only when the form has layout attachment fields
    if (!document.getElementById("leftEnabled")
        && !document.getElementById("topEnabled")
        && !document.getElementById("rightEnabled")
        && !document.getElementById("bottomEnabled")) {
        return;
    }
    let panel = document.getElementById("layoutResultPanel");
    if (!panel) {
        panel = document.createElement("div");
        panel.id = "layoutResultPanel";
        panel.className = "layout-result-panel";
    }
    // Always place at the bottom of the Layout options section (move if already elsewhere)
    let layoutContent = findLayoutSectionContent();
    if (layoutContent) {
        layoutContent.appendChild(panel);
    } else if (!panel.parentElement) {
        // Fallback: end of form if layout section markup is unexpected
        editArea.appendChild(panel);
    }
    panel.innerHTML = "<h4>Layout result</h4>"
        + "<p class=\"editor-hint\" id=\"layoutResultStatus\">Loading layout info...</p>"
        + "<ul id=\"layoutResultAttachments\" class=\"layout-result-list\"></ul>"
        + "<p id=\"layoutResultGeometry\" class=\"layout-result-geo\"></p>"
        + "<p id=\"layoutResultPages\" class=\"layout-result-pages\"></p>"
        + "<ul id=\"layoutResultWarnings\" class=\"layout-result-warnings\"></ul>";

    wireLayoutSideLiveHints();
    refreshLayoutSideLiveHints();
    loadComponentLayoutInfo(componentName);
}

function summarizeLayoutSideFromForm(side) {
    let en = document.getElementById(side + "Enabled");
    if (!en || !en.checked) {
        return "";
    }
    let relEl = document.getElementById(side + "ObjectName");
    let offEl = document.getElementById(side + "Offset");
    let pctEl = document.getElementById(side + "Percentage");
    let alEl = document.getElementById(side + "Alignment");
    let rel = relEl && relEl.value ? relEl.value : "";
    let edge = alEl && alEl.value ? alEl.value : "DEFAULT";
    let off = offEl ? parseInt(offEl.value, 10) || 0 : 0;
    let pct = pctEl ? parseInt(pctEl.value, 10) || 0 : 0;
    let target = rel ? ("\"" + rel + "\"") : "page";
    let s = capitalizeFirst(side) + ": " + String(edge).toLowerCase() + " edge of " + target;
    if (off) {
        s += (off > 0 ? " + " : " - ") + Math.abs(off) + " px";
    }
    if (pct) {
        s += " + " + pct + "%";
    }
    return s;
}

function capitalizeFirst(s) {
    if (!s) {
        return s;
    }
    return s.charAt(0).toUpperCase() + s.slice(1);
}

function refreshLayoutSideLiveHints() {
    ["left", "right", "top", "bottom"].forEach(function (side) {
        let hint = document.getElementById(side + "LayoutHint");
        if (!hint) {
            return;
        }
        hint.textContent = summarizeLayoutSideFromForm(side);
    });
}

function wireLayoutSideLiveHints() {
    ["left", "right", "top", "bottom"].forEach(function (side) {
        ["Enabled", "ObjectName", "Offset", "Percentage", "Alignment"].forEach(function (suffix) {
            let el = document.getElementById(side + suffix);
            if (el && !el._layoutHintWired) {
                el._layoutHintWired = true;
                el.addEventListener("change", refreshLayoutSideLiveHints);
                el.addEventListener("input", refreshLayoutSideLiveHints);
            }
        });
    });
}

function loadComponentLayoutInfo(componentName) {
    if (!componentName || typeof presentationName === "undefined") {
        return;
    }
    let st = document.getElementById("layoutResultStatus");
    if (st) {
        st.textContent = "Refreshing layout info...";
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/components/" + encodeURIComponent(componentName) + "/layout-info",
        type: "GET",
        dataType: "json",
        success: function (data) {
            renderLayoutResultPanel(data);
        },
        error: function (xhr) {
            let statusEl = document.getElementById("layoutResultStatus");
            if (statusEl) {
                statusEl.textContent = "Could not load layout info: "
                    + ((xhr && xhr.responseText) ? xhr.responseText : xhr.status);
            }
        }
    });
}

function renderLayoutResultPanel(data) {
    let st = document.getElementById("layoutResultStatus");
    let attUl = document.getElementById("layoutResultAttachments");
    let geoP = document.getElementById("layoutResultGeometry");
    let pagesP = document.getElementById("layoutResultPages");
    let warnUl = document.getElementById("layoutResultWarnings");
    if (!st) {
        return;
    }
    if (!data || data.ok === false) {
        st.textContent = (data && data.warnings && data.warnings[0])
            ? data.warnings[0]
            : "Layout info unavailable";
        return;
    }
    st.textContent = "After layout (saved attachments):";
    if (attUl) {
        attUl.innerHTML = "";
        let atts = data.attachments || {};
        ["left", "right", "top", "bottom"].forEach(function (side) {
            if (!atts[side]) {
                return;
            }
            let li = document.createElement("li");
            li.textContent = atts[side].summary || side;
            attUl.appendChild(li);
        });
        if (!attUl.children.length) {
            let li = document.createElement("li");
            li.className = "editor-hint";
            li.textContent = "(no attachments enabled)";
            attUl.appendChild(li);
        }
    }
    if (geoP) {
        let g = data.resolved;
        if (g) {
            geoP.textContent = "Resolved box: x=" + g.x + ", y=" + g.y
                + ", width=" + g.width + ", height=" + g.height + " px";
        } else {
            geoP.textContent = "Resolved box: (none)";
        }
    }
    if (pagesP) {
        let pages = data.pages || [];
        let pc = data.pageCount != null ? data.pageCount : "?";
        if (!pages.length) {
            pagesP.textContent = "Present on pages: none of " + pc;
        } else if (pages.length === 1) {
            pagesP.textContent = "Present on page " + (pages[0] + 1) + " of " + pc;
        } else {
            pagesP.textContent = "Present on "
                + pages.length + " pages (first page " + (pages[0] + 1)
                + ", last page " + (pages[pages.length - 1] + 1) + ") of " + pc;
        }
    }
    if (warnUl) {
        warnUl.innerHTML = "";
        let warns = data.warnings || [];
        for (let i = 0; i < warns.length; i++) {
            let li = document.createElement("li");
            li.textContent = warns[i];
            warnUl.appendChild(li);
        }
    }
}

/**
 * @param url form HTML URL
 * @param panelOptions optional { withPreview, componentName, geometry, layoutError, layoutErrorDetail, breadcrumb }
 */
function openEditArea(url, panelOptions) {
    panelOptions = panelOptions || {};
    // Component property forms show preview; connector/admin forms do not
    if (panelOptions.withPreview === undefined) {
        panelOptions.withPreview = false;
    }
    setSidePanelOpen(true, panelOptions);
    // Reset combo dependency registries for the new form
    connectorColumnListTables = [];
    connectorColumnSelects = [];
    connectorNames = null;
    themeNames = null;
    componentNames = null;
    $.ajax({
        url: url,
        type: "GET",
        contentType: "application/json; charset=utf-8",
        dataType: "html",
        success: function (snippet) {
            let editArea = document.getElementById("editArea");
            editArea.innerHTML = snippet;
            // Dual-asset monochrome icons (source-connector actions, etc.)
            if (typeof window.HThemeMode !== "undefined"
                && typeof window.HThemeMode.refreshUiIcons === "function") {
                window.HThemeMode.refreshUiIcons(editArea);
            } else if (typeof refreshUiIcons === "function") {
                refreshUiIcons(editArea);
            }
            // Breadcrumb first (presentation › page › ancestors › current)
            if (panelOptions.breadcrumb && panelOptions.breadcrumb.length) {
                installComponentBreadcrumb(panelOptions.breadcrumb);
            }
            // Defer past the AJAX completion stack
            setTimeout(function () {
                runFormScripts(componentPluginId || "component");
                // Layout/render error panel (above form fields, under breadcrumb)
                if (panelOptions.withPreview && panelOptions.componentName) {
                    loadComponentDiagnostics(
                        panelOptions.componentName,
                        panelOptions.layoutError || null,
                        panelOptions.layoutErrorDetail || null
                    );
                    installLayoutFeedbackPanel(panelOptions.componentName);
                } else {
                    clearComponentErrorPanel();
                }
                if (panelOptions.withPreview && panelOptions.componentName) {
                    loadComponentPreview(panelOptions.componentName, panelOptions.geometry || null);
                }
            }, 0);
        },
        error: function (request, status, error) {
            alert(request.responseText);
        }
    });
}

/**
 * Edit the component JSON given in the specified panel (div).
 * After editing we need to set the width of this panel back to 0.
 * The render ID and presentation name are known for the whole page.
 *
 * @param payload either a HComponent JSON object, or
 *   { component, logicalPageNumber, pageRole } from getComponent
 * @param requestData click context (renderId, pageNumber, x, y)
 */
function editComponent(payload, requestData) {
    // Prefer wrapped { component, logicalPageNumber, pageRole, breadcrumb } from getComponent
    let component;
    let layoutError = null;
    let layoutErrorDetail = null;
    let breadcrumb = null;
    if (payload && (payload.logicalPageNumber !== undefined || payload.pageRole !== undefined
        || payload.layoutError !== undefined || payload.component || payload.breadcrumb)) {
        component = payload.component;
        editLogicalPageNumber = parseInt(payload.logicalPageNumber);
        if (isNaN(editLogicalPageNumber) || editLogicalPageNumber < 0) {
            editLogicalPageNumber =
                requestData && requestData.pageNumber !== undefined
                    ? parseInt(requestData.pageNumber) : 0;
        }
        editPageRole = payload.pageRole || "page";
        layoutError = payload.layoutError || null;
        layoutErrorDetail = payload.layoutErrorDetail || null;
        breadcrumb = payload.breadcrumb || null;
    } else {
        // Legacy bare component JSON
        component = payload;
        editLogicalPageNumber =
            requestData && requestData.pageNumber !== undefined
                ? parseInt(requestData.pageNumber) : 0;
        if (isNaN(editLogicalPageNumber) || editLogicalPageNumber < 0) {
            editLogicalPageNumber = 0;
        }
        editPageRole = "page";
    }

    if (!component) {
        alert("No component data returned from server");
        return;
    }

    oldComponentName = component["name"];

    // Plugin map under component.component.{pluginId}
    let iComponent = component["component"];
    if (!iComponent) {
        alert("Component payload has no plugin data: " + JSON.stringify(component).slice(0, 200));
        return;
    }

    componentPluginId = Object.keys(iComponent)[0];
    componentJson = component;

    if (connectorNames === null) {
        connectorNames = getConnectorNames();
    }
    if (componentNames === null) {
        componentNames = getComponentNames();
    }
    if (themeNames === null) {
        themeNames = getThemeNames();
    }

    let geo = null;
    if (typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.getGeometries === "function") {
        let geos = window.hopperEdit.getGeometries() || [];
        for (let i = 0; i < geos.length; i++) {
            if (geos[i].componentName === oldComponentName && geos[i].geometry) {
                geo = geos[i].geometry;
                // Geometries may carry layout error from the last full-page render
                if (!layoutError && geos[i].layoutError) {
                    layoutError = geos[i].layoutError;
                    layoutErrorDetail = geos[i].layoutErrorDetail || geos[i].layoutError;
                }
                break;
            }
        }
    }

    openEditArea(API_BASE + "edit/component/" + componentPluginId, {
        withPreview: true,
        componentName: oldComponentName,
        geometry: geo,
        layoutError: layoutError,
        layoutErrorDetail: layoutErrorDetail,
        breadcrumb: breadcrumb
    });
}

function onCtrlLeftClick(requestData) {
    // In edit mode, skip the server round-trip when local hit-test already knows it's empty
    if (isEditMode()
        && typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.hitTest === "function") {
        let localHit = window.hopperEdit.hitTest(requestData.x, requestData.y);
        if (!localHit) {
            if (typeof window.hopperEdit.clearSelection === "function") {
                window.hopperEdit.clearSelection();
            }
            return;
        }
    }
    $.ajax({
        url: API_BASE + "render/getComponent/",
        type: "POST",
        data: JSON.stringify(requestData),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (payload) {
            // Empty canvas click — no component under cursor (not an error)
            if (!payload || payload.empty === true || !payload.component) {
                if (isEditMode()
                    && typeof window.hopperEdit !== "undefined"
                    && typeof window.hopperEdit.clearSelection === "function") {
                    window.hopperEdit.clearSelection();
                }
                return;
            }
            editComponent(payload, requestData);
        },
        error: function (request, status, error) {
            showAjaxError("Could not open component", request, status, error);
        }
    });
}


function handleMouseLeftClickActions(e) {
    // Ctrl/Meta + left is reserved for page pan (handled in mousedown)
    if (e.ctrlKey || e.metaKey) {
        return false;
    }

    // See if it's a toolbar icon
    //
    if (handleToolbarIconClick(e)) {
        return true;
    }

    let x = correctX(e.offsetX);
    let y = correctY(e.offsetY);

    if (invalidMouseLocation(x, y)) {
        return false;
    }

    let requestData = {
        "renderId": renderId,
        "pageNumber": renderPageNumber0,
        "x": x,
        "y": y
    };

    if (isEditMode()) {
        // Authoring: hopper-edit owns mousedown→drag→mouseup (select / move / open properties)
        if (typeof window.hopperEdit !== "undefined"
            && typeof window.hopperEdit.handleCanvasMouseDown === "function") {
            window.hopperEdit.handleCanvasMouseDown(e, x, y, requestData);
            return true;
        }
        onCtrlLeftClick(requestData);
        return true;
    } else {
        // View: interaction navigation only (no structural edit)
        onLeftClick(requestData);
        return true;
    }
}

/**
 * Begin panning the page view (grab-drag). Offset is in page/image space.
 */
function startPagePan(e) {
    if (e && typeof e.preventDefault === "function") {
        e.preventDefault();
    }
    // Do not start a component drag while panning
    if (isEditMode()
        && typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.handleCanvasMouseUp === "function"
        && typeof window.hopperEdit.isDragging === "function"
        && window.hopperEdit.isDragging()) {
        window.hopperEdit.handleCanvasMouseUp(e);
    }
    panState = {
        startClientX: e.clientX,
        startClientY: e.clientY,
        originOffsetX: offset.x,
        originOffsetY: offset.y
    };
    if (canvas) {
        canvas.style.cursor = "grabbing";
    }
}

/** Coalesce pan redraws to one paint per animation frame. */
let _panRedrawRaf = 0;

/**
 * While panning: update offset and redraw page + component outlines.
 * @returns {boolean} true if a pan is active
 */
function updatePagePan(e) {
    if (!panState) {
        return false;
    }
    if (e && typeof e.preventDefault === "function") {
        e.preventDefault();
    }
    let sc = (typeof scale === "number" && scale > 0) ? scale : 1;
    let dx = e.clientX - panState.startClientX;
    let dy = e.clientY - panState.startClientY;
    // Grab-style: content follows the pointer
    offset.x = panState.originOffsetX - dx / sc;
    offset.y = panState.originOffsetY - dy / sc;
    if (offset.x < 0) {
        offset.x = 0;
    }
    if (offset.y < 0) {
        offset.y = 0;
    }
    // Pan changes the base layer (page offset). Coalesce to 1 rebuild/frame.
    if (!_panRedrawRaf) {
        _panRedrawRaf = requestAnimationFrame(function () {
            _panRedrawRaf = 0;
            invalidatePageBaseCache();
            if (typeof drawSvg === "function") {
                drawSvg();
            }
        });
    }
    return true;
}

/**
 * End pan and do a final page-view redraw.
 */
function endPagePan(e) {
    if (!panState) {
        return;
    }
    // Apply last position if the event still has coordinates
    if (e && typeof e.clientX === "number") {
        updatePagePan(e);
    }
    panState = null;
    if (canvas) {
        canvas.style.cursor = "";
    }
    if (typeof drawSvg === "function") {
        drawSvg();
    }
}

/** @returns {boolean} true while the page view is being panned */
function isPagePanning() {
    return !!panState;
}

/**
 * Build parameter list for an interaction action from the click context.
 * Includes valueParameter (cell/slice value) and dimensionParameters mappings.
 *
 * @param {object} action HInteractionAction JSON
 * @param {object|null} ctx DrawnContext JSON
 * @returns {Array<{parameterName:string, parameterValue:string}>}
 */
function collectInteractionActionParameters(action, ctx) {
    let params = [];
    if (!action) {
        return params;
    }
    let cellValue = ctx ? ctx.value : null;
    if (action.valueParameter && cellValue !== null && cellValue !== undefined) {
        params.push({
            parameterName: action.valueParameter,
            parameterValue: String(cellValue)
        });
    }
    let dimVals = (ctx && ctx.dimensionValues) ? ctx.dimensionValues : {};
    let dimMaps = action.dimensionParameters || [];
    for (let i = 0; i < dimMaps.length; i++) {
        let m = dimMaps[i];
        if (!m) {
            continue;
        }
        let col = m.dimensionColumn || m.fieldName || "";
        let pn = m.parameterName || "";
        if (!col || !pn) {
            continue;
        }
        let pv = dimVals[col];
        if (pv === null || pv === undefined) {
            continue;
        }
        params.push({
            parameterName: pn,
            parameterValue: String(pv)
        });
    }
    return params;
}

/**
 * Open the presentation with the given name.
 *
 * @param {string} presentationName
 * @param {string|Array|{parameterName,parameterValue}|null} [parameterNameOrList]
 *        Either a single parameter name (legacy, with parameterValue), an array of
 *        {parameterName, parameterValue}, or null.
 * @param {string|null} [parameterValue] legacy single-value form
 */
function openPresentation(presentationName,
                          parameterNameOrList,
                          parameterValue
) {
    let postData = {};
    postData.presentationName = presentationName;
    postData.parameters = [];
    postData.colorMode = currentColorMode();
    postData.reload = true;
    if (Array.isArray(parameterNameOrList)) {
        for (let i = 0; i < parameterNameOrList.length; i++) {
            let p = parameterNameOrList[i];
            if (p && p.parameterName != null && p.parameterValue != null
                && String(p.parameterName) !== "") {
                postData.parameters.push({
                    parameterName: p.parameterName,
                    parameterValue: p.parameterValue
                });
            }
        }
    } else if (parameterNameOrList && typeof parameterNameOrList === "object"
        && parameterNameOrList.parameterName != null) {
        postData.parameters.push({
            parameterName: parameterNameOrList.parameterName,
            parameterValue: parameterNameOrList.parameterValue
        });
    } else if (parameterNameOrList !== null && parameterValue !== null
        && parameterNameOrList !== undefined && parameterValue !== undefined
        && String(parameterNameOrList) !== "") {
        postData.parameters.push({
            "parameterName": parameterNameOrList,
            "parameterValue": parameterValue
        });
    }
    let stringData = JSON.stringify(postData);
    console.log("Posting presentation postData: " + stringData);

    $.ajax({
        type: "POST",
        url: API_BASE + "render/presentation/",
        data: stringData,
        dataType: "text", // Returning ID
        contentType: "application/json; charset=utf-8",
        success: (newRenderId) => {
            let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
            // Name-based view URLs rebuild with empty parameters and would drop the values we
            // just posted. When parameters are present, open the session render by id.
            if (postData.parameters && postData.parameters.length && newRenderId) {
                window.open(
                    API_BASE + "render/page/" + encodeURIComponent(newRenderId)
                        + "/HTML/0/?colorMode=" + encodeURIComponent(cm),
                    "_self");
            } else if (typeof presentationName !== "undefined" && presentationName) {
                // Bookmarkable / restart-safe path when no interaction params
                window.open(viewPresentationUrl(presentationName, 0, cm), "_self");
            } else if (newRenderId) {
                window.open(
                    API_BASE + "render/page/" + encodeURIComponent(newRenderId)
                        + "/HTML/0/?colorMode=" + encodeURIComponent(cm),
                    "_self");
            }
        },
        error: function (request, status, error) {
            alert("Error rendering presentation, status: " + status + " : " +
                request.responseText + ", error: " + error);
        }
    });
}

/**
 * In-place page switch: reuse the existing renderId and only fetch the page bitmap +
 * (edit) geometries. Avoids full HTML navigation and server re-layout.
 * @returns {boolean} true if soft switch was applied
 */
function softSwitchRenderPage(page0) {
    if (typeof renderId === "undefined" || !renderId) {
        return false;
    }
    // Admin metadata host has no canvas — leave full navigation paths alone
    if (!document.getElementById("svgCanvas")) {
        return false;
    }
    let max = totalPageCount();
    let target = parseInt(page0, 10);
    if (isNaN(target) || target < 0 || target >= max) {
        return false;
    }
    if (target === currentPageIndex0()) {
        return true;
    }

    renderPageNumber0 = String(target);
    renderPageNumber = String(target + 1);

    // Keep address bar in sync without reloading the shell
    try {
        let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
        let url;
        if (isEditMode() && typeof presentationName !== "undefined" && presentationName) {
            url = API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/page/" + target + "/?reload=false&colorMode=" + encodeURIComponent(cm);
        } else if (typeof presentationName !== "undefined" && presentationName) {
            url = viewPresentationUrl(presentationName, target, cm);
        } else {
            url = API_BASE + "render/page/" + encodeURIComponent(renderId) + "/HTML/" + target
                + "/?colorMode=" + encodeURIComponent(cm);
        }
        if (history.replaceState) {
            history.replaceState({page: target, renderId: renderId}, "", url);
        }
    } catch (e) { /* ignore */ }

    try {
        if (document.title) {
            let base = (typeof presentationName !== "undefined" && presentationName)
                ? presentationName : "Presentation";
            let mode = isEditMode() ? "edit" : "view";
            document.title = base + " (" + mode + ") page "
                + (target + 1) + "/" + max;
        }
    } catch (e) { /* ignore */ }

    // Selection/geometries from the previous sheet are not valid on the new one
    if (typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.clearSelection === "function") {
        try {
            window.hopperEdit.clearSelection();
        } catch (e) { /* ignore */ }
    }
    if (typeof invalidatePageBaseCache === "function") {
        invalidatePageBaseCache();
    }
    // Fetch only the page image for this renderId + page (no doLayout)
    if (typeof loadDrawSvgPage === "function") {
        loadDrawSvgPage(null, null);
    }
    // Edit mode: hopper-edit wraps loadDrawSvgPage to reload geometries; also refresh lists
    if (typeof window.hopperEdit !== "undefined") {
        if (typeof window.hopperEdit.reloadGeometries === "function") {
            // Explicit call in case the paint path is still in flight
            window.hopperEdit.reloadGeometries();
        }
        if (typeof window.hopperEdit.reloadList === "function"
            && document.getElementById("pageComponentList")) {
            window.hopperEdit.reloadList();
        }
    }
    // Toolbar page label / enablement
    if (typeof scheduleRedraw === "function") {
        scheduleRedraw();
    } else if (typeof loadIcons === "function") {
        loadIcons(true);
    }
    return true;
}

/** Navigate to a 0-based page index (edit vs view). Prefers in-place soft switch. */
function goToPage(page0) {
    let max = totalPageCount();
    let target = parseInt(page0, 10);
    if (isNaN(target) || target < 0 || target >= max) {
        return;
    }
    if (target === currentPageIndex0()) {
        return;
    }
    if (softSwitchRenderPage(target)) {
        return;
    }
    // Fallback: full HTML navigation (e.g. missing renderId)
    let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
    if (isEditMode()) {
        window.open(
            API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/page/" + target + "/?reload=false&colorMode=" + encodeURIComponent(cm),
            "_self"
        );
        return;
    }
    if (typeof presentationName !== "undefined" && presentationName) {
        window.open(viewPresentationUrl(presentationName, target, cm), "_self");
    } else {
        window.open(
            API_BASE + "render/page/" + renderId + "/HTML/" + target
                + "/?colorMode=" + encodeURIComponent(cm),
            "_self"
        );
    }
}

function firstPage() {
    goToPage(0);
}

function lastPage() {
    goToPage(totalPageCount() - 1);
}

function nextPage() {
    goToPage(currentPageIndex0() + 1);
}

function previousPage() {
    goToPage(currentPageIndex0() - 1);
}

function correctX(value) {
    return offset.x + value / scale;
}

function correctY(value) {
    return -ICON_SIZE + offset.y + value / scale;
}

/** Tables that need column-name options refreshed when the source connector changes. */
let connectorColumnListTables = [];
/** Select fields bound to connectorColumns that should refresh on dependsOn change. */
let connectorColumnSelects = [];

function ensureFormMetadataCaches() {
    if (typeof renderId !== "undefined" && renderId) {
        componentNames = getComponentNames();
        connectorNames = getPresentationConnectorNames();
    } else {
        // Connector-only edit (no presentation render context)
        if (connectorNames === null) {
            connectorNames = getConnectorNames();
        }
        if (componentNames === null) {
            componentNames = [""];
        }
    }
    if (themeNames === null) {
        themeNames = getThemeNames();
    }
}

function getComponentNames() {
    let names = [];
    if (typeof renderId === "undefined" || !renderId) {
        return [""];
    }
    $.ajax({
            url: API_BASE + "render/info/components/" + renderId + "/" + renderPageNumber0 + "/",
            type: "GET",
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (list) {
                // Empty value means: relative to page (label set by bindSelectSource)
                names.push("");
                for (let i = 0; i < list.length; i++) {
                    names.push(list[i]);
                }
            },
            async: false
        }
    );
    return names;
}

/**
 * Sort connector names case-insensitively. Keeps a leading empty entry (optional/none)
 * first when present.
 */
function sortConnectorNamesCaseInsensitive(names) {
    if (!names || !names.length) {
        return names || [];
    }
    let emptyFirst = names.length && names[0] === "";
    let rest = emptyFirst ? names.slice(1) : names.slice();
    rest.sort(function (a, b) {
        return String(a == null ? "" : a).localeCompare(
            String(b == null ? "" : b),
            undefined,
            {sensitivity: "base"}
        );
    });
    return emptyFirst ? [""].concat(rest) : rest;
}

function getConnectorNames() {
    let names = [];
    $.ajax({
            url: API_BASE + "metadata/list/connector/",
            type: "GET",
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (list) {
                // Empty value means: no connector
                names.push("");
                for (let i = 0; i < list.length; i++) {
                    names.push(list[i]);
                }
            },
            async: false
        }
    );
    return sortConnectorNamesCaseInsensitive(names);
}

/** Presentation-local + shared metadata connector names for the active render. */
function getPresentationConnectorNames() {
    let names = [""];
    if (typeof renderId === "undefined" || !renderId) {
        return getConnectorNames();
    }
    $.ajax({
        url: API_BASE + "render/info/connectors/" + encodeURIComponent(renderId),
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            names = [""];
            for (let i = 0; i < list.length; i++) {
                names.push(list[i]);
            }
        },
        error: function () {
            names = getConnectorNames();
        }
    });
    return sortConnectorNamesCaseInsensitive(names);
}

function getThemeNames() {
    let names = [];
    $.ajax({
            url: API_BASE + "metadata/list/theme/",
            type: "GET",
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (list) {
                // Empty value means: no theme
                names.push("");
                for (let i = 0; i < list.length; i++) {
                    names.push(list[i]);
                }
            },
            async: false
        }
    );
    return names;
}

function getMetadataNames(metadataKey) {
    let names = [""];
    if (!metadataKey) {
        return names;
    }
    $.ajax({
        url: API_BASE + "metadata/list/" + encodeURIComponent(metadataKey) + "/",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            names = [""];
            for (let i = 0; i < list.length; i++) {
                names.push(list[i]);
            }
        }
    });
    return names;
}

function describeConnectorOutput(connectorName) {
    if (!connectorName) {
        return [];
    }
    let request = {
        renderId: typeof renderId !== "undefined" ? renderId : null,
        connectorName: connectorName
    };
    let rowMeta = [];
    $.ajax({
            url: API_BASE + "render/connector/describe/",
            type: "POST",
            data: JSON.stringify(request),
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (result) {
                rowMeta = result || [];
            },
            error: function (xhr) {
                // Non-fatal: empty column list; log for debugging
                console.warn("describeConnectorOutput failed for", connectorName, xhr && xhr.responseText);
                rowMeta = [];
            },
            async: false
        }
    );
    return rowMeta;
}

// ---------------------------------------------------------------------------
// Component form: input connector preview (sample rows) + field layout
// ---------------------------------------------------------------------------

/** @type {XMLHttpRequest|null} */
let sourceConnectorInspectXhr = null;
/** @type {string|null} currently shown mode: "data" | "layout" */
let sourceConnectorInspectMode = null;

function getSelectedSourceConnectorName() {
    let el = document.getElementById("sourceConnectorName");
    if (!el) {
        return "";
    }
    return (el.value || "").trim();
}

function closeSourceConnectorInspect() {
    if (sourceConnectorInspectXhr && typeof sourceConnectorInspectXhr.abort === "function") {
        try {
            sourceConnectorInspectXhr.abort();
        } catch (e) { /* ignore */ }
    }
    sourceConnectorInspectXhr = null;
    sourceConnectorInspectMode = null;
    let panel = document.getElementById("sourceConnectorInspect");
    if (panel) {
        panel.hidden = true;
    }
    let body = document.getElementById("sourceConnectorInspectBody");
    if (body) {
        body.innerHTML = "";
    }
    let title = document.getElementById("sourceConnectorInspectTitle");
    if (title) {
        title.textContent = "";
    }
}

/**
 * Toggle sample-row preview for the selected input connector.
 */
function previewSourceConnectorData() {
    openSourceConnectorInspect("data");
}

/**
 * Toggle field layout (column names / types) for the selected input connector.
 */
function previewSourceConnectorLayout() {
    openSourceConnectorInspect("layout");
}

/**
 * Show or refresh the inline inspect panel under the input connector row.
 * @param {"data"|"layout"} mode
 */
function openSourceConnectorInspect(mode) {
    let name = getSelectedSourceConnectorName();
    if (!name) {
        alert("Select an input connector first.");
        return;
    }
    let panel = document.getElementById("sourceConnectorInspect");
    let body = document.getElementById("sourceConnectorInspectBody");
    let title = document.getElementById("sourceConnectorInspectTitle");
    if (!panel || !body) {
        console.warn("sourceConnectorInspect panel missing from form HTML");
        return;
    }
    // Same mode + already open → close (toggle)
    if (!panel.hidden && sourceConnectorInspectMode === mode) {
        closeSourceConnectorInspect();
        return;
    }
    sourceConnectorInspectMode = mode;
    panel.hidden = false;
    if (title) {
        title.textContent = (mode === "layout" ? "Field layout" : "Sample data")
            + " — " + name;
    }
    body.innerHTML = '<p class="source-connector-inspect-loading">Loading…</p>';

    if (sourceConnectorInspectXhr && typeof sourceConnectorInspectXhr.abort === "function") {
        try {
            sourceConnectorInspectXhr.abort();
        } catch (e) { /* ignore */ }
    }

    // Layout-only: describe endpoint is enough (schema, no rows)
    if (mode === "layout") {
        sourceConnectorInspectXhr = $.ajax({
            url: API_BASE + "render/connector/describe/",
            type: "POST",
            data: JSON.stringify({
                renderId: typeof renderId !== "undefined" ? renderId : null,
                connectorName: name
            }),
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function (rowMeta) {
                body.innerHTML = typeof buildConnectorLayoutTableHtml === "function"
                    ? buildConnectorLayoutTableHtml(rowMeta || [])
                    : "<p>No layout helper available</p>";
                if (!rowMeta || !rowMeta.length) {
                    body.innerHTML = '<p class="source-connector-inspect-empty">'
                        + "No fields described for this connector.</p>";
                }
            },
            error: function (xhr, status) {
                if (status === "abort") {
                    return;
                }
                body.innerHTML = '<p class="source-connector-inspect-error">Could not load field layout'
                    + (xhr && xhr.responseText ? ": " + escapeHtmlText(String(xhr.responseText).slice(0, 200)) : "")
                    + "</p>";
            }
        });
        return;
    }

    // Sample data: load connector JSON then preview (output rows = connector output)
    sourceConnectorInspectXhr = $.ajax({
        url: API_BASE + "metadata/connector-json/" + encodeURIComponent(name),
        type: "GET",
        dataType: "json",
        success: function (data) {
            if (!data) {
                body.innerHTML = '<p class="source-connector-inspect-error">Connector not found.</p>';
                return;
            }
            let reqBody = {
                hopperConnectorJson: JSON.stringify(data),
                maxRows: 20
            };
            if (typeof renderId !== "undefined" && renderId) {
                reqBody.renderId = renderId;
            }
            sourceConnectorInspectXhr = $.ajax({
                url: API_BASE + "edit/connector/preview/",
                type: "POST",
                data: JSON.stringify(reqBody),
                contentType: "application/json; charset=utf-8",
                dataType: "json",
                success: function (result) {
                    if (result && result.output) {
                        let side = result.output;
                        let rowMeta = side.rowMeta || [];
                        let rows = side.rows || [];
                        if (typeof buildConnectorSampleTableHtml === "function") {
                            body.innerHTML = buildConnectorSampleTableHtml(rowMeta, rows);
                        } else {
                            body.innerHTML = "<pre>" + escapeHtmlText(JSON.stringify(rows, null, 2)) + "</pre>";
                        }
                        if ((!rows || !rows.length) && (!rowMeta || !rowMeta.length)) {
                            body.innerHTML = '<p class="source-connector-inspect-empty">No sample rows returned.</p>';
                        }
                    } else if (result && result.error) {
                        body.innerHTML = '<p class="source-connector-inspect-error">'
                            + escapeHtmlText(result.error.summary || "Preview failed")
                            + "</p>";
                    } else {
                        body.innerHTML = '<p class="source-connector-inspect-empty">No sample data.</p>';
                    }
                },
                error: function (xhr, status) {
                    if (status === "abort") {
                        return;
                    }
                    body.innerHTML = '<p class="source-connector-inspect-error">Preview request failed.</p>';
                }
            });
        },
        error: function (xhr, status) {
            if (status === "abort") {
                return;
            }
            body.innerHTML = '<p class="source-connector-inspect-error">Could not load connector \''
                + escapeHtmlText(name) + "\'.</p>";
        }
    });
}

function getConnectorColumnNames(connectorName) {
    let connectorColumnNames = [];
    if (connectorName !== null && connectorName !== undefined && connectorName !== "") {
        let rowMeta = describeConnectorOutput(connectorName);
        for (let i = 0; i < rowMeta.length; i++) {
            let v = rowMeta[i];
            if (v && v['name']) {
                connectorColumnNames.push(v['name']);
            }
        }
    }
    return connectorColumnNames;
}

/**
 * Describe output fields of an unsaved connector JSON (e.g. partial chain for the builder).
 * @param {object|string} hopperConnectorJson full Hop connector object or JSON string
 * @returns {Array.<string>} field names
 */
function describeInlineConnectorColumnNames(hopperConnectorJson) {
    if (!hopperConnectorJson) {
        return [];
    }
    let body = {
        hopperConnectorJson: typeof hopperConnectorJson === "string"
            ? hopperConnectorJson
            : JSON.stringify(hopperConnectorJson),
        renderId: typeof renderId !== "undefined" ? renderId : null
    };
    let names = [];
    $.ajax({
        url: API_BASE + "edit/connector/describe-inline/",
        type: "POST",
        data: JSON.stringify(body),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        async: false,
        success: function (result) {
            let rowMeta = result || [];
            for (let i = 0; i < rowMeta.length; i++) {
                if (rowMeta[i] && rowMeta[i].name) {
                    names.push(rowMeta[i].name);
                }
            }
        },
        error: function (xhr) {
            console.warn("describeInlineConnectorColumnNames failed", xhr && xhr.responseText);
            names = [];
        }
    });
    return names;
}

/** Empty option labels for relative-layout component selects (and generic combos). */
const EMPTY_OPTION_NONE = "(none)";
const EMPTY_OPTION_RELATIVE_TO_PAGE = "(relative to page)";
const EMPTY_OPTION_RELATIVE_TO_COMPOSITE = "(relative to composite)";

/**
 * Fill a &lt;select&gt; from a dynamic source (connectors, themes, components, columns, metadata).
 * Layout "components" source uses empty = relative to page (not a generic "(none)").
 */
function bindSelectSource(selectId, source, options) {
    options = options || {};
    let values = resolveSelectSourceValues(source, options);
    let emptyLabel = options.emptyLabel;
    if (emptyLabel == null && source === "components") {
        emptyLabel = EMPTY_OPTION_RELATIVE_TO_PAGE;
    }
    setSelectOptions(selectId, values, emptyLabel);
    if (source === "connectorColumns") {
        connectorColumnSelects.push({
            selectId: selectId,
            dependsOn: options.dependsOn || "sourceConnectorName"
        });
    }
}

function resolveSelectSourceValues(source, options) {
    options = options || {};
    ensureFormMetadataCaches();
    switch (source) {
        case "connectors":
            return connectorNames || getPresentationConnectorNames();
        case "themes":
            return themeNames || getThemeNames();
        case "components":
            return componentNames || getComponentNames();
        case "connectorColumns": {
            let dep = options.dependsOn || "sourceConnectorName";
            let el = document.getElementById(dep);
            let cname = el ? el.value : "";
            return getConnectorColumnNames(cname);
        }
        case "metadata":
            return getMetadataNames(options.metadataKey);
        case "none":
        default:
            return options.staticValues || [];
    }
}

function registerConnectorColumnListTable(tableId, dependsOn, itemKind) {
    connectorColumnListTables.push({
        tableId: tableId,
        dependsOn: dependsOn || "sourceConnectorName",
        itemKind: itemKind || "column"
    });
}

function wireConnectorDependentCombos() {
    // When source connector (or other dependsOn field) changes, refresh column options
    let deps = {};
    for (let i = 0; i < connectorColumnSelects.length; i++) {
        deps[connectorColumnSelects[i].dependsOn] = true;
    }
    for (let i = 0; i < connectorColumnListTables.length; i++) {
        deps[connectorColumnListTables[i].dependsOn] = true;
    }
    Object.keys(deps).forEach(function (depId) {
        let el = document.getElementById(depId);
        if (!el || el._hopperComboWired) {
            return;
        }
        el._hopperComboWired = true;
        el.addEventListener("change", function () {
            refreshConnectorColumnDependents(depId);
        });
    });
}

/**
 * True if value is present in options (string-coerced compare).
 */
function optionsIncludeValue(options, value) {
    if (value === null || value === undefined) {
        return false;
    }
    if (!options || !options.length) {
        return false;
    }
    let s = String(value);
    for (let i = 0; i < options.length; i++) {
        if (String(options[i]) === s) {
            return true;
        }
    }
    return false;
}

/**
 * Copy of options that always includes the current stored value.
 * Missing source fields keep their metadata (e.g. category when connector is down).
 * @param {Array} options live option list
 * @param {*} value current metadata value
 * @returns {{options: Array, missing: boolean}}
 */
function mergeValueIntoOptions(options, value) {
    let list = Array.isArray(options) ? options.slice() : [];
    if (value === null || value === undefined || value === "") {
        return { options: list, missing: false };
    }
    if (optionsIncludeValue(list, value)) {
        return { options: list, missing: false };
    }
    // Preserve metadata: keep the configured name even when not in live source
    list.unshift(value);
    return { options: list, missing: true };
}

/**
 * Display label for a select option; mark values not currently in the source.
 * @param {*} value option value
 * @param {boolean} missing true when value is not in the live source list
 * @param {string} [emptyLabel] label for empty value (default {@link EMPTY_OPTION_NONE})
 */
function optionDisplayText(value, missing, emptyLabel) {
    if (value === "" || value === null || value === undefined) {
        return emptyLabel != null && emptyLabel !== "" ? emptyLabel : EMPTY_OPTION_NONE;
    }
    return missing ? (String(value) + " (not in source)") : String(value);
}

/**
 * Rebuild a &lt;select&gt;'s options from a live list while keeping the current value.
 * Never silently switches to the first live column when the old name is missing.
 * @param {HTMLSelectElement} select
 * @param {Array} liveValues
 * @param {*} preferredValue
 * @param {string} [emptyLabel] optional empty-option label; falls back to data-empty-label
 */
function rebuildSelectOptions(select, liveValues, preferredValue, emptyLabel) {
    if (!select) {
        return;
    }
    if (emptyLabel == null || emptyLabel === "") {
        emptyLabel = select.getAttribute("data-empty-label") || null;
    }
    let prev = preferredValue;
    if (prev === undefined || prev === null) {
        prev = select.value;
    }
    // Also keep data-preserve-value if the control is empty (e.g. failed describe left 0 options)
    if ((prev === undefined || prev === null || prev === "")
        && select.getAttribute("data-preserve-value")) {
        prev = select.getAttribute("data-preserve-value");
    }
    let merged = mergeValueIntoOptions(liveValues || [], prev);
    while (select.options.length > 0) {
        select.remove(0);
    }
    for (let i = 0; i < merged.options.length; i++) {
        let v = merged.options[i];
        let isMissing = merged.missing && String(v) === String(prev);
        addOptionToSelect(select, v, optionDisplayText(v, isMissing, emptyLabel));
        if (isMissing && select.options.length) {
            select.options[select.options.length - 1].setAttribute("data-missing-source", "true");
        }
    }
    if (prev !== undefined && prev !== null && prev !== "") {
        select.value = String(prev);
        select.setAttribute("data-preserve-value", String(prev));
        // If the browser still refused (should not with merge), force-add once more
        if (select.value !== String(prev)) {
            addOptionToSelect(select, prev, optionDisplayText(prev, true, emptyLabel));
            select.value = String(prev);
        }
    } else if (prev === "" || prev === null || prev === undefined) {
        // Keep explicit empty selection (relative to page/composite)
        select.value = "";
    }
}

function refreshConnectorColumnDependents(dependsOnId) {
    let depEl = document.getElementById(dependsOnId);
    let cname = depEl ? depEl.value : "";
    let cols = getConnectorColumnNames(cname);

    for (let i = 0; i < connectorColumnSelects.length; i++) {
        let item = connectorColumnSelects[i];
        if (item.dependsOn === dependsOnId) {
            let sel = document.getElementById(item.selectId);
            if (sel) {
                rebuildSelectOptions(sel, cols, sel.value || sel.getAttribute("data-preserve-value"));
            }
        }
    }

    // Refresh column-name selects inside registered list tables
    for (let t = 0; t < connectorColumnListTables.length; t++) {
        let reg = connectorColumnListTables[t];
        if (reg.dependsOn !== dependsOnId) {
            continue;
        }
        let table = document.getElementById(reg.tableId);
        if (!table) {
            continue;
        }
        for (let r = 1; r < table.rows.length; r++) {
            let cell = table.rows[r].cells[0];
            if (!cell) {
                continue;
            }
            let select = cell.querySelector("select");
            if (!select) {
                continue;
            }
            rebuildSelectOptions(
                select,
                cols,
                select.value || select.getAttribute("data-preserve-value")
            );
        }
    }
}

/**
 * Fill select options. Preserves the currently selected value when it is not in the new list
 * (metadata must not be replaced by the first live column after a connector/source glitch).
 * @param {string} selectId
 * @param {Array} values
 * @param {string} [emptyLabel] when set, stored as data-empty-label and used for "" options
 */
function setSelectOptions(selectId, values, emptyLabel) {
    try {
        let list = document.getElementById(selectId);
        if (list === null || list === undefined) {
            return;
        }
        if (emptyLabel != null && emptyLabel !== "") {
            list.setAttribute("data-empty-label", emptyLabel);
        }
        rebuildSelectOptions(
            list,
            values || [],
            list.value || list.getAttribute("data-preserve-value"),
            emptyLabel
        );
    } catch (e) {
        throw "Error adding select options for select ID '" + selectId + "' and values: " + JSON.stringify(values) + " : " + e;
    }
}

function addOptionToSelect(list, value, displayText) {
    let option = document.createElement("option");
    option.value = value === null || value === undefined ? "" : value;
    let emptyLabel = list && list.getAttribute ? list.getAttribute("data-empty-label") : null;
    option.text = displayText != null ? displayText : optionDisplayText(value, false, emptyLabel);
    list.appendChild(option);
}

function toHex(v) {
    let n = parseInt(v, 10);
    if (isNaN(n)) {
        n = 0;
    }
    n = Math.max(0, Math.min(255, n));
    let h = n.toString(16);
    return h.length === 1 ? "0" + h : h;
}

/**
 * Convert RGB channels to a #rrggbb value accepted by {@code <input type="color">}.
 * Accepts either separate r,g,b args or a single {r,g,b} / {R,G,B} object.
 */
function rgbToHex(r, g, b) {
    if (r != null && typeof r === "object" && g === undefined && b === undefined) {
        let c = r;
        r = c.r != null ? c.r : c.R;
        g = c.g != null ? c.g : c.G;
        b = c.b != null ? c.b : c.B;
    }
    return "#" + toHex(r) + toHex(g) + toHex(b);
}

function hexToRgb(hex) {
    if (hex == null) {
        return null;
    }
    let s = String(hex).trim();
    // Expand #rgb short form
    let short = /^#?([a-f\d])([a-f\d])([a-f\d])$/i.exec(s);
    if (short) {
        s = "#" + short[1] + short[1] + short[2] + short[2] + short[3] + short[3];
    }
    let result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(s);
    return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
    } : null;
}

/**
 *
 * @param id the id of the selection widget to create.
 * @param value The value to set on the selection.
 * @param optionValues the options to add to the selection.
 * @param flags optional:
 *   - defaultEmptyToFirst: if true and value is empty, pick first option (enums only)
 *   - preserveMissing: if true (default), keep a non-empty value even when not in options
 * @returns HTML for a &lt;select&gt; widget
 */
function createSelection(id, value, optionValues, flags) {
    flags = flags || {};
    // Non-empty metadata values are always kept, even when the live source list is empty
    // or missing that name (connector offline / rename / describe failure).
    let preserveMissing = flags.preserveMissing !== false;
    let defaultEmptyToFirst = flags.defaultEmptyToFirst === true;

    let options = Array.isArray(optionValues) ? optionValues.slice() : [];
    let missing = false;
    if (value !== null && value !== undefined && value !== "") {
        if (preserveMissing) {
            let merged = mergeValueIntoOptions(options, value);
            options = merged.options;
            missing = merged.missing;
        }
    } else if (defaultEmptyToFirst && options.length) {
        // Closed enums only (alignment, aggregation) — never column names
        value = options[0];
    }

    let preserveAttr = "";
    if (value !== null && value !== undefined && value !== "") {
        preserveAttr = ' data-preserve-value="'
            + String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;") + '"';
    }
    let html = '<select id="' + id + '" name="' + id + '" style="width: 100%"' + preserveAttr + ">";
    let selectedAny = false;
    for (let i = 0; i < options.length; i++) {
        let optionValue = options[i];
        let selected = "";
        if (value !== null && value !== undefined && String(value) === String(optionValue)) {
            selected = ' selected="selected"';
            selectedAny = true;
        }
        let isMissing = missing && String(optionValue) === String(value);
        let label = optionDisplayText(optionValue, isMissing);
        let safeVal = String(optionValue === null || optionValue === undefined ? "" : optionValue)
            .replace(/&/g, "&amp;").replace(/"/g, "&quot;");
        let safeLabel = String(label).replace(/&/g, "&amp;").replace(/</g, "&lt;");
        html += '<option value="' + safeVal + '"' + selected
            + (isMissing ? ' data-missing-source="true"' : "")
            + ">" + safeLabel + "</option>";
    }
    // Stored value with empty live options: still show it so Apply does not wipe metadata
    if (!selectedAny && value !== null && value !== undefined && value !== "") {
        let safeVal = String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;");
        let safeLabel = optionDisplayText(value, true).replace(/&/g, "&amp;").replace(/</g, "&lt;");
        html += '<option value="' + safeVal + '" selected="selected" data-missing-source="true">'
            + safeLabel + "</option>";
        selectedAny = true;
    }
    html += "</select>";
    return html;
}

/**
 * @param id element id
 * @param value input value
 * @param style optional CSS for the input (e.g. "width: 4em")
 */
function createText(id, value, style) {
    let v = (value === null || value === undefined) ? "" : value;
    let styleAttr = style ? ' style="' + style + '"' : "";
    return '<input type="text" id="' + id + '" value="'
        + String(v).replace(/"/g, "&quot;") + '"' + styleAttr + ">";
}

/**
 * Display value for a table/fact column width field.
 * {@code 0} / empty means auto-detect from content — show blank in the panel.
 */
function formatColumnWidthInputValue(width) {
    if (width === null || width === undefined || width === "") {
        return "";
    }
    let n = Number(width);
    if (!isNaN(n) && n === 0) {
        return "";
    }
    return width;
}

/**
 * Parse width from the column properties panel. Blank / invalid / non-positive → 0 (auto).
 */
function parseColumnWidthInputValue(width) {
    if (width === null || width === undefined) {
        return 0;
    }
    let s = String(width).trim();
    if (s === "") {
        return 0;
    }
    let n = parseInt(s, 10);
    if (isNaN(n) || n <= 0) {
        return 0;
    }
    return n;
}

/**
 * Compact width text field: blank when auto (0), placeholder hints at auto-detect.
 */
function createColumnWidthText(id, width) {
    let v = formatColumnWidthInputValue(width);
    return '<input type="text" id="' + id + '" value="'
        + String(v).replace(/"/g, "&quot;")
        + '" style="width: 4em" placeholder="auto"'
        + ' title="Leave blank for automatic column width; enter a positive pixel width to fix the column">';
}

function createCheckBox(id, value) {
    let checked = (value === true || value === "true") ? " checked" : "";
    return '<input type="checkbox" id="' + id + '"' + checked + '>';
}

function createButton(id, label) {
    return '<button type="button" id="' + id + '">' + label + '</button>';
}


function createIcon(id, iconFile, label) {
    let bare = String(iconFile || "").replace(/^.*\//, "");
    return '<img src="' + resolveUiIcon(bare) + '" data-ui-icon="' + bare + '" id="' + id
        + '" alt="' + label + '" style="width: 16px;height: 16px">';
}

/**
 * Small icon button used for list row actions (up/down) and toolbars.
 * @param id element id
 * @param iconName file name under static/images/ (e.g. "arrow-up.svg")
 * @param label accessible title/alt text
 */
function createIconButton(id, iconName, label) {
    let bare = String(iconName || "").replace(/^.*\//, "");
    return '<button type="button" class="list-row-btn" id="' + id + '" title="' + label + '">'
        + '<img src="' + resolveUiIcon(bare) + '" data-ui-icon="' + bare + '" alt="' + label
        + '" width="16" height="16">'
        + '</button>';
}

/**
 * Inline &lt;img&gt; for monochrome chrome icons (supports theme toggle via data-ui-icon).
 * @param {string} iconName e.g. "delete.svg"
 * @param {string} [alt]
 * @param {number} [size]
 */
function uiIconImgTag(iconName, alt, size) {
    let bare = String(iconName || "").replace(/^.*\//, "");
    let s = size || 16;
    let a = alt != null ? alt : "";
    return '<img src="' + resolveUiIcon(bare) + '" data-ui-icon="' + bare + '" alt="' + a
        + '" width="' + s + '" height="' + s + '">';
}

// ---------------------------------------------------------------------------
// List field tables: header Add/Delete, row Up/Down reorder
// ---------------------------------------------------------------------------

function listFieldKind(table) {
    return table.getAttribute("data-list-kind") || "column";
}

function listFieldColumnPrefix(table) {
    return table.getAttribute("data-column-prefix") || table.id;
}

function listFieldConnectorColumnNames(table) {
    // Chain builder / nested forms stash upstream columns on the table
    if (table && table.getAttribute) {
        let raw = table.getAttribute("data-column-names");
        if (raw) {
            try {
                let parsed = JSON.parse(raw);
                if (Array.isArray(parsed) && parsed.length) {
                    return parsed;
                }
            } catch (e) { /* ignore */ }
        }
    }
    // Prefer live source-connector select when present (component editors)
    let sourceEl = document.getElementById("sourceConnectorName");
    let sourceName = sourceEl ? sourceEl.value : null;
    // Visual chain top-bar source when form field is hidden
    if (!sourceName) {
        let chainSrc = document.getElementById("chainEditorSource");
        if (chainSrc) {
            sourceName = chainSrc.value;
        }
    }
    if (typeof getConnectorColumnNames === "function") {
        return getConnectorColumnNames(sourceName);
    }
    return [""];
}

/**
 * Header Add: append a new empty row for the list kind of this table.
 */
function listFieldAdd(tableId) {
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    // insert at end: create*Row uses insertRow(i+1), so i = rows.length - 1 appends
    let i = Math.max(0, table.rows.length - 1);
    let kind = listFieldKind(table);
    let prefix = listFieldColumnPrefix(table);
    let colNames = listFieldConnectorColumnNames(table);

    if (kind === "fact") {
        createFactsRow(table, {
            "columnName": "",
            "headerValue": "",
            "width": 0,
            "horizontalAlignment": "LEFT",
            "verticalAlignment": "MIDDLE",
            "formatMask": "",
            "horizontalAggregation": true,
            "verticalAggregation": true,
            "aggregationMethod": "SUM"
        }, i, prefix, colNames);
    } else if (kind === "string") {
        createStringListRow(table, "", i);
    } else if (kind === "sort") {
        createSortMethodRow(table, {"type": "NATIVE_VALUE", "ascending": true}, i);
    } else if (kind === "filter") {
        createFilterValueRow(table, {"fieldName": "", "filterValue": ""}, i, colNames);
    } else if (kind === "groupKey") {
        createGroupKeyMappingRow(table, {"groupColumn": "", "connectorColumn": ""}, i);
    } else if (kind === "jsonField") {
        createJsonFieldRow(table, {
            "tag": "",
            "name": "",
            "type": "String",
            "formatMask": "",
            "length": "",
            "precision": ""
        }, i);
    } else if (kind === "csvField") {
        createCsvFieldRow(table, {
            "name": "",
            "type": "String",
            "formatMask": "",
            "length": "",
            "precision": ""
        }, i);
    } else if (kind === "connector" || kind === "bean") {
        createJsonObjectRow(table, {}, i);
    } else {
        createColumnsRow(table, {
            "columnName": "",
            "headerValue": "",
            "width": 0,
            "horizontalAlignment": "LEFT",
            "verticalAlignment": "MIDDLE",
            "formatMask": ""
        }, i, prefix, colNames);
    }
}

function listRowMoveUp(table, row) {
    let idx = row.rowIndex;
    if (idx <= 1) {
        return; // already first data row (row 0 is header)
    }
    let prev = table.rows[idx - 1];
    row.parentNode.insertBefore(row, prev);
}

function listRowMoveDown(table, row) {
    let idx = row.rowIndex;
    if (idx >= table.rows.length - 1) {
        return; // already last
    }
    let next = table.rows[idx + 1];
    // Move next before row => swap
    row.parentNode.insertBefore(next, row);
}

/**
 * Append Up, Down, and Delete icon buttons as the last three cells of a list data row.
 * @returns next cell index after the three cells
 */
function appendListReorderCells(row, table, startIndex) {
    let upId = row.id + "-up";
    let downId = row.id + "-down";
    let delId = row.id + "-delete";
    row.insertCell(startIndex).innerHTML = createIconButton(upId, "arrow-up.svg", "Move up");
    document.getElementById(upId).onclick = function (e) {
        e.stopPropagation();
        listRowMoveUp(table, row);
    };
    row.insertCell(startIndex + 1).innerHTML = createIconButton(downId, "arrow-down.svg", "Move down");
    document.getElementById(downId).onclick = function (e) {
        e.stopPropagation();
        listRowMoveDown(table, row);
    };
    row.insertCell(startIndex + 2).innerHTML = createIconButton(delId, "delete.svg", "Delete row");
    document.getElementById(delId).onclick = function (e) {
        e.stopPropagation();
        columnDelete(table, row);
    };
    return startIndex + 3;
}

function openPage(newRenderId) {
    let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
    if (isEditMode()) {
        // Prefer soft re-render if available (keeps editor shell)
        if (typeof softReloadEditor === "function") {
            softReloadEditor();
            return;
        }
        let page = typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0;
        window.open(
            API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/page/" + page + "/?reload=true&colorMode=" + encodeURIComponent(cm),
            "_self"
        );
        return;
    }
    // View mode: name-based shell (rebuild-safe); fall back to UUID if name unknown
    if (typeof presentationName !== "undefined" && presentationName) {
        let p0 = typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0;
        window.open(viewPresentationUrl(presentationName, p0, cm), "_self");
    } else {
        window.open(
            API_BASE + "render/page/" + newRenderId + "/HTML/" + renderPageNumber0
                + "/?colorMode=" + encodeURIComponent(cm),
            "_self"
        );
    }
}

/**
 * Clear server-side layout cache for this presentation, then soft-reload (full recompute).
 */
function forceRefreshPresentation() {
    if (typeof presentationName === "undefined" || !presentationName) {
        return;
    }
    beginPresentationBusy();
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/cache/clear/",
        type: "POST",
        dataType: "json",
        success: function () {
            // softReload/reload will begin their own busy; release the clear-cache hold
            endPresentationBusy();
            if (typeof softReloadEditor === "function") {
                softReloadEditor(
                    typeof window.hopperEdit !== "undefined"
                        && window.hopperEdit.getSelectedName
                        ? window.hopperEdit.getSelectedName()
                        : null
                );
            } else if (typeof reloadPresentation === "function") {
                reloadPresentation();
            }
        },
        error: function (xhr, status, error) {
            endPresentationBusy();
            if (typeof showAjaxError === "function") {
                showAjaxError("Refresh failed", xhr, status, error);
            } else {
                alert("Refresh failed: " + (xhr.responseText || status));
            }
        }
    });
}

/**
 * Soft re-render for edit mode: new renderId + SVG + editor list/geometries, no full navigation.
 * Falls back to full editor navigation if the re-render API fails.
 */
function currentColorMode() {
    if (typeof window.HThemeMode !== "undefined" && window.HThemeMode.getResolvedMode) {
        return window.HThemeMode.getResolvedMode();
    }
    return "light";
}

/**
 * Open/editor links now pass {@code colorMode}, so the server renders in the UI mode on first
 * paint. No client soft-reload on load (that caused light→dark flash and a double full layout).
 */
function syncPresentationColorModeOnLoad() {
    _hopperInitialColorSyncDone = true;
}

/**
 * Soft-reload timing: coarse server timings always; full Gantt-ready spans when
 * localStorage hopperDebugTimings=1 or URL ?debugTimings=1.
 */
function wantSoftReloadDebugTimings() {
    try {
        if (window.localStorage && localStorage.getItem("hopperDebugTimings") === "1") {
            return true;
        }
        let q = window.location && window.location.search ? window.location.search : "";
        return /[?&]debugTimings=1(?:&|$)/.test(q);
    } catch (e) {
        return false;
    }
}

function logSoftReloadTimings(serverTimings, clientParts) {
    let parts = clientParts || {};
    if (!serverTimings && parts.xhrMs == null && parts.perceivedMs == null) {
        return;
    }
    let row = {
        layoutMs: serverTimings && serverTimings.layoutMs,
        renderMs: serverTimings && serverTimings.renderMs,
        totalMs: serverTimings && serverTimings.totalMs,
        wallMs: serverTimings && serverTimings.wallMs,
        // Client phases (ms since softReload start unless noted)
        xhrMs: parts.xhrMs,
        svgLoadMs: parts.svgLoadMs,
        geometriesMs: parts.geometriesMs,
        paintMs: parts.paintMs,
        refreshMs: parts.refreshMs,
        perceivedMs: parts.perceivedMs,
        inlineSvg: parts.inlineSvg,
        inlinePng: parts.inlinePng,
        pageSvgChars: parts.pageSvgChars,
        pagePngBytes: serverTimings && serverTimings.pagePngBytes,
        pngMs: serverTimings && serverTimings.pngMs,
        cache: serverTimings && serverTimings.cache
    };
    // Back-compat alias
    row.clientMs = parts.xhrMs;
    console.info("[hopper softReload timings]", row);
    if (serverTimings && Array.isArray(serverTimings.top) && serverTimings.top.length) {
        console.table(
            serverTimings.top.map(function (s) {
                return {
                    ms: s.ms,
                    code: s.code,
                    subject: s.subject || "",
                    description: s.description || ""
                };
            })
        );
    }
    // Gantt input for the refresh timings panel
    if (serverTimings && Array.isArray(serverTimings.spans)) {
        window.__hopperLastRenderSpans = serverTimings.spans;
    }
    if (serverTimings) {
        window.__hopperLastRenderTimings = serverTimings;
    }
    window.__hopperLastSoftReloadClient = parts;

    // Refresh timings Gantt only when the user asked for it (toolbar stopwatch sets
    // hopperTimingsPanel=1) or the panel is already open. Default is hidden so
    // soft-reloads (add component, property save, etc.) do not pop the Gantt.
    if (typeof isEditMode === "function" && isEditMode()
        && typeof showRefreshTimingsPanel === "function") {
        try {
            let want = false;
            try {
                let pref = localStorage.getItem("hopperTimingsPanel");
                if (pref === "1" || pref === "on" || pref === "true") {
                    want = true;
                }
            } catch (e) { /* ignore */ }
            let panel = document.getElementById("refreshTimingsPanel");
            if (panel && !panel.hidden) {
                want = true;
            }
            if (want) {
                showRefreshTimingsPanel({fromSoftReload: true});
            }
        } catch (e) {
            console.warn("timings panel update failed", e);
        }
    }
}

/**
 * Floating panel with summary chips + Gantt SVG of the last refresh pipeline.
 * @param {{fromSoftReload?:boolean}} [opts]
 */
function showRefreshTimingsPanel(opts) {
    if (typeof isEditMode === "function" && !isEditMode()) {
        return;
    }
    if (typeof presentationName === "undefined" || !presentationName) {
        return;
    }
    let panel = ensureRefreshTimingsPanel();
    panel.hidden = false;

    let chips = document.getElementById("refreshTimingsChips");
    let img = document.getElementById("refreshTimingsGanttImg");
    let empty = document.getElementById("refreshTimingsEmpty");
    let meta = document.getElementById("refreshTimingsMeta");

    let serverTimings = window.__hopperLastRenderTimings || null;
    let clientParts = window.__hopperLastSoftReloadClient || {};
    let spans = window.__hopperLastRenderSpans || (serverTimings && serverTimings.spans) || [];

    if (chips) {
        chips.innerHTML = "";
        function addChip(label, val) {
            if (val == null || val === "" || isNaN(val)) {
                return;
            }
            let span = document.createElement("span");
            span.className = "refresh-timings-chip";
            span.textContent = label + ": " + Math.round(val) + " ms";
            chips.appendChild(span);
        }
        if (serverTimings) {
            addChip("Layout", serverTimings.layoutMs);
            addChip("Render", serverTimings.renderMs);
            addChip("Server", serverTimings.totalMs);
            addChip("Wall", serverTimings.wallMs);
        }
        addChip("XHR", clientParts.xhrMs);
        addChip("Perceived", clientParts.perceivedMs);
        if (!chips.childNodes.length) {
            chips.textContent = "No timing totals yet — trigger a soft refresh.";
        }
    }

    if (empty) {
        empty.hidden = false;
        empty.textContent = "Rendering Gantt…";
    }
    if (img) {
        img.classList.remove("is-visible");
    }

    // ~1.3× prior panel Gantt size (was max ~780×420)
    let w = Math.min(1014, Math.max(624, Math.round(((window.innerWidth || 800) - 80) * 1.3)));
    let h = Math.min(546, Math.max(286, Math.round(w * 0.45)));
    // Stash payload for Save & open
    window.__hopperLastTimingsGanttBody = {
        spans: spans,
        client: {
            xhrMs: clientParts.xhrMs,
            pngMs: clientParts.pngMs != null ? clientParts.pngMs
                : (serverTimings && serverTimings.pngMs),
            svgLoadMs: clientParts.svgLoadMs,
            geometriesMs: clientParts.geometriesMs,
            paintMs: clientParts.paintMs,
            refreshMs: clientParts.refreshMs,
            perceivedMs: clientParts.perceivedMs
        },
        width: 1123,
        height: 794
    };
    let body = window.__hopperLastTimingsGanttBody;
    let url = API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
        + "/timings/gantt.svg?width=" + encodeURIComponent(String(w))
        + "&height=" + encodeURIComponent(String(h))
        + "&colorMode=" + encodeURIComponent(
            typeof currentColorMode === "function" ? currentColorMode() : "light")
        + (typeof renderId !== "undefined" && renderId
            ? "&renderId=" + encodeURIComponent(renderId) : "")
        + "&_=" + Date.now();

    // Prefer POST so client phases are included; fall back to GET blob URL if needed
    fetch(url, {
        method: "POST",
        headers: {"Content-Type": "application/json; charset=utf-8"},
        body: JSON.stringify(body),
        credentials: "same-origin"
    }).then(function (res) {
        if (!res.ok) {
            throw new Error("HTTP " + res.status);
        }
        return res.blob();
    }).then(function (blob) {
        let objectUrl = URL.createObjectURL(blob);
        if (img) {
            if (img._objectUrl) {
                try {
                    URL.revokeObjectURL(img._objectUrl);
                } catch (e) { /* ignore */ }
            }
            img._objectUrl = objectUrl;
            img.onload = function () {
                img.classList.add("is-visible");
                if (empty) {
                    empty.hidden = true;
                }
            };
            img.onerror = function () {
                img.classList.remove("is-visible");
                if (empty) {
                    empty.hidden = false;
                    empty.textContent = "Gantt preview failed.";
                }
            };
            img.src = objectUrl;
        }
        if (meta) {
            let n = Array.isArray(spans) ? spans.length : 0;
            meta.textContent = (n ? n + " server span(s)" : "server timings")
                + " · " + w + "×" + h + " px"
                + " · snapshot save uses System / " + presentationName + " - Gantt";
        }
    }).catch(function (err) {
        if (empty) {
            empty.hidden = false;
            empty.textContent = "Could not load timings Gantt: "
                + (err && err.message ? err.message : String(err));
        }
        if (meta) {
            meta.textContent = "";
        }
    });
}

/**
 * Persist timings Gantt as System / "{presentation} - Gantt" and open full-screen view.
 */
function saveAndOpenRefreshTimingsGantt() {
    if (typeof presentationName === "undefined" || !presentationName) {
        alert("No presentation is open");
        return;
    }
    let body = window.__hopperLastTimingsGanttBody || {
        spans: window.__hopperLastRenderSpans || [],
        client: window.__hopperLastSoftReloadClient || {},
        width: 1123,
        height: 794
    };
    let status = document.getElementById("refreshTimingsMeta");
    if (status) {
        status.textContent = "Saving System / " + presentationName + " - Gantt…";
    }
    let saveBtn = document.getElementById("refreshTimingsSave");
    if (saveBtn) {
        saveBtn.disabled = true;
    }
    let url = API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
        + "/timings/save-gantt/?colorMode=" + encodeURIComponent(
            typeof currentColorMode === "function" ? currentColorMode() : "light")
        + (typeof renderId !== "undefined" && renderId
            ? "&renderId=" + encodeURIComponent(renderId) : "");
    fetch(url, {
        method: "POST",
        headers: {"Content-Type": "application/json; charset=utf-8"},
        body: JSON.stringify(body),
        credentials: "same-origin"
    }).then(function (res) {
        if (!res.ok) {
            return res.text().then(function (t) {
                throw new Error(t || ("HTTP " + res.status));
            });
        }
        return res.json();
    }).then(function (data) {
        if (status) {
            status.textContent = "Saved "
                + (data.virtualPath || "System") + " / " + (data.name || "")
                + " (" + (data.taskCount != null ? data.taskCount : "?") + " tasks)";
        }
        let viewUrl = data && data.viewUrl
            ? data.viewUrl
            : (data && data.renderId
                ? (API_BASE + "render/page/" + data.renderId + "/HTML/0/")
                : null);
        if (viewUrl) {
            // Absolute-from-root path from server is fine for window.open
            window.open(viewUrl, "_blank");
        } else {
            alert("Saved, but no view URL was returned");
        }
    }).catch(function (err) {
        let msg = err && err.message ? err.message : String(err);
        if (status) {
            status.textContent = "Save failed: " + msg.slice(0, 160);
        }
        if (typeof showAjaxError === "function") {
            showAjaxError("Save timings Gantt failed", {responseText: msg});
        } else {
            alert("Save timings Gantt failed: " + msg);
        }
    }).finally(function () {
        if (saveBtn) {
            saveBtn.disabled = false;
        }
    });
}

function hideRefreshTimingsPanel() {
    let panel = document.getElementById("refreshTimingsPanel");
    if (panel) {
        panel.hidden = true;
    }
}

function ensureRefreshTimingsPanel() {
    let existing = document.getElementById("refreshTimingsPanel");
    if (existing) {
        return existing;
    }
    let panel = document.createElement("div");
    panel.id = "refreshTimingsPanel";
    panel.className = "refresh-timings-panel";
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-label", "Refresh timings");
    panel.hidden = true;
    panel.innerHTML = ""
        + "<div class=\"refresh-timings-header\">"
        + "  <strong>Refresh timings</strong>"
        + "  <span class=\"refresh-timings-header-actions\">"
        + "    <button type=\"button\" class=\"refresh-timings-btn refresh-timings-btn-primary\" id=\"refreshTimingsSave\""
        + " title=\"Save snapshot under System / {name} - Gantt and open full-screen view\">Save &amp; open</button>"
        + "    <button type=\"button\" class=\"refresh-timings-btn\" id=\"refreshTimingsReload\" title=\"Reload Gantt\">Refresh</button>"
        + "    <button type=\"button\" class=\"refresh-timings-btn\" id=\"refreshTimingsClose\" title=\"Close\">Close</button>"
        + "  </span>"
        + "</div>"
        + "<div id=\"refreshTimingsChips\" class=\"refresh-timings-chips\"></div>"
        + "<div class=\"refresh-timings-frame\">"
        + "  <img id=\"refreshTimingsGanttImg\" class=\"refresh-timings-img\" alt=\"Refresh timings Gantt\">"
        + "  <p id=\"refreshTimingsEmpty\" class=\"refresh-timings-empty\">No data</p>"
        + "</div>"
        + "<p id=\"refreshTimingsMeta\" class=\"refresh-timings-meta\"></p>";
    document.body.appendChild(panel);
    let closeBtn = document.getElementById("refreshTimingsClose");
    if (closeBtn) {
        closeBtn.onclick = function () {
            hideRefreshTimingsPanel();
            try {
                localStorage.setItem("hopperTimingsPanel", "0");
            } catch (e) { /* ignore */ }
        };
    }
    let reloadBtn = document.getElementById("refreshTimingsReload");
    if (reloadBtn) {
        reloadBtn.onclick = function () {
            try {
                localStorage.setItem("hopperTimingsPanel", "1");
            } catch (e) { /* ignore */ }
            showRefreshTimingsPanel({});
        };
    }
    let saveBtn = document.getElementById("refreshTimingsSave");
    if (saveBtn) {
        saveBtn.onclick = function () {
            saveAndOpenRefreshTimingsGantt();
        };
    }
    return panel;
}

function softReloadEditor(keepSelectionName) {
    if (!isEditMode() || typeof presentationName === "undefined") {
        return;
    }
    beginPresentationBusy();
    let colorMode = currentColorMode();
    let debugTimings = wantSoftReloadDebugTimings();
    let t0 = (typeof performance !== "undefined" && performance.now) ? performance.now() : Date.now();
    if (typeof performance !== "undefined" && performance.mark) {
        try {
            performance.mark("hopper-softReload-start");
        } catch (e) { /* ignore */ }
    }
    let page0 = parseInt(renderPageNumber0, 10);
    if (isNaN(page0) || page0 < 0) {
        page0 = 0;
    }
    let url = API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
        + "/render/?colorMode=" + encodeURIComponent(colorMode)
        + "&page=" + encodeURIComponent(String(page0))
        + "&includePageSvg=true";
    if (debugTimings) {
        url += "&debugTimings=true";
    }
    $.ajax({
        url: url,
        type: "POST",
        dataType: "json",
        async: true,
        success: function (data) {
            let now = function () {
                return (typeof performance !== "undefined" && performance.now)
                    ? performance.now() : Date.now();
            };
            let tServer = now();
            if (!data || !data.renderId) {
                alert("Re-render did not return a renderId");
                endPresentationBusy();
                return;
            }
            renderId = data.renderId;
            if (typeof data.pageCount === "number" && data.pageCount > 0) {
                renderPageCount = String(data.pageCount);
                numberOfPages = data.pageCount;
                let p0 = parseInt(renderPageNumber0, 10) || 0;
                if (p0 >= data.pageCount) {
                    renderPageNumber0 = String(data.pageCount - 1);
                    renderPageNumber = String(data.pageCount);
                }
            }
            if (typeof data.pagesTruncated === "boolean") {
                pagesTruncated = data.pagesTruncated;
            }
            if (typeof data.pageNumber0 === "number") {
                renderPageNumber0 = String(data.pageNumber0);
                renderPageNumber = String(data.pageNumber0 + 1);
            }
            if (typeof data.logicalPageNumber0 === "number") {
                editLogicalPageNumber = data.logicalPageNumber0;
            }
            // If the kept selection only paints on another render page, navigate there
            // (avoids "I moved it and it vanished" after peer overflow / multi-page layout).
            if (keepSelectionName && data.componentRenderPages
                && typeof data.componentRenderPages === "object") {
                let targetR = data.componentRenderPages[keepSelectionName];
                if (typeof targetR !== "number") {
                    // metadata name may differ from drawn name — try exact keys only
                    targetR = -1;
                }
                let curR = parseInt(renderPageNumber0, 10) || 0;
                if (targetR >= 0 && targetR !== curR && typeof goToPage === "function") {
                    try {
                        sessionStorage.setItem("hopperPendingSelect", keepSelectionName);
                    } catch (e) { /* ignore */ }
                    // goToPage/loadDrawSvgPage manage their own busy; release soft-reload hold
                    endPresentationBusy();
                    goToPage(targetR);
                    return;
                }
            }
            lookupResults = [];
            let xhrMs = Math.round(tServer - t0);
            // SVG decode + paint (async); log full perceived time when done
            let prevPainted = typeof _onPageSvgPainted === "function" ? _onPageSvgPainted : null;
            _onPageSvgPainted = function (svgParts) {
                try {
                    let perceivedMs = Math.round(now() - t0);
                    logSoftReloadTimings(data.timings, {
                        xhrMs: xhrMs,
                        svgLoadMs: svgParts && svgParts.svgLoadMs,
                        geometriesMs: svgParts && svgParts.geometriesMs,
                        paintMs: svgParts && svgParts.paintMs,
                        refreshMs: _softReloadRefreshMs,
                        perceivedMs: perceivedMs,
                        inlineSvg: !!(svgParts && svgParts.inlineSvg),
                        inlinePng: !!(svgParts && svgParts.inlinePng),
                        pageSvgChars: data.pageSvgChars
                    });
                } finally {
                    if (typeof prevPainted === "function") {
                        try {
                            prevPainted(svgParts);
                        } catch (e) { /* ignore */ }
                    }
                    // Pair softReload begin; loadDrawSvgPage ends its own hold in finishPageSvgLoad
                    endPresentationBusy();
                }
            };
            let tRefresh0 = now();
            let _softReloadRefreshMs = 0;
            if (typeof loadDrawSvgPage === "function") {
                // Prefer PNG (fast light+dark, often 2× for HiDPI); SVG only as fallback
                loadDrawSvgPage(
                    data.pageSvg || null,
                    data.pagePngBase64 || null,
                    data.pagePngScale
                );
            } else {
                endPresentationBusy();
            }
            if (typeof invalidatePageBaseCache === "function") {
                invalidatePageBaseCache();
            }
            if (typeof window.hopperEdit !== "undefined" && typeof window.hopperEdit.refresh === "function") {
                window.hopperEdit.refresh(keepSelectionName);
            }
            _softReloadRefreshMs = Math.round(now() - tRefresh0);
            // Refresh isolated component preview, diagnostics, and layout result if property panel is open
            if (document.body.classList.contains("property-panel-open")
                && keepSelectionName) {
                if (typeof loadComponentPreview === "function") {
                    loadComponentPreview(keepSelectionName, null);
                }
                if (typeof loadComponentDiagnostics === "function") {
                    loadComponentDiagnostics(keepSelectionName, null, null);
                }
                // Update Layout result after Apply (saved attachments + re-layout)
                if (typeof loadComponentLayoutInfo === "function"
                    && document.getElementById("layoutResultPanel")) {
                    loadComponentLayoutInfo(keepSelectionName);
                }
            }
            if (typeof refreshUndoRedoState === "function") {
                refreshUndoRedoState();
            }
            if (typeof performance !== "undefined" && performance.mark) {
                try {
                    performance.mark("hopper-softReload-ui-done");
                } catch (e) { /* ignore */ }
            }
            // If SVG path never fires onload (edge case), still log xhr-only line
            if (typeof loadDrawSvgPage !== "function") {
                logSoftReloadTimings(data.timings, {xhrMs: xhrMs, perceivedMs: xhrMs});
            }
        },
        error: function (xhr) {
            console.warn("softReloadEditor failed, full navigation:", xhr.responseText);
            endPresentationBusy();
            beginPresentationBusy(); // stay wait until navigation replaces the page
            let page = typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0;
            let cm = currentColorMode();
            window.open(
                API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                    + "/page/" + page + "/?reload=true&colorMode=" + encodeURIComponent(cm),
                "_self"
            );
        }
    });
}

function reloadPresentation() {
    if (isEditMode()) {
        softReloadEditor(
            typeof window.hopperEdit !== "undefined" && window.hopperEdit.getSelectedName
                ? window.hopperEdit.getSelectedName()
                : oldComponentName
        );
        return;
    }
    beginPresentationBusy();
    let request = {
        "presentationName": presentationName,
        "parameters": parameterValues,
        "reload": true
    };
    $.ajax({
        url: API_BASE + "render/presentation/",
        type: "POST",
        data: JSON.stringify(request),
        contentType: "application/json; charset=utf-8",
        dataType: "text",
        success: function (newRenderId) {
            // Navigation replaces the document; keep wait cursor until then
            openPage(newRenderId);
        },
        error: function (request, status, error) {
            endPresentationBusy();
            showAjaxError("Reload of presentation failed", request, status, error);
        },
        async: false
    });
}

/**
 * The save button is clicked when editing a component.
 * We're now going to find and evaluate the "componentSaveScript".
 *
 */
function saveComponent() {
    try {
        let saveScript = document.getElementById("componentSaveScript");
        eval(saveScript.innerHTML);

        // Normalize blanks written by form controls before persistence
        normalizeOptionalEmptyStrings(componentJson);

        // The values in 'component' and iComponent will have been modified.
        // logicalPageNumber / pageRole were captured when the editor was opened.
        //
        let pageIndex = (typeof editLogicalPageNumber === "number" && !isNaN(editLogicalPageNumber))
            ? editLogicalPageNumber
            : 0;
        let role = editPageRole || "page";

        let modifyComponentRequest = {
            "presentationName": presentationName,
            "oldComponentName": oldComponentName,
            "logicalPageNumber": pageIndex,
            "pageRole": role,
            "hopperComponentJson": JSON.stringify(componentJson)
        };
        $.ajax({
            url: API_BASE + "metadata/modify/component/",
            type: "POST",
            data: JSON.stringify(modifyComponentRequest),
            contentType: "application/json; charset=utf-8",
            dataType: "text",
            async: false,
            success: () => {
                // Update name if renamed so further applies still find the component
                if (componentJson && componentJson["name"]) {
                    oldComponentName = componentJson["name"];
                }
                if (isEditMode()) {
                    softReloadEditor(oldComponentName);
                } else {
                    reloadPresentation();
                }
            },
            error: function (request, status, error) {
                showAjaxError("Save component failed", request, status, error);
            }
        });
    } catch (e) {
        showErrorDialog("Error saving component", e);
    }
}

function closeComponent() {
    if (typeof closeSourceConnectorInspect === "function") {
        closeSourceConnectorInspect();
    }
    setSidePanelOpen(false);
    // Drop the blue selection border when leaving the property editor
    if (typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.clearSelection === "function") {
        window.hopperEdit.clearSelection();
    }
    oldComponentName = null;
}

/**
 * Delete the component currently open in the property panel (Apply/Close bar).
 * Confirms, removes from metadata, soft-reloads the canvas, and closes the panel.
 */
function deleteComponent() {
    let name = (typeof oldComponentName !== "undefined" && oldComponentName)
        ? oldComponentName
        : null;
    if (!name && typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.getSelectedName === "function") {
        name = window.hopperEdit.getSelectedName();
    }
    if (!name) {
        if (typeof showErrorDialog === "function") {
            showErrorDialog("Delete component", "No component is open to delete.");
        } else {
            alert("No component is open to delete.");
        }
        return;
    }
    if (typeof window.hopperEdit === "undefined"
        || typeof window.hopperEdit.deleteSelectedComponent !== "function") {
        alert("Delete is only available in the presentation editor.");
        return;
    }
    // Ensure selection matches the open form (toolbar delete uses selectedComponentName)
    if (typeof window.hopperEdit.selectComponent === "function") {
        window.hopperEdit.selectComponent(name, false);
    }
    window.hopperEdit.deleteSelectedComponent({
        onSuccess: function () {
            oldComponentName = null;
        }
    });
}

/**
 * Open component property form by name (edit mode list / API path).
 * Uses GET edit/presentation/{name}/components/{componentName}/ then existing form HTML.
 */
function openComponentPropertiesByName(componentName) {
    if (!componentName || typeof presentationName === "undefined") {
        return;
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/components/" + encodeURIComponent(componentName) + "/",
        type: "GET",
        dataType: "json",
        success: function (payload) {
            editComponent(payload, {
                renderId: typeof renderId !== "undefined" ? renderId : null,
                pageNumber: typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0
            });
        },
        error: function (xhr, status, error) {
            showAjaxError("Failed to load component '" + componentName + "'", xhr, status, error);
        }
    });
}

/**
 * URL for a connector type icon declared on {@code @HConnectorPlugin(image=...)} in hopper-presentation-core
 * (or another plugin JAR). Served by {@code GET plugins/connectors/{id}/image}.
 */
function connectorPluginIconUrl(pluginId) {
    if (!pluginId) {
        return API_BASE + "plugins/connectors/default/image";
    }
    return API_BASE + "plugins/connectors/" + encodeURIComponent(pluginId) + "/image";
}

/** @deprecated use connectorPluginIconUrl — kept for any leftover callers */
function connectorPluginIconFile(pluginId) {
    // No longer a filename under static/images; return full URL for convenience
    return connectorPluginIconUrl(pluginId);
}

/**
 * @returns {Object.<string, {id:string, name:string, description:string}>} by plugin id
 */
function getConnectorPluginInfoMap() {
    let byId = {};
    $.ajax({
        url: API_BASE + "plugins/connectors",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            if (!list) {
                return;
            }
            for (let i = 0; i < list.length; i++) {
                let p = list[i];
                let id = p.id || p.pluginId;
                if (!id) {
                    continue;
                }
                byId[id] = {
                    id: id,
                    name: p.name || id,
                    description: p.description || ""
                };
            }
        },
        error: function () {
            // leave empty; tooltips fall back to plugin id
        }
    });
    return byId;
}

/**
 * @returns {Array.<{name:string, pluginId:string|null, description?:string, virtualPath?:string}>}
 */
function getConnectorSummaries() {
    if (window.HMetadataList && typeof HMetadataList.fetchMetadataSummary === "function") {
        let rows = HMetadataList.fetchMetadataSummary("connector");
        if (rows && rows.length) {
            return rows;
        }
    }
    let rows = [];
    $.ajax({
        url: API_BASE + "metadata/summary/connector/",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            rows = list || [];
        },
        error: function () {
            $.ajax({
                url: API_BASE + "metadata/connectors/summary/",
                type: "GET",
                dataType: "json",
                async: false,
                success: function (list) {
                    rows = list || [];
                },
                error: function () {
                    let names = getConnectorNames();
                    for (let i = 0; i < names.length; i++) {
                        if (names[i]) {
                            rows.push({name: names[i], pluginId: null, virtualPath: "", description: ""});
                        }
                    }
                }
            });
        }
    });
    return rows;
}

/**
 * Tooltip text: type name + description (plugin catalog).
 */
function connectorTypeTooltip(pluginId, pluginInfoMap) {
    let info = (pluginId && pluginInfoMap) ? pluginInfoMap[pluginId] : null;
    let typeLabel = info ? info.name : (pluginId || "Unknown type");
    let desc = info && info.description ? String(info.description).trim() : "";
    if (pluginId && typeLabel !== pluginId) {
        typeLabel = typeLabel + " (" + pluginId + ")";
    }
    // Use newline for multi-line native tooltips where supported
    if (desc) {
        return typeLabel + "\n" + desc;
    }
    return typeLabel;
}

/**
 * Open the side panel with a table of connector metadata elements.
 * Create controls at the top; shared meta-list chrome (icon, filter, path groups, delete).
 */
function editConnectorsList() {
    // Leaving the editor for the list discards form state — warn if dirty
    if (isConnectorEditorOpen() && isConnectorFormDirty()) {
        if (!confirmDiscardUnsavedConnector()) {
            return;
        }
    }
    clearConnectorEditorState();
    abortConnectorStudioRequests();
    let summaries = getConnectorSummaries();
    let pluginInfoMap = getConnectorPluginInfoMap();
    // Keep name cache warm for forms
    connectorNames = [""];
    for (let s = 0; s < summaries.length; s++) {
        if (summaries[s] && summaries[s].name) {
            connectorNames.push(summaries[s].name);
        }
    }

    let ML = window.HMetadataList;
    if (!ML) {
        alert("Metadata list helper not loaded");
        return;
    }

    function listOptions(filterQuery) {
        return {
            rows: summaries,
            filterQuery: filterQuery || "",
            listId: "connectorListTable",
            emptyMessage: "No connectors yet",
            iconForRow: function (row) {
                let pluginId = (row && row.pluginId) || "";
                return {
                    url: connectorPluginIconUrl(pluginId),
                    title: connectorTypeTooltip(pluginId, pluginInfoMap)
                };
            },
            actions: [
                {
                    id: "copy",
                    iconUrl: ML.staticImage("copy.svg"),
                    title: "Copy as…"
                },
                {
                    id: "delete",
                    iconUrl: ML.staticImage("delete.svg"),
                    title: "Delete connector"
                }
            ]
        };
    }

    let createHtml = "<label for=\"newConnectorPluginId\">New connector type</label> "
        + "<select id=\"newConnectorPluginId\" class=\"connector-list-type-select\"></select> "
        + "<button type=\"button\" class=\"connector-list-action-btn\" id=\"createConnectorBtn\">Create</button>";
    let footerHtml = "<button type=\"button\" class=\"connector-list-action-btn\" id=\"closeConnectorListBtn\">Close</button>";

    let html = ML.buildMetadataListPanelHtml(Object.assign({
        title: "Connectors",
        hint: "Select a connector to edit, or create a new one.",
        createHtml: createHtml,
        footerHtml: footerHtml,
        filterId: "connectorListFilter",
        bodyId: "metaListBody"
    }, listOptions("")));

    setSidePanelOpen(true, {withPreview: false});
    let editArea = document.getElementById("editArea");
    editArea.innerHTML = html;

    let byName = ML.rowsByNameMap(summaries);
    ML.bindMetadataListHandlers(editArea, {
        primary: function (name) {
            editConnectorByName(name);
        },
        copy: function (name) {
            ML.copyMetadataAs("connector", name, {
                existingRows: summaries,
                onSuccess: function () {
                    editConnectorsList();
                }
            });
        },
        delete: function (name) {
            deleteConnectorByName(name);
        }
    }, byName);

    let filterEl = document.getElementById("connectorListFilter");
    let bodyEl = document.getElementById("metaListBody");
    ML.bindMetadataListFilter(filterEl, bodyEl, function (q) {
        return listOptions(q);
    });

    let createBtn = document.getElementById("createConnectorBtn");
    if (createBtn) {
        createBtn.onclick = function () {
            createNewConnector();
        };
    }
    let closeBtn = document.getElementById("closeConnectorListBtn");
    if (closeBtn) {
        closeBtn.onclick = function () {
            closeConnector();
        };
    }

    loadConnectorPluginTypes("#newConnectorPluginId");
}

/**
 * Delete a connector after confirmation, then refresh the list.
 */
function deleteConnectorByName(name) {
    if (!name) {
        return;
    }
    let ok = window.confirm(
        "Delete connector \"" + name + "\"?\n\n"
        + "This cannot be undone. Presentations that use this connector may fail until reconfigured."
    );
    if (!ok) {
        return;
    }
    $.ajax({
        url: API_BASE + "metadata/connector/" + encodeURIComponent(name),
        type: "DELETE",
        dataType: "text",
        success: function () {
            connectorNames = null;
            // If we were editing this connector, clear form state (already confirmed delete)
            if (oldConnectorName === name || (connectorJson && connectorJson.name === name)) {
                clearConnectorEditorState();
            }
            editConnectorsList();
            if (typeof presentationName !== "undefined" && presentationName
                && typeof reloadPresentation === "function") {
                try {
                    reloadPresentation();
                } catch (e) {
                    console.warn("reloadPresentation after connector delete failed:", e);
                }
            }
        },
        error: function (xhr, status, error) {
            showAjaxError("Failed to delete connector '" + name + "'", xhr, status, error);
        }
    });
}

/** Escape text for use inside an HTML attribute delimited by double quotes. */
function escapeHtmlAttribute(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

/** Escape text for use as HTML element text content. */
function escapeHtmlText(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

function loadConnectorPluginTypes(selectSelector) {
    $.ajax({
        url: API_BASE + "plugins/connectors",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            let sel = $(selectSelector);
            sel.empty();
            if (!list || list.length === 0) {
                // Fallback known types (keep in sync with hopper-presentation-core HConnectorPlugin ids)
                list = [
                    {"id": "SqlConnector", "name": "SQL"},
                    {"id": "SampleDataConnector", "name": "Sample data"},
                    {"id": "CsvConnector", "name": "CSV file"},
                    {"id": "SortConnector", "name": "Sort"},
                    {"id": "SelectionConnector", "name": "Select fields"},
                    {"id": "SimpleFilterConnector", "name": "Simple filter"},
                    {"id": "HRestConnector", "name": "REST"},
                    {"id": "HListConnector", "name": "List"},
                    {"id": "DistinctConnector", "name": "Select distinct rows"},
                    {"id": "AggregateConnector", "name": "Aggregate"},
                    {"id": "PassthroughConnector", "name": "Passthrough"},
                    {"id": "ChainConnector", "name": "Chain connectors"}
                ];
            }
            for (let i = 0; i < list.length; i++) {
                let p = list[i];
                let id = p.id || p.pluginId;
                let name = p.name || id;
                sel.append($("<option></option>").attr("value", id).text(name));
            }
        },
        error: function () {
            let sel = $(selectSelector);
            sel.empty();
            ["SqlConnector", "SampleDataConnector", "CsvConnector", "SortConnector", "SelectionConnector",
                "SimpleFilterConnector", "HRestConnector", "HListConnector",
                "DistinctConnector", "AggregateConnector", "PassthroughConnector", "ChainConnector"].forEach(function (id) {
                sel.append($("<option></option>").attr("value", id).text(id));
            });
        }
    });
}

function createNewConnector() {
    let pluginId = document.getElementById("newConnectorPluginId").value;
    if (!pluginId) {
        alert("Select a connector type");
        return;
    }
    let name = "New " + pluginId;
    connectorJson = {
        "name": name,
        "connector": {}
    };
    connectorJson["connector"][pluginId] = {"pluginId": pluginId};
    connectorPluginId = pluginId;
    oldConnectorName = null;
    connectorFormBaseline = null;
    openConnectorEditForm(pluginId);
}

/**
 * Load connector metadata by name and open the generated form for its plugin type.
 */
function editConnectorByName(name) {
    $.ajax({
        url: API_BASE + "metadata/connector-json/" + encodeURIComponent(name),
        type: "GET",
        dataType: "json",
        success: function (data) {
            connectorJson = data;
            oldConnectorName = data["name"] || name;
            connectorFormBaseline = null;
            // Hop metadata shape: connector.{PluginId}: { fields... }
            let nested = data["connector"] || {};
            let keys = Object.keys(nested);
            if (keys.length === 0) {
                alert("Connector has no plugin payload: " + name);
                return;
            }
            connectorPluginId = keys[0];
            openConnectorEditForm(connectorPluginId);
        },
        error: function (request) {
            alert("Failed to load connector '" + name + "': " + request.responseText);
        }
    });
}

/** Default sample size for connector studio Apply preview. */
const CONNECTOR_STUDIO_MAX_ROWS = 20;
/** Debounce delay (ms) for auto full preview after source changes. */
const CONNECTOR_STUDIO_PREVIEW_DEBOUNCE_MS = 200;

let connectorStudioPreviewXhr = null;
let connectorStudioInputXhr = null;
let connectorStudioPreviewTimer = null;
let connectorStudioPreviewSeq = 0;

function openConnectorEditForm(pluginId) {
    setSidePanelOpen(true, {withPreview: false, connectorStudio: true});
    connectorColumnListTables = [];
    connectorColumnSelects = [];
    abortConnectorStudioRequests();
    // Keep presentation connector/theme caches warm when possible; only clear if missing
    // so ensureFormMetadataCaches does less work under nested sync XHR.
    if (typeof ensureFormMetadataCaches === "function") {
        try {
            ensureFormMetadataCaches();
        } catch (e) {
            console.warn("ensureFormMetadataCaches before connector form:", e);
        }
    }
    $.ajax({
        url: API_BASE + "edit/connector/" + encodeURIComponent(pluginId) + "/",
        type: "GET",
        dataType: "html",
        success: function (snippet) {
            let editArea = document.getElementById("editArea");
            editArea.innerHTML = buildConnectorStudioShell(snippet);
            // Defer so sync XHR in init/load (describe columns, metadata lists) is not nested
            // inside this async AJAX success callback.
            setTimeout(function () {
                runFormScripts(pluginId || "connector");
                wireConnectorStudioListeners();
                // Baseline after load so Close can detect unsaved edits
                captureConnectorFormBaseline();
                // Immediate input sample if a source is already selected
                let sourceEl = document.getElementById("sourceConnectorName");
                let sourceName = sourceEl ? (sourceEl.value || "").trim() : "";
                if (sourceName) {
                    let inputPane = document.getElementById("connectorInputPane");
                    if (inputPane) {
                        inputPane.removeAttribute("hidden");
                    }
                    previewConnectorStudioInputSource(sourceName);
                }
                // Full input+output preview from form state
                applyConnectorPreview();
            }, 0);
        },
        error: function (request) {
            alert("Failed to open connector editor: " + request.responseText);
        }
    });
}

/** True when the connector studio form (with save script) is in the side panel. */
function isConnectorEditorOpen() {
    return !!(document.getElementById("connectorSaveScript")
        || document.getElementById("connectorStudio"));
}

/**
 * Snapshot current form values into {@code connectorFormBaseline} (post-load / post-Save).
 */
function captureConnectorFormBaseline() {
    try {
        if (typeof syncConnectorJsonFromForm === "function") {
            syncConnectorJsonFromForm();
        }
        connectorFormBaseline = connectorJson != null
            ? JSON.stringify(connectorJson)
            : null;
    } catch (e) {
        console.warn("captureConnectorFormBaseline failed:", e);
        connectorFormBaseline = connectorJson != null
            ? JSON.stringify(connectorJson)
            : null;
    }
}

/**
 * True if the connector form differs from the last baseline (load or Save).
 * Apply (preview) does not clear dirty state — only Save does.
 */
function isConnectorFormDirty() {
    if (!isConnectorEditorOpen() || !connectorJson) {
        return false;
    }
    try {
        if (typeof syncConnectorJsonFromForm === "function") {
            if (!syncConnectorJsonFromForm()) {
                // Cannot read form — assume dirty if we have a baseline
                return connectorFormBaseline != null;
            }
        }
        let now = JSON.stringify(connectorJson);
        // New connector never saved: dirty once form differs from initial scaffold
        if (connectorFormBaseline == null) {
            return true;
        }
        return now !== connectorFormBaseline;
    } catch (e) {
        return true;
    }
}

/**
 * Confirm discarding unsaved connector edits. Returns true if the caller may proceed.
 */
function confirmDiscardUnsavedConnector() {
    if (!isConnectorFormDirty()) {
        return true;
    }
    let name = "";
    try {
        let nameEl = document.getElementById("connectorName");
        name = (nameEl && nameEl.value) ? nameEl.value.trim()
            : (oldConnectorName || (connectorJson && connectorJson.name) || "");
    } catch (e) {
        name = oldConnectorName || "";
    }
    let label = name ? ('"' + name + '"') : "this connector";
    return window.confirm(
        "You have unsaved changes to " + label + ".\n\n"
        + "If you close now, those changes will be lost.\n\n"
        + "Click OK to discard, or Cancel to keep editing (use Save to keep your work)."
    );
}

function clearConnectorEditorState() {
    connectorJson = null;
    connectorPluginId = null;
    oldConnectorName = null;
    connectorFormBaseline = null;
}

/**
 * Wrap generated connector form HTML in the studio shell:
 * input samples (top) → settings (middle) → output samples + errors (bottom).
 */
function buildConnectorStudioShell(settingsHtml) {
    return ""
        + '<div class="connector-studio" id="connectorStudio">'
        + '  <div class="connector-studio-toolbar">'
        + '    <label for="connectorStudioMaxRows">Sample rows </label>'
        + '    <select id="connectorStudioMaxRows" class="connector-studio-max-rows" title="Rows to fetch on Apply">'
        + '      <option value="10">10</option>'
        + '      <option value="20" selected>20</option>'
        + '      <option value="50">50</option>'
        + '      <option value="100">100</option>'
        + "    </select>"
        + '    <span class="connector-studio-status" id="connectorStudioStatus" aria-live="polite"></span>'
        + "  </div>"
        + '  <div class="connector-studio-pane connector-studio-input" id="connectorInputPane" hidden>'
        + '    <div class="connector-studio-pane-header">'
        + '      <span class="connector-studio-pane-title">Input</span>'
        + '      <span class="connector-studio-pane-meta" id="connectorInputMeta"></span>'
        + "    </div>"
        + '    <div class="connector-studio-sample" id="connectorInputSample">'
        + '      <p class="connector-studio-placeholder">Select a source connector to preview input rows</p>'
        + "    </div>"
        + '    <button type="button" class="connector-studio-layout-btn" id="connectorInputLayoutBtn"'
        + '            onclick="toggleConnectorLayoutDetails(\'input\')">Show layout details</button>'
        + '    <div class="connector-studio-layout" id="connectorInputLayout" hidden></div>'
        + "  </div>"
        + '  <div class="connector-studio-settings" id="connectorSettings">'
        + settingsHtml
        + "  </div>"
        + '  <div class="connector-studio-pane connector-studio-output" id="connectorOutputPane">'
        + '    <div class="connector-studio-pane-header">'
        + '      <span class="connector-studio-pane-title">Output</span>'
        + '      <span class="connector-studio-pane-meta" id="connectorOutputMeta"></span>'
        + "    </div>"
        + '    <div class="connector-studio-sample" id="connectorOutputSample">'
        + '      <p class="connector-studio-placeholder">Apply to load output sample rows</p>'
        + "    </div>"
        + '    <button type="button" class="connector-studio-layout-btn" id="connectorOutputLayoutBtn"'
        + '            onclick="toggleConnectorLayoutDetails(\'output\')">Show layout details</button>'
        + '    <div class="connector-studio-layout" id="connectorOutputLayout" hidden></div>'
        + "  </div>"
        + '  <div class="connector-studio-error" id="connectorStudioError" hidden>'
        + '    <div class="connector-studio-error-header">'
        + '      <strong>Error</strong>'
        + '      <button type="button" class="connector-studio-error-toggle" id="connectorStudioErrorToggle"'
        + '              onclick="toggleConnectorStudioErrorDetail()">Details</button>'
        + "    </div>"
        + '    <div class="connector-studio-error-summary" id="connectorStudioErrorSummary"></div>'
        + '    <pre class="connector-studio-error-detail" id="connectorStudioErrorDetail" hidden></pre>'
        + "  </div>"
        + "</div>";
}

/**
 * Wire studio behaviour after the generated form scripts run.
 * Source connector changes immediately show the input pane + sample.
 */
function wireConnectorStudioListeners() {
    let sourceEl = document.getElementById("sourceConnectorName");
    if (sourceEl && !sourceEl._hopperStudioWired) {
        sourceEl._hopperStudioWired = true;
        sourceEl.addEventListener("change", function () {
            onConnectorStudioSourceChanged();
        });
    }
    let maxEl = document.getElementById("connectorStudioMaxRows");
    if (maxEl && !maxEl._hopperStudioWired) {
        maxEl._hopperStudioWired = true;
        maxEl.addEventListener("change", function () {
            scheduleConnectorPreview(CONNECTOR_STUDIO_PREVIEW_DEBOUNCE_MS);
        });
    }
}

/**
 * User picked/cleared Source connector: show/hide input pane and load input samples now;
 * also schedule a full Apply so output stays consistent.
 */
function onConnectorStudioSourceChanged() {
    let sourceEl = document.getElementById("sourceConnectorName");
    let sourceName = sourceEl ? (sourceEl.value || "").trim() : "";
    let inputPane = document.getElementById("connectorInputPane");

    if (!sourceName) {
        if (inputPane) {
            inputPane.setAttribute("hidden", "hidden");
        }
        clearConnectorStudioSide("input");
        abortConnectorStudioInputRequest();
        // Still refresh output (source may no longer apply)
        scheduleConnectorPreview(CONNECTOR_STUDIO_PREVIEW_DEBOUNCE_MS);
        return;
    }

    if (inputPane) {
        inputPane.removeAttribute("hidden");
    }
    // Immediate input sample from the selected source (does not wait for full transform)
    previewConnectorStudioInputSource(sourceName);
    // Debounced full preview updates output (and reconciles input from the same request)
    scheduleConnectorPreview(CONNECTOR_STUDIO_PREVIEW_DEBOUNCE_MS);
}

function getConnectorStudioMaxRows() {
    let el = document.getElementById("connectorStudioMaxRows");
    if (el && el.value) {
        let n = parseInt(el.value, 10);
        if (!isNaN(n) && n > 0) {
            return Math.min(100, n);
        }
    }
    return CONNECTOR_STUDIO_MAX_ROWS;
}

function setConnectorStudioStatus(text) {
    let el = document.getElementById("connectorStudioStatus");
    if (el) {
        el.textContent = text || "";
    }
}

function abortConnectorStudioInputRequest() {
    if (connectorStudioInputXhr && connectorStudioInputXhr.readyState !== 4) {
        try {
            connectorStudioInputXhr.abort();
        } catch (e) { /* ignore */ }
    }
    connectorStudioInputXhr = null;
}

function abortConnectorStudioPreviewRequest() {
    if (connectorStudioPreviewXhr && connectorStudioPreviewXhr.readyState !== 4) {
        try {
            connectorStudioPreviewXhr.abort();
        } catch (e) { /* ignore */ }
    }
    connectorStudioPreviewXhr = null;
}

function abortConnectorStudioRequests() {
    if (connectorStudioPreviewTimer) {
        clearTimeout(connectorStudioPreviewTimer);
        connectorStudioPreviewTimer = null;
    }
    abortConnectorStudioInputRequest();
    abortConnectorStudioPreviewRequest();
    setConnectorStudioBusy(false);
    setConnectorStudioStatus("");
}

/**
 * Load sample rows for a named source connector into the INPUT pane only.
 */
function previewConnectorStudioInputSource(sourceName) {
    if (!sourceName) {
        return;
    }
    abortConnectorStudioInputRequest();
    setConnectorStudioMeta("input", sourceName + " | loading...");
    let sampleEl = document.getElementById("connectorInputSample");
    if (sampleEl) {
        sampleEl.innerHTML = '<p class="connector-studio-placeholder">Loading input sample...</p>';
    }

    let seq = ++connectorStudioPreviewSeq;
    connectorStudioInputXhr = $.ajax({
        url: API_BASE + "metadata/connector-json/" + encodeURIComponent(sourceName),
        type: "GET",
        dataType: "json",
        success: function (data) {
            if (!data) {
                return;
            }
            let body = {
                hopperConnectorJson: JSON.stringify(data),
                maxRows: getConnectorStudioMaxRows()
            };
            if (typeof renderId !== "undefined" && renderId) {
                body.renderId = renderId;
            }
            connectorStudioInputXhr = $.ajax({
                url: API_BASE + "edit/connector/preview/",
                type: "POST",
                data: JSON.stringify(body),
                contentType: "application/json; charset=utf-8",
                dataType: "json",
                success: function (result) {
                    // Ignore stale responses if a newer preview finished
                    if (seq < connectorStudioPreviewSeq - 1) {
                        return;
                    }
                    let inputPane = document.getElementById("connectorInputPane");
                    if (inputPane) {
                        inputPane.removeAttribute("hidden");
                    }
                    if (result && result.output) {
                        // Source connector's output is this transform's input
                        let side = result.output;
                        side.connectorName = sourceName;
                        renderConnectorStudioSide("input", side);
                    } else if (result && result.error) {
                        renderConnectorStudioSide("input", {
                            connectorName: sourceName,
                            rowMeta: [],
                            rows: [],
                            errorSummary: result.error.summary || "Could not sample source",
                            errorDetail: result.error.detail
                        });
                    }
                },
                error: function (xhr, status) {
                    if (status === "abort") {
                        return;
                    }
                    setConnectorStudioMeta("input", sourceName);
                    if (sampleEl) {
                        sampleEl.innerHTML = '<p class="connector-studio-placeholder connector-studio-side-error">'
                            + "Failed to load input sample</p>";
                    }
                }
            });
        },
        error: function (xhr, status) {
            if (status === "abort") {
                return;
            }
            setConnectorStudioMeta("input", sourceName);
            if (sampleEl) {
                sampleEl.innerHTML = '<p class="connector-studio-placeholder connector-studio-side-error">'
                    + "Could not load source connector '" + escapeHtmlText(sourceName) + "'</p>";
            }
        }
    });
}

/**
 * Pull form values into {@code connectorJson} via the generated save script (does not persist).
 * @returns {boolean} true if sync succeeded
 */
function syncConnectorJsonFromForm() {
    try {
        // Flush visual chain step form into state before generated save script reads the list
        if (typeof HopperChainEditor !== "undefined" && HopperChainEditor.isActive()) {
            HopperChainEditor.flushSelectedStepForm();
            // Top-bar Source combo → hidden sourceConnectorName before save script runs
            let chainSrc = document.getElementById("chainEditorSource");
            let formSrc = document.getElementById("sourceConnectorName");
            if (chainSrc && formSrc) {
                let v = chainSrc.value || "";
                let found = false;
                for (let i = 0; i < formSrc.options.length; i++) {
                    if (formSrc.options[i].value === v) {
                        found = true;
                        break;
                    }
                }
                if (!found && v) {
                    let o = document.createElement("option");
                    o.value = v;
                    o.textContent = v;
                    formSrc.appendChild(o);
                }
                formSrc.value = v;
            }
        }
        let saveScript = document.getElementById("connectorSaveScript");
        if (saveScript) {
            eval(saveScript.innerHTML);
        }
        // Ensure chain virtual path is on the wrapper (shared default for the chain package)
        if (typeof HopperChainEditor !== "undefined" && HopperChainEditor.isActive()) {
            let vpEl = document.getElementById("chainEditorVirtualPath")
                || document.getElementById("connectorVirtualPath");
            if (vpEl && typeof connectorJson !== "undefined" && connectorJson) {
                connectorJson.virtualPath = (vpEl.value || "").trim();
            }
            // Source on plugin payload
            let chainSrc2 = document.getElementById("chainEditorSource");
            if (chainSrc2 && connectorJson && connectorJson.connector
                && connectorJson.connector.ChainConnector) {
                let sv = (chainSrc2.value || "").trim();
                connectorJson.connector.ChainConnector.sourceConnectorName = sv || null;
            }
        }
        return true;
    } catch (e) {
        showConnectorStudioError("Could not read form values", String(e));
        return false;
    }
}

/**
 * Schedule a full input+output preview after {@code delayMs} (cancels previous timer).
 */
function scheduleConnectorPreview(delayMs) {
    if (connectorStudioPreviewTimer) {
        clearTimeout(connectorStudioPreviewTimer);
    }
    connectorStudioPreviewTimer = setTimeout(function () {
        connectorStudioPreviewTimer = null;
        applyConnectorPreview();
    }, typeof delayMs === "number" ? delayMs : CONNECTOR_STUDIO_PREVIEW_DEBOUNCE_MS);
}

/**
 * Apply: refresh input/output sample tables from current form state (no metadata write).
 */
function applyConnectorPreview() {
    if (!syncConnectorJsonFromForm()) {
        return;
    }
    if (!connectorJson) {
        showConnectorStudioError("No connector data to preview", "connectorJson is not set");
        return;
    }
    abortConnectorStudioPreviewRequest();
    setConnectorStudioBusy(true);
    setConnectorStudioStatus("Previewing...");
    clearConnectorStudioError();

    let body = {
        hopperConnectorJson: JSON.stringify(connectorJson),
        maxRows: getConnectorStudioMaxRows()
    };
    if (typeof renderId !== "undefined" && renderId) {
        body.renderId = renderId;
    }

    let seq = ++connectorStudioPreviewSeq;
    connectorStudioPreviewXhr = $.ajax({
        url: API_BASE + "edit/connector/preview/",
        type: "POST",
        data: JSON.stringify(body),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (result) {
            if (seq !== connectorStudioPreviewSeq) {
                return; // superseded
            }
            setConnectorStudioBusy(false);
            setConnectorStudioStatus("Updated");
            renderConnectorStudioPreview(result);
            // Clear status after a moment
            setTimeout(function () {
                if (seq === connectorStudioPreviewSeq) {
                    setConnectorStudioStatus("");
                }
            }, 1500);
        },
        error: function (xhr, status) {
            if (status === "abort") {
                return;
            }
            if (seq !== connectorStudioPreviewSeq) {
                return;
            }
            setConnectorStudioBusy(false);
            setConnectorStudioStatus("");
            let msg = (xhr && xhr.responseText) ? xhr.responseText : "Preview request failed";
            showConnectorStudioError("Preview request failed", msg);
            clearConnectorStudioSide("output");
        }
    });
}

/**
 * Save: persist connector metadata (and soft-reload presentation when in the editor).
 */
function saveConnector() {
    try {
        if (!syncConnectorJsonFromForm()) {
            return;
        }
        let request = {
            "oldConnectorName": oldConnectorName,
            "hopperConnectorJson": JSON.stringify(connectorJson)
        };
        $.ajax({
            url: API_BASE + "metadata/modify/connector/",
            type: "POST",
            data: JSON.stringify(request),
            contentType: "application/json; charset=utf-8",
            dataType: "text",
            async: false,
            success: function (savedName) {
                oldConnectorName = savedName;
                connectorNames = null; // refresh cache
                // Saved state is the new clean baseline
                captureConnectorFormBaseline();
                // Soft-reload presentation so components pick up connector changes
                if (typeof presentationName !== "undefined" && presentationName
                    && typeof reloadPresentation === "function") {
                    try {
                        reloadPresentation();
                        return;
                    } catch (e) {
                        console.warn("reloadPresentation after connector save failed:", e);
                    }
                }
                alert("Connector saved: " + savedName);
            },
            error: function (request) {
                alert("Save connector failed: " + request.responseText);
            }
        });
    } catch (e) {
        alert("Error saving connector: " + e);
    }
}

function closeConnector() {
    if (!confirmDiscardUnsavedConnector()) {
        return;
    }
    abortConnectorStudioRequests();
    clearConnectorEditorState();
    // Admin list Close: leave the Connectors tab (do not re-open the list)
    if (isAdminMetadataHost() && isAdminMetadataCatalogListOpen()) {
        exitAdminMetadataCatalog("overview");
        return;
    }
    // Editor Close (presentation or admin form): hide panel / return to catalog list
    setSidePanelOpen(false);
}

// ---------------------------------------------------------------------------
// Connector studio: sample tables, layout details, errors
// ---------------------------------------------------------------------------

function setConnectorStudioBusy(busy) {
    let studio = document.getElementById("connectorStudio");
    if (!studio) {
        return;
    }
    // Soft busy: dim sample panes only so settings stay editable while preview runs
    let samples = studio.querySelectorAll(".connector-studio-sample");
    for (let i = 0; i < samples.length; i++) {
        if (busy) {
            samples[i].classList.add("is-busy");
        } else {
            samples[i].classList.remove("is-busy");
        }
    }
    if (busy) {
        studio.classList.add("is-previewing");
    } else {
        studio.classList.remove("is-previewing");
    }
}

function renderConnectorStudioPreview(result) {
    if (!result) {
        showConnectorStudioError("Empty preview response", "");
        return;
    }

    // Input pane: show only when we have input data or an input-related failure with source
    let hasInput = result.input
        && (result.input.rowMeta || (result.input.rows && result.input.rows.length)
            || result.input.errorSummary);
    // Also show input pane if form has a source connector selected (even before first successful sample)
    let sourceEl = document.getElementById("sourceConnectorName");
    let sourceName = sourceEl ? (sourceEl.value || "").trim() : "";
    let showInput = hasInput || !!sourceName;

    let inputPane = document.getElementById("connectorInputPane");
    if (inputPane) {
        if (showInput) {
            inputPane.removeAttribute("hidden");
            if (result.input) {
                renderConnectorStudioSide("input", result.input);
            } else {
                clearConnectorStudioSide("input");
                setConnectorStudioMeta("input", sourceName ? ("source: " + sourceName) : "");
            }
        } else {
            inputPane.setAttribute("hidden", "hidden");
            clearConnectorStudioSide("input");
        }
    }

    if (result.output) {
        renderConnectorStudioSide("output", result.output);
    } else {
        clearConnectorStudioSide("output");
    }

    if (result.ok === false && result.error) {
        showConnectorStudioError(
            result.error.summary || "Preview failed",
            result.error.detail || result.error.summary || ""
        );
    } else {
        clearConnectorStudioError();
    }
}

function renderConnectorStudioSide(which, side) {
    let sampleEl = document.getElementById(
        which === "input" ? "connectorInputSample" : "connectorOutputSample");
    let layoutEl = document.getElementById(
        which === "input" ? "connectorInputLayout" : "connectorOutputLayout");
    if (!sampleEl) {
        return;
    }

    let rowMeta = side.rowMeta || [];
    let rows = side.rows || [];
    let metaParts = [];
    if (side.connectorName) {
        metaParts.push(side.connectorName);
    }
    if (typeof side.rowCountReturned === "number") {
        metaParts.push(side.rowCountReturned + " row(s)");
    }
    if (side.truncated) {
        metaParts.push("truncated");
    }
    setConnectorStudioMeta(which, metaParts.join(" | "));

    if (side.errorSummary && (!rows || !rows.length)) {
        sampleEl.innerHTML = '<p class="connector-studio-placeholder connector-studio-side-error">'
            + escapeHtmlText(side.errorSummary) + "</p>";
    } else if (!rows || !rows.length) {
        if (rowMeta && rowMeta.length) {
            sampleEl.innerHTML = '<p class="connector-studio-placeholder">No sample rows '
                + "(layout available via Show layout details)</p>";
        } else {
            sampleEl.innerHTML = '<p class="connector-studio-placeholder">No sample rows</p>';
        }
    } else {
        sampleEl.innerHTML = buildConnectorSampleTableHtml(rowMeta, rows);
    }

    if (layoutEl) {
        // Preserve expand/collapse state across Apply refreshes
        let wasOpen = !layoutEl.hasAttribute("hidden");
        layoutEl.innerHTML = buildConnectorLayoutTableHtml(rowMeta);
        let btn = document.getElementById(
            which === "input" ? "connectorInputLayoutBtn" : "connectorOutputLayoutBtn");
        if (wasOpen) {
            layoutEl.removeAttribute("hidden");
            if (btn) {
                btn.textContent = "Hide layout details";
            }
        } else {
            layoutEl.setAttribute("hidden", "hidden");
            if (btn) {
                btn.textContent = "Show layout details";
            }
        }
    }
}

function setConnectorStudioMeta(which, text) {
    let el = document.getElementById(
        which === "input" ? "connectorInputMeta" : "connectorOutputMeta");
    if (el) {
        el.textContent = text || "";
    }
}

function clearConnectorStudioSide(which) {
    let sampleEl = document.getElementById(
        which === "input" ? "connectorInputSample" : "connectorOutputSample");
    let layoutEl = document.getElementById(
        which === "input" ? "connectorInputLayout" : "connectorOutputLayout");
    if (sampleEl) {
        sampleEl.innerHTML = '<p class="connector-studio-placeholder">-</p>';
    }
    if (layoutEl) {
        layoutEl.innerHTML = "";
        layoutEl.setAttribute("hidden", "hidden");
    }
    let btn = document.getElementById(
        which === "input" ? "connectorInputLayoutBtn" : "connectorOutputLayoutBtn");
    if (btn) {
        btn.textContent = "Show layout details";
    }
    setConnectorStudioMeta(which, "");
}

function buildConnectorSampleTableHtml(rowMeta, rows) {
    let cols = rowMeta && rowMeta.length
        ? rowMeta
        : (rows[0] || []).map(function (_, i) {
            return {name: "c" + i, type: ""};
        });
    let html = '<div class="connector-studio-table-wrap"><table class="connector-studio-table hopper-table">';
    html += "<thead><tr>";
    for (let c = 0; c < cols.length; c++) {
        let name = cols[c].name || ("#" + c);
        html += "<th>" + escapeHtmlText(name) + "</th>";
    }
    html += "</tr></thead><tbody>";
    for (let r = 0; r < rows.length; r++) {
        html += "<tr>";
        let row = rows[r] || [];
        for (let c = 0; c < cols.length; c++) {
            let cell = c < row.length ? row[c] : "";
            if (cell === null || cell === undefined) {
                cell = "";
            }
            html += "<td>" + escapeHtmlText(String(cell)) + "</td>";
        }
        html += "</tr>";
    }
    html += "</tbody></table></div>";
    return html;
}

/**
 * Format length/precision for layout tables: blank when unset or negative
 * (Hop often uses -1 for "not applicable").
 */
function formatRowMetaSizeValue(value) {
    if (value === undefined || value === null || value === "") {
        return "";
    }
    let n = Number(value);
    if (!isNaN(n) && n < 0) {
        return "";
    }
    return String(value);
}

function buildConnectorLayoutTableHtml(rowMeta) {
    if (!rowMeta || !rowMeta.length) {
        return '<p class="connector-studio-placeholder">No layout (row meta) available</p>';
    }
    let html = '<div class="connector-studio-table-wrap"><table class="connector-studio-table connector-studio-layout-table hopper-table">';
    html += "<thead><tr><th>Name</th><th>Type</th><th>Length</th><th>Precision</th></tr></thead><tbody>";
    for (let i = 0; i < rowMeta.length; i++) {
        let v = rowMeta[i] || {};
        html += "<tr>"
            + "<td>" + escapeHtmlText(v.name || "") + "</td>"
            + "<td>" + escapeHtmlText(v.type || "") + "</td>"
            + "<td>" + escapeHtmlText(formatRowMetaSizeValue(v.length)) + "</td>"
            + "<td>" + escapeHtmlText(formatRowMetaSizeValue(v.precision)) + "</td>"
            + "</tr>";
    }
    html += "</tbody></table></div>";
    return html;
}

/**
 * Toggle layout details for input or output pane.
 * @param {"input"|"output"} which
 */
function toggleConnectorLayoutDetails(which) {
    let layoutEl = document.getElementById(
        which === "input" ? "connectorInputLayout" : "connectorOutputLayout");
    let btn = document.getElementById(
        which === "input" ? "connectorInputLayoutBtn" : "connectorOutputLayoutBtn");
    if (!layoutEl) {
        return;
    }
    if (layoutEl.hasAttribute("hidden")) {
        layoutEl.removeAttribute("hidden");
        if (btn) {
            btn.textContent = "Hide layout details";
        }
    } else {
        layoutEl.setAttribute("hidden", "hidden");
        if (btn) {
            btn.textContent = "Show layout details";
        }
    }
}

function showConnectorStudioError(summary, detail) {
    let panel = document.getElementById("connectorStudioError");
    if (!panel) {
        // Studio shell not present (e.g. early failure)
        console.warn("connector studio error:", summary, detail);
        return;
    }
    let summaryEl = document.getElementById("connectorStudioErrorSummary");
    let detailEl = document.getElementById("connectorStudioErrorDetail");
    let toggle = document.getElementById("connectorStudioErrorToggle");
    if (summaryEl) {
        summaryEl.textContent = summary || "Error";
    }
    if (detailEl) {
        detailEl.textContent = detail || summary || "";
        detailEl.setAttribute("hidden", "hidden");
    }
    if (toggle) {
        toggle.textContent = "Details";
        // Auto-expand when multi-line detail
        if (detail && detail !== summary && detail.indexOf("\n") >= 0) {
            detailEl.removeAttribute("hidden");
            toggle.textContent = "Hide details";
        }
    }
    panel.removeAttribute("hidden");
}

function clearConnectorStudioError() {
    let panel = document.getElementById("connectorStudioError");
    if (!panel) {
        return;
    }
    panel.setAttribute("hidden", "hidden");
    let summaryEl = document.getElementById("connectorStudioErrorSummary");
    let detailEl = document.getElementById("connectorStudioErrorDetail");
    if (summaryEl) {
        summaryEl.textContent = "";
    }
    if (detailEl) {
        detailEl.textContent = "";
        detailEl.setAttribute("hidden", "hidden");
    }
}

function toggleConnectorStudioErrorDetail() {
    let detailEl = document.getElementById("connectorStudioErrorDetail");
    let toggle = document.getElementById("connectorStudioErrorToggle");
    if (!detailEl) {
        return;
    }
    if (detailEl.hasAttribute("hidden")) {
        detailEl.removeAttribute("hidden");
        if (toggle) {
            toggle.textContent = "Hide details";
        }
    } else {
        detailEl.setAttribute("hidden", "hidden");
        if (toggle) {
            toggle.textContent = "Details";
        }
    }
}

// ---------------------------------------------------------------------------
// Database connection administration (HDatabaseConnection metadata)
// ---------------------------------------------------------------------------

const DB_CONNECTION_METADATA_KEY = "hopper-database-connection";
let databaseConnectionNames = null;
let oldDatabaseConnectionName = null;
let databaseConnectionJson = null;

/**
 * Open the side panel with a list of Data Hopper Database Connection metadata elements.
 */
function editDatabaseConnectionsList() {
    let ML = window.HMetadataList;
    if (!ML) {
        alert("Metadata list helper not loaded");
        return;
    }
    let summaries = getDatabaseConnectionSummaries();
    databaseConnectionNames = summaries.map(function (r) {
        return r && r.name ? r.name : "";
    }).filter(Boolean);

    function listOptions(filterQuery) {
        return {
            rows: summaries,
            filterQuery: filterQuery || "",
            listId: "databaseConnectionListTable",
            emptyMessage: "No database connections yet",
            iconForRow: function () {
                return {url: ML.staticImage("database.svg"), title: "Database connection"};
            },
            actions: [
                {
                    id: "copy",
                    iconUrl: ML.staticImage("copy.svg"),
                    title: "Copy as…"
                },
                {
                    id: "delete",
                    iconUrl: ML.staticImage("delete.svg"),
                    title: "Delete connection"
                }
            ]
        };
    }

    let createHtml = "<button type=\"button\" id=\"createDatabaseConnectionBtn\" "
        + "class=\"home-btn home-btn-primary\">New connection</button>";
    let footerHtml = "<button type=\"button\" id=\"closeDatabaseConnectionListBtn\" class=\"home-btn\">Close</button>";

    let html = ML.buildMetadataListPanelHtml(Object.assign({
        title: "Database connections",
        hint: "Manage <code>HDatabaseConnection</code> metadata "
            + "(used by SQL connectors and data sources).",
        createHtml: createHtml,
        footerHtml: footerHtml,
        filterId: "databaseConnectionListFilter",
        bodyId: "metaListBody"
    }, listOptions("")));

    setSidePanelOpen(true, {withPreview: false});
    let editArea = document.getElementById("editArea");
    editArea.innerHTML = html;

    let byName = ML.rowsByNameMap(summaries);
    ML.bindMetadataListHandlers(editArea, {
        primary: function (name) {
            editDatabaseConnectionByName(name);
        },
        copy: function (name) {
            ML.copyMetadataAs("hopper-database-connection", name, {
                existingRows: summaries,
                onSuccess: function () {
                    editDatabaseConnectionsList();
                }
            });
        },
        delete: function (name) {
            deleteDatabaseConnectionByName(name);
        }
    }, byName);

    ML.bindMetadataListFilter(
        document.getElementById("databaseConnectionListFilter"),
        document.getElementById("metaListBody"),
        function (q) {
            return listOptions(q);
        }
    );

    let createBtn = document.getElementById("createDatabaseConnectionBtn");
    if (createBtn) {
        createBtn.onclick = function () {
            createNewDatabaseConnection();
        };
    }
    let closeBtn = document.getElementById("closeDatabaseConnectionListBtn");
    if (closeBtn) {
        closeBtn.onclick = function () {
            closeDatabaseConnection();
        };
    }
}

function getDatabaseConnectionSummaries() {
    if (window.HMetadataList) {
        let rows = HMetadataList.fetchMetadataSummary(DB_CONNECTION_METADATA_KEY);
        if (rows) {
            return rows;
        }
    }
    return [];
}

function getDatabaseConnectionNames() {
    let summaries = getDatabaseConnectionSummaries();
    if (summaries && summaries.length) {
        return summaries.map(function (r) {
            return r.name;
        }).filter(Boolean);
    }
    let names = [];
    $.ajax({
        url: API_BASE + "metadata/list/" + DB_CONNECTION_METADATA_KEY + "/",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            names = list || [];
        },
        error: function (xhr) {
            console.warn("Failed to list database connections:", xhr.responseText || xhr.status);
        }
    });
    return names;
}

/**
 * Delete a database connection from the list (row action).
 */
function deleteDatabaseConnectionByName(name) {
    if (!name) {
        return;
    }
    let ok = window.confirm(
        "Delete database connection \"" + name + "\"?\n\n"
        + "This cannot be undone. SQL connectors that use it may fail."
    );
    if (!ok) {
        return;
    }
    $.ajax({
        url: API_BASE + "metadata/" + DB_CONNECTION_METADATA_KEY + "/" + encodeURIComponent(name),
        type: "DELETE",
        dataType: "text",
        success: function () {
            if (oldDatabaseConnectionName === name) {
                oldDatabaseConnectionName = null;
                databaseConnectionJson = null;
            }
            editDatabaseConnectionsList();
        },
        error: function (xhr, status, error) {
            showAjaxError("Failed to delete connection '" + name + "'", xhr, status, error);
        }
    });
}

function getDatabaseTypeCodes() {
    let types = [];
    $.ajax({
        url: API_BASE + "metadata/database-types",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            types = list || [];
        },
        error: function () {
            types = [
                {id: "POSTGRESQL", name: "PostgreSQL"},
                {id: "MYSQL", name: "MySQL"},
                {id: "H2", name: "H2"},
                {id: "ORACLE", name: "Oracle"},
                {id: "MSSQL", name: "MS SQL Server"},
                {id: "GENERIC", name: "Generic"}
            ];
        }
    });
    return types;
}

function createNewDatabaseConnection() {
    oldDatabaseConnectionName = null;
    databaseConnectionJson = {
        name: "New connection",
        virtualPath: "",
        databaseTypeCode: "POSTGRESQL",
        hostname: "localhost",
        port: "5432",
        databaseName: "",
        username: "",
        password: ""
    };
    openDatabaseConnectionForm(databaseConnectionJson);
}

function editDatabaseConnectionByName(name) {
    $.ajax({
        url: API_BASE + "metadata/" + DB_CONNECTION_METADATA_KEY + "/" + encodeURIComponent(name),
        type: "GET",
        dataType: "json",
        success: function (data) {
            databaseConnectionJson = data || {};
            oldDatabaseConnectionName = data["name"] || name;
            openDatabaseConnectionForm(databaseConnectionJson);
        },
        error: function (xhr) {
            alert("Failed to load database connection '" + name + "': "
                + (xhr.responseText || xhr.status));
        }
    });
}

function openDatabaseConnectionForm(json) {
    setSidePanelOpen(true, {withPreview: false});
    let types = getDatabaseTypeCodes();
    let typeCode = json["databaseTypeCode"] || "POSTGRESQL";
    let html = "";
    html += "<div class=\"form-action-bar\">";
    html += "<button type=\"button\" id=\"dbConnSaveBtn\" title=\"Save connection\">Apply</button>";
    html += "<button type=\"button\" id=\"dbConnTestBtn\" title=\"Test connection\">Test</button>";
    html += "<button type=\"button\" id=\"dbConnDeleteBtn\" title=\"Delete connection\">Delete</button>";
    html += "<button type=\"button\" id=\"dbConnBackBtn\" title=\"Back to list\">Back</button>";
    html += "<button type=\"button\" id=\"dbConnCloseBtn\" title=\"Close panel\">Close</button>";
    html += "</div>";
    html += "<h3>Database connection</h3>";
    html += "<label for=\"dbConnName\">Name: </label>";
    html += "<input type=\"text\" id=\"dbConnName\" style=\"width:90%\" value=\""
        + escapeHtmlAttribute(json["name"] || "") + "\"><br><br>";
    html += "<label for=\"dbConnVirtualPath\">Virtual path: </label>";
    html += "<input type=\"text\" id=\"dbConnVirtualPath\" style=\"width:70%\" placeholder=\"e.g. prod/warehouse\" value=\""
        + escapeHtmlAttribute(json["virtualPath"] || "") + "\"><br><br>";
    html += "<label for=\"dbConnType\">Database type: </label>";
    html += "<select id=\"dbConnType\" style=\"width:70%\">";
    for (let i = 0; i < types.length; i++) {
        let t = types[i];
        let id = t.id || t;
        let label = t.name || id;
        let sel = (String(id) === String(typeCode)) ? " selected" : "";
        html += "<option value=\"" + escapeHtmlAttribute(id) + "\"" + sel + ">"
            + escapeHtmlText(label) + " (" + escapeHtmlText(id) + ")</option>";
    }
    html += "</select><br><br>";
    html += "<label for=\"dbConnHost\">Hostname: </label>";
    html += "<input type=\"text\" id=\"dbConnHost\" style=\"width:70%\" value=\""
        + escapeHtmlAttribute(json["hostname"] || "") + "\"><br><br>";
    html += "<label for=\"dbConnPort\">Port: </label>";
    html += "<input type=\"text\" id=\"dbConnPort\" style=\"width:30%\" value=\""
        + escapeHtmlAttribute(json["port"] || "") + "\"><br><br>";
    html += "<label for=\"dbConnDatabase\">Database name / path: </label>";
    html += "<input type=\"text\" id=\"dbConnDatabase\" style=\"width:90%\" value=\""
        + escapeHtmlAttribute(json["databaseName"] || "") + "\"><br><br>";
    html += "<label for=\"dbConnUser\">Username: </label>";
    html += "<input type=\"text\" id=\"dbConnUser\" style=\"width:50%\" value=\""
        + escapeHtmlAttribute(json["username"] || "") + "\" autocomplete=\"off\"><br><br>";
    html += "<label for=\"dbConnPassword\">Password: </label>";
    html += "<input type=\"password\" id=\"dbConnPassword\" style=\"width:50%\" value=\""
        + escapeHtmlAttribute(json["password"] || "") + "\" autocomplete=\"new-password\"><br>";
    html += "<p class=\"editor-hint\">Leave password blank only if you intend an empty password. "
        + "Encrypted values from Hop are re-saved as-is unless changed.</p>";
    html += "<p id=\"dbConnStatus\" class=\"editor-hint\"></p>";

    document.getElementById("editArea").innerHTML = html;

    document.getElementById("dbConnSaveBtn").onclick = function () {
        saveDatabaseConnection();
    };
    document.getElementById("dbConnTestBtn").onclick = function () {
        testDatabaseConnection();
    };
    document.getElementById("dbConnDeleteBtn").onclick = function () {
        deleteDatabaseConnection();
    };
    document.getElementById("dbConnBackBtn").onclick = function () {
        editDatabaseConnectionsList();
    };
    document.getElementById("dbConnCloseBtn").onclick = function () {
        closeDatabaseConnection();
    };
    // Hide delete for brand-new unsaved connections
    if (!oldDatabaseConnectionName) {
        document.getElementById("dbConnDeleteBtn").disabled = true;
    }
}

function collectDatabaseConnectionForm() {
    return {
        name: (document.getElementById("dbConnName").value || "").trim(),
        virtualPath: (document.getElementById("dbConnVirtualPath")
            ? (document.getElementById("dbConnVirtualPath").value || "").trim()
            : ""),
        databaseTypeCode: document.getElementById("dbConnType").value,
        hostname: (document.getElementById("dbConnHost").value || "").trim(),
        port: (document.getElementById("dbConnPort").value || "").trim(),
        databaseName: (document.getElementById("dbConnDatabase").value || "").trim(),
        username: (document.getElementById("dbConnUser").value || "").trim(),
        password: document.getElementById("dbConnPassword").value || ""
    };
}

function saveDatabaseConnection() {
    let body = collectDatabaseConnectionForm();
    if (!body.name) {
        alert("Name is required");
        return;
    }
    let status = document.getElementById("dbConnStatus");
    if (status) {
        status.textContent = "Saving...";
    }
    // Rename: delete old name after save if changed
    let previousName = oldDatabaseConnectionName;
    $.ajax({
        url: API_BASE + "metadata/" + DB_CONNECTION_METADATA_KEY + "/",
        type: "POST",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(body),
        dataType: "text",
        success: function (savedName) {
            if (previousName && previousName !== savedName) {
                $.ajax({
                    url: API_BASE + "metadata/" + DB_CONNECTION_METADATA_KEY + "/"
                        + encodeURIComponent(previousName),
                    type: "DELETE",
                    dataType: "text",
                    async: false
                });
            }
            oldDatabaseConnectionName = savedName;
            databaseConnectionNames = null;
            if (status) {
                status.textContent = "Saved: " + savedName;
            }
            // Re-enable delete after first save
            let del = document.getElementById("dbConnDeleteBtn");
            if (del) {
                del.disabled = false;
            }
        },
        error: function (xhr) {
            if (status) {
                status.textContent = "";
            }
            alert("Save failed: " + (xhr.responseText || xhr.status));
        }
    });
}

function testDatabaseConnection() {
    let body = collectDatabaseConnectionForm();
    if (!body.name) {
        body.name = "test";
    }
    let status = document.getElementById("dbConnStatus");
    if (status) {
        status.textContent = "Testing...";
    }
    $.ajax({
        url: API_BASE + "metadata/database-connection/test/",
        type: "POST",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(body),
        dataType: "text",
        success: function (msg) {
            if (status) {
                status.textContent = msg;
            } else {
                alert(msg);
            }
        },
        error: function (xhr) {
            let msg = xhr.responseText || xhr.status;
            if (status) {
                status.textContent = "Test failed: " + msg;
            } else {
                alert("Test failed: " + msg);
            }
        }
    });
}

function deleteDatabaseConnection() {
    let name = oldDatabaseConnectionName
        || (document.getElementById("dbConnName")
            ? document.getElementById("dbConnName").value.trim()
            : "");
    if (!name) {
        alert("Nothing to delete");
        return;
    }
    if (!confirm("Delete database connection '" + name + "'?")) {
        return;
    }
    $.ajax({
        url: API_BASE + "metadata/" + DB_CONNECTION_METADATA_KEY + "/" + encodeURIComponent(name),
        type: "DELETE",
        dataType: "text",
        success: function () {
            databaseConnectionNames = null;
            oldDatabaseConnectionName = null;
            editDatabaseConnectionsList();
        },
        error: function (xhr, status, error) {
            showAjaxError("Failed to delete database connection '" + name + "'", xhr, status, error);
        }
    });
}

function closeDatabaseConnection() {
    databaseConnectionJson = null;
    oldDatabaseConnectionName = null;
    if (isAdminMetadataHost() && isAdminMetadataCatalogListOpen()) {
        exitAdminMetadataCatalog("overview");
        return;
    }
    setSidePanelOpen(false);
}

// ---------------------------------------------------------------------------
// Theme metadata admin (catalog list + minimal form)
// ---------------------------------------------------------------------------

const THEME_METADATA_KEY = "theme";
let themeAdminJson = null;
let oldThemeAdminName = null;

/**
 * Open the side panel with theme metadata (filter, path groups, delete).
 */
function editThemesList() {
    let ML = window.HMetadataList;
    if (!ML) {
        alert("Metadata list helper not loaded");
        return;
    }
    let summaries = getThemeSummaries();
    themeNames = [""].concat(summaries.map(function (r) {
        return r && r.name ? r.name : "";
    }).filter(Boolean));

    function listOptions(filterQuery) {
        return {
            rows: summaries,
            filterQuery: filterQuery || "",
            listId: "themeListTable",
            emptyMessage: "No themes yet",
            iconForRow: function () {
                return {url: ML.staticImage("theme.svg"), title: "Theme"};
            },
            actions: [
                {
                    id: "copy",
                    iconUrl: ML.staticImage("copy.svg"),
                    title: "Copy as…"
                },
                {
                    id: "delete",
                    iconUrl: ML.staticImage("delete.svg"),
                    title: "Delete theme"
                }
            ]
        };
    }

    let createHtml = ""
        + "<button type=\"button\" id=\"createThemeBtn\" "
        + "class=\"home-btn home-btn-primary\">New theme</button> "
        + "<button type=\"button\" id=\"generateLightThemeBtn\" class=\"home-btn\" "
        + "title=\"Save/overwrite catalog theme 'Default'\">Generate Default (light)</button> "
        + "<button type=\"button\" id=\"generateDarkThemeBtn\" class=\"home-btn\" "
        + "title=\"Save/overwrite catalog theme 'Default Dark' (PDI assessment palette)\">"
        + "Generate Default Dark</button>";
    let footerHtml = "<button type=\"button\" id=\"closeThemeListBtn\" class=\"home-btn\">Close</button>";

    let html = ML.buildMetadataListPanelHtml(Object.assign({
        title: "Themes",
        hint: "Catalog themes: identity, base/chart fonts and colors, and series palette. "
            + "Presentations reference light/dark theme names. Use Generate for built-in defaults.",
        createHtml: createHtml,
        footerHtml: footerHtml,
        filterId: "themeListFilter",
        bodyId: "metaListBody"
    }, listOptions("")));

    setSidePanelOpen(true, {withPreview: false});
    let editArea = document.getElementById("editArea");
    editArea.innerHTML = html;

    let byName = ML.rowsByNameMap(summaries);
    ML.bindMetadataListHandlers(editArea, {
        primary: function (name) {
            editThemeByName(name);
        },
        copy: function (name) {
            ML.copyMetadataAs("theme", name, {
                existingRows: summaries,
                onSuccess: function () {
                    editThemesList();
                }
            });
        },
        delete: function (name) {
            deleteThemeByName(name);
        }
    }, byName);

    ML.bindMetadataListFilter(
        document.getElementById("themeListFilter"),
        document.getElementById("metaListBody"),
        function (q) {
            return listOptions(q);
        }
    );

    let createBtn = document.getElementById("createThemeBtn");
    if (createBtn) {
        createBtn.onclick = function () {
            createNewTheme();
        };
    }
    let genLight = document.getElementById("generateLightThemeBtn");
    if (genLight) {
        genLight.onclick = function () {
            generateBuiltinTheme("light");
        };
    }
    let genDark = document.getElementById("generateDarkThemeBtn");
    if (genDark) {
        genDark.onclick = function () {
            generateBuiltinTheme("dark");
        };
    }
    let closeBtn = document.getElementById("closeThemeListBtn");
    if (closeBtn) {
        closeBtn.onclick = function () {
            closeThemeAdmin();
        };
    }
}

/**
 * @param {"light"|"dark"} which
 * @returns {object} theme metadata document
 */
function buildBuiltinThemeDocument(which) {
    function rgb(hex) {
        let h = String(hex || "").replace("#", "");
        if (h.length === 3) {
            h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
        }
        let n = parseInt(h, 16);
        return {r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255};
    }
    function font(name, size, bold, italic) {
        return {
            fontName: name,
            fontSize: String(size),
            bold: !!bold,
            italic: !!italic
        };
    }
    if (which === "dark") {
        // Palette from pdi-codebase-assessment.html
        return {
            name: "Default Dark",
            description:
                "Built-in dark theme (PDI assessment palette): deep navy #0b1220, blue/cyan accents",
            virtualPath: "",
            colors: [
                rgb("#3b82f6"),
                rgb("#22d3ee"),
                rgb("#a78bfa"),
                rgb("#34d399"),
                rgb("#fbbf24"),
                rgb("#f87171"),
                rgb("#93c5fd"),
                rgb("#67e8f9")
            ],
            backgroundColor: rgb("#0b1220"),
            defaultColor: rgb("#e8eef9"),
            defaultFont: font("Arial", 12, false, false),
            borderColor: rgb("#1b2740"),
            horizontalDimensionsFont: font("Arial", 12, true, false),
            horizontalDimensionsColor: rgb("#e8eef9"),
            verticalDimensionsFont: font("Arial", 12, true, false),
            verticalDimensionsColor: rgb("#e8eef9"),
            factsFont: font("Hack", 12, false, false),
            factsColor: rgb("#e8eef9"),
            titleFont: font("Arial", 10, true, true),
            titleColor: rgb("#9aa8c0"),
            axisColor: rgb("#9aa8c0"),
            gridColor: rgb("#1b2740")
        };
    }
    return {
        name: "Default",
        description: "Built-in light theme for presentations",
        virtualPath: "",
        colors: [
            rgb("#003f5c"),
            rgb("#2f4b7c"),
            rgb("#665191"),
            rgb("#a05195"),
            rgb("#d45087"),
            rgb("#f95d6a"),
            rgb("#ff7c43"),
            rgb("#ffa600")
        ],
        backgroundColor: rgb("#ffffff"),
        defaultColor: rgb("#000000"),
        defaultFont: font("Arial", 12, false, false),
        borderColor: rgb("#f0f0f0"),
        horizontalDimensionsFont: font("Arial", 12, true, false),
        horizontalDimensionsColor: rgb("#000000"),
        verticalDimensionsFont: font("Arial", 12, true, false),
        verticalDimensionsColor: rgb("#000000"),
        factsFont: font("Hack", 12, false, false),
        factsColor: rgb("#000000"),
        titleFont: font("Arial", 10, true, true),
        titleColor: rgb("#c8c8c8"),
        axisColor: rgb("#000000"),
        gridColor: rgb("#c8c8c8")
    };
}

/**
 * Save Default or Default Dark into the theme catalog (overwrites if present).
 * @param {"light"|"dark"} which
 */
function generateBuiltinTheme(which) {
    let body = buildBuiltinThemeDocument(which);
    let label = body.name;
    let ok = window.confirm(
        "Save theme \"" + label + "\" to the catalog?\n\n"
        + "If it already exists it will be overwritten."
    );
    if (!ok) {
        return;
    }
    $.ajax({
        url: API_BASE + "metadata/" + THEME_METADATA_KEY + "/",
        type: "POST",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(body),
        dataType: "text",
        success: function (savedName) {
            themeNames = null;
            alert("Saved theme: " + (savedName || label));
            editThemesList();
        },
        error: function (xhr) {
            if (typeof showAjaxError === "function") {
                showAjaxError("Failed to generate theme \"" + label + "\"", xhr);
            } else {
                alert("Failed to generate theme: " + (xhr.responseText || xhr.status));
            }
        }
    });
}

function getThemeSummaries() {
    if (window.HMetadataList) {
        let rows = HMetadataList.fetchMetadataSummary(THEME_METADATA_KEY);
        if (rows) {
            return rows;
        }
    }
    return [];
}

function createNewTheme() {
    oldThemeAdminName = null;
    // Start from built-in light defaults so all properties are present for editing
    let base = buildBuiltinThemeDocument("light");
    base.name = "New Theme";
    base.description = "";
    base.virtualPath = "";
    themeAdminJson = base;
    openThemeAdminForm(themeAdminJson);
}

function editThemeByName(name) {
    $.ajax({
        url: API_BASE + "metadata/" + THEME_METADATA_KEY + "/" + encodeURIComponent(name),
        type: "GET",
        dataType: "json",
        success: function (data) {
            themeAdminJson = data || {};
            oldThemeAdminName = data["name"] || name;
            openThemeAdminForm(themeAdminJson);
        },
        error: function (xhr) {
            alert("Failed to load theme '" + name + "': "
                + (xhr.responseText || xhr.status));
        }
    });
}

/** Normalize theme color object or hex to #rrggbb for {@code <input type="color">}. */
function themeAdminColorToHex(c) {
    if (c == null) {
        return "#000000";
    }
    if (typeof c === "string") {
        let p = hexToRgb(c);
        return p ? rgbToHex(p.r, p.g, p.b) : "#000000";
    }
    return rgbToHex(c);
}

/**
 * Append a color property row to the theme admin form HTML parts.
 * @param {string[]} parts
 * @param {string} id
 * @param {string} label
 * @param {*} color
 */
function themeAdminAppendColorRow(parts, id, label, color) {
    let hex = themeAdminColorToHex(color);
    parts.push('<div class="form-field-row">');
    parts.push('<span class="form-field-check" aria-hidden="true">'
        + '<span class="form-field-check-spacer"></span></span>');
    parts.push('<label class="form-field-label" for="' + id + '">'
        + escapeHtmlText(label) + "</label>");
    parts.push('<span class="form-field-control">');
    parts.push('<input type="color" id="' + id + '" value="' + hex + '">');
    parts.push("</span></div>");
}

/**
 * Append a font property row (name, size, bold, italic).
 * @param {string[]} parts
 * @param {string} prefix DOM id prefix (e.g. themeAdminDefaultFont)
 * @param {string} label
 * @param {object|null} font
 */
function themeAdminAppendFontRow(parts, prefix, label, font) {
    font = font || {};
    let name = font.fontName != null ? font.fontName : (font.name || "");
    let size = font.fontSize != null ? font.fontSize
        : (font.size != null ? font.size : "12");
    let bold = !!font.bold;
    let italic = !!font.italic;
    parts.push('<div class="form-field-row form-field-row-font">');
    parts.push('<span class="form-field-check" aria-hidden="true">'
        + '<span class="form-field-check-spacer"></span></span>');
    parts.push('<label class="form-field-label" for="' + prefix + 'Name">'
        + escapeHtmlText(label) + "</label>");
    parts.push('<span class="form-field-control">');
    parts.push('<input type="text" id="' + prefix + 'Name" class="form-field-font-name" value="'
        + escapeHtmlAttribute(String(name)) + '">');
    parts.push('<label class="form-field-inline-label" for="' + prefix + 'Size">size</label>');
    parts.push('<input type="text" id="' + prefix + 'Size" class="form-field-font-size" value="'
        + escapeHtmlAttribute(String(size)) + '">');
    parts.push('<label class="form-field-inline-label" for="' + prefix + 'Bold">bold?</label>');
    parts.push('<input type="checkbox" id="' + prefix + 'Bold"' + (bold ? " checked" : "") + ">");
    parts.push('<label class="form-field-inline-label" for="' + prefix + 'Italic">italic?</label>');
    parts.push('<input type="checkbox" id="' + prefix + 'Italic"'
        + (italic ? " checked" : "") + ">");
    parts.push("</span></div>");
}

/**
 * Read a font object from theme admin form controls.
 * @param {string} prefix
 * @returns {{fontName:string,fontSize:string,bold:boolean,italic:boolean}}
 */
function themeAdminReadFont(prefix) {
    let nameEl = document.getElementById(prefix + "Name");
    let sizeEl = document.getElementById(prefix + "Size");
    let boldEl = document.getElementById(prefix + "Bold");
    let italicEl = document.getElementById(prefix + "Italic");
    return {
        fontName: nameEl ? (nameEl.value || "").trim() : "",
        fontSize: sizeEl ? String(sizeEl.value || "").trim() : "12",
        bold: !!(boldEl && boldEl.checked),
        italic: !!(italicEl && italicEl.checked)
    };
}

/**
 * Read an RGB color from a color input; returns null if element missing.
 * @param {string} id
 * @returns {{r:number,g:number,b:number}|null}
 */
function themeAdminReadColor(id) {
    let el = document.getElementById(id);
    if (!el || !el.value) {
        return null;
    }
    return hexToRgb(el.value);
}

/**
 * Build one series-palette color row (swatch + remove).
 * @param {string} hex
 * @param {number} index
 * @returns {string}
 */
function themeAdminPaletteRowHtml(hex, index) {
    let h = themeAdminColorToHex(hex);
    return '<div class="theme-admin-palette-row" data-palette-index="' + index + '">'
        + '<input type="color" class="theme-admin-palette-color" value="' + h + '" '
        + 'title="Series color ' + (index + 1) + '">'
        + '<button type="button" class="home-btn home-btn-small theme-admin-palette-remove" '
        + 'title="Remove this color">Remove</button>'
        + "</div>";
}

function themeAdminBindPaletteHandlers(root) {
    let list = root.querySelector("#themeAdminPaletteColors");
    let addBtn = root.querySelector("#themeAdminColorAdd");
    if (!list) {
        return;
    }
    if (addBtn) {
        addBtn.onclick = function () {
            let idx = list.querySelectorAll(".theme-admin-palette-row").length;
            list.insertAdjacentHTML("beforeend", themeAdminPaletteRowHtml("#808080", idx));
            themeAdminBindPaletteRemove(list);
        };
    }
    themeAdminBindPaletteRemove(list);
}

function themeAdminBindPaletteRemove(list) {
    let buttons = list.querySelectorAll(".theme-admin-palette-remove");
    for (let i = 0; i < buttons.length; i++) {
        buttons[i].onclick = function (ev) {
            let row = ev.target.closest(".theme-admin-palette-row");
            if (row && row.parentNode) {
                row.parentNode.removeChild(row);
            }
        };
    }
}

function openThemeAdminForm(json) {
    setSidePanelOpen(true, {withPreview: false});
    json = json || {};
    let parts = [];
    parts.push('<div class="form-action-bar">');
    parts.push('<button type="button" id="themeAdminSaveBtn" title="Save theme">Save</button>');
    parts.push('<button type="button" id="themeAdminDeleteBtn" title="Delete theme">Delete</button>');
    parts.push('<button type="button" id="themeAdminBackBtn" title="Back to list">Back</button>');
    parts.push('<button type="button" id="themeAdminCloseBtn" title="Close panel">Close</button>');
    parts.push("</div>");
    parts.push('<div class="theme-admin-form property-form-area">');
    parts.push("<h3>Theme properties</h3>");
    parts.push('<p class="editor-hint">Edit all catalog theme colors and fonts used by '
        + "components (labels, tables, charts, crosstabs).</p>");

    // ── Identity ──────────────────────────────────────────────────────
    parts.push('<button type="button" class="collapsible">Identity</button>');
    parts.push('<div class="content" style="display:block">');
    parts.push('<div class="form-field-row">');
    parts.push('<span class="form-field-check" aria-hidden="true">'
        + '<span class="form-field-check-spacer"></span></span>');
    parts.push('<label class="form-field-label" for="themeAdminName">Name</label>');
    parts.push('<span class="form-field-control">');
    parts.push('<input type="text" id="themeAdminName" class="form-field-input" value="'
        + escapeHtmlAttribute(json["name"] || "") + '">');
    parts.push("</span></div>");

    parts.push('<div class="form-field-row form-field-row-multiline">');
    parts.push('<span class="form-field-check" aria-hidden="true">'
        + '<span class="form-field-check-spacer"></span></span>');
    parts.push('<label class="form-field-label" for="themeAdminDescription">Description</label>');
    parts.push('<span class="form-field-control">');
    parts.push('<textarea id="themeAdminDescription" class="form-field-input" rows="2">'
        + escapeHtmlText(json["description"] || "") + "</textarea>");
    parts.push("</span></div>");

    parts.push('<div class="form-field-row">');
    parts.push('<span class="form-field-check" aria-hidden="true">'
        + '<span class="form-field-check-spacer"></span></span>');
    parts.push('<label class="form-field-label" for="themeAdminVirtualPath">Virtual path</label>');
    parts.push('<span class="form-field-control">');
    parts.push('<input type="text" id="themeAdminVirtualPath" class="form-field-input" '
        + 'placeholder="e.g. brand/dark" value="'
        + escapeHtmlAttribute(json["virtualPath"] || "") + '">');
    parts.push("</span></div>");
    parts.push("</div>");

    // ── Base colors & font ────────────────────────────────────────────
    parts.push('<button type="button" class="collapsible">Base colors &amp; font</button>');
    parts.push('<div class="content" style="display:block">');
    themeAdminAppendColorRow(parts, "themeAdminBackgroundColor", "Background color",
        json.backgroundColor);
    themeAdminAppendColorRow(parts, "themeAdminDefaultColor", "Default (ink) color",
        json.defaultColor);
    themeAdminAppendColorRow(parts, "themeAdminBorderColor", "Border color",
        json.borderColor);
    themeAdminAppendFontRow(parts, "themeAdminDefaultFont", "Default font",
        json.defaultFont);
    parts.push("</div>");

    // ── Chart / table / crosstab ───────────────────────────────────────
    parts.push('<button type="button" class="collapsible">Charts, tables &amp; crosstabs</button>');
    parts.push('<div class="content" style="display:block">');
    themeAdminAppendFontRow(parts, "themeAdminTitleFont", "Title font", json.titleFont);
    themeAdminAppendColorRow(parts, "themeAdminTitleColor", "Title color", json.titleColor);
    themeAdminAppendFontRow(parts, "themeAdminHorizontalDimensionsFont",
        "Horizontal dimensions font", json.horizontalDimensionsFont);
    themeAdminAppendColorRow(parts, "themeAdminHorizontalDimensionsColor",
        "Horizontal dimensions color", json.horizontalDimensionsColor);
    themeAdminAppendFontRow(parts, "themeAdminVerticalDimensionsFont",
        "Vertical dimensions font", json.verticalDimensionsFont);
    themeAdminAppendColorRow(parts, "themeAdminVerticalDimensionsColor",
        "Vertical dimensions color", json.verticalDimensionsColor);
    themeAdminAppendFontRow(parts, "themeAdminFactsFont", "Facts font", json.factsFont);
    themeAdminAppendColorRow(parts, "themeAdminFactsColor", "Facts color", json.factsColor);
    themeAdminAppendColorRow(parts, "themeAdminAxisColor", "Axis color", json.axisColor);
    themeAdminAppendColorRow(parts, "themeAdminGridColor", "Grid color", json.gridColor);
    parts.push("</div>");

    // ── Series palette ────────────────────────────────────────────────
    parts.push('<button type="button" class="collapsible">Series palette</button>');
    parts.push('<div class="content" style="display:block">');
    parts.push('<p class="editor-hint">Stable series colors for charts (bars, lines, pie slices, '
        + "Gantt). Order is the palette cycle.</p>");
    parts.push('<div class="form-field-list-block theme-admin-palette">');
    parts.push('<div class="list-field-header">');
    parts.push("<label>Colors</label>");
    parts.push('<span class="list-field-toolbar">');
    parts.push('<button type="button" class="home-btn home-btn-small" id="themeAdminColorAdd" '
        + 'title="Add series color">Add color</button>');
    parts.push("</span></div>");
    parts.push('<div id="themeAdminPaletteColors" class="theme-admin-palette-list">');
    let palette = Array.isArray(json.colors) ? json.colors : [];
    for (let i = 0; i < palette.length; i++) {
        parts.push(themeAdminPaletteRowHtml(palette[i], i));
    }
    parts.push("</div></div>");
    parts.push("</div>");

    parts.push('<p id="themeAdminStatus" class="editor-hint"></p>');
    parts.push("</div>"); // theme-admin-form

    let editArea = document.getElementById("editArea");
    editArea.innerHTML = parts.join("\n");

    // Collapsible sections (same pattern as component forms)
    let coll = editArea.getElementsByClassName("collapsible");
    for (let c = 0; c < coll.length; c++) {
        coll[c].addEventListener("click", function () {
            this.classList.toggle("active");
            let content = this.nextElementSibling;
            if (!content) {
                return;
            }
            if (content.style.display === "block") {
                content.style.display = "none";
            } else {
                content.style.display = "block";
            }
        });
    }

    themeAdminBindPaletteHandlers(editArea);

    document.getElementById("themeAdminSaveBtn").onclick = function () {
        saveThemeAdmin();
    };
    document.getElementById("themeAdminDeleteBtn").onclick = function () {
        if (oldThemeAdminName) {
            deleteThemeByName(oldThemeAdminName);
        }
    };
    document.getElementById("themeAdminBackBtn").onclick = function () {
        editThemesList();
    };
    document.getElementById("themeAdminCloseBtn").onclick = function () {
        closeThemeAdmin();
    };
    if (!oldThemeAdminName) {
        document.getElementById("themeAdminDeleteBtn").disabled = true;
    }
}

function collectThemeAdminForm() {
    let body = themeAdminJson ? JSON.parse(JSON.stringify(themeAdminJson)) : {};
    body.name = (document.getElementById("themeAdminName").value || "").trim();
    body.description = document.getElementById("themeAdminDescription").value || "";
    body.virtualPath = (document.getElementById("themeAdminVirtualPath").value || "").trim();

    body.backgroundColor = themeAdminReadColor("themeAdminBackgroundColor");
    body.defaultColor = themeAdminReadColor("themeAdminDefaultColor");
    body.borderColor = themeAdminReadColor("themeAdminBorderColor");
    body.defaultFont = themeAdminReadFont("themeAdminDefaultFont");

    body.titleFont = themeAdminReadFont("themeAdminTitleFont");
    body.titleColor = themeAdminReadColor("themeAdminTitleColor");
    body.horizontalDimensionsFont = themeAdminReadFont("themeAdminHorizontalDimensionsFont");
    body.horizontalDimensionsColor = themeAdminReadColor("themeAdminHorizontalDimensionsColor");
    body.verticalDimensionsFont = themeAdminReadFont("themeAdminVerticalDimensionsFont");
    body.verticalDimensionsColor = themeAdminReadColor("themeAdminVerticalDimensionsColor");
    body.factsFont = themeAdminReadFont("themeAdminFactsFont");
    body.factsColor = themeAdminReadColor("themeAdminFactsColor");
    body.axisColor = themeAdminReadColor("themeAdminAxisColor");
    body.gridColor = themeAdminReadColor("themeAdminGridColor");

    let colors = [];
    let swatches = document.querySelectorAll(
        "#themeAdminPaletteColors .theme-admin-palette-color");
    for (let i = 0; i < swatches.length; i++) {
        let rgb = hexToRgb(swatches[i].value);
        if (rgb) {
            colors.push(rgb);
        }
    }
    body.colors = colors;
    return body;
}

function saveThemeAdmin() {
    let body = collectThemeAdminForm();
    if (!body.name) {
        alert("Name is required");
        return;
    }
    let status = document.getElementById("themeAdminStatus");
    if (status) {
        status.textContent = "Saving...";
    }
    let previousName = oldThemeAdminName;
    $.ajax({
        url: API_BASE + "metadata/" + THEME_METADATA_KEY + "/",
        type: "POST",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(body),
        dataType: "text",
        success: function (savedName) {
            if (previousName && previousName !== savedName) {
                $.ajax({
                    url: API_BASE + "metadata/" + THEME_METADATA_KEY + "/"
                        + encodeURIComponent(previousName),
                    type: "DELETE",
                    dataType: "text",
                    async: false
                });
            }
            oldThemeAdminName = savedName;
            themeAdminJson = body;
            themeAdminJson.name = savedName;
            themeNames = null;
            if (status) {
                status.textContent = "Saved: " + savedName;
            }
            let del = document.getElementById("themeAdminDeleteBtn");
            if (del) {
                del.disabled = false;
            }
        },
        error: function (xhr) {
            if (status) {
                status.textContent = "";
            }
            alert("Save failed: " + (xhr.responseText || xhr.status));
        }
    });
}

function deleteThemeByName(name) {
    if (!name) {
        return;
    }
    let ok = window.confirm(
        "Delete theme \"" + name + "\"?\n\n"
        + "Presentations that reference it may fall back to defaults."
    );
    if (!ok) {
        return;
    }
    $.ajax({
        url: API_BASE + "metadata/" + THEME_METADATA_KEY + "/" + encodeURIComponent(name),
        type: "DELETE",
        dataType: "text",
        success: function () {
            themeNames = null;
            if (oldThemeAdminName === name) {
                oldThemeAdminName = null;
                themeAdminJson = null;
            }
            editThemesList();
        },
        error: function (xhr, status, error) {
            showAjaxError("Failed to delete theme '" + name + "'", xhr, status, error);
        }
    });
}

function closeThemeAdmin() {
    themeAdminJson = null;
    oldThemeAdminName = null;
    if (isAdminMetadataHost() && isAdminMetadataCatalogListOpen()) {
        exitAdminMetadataCatalog("overview");
        return;
    }
    setSidePanelOpen(false);
}

function toInteger(value) {
    if (value === null) {
        return null;
    }
    return parseInt(value);
}

/**
 * Common font names suggested on FONT property fields (datalist).
 * Includes one fixed-width face (Courier New) for labels/tables/code-style text.
 */
const COMMON_FONT_NAMES = [
    "Arial",
    "Helvetica",
    "Times New Roman",
    "Georgia",
    "Verdana",
    "Tahoma",
    "Calibri",
    "Trebuchet MS",
    "Garamond",
    "Courier New"
];

/** DOM id of the shared font-name suggestion list (one per page). */
const FONT_NAME_DATALIST_ID = "hopper-common-font-names";

/**
 * Ensure a document-level {@code <datalist>} of {@link COMMON_FONT_NAMES} exists
 * so font name inputs can reference it via {@code list="..."}.
 * @returns {string} datalist element id
 */
function ensureFontNameSuggestionsDatalist() {
    let existing = document.getElementById(FONT_NAME_DATALIST_ID);
    if (existing) {
        return FONT_NAME_DATALIST_ID;
    }
    let list = document.createElement("datalist");
    list.id = FONT_NAME_DATALIST_ID;
    for (let i = 0; i < COMMON_FONT_NAMES.length; i++) {
        let opt = document.createElement("option");
        opt.value = COMMON_FONT_NAMES[i];
        list.appendChild(opt);
    }
    document.body.appendChild(list);
    return FONT_NAME_DATALIST_ID;
}

function setFont(iComponent, jsonId, setId, idPrefix) {
    try {
        let srcFont = iComponent[jsonId];
        if (srcFont !== null) {
            document.getElementById(setId).checked = true;
            let nameEl = document.getElementById(idPrefix + "Name");
            if (nameEl) {
                nameEl.value = srcFont["fontName"];
                // Attach suggestions if this is a plain text font field
                let listId = ensureFontNameSuggestionsDatalist();
                if (!nameEl.getAttribute("list")) {
                    nameEl.setAttribute("list", listId);
                    nameEl.setAttribute("autocomplete", "off");
                    nameEl.setAttribute("placeholder", "Font name");
                }
            }
            document.getElementById(idPrefix + "Size").value = srcFont["fontSize"];
            document.getElementById(idPrefix + "Bold").checked = srcFont["bold"];
            document.getElementById(idPrefix + "Italic").checked = srcFont["italic"];
        }
    } catch (e) {
        throw "Error setting font data for jsonId='" + jsonId + "', setId='" + setId + "', idPrefix='" + idPrefix + " : " + e;
    }
}

function getFont(iComponent, jsonId, setId, idPrefix) {
    let font = null;
    if (document.getElementById(setId).checked) {
        font = {
            "fontName": document.getElementById(idPrefix + "Name").value,
            "fontSize": toInteger(document.getElementById(idPrefix + "Size").value),
            "bold": document.getElementById(idPrefix + "Bold").checked,
            "italic": document.getElementById(idPrefix + "Italic").checked
        };
    }
    iComponent[jsonId] = font;
}

function setColor(iComponent, jsonId, setId, colorId, defaultColor) {
    try {
        let colorEl = document.getElementById(colorId);
        let setEl = document.getElementById(setId);
        if (!colorEl) {
            return;
        }
        let color = iComponent ? iComponent[jsonId] : null;
        let hex = defaultColor || "#000000";
        if (color !== null && color !== undefined) {
            if (typeof color === "string") {
                // Already a hex string in metadata
                let parsed = hexToRgb(color);
                hex = parsed ? rgbToHex(parsed.r, parsed.g, parsed.b) : hex;
            } else {
                hex = rgbToHex(color);
            }
            if (setEl) {
                setEl.checked = true;
            }
        } else if (setEl) {
            setEl.checked = false;
        }
        // input[type=color] requires #rrggbb; reject invalid values
        if (!/^#[0-9a-fA-F]{6}$/.test(hex)) {
            hex = defaultColor || "#000000";
        }
        colorEl.value = hex;
        // If we have this flag in the component plugin JSON, set the checkbox.
        //
        let flag = iComponent ? iComponent[setId] : null;
        if (flag !== null && flag !== undefined && setEl) {
            setEl.checked = !!flag;
        }
    } catch (e) {
        throw "Error setting color data for jsonId='" + jsonId
        + "', setId='" + setId
        + "', colorId='" + colorId
        + "', JSON=" + JSON.stringify(iComponent) + " : " + e;
    }
}

function getColor(iComponent, jsonId, setId, colorId) {
    let color = null;
    let checked = document.getElementById(setId).checked;
    if (checked) {
        color = hexToRgb(document.getElementById(colorId).value);
    }
    iComponent[jsonId] = color;

    if (iComponent[setId] !== null) {
        iComponent[setId] = checked;
    }
}

function setElement(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    let el = document.getElementById(elementId);
    if (!el) {
        return;
    }
    let value = json[jsonId];
    el.value = (value === null || value === undefined) ? "" : value;
}

function getElement(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    let el = document.getElementById(elementId);
    if (!el) {
        return;
    }
    let value = el.value;
    // Optional metadata selectors: empty / "(none)" must be null, not "".
    // Blank themeName makes render lookupTheme("") fail ("no default font set").
    if (value === "" && isOptionalEmptyStringField(jsonId)) {
        json[jsonId] = null;
    } else {
        json[jsonId] = value;
    }
}

/**
 * Field names where empty form value means "unset" (null), not an empty string.
 * themeName "" is especially harmful: PresentationRenderContext treats it as a
 * named theme and fails instead of using the presentation default.
 */
function isOptionalEmptyStringField(jsonId) {
    if (!jsonId) {
        return false;
    }
    switch (jsonId) {
        case "themeName":
        case "sourceConnectorName":
        case "rotation":
        case "transparency":
        case "customHtml":
        case "formatMask":
        case "lineWidth":
        case "horizontalLabelInterval":
        case "componentName": // layout reference: empty = page, not a component
            return true;
        default:
            return false;
    }
}

/**
 * Recursively turn "" into null for optional fields after form save (covers nested
 * plugin maps, layout sides, list items).
 */
function normalizeOptionalEmptyStrings(obj) {
    if (obj === null || obj === undefined) {
        return;
    }
    if (Array.isArray(obj)) {
        for (let i = 0; i < obj.length; i++) {
            normalizeOptionalEmptyStrings(obj[i]);
        }
        return;
    }
    if (typeof obj !== "object") {
        return;
    }
    for (let key of Object.keys(obj)) {
        let val = obj[key];
        if (val === "" && isOptionalEmptyStringField(key)) {
            obj[key] = null;
        } else if (val !== null && typeof val === "object") {
            normalizeOptionalEmptyStrings(val);
        }
    }
}

/**
 * Load a HSize {width,height} into idWidth / idHeight inputs.
 */
function setSize(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    let size = json[jsonId];
    let widthEl = document.getElementById(elementId + "Width");
    let heightEl = document.getElementById(elementId + "Height");
    if (!widthEl || !heightEl) {
        return;
    }
    if (size === null || size === undefined) {
        widthEl.value = "";
        heightEl.value = "";
        return;
    }
    widthEl.value = (size["width"] === null || size["width"] === undefined) ? "" : size["width"];
    heightEl.value = (size["height"] === null || size["height"] === undefined) ? "" : size["height"];
}

/**
 * Save idWidth / idHeight into a HSize object, or null when both empty / zero.
 */
function getSize(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    let widthEl = document.getElementById(elementId + "Width");
    let heightEl = document.getElementById(elementId + "Height");
    if (!widthEl || !heightEl) {
        json[jsonId] = null;
        return;
    }
    let widthStr = (widthEl.value || "").trim();
    let heightStr = (heightEl.value || "").trim();
    if (widthStr === "" && heightStr === "") {
        json[jsonId] = null;
        return;
    }
    let width = widthStr === "" ? 0 : toInteger(widthStr);
    let height = heightStr === "" ? 0 : toInteger(heightStr);
    json[jsonId] = {"width": width, "height": height};
}

function getElementInteger(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    json[jsonId] = toInteger(document.getElementById(elementId).value);
}

function setChecked(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    document.getElementById(elementId).checked = json[jsonId];
}

function getChecked(json, elementId, jsonId) {
    if (jsonId === undefined) {
        jsonId = elementId;
    }
    json[jsonId] = document.getElementById(elementId).checked;
}

function setLayout(componentJson, name) {
    let layout = componentJson["layout"] ? componentJson["layout"][name] : null;
    let isEnabled = layout != null;
    let en = document.getElementById(name + "Enabled");
    if (!en) {
        return;
    }
    en.checked = isEnabled;
    if (isEnabled) {
        let obj = document.getElementById(name + "ObjectName");
        if (obj) {
            // null / missing componentName = page
            obj.value = layout["componentName"] != null ? layout["componentName"] : "";
        }
        let off = document.getElementById(name + "Offset");
        if (off) {
            off.value = "" + (layout["offset"] != null ? layout["offset"] : 0);
        }
        let pct = document.getElementById(name + "Percentage");
        if (pct) {
            pct.value = "" + (layout["percentage"] != null ? layout["percentage"] : 0);
        }
        let al = document.getElementById(name + "Alignment");
        if (al) {
            al.value = "" + (layout["alignment"] != null ? layout["alignment"] : "DEFAULT");
        }
    }
}

/**
 * Map UI values to HAttachment.Alignment. Content vertical uses MIDDLE;
 * layout attachment uses CENTER — older forms may still submit MIDDLE.
 */
function normalizeLayoutAlignment(value) {
    if (value == null || value === "") {
        return "DEFAULT";
    }
    if (value === "MIDDLE") {
        return "CENTER";
    }
    return value;
}

function getLayout(componentJson, name) {
    let layout = null;
    let en = document.getElementById(name + "Enabled");
    let isEnabled = en && en.checked;
    if (isEnabled) {
        let objName = document.getElementById(name + "ObjectName").value;
        if (objName === "") {
            objName = null; // page reference
        }
        layout = {
            "componentName": objName,
            "offset": parseInt(document.getElementById(name + "Offset").value) || 0,
            "percentage": parseInt(document.getElementById(name + "Percentage").value) || 0,
            "alignment": normalizeLayoutAlignment(document.getElementById(name + "Alignment").value)
        };
    }
    if (!componentJson["layout"]) {
        componentJson["layout"] = {};
    }
    componentJson["layout"][name] = layout;
}

/**
 * Apply a layout side preset in the property form (page = empty object name).
 * Alignments match HLayout.fullPage() / topLeftPage().
 */
function setLayoutSideForm(side, enabled, componentName, offset, percentage, alignment) {
    let en = document.getElementById(side + "Enabled");
    if (!en) {
        return;
    }
    en.checked = !!enabled;
    if (!enabled) {
        return;
    }
    let obj = document.getElementById(side + "ObjectName");
    if (obj) {
        obj.value = componentName != null ? componentName : "";
    }
    let off = document.getElementById(side + "Offset");
    if (off) {
        off.value = "" + (offset != null ? offset : 0);
    }
    let pct = document.getElementById(side + "Percentage");
    if (pct) {
        pct.value = "" + (percentage != null ? percentage : 0);
    }
    let al = document.getElementById(side + "Alignment");
    if (al) {
        al.value = alignment || "DEFAULT";
    }
}

/** HLayout.fullPage(): left/top/right/bottom → page, offset 0 */
function applyLayoutFullPage() {
    setLayoutSideForm("left", true, null, 0, 0, "LEFT");
    setLayoutSideForm("top", true, null, 0, 0, "TOP");
    setLayoutSideForm("right", true, null, 0, 0, "RIGHT");
    setLayoutSideForm("bottom", true, null, 0, 0, "BOTTOM");
}

/** HLayout.topLeftPage(): left/top → page, offset 0; clear right/bottom */
function applyLayoutTopLeft() {
    setLayoutSideForm("left", true, null, 0, 0, "LEFT");
    setLayoutSideForm("top", true, null, 0, 0, "TOP");
    setLayoutSideForm("right", false);
    setLayoutSideForm("bottom", false);
}

function createTableRowId(tableId, rowNumber) {
    return tableId + "-" + (rowNumber + 1);
}

function setColumns(json, columnsId, tableId, columnPrefix, connectorColumnNames) {
    let columns = json[columnsId];
    let table = document.getElementById(tableId);
    if (!table || !columns) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "column");
    }
    if (columnPrefix) {
        table.setAttribute("data-column-prefix", columnPrefix);
    }

    for (let i = 0; i < columns.length; i++) {
        let column = columns[i];
        createColumnsRow(table, column, i, columnPrefix, connectorColumnNames);
    }

}

function createColumnsRow(table, column, i, columnPrefix, connectorColumnNames) {
    let row = table.insertRow(i + 1);
    let index = 0;

    // For the unique id for the row we use a global row number.
    //
    row.id = createTableRowId(table.id, rowIdNumber++);

    // Column name: always preserve stored name if not in live connector list
    //
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "Name", i),
        column["columnName"],
        connectorColumnNames,
        { preserveMissing: true }
    );

    // Header value: a text box
    //
    row.insertCell(index++).innerHTML = createText(
        createTableColumnId(columnPrefix, "Header", i),
        column["headerValue"]
    );
    // Width: blank = auto-detect; positive = fixed pixels when drawing the table
    row.insertCell(index++).innerHTML = createColumnWidthText(
        createTableColumnId(columnPrefix, "Width", i),
        column["width"]
    );
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "HorizontalAlignment", i),
        column["horizontalAlignment"],
        HORIZONTAL_ALIGNMENTS,
        { defaultEmptyToFirst: true }
    );
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "VerticalAlignment", i),
        column["verticalAlignment"],
        VERTICAL_ALIGNMENTS,
        { defaultEmptyToFirst: true }
    );
    let mask = column["formatMask"];
    row.insertCell(index++).innerHTML = createText(
        createTableColumnId(columnPrefix, "Format", i),
        mask === null ? "" : mask,
        "width: 4em"
    );

    appendListReorderCells(row, table, index);
}

function columnAdd(table, row, columnsPrefix, connectorColumnNames) {
    // Legacy helper: insert after the given row (header Add uses listFieldAdd)
    let index = row.rowIndex;
    let column = {
        "columnName": "",
        "headerValue": "",
        "width": 0,
        "horizontalAlignment": "LEFT",
        "verticalAlignment": "MIDDLE",
        "formatMask": ""
    }
    createColumnsRow(table, column, index, columnsPrefix, connectorColumnNames);
}

function columnDelete(table, row) {
    table.deleteRow(row.rowIndex);
}


function setFacts(json, columnsId, tableId, columnPrefix, connectorColumnNames) {
    let columns = json[columnsId];
    let table = document.getElementById(tableId);
    if (!table || !columns) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "fact");
    }
    if (columnPrefix) {
        table.setAttribute("data-column-prefix", columnPrefix);
    }

    for (let i = 0; i < columns.length; i++) {
        let column = columns[i];
        createFactsRow(table, column, i, columnPrefix, connectorColumnNames);
    }
}

function createTableColumnId(prefix, typeIndicator, index) {
    return prefix + typeIndicator + "-" + index;
}

function createFactsRow(table, column, i, columnPrefix, connectorColumnNames) {
    let row = table.insertRow(i + 1);
    let index = 0;

    // For the unique id for the row we use a global row number.
    //
    row.id = createTableRowId(table.id, rowIdNumber++);

    // Fact column name: preserve stored name when connector columns unavailable
    //
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "Name", i),
        column["columnName"],
        connectorColumnNames,
        { preserveMissing: true }
    );

    // Header value: a text box
    //
    row.insertCell(index++).innerHTML = createText(
        createTableColumnId(columnPrefix, "Header", i),
        column["headerValue"]
    );
    // Width: blank = auto-detect; positive = fixed pixels when drawing
    row.insertCell(index++).innerHTML = createColumnWidthText(
        createTableColumnId(columnPrefix, "Width", i),
        column["width"]
    );
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "HorizontalAlignment", i),
        column["horizontalAlignment"],
        HORIZONTAL_ALIGNMENTS,
        { defaultEmptyToFirst: true }
    );
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "VerticalAlignment", i),
        column["verticalAlignment"],
        VERTICAL_ALIGNMENTS,
        { defaultEmptyToFirst: true }
    );
    let mask = column["formatMask"];
    row.insertCell(index++).innerHTML = createText(
        createTableColumnId(columnPrefix, "Format", i),
        mask === null ? "" : mask,
        "width: 4em"
    );

    // Aggregation settings
    //
    row.insertCell(index++).innerHTML = createCheckBox(
        createTableColumnId(columnPrefix, "HorizontalAggregation", i),
        column["horizontalAggregation"]);
    row.insertCell(index++).innerHTML = createCheckBox(
        createTableColumnId(columnPrefix, "VerticalAggregation", i),
        column["verticalAggregation"]);
    row.insertCell(index++).innerHTML = createSelection(
        createTableColumnId(columnPrefix, "aggregationMethod", i),
        column["aggregationMethod"],
        AGGREGATION_METHODS,
        { defaultEmptyToFirst: true }
    );

    appendListReorderCells(row, table, index);
}

function factAdd(table, row, columnsPrefix, connectorColumnNames) {
    // Legacy helper: insert after the given row (header Add uses listFieldAdd)
    let index = row.rowIndex;
    let column = {
        "columnName": "",
        "headerValue": "",
        "width": 0,
        "horizontalAlignment": "LEFT",
        "verticalAlignment": "MIDDLE",
        "formatMask": "",
        "horizontalAggregation": true,
        "verticalAggregation": true,
        "aggregationMethod": "SUM"
    }
    createFactsRow(table, column, index, columnsPrefix, connectorColumnNames);
}

function cellControlValue(cell) {
    if (cell === null || cell === undefined) {
        return null;
    }
    let el = cell.querySelector("input, select, textarea");
    if (el === null || el === undefined) {
        // Fallback: raw text
        return cell.textContent;
    }
    if (el.type === "checkbox") {
        return el.checked;
    }
    return el.value;
}

function getColumns(json, columnsId, tableId) {
    try {
        let columns = [];
        let table = document.getElementById(tableId);
        if (table === null || table === undefined) {
            throw "unable to find table with id: " + tableId;
        }
        let rows = table.rows;
        if (rows === null || rows === undefined) {
            throw "unable to find rows in table with id: " + tableId;
        }

        for (let i = 1; i < rows.length; i++) {
            columns.push(getColumnsRow(rows[i]));
        }
        json[columnsId] = columns;

    } catch (e) {
        alert("Error getting column values for tableId=" + tableId + " : " + e);
    }
}

/**
 * Form action buttons from {@code GuiFormFieldType.BUTTON} (no value binding).
 * @param {string} fieldName Java field name from the form schema
 */
function hopperFormButtonClick(fieldName) {
    if (fieldName === "swapHorizontalVerticalDimensions") {
        swapCrosstabHorizontalVerticalDimensions();
        return;
    }
    if (fieldName === "detectCsvLayout") {
        detectCsvLayoutFromFile();
        return;
    }
    console.warn("hopperFormButtonClick: no handler for field '" + fieldName + "'");
}

/**
 * CSV connector form action: sample header + first 100 rows and fill the columns list.
 */
function detectCsvLayoutFromFile() {
    let filename = (document.getElementById("filename")
        && document.getElementById("filename").value) || "";
    if (!filename.trim()) {
        alert("Enter a VFS filename first (e.g. file:///path/to/data.csv).");
        return;
    }
    let headerEl = document.getElementById("headerPresent");
    let headerPresent = headerEl ? !!headerEl.checked : true;
    let separator = (document.getElementById("separator")
        && document.getElementById("separator").value) || ",";
    let groupingSymbol = (document.getElementById("groupingSymbol")
        && document.getElementById("groupingSymbol").value) || "";
    let localeEl = document.getElementById("locale");
    let locale = localeEl ? (localeEl.value || "") : "";
    let encodingEl = document.getElementById("encoding");
    let encoding = encodingEl ? (encodingEl.value || "UTF-8") : "UTF-8";

    let body = {
        filename: filename,
        headerPresent: headerPresent,
        separator: separator,
        locale: locale,
        groupingSymbol: groupingSymbol,
        encoding: encoding
    };

    $.ajax({
        url: (typeof API_BASE !== "undefined" ? API_BASE : "/hopper/api/")
            + "edit/connector/csv/detect-layout/",
        type: "POST",
        contentType: "application/json",
        data: JSON.stringify(body),
        success: function (data) {
            let fields = (data && data.fields) ? data.fields : [];
            let table = document.getElementById("fields");
            if (!table) {
                alert("Columns table not found on this form.");
                return;
            }
            while (table.rows.length > 1) {
                table.deleteRow(1);
            }
            table.setAttribute("data-list-kind", "csvField");
            setCsvFields({fields: fields}, "fields", "fields");

            // Sync into connector JSON working copy when editing connectors
            if (typeof connectorJson !== "undefined" && connectorJson && connectorJson.connector) {
                let keys = Object.keys(connectorJson.connector);
                if (keys.length === 1) {
                    let plugin = connectorJson.connector[keys[0]];
                    if (plugin) {
                        plugin.fields = fields;
                    }
                }
            }
        },
        error: function (xhr) {
            let msg = "Detect layout failed";
            try {
                let err = JSON.parse(xhr.responseText);
                if (err && err.error) {
                    msg = err.error;
                } else if (xhr.responseText) {
                    msg = xhr.responseText;
                }
            } catch (e) {
                if (xhr.responseText) {
                    msg = xhr.responseText;
                }
            }
            alert(msg);
        }
    });
}

/**
 * Exchange the horizontal and vertical dimension lists on a crosstab (or other aggregating)
 * component form. Tables use ids {@code horizontalDimensions} / {@code verticalDimensions}.
 */
function swapCrosstabHorizontalVerticalDimensions() {
    let hTable = document.getElementById("horizontalDimensions");
    let vTable = document.getElementById("verticalDimensions");
    if (!hTable || !vTable) {
        alert("Horizontal / vertical dimension lists were not found on this form.");
        return;
    }
    let fromH = {};
    let fromV = {};
    getColumns(fromH, "horizontalDimensions", "horizontalDimensions");
    getColumns(fromV, "verticalDimensions", "verticalDimensions");
    let hCols = fromH.horizontalDimensions || [];
    let vCols = fromV.verticalDimensions || [];

    // Clear data rows (keep header)
    while (hTable.rows.length > 1) {
        hTable.deleteRow(1);
    }
    while (vTable.rows.length > 1) {
        vTable.deleteRow(1);
    }

    let colNames = typeof listFieldConnectorColumnNames === "function"
        ? listFieldConnectorColumnNames(hTable)
        : [];
    let hPrefix = hTable.getAttribute("data-column-prefix") || "horizontalDimensions";
    let vPrefix = vTable.getAttribute("data-column-prefix") || "verticalDimensions";

    // Vertical list → horizontal table, and vice versa
    setColumns(
        {horizontalDimensions: vCols},
        "horizontalDimensions",
        "horizontalDimensions",
        hPrefix,
        colNames
    );
    setColumns(
        {verticalDimensions: hCols},
        "verticalDimensions",
        "verticalDimensions",
        vPrefix,
        colNames
    );

    // Sync into componentJson so Apply persists the swap even if user doesn't re-touch fields
    if (typeof componentJson !== "undefined" && componentJson && componentJson.component
        && typeof componentPluginId !== "undefined" && componentPluginId) {
        let iComponent = componentJson.component[componentPluginId];
        if (iComponent) {
            iComponent.horizontalDimensions = vCols;
            iComponent.verticalDimensions = hCols;
        }
    }
}

function getColumnsRow(row) {
    try {
        let column = {};
        let index = 0;
        column["columnName"] = cellControlValue(row.cells[index++]);
        column["headerValue"] = cellControlValue(row.cells[index++]);
        // Blank / zero → auto width when drawing; positive → fixed pixel width
        column["width"] = parseColumnWidthInputValue(cellControlValue(row.cells[index++]));
        let hAlign = cellControlValue(row.cells[index++]);
        let vAlign = cellControlValue(row.cells[index++]);
        // Never write null/empty enums — Hop leaves them null and switch(enum) NPEs
        column["horizontalAlignment"] = hAlign || "LEFT";
        column["verticalAlignment"] = vAlign || "TOP";
        column["formatMask"] = cellControlValue(row.cells[index++]);
        return column;
    } catch (e) {
        throw "Error getting values from row " + row.id + " : " + e;
    }
}

function getFacts(json, columnsId, tableId) {
    try {
        let facts = [];
        let table = document.getElementById(tableId);
        if (table === null || table === undefined) {
            throw "unable to find table with id: " + tableId;
        }
        for (let i = 1; i < table.rows.length; i++) {
            facts.push(getFactsRow(table.rows[i]));
        }
        json[columnsId] = facts;
    } catch (e) {
        alert("Error getting fact values for tableId=" + tableId + " : " + e);
    }
}

function getFactsRow(row) {
    try {
        let fact = getColumnsRow(row);
        // After HColumn cells (0-5): H-Agg, V-Agg, Method
        fact["horizontalAggregation"] = !!cellControlValue(row.cells[6]);
        fact["verticalAggregation"] = !!cellControlValue(row.cells[7]);
        fact["aggregationMethod"] = cellControlValue(row.cells[8]) || "SUM";
        // Header cell alignments are not yet on the form — keep safe defaults so
        // Apply does not wipe them to null (crosstab render NPEs on null enums).
        fact["headerHorizontalAlignment"] = "LEFT";
        fact["headerVerticalAlignment"] = "TOP";
        return fact;
    } catch (e) {
        throw "Error getting fact values from row " + row.id + " : " + e;
    }
}

function setStringList(json, fieldId, tableId) {
    let values = json[fieldId];
    if (values === null || values === undefined) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "string");
    }
    for (let i = 0; i < values.length; i++) {
        createStringListRow(table, values[i], i);
    }
}

function createStringListRow(table, value, i) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    row.insertCell(0).innerHTML = createText("stringList-" + i, value === null ? "" : value);
    appendListReorderCells(row, table, 1);
}

function getStringList(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (table === null) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        values.push(cellControlValue(table.rows[i].cells[0]));
    }
    json[fieldId] = values;
}

/**
 * Edit the current presentation.
 * Get the metadata using the name.
 *
 * The render ID and presentation name are known for the whole page.
 *
 * @param component
 * @param requestData
 */
// ---------------------------------------------------------------------------
// Component interaction builder (selection toolbar → Add interaction)
// ---------------------------------------------------------------------------

/** Working state for the component-scoped interaction builder panel. */
let componentIxBuilder = null;

/**
 * Open the HInteraction builder for a selected component (edit mode toolbar).
 * @param {string} componentName
 * @param {{index?: number, interaction?: object}|null} opts optional edit of existing
 */
function openComponentInteractionBuilder(componentName, opts) {
    if (!componentName || typeof presentationName === "undefined" || !presentationName) {
        alert("No component or presentation selected.");
        return;
    }
    opts = opts || {};
    setSidePanelOpen(true, {withPreview: false});
    let editArea = document.getElementById("editArea");
    if (!editArea) {
        return;
    }
    editArea.innerHTML = "<p class=\"editor-hint\">Loading interaction locations…</p>";

    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/components/" + encodeURIComponent(componentName) + "/interaction-locations/",
        type: "GET",
        dataType: "json",
        success: function (payload) {
            let locations = (payload && payload.locations) || [];
            let pluginId = (payload && payload.componentPluginId) || "";
            let resolvedName = (payload && payload.componentName) || componentName;
            let existing = opts.interaction || null;
            let editIndex = (typeof opts.index === "number") ? opts.index : -1;

            // Seed location selection from existing or first option (prefer non-whole if adding)
            let selectedLocId = "whole";
            let selectedDims = [];
            if (existing && existing.location) {
                selectedLocId = matchLocationOptionId(locations, existing.location) || "whole";
                selectedDims = existing.location.dimensionColumns
                    ? existing.location.dimensionColumns.slice()
                    : [];
            } else if (locations.length > 1) {
                // Prefer first non-whole target when adding
                selectedLocId = locations[1].id || "whole";
                selectedDims = (locations[1].dimensionColumns || []).slice();
            } else if (locations.length === 1) {
                selectedDims = (locations[0].dimensionColumns || []).slice();
            }

            let method = (existing && existing.method) || {mouseClick: true, mouseDoubleClick: false};
            let actions = (existing && existing.actions && existing.actions.length)
                ? existing.actions.map(function (a) {
                    return {
                        actionType: a.actionType || "OPEN_PRESENTATION",
                        objectName: a.objectName || "",
                        valueParameter: a.valueParameter || "",
                        dimensionParameters: (a.dimensionParameters || []).map(function (m) {
                            return {
                                dimensionColumn: m.dimensionColumn || "",
                                parameterName: m.parameterName || ""
                            };
                        })
                    };
                })
                : [{
                    actionType: "OPEN_PRESENTATION",
                    objectName: "",
                    valueParameter: "",
                    dimensionParameters: []
                }];

            componentIxBuilder = {
                componentName: resolvedName,
                componentPluginId: pluginId,
                locations: locations,
                selectedLocId: selectedLocId,
                selectedDims: selectedDims,
                method: method,
                actions: actions,
                editIndex: editIndex
            };
            renderComponentInteractionBuilder();
        },
        error: function (xhr, status, error) {
            showAjaxError("Could not load interaction locations", xhr, status, error);
            editArea.innerHTML = "<p class=\"editor-hint\">Failed to load locations.</p>"
                + "<button type=\"button\" class=\"form-action-close\" "
                + "onclick=\"closeComponentInteractionBuilder()\">Close</button>";
        }
    });
}

/**
 * Match a stored location to an option id (by itemType + itemCategory).
 */
function matchLocationOptionId(locations, location) {
    if (!locations || !location) {
        return null;
    }
    let itemType = location.itemType || "";
    let itemCategory = location.itemCategory || "";
    for (let i = 0; i < locations.length; i++) {
        let loc = locations[i];
        if ((loc.itemType || "") === itemType
            && (loc.itemCategory || "") === itemCategory) {
            return loc.id;
        }
    }
    if (itemType === "Component") {
        return "whole";
    }
    return null;
}

function closeComponentInteractionBuilder() {
    componentIxBuilder = null;
    setSidePanelOpen(false);
}

function renderComponentInteractionBuilder() {
    let st = componentIxBuilder;
    let editArea = document.getElementById("editArea");
    if (!st || !editArea) {
        return;
    }

    let selectedOpt = null;
    for (let i = 0; i < st.locations.length; i++) {
        if (st.locations[i].id === st.selectedLocId) {
            selectedOpt = st.locations[i];
            break;
        }
    }
    if (!selectedOpt && st.locations.length) {
        selectedOpt = st.locations[0];
        st.selectedLocId = selectedOpt.id;
    }

    let isDbl = !!(st.method && st.method.mouseDoubleClick);
    let html = "";
    html += "<div class=\"ix-builder\">";
    html += "<div class=\"form-action-bar\" id=\"formActionBar-ix-builder\">";
    html += "<button type=\"button\" class=\"form-action-apply\" id=\"ixBuilderSave\">Save</button>";
    html += "<button type=\"button\" class=\"form-action-close\" id=\"ixBuilderCancel\">Cancel</button>";
    html += "</div>";
    html += "<h3 class=\"ix-builder-title\">Interaction — "
        + escapeHtmlText(st.componentName) + "</h3>";
    html += "<p class=\"editor-hint\">Define how clicks on this component navigate or set "
        + "parameters. Locations match drawn hit targets at render time.</p>";

    // Method
    html += "<fieldset class=\"ix-builder-section\"><legend>Method</legend>";
    html += "<label class=\"ix-builder-radio\"><input type=\"radio\" name=\"ixBMethod\" value=\"click\""
        + (!isDbl ? " checked" : "") + "> Single click</label> ";
    html += "<label class=\"ix-builder-radio\"><input type=\"radio\" name=\"ixBMethod\" value=\"dbl\""
        + (isDbl ? " checked" : "") + "> Double click</label>";
    html += "</fieldset>";

    // Locations
    html += "<fieldset class=\"ix-builder-section\"><legend>Location</legend>";
    html += "<div class=\"ix-builder-location-list\" id=\"ixBLocationList\">";
    for (let i = 0; i < st.locations.length; i++) {
        let loc = st.locations[i];
        let checked = loc.id === st.selectedLocId ? " checked" : "";
        let catHint = loc.itemCategory
            ? (" <span class=\"editor-hint\">(" + escapeHtmlText(loc.itemCategory) + ")</span>")
            : "";
        html += "<label class=\"ix-builder-location-option\">";
        html += "<input type=\"radio\" name=\"ixBLocation\" value=\""
            + escapeHtmlAttribute(loc.id || "") + "\"" + checked + "> ";
        html += "<span class=\"ix-builder-location-label\">"
            + escapeHtmlText(loc.label || loc.id || "?") + "</span>" + catHint;
        html += "</label>";
    }
    if (!st.locations.length) {
        html += "<p class=\"editor-hint\">No locations returned.</p>";
    }
    html += "</div>";

    // Dimension columns when editable
    let showDims = selectedOpt && selectedOpt.dimensionsEditable;
    html += "<div id=\"ixBDimsWrap\"" + (showDims ? "" : " hidden") + ">";
    html += "<label>Dimension columns</label>";
    html += "<p class=\"editor-hint\">Match only hits that include these dimension columns "
        + "(empty = any).</p>";
    html += "<div id=\"ixBDimsBox\" class=\"pres-prop-check-list\">";
    html += buildIxBuilderDimChecklist(
        selectedOpt ? (selectedOpt.dimensionColumns || []) : [],
        st.selectedDims || []);
    html += "</div></div>";
    html += "</fieldset>";

    // Actions
    html += "<fieldset class=\"ix-builder-section\"><legend>Actions</legend>";
    html += "<div class=\"ix-builder-actions-toolbar\">";
    html += "<button type=\"button\" class=\"home-btn\" id=\"ixBAddAction\">+ Add action</button>";
    html += "</div>";
    html += "<div id=\"ixBActionsList\"></div>";
    html += "</fieldset>";

    html += "<div class=\"form-action-bar\" id=\"formActionBar-ix-builder-bottom\">";
    html += "<button type=\"button\" class=\"form-action-apply\" id=\"ixBuilderSaveBottom\">Save</button>";
    html += "<button type=\"button\" class=\"form-action-close\" id=\"ixBuilderCancelBottom\">Cancel</button>";
    html += "</div>";
    html += "</div>";

    editArea.innerHTML = html;
    renderIxBuilderActionsList();
    wireIxBuilderHandlers();
}

function buildIxBuilderDimChecklist(available, selected) {
    available = available || [];
    selected = selected || [];
    let selectedSet = {};
    for (let i = 0; i < selected.length; i++) {
        selectedSet[selected[i]] = true;
    }
    // Union available + selected so custom dims stay visible
    let all = available.slice();
    for (let i = 0; i < selected.length; i++) {
        if (selected[i] && all.indexOf(selected[i]) < 0) {
            all.push(selected[i]);
        }
    }
    if (!all.length) {
        return "<p class=\"editor-hint\">No dimension columns on this location "
            + "(you can still leave the list empty).</p>";
    }
    let html = "";
    for (let i = 0; i < all.length; i++) {
        let c = all[i];
        html += "<label class=\"pres-prop-check\">"
            + "<input type=\"checkbox\" class=\"ixb-dim-cb\" value=\""
            + escapeHtmlAttribute(c) + "\""
            + (selectedSet[c] ? " checked" : "") + "> "
            + escapeHtmlText(c) + "</label> ";
    }
    return html;
}

function collectIxBuilderDims() {
    let dims = [];
    let boxes = document.querySelectorAll("#ixBDimsBox .ixb-dim-cb:checked");
    for (let i = 0; i < boxes.length; i++) {
        if (boxes[i].value) {
            dims.push(boxes[i].value);
        }
    }
    return dims;
}

function renderIxBuilderActionsList() {
    let st = componentIxBuilder;
    let root = document.getElementById("ixBActionsList");
    if (!st || !root) {
        return;
    }
    let presentations = getPresentationNamesList();
    let html = "";
    let targetsToLoad = [];
    if (!st.actions.length) {
        html = "<p class=\"editor-hint\">No actions yet. Add at least one.</p>";
    }
    for (let i = 0; i < st.actions.length; i++) {
        let act = st.actions[i];
        let type = act.actionType || "OPEN_PRESENTATION";
        let targetPres = (type === "OPEN_PRESENTATION" && act.objectName)
            ? String(act.objectName).trim() : "";
        let paramListId = targetPres
            ? ("ixbTargetParams-" + i)
            : "presParamNamesList";
        let paramNamesHint = targetPres
            ? getCachedPresentationParameterNames(targetPres)
            : getPresentationParameterDefinitionNames();
        if (targetPres) {
            targetsToLoad.push({index: i, name: targetPres, listId: paramListId});
        }

        html += "<div class=\"ix-builder-action-card\" data-act-index=\"" + i + "\">";
        html += "<div class=\"ix-builder-action-head\">Action " + (i + 1);
        html += " <span class=\"ix-builder-action-move\">";
        html += "<button type=\"button\" data-act-up=\"" + i + "\" title=\"Move up\">Up</button> ";
        html += "<button type=\"button\" data-act-down=\"" + i + "\" title=\"Move down\">Down</button> ";
        html += "<button type=\"button\" data-act-del=\"" + i + "\" title=\"Delete\">Del</button>";
        html += "</span></div>";

        html += "<label>Action type</label><br>";
        html += "<select class=\"pres-prop-input ixb-act-type\" data-act-i=\"" + i + "\">";
        ["OPEN_PRESENTATION", "OPEN_LINK_SAME_TAB", "OPEN_LINK_NEW_TAB"].forEach(function (t) {
            html += "<option value=\"" + t + "\"" + (t === type ? " selected" : "") + ">"
                + t + "</option>";
        });
        html += "</select><br>";

        if (type === "OPEN_PRESENTATION") {
            html += "<label>Target presentation</label><br>";
            html += "<select class=\"pres-prop-input ixb-act-object\" data-act-i=\"" + i + "\">";
            html += "<option value=\"\">(use clicked value)</option>";
            for (let p = 0; p < presentations.length; p++) {
                let pn = presentations[p];
                if (!pn) {
                    continue;
                }
                html += "<option value=\"" + escapeHtmlAttribute(pn) + "\""
                    + (pn === (act.objectName || "") ? " selected" : "") + ">"
                    + escapeHtmlText(pn) + "</option>";
            }
            // Keep free-text value if not in list
            if (act.objectName && presentations.indexOf(act.objectName) < 0) {
                html += "<option value=\"" + escapeHtmlAttribute(act.objectName)
                    + "\" selected>" + escapeHtmlText(act.objectName) + "</option>";
            }
            html += "</select>";
            if (targetPres) {
                html += "<p class=\"editor-hint ixb-target-param-hint\" data-act-i=\"" + i + "\">"
                    + "Parameter names suggest values from <strong>"
                    + escapeHtmlText(targetPres) + "</strong>"
                    + (paramNamesHint.length
                        ? " (" + paramNamesHint.length + " declared)"
                        : " (loading…)")
                    + ".</p>";
            } else {
                html += "<p class=\"editor-hint\">Select a target presentation to pick from "
                    + "its declared parameters.</p>";
            }
        } else {
            html += "<label>URL / object name</label><br>";
            html += "<input type=\"text\" class=\"pres-prop-input ixb-act-object\" data-act-i=\""
                + i + "\" value=\"" + escapeHtmlAttribute(act.objectName || "") + "\" "
                + "placeholder=\"https://… or template\"><br>";
        }

        html += "<label>Value parameter (clicked cell / slice)</label><br>";
        html += "<input type=\"text\" class=\"pres-prop-input ixb-act-param\" data-act-i=\""
            + i + "\" list=\"" + escapeHtmlAttribute(paramListId) + "\" value=\""
            + escapeHtmlAttribute(act.valueParameter || "") + "\" "
            + "placeholder=\"target parameter name\" autocomplete=\"off\">";
        html += buildDimensionParameterMapHtml(
            "ixb-act-dimmap-" + i,
            act.dimensionParameters || [],
            getIxBuilderAvailableDimColumns(),
            true,
            paramListId);
        html += buildPresentationParameterDatalistHtml(paramListId, paramNamesHint);
        html += "</div>";
    }
    // Fallback list for actions without a target
    html += buildPresentationParameterDatalistHtml(
        "presParamNamesList", getPresentationParameterDefinitionNames());
    root.innerHTML = html;

    // Async: load target presentation parameters into each action's datalist
    for (let t = 0; t < targetsToLoad.length; t++) {
        (function (item) {
            ensurePresentationParameterNames(item.name, function (names) {
                fillParameterNamesDatalist(item.listId, names);
                let hint = root.querySelector(
                    ".ixb-target-param-hint[data-act-i=\"" + item.index + "\"]");
                if (hint) {
                    hint.innerHTML = "Parameter names suggest values from <strong>"
                        + escapeHtmlText(item.name) + "</strong>"
                        + (names.length
                            ? " (" + names.length + " declared)."
                            : " (none declared — free text still allowed).");
                }
            });
        })(targetsToLoad[t]);
    }

    // Wire per-action controls + dimension mapping add/remove
    root.onclick = function (e) {
        let t = e.target;
        if (!t || !t.getAttribute) {
            return;
        }
        if (t.classList && t.classList.contains("ix-dimmap-add")) {
            let tbodyId = t.getAttribute("data-dimmap-tbody");
            let listId = t.getAttribute("data-dimmap-param-list") || "presParamNamesList";
            let tbody = tbodyId ? document.getElementById(tbodyId) : null;
            if (tbody) {
                tbody.insertAdjacentHTML(
                    "beforeend",
                    buildDimensionParameterMapRowHtml(
                        getIxBuilderAvailableDimColumns(), "", "", listId));
            }
            return;
        }
        if (t.classList && t.classList.contains("ix-dimmap-del")) {
            let tr = t.closest("tr");
            if (tr && tr.parentNode) {
                tr.parentNode.removeChild(tr);
            }
            return;
        }
        if (t.getAttribute("data-act-del") != null) {
            let di = parseInt(t.getAttribute("data-act-del"), 10);
            syncIxBuilderActionsFromDom();
            st.actions.splice(di, 1);
            renderIxBuilderActionsList();
        } else if (t.getAttribute("data-act-up") != null) {
            let ui = parseInt(t.getAttribute("data-act-up"), 10);
            if (ui > 0) {
                syncIxBuilderActionsFromDom();
                let tmp = st.actions[ui - 1];
                st.actions[ui - 1] = st.actions[ui];
                st.actions[ui] = tmp;
                renderIxBuilderActionsList();
            }
        } else if (t.getAttribute("data-act-down") != null) {
            let di2 = parseInt(t.getAttribute("data-act-down"), 10);
            if (di2 < st.actions.length - 1) {
                syncIxBuilderActionsFromDom();
                let tmp2 = st.actions[di2 + 1];
                st.actions[di2 + 1] = st.actions[di2];
                st.actions[di2] = tmp2;
                renderIxBuilderActionsList();
            }
        }
    };
    root.onchange = function (e) {
        let t = e.target;
        if (!t || !t.classList) {
            return;
        }
        if (t.classList.contains("ixb-act-type")) {
            syncIxBuilderActionsFromDom();
            renderIxBuilderActionsList();
            return;
        }
        if (t.classList.contains("ixb-act-object")) {
            // Target presentation changed: reload that presentation's parameter names
            syncIxBuilderActionsFromDom();
            let actI = parseInt(t.getAttribute("data-act-i"), 10);
            let card = t.closest(".ix-builder-action-card");
            let typeEl = card ? card.querySelector(".ixb-act-type") : null;
            let type = typeEl ? typeEl.value : "OPEN_PRESENTATION";
            if (type === "OPEN_PRESENTATION") {
                // Re-render so list ids / hints stay in sync with the selected target
                renderIxBuilderActionsList();
            }
        }
    };
}

function getIxBuilderAvailableDimColumns() {
    let st = componentIxBuilder;
    if (!st) {
        return [];
    }
    let opt = null;
    for (let i = 0; i < (st.locations || []).length; i++) {
        if (st.locations[i].id === st.selectedLocId) {
            opt = st.locations[i];
            break;
        }
    }
    let cols = [];
    if (st.selectedDims && st.selectedDims.length) {
        cols = st.selectedDims.slice();
    } else if (opt && opt.dimensionColumns) {
        cols = opt.dimensionColumns.slice();
    }
    return cols;
}

/**
 * HTML for mapping dimension columns → parameter names.
 * @param {string} tableId unique id for the tbody
 * @param {Array} mappings [{dimensionColumn, parameterName}]
 * @param {string[]} availableDims column names for the select
 * @param {boolean} withAddButton
 * @param {string} [paramListId] datalist id for parameter name suggestions
 */
function buildDimensionParameterMapHtml(
    tableId, mappings, availableDims, withAddButton, paramListId) {
    mappings = mappings || [];
    availableDims = availableDims || [];
    let listId = paramListId || "presParamNamesList";
    let html = "<div class=\"ix-dim-param-map\" data-dimmap-id=\"" + escapeHtmlAttribute(tableId)
        + "\" data-dimmap-param-list=\"" + escapeHtmlAttribute(listId) + "\">";
    html += "<label>Dimension → parameter mappings</label>";
    html += "<p class=\"editor-hint\">Map crosstab (or chart) dimension columns from the click "
        + "context to parameters on the <strong>target</strong> presentation. "
        + "Independent of the value parameter.</p>";
    html += "<table class=\"pres-prop-map-table\"><thead><tr>"
        + "<th>Dimension column</th><th>Parameter name</th><th></th></tr></thead>";
    html += "<tbody id=\"" + escapeHtmlAttribute(tableId) + "\">";
    if (!mappings.length) {
        html += buildDimensionParameterMapRowHtml(availableDims, "", "", listId);
    } else {
        for (let r = 0; r < mappings.length; r++) {
            html += buildDimensionParameterMapRowHtml(
                availableDims,
                mappings[r].dimensionColumn || "",
                mappings[r].parameterName || "",
                listId);
        }
    }
    html += "</tbody></table>";
    if (withAddButton) {
        html += "<button type=\"button\" class=\"home-btn ix-dimmap-add\" data-dimmap-tbody=\""
            + escapeHtmlAttribute(tableId) + "\" data-dimmap-param-list=\""
            + escapeHtmlAttribute(listId) + "\">+ Dimension mapping</button>";
    }
    html += "</div>";
    return html;
}

/**
 * @param {string[]} availableDims
 * @param {string} dimensionColumn
 * @param {string} parameterName
 * @param {string} [paramListId] datalist id for the parameter name field
 */
function buildDimensionParameterMapRowHtml(
    availableDims, dimensionColumn, parameterName, paramListId) {
    availableDims = availableDims || [];
    let listId = paramListId || "presParamNamesList";
    let html = "<tr>";
    html += "<td>";
    if (availableDims.length) {
        html += "<select class=\"pres-prop-input ix-dimmap-col\">";
        html += "<option value=\"\">- dimension -</option>";
        let found = false;
        for (let i = 0; i < availableDims.length; i++) {
            let d = availableDims[i];
            let sel = (d === dimensionColumn) ? " selected" : "";
            if (d === dimensionColumn) {
                found = true;
            }
            html += "<option value=\"" + escapeHtmlAttribute(d) + "\"" + sel + ">"
                + escapeHtmlText(d) + "</option>";
        }
        if (dimensionColumn && !found) {
            html += "<option value=\"" + escapeHtmlAttribute(dimensionColumn)
                + "\" selected>" + escapeHtmlText(dimensionColumn) + "</option>";
        }
        html += "</select>";
    } else {
        html += "<input type=\"text\" class=\"pres-prop-input ix-dimmap-col\" value=\""
            + escapeHtmlAttribute(dimensionColumn || "") + "\" placeholder=\"e.g. region\">";
    }
    html += "</td>";
    html += "<td><input type=\"text\" class=\"pres-prop-input ix-dimmap-param\" list=\""
        + escapeHtmlAttribute(listId) + "\" value=\""
        + escapeHtmlAttribute(parameterName || "")
        + "\" placeholder=\"target parameter\" autocomplete=\"off\"></td>";
    html += "<td><button type=\"button\" class=\"ix-dimmap-del\" title=\"Remove\">x</button></td>";
    html += "</tr>";
    return html;
}

/**
 * Wire add/remove for a standalone dimension-parameter map (presentation-props editor).
 * @param {HTMLElement} root container
 * @param {string[]} availableDims
 * @param {string} [paramListId]
 */
function wireDimensionParameterMapButtons(root, availableDims, paramListId) {
    if (!root) {
        return;
    }
    root.onclick = function (e) {
        let t = e.target;
        if (!t || !t.classList) {
            return;
        }
        if (t.classList.contains("ix-dimmap-add")) {
            let tbodyId = t.getAttribute("data-dimmap-tbody");
            let listId = t.getAttribute("data-dimmap-param-list")
                || paramListId
                || "presParamNamesList";
            let tbody = tbodyId ? document.getElementById(tbodyId) : null;
            if (tbody) {
                tbody.insertAdjacentHTML(
                    "beforeend",
                    buildDimensionParameterMapRowHtml(availableDims || [], "", "", listId));
            }
        } else if (t.classList.contains("ix-dimmap-del")) {
            let tr = t.closest("tr");
            if (tr && tr.parentNode) {
                tr.parentNode.removeChild(tr);
            }
        }
    };
}

function collectDimensionParameterMappingsFrom(tbody) {
    let mappings = [];
    if (!tbody) {
        return mappings;
    }
    let rows = tbody.querySelectorAll("tr");
    for (let i = 0; i < rows.length; i++) {
        let colEl = rows[i].querySelector(".ix-dimmap-col");
        let paramEl = rows[i].querySelector(".ix-dimmap-param");
        let col = colEl ? (colEl.value || "").trim() : "";
        let pn = paramEl ? (paramEl.value || "").trim() : "";
        if (col || pn) {
            if (col && pn) {
                mappings.push({dimensionColumn: col, parameterName: pn});
            }
        }
    }
    return mappings;
}

function syncIxBuilderActionsFromDom() {
    let st = componentIxBuilder;
    if (!st) {
        return;
    }
    let cards = document.querySelectorAll("#ixBActionsList .ix-builder-action-card");
    let next = [];
    for (let i = 0; i < cards.length; i++) {
        let card = cards[i];
        let typeEl = card.querySelector(".ixb-act-type");
        let objEl = card.querySelector(".ixb-act-object");
        let paramEl = card.querySelector(".ixb-act-param");
        let dimTbody = card.querySelector("tbody[id^=\"ixb-act-dimmap-\"]");
        let act = {
            actionType: typeEl ? typeEl.value : "OPEN_PRESENTATION",
            objectName: objEl ? (objEl.value || "") : "",
            valueParameter: paramEl ? (paramEl.value || "") : "",
            dimensionParameters: collectDimensionParameterMappingsFrom(dimTbody)
        };
        next.push(act);
    }
    st.actions = next;
}

function wireIxBuilderHandlers() {
    let st = componentIxBuilder;
    if (!st) {
        return;
    }

    function onSave() {
        saveComponentInteractionBuilder();
    }
    function onCancel() {
        closeComponentInteractionBuilder();
    }
    let saveTop = document.getElementById("ixBuilderSave");
    let saveBot = document.getElementById("ixBuilderSaveBottom");
    let cancelTop = document.getElementById("ixBuilderCancel");
    let cancelBot = document.getElementById("ixBuilderCancelBottom");
    if (saveTop) {
        saveTop.onclick = onSave;
    }
    if (saveBot) {
        saveBot.onclick = onSave;
    }
    if (cancelTop) {
        cancelTop.onclick = onCancel;
    }
    if (cancelBot) {
        cancelBot.onclick = onCancel;
    }

    let addBtn = document.getElementById("ixBAddAction");
    if (addBtn) {
        addBtn.onclick = function () {
            syncIxBuilderActionsFromDom();
            st.actions.push({
                actionType: "OPEN_PRESENTATION",
                objectName: "",
                valueParameter: "",
                dimensionParameters: []
            });
            renderIxBuilderActionsList();
        };
    }

    let locList = document.getElementById("ixBLocationList");
    if (locList) {
        locList.onchange = function (e) {
            let t = e.target;
            if (!t || t.name !== "ixBLocation") {
                return;
            }
            st.selectedDims = collectIxBuilderDims();
            st.selectedLocId = t.value;
            let opt = null;
            for (let i = 0; i < st.locations.length; i++) {
                if (st.locations[i].id === st.selectedLocId) {
                    opt = st.locations[i];
                    break;
                }
            }
            // Reset dims to option defaults when switching location
            st.selectedDims = opt && opt.dimensionColumns
                ? opt.dimensionColumns.slice() : [];
            let wrap = document.getElementById("ixBDimsWrap");
            let box = document.getElementById("ixBDimsBox");
            if (wrap && box) {
                if (opt && opt.dimensionsEditable) {
                    wrap.removeAttribute("hidden");
                    box.innerHTML = buildIxBuilderDimChecklist(
                        opt.dimensionColumns || [], st.selectedDims);
                } else {
                    wrap.setAttribute("hidden", "hidden");
                    box.innerHTML = "";
                }
            }
        };
    }
}

function saveComponentInteractionBuilder() {
    let st = componentIxBuilder;
    if (!st || !presentationName) {
        return;
    }
    syncIxBuilderActionsFromDom();
    st.selectedDims = collectIxBuilderDims();

    let methodVal = document.querySelector("input[name=\"ixBMethod\"]:checked");
    let isDbl = methodVal && methodVal.value === "dbl";

    let opt = null;
    for (let i = 0; i < st.locations.length; i++) {
        if (st.locations[i].id === st.selectedLocId) {
            opt = st.locations[i];
            break;
        }
    }
    if (!opt) {
        alert("Select a location for the interaction.");
        return;
    }

    let dims = [];
    if (opt.itemType === "Component") {
        dims = [];
    } else if (opt.dimensionsEditable) {
        dims = st.selectedDims || [];
    } else {
        dims = (opt.dimensionColumns || []).slice();
    }

    if (!st.actions.length) {
        alert("Add at least one action.");
        return;
    }

    let actions = st.actions.map(function (a) {
        let out = {
            actionType: a.actionType || "OPEN_PRESENTATION"
        };
        if (a.objectName) {
            out.objectName = a.objectName;
        }
        if (a.valueParameter) {
            out.valueParameter = a.valueParameter;
        }
        if (a.dimensionParameters && a.dimensionParameters.length) {
            out.dimensionParameters = a.dimensionParameters;
        }
        return out;
    });

    let interaction = {
        method: {mouseClick: !isDbl, mouseDoubleClick: !!isDbl},
        location: {
            componentName: st.componentName,
            componentPluginId: st.componentPluginId || "",
            itemType: opt.itemType || "ComponentItem",
            itemCategory: (opt.itemType === "Component") ? null : (opt.itemCategory || null),
            dimensionColumns: dims
        },
        actions: actions
    };
    // Clean null itemCategory for hop friendliness
    if (!interaction.location.itemCategory) {
        delete interaction.location.itemCategory;
    }

    let body = {interaction: interaction};
    if (st.editIndex >= 0) {
        body.index = st.editIndex;
    }

    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/interactions/",
        type: "POST",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(body),
        dataType: "json",
        success: function (result) {
            // Keep in-memory presentation JSON in sync when present
            if (typeof presentationJson !== "undefined" && presentationJson) {
                if (!presentationJson.interactions) {
                    presentationJson.interactions = [];
                }
                if (st.editIndex >= 0
                    && st.editIndex < presentationJson.interactions.length) {
                    presentationJson.interactions[st.editIndex] = interaction;
                } else {
                    presentationJson.interactions.push(interaction);
                }
            }
            if (typeof presentationPropertiesWorking !== "undefined"
                && presentationPropertiesWorking) {
                if (!presentationPropertiesWorking.interactions) {
                    presentationPropertiesWorking.interactions = [];
                }
                if (st.editIndex >= 0
                    && st.editIndex < presentationPropertiesWorking.interactions.length) {
                    presentationPropertiesWorking.interactions[st.editIndex] = interaction;
                } else {
                    presentationPropertiesWorking.interactions.push(interaction);
                }
                if (typeof refreshPresentationInteractionsList === "function") {
                    refreshPresentationInteractionsList();
                }
            }
            closeComponentInteractionBuilder();
            if (typeof softReloadEditor === "function") {
                softReloadEditor(st.componentName);
            }
        },
        error: function (xhr, status, error) {
            showAjaxError("Save interaction failed", xhr, status, error);
        }
    });
}


// ---------------------------------------------------------------------------
// Presentation properties (name, theme, interactions, parameter mappings)
// ---------------------------------------------------------------------------

/** Working copy while the properties panel is open. */
let presentationPropertiesWorking = null;
/** Snapshot JSON string when the panel was opened (dirty detection). */
let presentationPropertiesBaseline = null;
/** Name when the panel was opened (for rename). */
let presentationPropertiesOldName = null;
/** Index of interaction being edited in the expanded form, or -1. */
let presentationInteractionEditIndex = -1;
/** Index of parameter mapping group being edited, or -1. */
let presentationParamMapEditIndex = -1;
/** True after user edits in the properties panel (or nested editors). */
let presentationPropertiesDirty = false;

function markPresentationPropertiesDirty() {
    presentationPropertiesDirty = true;
}

function clearPresentationPropertiesDirty() {
    presentationPropertiesDirty = false;
    if (presentationPropertiesWorking) {
        try {
            presentationPropertiesBaseline = JSON.stringify(presentationPropertiesWorking);
        } catch (e) {
            presentationPropertiesBaseline = null;
        }
    }
}

/**
 * True if working copy or basic form fields differ from the load baseline.
 */
function isPresentationPropertiesDirty() {
    if (presentationPropertiesDirty) {
        return true;
    }
    if (!presentationPropertiesWorking || presentationPropertiesBaseline == null) {
        return false;
    }
    // Include current form basics without mutating working copy permanently
    let snap = JSON.parse(JSON.stringify(presentationPropertiesWorking));
    let nameEl = document.getElementById("presPropName");
    let descEl = document.getElementById("presPropDescription");
    let pathEl = document.getElementById("presPropVirtualPath");
    let themeEl = document.getElementById("presPropDefaultTheme");
    let darkThemeEl = document.getElementById("presPropDarkTheme");
    if (nameEl) {
        snap.name = nameEl.value.trim();
    }
    if (descEl) {
        snap.description = descEl.value;
    }
    if (pathEl) {
        snap.virtualPath = (pathEl.value || "").trim();
    }
    if (themeEl) {
        snap.defaultThemeName = themeEl.value;
    }
    if (darkThemeEl) {
        snap.darkThemeName = darkThemeEl.value || "";
    }
    try {
        return JSON.stringify(snap) !== presentationPropertiesBaseline;
    } catch (e) {
        return presentationPropertiesDirty;
    }
}

/**
 * Ensure the presentation title bar exists and is wired (edit: clickable; view: label only).
 * Positioned immediately after the canvas toolbar icons (see {@link positionPresentationTitleBar}).
 */
function installPresentationTitleBar() {
    let bar = document.getElementById("presentationTitleBar");
    let link = document.getElementById("presentationTitleLink");
    if (!bar || !link) {
        // Inject if template omitted it
        if (!bar && typeof presentationName !== "undefined" && presentationName) {
            bar = document.createElement("div");
            bar.id = "presentationTitleBar";
            bar.className = "presentation-title-bar"
                + (isEditMode() ? "" : " presentation-title-bar-view");
            if (isEditMode()) {
                bar.innerHTML = '<a href="#" id="presentationTitleLink" class="presentation-title-link"></a>';
            } else {
                bar.innerHTML = '<span id="presentationTitleLink" class="presentation-title-text"></span>';
            }
            document.body.insertBefore(bar, document.body.firstChild);
            link = document.getElementById("presentationTitleLink");
        }
    }
    if (!link || typeof presentationName === "undefined") {
        return;
    }
    link.textContent = presentationName || "";
    if (isEditMode() && link.tagName === "A" && !link._hopperPropsWired) {
        link._hopperPropsWired = true;
        link.title = "Edit presentation properties";
        link.addEventListener("click", function (e) {
            e.preventDefault();
            openPresentationProperties();
        });
    }
    // Color mode toggle (light / dark / system) — re-renders presentation canvas on change
    if (bar && typeof window.HThemeMode !== "undefined" && !bar._hopperThemeWired) {
        bar._hopperThemeWired = true;
        let host = document.createElement("span");
        host.id = "hopperThemeToggleHost";
        host.style.pointerEvents = "auto";
        // Order in strip: presentation name · theme toggle · auth
        if (link && link.parentNode === bar) {
            link.insertAdjacentElement("afterend", host);
        } else {
            bar.insertBefore(host, bar.firstChild);
        }
        window.HThemeMode.installToggle(host);
        window.HThemeMode.onChange(function () {
            // Swap chrome toolbar to dual static assets (no canvas invert)
            if (typeof refreshToolbarIconUrls === "function") {
                refreshToolbarIconUrls();
            }
            if (typeof loadIcons === "function") {
                loadIcons(true);
            }
            if (typeof softReloadEditor === "function" && isEditMode()) {
                softReloadEditor(
                    typeof window.hopperEdit !== "undefined"
                        && window.hopperEdit.getSelectedName
                        ? window.hopperEdit.getSelectedName()
                        : null
                );
            } else if (typeof presentationName !== "undefined" && presentationName
                && typeof openPresentation === "function" && isViewMode()) {
                openPresentation(presentationName, null, null);
            }
            if (typeof positionPresentationTitleBar === "function") {
                positionPresentationTitleBar();
            }
        });
    }
    // Keep title immediately after toolbar icons on resize / rail changes
    if (bar && !bar._hopperTitlePosWired) {
        bar._hopperTitlePosWired = true;
        window.addEventListener("resize", function () {
            positionPresentationTitleBar();
        });
    }
    positionPresentationTitleBar();
}

function updatePresentationTitleBar(name) {
    let link = document.getElementById("presentationTitleLink");
    if (link) {
        link.textContent = name || "";
    }
    if (typeof document !== "undefined" && name) {
        // Keep document title roughly in sync
        let t = document.title || "";
        if (t.indexOf("(edit)") >= 0 || t.indexOf("(view)") >= 0) {
            document.title = t.replace(/^[^ ]+/, name);
        }
    }
    if (typeof positionPresentationTitleBar === "function") {
        positionPresentationTitleBar();
    }
}

// ---------------------------------------------------------------------------
// Page properties (size, margins, presets, header/footer, component list)
// ---------------------------------------------------------------------------

/** Paper size presets in engine CSS-px (~96 dpi). Portrait dimensions. */
const PAGE_PAPER_PRESETS = {
    A4: {w: 794, h: 1123, label: "A4"},
    Letter: {w: 816, h: 1056, label: "US Letter"},
    Legal: {w: 816, h: 1344, label: "US Legal"},
    A3: {w: 1123, h: 1587, label: "A3"}
};

let pagePropertiesWorking = null;
let pagePropertiesLogicalIndex = 0;
let pagePropertiesDirty = false;

/**
 * Open the page properties side panel for a logical body page (0-based).
 */
function openPageProperties(logicalIndex) {
    if (!isEditMode()) {
        return;
    }
    if (typeof presentationName === "undefined" || !presentationName) {
        alert("No presentation is open");
        return;
    }
    let idx = parseInt(logicalIndex, 10);
    if (isNaN(idx) || idx < 0) {
        idx = 0;
    }
    pagePropertiesLogicalIndex = idx;
    setSidePanelOpen(true, {withPreview: false});
    let editArea = document.getElementById("editArea");
    if (editArea) {
        editArea.innerHTML = "<p class=\"editor-hint\">Loading page...</p>";
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/pages/" + encodeURIComponent(idx) + "/",
        type: "GET",
        dataType: "json",
        success: function (data) {
            pagePropertiesWorking = data || {};
            pagePropertiesLogicalIndex = data.logicalIndex != null ? data.logicalIndex : idx;
            pagePropertiesDirty = false;
            renderPagePropertiesForm();
        },
        error: function (xhr) {
            showAjaxError("Failed to load page properties", xhr);
            setSidePanelOpen(false);
        }
    });
}

function markPagePropertiesDirty() {
    pagePropertiesDirty = true;
}

function detectPaperPreset(width, height) {
    let w = parseInt(width, 10) || 0;
    let h = parseInt(height, 10) || 0;
    let portrait = h >= w;
    let pw = portrait ? w : h;
    let ph = portrait ? h : w;
    let keys = Object.keys(PAGE_PAPER_PRESETS);
    for (let i = 0; i < keys.length; i++) {
        let p = PAGE_PAPER_PRESETS[keys[i]];
        if (p.w === pw && p.h === ph) {
            return {preset: keys[i], portrait: portrait};
        }
    }
    return {preset: "custom", portrait: portrait};
}

function applyPaperPresetToForm(presetId, portrait) {
    let p = PAGE_PAPER_PRESETS[presetId];
    if (!p) {
        return;
    }
    let w = portrait ? p.w : p.h;
    let h = portrait ? p.h : p.w;
    let wEl = document.getElementById("pagePropWidth");
    let hEl = document.getElementById("pagePropHeight");
    if (wEl) {
        wEl.value = w;
    }
    if (hEl) {
        hEl.value = h;
    }
    markPagePropertiesDirty();
}

function renderPagePropertiesForm() {
    let d = pagePropertiesWorking;
    if (!d) {
        return;
    }
    let detected = detectPaperPreset(d.width, d.height);
    let presetOpts = "<option value=\"custom\""
        + (detected.preset === "custom" ? " selected" : "") + ">Custom</option>";
    Object.keys(PAGE_PAPER_PRESETS).forEach(function (key) {
        let p = PAGE_PAPER_PRESETS[key];
        presetOpts += "<option value=\"" + key + "\""
            + (detected.preset === key ? " selected" : "") + ">"
            + escapeHtmlText(p.label) + "</option>";
    });

    let header = d.header || {enabled: false, height: 50};
    let footer = d.footer || {enabled: false, height: 25};
    let label = d.label || ("Page " + ((d.logicalIndex || 0) + 1));
    let presLabel = (typeof presentationName !== "undefined" && presentationName)
        ? presentationName : "Presentation";

    let html = "";
    html += "<div class=\"form-action-bar\" id=\"formActionBar-page\">";
    html += "<button type=\"button\" class=\"form-action-save\" id=\"pagePropSave\">Save</button> ";
    html += "<button type=\"button\" class=\"form-action-close\" id=\"pagePropClose\">Close</button>";
    html += "</div>";

    html += "<nav class=\"component-breadcrumb\" id=\"componentBreadcrumb\" aria-label=\"Page location\">";
    html += "<button type=\"button\" class=\"component-breadcrumb-item component-breadcrumb-link\""
        + " id=\"pagePropCrumbPresentation\">" + escapeHtmlText(presLabel) + "</button>";
    html += "<span class=\"component-breadcrumb-sep\" aria-hidden=\"true\">›</span>";
    html += "<span class=\"component-breadcrumb-item component-breadcrumb-current\" aria-current=\"page\">"
        + escapeHtmlText(label) + "</span>";
    html += "</nav>";

    html += "<h3>Page properties</h3>";
    html += "<p id=\"pagePropStatus\" class=\"editor-hint\" hidden></p>";

    html += "<div class=\"pres-prop-section\">";
    html += "<h4>Paper size</h4>";
    html += "<label for=\"pagePropPreset\">Preset</label> ";
    html += "<select id=\"pagePropPreset\" class=\"pres-prop-input-sm\">" + presetOpts + "</select> ";
    html += "<label for=\"pagePropOrientation\">Orientation</label> ";
    html += "<select id=\"pagePropOrientation\" class=\"pres-prop-input-sm\">";
    html += "<option value=\"portrait\"" + (detected.portrait ? " selected" : "") + ">Portrait</option>";
    html += "<option value=\"landscape\"" + (!detected.portrait ? " selected" : "") + ">Landscape</option>";
    html += "</select><br>";
    html += "<label for=\"pagePropWidth\">Width</label> ";
    html += "<input type=\"number\" id=\"pagePropWidth\" class=\"pres-prop-num\" min=\"1\" value=\""
        + (d.width != null ? d.width : 1123) + "\"> ";
    html += "<label for=\"pagePropHeight\">Height</label> ";
    html += "<input type=\"number\" id=\"pagePropHeight\" class=\"pres-prop-num\" min=\"1\" value=\""
        + (d.height != null ? d.height : 794) + "\"> px";
    html += "<p class=\"editor-hint\">Sizes use the same CSS pixels as the engine (A4 portrait = 794×1123).</p>";
    html += "<label>Margins</label><br>";
    html += "L <input type=\"number\" id=\"pagePropLeftMargin\" class=\"pres-prop-num\" min=\"0\" value=\""
        + (d.leftMargin != null ? d.leftMargin : 25) + "\"> ";
    html += "R <input type=\"number\" id=\"pagePropRightMargin\" class=\"pres-prop-num\" min=\"0\" value=\""
        + (d.rightMargin != null ? d.rightMargin : 25) + "\"> ";
    html += "T <input type=\"number\" id=\"pagePropTopMargin\" class=\"pres-prop-num\" min=\"0\" value=\""
        + (d.topMargin != null ? d.topMargin : 25) + "\"> ";
    html += "B <input type=\"number\" id=\"pagePropBottomMargin\" class=\"pres-prop-num\" min=\"0\" value=\""
        + (d.bottomMargin != null ? d.bottomMargin : 25) + "\"> px";
    html += "</div>";

    html += "<div class=\"pres-prop-section\">";
    html += "<h4>Header / Footer</h4>";
    html += "<p class=\"editor-hint\">Header and footer apply to every page of this presentation.</p>";
    html += "<label><input type=\"checkbox\" id=\"pagePropHeaderEnabled\""
        + (header.enabled ? " checked" : "") + "> Header enabled</label> ";
    html += "height <input type=\"number\" id=\"pagePropHeaderHeight\" class=\"pres-prop-num\" min=\"1\" value=\""
        + (header.height != null ? header.height : 50) + "\"> px<br>";
    html += "<label><input type=\"checkbox\" id=\"pagePropFooterEnabled\""
        + (footer.enabled ? " checked" : "") + "> Footer enabled</label> ";
    html += "height <input type=\"number\" id=\"pagePropFooterHeight\" class=\"pres-prop-num\" min=\"1\" value=\""
        + (footer.height != null ? footer.height : 25) + "\"> px";
    html += "<p class=\"editor-hint\">Edit header/footer content by selecting components on the canvas bands.</p>";
    html += "</div>";

    html += "<div class=\"pres-prop-section\">";
    html += "<h4>Components on this page</h4>";
    html += "<p class=\"editor-hint\">Click a component name to open its editor. Use up/down/delete on each line.</p>";
    html += "<ul id=\"pageComponentList\" class=\"page-component-list\"></ul>";
    html += "<p class=\"editor-hint\" id=\"pageComponentListEmpty\">No components yet.</p>";
    html += "</div>";

    let editArea = document.getElementById("editArea");
    if (!editArea) {
        return;
    }
    editArea.innerHTML = html;

    document.getElementById("pagePropSave").onclick = function () {
        savePageProperties();
    };
    document.getElementById("pagePropClose").onclick = function () {
        closePageProperties();
    };
    document.getElementById("pagePropCrumbPresentation").onclick = function () {
        openPresentationProperties();
    };

    let presetEl = document.getElementById("pagePropPreset");
    let orientEl = document.getElementById("pagePropOrientation");
    function onPresetOrOrientChange() {
        let preset = presetEl.value;
        let portrait = orientEl.value === "portrait";
        if (preset !== "custom") {
            applyPaperPresetToForm(preset, portrait);
        } else if (presetEl._lastPreset && presetEl._lastPreset !== "custom") {
            // Switching to custom after a named preset: swap W/H if orientation flipped
            markPagePropertiesDirty();
        } else {
            markPagePropertiesDirty();
        }
        presetEl._lastPreset = preset;
    }
    presetEl._lastPreset = detected.preset;
    presetEl.addEventListener("change", onPresetOrOrientChange);
    orientEl.addEventListener("change", function () {
        let preset = presetEl.value;
        let portrait = orientEl.value === "portrait";
        if (preset !== "custom") {
            applyPaperPresetToForm(preset, portrait);
        } else {
            // Flip current W/H when orientation changes on custom
            let wEl = document.getElementById("pagePropWidth");
            let hEl = document.getElementById("pagePropHeight");
            if (wEl && hEl) {
                let tw = wEl.value;
                wEl.value = hEl.value;
                hEl.value = tw;
            }
            markPagePropertiesDirty();
        }
    });

    ["pagePropWidth", "pagePropHeight", "pagePropLeftMargin", "pagePropRightMargin",
        "pagePropTopMargin", "pagePropBottomMargin", "pagePropHeaderEnabled", "pagePropFooterEnabled",
        "pagePropHeaderHeight", "pagePropFooterHeight"].forEach(function (id) {
        let el = document.getElementById(id);
        if (el) {
            el.addEventListener("change", function () {
                if (id === "pagePropWidth" || id === "pagePropHeight") {
                    let det = detectPaperPreset(
                        document.getElementById("pagePropWidth").value,
                        document.getElementById("pagePropHeight").value
                    );
                    presetEl.value = det.preset;
                    orientEl.value = det.portrait ? "portrait" : "landscape";
                }
                markPagePropertiesDirty();
            });
            el.addEventListener("input", markPagePropertiesDirty);
        }
    });

    if (typeof window.hopperEdit !== "undefined"
        && typeof window.hopperEdit.fillComponentList === "function") {
        window.hopperEdit.fillComponentList(d.components || []);
    }
}

function collectPagePropertiesBody() {
    let hEn = document.getElementById("pagePropHeaderEnabled");
    let fEn = document.getElementById("pagePropFooterEnabled");
    let hH = document.getElementById("pagePropHeaderHeight");
    let fH = document.getElementById("pagePropFooterHeight");
    return {
        width: parseInt(document.getElementById("pagePropWidth").value, 10) || 1,
        height: parseInt(document.getElementById("pagePropHeight").value, 10) || 1,
        leftMargin: parseInt(document.getElementById("pagePropLeftMargin").value, 10) || 0,
        rightMargin: parseInt(document.getElementById("pagePropRightMargin").value, 10) || 0,
        topMargin: parseInt(document.getElementById("pagePropTopMargin").value, 10) || 0,
        bottomMargin: parseInt(document.getElementById("pagePropBottomMargin").value, 10) || 0,
        header: {
            enabled: hEn ? !!hEn.checked : false,
            height: hH ? (parseInt(hH.value, 10) || 50) : 50
        },
        footer: {
            enabled: fEn ? !!fEn.checked : false,
            height: fH ? (parseInt(fH.value, 10) || 25) : 25
        }
    };
}

function setPagePropertiesStatus(msg, isError) {
    let el = document.getElementById("pagePropStatus");
    if (!el) {
        return;
    }
    if (!msg) {
        el.setAttribute("hidden", "hidden");
        el.textContent = "";
        return;
    }
    el.removeAttribute("hidden");
    el.textContent = msg;
    el.style.color = isError ? "#a00" : "#234";
}

/**
 * Save page properties.
 * @param {function(Object=):void} [onSuccess] called after a successful save (optional)
 * @param {{skipSoftReload?:boolean, skipListRefresh?:boolean}} [options]
 */
function savePageProperties(onSuccess, options) {
    options = options || {};
    if (typeof presentationName === "undefined") {
        return;
    }
    let body = collectPagePropertiesBody();
    if (body.leftMargin + body.rightMargin >= body.width
        || body.topMargin + body.bottomMargin >= body.height) {
        setPagePropertiesStatus("Margins must leave a positive usable page area", true);
        return;
    }
    setPagePropertiesStatus("Saving...", false);
    let idx = pagePropertiesLogicalIndex;
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/pages/" + encodeURIComponent(idx) + "/",
        type: "POST",
        data: JSON.stringify(body),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (data) {
            pagePropertiesWorking = data || pagePropertiesWorking;
            pagePropertiesDirty = false;
            setPagePropertiesStatus("Saved", false);
            if (!options.skipSoftReload && typeof softReloadEditor === "function") {
                softReloadEditor();
            }
            if (typeof window.hopperEdit !== "undefined"
                && typeof window.hopperEdit.refreshHeaderFooter === "function") {
                window.hopperEdit.refreshHeaderFooter();
            }
            // Refresh list in panel from saved payload (skip when leaving for component editor)
            if (!options.skipListRefresh
                && data
                && typeof window.hopperEdit !== "undefined"
                && typeof window.hopperEdit.fillComponentList === "function") {
                window.hopperEdit.fillComponentList(data.components || []);
            }
            if (typeof onSuccess === "function") {
                onSuccess(data);
            }
        },
        error: function (xhr) {
            let msg = "Save failed: " + (xhr.responseText || xhr.status);
            setPagePropertiesStatus(msg, true);
            showAjaxError("Save page properties failed", xhr);
        }
    });
}

/**
 * If page properties are dirty, ask the user to save before continuing (e.g. open a component).
 * Cancel leaves the user on the page properties panel.
 * @param {function():void} onContinue called when it is safe to proceed (saved or not dirty)
 */
function confirmSavePagePropertiesIfDirty(onContinue) {
    if (typeof onContinue !== "function") {
        return;
    }
    // Only when page properties form is open
    if (!document.getElementById("pagePropSave")) {
        onContinue();
        return;
    }
    if (!pagePropertiesDirty) {
        onContinue();
        return;
    }
    if (!confirm(
        "This page has unsaved property changes.\n\n"
            + "Save them before editing the component?\n\n"
            + "OK = Save and continue\n"
            + "Cancel = Stay on page properties"
    )) {
        return;
    }
    savePageProperties(function () {
        onContinue();
    }, {skipListRefresh: true});
}

function closePageProperties() {
    if (pagePropertiesDirty) {
        if (!confirm("Discard unsaved page property changes?")) {
            return;
        }
    }
    pagePropertiesWorking = null;
    pagePropertiesDirty = false;
    setSidePanelOpen(false);
}

/**
 * Open the presentation properties side panel (edit mode).
 */
function openPresentationProperties() {
    if (!isEditMode()) {
        return;
    }
    if (typeof presentationName === "undefined" || !presentationName) {
        alert("No presentation is open");
        return;
    }
    if (themeNames === null) {
        themeNames = getThemeNames();
    }
    setSidePanelOpen(true, {withPreview: false});
    let editArea = document.getElementById("editArea");
    if (editArea) {
        editArea.innerHTML = "<p class=\"editor-hint\">Loading presentation...</p>";
    }
    $.ajax({
        url: API_BASE + "metadata/presentation/" + encodeURIComponent(presentationName),
        type: "GET",
        dataType: "json",
        success: function (json) {
            presentationJson = json || {};
            presentationPropertiesWorking = JSON.parse(JSON.stringify(presentationJson));
            if (!presentationPropertiesWorking.interactions) {
                presentationPropertiesWorking.interactions = [];
            }
            if (!presentationPropertiesWorking.parameterMappings) {
                presentationPropertiesWorking.parameterMappings = [];
            }
            if (!presentationPropertiesWorking.parameters) {
                presentationPropertiesWorking.parameters = [];
            }
            if (!presentationPropertiesWorking.themes) {
                presentationPropertiesWorking.themes = [];
            }
            if (!presentationPropertiesWorking.pages) {
                presentationPropertiesWorking.pages = [];
            }
            presentationPropertiesOldName = presentationPropertiesWorking.name || presentationName;
            presentationInteractionEditIndex = -1;
            presentationParamMapEditIndex = -1;
            clearPresentationPropertiesDirty();
            renderPresentationPropertiesForm();
        },
        error: function (xhr) {
            alert("Failed to load presentation: " + (xhr.responseText || xhr.status));
            setSidePanelOpen(false);
        }
    });
}

/** @deprecated use openPresentationProperties */
function editPresentationMetadata() {
    openPresentationProperties();
}

function renderPresentationPropertiesForm() {
    let w = presentationPropertiesWorking;
    if (!w) {
        return;
    }
    let themes = themeNames || getThemeNames() || [];
    let themeOpts = "";
    let darkThemeOpts = "<option value=\"\">(auto-derive from light)</option>";
    let defTheme = w.defaultThemeName || "Default";
    let darkTheme = w.darkThemeName || "";
    for (let i = 0; i < themes.length; i++) {
        let t = themes[i];
        if (!t) {
            continue;
        }
        themeOpts += "<option value=\"" + escapeHtmlAttribute(t) + "\""
            + (t === defTheme ? " selected" : "") + ">" + escapeHtmlText(t) + "</option>";
        darkThemeOpts += "<option value=\"" + escapeHtmlAttribute(t) + "\""
            + (t === darkTheme ? " selected" : "") + ">" + escapeHtmlText(t) + "</option>";
    }
    if (!themeOpts) {
        themeOpts = "<option value=\"Default\">Default</option>";
    }

    let html = "";
    html += "<div class=\"form-action-bar\" id=\"formActionBar-presentation\">";
    html += "<button type=\"button\" class=\"form-action-save\" id=\"presPropSave\">Save</button> ";
    html += "<button type=\"button\" class=\"form-action-close\" id=\"presPropClose\">Close</button>";
    html += "</div>";
    html += "<h3>Presentation properties</h3>";
    html += "<p id=\"presPropStatus\" class=\"editor-hint\" hidden></p>";

    html += "<div class=\"pres-prop-section\">";
    html += "<label for=\"presPropName\">Name</label><br>";
    html += "<input type=\"text\" id=\"presPropName\" class=\"pres-prop-input\" value=\""
        + escapeHtmlAttribute(w.name || "") + "\"><br>";
    html += "<label for=\"presPropDescription\">Description</label><br>";
    html += "<textarea id=\"presPropDescription\" class=\"pres-prop-textarea\" rows=\"2\">"
        + escapeHtmlText(w.description || "") + "</textarea><br>";
    html += "<label for=\"presPropVirtualPath\">Virtual path</label><br>";
    html += "<input type=\"text\" id=\"presPropVirtualPath\" class=\"pres-prop-input\" "
        + "placeholder=\"e.g. demos/sales\" value=\""
        + escapeHtmlAttribute(w.virtualPath || "") + "\"><br>";
    html += "<label for=\"presPropDefaultTheme\">Light theme (default)</label><br>";
    html += "<select id=\"presPropDefaultTheme\" class=\"pres-prop-input\">" + themeOpts + "</select>";
    html += "<p class=\"editor-hint\">Used when the UI color mode is light.</p>";
    html += "<label for=\"presPropDarkTheme\">Dark theme</label><br>";
    html += "<select id=\"presPropDarkTheme\" class=\"pres-prop-input\">" + darkThemeOpts + "</select>";
    html += "<p class=\"editor-hint\">Optional. When blank, a dark variant is derived from the light theme.</p>";
    html += "</div>";

    html += "<div class=\"pres-prop-section\">";
    html += "<div class=\"pres-prop-section-head\">";
    html += "<h4>Pages</h4>";
    html += "<button type=\"button\" id=\"presPropAddPage\" class=\"home-btn\" title=\"Add a page\">+ Add page</button>";
    html += "</div>";
    html += "<p class=\"editor-hint\">Open a page to edit size, margins, header/footer, and its components.</p>";
    html += "<ul class=\"pres-prop-page-list\" id=\"presPropPageList\">"
        + buildPresentationPagesListHtml(w) + "</ul>";
    html += "</div>";

    html += "<div class=\"pres-prop-section\">";
    html += "<div class=\"pres-prop-section-head\">";
    html += "<h4>Interactions</h4>";
    html += "<span>";
    html += "<button type=\"button\" id=\"presPropAddInteraction\" class=\"home-btn\" title=\"Blank interaction\">+ Add</button> ";
    html += "<button type=\"button\" id=\"presPropPresetTableDrill\" class=\"home-btn\" "
        + "title=\"Preset: table cell click opens another presentation\">Table drill-down</button>";
    html += "</span>";
    html += "</div>";
    html += "<p class=\"editor-hint\">Drill-down: table cell click opens another presentation "
        + "(optionally sets a parameter from the cell value). "
        + "Test interactions in <strong>view</strong> mode after Save.</p>";
    html += "<div id=\"presPropInteractionsList\"></div>";
    html += "<div id=\"presPropInteractionEditor\" class=\"pres-prop-nested-editor\" hidden></div>";
    html += "</div>";

    html += "<div class=\"pres-prop-section\">";
    html += "<div class=\"pres-prop-section-head\">";
    html += "<h4>Parameters</h4>";
    html += "<button type=\"button\" id=\"presPropAddParam\" class=\"home-btn\" title=\"Add parameter\">+ Add</button>";
    html += "</div>";
    html += "<p class=\"editor-hint\">Declare presentation parameters (Hop style): name, description, "
        + "and default value. Defaults apply after system variables and before request/interaction "
        + "values. Listed automatically in interaction mapping fields.</p>";
    html += "<div id=\"presPropParamsList\"></div>";
    html += "</div>";

    html += "<div class=\"pres-prop-section\">";
    html += "<div class=\"pres-prop-section-head\">";
    html += "<h4>Parameter mappings</h4>";
    html += "<button type=\"button\" id=\"presPropAddParamMap\" class=\"home-btn\">+ Add</button>";
    html += "</div>";
    html += "<p class=\"editor-hint\">Map connector fields to presentation parameters "
        + "(e.g. for labels using \${PARAM}). Optional default values seed parameters when "
        + "editing / previewing without a request or interaction value.</p>";
    html += "<div id=\"presPropParamMapsList\"></div>";
    html += "<div id=\"presPropParamMapEditor\" class=\"pres-prop-nested-editor\" hidden></div>";
    html += "</div>";

    let editArea = document.getElementById("editArea");
    if (!editArea) {
        return;
    }
    editArea.innerHTML = html;

    document.getElementById("presPropSave").onclick = function () {
        savePresentationProperties();
    };
    document.getElementById("presPropClose").onclick = function () {
        closePresentationProperties();
    };
    document.getElementById("presPropAddInteraction").onclick = function () {
        addPresentationInteraction(null);
    };
    document.getElementById("presPropPresetTableDrill").onclick = function () {
        addPresentationInteraction({preset: "table-drill"});
    };
    document.getElementById("presPropAddParamMap").onclick = function () {
        addPresentationParamMapping();
    };
    let addParamBtn = document.getElementById("presPropAddParam");
    if (addParamBtn) {
        addParamBtn.onclick = function () {
            addPresentationParameterDefinition();
        };
    }
    wirePresentationPagesList();
    let addPageBtn = document.getElementById("presPropAddPage");
    if (addPageBtn) {
        addPageBtn.onclick = function () {
            addPresentationPage();
        };
    }
    // Mark dirty when basic fields change
    ["presPropName", "presPropDescription", "presPropVirtualPath",
        "presPropDefaultTheme", "presPropDarkTheme"].forEach(function (id) {
        let el = document.getElementById(id);
        if (el) {
            el.addEventListener("change", markPresentationPropertiesDirty);
            el.addEventListener("input", markPresentationPropertiesDirty);
        }
    });

    refreshPresentationParameterDefinitionsList();
    refreshPresentationInteractionsList();
    refreshPresentationParamMapsList();
}

/**
 * Cache of presentation name → declared parameter names (from metadata).
 * Populated asynchronously via {@link ensurePresentationParameterNames}.
 * @type {Object.<string, string[]>}
 */
var presentationParameterNamesCache = presentationParameterNamesCache || {};

/**
 * Extract parameter definition names from a presentation JSON object.
 * @param {object|null} json
 * @returns {string[]}
 */
function extractParameterNamesFromPresentationJson(json) {
    let names = [];
    let seen = {};
    let defs = (json && json.parameters) || [];
    for (let i = 0; i < defs.length; i++) {
        let n = defs[i] && defs[i].name ? String(defs[i].name).trim() : "";
        if (!n || seen[n]) {
            continue;
        }
        seen[n] = true;
        names.push(n);
    }
    return names;
}

/**
 * Names from the currently open presentation's parameter definitions.
 * @returns {string[]}
 */
function getPresentationParameterDefinitionNames() {
    let src = presentationPropertiesWorking || presentationJson || {};
    return extractParameterNamesFromPresentationJson(src);
}

/**
 * Cached parameter names for a presentation (empty array if not loaded yet).
 * @param {string} presentationName
 * @returns {string[]}
 */
function getCachedPresentationParameterNames(presentationName) {
    if (!presentationName) {
        return [];
    }
    let cached = presentationParameterNamesCache[presentationName];
    return cached ? cached.slice() : [];
}

/**
 * Load parameter definition names for a presentation (cached). Calls onDone(names).
 * @param {string} targetPresentationName catalog name to load
 * @param {function(string[])} [onDone]
 */
function ensurePresentationParameterNames(targetPresentationName, onDone) {
    if (!targetPresentationName) {
        if (onDone) {
            onDone([]);
        }
        return;
    }
    if (Object.prototype.hasOwnProperty.call(
            presentationParameterNamesCache, targetPresentationName)) {
        if (onDone) {
            onDone(presentationParameterNamesCache[targetPresentationName].slice());
        }
        return;
    }

    // Prefer in-memory JSON when the target is the presentation currently open in the editor
    // (avoids a round-trip and picks up unsaved parameter definitions).
    let openName = null;
    try {
        // top-level editor var (not shadowed here)
        openName = presentationName;
    } catch (e) {
        openName = null;
    }
    if (openName && openName === targetPresentationName) {
        let live = presentationPropertiesWorking || presentationJson;
        if (live) {
            let liveNames = extractParameterNamesFromPresentationJson(live);
            presentationParameterNamesCache[targetPresentationName] = liveNames;
            if (onDone) {
                onDone(liveNames.slice());
            }
            return;
        }
    }

    $.ajax({
        url: API_BASE + "metadata/presentation/" + encodeURIComponent(targetPresentationName),
        type: "GET",
        dataType: "json",
        success: function (json) {
            let names = extractParameterNamesFromPresentationJson(json || {});
            presentationParameterNamesCache[targetPresentationName] = names;
            if (onDone) {
                onDone(names.slice());
            }
        },
        error: function () {
            presentationParameterNamesCache[targetPresentationName] = [];
            if (onDone) {
                onDone([]);
            }
        }
    });
}

/**
 * Invalidate cache entry when the open presentation's parameter list is edited.
 * @param {string} [targetPresentationName]
 */
function invalidatePresentationParameterNamesCache(targetPresentationName) {
    if (targetPresentationName) {
        delete presentationParameterNamesCache[targetPresentationName];
    } else {
        presentationParameterNamesCache = {};
    }
}

/**
 * Replace options of a &lt;datalist&gt; element.
 * @param {string} listId
 * @param {string[]} names
 */
function fillParameterNamesDatalist(listId, names) {
    let el = document.getElementById(listId);
    if (!el) {
        return;
    }
    let html = "";
    names = names || [];
    for (let i = 0; i < names.length; i++) {
        if (!names[i]) {
            continue;
        }
        html += "<option value=\"" + escapeHtmlAttribute(names[i]) + "\">";
    }
    el.innerHTML = html;
}

/**
 * HTML datalist for parameter name pickers.
 * @param {string} [listId]
 * @param {string[]} [names] when omitted, uses current presentation definitions
 */
function buildPresentationParameterDatalistHtml(listId, names) {
    let id = listId || "presParamNamesList";
    let opts = names != null ? names : getPresentationParameterDefinitionNames();
    let html = "<datalist id=\"" + id + "\">";
    for (let i = 0; i < opts.length; i++) {
        html += "<option value=\"" + escapeHtmlAttribute(opts[i]) + "\">";
    }
    html += "</datalist>";
    return html;
}

function refreshPresentationParameterDefinitionsList() {
    let root = document.getElementById("presPropParamsList");
    let w = presentationPropertiesWorking;
    if (!root || !w) {
        return;
    }
    if (!w.parameters) {
        w.parameters = [];
    }
    let rows = w.parameters;
    let html = "";
    if (!rows.length) {
        html += "<p class=\"editor-hint\">No parameters declared yet.</p>";
    } else {
        html += "<table class=\"pres-prop-map-table\" id=\"presPropParamsTable\"><thead><tr>"
            + "<th>Name</th><th>Description</th><th>Default value</th><th></th></tr></thead><tbody>";
        for (let i = 0; i < rows.length; i++) {
            let d = rows[i] || {};
            html += "<tr data-param-i=\"" + i + "\">";
            html += "<td><input type=\"text\" class=\"pres-prop-input pres-param-name\" data-i=\""
                + i + "\" value=\"" + escapeHtmlAttribute(d.name || "")
                + "\" placeholder=\"REGION\" autocomplete=\"off\"></td>";
            html += "<td><input type=\"text\" class=\"pres-prop-input pres-param-desc\" data-i=\""
                + i + "\" value=\"" + escapeHtmlAttribute(d.description || "")
                + "\" placeholder=\"Sales region filter\"></td>";
            html += "<td><input type=\"text\" class=\"pres-prop-input pres-param-default\" data-i=\""
                + i + "\" value=\"" + escapeHtmlAttribute(d.defaultValue || "")
                + "\" placeholder=\"EMEA or \${SYS_DEFAULT}\"></td>";
            html += "<td><button type=\"button\" class=\"home-btn pres-param-del\" data-i=\""
                + i + "\" title=\"Remove\">×</button></td>";
            html += "</tr>";
        }
        html += "</tbody></table>";
    }
    root.innerHTML = html;

    root.onchange = function (e) {
        syncPresentationParameterDefinitionsFromDom();
        markPresentationPropertiesDirty();
    };
    root.oninput = function (e) {
        syncPresentationParameterDefinitionsFromDom();
        markPresentationPropertiesDirty();
    };
    root.onclick = function (e) {
        let t = e.target;
        if (t && t.classList && t.classList.contains("pres-param-del")) {
            let i = parseInt(t.getAttribute("data-i"), 10);
            syncPresentationParameterDefinitionsFromDom();
            if (!isNaN(i) && w.parameters && i >= 0 && i < w.parameters.length) {
                w.parameters.splice(i, 1);
                markPresentationPropertiesDirty();
                refreshPresentationParameterDefinitionsList();
            }
        }
    };
}

function syncPresentationParameterDefinitionsFromDom() {
    let w = presentationPropertiesWorking;
    if (!w) {
        return;
    }
    let tbody = document.querySelector("#presPropParamsTable tbody");
    if (!tbody) {
        return;
    }
    let rows = tbody.querySelectorAll("tr");
    let next = [];
    for (let r = 0; r < rows.length; r++) {
        let tr = rows[r];
        let nameEl = tr.querySelector(".pres-param-name");
        let descEl = tr.querySelector(".pres-param-desc");
        let defEl = tr.querySelector(".pres-param-default");
        let name = nameEl ? (nameEl.value || "").trim() : "";
        next.push({
            name: name,
            description: descEl ? (descEl.value || "") : "",
            defaultValue: defEl ? (defEl.value || "") : ""
        });
    }
    w.parameters = next;
    // Keep parameter-name suggestions fresh when editing this presentation
    if (w.name) {
        presentationParameterNamesCache[w.name] =
            extractParameterNamesFromPresentationJson(w);
    }
}

function addPresentationParameterDefinition() {
    let w = presentationPropertiesWorking;
    if (!w) {
        return;
    }
    if (!w.parameters) {
        w.parameters = [];
    }
    syncPresentationParameterDefinitionsFromDom();
    w.parameters.push({name: "", description: "", defaultValue: ""});
    markPresentationPropertiesDirty();
    refreshPresentationParameterDefinitionsList();
}

/**
 * List of logical pages for the presentation properties panel.
 */
function buildPresentationPagesListHtml(w) {
    let pages = (w && w.pages) || [];
    if (!pages.length) {
        return "<li class=\"editor-hint\">No pages</li>";
    }
    let html = "";
    for (let i = 0; i < pages.length; i++) {
        let p = pages[i] || {};
        let summary = "";
        if (p.width && p.height) {
            summary = p.width + "×" + p.height;
        }
        html += "<li class=\"pres-prop-page-row\" data-page-index=\"" + i + "\">";
        html += "<button type=\"button\" class=\"pres-prop-page-link\" data-page-index=\"" + i + "\">"
            + "Page " + (i + 1)
            + (summary ? " <span class=\"editor-hint\">" + escapeHtmlText(summary) + "</span>" : "")
            + "</button>";
        html += "<span class=\"pres-prop-page-actions\">";
        html += "<button type=\"button\" class=\"pres-prop-page-icon-btn\" data-action=\"up\" data-page-index=\""
            + i + "\" title=\"Move up\" " + (i === 0 ? "disabled" : "") + ">"
            + uiIconImgTag("arrow-up.svg", "Up", 14) + "</button>";
        html += "<button type=\"button\" class=\"pres-prop-page-icon-btn\" data-action=\"down\" data-page-index=\""
            + i + "\" title=\"Move down\" " + (i === pages.length - 1 ? "disabled" : "") + ">"
            + uiIconImgTag("arrow-down.svg", "Down", 14) + "</button>";
        html += "<button type=\"button\" class=\"pres-prop-page-icon-btn\" data-action=\"delete\" data-page-index=\""
            + i + "\" title=\"Delete page\" " + (pages.length <= 1 ? "disabled" : "") + ">"
            + uiIconImgTag("delete.svg", "Delete", 14) + "</button>";
        html += "</span></li>";
    }
    return html;
}

function wirePresentationPagesList() {
    let list = document.getElementById("presPropPageList");
    if (!list || list._hopperPagesWired) {
        return;
    }
    list._hopperPagesWired = true;
    list.addEventListener("click", function (ev) {
        let link = ev.target.closest(".pres-prop-page-link");
        if (link && list.contains(link)) {
            ev.preventDefault();
            let idx = parseInt(link.getAttribute("data-page-index"), 10);
            if (!isNaN(idx)) {
                openPageProperties(idx);
            }
            return;
        }
        let btn = ev.target.closest(".pres-prop-page-icon-btn");
        if (!btn || !list.contains(btn) || btn.disabled) {
            return;
        }
        ev.preventDefault();
        let action = btn.getAttribute("data-action");
        let pidx = parseInt(btn.getAttribute("data-page-index"), 10);
        if (isNaN(pidx)) {
            return;
        }
        if (action === "delete") {
            deletePresentationPage(pidx);
        } else if (action === "up") {
            movePresentationPage(pidx, "up");
        } else if (action === "down") {
            movePresentationPage(pidx, "down");
        }
    });
}

function refreshPresentationPagesList() {
    let list = document.getElementById("presPropPageList");
    if (!list || !presentationPropertiesWorking) {
        return;
    }
    list._hopperPagesWired = false;
    list.innerHTML = buildPresentationPagesListHtml(presentationPropertiesWorking);
    wirePresentationPagesList();
}

function addPresentationPage() {
    if (typeof presentationName === "undefined") {
        return;
    }
    let pages = (presentationPropertiesWorking && presentationPropertiesWorking.pages) || [];
    let afterIndex = pages.length > 0 ? pages.length - 1 : -1;
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName) + "/pages/",
        type: "POST",
        data: JSON.stringify({afterIndex: afterIndex}),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (data) {
            // Reload presentation JSON so pages array stays accurate
            reloadPresentationPropertiesWorking(function () {
                refreshPresentationPagesList();
                if (typeof softReloadEditor === "function") {
                    softReloadEditor();
                }
                if (data && data.logicalIndex != null) {
                    // Optionally open the new page
                    // openPageProperties(data.logicalIndex);
                }
            });
        },
        error: function (xhr) {
            showAjaxError("Add page failed", xhr);
        }
    });
}

function deletePresentationPage(logicalIndex) {
    if (typeof presentationName === "undefined") {
        return;
    }
    let pages = (presentationPropertiesWorking && presentationPropertiesWorking.pages)
        || (presentationJson && presentationJson.pages)
        || [];
    // When metadata is loaded, refuse client-side if this is the only page
    if (pages.length === 1) {
        alert("Cannot delete the only page");
        return;
    }
    if (!confirm("Delete Page " + (logicalIndex + 1) + " and all of its components?")) {
        return;
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/pages/" + encodeURIComponent(logicalIndex) + "/",
        type: "DELETE",
        dataType: "json",
        success: function () {
            reloadPresentationPropertiesWorking(function () {
                refreshPresentationPagesList();
                if (typeof softReloadEditor === "function") {
                    softReloadEditor();
                }
            });
        },
        error: function (xhr) {
            showAjaxError("Delete page failed", xhr);
        }
    });
}

function movePresentationPage(logicalIndex, direction) {
    if (typeof presentationName === "undefined") {
        return;
    }
    $.ajax({
        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
            + "/pages/" + encodeURIComponent(logicalIndex) + "/move/",
        type: "POST",
        data: JSON.stringify({direction: direction}),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function () {
            reloadPresentationPropertiesWorking(function () {
                refreshPresentationPagesList();
                if (typeof softReloadEditor === "function") {
                    softReloadEditor();
                }
            });
        },
        error: function (xhr) {
            showAjaxError("Move page failed", xhr);
        }
    });
}

/** Reload presentationPropertiesWorking from metadata (keeps panel open). */
function reloadPresentationPropertiesWorking(done) {
    if (typeof presentationName === "undefined") {
        if (done) {
            done();
        }
        return;
    }
    $.ajax({
        url: API_BASE + "metadata/presentation/" + encodeURIComponent(presentationName),
        type: "GET",
        dataType: "json",
        success: function (json) {
            presentationJson = json || {};
            let interactions = presentationPropertiesWorking
                ? presentationPropertiesWorking.interactions : null;
            let paramMaps = presentationPropertiesWorking
                ? presentationPropertiesWorking.parameterMappings : null;
            presentationPropertiesWorking = JSON.parse(JSON.stringify(presentationJson));
            if (!presentationPropertiesWorking.interactions && interactions) {
                presentationPropertiesWorking.interactions = interactions;
            }
            if (!presentationPropertiesWorking.parameterMappings && paramMaps) {
                presentationPropertiesWorking.parameterMappings = paramMaps;
            }
            if (!presentationPropertiesWorking.pages) {
                presentationPropertiesWorking.pages = [];
            }
            if (done) {
                done();
            }
        },
        error: function (xhr) {
            showAjaxError("Failed to refresh presentation", xhr);
            if (done) {
                done();
            }
        }
    });
}

function collectPresentationPropertiesBasics() {
    let w = presentationPropertiesWorking;
    if (!w) {
        return;
    }
    let nameEl = document.getElementById("presPropName");
    let descEl = document.getElementById("presPropDescription");
    let pathEl = document.getElementById("presPropVirtualPath");
    let themeEl = document.getElementById("presPropDefaultTheme");
    let darkThemeEl = document.getElementById("presPropDarkTheme");
    if (nameEl) {
        w.name = nameEl.value.trim();
    }
    if (descEl) {
        w.description = descEl.value;
    }
    if (pathEl) {
        w.virtualPath = (pathEl.value || "").trim();
    }
    if (themeEl) {
        w.defaultThemeName = themeEl.value;
    }
    if (darkThemeEl) {
        w.darkThemeName = darkThemeEl.value || "";
    }
    syncPresentationParameterDefinitionsFromDom();
}

// ── Interactions ──────────────────────────────────────────────────────────

function interactionSummary(ix) {
    if (!ix) {
        return "(empty)";
    }
    let method = ix.method || {};
    let click = method.mouseDoubleClick ? "Double-click" : "Click";
    let loc = ix.location || {};
    // ASCII separators only (avoid UTF-8 mojibake if charset is wrong)
    let where = (loc.componentName || "?")
        + (loc.itemCategory ? " | " + loc.itemCategory : "")
        + (loc.dimensionColumns && loc.dimensionColumns.length
            ? " | [" + loc.dimensionColumns.join(", ") + "]" : "");
    let act = (ix.actions && ix.actions[0]) || {};
    let target = act.objectName
        ? ("-> " + act.objectName)
        : "-> (presentation = cell value)";
    if (act.valueParameter) {
        target += " (param " + act.valueParameter + ")";
    }
    if (act.dimensionParameters && act.dimensionParameters.length) {
        let dm = act.dimensionParameters.map(function (m) {
            return (m.dimensionColumn || "?") + "->" + (m.parameterName || "?");
        }).join(", ");
        target += " [" + dm + "]";
    }
    return click + " on " + where + " " + target;
}

function refreshPresentationInteractionsList() {
    let root = document.getElementById("presPropInteractionsList");
    if (!root || !presentationPropertiesWorking) {
        return;
    }
    let list = presentationPropertiesWorking.interactions || [];
    if (!list.length) {
        root.innerHTML = "<p class=\"editor-hint\">No interactions yet.</p>";
        return;
    }
    let html = "<ul class=\"pres-prop-card-list\">";
    for (let i = 0; i < list.length; i++) {
        html += "<li class=\"pres-prop-card\">";
        html += "<div class=\"pres-prop-card-summary\">" + escapeHtmlText(interactionSummary(list[i])) + "</div>";
        html += "<div class=\"pres-prop-card-actions\">";
        html += "<button type=\"button\" data-ix-edit=\"" + i + "\">Edit</button> ";
        html += "<button type=\"button\" data-ix-up=\"" + i + "\" title=\"Move up\">Up</button> ";
        html += "<button type=\"button\" data-ix-down=\"" + i + "\" title=\"Move down\">Down</button> ";
        html += "<button type=\"button\" data-ix-del=\"" + i + "\" title=\"Delete\">Del</button>";
        html += "</div></li>";
    }
    html += "</ul>";
    root.innerHTML = html;
    root.onclick = function (e) {
        let t = e.target;
        if (!t || !t.getAttribute) {
            return;
        }
        if (t.getAttribute("data-ix-edit") != null) {
            openPresentationInteractionEditor(parseInt(t.getAttribute("data-ix-edit"), 10));
        } else if (t.getAttribute("data-ix-del") != null) {
            let di = parseInt(t.getAttribute("data-ix-del"), 10);
            presentationPropertiesWorking.interactions.splice(di, 1);
            presentationInteractionEditIndex = -1;
            hidePresentationInteractionEditor();
            markPresentationPropertiesDirty();
            refreshPresentationInteractionsList();
        } else if (t.getAttribute("data-ix-up") != null) {
            let ui = parseInt(t.getAttribute("data-ix-up"), 10);
            if (ui > 0) {
                let a = presentationPropertiesWorking.interactions;
                let tmp = a[ui - 1];
                a[ui - 1] = a[ui];
                a[ui] = tmp;
                markPresentationPropertiesDirty();
                refreshPresentationInteractionsList();
            }
        } else if (t.getAttribute("data-ix-down") != null) {
            let di2 = parseInt(t.getAttribute("data-ix-down"), 10);
            let a2 = presentationPropertiesWorking.interactions;
            if (di2 < a2.length - 1) {
                let tmp2 = a2[di2 + 1];
                a2[di2 + 1] = a2[di2];
                a2[di2] = tmp2;
                markPresentationPropertiesDirty();
                refreshPresentationInteractionsList();
            }
        }
    };
}

/**
 * @param {{preset?: string}|null} opts  preset "table-drill" fills table cell location defaults
 */
function addPresentationInteraction(opts) {
    if (!presentationPropertiesWorking.interactions) {
        presentationPropertiesWorking.interactions = [];
    }
    let pageComps = (typeof window.hopperEdit !== "undefined" && window.hopperEdit.getPageComponents)
        ? (window.hopperEdit.getPageComponents() || [])
        : [];
    // Prefer first table-like component for drill-down preset
    let defaultComp = "";
    let defaultPlugin = "HTableComponent";
    for (let i = 0; i < pageComps.length; i++) {
        let p = pageComps[i];
        if (p && p.pluginId && String(p.pluginId).indexOf("Table") >= 0) {
            defaultComp = p.name || "";
            defaultPlugin = p.pluginId;
            break;
        }
    }
    if (!defaultComp && pageComps.length && pageComps[0].name) {
        defaultComp = pageComps[0].name;
        defaultPlugin = pageComps[0].pluginId || defaultPlugin;
    }
    let isPreset = opts && opts.preset === "table-drill";
    presentationPropertiesWorking.interactions.push({
        method: {mouseClick: true, mouseDoubleClick: false},
        location: {
            componentName: isPreset ? defaultComp : "",
            componentPluginId: isPreset ? defaultPlugin : "HTableComponent",
            itemType: "ComponentItem",
            itemCategory: "Cell",
            dimensionColumns: []
        },
        actions: [{
            actionType: "OPEN_PRESENTATION",
            objectName: "",
            valueParameter: "",
            dimensionParameters: []
        }]
    });
    markPresentationPropertiesDirty();
    let idx = presentationPropertiesWorking.interactions.length - 1;
    refreshPresentationInteractionsList();
    openPresentationInteractionEditor(idx);
}

function hidePresentationInteractionEditor() {
    let ed = document.getElementById("presPropInteractionEditor");
    if (ed) {
        ed.setAttribute("hidden", "hidden");
        ed.innerHTML = "";
    }
    presentationInteractionEditIndex = -1;
}

function openPresentationInteractionEditor(index) {
    let list = presentationPropertiesWorking.interactions || [];
    if (index < 0 || index >= list.length) {
        return;
    }
    presentationInteractionEditIndex = index;
    let ix = list[index];
    let loc = ix.location || {};
    let act = (ix.actions && ix.actions[0]) || {};
    let method = ix.method || {};
    let selectedDims = loc.dimensionColumns || [];

    // Prefer live page component list (name + pluginId) from edit mode
    let pageComps = (typeof window.hopperEdit !== "undefined" && window.hopperEdit.getPageComponents)
        ? (window.hopperEdit.getPageComponents() || [])
        : [];
    let pluginByName = {};
    for (let i = 0; i < pageComps.length; i++) {
        if (pageComps[i] && pageComps[i].name) {
            pluginByName[pageComps[i].name] = pageComps[i].pluginId || "";
        }
    }
    let componentNamesList = pageComps.length
        ? pageComps.map(function (c) {
            return c.name;
        }).filter(Boolean)
        : getPresentationComponentNamesForProps();

    let compOptions = "<option value=\"\">- select -</option>";
    for (let i = 0; i < componentNamesList.length; i++) {
        let cn = componentNamesList[i];
        compOptions += "<option value=\"" + escapeHtmlAttribute(cn) + "\""
            + (cn === (loc.componentName || "") ? " selected" : "") + ">"
            + escapeHtmlText(cn) + "</option>";
    }
    // Keep a free-text fallback if the stored name is not on the current page
    if (loc.componentName && componentNamesList.indexOf(loc.componentName) < 0) {
        compOptions += "<option value=\"" + escapeHtmlAttribute(loc.componentName)
            + "\" selected>" + escapeHtmlText(loc.componentName) + " (other page)</option>";
    }

    let presOptions = "<option value=\"\">(use clicked cell value)</option>";
    let presentations = getPresentationNamesList();
    for (let i = 0; i < presentations.length; i++) {
        let pn = presentations[i];
        if (!pn) {
            continue;
        }
        presOptions += "<option value=\"" + escapeHtmlAttribute(pn) + "\""
            + (pn === (act.objectName || "") ? " selected" : "") + ">"
            + escapeHtmlText(pn) + "</option>";
    }

    let colNames = getPresentationComponentColumnNames(loc.componentName || "");
    let dimCheckHtml = buildDimensionColumnsChecklist(colNames, selectedDims);

    let html = "<h5>Edit interaction</h5>";
    html += "<p class=\"editor-hint\">Preset tip: use <strong>Table drill-down</strong> for "
        + "cell click -&gt; OPEN_PRESENTATION.</p>";
    html += "<label>Method</label><br>";
    html += "<label><input type=\"radio\" name=\"ixMethod\" value=\"click\""
        + (!method.mouseDoubleClick ? " checked" : "") + "> Single click</label> ";
    html += "<label><input type=\"radio\" name=\"ixMethod\" value=\"dbl\""
        + (method.mouseDoubleClick ? " checked" : "") + "> Double click</label><br><br>";

    html += "<label for=\"ixComponentName\">Component name</label><br>";
    html += "<select id=\"ixComponentName\" class=\"pres-prop-input\">" + compOptions + "</select><br>";
    html += "<label for=\"ixPluginId\">Component plugin id</label><br>";
    html += "<input type=\"text\" id=\"ixPluginId\" class=\"pres-prop-input\" value=\""
        + escapeHtmlAttribute(loc.componentPluginId || "HTableComponent") + "\"><br>";
    html += "<label for=\"ixItemType\">Item type</label><br>";
    html += "<select id=\"ixItemType\" class=\"pres-prop-input\">";
    ["ComponentItem", "Component"].forEach(function (t) {
        html += "<option value=\"" + t + "\"" + (t === (loc.itemType || "ComponentItem") ? " selected" : "")
            + ">" + t + "</option>";
    });
    html += "</select><br>";
    html += "<label for=\"ixItemCategory\">Item category</label><br>";
    html += "<select id=\"ixItemCategory\" class=\"pres-prop-input\">";
    // Must match DrawnItem.Category names from component render (pie slices = ChartLabel)
    let categoryOptions = [
        "Cell", "Header", "ChartLabel", "ChartSeriesLabel", "LegendEntry",
        "Title", "Label", "XAxisLabel", "YAxisLabel", "GanttBar", ""
    ];
    let currentCat = loc.itemCategory != null ? loc.itemCategory : "Cell";
    if (currentCat && categoryOptions.indexOf(currentCat) < 0) {
        categoryOptions.unshift(currentCat);
    }
    categoryOptions.forEach(function (t) {
        let lab = t || "(any)";
        html += "<option value=\"" + escapeHtmlAttribute(t) + "\""
            + (t === currentCat ? " selected" : "") + ">" + lab + "</option>";
    });
    html += "</select><br>";
    html += "<label>Dimension columns</label>";
    html += "<p class=\"editor-hint\">Match only cells for these columns "
        + "(empty = any column). Required for multi-column tables.</p>";
    html += "<div id=\"ixDimensionsBox\" class=\"pres-prop-check-list\">" + dimCheckHtml + "</div>";
    html += "<label for=\"ixDimensionsExtra\">Extra dimensions (comma-separated)</label><br>";
    html += "<input type=\"text\" id=\"ixDimensionsExtra\" class=\"pres-prop-input\" value=\"\" "
        + "placeholder=\"optional names not listed above\"><br><br>";

    html += "<label>Action</label><br>";
    html += "<input type=\"hidden\" id=\"ixActionType\" value=\"OPEN_PRESENTATION\">";
    html += "<span class=\"editor-hint\">OPEN_PRESENTATION</span><br>";
    html += "<label for=\"ixObjectName\">Target presentation</label><br>";
    html += "<select id=\"ixObjectName\" class=\"pres-prop-input\">" + presOptions + "</select>";
    let targetPresName = (act.objectName || "").trim();
    let targetParamListId = "ixPresTargetParamList";
    let targetParamNames = targetPresName
        ? getCachedPresentationParameterNames(targetPresName)
        : getPresentationParameterDefinitionNames();
    if (targetPresName) {
        html += "<p class=\"editor-hint\" id=\"ixTargetParamHint\">Parameter names are suggested "
            + "from <strong>" + escapeHtmlText(targetPresName) + "</strong>"
            + (targetParamNames.length ? " (" + targetParamNames.length + " declared)" : " (loading…)")
            + ".</p>";
    } else {
        html += "<p class=\"editor-hint\" id=\"ixTargetParamHint\">Select a target presentation "
            + "to pick from its declared parameters in the fields below.</p>";
    }
    html += "<label for=\"ixValueParameter\">Set parameter from cell value</label><br>";
    html += "<input type=\"text\" id=\"ixValueParameter\" class=\"pres-prop-input\" list=\""
        + targetParamListId + "\" value=\""
        + escapeHtmlAttribute(act.valueParameter || "") + "\" "
        + "placeholder=\"target parameter name\" autocomplete=\"off\"><br>";
    // Dimension column → parameter mappings (crosstab / multi-dim hits)
    let dimMapCols = selectedDims.length ? selectedDims : colNames;
    html += buildDimensionParameterMapHtml(
        "ixPresDimMapBody",
        act.dimensionParameters || [],
        dimMapCols,
        true,
        targetParamListId);
    html += buildPresentationParameterDatalistHtml(targetParamListId, targetParamNames);
    html += "<br>";

    html += "<button type=\"button\" id=\"ixEditorOk\" class=\"form-action-save\">OK</button> ";
    html += "<button type=\"button\" id=\"ixEditorCancel\" class=\"form-action-close\">Cancel</button>";

    let ed = document.getElementById("presPropInteractionEditor");
    ed.innerHTML = html;
    ed.removeAttribute("hidden");
    wireDimensionParameterMapButtons(ed, dimMapCols, targetParamListId);

    function refreshIxPresTargetParamSuggestions() {
        let sel = document.getElementById("ixObjectName");
        let target = sel ? (sel.value || "").trim() : "";
        let hint = document.getElementById("ixTargetParamHint");
        let valueParam = document.getElementById("ixValueParameter");
        let dimMap = ed.querySelector(".ix-dim-param-map");
        if (!target) {
            let localNames = getPresentationParameterDefinitionNames();
            fillParameterNamesDatalist(targetParamListId, localNames);
            if (valueParam) {
                valueParam.setAttribute("list", targetParamListId);
            }
            if (dimMap) {
                dimMap.setAttribute("data-dimmap-param-list", targetParamListId);
            }
            if (hint) {
                hint.textContent = "Select a target presentation to pick from its declared parameters.";
            }
            return;
        }
        if (hint) {
            hint.innerHTML = "Parameter names are suggested from <strong>"
                + escapeHtmlText(target) + "</strong> (loading…).";
        }
        ensurePresentationParameterNames(target, function (names) {
            fillParameterNamesDatalist(targetParamListId, names);
            // Point all parameter name inputs at this list
            if (valueParam) {
                valueParam.setAttribute("list", targetParamListId);
            }
            let paramInputs = ed.querySelectorAll(".ix-dimmap-param");
            for (let pi = 0; pi < paramInputs.length; pi++) {
                paramInputs[pi].setAttribute("list", targetParamListId);
            }
            let addBtn = ed.querySelector(".ix-dimmap-add");
            if (addBtn) {
                addBtn.setAttribute("data-dimmap-param-list", targetParamListId);
            }
            if (dimMap) {
                dimMap.setAttribute("data-dimmap-param-list", targetParamListId);
            }
            if (hint) {
                hint.innerHTML = "Parameter names are suggested from <strong>"
                    + escapeHtmlText(target) + "</strong>"
                    + (names.length
                        ? " (" + names.length + " declared). Free text is still allowed."
                        : " (none declared — free text still allowed).");
            }
        });
    }

    if (targetPresName) {
        refreshIxPresTargetParamSuggestions();
    }
    let ixObj = document.getElementById("ixObjectName");
    if (ixObj) {
        ixObj.onchange = function () {
            refreshIxPresTargetParamSuggestions();
        };
    }

    document.getElementById("ixEditorOk").onclick = function () {
        commitPresentationInteractionEditor();
    };
    document.getElementById("ixEditorCancel").onclick = function () {
        hidePresentationInteractionEditor();
    };
    document.getElementById("ixComponentName").onchange = function () {
        let name = this.value;
        let pluginEl = document.getElementById("ixPluginId");
        if (pluginEl && name && pluginByName[name]) {
            pluginEl.value = pluginByName[name];
        }
        // Refresh dimension checklist for the selected component
        let box = document.getElementById("ixDimensionsBox");
        let cols = getPresentationComponentColumnNames(name);
        if (box) {
            let current = collectDimensionColumnsFromEditor();
            box.innerHTML = buildDimensionColumnsChecklist(cols, current);
        }
        // Refresh dimension mapping column selects with new available dims
        let dimTbody = document.getElementById("ixPresDimMapBody");
        if (dimTbody) {
            let listId = targetParamListId || "ixPresTargetParamList";
            let preserved = collectDimensionParameterMappingsFrom(dimTbody);
            dimTbody.innerHTML = "";
            if (!preserved.length) {
                dimTbody.insertAdjacentHTML(
                    "beforeend",
                    buildDimensionParameterMapRowHtml(cols, "", "", listId));
            } else {
                for (let r = 0; r < preserved.length; r++) {
                    dimTbody.insertAdjacentHTML(
                        "beforeend",
                        buildDimensionParameterMapRowHtml(
                            cols,
                            preserved[r].dimensionColumn,
                            preserved[r].parameterName,
                            listId));
                }
            }
            wireDimensionParameterMapButtons(ed, cols, listId);
        }
    };
}

function buildDimensionColumnsChecklist(colNames, selected) {
    selected = selected || [];
    let selectedSet = {};
    for (let i = 0; i < selected.length; i++) {
        selectedSet[selected[i]] = true;
    }
    if (!colNames || !colNames.length) {
        return "<p class=\"editor-hint\">No columns found for this component "
            + "(pick a table component or type extra names below).</p>";
    }
    let html = "";
    for (let i = 0; i < colNames.length; i++) {
        let c = colNames[i];
        html += "<label class=\"pres-prop-check\">"
            + "<input type=\"checkbox\" class=\"ix-dim-cb\" value=\""
            + escapeHtmlAttribute(c) + "\""
            + (selectedSet[c] ? " checked" : "") + "> "
            + escapeHtmlText(c) + "</label> ";
    }
    return html;
}

function collectDimensionColumnsFromEditor() {
    let dims = [];
    let boxes = document.querySelectorAll("#ixDimensionsBox .ix-dim-cb:checked");
    for (let i = 0; i < boxes.length; i++) {
        if (boxes[i].value) {
            dims.push(boxes[i].value);
        }
    }
    let extraEl = document.getElementById("ixDimensionsExtra");
    let extraRaw = extraEl ? (extraEl.value || "").trim() : "";
    if (extraRaw) {
        extraRaw.split(",").forEach(function (s) {
            s = s.trim();
            if (s && dims.indexOf(s) < 0) {
                dims.push(s);
            }
        });
    }
    return dims;
}

/**
 * Column names for a component: table columnSelection, else describe(sourceConnectorName).
 */
function getPresentationComponentColumnNames(componentName) {
    let names = [];
    if (!componentName || !presentationPropertiesWorking) {
        return names;
    }
    let pages = presentationPropertiesWorking.pages || [];
    function considerComponent(lc) {
        if (!lc || lc.name !== componentName) {
            return;
        }
        let pluginWrap = lc.component;
        if (!pluginWrap || typeof pluginWrap !== "object") {
            return;
        }
        let inner = null;
        let keys = Object.keys(pluginWrap);
        if (keys.length === 1 && typeof pluginWrap[keys[0]] === "object") {
            inner = pluginWrap[keys[0]];
        } else {
            inner = pluginWrap;
        }
        if (!inner) {
            return;
        }
        let cols = inner.columnSelection || inner.columns || [];
        for (let i = 0; i < cols.length; i++) {
            let cn = cols[i] && (cols[i].columnName || cols[i].name);
            if (cn && names.indexOf(cn) < 0) {
                names.push(cn);
            }
        }
        // Crosstab / charts: horizontal + vertical dimensions
        [["horizontalDimensions"], ["verticalDimensions"]].forEach(function (keyArr) {
            let dims = inner[keyArr[0]] || [];
            for (let d = 0; d < dims.length; d++) {
                let dn = dims[d] && (dims[d].columnName || dims[d].name);
                if (dn && names.indexOf(dn) < 0) {
                    names.push(dn);
                }
            }
        });
        if (!names.length && inner.sourceConnectorName
            && typeof getConnectorColumnNames === "function") {
            let fromConn = getConnectorColumnNames(inner.sourceConnectorName) || [];
            for (let j = 0; j < fromConn.length; j++) {
                if (fromConn[j] && names.indexOf(fromConn[j]) < 0) {
                    names.push(fromConn[j]);
                }
            }
        }
    }
    for (let p = 0; p < pages.length; p++) {
        let comps = (pages[p] && pages[p].components) || [];
        for (let c = 0; c < comps.length; c++) {
            considerComponent(comps[c]);
        }
    }
    // Header / footer components
    [["header"], ["footer"]].forEach(function (keyArr) {
        let band = presentationPropertiesWorking[keyArr[0]];
        if (band && band.components) {
            for (let i = 0; i < band.components.length; i++) {
                considerComponent(band.components[i]);
            }
        }
    });
    return names;
}

function commitPresentationInteractionEditor() {
    let idx = presentationInteractionEditIndex;
    if (idx < 0 || !presentationPropertiesWorking) {
        return;
    }
    let componentName = document.getElementById("ixComponentName").value || "";
    if (!componentName.trim()) {
        alert("Component name is required for the interaction location.");
        return;
    }
    let methodVal = document.querySelector("input[name=\"ixMethod\"]:checked");
    let isDbl = methodVal && methodVal.value === "dbl";
    let dims = collectDimensionColumnsFromEditor();
    let pluginId = (document.getElementById("ixPluginId").value || "").trim();
    // Prefer the real plugin id from the page component list when known
    if (typeof window.hopperEdit !== "undefined" && window.hopperEdit.getPageComponents) {
        let pcs = window.hopperEdit.getPageComponents() || [];
        for (let pi = 0; pi < pcs.length; pi++) {
            if (pcs[pi] && pcs[pi].name === componentName && pcs[pi].pluginId) {
                // If the plugin field was left blank or mistakenly set to a component name, fix it
                if (!pluginId || pluginId === componentName || !/^(Lean|H)[A-Z]/.test(pluginId)) {
                    pluginId = pcs[pi].pluginId;
                }
                break;
            }
        }
    }
    let itemType = document.getElementById("ixItemType").value || "ComponentItem";
    let itemCategory = document.getElementById("ixItemCategory").value || "";
    // Whole-component interactions do not use item category / dimensions
    if (itemType === "Component") {
        itemCategory = "";
        dims = [];
    }
    let dimParamMaps = collectDimensionParameterMappingsFrom(
        document.getElementById("ixPresDimMapBody"));
    let actionObj = {
        actionType: "OPEN_PRESENTATION",
        objectName: document.getElementById("ixObjectName").value || null,
        valueParameter: document.getElementById("ixValueParameter").value || null
    };
    if (dimParamMaps.length) {
        actionObj.dimensionParameters = dimParamMaps;
    }
    presentationPropertiesWorking.interactions[idx] = {
        method: {mouseClick: !isDbl, mouseDoubleClick: !!isDbl},
        location: {
            componentName: componentName,
            componentPluginId: pluginId,
            itemType: itemType,
            itemCategory: itemCategory,
            dimensionColumns: dims
        },
        actions: [actionObj]
    };
    // Clean null empty strings for Hop friendliness
    let a = presentationPropertiesWorking.interactions[idx].actions[0];
    if (!a.objectName) {
        delete a.objectName;
    }
    if (!a.valueParameter) {
        delete a.valueParameter;
    }
    markPresentationPropertiesDirty();
    hidePresentationInteractionEditor();
    refreshPresentationInteractionsList();
}

function getPresentationComponentNamesForProps() {
    let names = [];
    if (typeof presentationName === "undefined" || !presentationName) {
        return names;
    }
    // Prefer live page component list from edit mode if exposed
    if (typeof window.hopperEdit !== "undefined" && window.hopperEdit.getComponentNames) {
        return window.hopperEdit.getComponentNames() || [];
    }
    // Sync fetch component names for current page
    if (typeof renderId !== "undefined" && renderId) {
        $.ajax({
            url: API_BASE + "render/info/components/" + encodeURIComponent(renderId)
                + "/" + encodeURIComponent(renderPageNumber0 || 0) + "/",
            type: "GET",
            dataType: "json",
            async: false,
            success: function (list) {
                if (Array.isArray(list)) {
                    names = list;
                } else if (list && list.names) {
                    names = list.names;
                }
            }
        });
    }
    if (!names.length && typeof presentationName !== "undefined") {
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/pages/" + encodeURIComponent(renderPageNumber0 || 0) + "/components/",
            type: "GET",
            dataType: "json",
            async: false,
            success: function (list) {
                if (Array.isArray(list)) {
                    for (let i = 0; i < list.length; i++) {
                        if (list[i] && list[i].name) {
                            names.push(list[i].name);
                        }
                    }
                }
            }
        });
    }
    return names;
}

function getPresentationNamesList() {
    let names = [];
    $.ajax({
        url: API_BASE + "metadata/list/presentation/",
        type: "GET",
        dataType: "json",
        async: false,
        success: function (list) {
            names = list || [];
        },
        error: function () {
            $.ajax({
                url: API_BASE + "metadata/presentations/",
                type: "GET",
                dataType: "json",
                async: false,
                success: function (list) {
                    if (Array.isArray(list)) {
                        names = list.map(function (p) {
                            return p.name || p;
                        });
                    }
                }
            });
        }
    });
    // Case-insensitive A–Z for interaction / property pickers
    names = (names || []).filter(function (n) {
        return n != null && String(n) !== "";
    });
    names.sort(function (a, b) {
        return String(a).localeCompare(String(b), undefined, {sensitivity: "base"});
    });
    return names;
}

// ── Parameter mappings ────────────────────────────────────────────────────

function paramMapSummary(pm) {
    if (!pm) {
        return "(empty)";
    }
    let maps = pm.mappings || [];
    let bits = maps.map(function (m) {
        let s = (m.fieldName || "?") + "->" + (m.parameterName || "?");
        if (m.defaultValue) {
            s += " (default " + m.defaultValue + ")";
        }
        return s;
    });
    return (pm.connectorName || "?") + (bits.length ? ": " + bits.join(", ") : "");
}

function refreshPresentationParamMapsList() {
    let root = document.getElementById("presPropParamMapsList");
    if (!root || !presentationPropertiesWorking) {
        return;
    }
    let list = presentationPropertiesWorking.parameterMappings || [];
    if (!list.length) {
        root.innerHTML = "<p class=\"editor-hint\">No parameter mappings yet.</p>";
        return;
    }
    let html = "<ul class=\"pres-prop-card-list\">";
    for (let i = 0; i < list.length; i++) {
        html += "<li class=\"pres-prop-card\">";
        html += "<div class=\"pres-prop-card-summary\">" + escapeHtmlText(paramMapSummary(list[i])) + "</div>";
        html += "<div class=\"pres-prop-card-actions\">";
        html += "<button type=\"button\" data-pm-edit=\"" + i + "\">Edit</button> ";
        html += "<button type=\"button\" data-pm-up=\"" + i + "\" title=\"Move up\">Up</button> ";
        html += "<button type=\"button\" data-pm-down=\"" + i + "\" title=\"Move down\">Down</button> ";
        html += "<button type=\"button\" data-pm-del=\"" + i + "\" title=\"Delete\">Del</button>";
        html += "</div></li>";
    }
    html += "</ul>";
    root.innerHTML = html;
    root.onclick = function (e) {
        let t = e.target;
        if (!t || !t.getAttribute) {
            return;
        }
        if (t.getAttribute("data-pm-edit") != null) {
            openPresentationParamMapEditor(parseInt(t.getAttribute("data-pm-edit"), 10));
        } else if (t.getAttribute("data-pm-del") != null) {
            presentationPropertiesWorking.parameterMappings.splice(
                parseInt(t.getAttribute("data-pm-del"), 10), 1);
            hidePresentationParamMapEditor();
            markPresentationPropertiesDirty();
            refreshPresentationParamMapsList();
        } else if (t.getAttribute("data-pm-up") != null) {
            let ui = parseInt(t.getAttribute("data-pm-up"), 10);
            let a = presentationPropertiesWorking.parameterMappings;
            if (ui > 0) {
                let tmp = a[ui - 1];
                a[ui - 1] = a[ui];
                a[ui] = tmp;
                markPresentationPropertiesDirty();
                refreshPresentationParamMapsList();
            }
        } else if (t.getAttribute("data-pm-down") != null) {
            let di = parseInt(t.getAttribute("data-pm-down"), 10);
            let a = presentationPropertiesWorking.parameterMappings;
            if (di < a.length - 1) {
                let tmp = a[di + 1];
                a[di + 1] = a[di];
                a[di] = tmp;
                markPresentationPropertiesDirty();
                refreshPresentationParamMapsList();
            }
        }
    };
}

function addPresentationParamMapping() {
    if (!presentationPropertiesWorking.parameterMappings) {
        presentationPropertiesWorking.parameterMappings = [];
    }
    presentationPropertiesWorking.parameterMappings.push({
        connectorName: "",
        separator: "",
        mappings: [{fieldName: "", parameterName: "", defaultValue: ""}]
    });
    markPresentationPropertiesDirty();
    refreshPresentationParamMapsList();
    openPresentationParamMapEditor(presentationPropertiesWorking.parameterMappings.length - 1);
}

function hidePresentationParamMapEditor() {
    let ed = document.getElementById("presPropParamMapEditor");
    if (ed) {
        ed.setAttribute("hidden", "hidden");
        ed.innerHTML = "";
    }
    presentationParamMapEditIndex = -1;
}

function openPresentationParamMapEditor(index) {
    let list = presentationPropertiesWorking.parameterMappings || [];
    if (index < 0 || index >= list.length) {
        return;
    }
    presentationParamMapEditIndex = index;
    let pm = list[index];
    let connOpts = "<option value=\"\">- select -</option>";
    let conns = getConnectorNames().filter(function (n) {
        return n;
    });
    for (let i = 0; i < conns.length; i++) {
        connOpts += "<option value=\"" + escapeHtmlAttribute(conns[i]) + "\""
            + (conns[i] === (pm.connectorName || "") ? " selected" : "") + ">"
            + escapeHtmlText(conns[i]) + "</option>";
    }

    let fieldNames = pm.connectorName
        ? (getConnectorColumnNames(pm.connectorName) || [])
        : [];
    let rows = pm.mappings || [];
    let mapRows = "";
    for (let r = 0; r < rows.length; r++) {
        mapRows += buildParamMapFieldRowHtml(r, rows[r].fieldName || "",
            rows[r].parameterName || "", rows[r].defaultValue || "", fieldNames);
    }
    if (!mapRows) {
        mapRows = buildParamMapFieldRowHtml(0, "", "", "", fieldNames);
    }

    let html = "<h5>Edit parameter mapping</h5>";
    html += "<label for=\"pmConnector\">Connector</label><br>";
    html += "<select id=\"pmConnector\" class=\"pres-prop-input\">" + connOpts + "</select><br>";
    html += "<label for=\"pmSeparator\">Separator (multi-row join)</label><br>";
    html += "<input type=\"text\" id=\"pmSeparator\" class=\"pres-prop-input\" value=\""
        + escapeHtmlAttribute(pm.separator || "") + "\"><br>";
    html += "<p class=\"editor-hint\">Default value is for authoring preview when the parameter is "
        + "not passed in (labels show it instead of ${PARAM}). Request/interaction parameters "
        + "always overwrite it at layout. Multi-row connector mapping with a blank separator does "
        + "not fill the parameter (avoids concatenating all values).</p>";
    html += "<table class=\"pres-prop-map-table\"><thead><tr>"
        + "<th>Field name</th><th>Parameter name</th><th>Default value</th><th></th>"
        + "</tr></thead>";
    html += "<tbody id=\"pmMapBody\">" + mapRows + "</tbody></table>";
    html += "<button type=\"button\" id=\"pmAddRow\">+ Field</button><br><br>";
    html += "<button type=\"button\" id=\"pmEditorOk\" class=\"form-action-save\">OK</button> ";
    html += "<button type=\"button\" id=\"pmEditorCancel\" class=\"form-action-close\">Cancel</button>";

    let ed = document.getElementById("presPropParamMapEditor");
    ed.innerHTML = html;
    ed.removeAttribute("hidden");

    document.getElementById("pmConnector").onchange = function () {
        let cname = this.value;
        let cols = cname ? (getConnectorColumnNames(cname) || []) : [];
        // Rebuild field selects, preserve parameter names / defaults when possible
        let body = document.getElementById("pmMapBody");
        if (!body) {
            return;
        }
        let preserved = [];
        for (let i = 0; i < body.rows.length; i++) {
            let row = body.rows[i];
            let fieldInp = row.querySelector(".pm-field");
            let paramInp = row.querySelector(".pm-param");
            let defInp = row.querySelector(".pm-default");
            preserved.push({
                fieldName: fieldInp ? fieldInp.value : "",
                parameterName: paramInp ? paramInp.value : "",
                defaultValue: defInp ? defInp.value : ""
            });
        }
        body.innerHTML = "";
        if (!preserved.length) {
            preserved = [{fieldName: "", parameterName: "", defaultValue: ""}];
        }
        for (let r = 0; r < preserved.length; r++) {
            body.insertAdjacentHTML("beforeend",
                buildParamMapFieldRowHtml(r, preserved[r].fieldName,
                    preserved[r].parameterName, preserved[r].defaultValue, cols));
        }
    };
    document.getElementById("pmAddRow").onclick = function () {
        let body = document.getElementById("pmMapBody");
        let cname = (document.getElementById("pmConnector") || {}).value || "";
        let cols = cname ? (getConnectorColumnNames(cname) || []) : [];
        let r = body.rows.length;
        body.insertAdjacentHTML("beforeend", buildParamMapFieldRowHtml(r, "", "", "", cols));
    };
    ed.onclick = function (e) {
        let t = e.target;
        if (t && t.getAttribute && t.getAttribute("data-pm-row-del") != null) {
            let tr = t.closest("tr");
            if (tr) {
                tr.parentNode.removeChild(tr);
            }
        }
    };
    document.getElementById("pmEditorOk").onclick = function () {
        commitPresentationParamMapEditor();
    };
    document.getElementById("pmEditorCancel").onclick = function () {
        hidePresentationParamMapEditor();
    };
}

/**
 * @param {number} r row index
 * @param {string} fieldName
 * @param {string} parameterName
 * @param {string} defaultValue
 * @param {string[]} fieldNames connector columns for the field select
 */
function buildParamMapFieldRowHtml(r, fieldName, parameterName, defaultValue, fieldNames) {
    fieldNames = fieldNames || [];
    let html = "<tr>";
    html += "<td>";
    if (fieldNames.length) {
        html += "<select class=\"pm-field\" data-r=\"" + r + "\">";
        html += "<option value=\"\">- field -</option>";
        let found = false;
        for (let i = 0; i < fieldNames.length; i++) {
            let fn = fieldNames[i];
            let sel = (fn === fieldName) ? " selected" : "";
            if (fn === fieldName) {
                found = true;
            }
            html += "<option value=\"" + escapeHtmlAttribute(fn) + "\"" + sel + ">"
                + escapeHtmlText(fn) + "</option>";
        }
        if (fieldName && !found) {
            html += "<option value=\"" + escapeHtmlAttribute(fieldName)
                + "\" selected>" + escapeHtmlText(fieldName) + " (custom)</option>";
        }
        html += "</select>";
    } else {
        html += "<input type=\"text\" class=\"pm-field\" data-r=\"" + r + "\" value=\""
            + escapeHtmlAttribute(fieldName || "") + "\" placeholder=\"field name\">";
    }
    html += "</td>";
    html += "<td><input type=\"text\" class=\"pm-param\" data-r=\"" + r + "\" value=\""
        + escapeHtmlAttribute(parameterName || "") + "\" placeholder=\"PARAM_NAME\"></td>";
    html += "<td><input type=\"text\" class=\"pm-default\" data-r=\"" + r + "\" value=\""
        + escapeHtmlAttribute(defaultValue || "") + "\" placeholder=\"optional\" "
        + "title=\"Used when the parameter is not already set (preview / edit)\"></td>";
    html += "<td><button type=\"button\" data-pm-row-del=\"" + r + "\">x</button></td>";
    html += "</tr>";
    return html;
}

function commitPresentationParamMapEditor() {
    let idx = presentationParamMapEditIndex;
    if (idx < 0 || !presentationPropertiesWorking) {
        return;
    }
    let connectorName = (document.getElementById("pmConnector").value || "").trim();
    if (!connectorName) {
        alert("Connector is required for a parameter mapping.");
        return;
    }
    let mappings = [];
    let body = document.getElementById("pmMapBody");
    if (body) {
        for (let i = 0; i < body.rows.length; i++) {
            let row = body.rows[i];
            let fieldInp = row.querySelector(".pm-field");
            let paramInp = row.querySelector(".pm-param");
            let defInp = row.querySelector(".pm-default");
            let fn = fieldInp ? fieldInp.value.trim() : "";
            let pn = paramInp ? paramInp.value.trim() : "";
            let dv = defInp ? defInp.value.trim() : "";
            if (fn || pn || dv) {
                if (!fn || !pn) {
                    alert("Each mapping row needs both a field name and a parameter name.");
                    return;
                }
                let m = {fieldName: fn, parameterName: pn};
                if (dv) {
                    m.defaultValue = dv;
                }
                mappings.push(m);
            }
        }
    }
    if (!mappings.length) {
        alert("Add at least one field to parameter mapping.");
        return;
    }
    presentationPropertiesWorking.parameterMappings[idx] = {
        connectorName: connectorName,
        separator: document.getElementById("pmSeparator").value || "",
        mappings: mappings
    };
    markPresentationPropertiesDirty();
    hidePresentationParamMapEditor();
    refreshPresentationParamMapsList();
}

// ── Save / close ──────────────────────────────────────────────────────────

function setPresentationPropertiesStatus(msg, isError) {
    let el = document.getElementById("presPropStatus");
    if (!el) {
        return;
    }
    if (!msg) {
        el.setAttribute("hidden", "hidden");
        el.textContent = "";
        return;
    }
    el.removeAttribute("hidden");
    el.textContent = msg;
    el.style.color = isError ? "#a00" : "#245";
}

/**
 * Validate interactions and parameter mappings before save.
 * @returns {string|null} error message or null if ok
 */
function validatePresentationPropertiesWorking(w) {
    if (!w.name || !w.name.trim()) {
        return "Presentation name is required.";
    }
    let ixs = w.interactions || [];
    for (let i = 0; i < ixs.length; i++) {
        let loc = (ixs[i] && ixs[i].location) || {};
        if (!loc.componentName || !String(loc.componentName).trim()) {
            return "Interaction #" + (i + 1) + " needs a component name.";
        }
        let acts = (ixs[i] && ixs[i].actions) || [];
        if (!acts.length || !acts[0].actionType) {
            return "Interaction #" + (i + 1) + " needs an action.";
        }
    }
    let params = w.parameters || [];
    let seenParam = {};
    for (let pi = 0; pi < params.length; pi++) {
        let pn = params[pi] && params[pi].name ? String(params[pi].name).trim() : "";
        if (!pn) {
            return "Parameter #" + (pi + 1) + " needs a name.";
        }
        if (seenParam[pn]) {
            return "Duplicate parameter name: " + pn;
        }
        seenParam[pn] = true;
    }
    let pms = w.parameterMappings || [];
    for (let j = 0; j < pms.length; j++) {
        let pm = pms[j] || {};
        if (!pm.connectorName || !String(pm.connectorName).trim()) {
            return "Parameter mapping #" + (j + 1) + " needs a connector.";
        }
        let maps = pm.mappings || [];
        if (!maps.length) {
            return "Parameter mapping #" + (j + 1) + " needs at least one field mapping.";
        }
        for (let k = 0; k < maps.length; k++) {
            if (!maps[k].fieldName || !maps[k].parameterName) {
                return "Parameter mapping #" + (j + 1)
                    + " row " + (k + 1) + " needs field and parameter names.";
            }
        }
    }
    return null;
}

function savePresentationProperties() {
    if (!presentationPropertiesWorking) {
        return;
    }
    // Commit open nested editors first
    if (presentationInteractionEditIndex >= 0
        && document.getElementById("presPropInteractionEditor")
        && !document.getElementById("presPropInteractionEditor").hasAttribute("hidden")) {
        // commit may alert and leave editor open on validation failure
        let beforeIx = presentationInteractionEditIndex;
        commitPresentationInteractionEditor();
        if (presentationInteractionEditIndex === beforeIx
            && document.getElementById("presPropInteractionEditor")
            && !document.getElementById("presPropInteractionEditor").hasAttribute("hidden")) {
            return;
        }
    }
    if (presentationParamMapEditIndex >= 0
        && document.getElementById("presPropParamMapEditor")
        && !document.getElementById("presPropParamMapEditor").hasAttribute("hidden")) {
        let beforePm = presentationParamMapEditIndex;
        commitPresentationParamMapEditor();
        if (presentationParamMapEditIndex === beforePm
            && document.getElementById("presPropParamMapEditor")
            && !document.getElementById("presPropParamMapEditor").hasAttribute("hidden")) {
            return;
        }
    }
    collectPresentationPropertiesBasics();
    let w = presentationPropertiesWorking;
    let validationError = validatePresentationPropertiesWorking(w);
    if (validationError) {
        setPresentationPropertiesStatus(validationError, true);
        alert(validationError);
        return;
    }
    let oldName = presentationPropertiesOldName;
    let newName = w.name.trim();
    w.name = newName;

    setPresentationPropertiesStatus("Saving...", false);

    function doPost() {
        $.ajax({
            url: API_BASE + "metadata/presentation/",
            type: "POST",
            data: JSON.stringify(w),
            contentType: "application/json; charset=utf-8",
            dataType: "text",
            success: function (savedName) {
                let finalName = savedName || newName;
                // Rename: delete old if name changed
                if (oldName && finalName && oldName !== finalName) {
                    $.ajax({
                        url: API_BASE + "metadata/presentation/" + encodeURIComponent(oldName),
                        type: "DELETE",
                        async: false
                    });
                }
                presentationName = finalName;
                presentationJson = w;
                presentationPropertiesOldName = finalName;
                clearPresentationPropertiesDirty();
                updatePresentationTitleBar(finalName);
                // If renamed, navigate to new editor URL so bookmarks stay valid
                if (oldName && finalName && oldName !== finalName) {
                    let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
                    window.open(
                        API_BASE + "edit/presentation/" + encodeURIComponent(finalName)
                            + "/?colorMode=" + encodeURIComponent(cm),
                        "_self"
                    );
                    return;
                }
                if (typeof softReloadEditor === "function") {
                    softReloadEditor();
                }
                setPresentationPropertiesStatus("Saved: " + finalName, false);
            },
            error: function (xhr) {
                let msg = "Save failed: " + (xhr.responseText || xhr.status);
                setPresentationPropertiesStatus(msg, true);
                alert(msg);
            }
        });
    }

    // Drop legacy embedded themes/connectors if present on the working copy
    delete w.themes;
    delete w.connectors;
    doPost();
}

function closePresentationProperties() {
    if (presentationPropertiesWorking && isPresentationPropertiesDirty()) {
        if (!confirm("Discard unsaved presentation property changes?")) {
            return;
        }
    }
    presentationPropertiesWorking = null;
    presentationPropertiesBaseline = null;
    presentationPropertiesDirty = false;
    presentationInteractionEditIndex = -1;
    presentationParamMapEditIndex = -1;
    setSidePanelOpen(false);
}

/** @deprecated */
function savePresentation() {
    savePresentationProperties();
}

function closePresentation() {
    closePresentationProperties();
}

// ---------------------------------------------------------------------------
// Nested HComponent editors (Group.groupComponent, Composite.children, …)
// Driven by window.componentCatalog from generated form schemas.
// ---------------------------------------------------------------------------

let nestedComponentSeq = 0;

function catalogById(pluginId) {
    if (!window.componentCatalog) {
        return null;
    }
    for (let i = 0; i < window.componentCatalog.length; i++) {
        if (window.componentCatalog[i].pluginId === pluginId) {
            return window.componentCatalog[i];
        }
    }
    return null;
}

function catalogPluginIds() {
    if (!window.componentCatalog) {
        return [];
    }
    return window.componentCatalog.map(c => c.pluginId);
}

function initNestedComponentPanel(prefix) {
    let panel = document.getElementById(prefix + "_panel");
    if (panel === null) {
        return;
    }
    panel.innerHTML = buildNestedComponentShellHtml(prefix, false);
    wireNestedComponentShell(prefix);
}

function initNestedComponentList(prefix) {
    let items = document.getElementById(prefix + "_items");
    if (items === null) {
        return;
    }
    items.innerHTML = "";
}

function buildNestedComponentShellHtml(prefix, withRemove) {
    let options = "";
    let ids = catalogPluginIds();
    for (let i = 0; i < ids.length; i++) {
        let info = catalogById(ids[i]);
        let label = info && info.name ? info.name : ids[i];
        options += '<option value="' + ids[i] + '">' + label + " (" + ids[i] + ")</option>";
    }
    let removeBtn = withRemove
        ? '<button type="button" onclick="nestedComponentListRemove(this)">Remove</button>'
        : "";
    return ""
        + '<div class="nested-component-shell" data-prefix="' + prefix + '">'
        + '  <label>Name </label><input type="text" id="' + prefix + '_name" style="width: 40%">'
        + '  <label> Type </label><select id="' + prefix + '_pluginId" style="width: 40%">' + options + '</select>'
        + "  " + removeBtn + "<br>"
        + '  <div id="' + prefix + '_pluginFields" class="nested-plugin-fields"></div>'
        + '  <button type="button" class="collapsible nested-layout-toggle">Layout</button>'
        + '  <div class="content nested-layout" id="' + prefix + '_layout" style="display: none;">'
        + buildNestedLayoutHtml(prefix)
        + "  </div>"
        + "</div>";
}

function buildNestedLayoutHtml(prefix) {
    let sides = ["left", "right", "top", "bottom"];
    let html = "";
    for (let s = 0; s < sides.length; s++) {
        let side = sides[s];
        let cap = side.charAt(0).toUpperCase() + side.slice(1);
        html += '<fieldset class="hopper-fieldset layout-side-fieldset">'
            + "<legend>" + cap + "</legend>"
            + '<label><input type="checkbox" id="' + prefix + '_' + side + 'Enabled"> enabled</label> '
            + 'to <select id="' + prefix + '_' + side + 'ObjectName" style="width:40%"'
            + ' data-empty-label="' + EMPTY_OPTION_RELATIVE_TO_COMPOSITE + '"'
            + ' title="Leave empty to attach relative to the composite"></select><br>'
            + 'Offset <input type="text" id="' + prefix + '_' + side + 'Offset" style="width:15%"> '
            + 'Pct <input type="text" id="' + prefix + '_' + side + 'Percentage" style="width:15%"> '
            + 'From <select id="' + prefix + '_' + side + 'Alignment" style="width:20%"></select>'
            + "</fieldset>";
    }
    return html;
}

function wireNestedComponentShell(prefix) {
    let typeSelect = document.getElementById(prefix + "_pluginId");
    if (typeSelect !== null) {
        typeSelect.onchange = function () {
            rebuildNestedPluginFields(prefix, typeSelect.value, null);
        };
        if (typeSelect.options.length > 0 && !typeSelect.value) {
            typeSelect.selectedIndex = 0;
        }
        if (typeSelect.value) {
            rebuildNestedPluginFields(prefix, typeSelect.value, null);
        }
    }
    // Nested layout: relative only to sibling components in the same composite (not page).
    // Empty = relative to composite edges.
    fillNestedLayoutRelativeOptions(prefix);
    let nameEl = document.getElementById(prefix + "_name");
    if (nameEl && !nameEl._hopperSiblingLayoutWired) {
        nameEl._hopperSiblingLayoutWired = true;
        let onNameChange = function () {
            refreshSiblingLayoutRelativeOptions(prefix);
        };
        nameEl.addEventListener("change", onNameChange);
        nameEl.addEventListener("input", onNameChange);
    }
    // layout alignment options
    let sides = ["left", "right", "top", "bottom"];
    for (let s = 0; s < sides.length; s++) {
        let side = sides[s];
        let align = document.getElementById(prefix + "_" + side + "Alignment");
        if (align) {
            // HAttachment.Alignment (CENTER), not content HVerticalAlignment (MIDDLE)
            let vals = (side === "left" || side === "right")
                ? (typeof LAYOUT_HORIZONTAL_ALIGNMENTS !== "undefined"
                    ? LAYOUT_HORIZONTAL_ALIGNMENTS
                    : ["DEFAULT", "LEFT", "RIGHT", "CENTER"])
                : (typeof LAYOUT_VERTICAL_ALIGNMENTS !== "undefined"
                    ? LAYOUT_VERTICAL_ALIGNMENTS
                    : ["DEFAULT", "TOP", "BOTTOM", "CENTER"]);
            align.innerHTML = "";
            for (let i = 0; i < vals.length; i++) {
                addOptionToSelect(align, vals[i]);
            }
        }
    }
    // collapsible for this shell only
    let shell = document.querySelector('.nested-component-shell[data-prefix="' + prefix + '"]');
    if (shell) {
        let toggles = shell.querySelectorAll(".nested-layout-toggle");
        for (let t = 0; t < toggles.length; t++) {
            toggles[t].onclick = function () {
                let c = this.nextElementSibling;
                if (c.style.display === "block") {
                    c.style.display = "none";
                } else {
                    c.style.display = "block";
                }
            };
        }
    }
}

/**
 * Nested list item element for a shell prefix, or null when not in a component list
 * (e.g. Group.groupComponent single nested panel).
 */
function findNestedListItemForPrefix(prefix) {
    let shell = document.querySelector('.nested-component-shell[data-prefix="' + prefix + '"]');
    if (!shell) {
        // list item may wrap the shell
        let item = document.querySelector('.nested-component-list-item[data-prefix="' + prefix + '"]');
        return item;
    }
    return shell.closest(".nested-component-list-item");
}

/**
 * Relative-to options for nested layout: empty + sibling names in the same composite list.
 * Does not include page-level components (cannot layout against those).
 * @param {string} prefix nested shell prefix
 * @returns {string[]} always starts with ""
 */
function getNestedLayoutRelativeComponentNames(prefix) {
    let names = [""];
    let listItem = findNestedListItemForPrefix(prefix);
    if (!listItem || !listItem.parentElement) {
        return names;
    }
    let siblings = listItem.parentElement.querySelectorAll(":scope > .nested-component-list-item");
    for (let i = 0; i < siblings.length; i++) {
        let p = siblings[i].getAttribute("data-prefix");
        if (!p || p === prefix) {
            continue;
        }
        let nameEl = document.getElementById(p + "_name");
        let n = nameEl ? (nameEl.value || "").trim() : "";
        if (n && names.indexOf(n) < 0) {
            names.push(n);
        }
    }
    return names;
}

/**
 * Fill left/right/top/bottom ObjectName selects for one nested shell.
 * Empty option is labeled "(relative to composite)".
 */
function fillNestedLayoutRelativeOptions(prefix) {
    let names = getNestedLayoutRelativeComponentNames(prefix);
    let sides = ["left", "right", "top", "bottom"];
    for (let s = 0; s < sides.length; s++) {
        let id = prefix + "_" + sides[s] + "ObjectName";
        if (document.getElementById(id)) {
            setSelectOptions(id, names, EMPTY_OPTION_RELATIVE_TO_COMPOSITE);
        }
    }
}

/**
 * Refresh relative-to options for every shell in the same nested component list
 * (or just this shell when not in a list).
 */
function refreshSiblingLayoutRelativeOptions(prefix) {
    let listItem = findNestedListItemForPrefix(prefix);
    if (!listItem || !listItem.parentElement) {
        fillNestedLayoutRelativeOptions(prefix);
        return;
    }
    let siblings = listItem.parentElement.querySelectorAll(":scope > .nested-component-list-item");
    for (let i = 0; i < siblings.length; i++) {
        let p = siblings[i].getAttribute("data-prefix");
        if (p) {
            fillNestedLayoutRelativeOptions(p);
        }
    }
}

/**
 * After loading or mutating a nested component list, refresh all layout relative-to dropdowns.
 * @param {string} listPrefix prefix of the list field (items live in listPrefix + "_items")
 */
function refreshNestedComponentListLayoutOptions(listPrefix) {
    let items = document.getElementById(listPrefix + "_items");
    if (!items) {
        return;
    }
    let shells = items.querySelectorAll(":scope > .nested-component-list-item");
    for (let i = 0; i < shells.length; i++) {
        let p = shells[i].getAttribute("data-prefix");
        if (p) {
            fillNestedLayoutRelativeOptions(p);
        }
    }
}

function rebuildNestedPluginFields(prefix, pluginId, values) {
    let container = document.getElementById(prefix + "_pluginFields");
    if (container === null) {
        return;
    }
    container.innerHTML = "";
    let info = catalogById(pluginId);
    if (info === null || !info.sections) {
        container.innerHTML = "<em>No form schema for " + pluginId + "</em>";
        return;
    }
    let pluginValues = values || {};
    for (let s = 0; s < info.sections.length; s++) {
        let section = info.sections[s];
        let title = section.title || section.id;
        let open = section.openByDefault ? "block" : "none";
        let secId = prefix + "_sec_" + section.id;
        let secHtml = '<button type="button" class="collapsible nested-sec-toggle">' + title + "</button>"
            + '<div class="content" id="' + secId + '" style="display: ' + open + ';">';
        container.insertAdjacentHTML("beforeend", secHtml);
        let secDiv = document.getElementById(secId);
        for (let f = 0; f < (section.fields || []).length; f++) {
            appendNestedFieldControl(secDiv, prefix, section.fields[f], pluginValues);
        }
        // wire section toggle
        let btn = secDiv.previousElementSibling;
        if (btn) {
            btn.onclick = function () {
                let c = this.nextElementSibling;
                c.style.display = c.style.display === "block" ? "none" : "block";
            };
        }
    }
}

function nestedFieldDomId(prefix, field) {
    return prefix + "_f_" + field.id;
}

/**
 * @param {Array.<string>|null} [columnNamesOverride] chain-builder upstream columns
 */
function appendNestedFieldControl(container, prefix, field, pluginValues, columnNamesOverride) {
    let domId = nestedFieldDomId(prefix, field);
    let type = field.type;
    let label = field.label || field.fieldName || field.id;
    let val = pluginValues ? pluginValues[field.fieldName] : null;

    if (type === "BUTTON") {
        let safeName = String(field.fieldName || field.id || "")
            .replace(/\\/g, "\\\\").replace(/'/g, "\\'");
        let tip = field.toolTip
            ? ' title="' + String(field.toolTip).replace(/&/g, "&amp;").replace(/"/g, "&quot;") + '"'
            : "";
        container.insertAdjacentHTML("beforeend",
            '<div class="form-field-button-row">'
            + '<button type="button" class="form-field-btn" id="' + domId + '"' + tip
            + " onclick=\"if(typeof hopperFormButtonClick==='function')hopperFormButtonClick('"
            + safeName + "');\">" + label + "</button></div>");
        return;
    }

    if (type === "COMPONENT") {
        let wrap = document.createElement("div");
        wrap.innerHTML = '<fieldset class="hopper-fieldset"><legend>'
            + label + '</legend><div id="' + domId + '_panel" data-prefix="' + domId + '"></div></fieldset>';
        container.appendChild(wrap);
        let panel = document.getElementById(domId + "_panel");
        panel.innerHTML = buildNestedComponentShellHtml(domId, false);
        wireNestedComponentShell(domId);
        if (val) {
            loadNestedComponentIntoPanel(domId, val);
        }
        return;
    }

    if (type === "LIST" && field.itemKind === "component") {
        let wrap = document.createElement("div");
        wrap.innerHTML = '<fieldset class="hopper-fieldset"><legend>'
            + label + '</legend>'
            + '<div id="' + domId + '_items"></div>'
            + '<button type="button" onclick="nestedComponentListAdd(\'' + domId + '\')">Add child</button>'
            + "</fieldset>";
        container.appendChild(wrap);
        if (val && Array.isArray(val)) {
            setNestedComponentList({[field.fieldName]: val}, field.fieldName, domId);
        }
        return;
    }

    if (type === "LIST" && field.itemKind === "connector") {
        let wrap = document.createElement("div");
        wrap.innerHTML = '<fieldset class="nested-connector-list-fieldset hopper-fieldset">'
            + "<legend>" + label + "</legend>"
            + '<div id="' + domId + '_items" class="nested-connector-list" data-prefix="' + domId + '"></div>'
            + '<button type="button" onclick="nestedConnectorListAdd(\'' + domId + '\')">Add step</button>'
            + "</fieldset>";
        container.appendChild(wrap);
        let tmp = {};
        tmp[field.fieldName] = val || [];
        setNestedConnectorList(tmp, field.fieldName, domId);
        return;
    }

    if (type === "LIST") {
        // column / fact / string tables — Add/Delete in header, Up/Down on rows
        let tableId = domId;
        let kind = field.itemKind || "column";
        let headers;
        if (kind === "fact") {
            headers = "<tr><th>Column</th><th>Header</th><th>Width</th><th>H</th><th>V</th><th>Format</th><th>H-Agg</th><th>V-Agg</th><th>Method</th><th></th><th></th><th></th></tr>";
        } else if (kind === "string") {
            headers = "<tr><th>Value</th><th></th><th></th><th></th></tr>";
        } else if (kind === "sort") {
            headers = "<tr><th>Type</th><th>Ascending</th><th></th><th></th><th></th></tr>";
        } else if (kind === "filter") {
            headers = "<tr><th>Field name</th><th>Filter value</th><th></th><th></th><th></th></tr>";
        } else if (kind === "groupKey") {
            headers = "<tr><th>Group column</th><th>Connector column</th><th></th><th></th><th></th></tr>";
        } else if (kind === "jsonField") {
            headers = "<tr><th>JSON tag</th><th>Name</th><th>Type</th><th>Format</th><th>Length</th><th>Precision</th><th></th><th></th><th></th></tr>";
        } else if (kind === "csvField") {
            headers = "<tr><th>Name</th><th>Type</th><th>Format</th><th>Length</th><th>Precision</th><th></th><th></th><th></th></tr>";
        } else if (kind === "connector" || kind === "bean") {
            headers = "<tr><th>Plugin JSON (advanced)</th><th></th><th></th><th></th></tr>";
        } else {
            headers = "<tr><th>Column</th><th>Header</th><th>Width</th><th>H</th><th>V</th><th>Format</th><th></th><th></th><th></th></tr>";
        }
        let wrap = document.createElement("div");
        wrap.innerHTML =
            '<div class="list-field-header">'
            + "<label>" + label + "</label>"
            + '<span class="list-field-toolbar">'
            + '<button type="button" class="list-toolbar-btn" title="Add row" onclick="listFieldAdd(\'' + tableId + '\')">'
            + uiIconImgTag("add-item.svg", "Add", 16)
            + "</button>"
            + "</span></div>"
            + '<table id="' + tableId + '" class="list-field-table" data-list-kind="' + kind
            + '" data-column-prefix="' + domId + '">' + headers + "</table>";
        container.appendChild(wrap);
        let colNames = [];
        if (columnNamesOverride && Array.isArray(columnNamesOverride)) {
            colNames = columnNamesOverride.slice();
        } else {
            let sourceName = pluginValues ? pluginValues["sourceConnectorName"] : null;
            // Fall back to top-level source only when not in a nested chain step form
            if (!sourceName && prefix !== "chainStep") {
                let topSrc = document.getElementById("sourceConnectorName");
                sourceName = topSrc ? topSrc.value : null;
            }
            colNames = (typeof getConnectorColumnNames === "function" && sourceName)
                ? getConnectorColumnNames(sourceName) : [];
        }
        let tmp = {};
        tmp[field.fieldName] = val || [];
        if (kind === "fact") {
            setFacts(tmp, field.fieldName, tableId, domId, colNames);
        } else if (kind === "string") {
            setStringList(tmp, field.fieldName, tableId);
        } else if (kind === "sort") {
            setSortMethods(tmp, field.fieldName, tableId);
        } else if (kind === "filter") {
            setFilterValues(tmp, field.fieldName, tableId, colNames);
        } else if (kind === "groupKey") {
            setGroupKeyMappings(tmp, field.fieldName, tableId);
        } else if (kind === "jsonField") {
            setJsonFields(tmp, field.fieldName, tableId);
        } else if (kind === "csvField") {
            setCsvFields(tmp, field.fieldName, tableId);
        } else if (kind === "bean") {
            setJsonObjectList(tmp, field.fieldName, tableId);
        } else {
            setColumns(tmp, field.fieldName, tableId, domId, colNames);
        }
        // Remember override for listFieldAdd new rows
        let tableEl = document.getElementById(tableId);
        if (tableEl && colNames && colNames.length) {
            tableEl.setAttribute("data-column-names", JSON.stringify(colNames));
        }
        return;
    }

    if (type === "CHECKBOX") {
        let checked = val === true ? " checked" : "";
        container.insertAdjacentHTML("beforeend",
            '<input type="checkbox" id="' + domId + '"' + checked + '> <label for="' + domId + '">' + label + "</label><br>");
        return;
    }

    if (type === "COMBO" || type === "METADATA") {
        let source = field.comboSource || "none";
        let options = field.comboValues || [];
        if (source === "connectorColumns" && columnNamesOverride && Array.isArray(columnNamesOverride)) {
            options = columnNamesOverride.slice();
        } else if (source && source !== "none") {
            options = resolveSelectSourceValues(source, {
                dependsOn: field.comboDependsOn || "sourceConnectorName",
                metadataKey: field.metadataKey || "",
                staticValues: field.comboValues || []
            });
        } else {
            if (field.fieldName === "themeName") {
                options = themeNames || getThemeNames();
            }
            if (field.fieldName === "sourceConnectorName") {
                options = connectorNames || getPresentationConnectorNames();
            }
        }
        // Preserve stored combo value when not in live options (e.g. connector columns offline)
        let missingVal = false;
        if (val !== null && val !== undefined && val !== ""
            && !optionsIncludeValue(options, val)) {
            options = [val].concat(options || []);
            missingVal = true;
        }
        let preserveAttr = "";
        if (val !== null && val !== undefined && val !== "") {
            preserveAttr = ' data-preserve-value="'
                + String(val).replace(/&/g, "&amp;").replace(/"/g, "&quot;") + '"';
        }
        let optionHtml = "";
        for (let i = 0; i < options.length; i++) {
            let isMissing = missingVal && String(options[i]) === String(val);
            let display = optionDisplayText(options[i], isMissing);
            let sel = (val !== null && val !== undefined && String(val) === String(options[i]))
                ? " selected" : "";
            let safeVal = String(options[i] == null ? "" : options[i])
                .replace(/&/g, "&amp;").replace(/"/g, "&quot;");
            let safeDisplay = String(display).replace(/&/g, "&amp;").replace(/</g, "&lt;");
            optionHtml += '<option value="' + safeVal + '"' + sel
                + (isMissing ? ' data-missing-source="true"' : "")
                + ">" + safeDisplay + "</option>";
        }
        // Nested / client-built forms: match server HTML for the top-level input connector
        if (field.fieldName === "sourceConnectorName" || field.id === "sourceConnectorName") {
            let rowHtml = '<div class="source-connector-row" id="sourceConnectorRow">'
                + '<label for="' + domId + '" class="source-connector-label">' + label + "</label>"
                + '<select id="' + domId + '" class="source-connector-select" style="width:50%"'
                + preserveAttr + ">" + optionHtml + "</select>"
                + '<span class="source-connector-actions">'
                + '<button type="button" class="source-connector-btn" title="Preview sample data from this connector" '
                + 'onclick="if(typeof previewSourceConnectorData===\'function\')previewSourceConnectorData();">'
                + uiIconImgTag("connector-sample-data.svg", "Preview data", 18)
                + "</button>"
                + '<button type="button" class="source-connector-btn" title="Show field layout (column names and types)" '
                + 'onclick="if(typeof previewSourceConnectorLayout===\'function\')previewSourceConnectorLayout();">'
                + uiIconImgTag("connector-metadata.svg", "Field layout", 18)
                + "</button>"
                + "</span></div>"
                + '<div id="sourceConnectorInspect" class="source-connector-inspect" hidden>'
                + '<div class="source-connector-inspect-header">'
                + '<span id="sourceConnectorInspectTitle" class="source-connector-inspect-title"></span>'
                + '<button type="button" class="source-connector-inspect-close" title="Close" '
                + 'onclick="if(typeof closeSourceConnectorInspect===\'function\')closeSourceConnectorInspect();">×</button>'
                + "</div>"
                + '<div id="sourceConnectorInspectBody" class="source-connector-inspect-body"></div>'
                + "</div>";
            container.insertAdjacentHTML("beforeend", rowHtml);
            return;
        }
        let html = "<label for=\"" + domId + "\">" + label + ' </label><select id="'
            + domId + '" style="width:50%"' + preserveAttr + ">";
        html += optionHtml;
        html += "</select><br>";
        container.insertAdjacentHTML("beforeend", html);
        if (source === "connectorColumns") {
            connectorColumnSelects.push({
                selectId: domId,
                dependsOn: (prefix ? prefix + "_f_" : "") + (field.comboDependsOn || "sourceConnectorName")
            });
        }
        return;
    }

    if (type === "COLOR") {
        // simplified color: optional enable + color input
        let setId = domId + "_set";
        let has = val !== null && val !== undefined;
        let hex = has ? rgbToHex(val.r, val.g, val.b) : "#000000";
        container.insertAdjacentHTML("beforeend",
            '<input type="checkbox" id="' + setId + '"' + (has ? " checked" : "") + "> "
            + "<label>" + label + ' </label><input type="color" id="' + domId + '" value="' + hex + '"><br>');
        return;
    }

    if (type === "FONT") {
        let setId = domId + "_set";
        let has = val !== null && val !== undefined;
        let name = has ? (val.fontName || "") : "";
        let size = has ? (val.fontSize || "") : "";
        let bold = has && val.bold ? " checked" : "";
        let italic = has && val.italic ? " checked" : "";
        let listId = ensureFontNameSuggestionsDatalist();
        let safeName = String(name)
            .replace(/&/g, "&amp;")
            .replace(/"/g, "&quot;")
            .replace(/</g, "&lt;");
        let safeSize = String(size)
            .replace(/&/g, "&amp;")
            .replace(/"/g, "&quot;");
        container.insertAdjacentHTML("beforeend",
            '<input type="checkbox" id="' + setId + '"' + (has ? " checked" : "") + "> "
            + "<label>" + label + ' </label>'
            + '<input type="text" id="' + domId + 'Name" value="' + safeName + '"'
            + ' list="' + listId + '" autocomplete="off" placeholder="Font name"'
            + ' title="Common fonts: ' + COMMON_FONT_NAMES.join(", ") + '"'
            + ' style="width:25%">'
            + '<input type="text" id="' + domId + 'Size" value="' + safeSize + '" style="width:10%"'
            + ' placeholder="Size" title="Font size">'
            + " bold<input type=\"checkbox\" id=\"" + domId + "Bold\"" + bold + ">"
            + " italic<input type=\"checkbox\" id=\"" + domId + "Italic\"" + italic + "><br>");
        return;
    }

    // MULTI_LINE_TEXT
    if (type === "MULTI_LINE_TEXT") {
        let textVal = val === null || val === undefined ? "" : String(val);
        let rows = Math.max(1, field.multiLineTextHeight || 4);
        container.insertAdjacentHTML("beforeend",
            "<label for=\"" + domId + "\">" + label + " </label><br>"
            + '<textarea id="' + domId + '" rows="' + rows + '" style="width:90%">'
            + textVal.replace(/&/g, "&amp;").replace(/</g, "&lt;")
            + "</textarea><br>");
        return;
    }

    // TEXT and default
    let textVal = val === null || val === undefined ? "" : val;
    container.insertAdjacentHTML("beforeend",
        "<label for=\"" + domId + "\">" + label + ' </label>'
        + '<input type="text" id="' + domId + '" value="' + String(textVal).replace(/"/g, "&quot;") + '"><br>');
}

function setNestedComponent(parentObj, fieldName, prefix) {
    initNestedComponentPanel(prefix);
    let nested = parentObj[fieldName];
    if (nested === null || nested === undefined) {
        return;
    }
    loadNestedComponentIntoPanel(prefix, nested);
}

function loadNestedComponentIntoPanel(prefix, nested) {
    let nameEl = document.getElementById(prefix + "_name");
    if (nameEl) {
        nameEl.value = nested.name || "";
    }
    let pluginMap = nested.component || {};
    let pluginId = Object.keys(pluginMap)[0];
    let typeSelect = document.getElementById(prefix + "_pluginId");
    if (typeSelect && pluginId) {
        typeSelect.value = pluginId;
        rebuildNestedPluginFields(prefix, pluginId, pluginMap[pluginId] || {});
    }
    loadNestedLayout(prefix, nested.layout);
}

function loadNestedLayout(prefix, layout) {
    if (!layout) {
        return;
    }
    let sides = ["left", "right", "top", "bottom"];
    for (let s = 0; s < sides.length; s++) {
        let side = sides[s];
        let att = layout[side];
        let enabled = document.getElementById(prefix + "_" + side + "Enabled");
        if (!enabled) {
            continue;
        }
        if (att) {
            enabled.checked = true;
            let obj = document.getElementById(prefix + "_" + side + "ObjectName");
            if (obj) {
                obj.value = att.componentName || "";
            }
            let off = document.getElementById(prefix + "_" + side + "Offset");
            if (off) {
                off.value = att.offset !== undefined ? att.offset : 0;
            }
            let pct = document.getElementById(prefix + "_" + side + "Percentage");
            if (pct) {
                pct.value = att.percentage !== undefined ? att.percentage : 0;
            }
            let al = document.getElementById(prefix + "_" + side + "Alignment");
            if (al) {
                al.value = att.alignment || "DEFAULT";
            }
        } else {
            enabled.checked = false;
        }
    }
}

function getNestedComponent(parentObj, fieldName, prefix) {
    parentObj[fieldName] = readNestedComponentFromPanel(prefix);
}

function readNestedComponentFromPanel(prefix) {
    let nameEl = document.getElementById(prefix + "_name");
    let typeSelect = document.getElementById(prefix + "_pluginId");
    if (!nameEl || !typeSelect) {
        return null;
    }
    let pluginId = typeSelect.value;
    let info = catalogById(pluginId);
    let pluginValues = {};
    if (info && info.sections) {
        for (let s = 0; s < info.sections.length; s++) {
            let fields = info.sections[s].fields || [];
            for (let f = 0; f < fields.length; f++) {
                readNestedFieldValue(prefix, fields[f], pluginValues);
            }
        }
    }
    pluginValues["pluginId"] = pluginId;
    return {
        "name": nameEl.value,
        "layout": readNestedLayout(prefix),
        "component": (function () {
            let m = {};
            m[pluginId] = pluginValues;
            return m;
        })()
    };
}

function readNestedFieldValue(prefix, field, pluginValues) {
    let domId = nestedFieldDomId(prefix, field);
    let type = field.type;
    let key = field.fieldName;

    // Action-only widgets have no value binding
    if (type === "BUTTON" || type === "LINK") {
        return;
    }

    if (type === "COMPONENT") {
        pluginValues[key] = readNestedComponentFromPanel(domId);
        return;
    }
    if (type === "LIST" && field.itemKind === "component") {
        let tmp = {};
        getNestedComponentList(tmp, key, domId);
        pluginValues[key] = tmp[key];
        return;
    }
    if (type === "LIST") {
        let tmp = {};
        let kind = field.itemKind || "column";
        if (kind === "fact") {
            getFacts(tmp, key, domId);
        } else if (kind === "string") {
            getStringList(tmp, key, domId);
        } else if (kind === "sort") {
            getSortMethods(tmp, key, domId);
        } else if (kind === "filter") {
            getFilterValues(tmp, key, domId);
        } else if (kind === "groupKey") {
            getGroupKeyMappings(tmp, key, domId);
        } else if (kind === "jsonField") {
            getJsonFields(tmp, key, domId);
        } else if (kind === "csvField") {
            getCsvFields(tmp, key, domId);
        } else if (kind === "connector") {
            getNestedConnectorList(tmp, key, domId);
        } else if (kind === "bean") {
            getJsonObjectList(tmp, key, domId);
        } else {
            getColumns(tmp, key, domId);
        }
        pluginValues[key] = tmp[key];
        return;
    }
    if (type === "CHECKBOX") {
        let el = document.getElementById(domId);
        pluginValues[key] = el ? el.checked : false;
        return;
    }
    if (type === "COLOR") {
        let setEl = document.getElementById(domId + "_set");
        if (setEl && setEl.checked) {
            let colorEl = document.getElementById(domId);
            pluginValues[key] = colorEl ? hexToRgb(colorEl.value) : null;
            // mirror flags for border/background style when field names match
            if (key === "borderColor") {
                pluginValues["border"] = true;
            }
            if (key === "backGroundColor") {
                pluginValues["background"] = true;
            }
        } else {
            pluginValues[key] = null;
            if (key === "borderColor") {
                pluginValues["border"] = false;
            }
            if (key === "backGroundColor") {
                pluginValues["background"] = false;
            }
        }
        return;
    }
    if (type === "FONT") {
        let setEl = document.getElementById(domId + "_set");
        if (setEl && setEl.checked) {
            pluginValues[key] = {
                "fontName": document.getElementById(domId + "Name").value,
                "fontSize": toInteger(document.getElementById(domId + "Size").value),
                "bold": document.getElementById(domId + "Bold").checked,
                "italic": document.getElementById(domId + "Italic").checked
            };
        } else {
            pluginValues[key] = null;
        }
        return;
    }
    let el = document.getElementById(domId);
    if (!el) {
        return;
    }
    if (field.integerValue) {
        pluginValues[key] = toInteger(el.value);
    } else {
        pluginValues[key] = el.value;
    }
}

function readNestedLayout(prefix) {
    let layout = {};
    let sides = ["left", "right", "top", "bottom"];
    for (let s = 0; s < sides.length; s++) {
        let side = sides[s];
        let enabled = document.getElementById(prefix + "_" + side + "Enabled");
        if (enabled && enabled.checked) {
            layout[side] = {
                "componentName": document.getElementById(prefix + "_" + side + "ObjectName").value || null,
                "offset": parseInt(document.getElementById(prefix + "_" + side + "Offset").value) || 0,
                "percentage": parseInt(document.getElementById(prefix + "_" + side + "Percentage").value) || 0,
                "alignment": normalizeLayoutAlignment(
                    document.getElementById(prefix + "_" + side + "Alignment").value)
            };
        } else {
            layout[side] = null;
        }
    }
    return layout;
}

function setNestedComponentList(parentObj, fieldName, prefix) {
    let items = document.getElementById(prefix + "_items");
    if (items === null) {
        return;
    }
    items.innerHTML = "";
    let list = parentObj[fieldName];
    if (!list || !Array.isArray(list)) {
        return;
    }
    for (let i = 0; i < list.length; i++) {
        nestedComponentListAppend(prefix, list[i]);
    }
    // Names are known after load — rebuild relative-to lists from composite siblings only
    refreshNestedComponentListLayoutOptions(prefix);
}

function getNestedComponentList(parentObj, fieldName, prefix) {
    let items = document.getElementById(prefix + "_items");
    let result = [];
    if (items) {
        let shells = items.querySelectorAll(":scope > .nested-component-list-item");
        for (let i = 0; i < shells.length; i++) {
            let p = shells[i].getAttribute("data-prefix");
            result.push(readNestedComponentFromPanel(p));
        }
    }
    parentObj[fieldName] = result;
}

function nestedComponentListAdd(prefix) {
    nestedComponentListAppend(prefix, null);
}

function nestedComponentListAppend(prefix, nestedValue) {
    let items = document.getElementById(prefix + "_items");
    if (items === null) {
        return;
    }
    let childPrefix = prefix + "_c" + (nestedComponentSeq++);
    let wrap = document.createElement("div");
    wrap.className = "nested-component-list-item";
    wrap.setAttribute("data-prefix", childPrefix);
    wrap.style.border = "1px dashed #888";
    wrap.style.margin = "6px 0";
    wrap.style.padding = "6px";
    wrap.innerHTML = buildNestedComponentShellHtml(childPrefix, true);
    items.appendChild(wrap);
    wireNestedComponentShell(childPrefix);
    if (nestedValue) {
        loadNestedComponentIntoPanel(childPrefix, nestedValue);
    } else {
        // default to Label if available
        let typeSelect = document.getElementById(childPrefix + "_pluginId");
        if (typeSelect) {
            if (catalogById("HLabelComponent")) {
                typeSelect.value = "HLabelComponent";
            }
            rebuildNestedPluginFields(childPrefix, typeSelect.value, null);
        }
        // New child: refresh siblings so existing children can target it once named
        refreshNestedComponentListLayoutOptions(prefix);
    }
}

function nestedComponentListRemove(btn) {
    let item = btn.closest(".nested-component-list-item");
    if (!item) {
        return;
    }
    let parent = item.parentElement;
    item.remove();
    // Drop removed name from remaining relative-to dropdowns
    if (parent) {
        let remaining = parent.querySelectorAll(":scope > .nested-component-list-item");
        for (let i = 0; i < remaining.length; i++) {
            let p = remaining[i].getAttribute("data-prefix");
            if (p) {
                fillNestedLayoutRelativeOptions(p);
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Nested connector steps (ChainConnector.connectors, …)
// Driven by window.connectorCatalog from generated form schemas.
// Nested sourceConnectorName is hidden — chain wiring sets it at runtime.
// ---------------------------------------------------------------------------

let nestedConnectorSeq = 0;

/** Plugin ids that are poor choices as nested chain steps (or recurse too easily). */
const NESTED_CONNECTOR_EXCLUDE = {
    "ChainConnector": true
};

function connectorCatalogById(pluginId) {
    if (!window.connectorCatalog) {
        return null;
    }
    for (let i = 0; i < window.connectorCatalog.length; i++) {
        if (window.connectorCatalog[i].pluginId === pluginId) {
            return window.connectorCatalog[i];
        }
    }
    return null;
}

function connectorCatalogPluginIds() {
    if (!window.connectorCatalog) {
        return [];
    }
    let ids = [];
    for (let i = 0; i < window.connectorCatalog.length; i++) {
        let id = window.connectorCatalog[i].pluginId;
        if (id && !NESTED_CONNECTOR_EXCLUDE[id]) {
            ids.push(id);
        }
    }
    return ids;
}

function initNestedConnectorList(prefix) {
    let items = document.getElementById(prefix + "_items");
    if (items === null) {
        return;
    }
    items.innerHTML = "";
}

/**
 * Normalize hop/form step JSON to a flat plugin payload with pluginId.
 * Accepts either { pluginId, …fields } or { SelectionConnector: { … } }.
 */
function unwrapConnectorStep(obj) {
    if (!obj || typeof obj !== "object") {
        return {pluginId: "PassthroughConnector"};
    }
    if (obj.pluginId) {
        return obj;
    }
    let keys = Object.keys(obj);
    if (keys.length === 1 && obj[keys[0]] && typeof obj[keys[0]] === "object") {
        let inner = Object.assign({}, obj[keys[0]]);
        if (!inner.pluginId) {
            inner.pluginId = keys[0];
        }
        return inner;
    }
    return obj;
}

function setNestedConnectorList(parentObj, fieldName, prefix) {
    // Visual chain editor replaces the vertical nested list for ChainConnector
    if (typeof HopperChainEditor !== "undefined"
        && typeof connectorPluginId !== "undefined"
        && connectorPluginId === "ChainConnector"
        && HopperChainEditor.activate(parentObj, fieldName, prefix)) {
        return;
    }
    let items = document.getElementById(prefix + "_items");
    if (items === null) {
        return;
    }
    items.innerHTML = "";
    let list = parentObj[fieldName];
    if (!list || !Array.isArray(list)) {
        return;
    }
    for (let i = 0; i < list.length; i++) {
        nestedConnectorListAppend(prefix, unwrapConnectorStep(list[i]));
    }
}

function getNestedConnectorList(parentObj, fieldName, prefix) {
    if (typeof HopperChainEditor !== "undefined" && HopperChainEditor.isActive()) {
        parentObj[fieldName] = HopperChainEditor.collectSteps();
        return;
    }
    let items = document.getElementById(prefix + "_items");
    let result = [];
    if (items) {
        let shells = items.querySelectorAll(":scope > .nested-connector-list-item");
        for (let i = 0; i < shells.length; i++) {
            let p = shells[i].getAttribute("data-prefix");
            let step = readNestedConnectorFromPanel(p);
            if (step) {
                result.push(step);
            }
        }
    }
    parentObj[fieldName] = result;
}

function nestedConnectorListAdd(prefix) {
    nestedConnectorListAppend(prefix, null);
}

function nestedConnectorListAppend(prefix, stepValue) {
    let items = document.getElementById(prefix + "_items");
    if (items === null) {
        return;
    }
    let childPrefix = prefix + "_s" + (nestedConnectorSeq++);
    let wrap = document.createElement("div");
    wrap.className = "nested-connector-list-item";
    wrap.setAttribute("data-prefix", childPrefix);
    wrap.innerHTML = buildNestedConnectorShellHtml(childPrefix);
    items.appendChild(wrap);
    wireNestedConnectorShell(childPrefix);
    if (stepValue) {
        loadNestedConnectorIntoPanel(childPrefix, stepValue);
    } else {
        let typeSelect = document.getElementById(childPrefix + "_pluginId");
        if (typeSelect) {
            let preferred = ["SelectionConnector", "SimpleFilterConnector", "SortConnector",
                "DistinctConnector", "PassthroughConnector"];
            for (let p = 0; p < preferred.length; p++) {
                if (connectorCatalogById(preferred[p])) {
                    typeSelect.value = preferred[p];
                    break;
                }
            }
            if (!typeSelect.value && typeSelect.options.length) {
                typeSelect.selectedIndex = 0;
            }
            rebuildNestedConnectorPluginFields(childPrefix, typeSelect.value, null);
            updateNestedConnectorStepSummary(childPrefix);
        }
    }
}

function nestedConnectorListRemove(btn) {
    let item = btn.closest(".nested-connector-list-item");
    if (item) {
        item.remove();
    }
}

function nestedConnectorListMoveUp(btn) {
    let item = btn.closest(".nested-connector-list-item");
    if (!item || !item.parentNode) {
        return;
    }
    let prev = item.previousElementSibling;
    if (prev && prev.classList.contains("nested-connector-list-item")) {
        item.parentNode.insertBefore(item, prev);
    }
}

function nestedConnectorListMoveDown(btn) {
    let item = btn.closest(".nested-connector-list-item");
    if (!item || !item.parentNode) {
        return;
    }
    let next = item.nextElementSibling;
    if (next && next.classList.contains("nested-connector-list-item")) {
        item.parentNode.insertBefore(next, item);
    }
}

function buildNestedConnectorShellHtml(prefix) {
    let options = "";
    let ids = connectorCatalogPluginIds();
    for (let i = 0; i < ids.length; i++) {
        let info = connectorCatalogById(ids[i]);
        let label = info && info.name ? info.name : ids[i];
        options += '<option value="' + ids[i] + '">' + label + "</option>";
    }
    if (!options) {
        // Fallback when catalog missing
        ["SelectionConnector", "SortConnector", "SimpleFilterConnector", "DistinctConnector",
            "AggregateConnector", "PassthroughConnector", "SqlConnector", "SampleDataConnector",
            "CsvConnector", "HRestConnector", "HListConnector"].forEach(function (id) {
            options += '<option value="' + id + '">' + id + "</option>";
        });
    }
    let iconSrc = resolveUiIcon("connector.svg");
    return ""
        + '<div class="nested-connector-shell" data-prefix="' + prefix + '">'
        + '  <div class="nested-connector-step-header">'
        + '    <img class="nested-connector-step-icon" id="' + prefix + '_icon" src="' + iconSrc
        + '" data-ui-icon="connector.svg" width="18" height="18" alt="">'
        + '    <span class="nested-connector-step-summary" id="' + prefix + '_summary">Step</span>'
        + '    <label class="nested-connector-type-label">Type </label>'
        + '    <select id="' + prefix + '_pluginId" class="nested-connector-type-select">' + options + "</select>"
        + '    <span class="nested-connector-step-actions">'
        + '      <button type="button" class="list-row-btn" title="Move up" '
        + 'onclick="nestedConnectorListMoveUp(this)">'
        + uiIconImgTag("arrow-up.svg", "Up", 14) + "</button>"
        + '      <button type="button" class="list-row-btn" title="Move down" '
        + 'onclick="nestedConnectorListMoveDown(this)">'
        + uiIconImgTag("arrow-down.svg", "Down", 14) + "</button>"
        + '      <button type="button" class="list-row-btn" title="Remove step" '
        + 'onclick="nestedConnectorListRemove(this)">'
        + uiIconImgTag("delete.svg", "Remove", 14) + "</button>"
        + '      <button type="button" class="nested-connector-toggle" id="' + prefix
        + '_toggle" title="Expand or collapse settings">Settings</button>'
        + "    </span>"
        + "  </div>"
        + '  <div id="' + prefix + '_pluginFields" class="nested-connector-plugin-fields" style="display:none;"></div>'
        + "</div>";
}

function wireNestedConnectorShell(prefix) {
    let typeSelect = document.getElementById(prefix + "_pluginId");
    if (typeSelect) {
        typeSelect.onchange = function () {
            rebuildNestedConnectorPluginFields(prefix, typeSelect.value, null);
            updateNestedConnectorStepSummary(prefix);
        };
    }
    let toggle = document.getElementById(prefix + "_toggle");
    let fields = document.getElementById(prefix + "_pluginFields");
    if (toggle && fields) {
        toggle.onclick = function () {
            if (fields.style.display === "none" || !fields.style.display) {
                fields.style.display = "block";
                toggle.textContent = "Hide";
            } else {
                fields.style.display = "none";
                toggle.textContent = "Settings";
            }
        };
    }
}

function updateNestedConnectorStepSummary(prefix) {
    let typeSelect = document.getElementById(prefix + "_pluginId");
    let summary = document.getElementById(prefix + "_summary");
    let icon = document.getElementById(prefix + "_icon");
    if (!typeSelect) {
        return;
    }
    let pluginId = typeSelect.value;
    let info = connectorCatalogById(pluginId);
    let label = info && info.name ? info.name : pluginId;
    if (summary) {
        summary.textContent = label;
        summary.title = info && info.description
            ? (label + " - " + info.description)
            : label;
    }
    if (icon && typeof connectorPluginIconUrl === "function") {
        icon.src = connectorPluginIconUrl(pluginId);
        icon.alt = pluginId || "connector";
        if (info && info.description) {
            icon.title = label + "\n" + info.description;
        } else {
            icon.title = label;
        }
    }
}

/**
 * @param {string} prefix DOM prefix for nested fields
 * @param {string} pluginId connector plugin id
 * @param {object} values flat plugin field values
 * @param {Array.<string>|null} [columnNamesOverride] when set (chain builder), use these column
 *        names for filter/fact/column lists instead of describing sourceConnectorName
 */
function rebuildNestedConnectorPluginFields(prefix, pluginId, values, columnNamesOverride) {
    let container = document.getElementById(prefix + "_pluginFields");
    if (container === null) {
        return;
    }
    container.innerHTML = "";
    let info = connectorCatalogById(pluginId);
    if (info === null || !info.sections) {
        container.innerHTML = "<em class=\"editor-hint\">No form schema for "
            + escapeHtmlText(pluginId || "?")
            + ". Source wiring is automatic for chain steps.</em>";
        return;
    }
    let pluginValues = values || {};
    let anyField = false;
    for (let s = 0; s < info.sections.length; s++) {
        let section = info.sections[s];
        let fields = section.fields || [];
        // Filter out sourceConnectorName — chain sets this at runtime
        let visible = [];
        for (let f = 0; f < fields.length; f++) {
            if (fields[f].fieldName === "sourceConnectorName" || fields[f].id === "sourceConnectorName") {
                continue;
            }
            // Nested chain lists are stripped from catalog at depth; still skip
            if (fields[f].type === "LIST" && fields[f].itemKind === "connector") {
                continue;
            }
            visible.push(fields[f]);
        }
        if (!visible.length) {
            continue;
        }
        anyField = true;
        let title = section.title || section.id || "Options";
        let open = section.openByDefault ? "block" : "block";
        let secId = prefix + "_sec_" + (section.id || s);
        container.insertAdjacentHTML("beforeend",
            '<button type="button" class="collapsible nested-sec-toggle">' + escapeHtmlText(title) + "</button>"
            + '<div class="content" id="' + secId + '" style="display: ' + open + ';"></div>');
        let secDiv = document.getElementById(secId);
        for (let f = 0; f < visible.length; f++) {
            appendNestedFieldControl(secDiv, prefix, visible[f], pluginValues, columnNamesOverride);
        }
        let btn = secDiv.previousElementSibling;
        if (btn) {
            btn.onclick = function () {
                let c = this.nextElementSibling;
                c.style.display = c.style.display === "block" ? "none" : "block";
            };
        }
    }
    if (!anyField) {
        container.innerHTML = "<em class=\"editor-hint\">This step has no extra settings "
            + "(uses the chain source automatically).</em>";
    }
}

function loadNestedConnectorIntoPanel(prefix, step) {
    step = unwrapConnectorStep(step);
    let pluginId = step.pluginId;
    let typeSelect = document.getElementById(prefix + "_pluginId");
    if (typeSelect && pluginId) {
        // Ensure option exists
        let found = false;
        for (let i = 0; i < typeSelect.options.length; i++) {
            if (typeSelect.options[i].value === pluginId) {
                found = true;
                break;
            }
        }
        if (!found) {
            let opt = document.createElement("option");
            opt.value = pluginId;
            opt.textContent = pluginId;
            typeSelect.appendChild(opt);
        }
        typeSelect.value = pluginId;
        rebuildNestedConnectorPluginFields(prefix, pluginId, step);
    }
    updateNestedConnectorStepSummary(prefix);
}

function readNestedConnectorFromPanel(prefix) {
    let typeSelect = document.getElementById(prefix + "_pluginId");
    if (!typeSelect) {
        return null;
    }
    let pluginId = typeSelect.value;
    if (!pluginId) {
        return null;
    }
    let info = connectorCatalogById(pluginId);
    let pluginValues = {};
    if (info && info.sections) {
        for (let s = 0; s < info.sections.length; s++) {
            let fields = info.sections[s].fields || [];
            for (let f = 0; f < fields.length; f++) {
                let field = fields[f];
                if (field.fieldName === "sourceConnectorName" || field.id === "sourceConnectorName") {
                    continue;
                }
                if (field.type === "LIST" && field.itemKind === "connector") {
                    continue;
                }
                readNestedFieldValue(prefix, field, pluginValues);
            }
        }
    }
    // Runtime wiring: leave source unset so chain context assigns it
    pluginValues["pluginId"] = pluginId;
    pluginValues["sourceConnectorName"] = null;

    // Hop JsonMetadataParser requires @HopMetadataObject list items as:
    //   { "SelectionConnector": { "pluginId": "SelectionConnector", ...fields } }
    // Flat { pluginId, ... } makes createObject("pluginId") → null → NPE on save/preview.
    let wrapped = {};
    wrapped[pluginId] = pluginValues;
    return wrapped;
}

// ---------------------------------------------------------------------------
// Sort methods, filter values, and JSON object lists (connectors)
// ---------------------------------------------------------------------------

const SORT_METHOD_TYPES = [
    "NATIVE_VALUE",
    "STRING_ALPHA",
    "STRING_ALPHA_CASE_INSENSITIVE",
    "STRING_NUMERIC",
    "STRING_CUSTOM"
];

function setSortMethods(json, fieldId, tableId) {
    let values = json[fieldId];
    if (!values) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "sort");
    }
    for (let i = 0; i < values.length; i++) {
        createSortMethodRow(table, values[i], i);
    }
}

function createSortMethodRow(table, method, i) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    let type = method && method.type ? method.type : "NATIVE_VALUE";
    let ascending = method && method.ascending !== false;
    row.insertCell(0).innerHTML = createSelection(
        "sortType-" + i, type, SORT_METHOD_TYPES, { defaultEmptyToFirst: true });
    row.insertCell(1).innerHTML = createCheckBox("sortAsc-" + i, ascending);
    appendListReorderCells(row, table, 2);
}

function getSortMethods(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (!table) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        let row = table.rows[i];
        values.push({
            "type": cellControlValue(row.cells[0]),
            "ascending": !!cellControlValue(row.cells[1]),
            "customOrder": []
        });
    }
    json[fieldId] = values;
}

function setFilterValues(json, fieldId, tableId, connectorColumnNames) {
    let values = json[fieldId];
    if (!values) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "filter");
    }
    let colNames = connectorColumnNames;
    if (!colNames || !colNames.length) {
        // Prefer live source connector select when present (connector editors)
        colNames = (typeof listFieldConnectorColumnNames === "function")
            ? listFieldConnectorColumnNames(table)
            : [];
    }
    for (let i = 0; i < values.length; i++) {
        createFilterValueRow(table, values[i], i, colNames);
    }
}

function createFilterValueRow(table, filter, i, connectorColumnNames) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    let fieldName = filter && filter.fieldName ? filter.fieldName : "";
    let filterValue = filter && filter.filterValue ? filter.filterValue : "";
    let colNames = connectorColumnNames;
    if (!colNames) {
        colNames = (typeof listFieldConnectorColumnNames === "function")
            ? listFieldConnectorColumnNames(table)
            : [];
    }
    // Column select from source connector (preserve stored name if offline)
    row.insertCell(0).innerHTML = createSelection(
        "filterField-" + i, fieldName, colNames || [], { preserveMissing: true });
    row.insertCell(1).innerHTML = createText("filterValue-" + i, filterValue);
    appendListReorderCells(row, table, 2);
}

/** Hop value type names for REST JsonField mapping (matches HRestConnector.JsonField). */
const JSON_FIELD_TYPES = [
    "String",
    "Integer",
    "Number",
    "BigNumber",
    "Boolean",
    "Date",
    "Timestamp",
    "Binary",
    "Internet Address"
];

function setJsonFields(json, fieldId, tableId) {
    let values = json[fieldId];
    if (!values) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "jsonField");
    }
    for (let i = 0; i < values.length; i++) {
        createJsonFieldRow(table, values[i], i);
    }
}

function createJsonFieldRow(table, field, i) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    let f = field || {};
    let type = f.type || "String";
    row.insertCell(0).innerHTML = createText("jsonTag-" + i, f.tag || "");
    row.insertCell(1).innerHTML = createText("jsonName-" + i, f.name || "");
    row.insertCell(2).innerHTML = createSelection(
        "jsonType-" + i, type, JSON_FIELD_TYPES, { defaultEmptyToFirst: true, preserveMissing: true });
    // Cell widths from CSS; inputs fill the cell (no dead space beside a fixed-em control)
    row.insertCell(3).innerHTML = createText("jsonFormat-" + i, f.formatMask || "");
    row.insertCell(4).innerHTML = createText("jsonLength-" + i, f.length || "");
    row.insertCell(5).innerHTML = createText("jsonPrecision-" + i, f.precision || "");
    appendListReorderCells(row, table, 6);
}

function getJsonFields(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (!table) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        let row = table.rows[i];
        values.push({
            "tag": cellControlValue(row.cells[0]),
            "name": cellControlValue(row.cells[1]),
            "type": cellControlValue(row.cells[2]),
            "formatMask": cellControlValue(row.cells[3]),
            "length": cellControlValue(row.cells[4]),
            "precision": cellControlValue(row.cells[5]),
            "decimal": "",
            "grouping": ""
        });
    }
    json[fieldId] = values;
}

function setCsvFields(json, fieldId, tableId) {
    let values = json[fieldId];
    if (!values) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "csvField");
    }
    for (let i = 0; i < values.length; i++) {
        createCsvFieldRow(table, values[i], i);
    }
}

function createCsvFieldRow(table, field, i) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    let f = field || {};
    let type = f.type || "String";
    // Column widths come from CSS (.list-field-table[data-list-kind=csvField]); inputs fill cells.
    row.insertCell(0).innerHTML = createText("csvName-" + i, f.name || "");
    row.insertCell(1).innerHTML = createSelection(
        "csvType-" + i, type, JSON_FIELD_TYPES, { defaultEmptyToFirst: true, preserveMissing: true });
    row.insertCell(2).innerHTML = createText("csvFormat-" + i, f.formatMask || "");
    row.insertCell(3).innerHTML = createText("csvLength-" + i, f.length || "");
    row.insertCell(4).innerHTML = createText("csvPrecision-" + i, f.precision || "");
    appendListReorderCells(row, table, 5);
}

function getCsvFields(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (!table) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        let row = table.rows[i];
        values.push({
            "name": cellControlValue(row.cells[0]),
            "type": cellControlValue(row.cells[1]),
            "formatMask": cellControlValue(row.cells[2]),
            "length": cellControlValue(row.cells[3]),
            "precision": cellControlValue(row.cells[4])
        });
    }
    json[fieldId] = values;
}

function getFilterValues(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (!table) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        let row = table.rows[i];
        values.push({
            "fieldName": cellControlValue(row.cells[0]),
            "filterValue": cellControlValue(row.cells[1])
        });
    }
    json[fieldId] = values;
}

/** Group component: group column → nested connector column key mappings. */
function setGroupKeyMappings(json, fieldId, tableId) {
    let values = json[fieldId];
    if (!values) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "groupKey");
    }
    for (let i = 0; i < values.length; i++) {
        createGroupKeyMappingRow(table, values[i], i);
    }
}

function createGroupKeyMappingRow(table, mapping, i) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    let m = mapping || {};
    row.insertCell(0).innerHTML = createText("groupKeyGroup-" + i, m.groupColumn || "");
    row.insertCell(1).innerHTML = createText("groupKeyConn-" + i, m.connectorColumn || "");
    appendListReorderCells(row, table, 2);
}

function getGroupKeyMappings(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (!table) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        let row = table.rows[i];
        values.push({
            "groupColumn": cellControlValue(row.cells[0]),
            "connectorColumn": cellControlValue(row.cells[1])
        });
    }
    json[fieldId] = values;
}

function setJsonObjectList(json, fieldId, tableId) {
    let values = json[fieldId];
    if (!values) {
        return;
    }
    let table = document.getElementById(tableId);
    if (!table) {
        return;
    }
    if (table.getAttribute("data-list-kind") === null) {
        table.setAttribute("data-list-kind", "connector");
    }
    for (let i = 0; i < values.length; i++) {
        createJsonObjectRow(table, values[i], i);
    }
}

function createJsonObjectRow(table, obj, i) {
    let row = table.insertRow(i + 1);
    row.id = createTableRowId(table.id, rowIdNumber++);
    let text = "";
    try {
        text = obj === null || obj === undefined ? "" : JSON.stringify(obj);
    } catch (e) {
        text = String(obj);
    }
    row.insertCell(0).innerHTML = '<textarea id="jsonObj-' + i + '" rows="3" style="width:95%">'
        + text.replace(/</g, "&lt;") + "</textarea>";
    appendListReorderCells(row, table, 1);
}

function getJsonObjectList(json, fieldId, tableId) {
    let values = [];
    let table = document.getElementById(tableId);
    if (!table) {
        json[fieldId] = values;
        return;
    }
    for (let i = 1; i < table.rows.length; i++) {
        let row = table.rows[i];
        let raw = cellControlValue(row.cells[0]);
        try {
            values.push(raw ? JSON.parse(raw) : {});
        } catch (e) {
            alert("Invalid JSON in list row " + i + ": " + e);
            values.push({});
        }
    }
    json[fieldId] = values;
}
