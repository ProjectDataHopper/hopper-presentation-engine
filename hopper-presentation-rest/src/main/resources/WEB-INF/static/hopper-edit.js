/**
 * Edit-mode (WYSIWYG) helpers for hopper-presentation.
 * Loaded after hopper-presentation.js when hopperMode === 'edit'.
 *
 * PR2: geometries, list (name + type), hover + selection
 * PR3: delete, name-based properties, soft re-render after mutations
 * PR4: palette drag/drop create + toolbar Add
 * PR5: offset-only drag of existing components (outline + nudge API)
 * PR6: edge-hover resize (cursors + outline + resize API)
 * Hold Shift while editing to suppress resize edges (move small components).
 */
(function () {
    if (typeof hopperMode === "undefined" || hopperMode !== "edit") {
        return;
    }

    /** @type {Array<{componentName:string, pluginId:string, geometry:{x,y,width,height}, pageRole?:string, logicalPageNumber?:number}>} */
    let componentGeometries = [];
    /** @type {Array<{name:string, pluginId:string, pluginName:string, pageRole?:string}>} */
    let pageComponents = [];
    let componentPluginCatalog = [];

    let selectedComponentName = null;
    /** pageRole for the current selection (page|header|footer) — used for Apply routing */
    let selectedPageRole = "page";
    /**
     * In-memory component clipboard for cut/copy/paste (session only).
     * @type {null|{componentJson:object, pageRole:string, sourcePresentation:string}}
     */
    let componentClipboard = null;
    /** Default paste offset so the clone is not stacked exactly on the original. */
    const PASTE_OFFSET_PX = 20;
    /**
     * Last known page-space pointer (presentation units). Updated while the cursor is over the
     * canvas; used so Ctrl+V pastes at the mouse location.
     * @type {null|{x:number,y:number,ts:number}}
     */
    let lastPagePointer = null;
    /** Max age (ms) for using lastPagePointer as paste target. */
    const PASTE_POINTER_MAX_AGE_MS = 30000;
    let hoverComponentName = null;
    let lastHoverName = null;
    /** @type {null|{n:boolean,s:boolean,e:boolean,w:boolean}} last edge under pointer (for cursor) */
    let lastHoverEdges = null;
    let redrawScheduled = false;
    let pendingSelectName = null;

    /**
     * Active pointer interaction: move (nudge) or resize.
     * @type {null|{
     *   mode:"move"|"resize",
     *   drawnName:string, metadataName:string, pageRole:string,
     *   startPageX:number, startPageY:number,
     *   originGeo:{x,y,width,height},
     *   dx:number, dy:number,
     *   edges?:{n:boolean,s:boolean,e:boolean,w:boolean},
     *   liveGeo?:{x,y,width,height},
     *   dragging:boolean, openPropsOnUp:boolean,
     *   requestData?:object
     * }}
     */
    let dragState = null;
    const DRAG_THRESHOLD_PX = 4;
    /** Screen-pixel hit zone for component edges (converted to page space via scale). */
    const EDGE_HIT_SCREEN_PX = 8;
    /** Minimum component size after a resize (page pixels). */
    const MIN_RESIZE_PX = 10;
    /**
     * When true (Shift held), edge resize hits and resize cursors are disabled so the
     * whole component can be moved — needed for small labels where edges fill the box.
     */
    let shiftSuppressesResize = false;

    function initEditShell() {
        // Restore selection after cross-page drag navigation
        try {
            let pending = sessionStorage.getItem("hopperPendingSelect");
            if (pending) {
                pendingSelectName = pending;
                sessionStorage.removeItem("hopperPendingSelect");
            }
        } catch (e) { /* ignore */ }
        loadComponentPalette();
        refreshEditorState();
        // Component list + HF controls live in the page properties panel (not left rail)
        wireListToolbar();
        wireCanvasDrop();
        console.log("Hopper edit mode ready for presentation:", presentationName);
    }

    function refreshEditorState(selectName) {
        if (selectName !== undefined && selectName !== null) {
            pendingSelectName = selectName;
        }
        loadPageComponentList();
        loadComponentGeometries();
        loadHeaderFooterState();
    }

    // ── Header / Footer ──────────────────────────────────────────────────

    let headerFooterState = {
        header: {enabled: false, height: 50},
        footer: {enabled: false, height: 25},
        regions: null
    };
    /**
     * Region under pointer while dragging.
     * @type {null|"header"|"content"|"footer"|"prev-page"|"next-page"}
     */
    let activeDropRegion = null;
    /** Palette HTML5 drag currently over the canvas */
    let paletteDragActive = false;
    /**
     * Page-edge band thickness (page pixels) for "move to prev/next page" while dragging a
     * component. Also treats fully outside the page above/below as that band.
     */
    const PAGE_TRANSFER_ZONE_PX = 36;

    function loadHeaderFooterState() {
        if (typeof presentationName === "undefined" || !presentationName) {
            return;
        }
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/header-footer/",
            type: "GET",
            dataType: "json",
            success: function (state) {
                headerFooterState = state || headerFooterState;
                scheduleRedraw();
            },
            error: function (xhr) {
                console.warn("Failed to load header/footer state:", xhr.responseText || xhr.status);
            }
        });
    }

    function getPageRegions() {
        return (headerFooterState && headerFooterState.regions) || null;
    }

    function getActiveDropRegion() {
        return activeDropRegion;
    }

    /**
     * Which band (header / content / footer / prev-page / next-page) contains page-space
     * point (px, py). Footer and header win over content when overlapping edges.
     *
     * @param {number} px
     * @param {number} py
     * @param {{allowPageTransfer?:boolean}} [opts] when allowPageTransfer, near/outside top
     *   and bottom of the page report prev-page / next-page (component move only).
     */
    function hitTestPageRegion(px, py, opts) {
        let regions = getPageRegions();
        let allowTransfer = !!(opts && opts.allowPageTransfer);
        let page = regions && regions.page ? regions.page : null;
        // Without server regions, use full logical page box when available
        if (!page && typeof pageLogicalSize === "function") {
            let sz = pageLogicalSize();
            if (sz && sz.width > 0 && sz.height > 0) {
                page = {x: 0, y: 0, width: sz.width, height: sz.height};
            }
        }
        if (allowTransfer && page) {
            let topEdge = page.y + PAGE_TRANSFER_ZONE_PX;
            let bottomEdge = page.y + page.height - PAGE_TRANSFER_ZONE_PX;
            if (py < topEdge) {
                return "prev-page";
            }
            if (py > bottomEdge) {
                return "next-page";
            }
        }
        if (!regions) {
            return allowTransfer ? "content" : "content";
        }
        function contains(r) {
            return r && px >= r.x && py >= r.y
                && px <= r.x + r.width && py <= r.y + r.height;
        }
        if (contains(regions.header)) {
            return "header";
        }
        if (contains(regions.footer)) {
            return "footer";
        }
        if (contains(regions.content)) {
            return "content";
        }
        if (contains(regions.page)) {
            return "content";
        }
        // Fully outside page vertically still counts as transfer when dragging
        if (allowTransfer && page) {
            if (py < page.y) {
                return "prev-page";
            }
            if (py > page.y + page.height) {
                return "next-page";
            }
        }
        return null;
    }

    function setActiveDropRegion(region) {
        if (activeDropRegion === region) {
            return;
        }
        activeDropRegion = region;
        scheduleRedraw();
    }

    function clearActiveDropRegion() {
        if (activeDropRegion === null && !paletteDragActive) {
            return;
        }
        activeDropRegion = null;
        paletteDragActive = false;
        scheduleRedraw();
    }

    // ── Palette ──────────────────────────────────────────────────────────

    /**
     * URL for a component type icon from {@code @HComponentPlugin(image=...)} /
     * {@code GET plugins/components/{id}/image}.
     */
    function componentPluginIconUrl(pluginId) {
        if (!pluginId) {
            return API_BASE + "plugins/components/default/image";
        }
        return API_BASE + "plugins/components/" + encodeURIComponent(pluginId) + "/image";
    }

    /** Tooltip: display name, plugin id, description (multi-line title). */
    function componentPluginTooltip(p, extraLine) {
        if (!p) {
            return extraLine || "";
        }
        let name = p.name || p.id || "Component";
        let id = p.id || "";
        let desc = (p.description || "").trim();
        let lines = [];
        if (id && name !== id) {
            lines.push(name + " (" + id + ")");
        } else {
            lines.push(name);
        }
        if (desc) {
            lines.push(desc);
        }
        if (extraLine) {
            lines.push(extraLine);
        }
        return lines.join("\n");
    }

    function findComponentPluginInCatalog(pluginId) {
        if (!pluginId || !componentPluginCatalog) {
            return null;
        }
        for (let i = 0; i < componentPluginCatalog.length; i++) {
            if (componentPluginCatalog[i].id === pluginId) {
                return componentPluginCatalog[i];
            }
        }
        return null;
    }

    function loadComponentPalette() {
        let root = document.getElementById("componentPalette");
        if (!root) {
            return;
        }
        root.innerHTML = "<p class=\"editor-hint\">Loading types…</p>";
        $.ajax({
            url: API_BASE + "plugins/components",
            type: "GET",
            dataType: "json",
            success: function (list) {
                componentPluginCatalog = list || [];
                root.innerHTML = "";
                if (componentPluginCatalog.length === 0) {
                    root.innerHTML = "<p class=\"editor-hint\">No component plugins found.</p>";
                    return;
                }
                for (let i = 0; i < componentPluginCatalog.length; i++) {
                    let p = componentPluginCatalog[i];
                    let btn = document.createElement("button");
                    btn.type = "button";
                    btn.className = "palette-item";
                    btn.draggable = true;
                    btn.setAttribute("data-plugin-id", p.id);
                    btn.title = componentPluginTooltip(p, "— drag onto the page");
                    let icon = document.createElement("img");
                    icon.className = "palette-item-icon";
                    icon.src = componentPluginIconUrl(p.id);
                    icon.alt = "";
                    icon.width = 20;
                    icon.height = 20;
                    icon.draggable = false;
                    let label = document.createElement("span");
                    label.className = "palette-item-label";
                    label.textContent = p.name || p.id;
                    btn.appendChild(icon);
                    btn.appendChild(label);
                    btn.addEventListener("dragstart", function (e) {
                        // Custom type + text/plain (browsers often only expose plain in drop)
                        e.dataTransfer.setData("text/hopper-component-plugin", p.id);
                        e.dataTransfer.setData("text/plain", p.id);
                        e.dataTransfer.effectAllowed = "copy";
                        btn.classList.add("palette-item-dragging");
                    });
                    btn.addEventListener("dragend", function () {
                        btn.classList.remove("palette-item-dragging");
                        let canvasEl = document.getElementById("svgCanvas");
                        if (canvasEl) {
                            canvasEl.classList.remove("canvas-drop-target");
                        }
                    });
                    root.appendChild(btn);
                }
            },
            error: function (xhr) {
                root.innerHTML = "<p class=\"editor-hint\">Failed to load plugins: "
                    + (xhr.responseText || xhr.status) + "</p>";
            }
        });
    }

    // ── Page component list ──────────────────────────────────────────────

    /**
     * Fill #pageComponentList when present (page properties panel). Safe no-op if the list
     * host is not in the DOM (palette-only left rail).
     * Rows: type icon, name/type, per-line up / down / delete; single-click selects, double-click edits.
     * Also refreshes the left-rail "Layout problems" list (components with no usable layout).
     * @param {Array=} preloaded optional component rows to render without re-fetching
     */
    function loadPageComponentList(preloaded) {
        let listEl = document.getElementById("pageComponentList");
        let emptyEl = document.getElementById("pageComponentListEmpty");
        function paint(list) {
            pageComponents = list || [];
            paintLayoutProblemList(pageComponents);
            if (!listEl) {
                updateListToolbarState();
                return;
            }
            listEl.innerHTML = "";
            if (emptyEl) {
                emptyEl.style.display = pageComponents.length ? "none" : "block";
                emptyEl.textContent = "No components yet.";
            }
            function uiIcon(name) {
                if (typeof resolveUiIcon === "function") {
                    return resolveUiIcon(name);
                }
                if (typeof uiIconUrl === "function") {
                    return uiIconUrl(name);
                }
                return API_BASE + "static/images/" + name;
            }
            for (let i = 0; i < pageComponents.length; i++) {
                let item = pageComponents[i];
                let name = item.name;
                let typeLabel = item.pluginName || item.pluginId || "component";
                let pluginInfo = findComponentPluginInCatalog(item.pluginId);
                let hasProblem = !!item.layoutProblem;
                let li = document.createElement("li");
                li.className = "page-component-item"
                    + (hasProblem ? " page-component-item-problem" : "");
                if (name === selectedComponentName || name === pendingSelectName) {
                    li.classList.add("selected");
                }
                li.setAttribute("data-component-name", name);
                li.setAttribute("data-component-index", String(i));
                let tipBase = componentPluginTooltip(
                    pluginInfo || {
                        id: item.pluginId,
                        name: typeLabel,
                        description: ""
                    },
                    "Component: " + name
                );
                if (hasProblem && item.layoutError) {
                    tipBase = (item.layoutError + "\n\n") + tipBase;
                }
                li.title = tipBase;
                li.innerHTML = ""
                    + "<button type=\"button\" class=\"page-component-main\" title=\"Edit component properties\">"
                    + (hasProblem
                        ? "<span class=\"layout-problem-marker\" title=\"Layout problem\" aria-label=\"Layout problem\">!</span>"
                        : "")
                    + "<img class=\"comp-type-icon\" width=\"18\" height=\"18\" alt=\"\">"
                    + "<span class=\"comp-text\">"
                    + "<span class=\"comp-name\"></span>"
                    + "<span class=\"comp-type\"></span>"
                    + "</span>"
                    + "</button>"
                    + "<span class=\"page-component-actions\">"
                    + "<button type=\"button\" class=\"list-row-btn page-comp-action\" data-action=\"up\""
                    + " title=\"Move up\"" + (i === 0 ? " disabled" : "") + ">"
                    + "<img src=\"" + uiIcon("arrow-up.svg") + "\" data-ui-icon=\"arrow-up.svg\" alt=\"Up\" width=\"14\" height=\"14\">"
                    + "</button>"
                    + "<button type=\"button\" class=\"list-row-btn page-comp-action\" data-action=\"down\""
                    + " title=\"Move down\""
                    + (i === pageComponents.length - 1 ? " disabled" : "") + ">"
                    + "<img src=\"" + uiIcon("arrow-down.svg") + "\" data-ui-icon=\"arrow-down.svg\" alt=\"Down\" width=\"14\" height=\"14\">"
                    + "</button>"
                    + "<button type=\"button\" class=\"list-row-btn page-comp-action\" data-action=\"delete\""
                    + " title=\"Delete component\">"
                    + "<img src=\"" + uiIcon("delete.svg") + "\" data-ui-icon=\"delete.svg\" alt=\"Delete\" width=\"14\" height=\"14\">"
                    + "</button>"
                    + "</span>";
                let iconEl = li.querySelector(".comp-type-icon");
                iconEl.src = componentPluginIconUrl(item.pluginId);
                iconEl.alt = typeLabel;
                li.querySelector(".comp-name").textContent = name;
                li.querySelector(".comp-type").textContent = typeLabel;
                if (item.pageRole && item.pageRole !== "page") {
                    li.querySelector(".comp-type").textContent =
                        typeLabel + " · " + item.pageRole;
                }
                listEl.appendChild(li);
            }
            if (!listEl._hopperCompListWired) {
                listEl._hopperCompListWired = true;
                listEl.addEventListener("click", onPageComponentListClick);
                listEl.addEventListener("dblclick", onPageComponentListDblClick);
            }
            if (pendingSelectName) {
                let stillThere = pageComponents.some(function (c) {
                    return c.name === pendingSelectName;
                });
                if (stillThere) {
                    selectComponent(pendingSelectName, false);
                } else {
                    selectedComponentName = null;
                }
                pendingSelectName = null;
            }
            updateListToolbarState();
        }
        if (preloaded !== undefined) {
            paint(preloaded);
            return;
        }
        if (typeof renderId === "undefined" || !renderId) {
            paint([]);
            return;
        }
        $.ajax({
            url: API_BASE + "edit/presentation/by-render/" + encodeURIComponent(renderId)
                + "/pages/" + encodeURIComponent(renderPageNumber0) + "/components/",
            type: "GET",
            dataType: "json",
            success: function (list) {
                paint(list || []);
            },
            error: function (xhr) {
                if (emptyEl) {
                    emptyEl.textContent = "Could not load component list: "
                        + (xhr.responseText || xhr.status);
                    emptyEl.style.display = "block";
                }
                paintLayoutProblemList([]);
            }
        });
    }

    /**
     * Left-rail list of components that failed layout / have no drawable geometry.
     * @param {Array} list page component rows (may include layoutProblem flags)
     */
    function paintLayoutProblemList(list) {
        let section = document.getElementById("editorLayoutProblems");
        let listEl = document.getElementById("layoutProblemList");
        if (!section || !listEl) {
            return;
        }
        let problems = [];
        let rows = list || [];
        for (let i = 0; i < rows.length; i++) {
            if (rows[i] && rows[i].layoutProblem) {
                problems.push(rows[i]);
            }
        }
        listEl.innerHTML = "";
        if (problems.length === 0) {
            section.hidden = true;
            return;
        }
        section.hidden = false;
        for (let i = 0; i < problems.length; i++) {
            let item = problems[i];
            let name = item.name || "?";
            let typeLabel = item.pluginName || item.pluginId || "component";
            let err = item.layoutError || "Layout problem";
            let li = document.createElement("li");
            li.className = "layout-problem-item";
            li.setAttribute("data-component-name", name);
            li.title = err;
            let btn = document.createElement("button");
            btn.type = "button";
            btn.className = "layout-problem-main";
            btn.title = "Edit properties — " + err;
            let marker = document.createElement("span");
            marker.className = "layout-problem-marker";
            marker.setAttribute("aria-hidden", "true");
            marker.textContent = "!";
            let icon = document.createElement("img");
            icon.className = "comp-type-icon";
            icon.width = 18;
            icon.height = 18;
            icon.alt = typeLabel;
            icon.src = componentPluginIconUrl(item.pluginId);
            let text = document.createElement("span");
            text.className = "comp-text";
            let nameEl = document.createElement("span");
            nameEl.className = "comp-name";
            nameEl.textContent = name;
            let typeEl = document.createElement("span");
            typeEl.className = "comp-type";
            typeEl.textContent = typeLabel;
            if (item.pageRole && item.pageRole !== "page") {
                typeEl.textContent = typeLabel + " · " + item.pageRole;
            }
            text.appendChild(nameEl);
            text.appendChild(typeEl);
            btn.appendChild(marker);
            btn.appendChild(icon);
            btn.appendChild(text);
            btn.addEventListener("click", function (e) {
                e.preventDefault();
                openComponentEditorFromPageList(name);
            });
            li.appendChild(btn);
            listEl.appendChild(li);
        }
    }

    /**
     * Open component property form from the page list. If page properties have unsaved
     * changes, ask to save first (see {@code confirmSavePagePropertiesIfDirty}).
     */
    function openComponentEditorFromPageList(name) {
        if (!name) {
            return;
        }
        function go() {
            selectComponent(name, true);
            openPropertiesForComponent(name);
        }
        if (typeof confirmSavePagePropertiesIfDirty === "function") {
            confirmSavePagePropertiesIfDirty(go);
        } else {
            go();
        }
    }

    /**
     * Click handler for page component list: main area = open editor; up/down/delete actions.
     */
    function onPageComponentListClick(ev) {
        let actionBtn = ev.target.closest(".page-comp-action");
        if (actionBtn && !actionBtn.disabled) {
            ev.preventDefault();
            ev.stopPropagation();
            let row = actionBtn.closest(".page-component-item");
            if (!row) {
                return;
            }
            let name = row.getAttribute("data-component-name");
            let action = actionBtn.getAttribute("data-action");
            if (action === "delete") {
                selectComponent(name, false);
                deleteSelectedComponent();
            } else if (action === "up") {
                moveComponentOnPage(name, "up");
            } else if (action === "down") {
                moveComponentOnPage(name, "down");
            }
            return;
        }
        let main = ev.target.closest(".page-component-main");
        if (main) {
            ev.preventDefault();
            let row = main.closest(".page-component-item");
            if (!row) {
                return;
            }
            let name = row.getAttribute("data-component-name");
            openComponentEditorFromPageList(name);
        }
    }

    /**
     * Double-click also opens the property form (same as single click).
     */
    function onPageComponentListDblClick(ev) {
        let actionBtn = ev.target.closest(".page-comp-action");
        if (actionBtn) {
            return;
        }
        let main = ev.target.closest(".page-component-main");
        if (!main) {
            return;
        }
        ev.preventDefault();
        let row = main.closest(".page-component-item");
        if (!row) {
            return;
        }
        let name = row.getAttribute("data-component-name");
        openComponentEditorFromPageList(name);
    }

    /**
     * Logical page index for reorder API: page properties panel index when open, else current render page.
     */
    function resolveListLogicalPageIndex() {
        if (typeof pagePropertiesLogicalIndex === "number" && pagePropertiesLogicalIndex >= 0
            && document.getElementById("pagePropSave")) {
            return pagePropertiesLogicalIndex;
        }
        let n = parseInt(typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0, 10);
        return isNaN(n) || n < 0 ? 0 : n;
    }

    function moveComponentOnPage(componentName, direction) {
        if (!componentName || typeof presentationName === "undefined") {
            return;
        }
        let logicalIndex = resolveListLogicalPageIndex();
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/pages/" + encodeURIComponent(logicalIndex)
                + "/components/" + encodeURIComponent(componentName) + "/move/",
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({direction: direction}),
            dataType: "json",
            success: function (result) {
                if (result && result.moved === false) {
                    return;
                }
                pendingSelectName = componentName;
                // Refresh list + canvas order
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(componentName);
                } else {
                    loadPageComponentList();
                    loadComponentGeometries();
                }
                // If page properties panel is open, refresh its list from GET
                if (document.getElementById("pagePropSave")
                    && typeof openPageProperties === "function"
                    && typeof pagePropertiesLogicalIndex === "number") {
                    // Keep panel: reload component list only
                    $.ajax({
                        url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                            + "/pages/" + encodeURIComponent(pagePropertiesLogicalIndex) + "/",
                        type: "GET",
                        dataType: "json",
                        success: function (data) {
                            if (data && data.components) {
                                loadPageComponentList(data.components);
                            }
                        }
                    });
                }
            },
            error: function (xhr) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Move component failed", xhr);
                } else {
                    alert("Move failed: " + (xhr.responseText || xhr.status));
                }
            }
        });
    }

    // ── Geometries ───────────────────────────────────────────────────────

    function loadComponentGeometries() {
        let q = (typeof renderSessionQuery === "function") ? renderSessionQuery() : "";
        $.ajax({
            url: API_BASE + "render/info/component-geometries/" + encodeURIComponent(renderId)
                + "/" + encodeURIComponent(renderPageNumber0) + "/" + q,
            type: "GET",
            dataType: "json",
            success: function (list, textStatus, xhr) {
                if (typeof applyRenderIdFromXhr === "function") {
                    applyRenderIdFromXhr(xhr);
                }
                componentGeometries = list || [];
                // Sync logical page for chrome label from any body-page geometry on this sheet
                for (let i = 0; i < componentGeometries.length; i++) {
                    let g = componentGeometries[i];
                    if (g && typeof g.logicalPageNumber === "number" && g.logicalPageNumber >= 0
                        && (!g.pageRole || g.pageRole === "page")) {
                        if (typeof editLogicalPageNumber !== "undefined") {
                            editLogicalPageNumber = g.logicalPageNumber;
                        }
                        break;
                    }
                }
                scheduleRedraw();
            },
            error: function (xhr) {
                // Render purged and rebuild failed: soft-reload editor by presentation name
                if (xhr && (xhr.status === 404 || xhr.status === 410)
                    && typeof softReloadEditor === "function"
                    && typeof presentationName !== "undefined" && presentationName) {
                    console.warn("Component geometries missing render; soft-reloading editor");
                    softReloadEditor(
                        typeof window.hopperEdit !== "undefined"
                            && window.hopperEdit.getSelectedName
                            ? window.hopperEdit.getSelectedName()
                            : null
                    );
                    return;
                }
                console.warn("Failed to load component geometries:", xhr.responseText || xhr.status);
                componentGeometries = [];
            }
        });
    }

    function findGeometry(name) {
        if (!name) {
            return null;
        }
        for (let i = 0; i < componentGeometries.length; i++) {
            if (componentGeometries[i].componentName === name) {
                return componentGeometries[i];
            }
        }
        return null;
    }

    function hitTest(pageX, pageY) {
        // Top-most drawn area on this render page (last in list = drawn later)
        for (let i = componentGeometries.length - 1; i >= 0; i--) {
            let entry = componentGeometries[i];
            let g = entry.geometry;
            if (!g || g.width <= 0 || g.height <= 0) {
                continue;
            }
            if (pageX >= g.x && pageY >= g.y
                && pageX <= g.x + g.width && pageY <= g.y + g.height) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Page-space distance that counts as "on the edge" (stable under zoom).
     */
    function edgeHitTolerancePage() {
        let sc = (typeof scale === "number" && scale > 0) ? scale : 1;
        return Math.max(3, EDGE_HIT_SCREEN_PX / sc);
    }

    /**
     * Which edges of {@code geo} are near (pageX, pageY). Only edges of a box that
     * actually contains the point (expanded by tolerance) are considered.
     * @returns {{n:boolean,s:boolean,e:boolean,w:boolean}|null}
     */
    function edgesNearPoint(geo, pageX, pageY) {
        if (!geo) {
            return null;
        }
        let tol = edgeHitTolerancePage();
        let x0 = geo.x;
        let y0 = geo.y;
        let x1 = geo.x + geo.width;
        let y1 = geo.y + geo.height;
        // Must be inside the box expanded by tol (so corners are hittable slightly outside)
        if (pageX < x0 - tol || pageX > x1 + tol || pageY < y0 - tol || pageY > y1 + tol) {
            return null;
        }
        let w = pageX >= x0 - tol && pageX <= x0 + tol;
        let e = pageX >= x1 - tol && pageX <= x1 + tol;
        let n = pageY >= y0 - tol && pageY <= y0 + tol;
        let s = pageY >= y1 - tol && pageY <= y1 + tol;
        // Interior only (no edge) → not a resize hit
        if (!w && !e && !n && !s) {
            return null;
        }
        // Ignore opposite-edge doubles on tiny boxes
        if (w && e) {
            w = pageX < (x0 + x1) / 2;
            e = !w;
        }
        if (n && s) {
            n = pageY < (y0 + y1) / 2;
            s = !n;
        }
        return {n: n, s: s, e: e, w: w};
    }

    /**
     * Prefer selected component edges, else top-most component under the pointer.
     * Edges slightly outside the fill (within tol) still count.
     * Suppressed while Shift is held (move-only mode for small components).
     * @returns {{entry:object, edges:{n,s,e,w}}|null}
     */
    function hitTestResize(pageX, pageY) {
        if (shiftSuppressesResize) {
            return null;
        }
        let tol = edgeHitTolerancePage();
        // Selected component first (easier to grab handles of the current selection)
        if (selectedComponentName) {
            let sel = findGeometry(selectedComponentName);
            if (sel && sel.geometry) {
                let edges = edgesNearPoint(sel.geometry, pageX, pageY);
                if (edges) {
                    return {entry: sel, edges: edges};
                }
            }
        }
        // Top-most geometry whose expanded bounds contain the pointer
        for (let i = componentGeometries.length - 1; i >= 0; i--) {
            let entry = componentGeometries[i];
            let g = entry.geometry;
            if (!g) {
                continue;
            }
            if (pageX < g.x - tol || pageX > g.x + g.width + tol
                || pageY < g.y - tol || pageY > g.y + g.height + tol) {
                continue;
            }
            let edges = edgesNearPoint(g, pageX, pageY);
            if (edges) {
                return {entry: entry, edges: edges};
            }
        }
        return null;
    }

    function cursorForEdges(edges) {
        if (!edges) {
            return null;
        }
        if (edges.n && edges.w) {
            return "nwse-resize";
        }
        if (edges.n && edges.e) {
            return "nesw-resize";
        }
        if (edges.s && edges.w) {
            return "nesw-resize";
        }
        if (edges.s && edges.e) {
            return "nwse-resize";
        }
        if (edges.n || edges.s) {
            return "ns-resize";
        }
        if (edges.e || edges.w) {
            return "ew-resize";
        }
        return null;
    }

    /**
     * Compute live geometry while resizing from origin + pointer delta and active edges.
     */
    function computeResizeGeo(origin, edges, dx, dy) {
        let x = origin.x;
        let y = origin.y;
        let w = origin.width;
        let h = origin.height;
        if (edges.e) {
            w = origin.width + dx;
        }
        if (edges.w) {
            x = origin.x + dx;
            w = origin.width - dx;
        }
        if (edges.s) {
            h = origin.height + dy;
        }
        if (edges.n) {
            y = origin.y + dy;
            h = origin.height - dy;
        }
        // Clamp minimum size; keep the opposite edge fixed
        if (w < MIN_RESIZE_PX) {
            if (edges.w && !edges.e) {
                x = origin.x + origin.width - MIN_RESIZE_PX;
            }
            w = MIN_RESIZE_PX;
        }
        if (h < MIN_RESIZE_PX) {
            if (edges.n && !edges.s) {
                y = origin.y + origin.height - MIN_RESIZE_PX;
            }
            h = MIN_RESIZE_PX;
        }
        return {x: Math.round(x), y: Math.round(y), width: Math.round(w), height: Math.round(h)};
    }

    function edgesEqual(a, b) {
        if (a === b) {
            return true;
        }
        if (!a || !b) {
            return false;
        }
        return a.n === b.n && a.s === b.s && a.e === b.e && a.w === b.w;
    }

    // ── Selection / hover ────────────────────────────────────────────────

    function resolvePageRoleForName(name) {
        let geo = findGeometry(name);
        if (geo && geo.pageRole) {
            return geo.pageRole;
        }
        for (let i = 0; i < pageComponents.length; i++) {
            if (pageComponents[i].name === name && pageComponents[i].pageRole) {
                return pageComponents[i].pageRole;
            }
        }
        return "page";
    }

    function selectComponent(name, fromList) {
        selectedComponentName = name;
        selectedPageRole = resolvePageRoleForName(name);
        // Keep global edit-mode save routing in sync with canvas/list selection
        if (typeof editPageRole !== "undefined") {
            editPageRole = selectedPageRole;
        }
        if (typeof editLogicalPageNumber !== "undefined") {
            let geo = findGeometry(name);
            if (geo && typeof geo.logicalPageNumber === "number" && geo.logicalPageNumber >= 0) {
                editLogicalPageNumber = geo.logicalPageNumber;
            }
        }
        let nodes = document.querySelectorAll("#pageComponentList .page-component-item");
        for (let i = 0; i < nodes.length; i++) {
            let n = nodes[i];
            if (n.getAttribute("data-component-name") === name) {
                n.classList.add("selected");
                if (fromList) {
                    n.scrollIntoView({block: "nearest"});
                }
            } else {
                n.classList.remove("selected");
            }
        }
        updateListToolbarState();
        scheduleRedraw();
        // Position after geometries redraw on next frame
        requestAnimationFrame(function () {
            updateSelectionToolbar();
        });
    }

    /**
     * @param {{keepBackgroundMenu?:boolean}} [opts]
     */
    function clearSelection(opts) {
        selectedComponentName = null;
        selectedPageRole = "page";
        let nodes = document.querySelectorAll("#pageComponentList .page-component-item");
        for (let i = 0; i < nodes.length; i++) {
            nodes[i].classList.remove("selected");
        }
        updateListToolbarState();
        hideSelectionToolbar();
        if (!(opts && opts.keepBackgroundMenu)) {
            hideBackgroundToolbar();
        }
        scheduleRedraw();
    }

    // ── Floating selection toolbar (cut / copy / paste / delete) ─────────

    function selectionToolbarIconUrl(iconName) {
        if (typeof resolveUiIcon === "function") {
            return resolveUiIcon(iconName);
        }
        if (typeof uiIconUrl === "function") {
            return uiIconUrl(iconName);
        }
        if (typeof window !== "undefined" && window.HThemeMode && window.HThemeMode.uiIconUrl) {
            return window.HThemeMode.uiIconUrl(iconName);
        }
        return (typeof API_BASE === "string" ? API_BASE : "/hopper/api/") + "static/images/" + iconName;
    }

    function toolbarHostElement() {
        let canvasEl = document.getElementById("svgCanvas");
        return canvasEl && canvasEl.parentElement
            ? canvasEl.parentElement
            : document.body;
    }

    /**
     * Create the floating toolbar once (inside .editor-main for positioning).
     * @returns {HTMLElement|null}
     */
    function ensureSelectionToolbar() {
        let existing = document.getElementById("componentSelectionToolbar");
        if (existing) {
            return existing;
        }
        let host = toolbarHostElement();
        let bar = document.createElement("div");
        bar.id = "componentSelectionToolbar";
        bar.className = "component-selection-toolbar";
        bar.setAttribute("role", "toolbar");
        bar.setAttribute("aria-label", "Component clipboard");
        bar.hidden = true;
        let actions = [
            {id: "edit", title: "Edit (Enter / double-click)", icon: "edit.svg"},
            {id: "interaction", title: "Add interaction", icon: "add-item.svg"},
            {id: "cut", title: "Cut (Ctrl+X)", icon: "cut.svg"},
            {id: "copy", title: "Copy (Ctrl+C)", icon: "copy.svg"},
            {id: "paste", title: "Paste (Ctrl+V)", icon: "paste.svg"},
            {id: "delete", title: "Delete (Del)", icon: "delete.svg"}
        ];
        for (let i = 0; i < actions.length; i++) {
            let a = actions[i];
            let btn = document.createElement("button");
            btn.type = "button";
            btn.setAttribute("data-sel-action", a.id);
            btn.title = a.title;
            btn.setAttribute("aria-label", a.title);
            let img = document.createElement("img");
            img.src = selectionToolbarIconUrl(a.icon);
            img.setAttribute("data-ui-icon", a.icon);
            img.alt = "";
            img.width = 16;
            img.height = 16;
            btn.appendChild(img);
            bar.appendChild(btn);
        }
        // Keep canvas drag/select from treating toolbar clicks as page hits
        bar.addEventListener("mousedown", function (e) {
            e.stopPropagation();
        });
        bar.addEventListener("click", function (e) {
            let btn = e.target.closest("button[data-sel-action]");
            if (!btn || btn.disabled || !bar.contains(btn)) {
                return;
            }
            e.preventDefault();
            e.stopPropagation();
            let action = btn.getAttribute("data-sel-action");
            if (action === "edit") {
                if (selectedComponentName) {
                    openPropertiesForComponent(selectedComponentName);
                }
            } else if (action === "interaction") {
                if (selectedComponentName
                    && typeof openComponentInteractionBuilder === "function") {
                    // If the property form is open for this component, return there after save/cancel
                    let returnToForm = (typeof oldComponentName !== "undefined"
                        && oldComponentName
                        && oldComponentName === selectedComponentName);
                    openComponentInteractionBuilder(selectedComponentName, {
                        returnToComponent: !!returnToForm
                    });
                }
            } else if (action === "cut") {
                cutSelectedComponent();
            } else if (action === "copy") {
                copySelectedComponent(function () {
                    updateSelectionToolbarPasteState();
                });
            } else if (action === "paste") {
                pasteComponent({atCursor: true});
            } else if (action === "delete") {
                deleteSelectedComponent();
            }
        });
        host.appendChild(bar);
        return bar;
    }

    function hideSelectionToolbar() {
        let bar = document.getElementById("componentSelectionToolbar");
        if (bar) {
            bar.hidden = true;
        }
    }

    function updateSelectionToolbarPasteState() {
        let bar = document.getElementById("componentSelectionToolbar");
        if (!bar) {
            return;
        }
        let pasteBtn = bar.querySelector('button[data-sel-action="paste"]');
        if (pasteBtn) {
            pasteBtn.disabled = !(componentClipboard && componentClipboard.componentJson);
        }
        updateBackgroundToolbarPasteState();
    }

    // ── Background click toolbar (edit / delete page / paste component) ──

    /**
     * 0-based logical body page for the sheet currently open in the editor.
     * Prefers {@code editLogicalPageNumber}; falls back to rendered page index.
     * @returns {number}
     */
    function currentLogicalPageIndex0() {
        if (typeof editLogicalPageNumber !== "undefined") {
            let l0 = parseInt(editLogicalPageNumber, 10);
            if (!isNaN(l0) && l0 >= 0) {
                return l0;
            }
        }
        if (typeof currentPageIndex0 === "function") {
            return currentPageIndex0();
        }
        if (typeof renderPageNumber0 !== "undefined") {
            return parseInt(renderPageNumber0, 10) || 0;
        }
        return 0;
    }

    /**
     * Known body-page count from presentation metadata (or null if unknown).
     * @returns {number|null}
     */
    function knownLogicalPageCount() {
        let pages = null;
        if (typeof presentationPropertiesWorking !== "undefined" && presentationPropertiesWorking
            && presentationPropertiesWorking.pages) {
            pages = presentationPropertiesWorking.pages;
        } else if (typeof presentationJson !== "undefined" && presentationJson
            && presentationJson.pages) {
            pages = presentationJson.pages;
        }
        if (pages && typeof pages.length === "number") {
            return pages.length;
        }
        return null;
    }

    /**
     * Floating menu when the user clicks empty page background.
     * @returns {HTMLElement|null}
     */
    function ensureBackgroundToolbar() {
        let existing = document.getElementById("pageBackgroundToolbar");
        if (existing) {
            return existing;
        }
        let host = toolbarHostElement();
        let bar = document.createElement("div");
        bar.id = "pageBackgroundToolbar";
        bar.className = "component-selection-toolbar page-background-toolbar";
        bar.setAttribute("role", "toolbar");
        bar.setAttribute("aria-label", "Page background");
        bar.hidden = true;
        let actions = [
            {id: "edit-page", title: "Edit page", icon: "edit.svg"},
            {id: "delete-page", title: "Delete page", icon: "delete.svg"},
            {id: "paste", title: "Paste component (Ctrl+V)", icon: "paste.svg"}
        ];
        for (let i = 0; i < actions.length; i++) {
            let a = actions[i];
            let btn = document.createElement("button");
            btn.type = "button";
            btn.setAttribute("data-bg-action", a.id);
            btn.title = a.title;
            btn.setAttribute("aria-label", a.title);
            let img = document.createElement("img");
            img.src = selectionToolbarIconUrl(a.icon);
            img.setAttribute("data-ui-icon", a.icon);
            img.alt = "";
            img.width = 16;
            img.height = 16;
            btn.appendChild(img);
            bar.appendChild(btn);
        }
        bar.addEventListener("mousedown", function (e) {
            e.stopPropagation();
        });
        bar.addEventListener("click", function (e) {
            let btn = e.target.closest("button[data-bg-action]");
            if (!btn || btn.disabled || !bar.contains(btn)) {
                return;
            }
            e.preventDefault();
            e.stopPropagation();
            let action = btn.getAttribute("data-bg-action");
            if (action === "edit-page") {
                hideBackgroundToolbar();
                let pageIdx = currentLogicalPageIndex0();
                if (typeof openPageProperties === "function") {
                    openPageProperties(pageIdx);
                }
            } else if (action === "delete-page") {
                hideBackgroundToolbar();
                let pageIdx = currentLogicalPageIndex0();
                if (typeof deletePresentationPage === "function") {
                    deletePresentationPage(pageIdx);
                }
            } else if (action === "paste") {
                hideBackgroundToolbar();
                pasteComponent({atCursor: true});
            }
        });
        host.appendChild(bar);
        return bar;
    }

    function hideBackgroundToolbar() {
        let bar = document.getElementById("pageBackgroundToolbar");
        if (bar) {
            bar.hidden = true;
        }
    }

    function updateBackgroundToolbarPasteState() {
        let bar = document.getElementById("pageBackgroundToolbar");
        if (!bar) {
            return;
        }
        let pasteBtn = bar.querySelector('button[data-bg-action="paste"]');
        if (pasteBtn) {
            pasteBtn.disabled = !(componentClipboard && componentClipboard.componentJson);
        }
        let delBtn = bar.querySelector('button[data-bg-action="delete-page"]');
        if (delBtn) {
            let count = knownLogicalPageCount();
            // Disable only when we know there is a single page; otherwise let the server decide
            delBtn.disabled = (count != null && count <= 1);
        }
    }

    /**
     * Position the background menu near the click (viewport / fixed coords).
     * @param {number} clientX
     * @param {number} clientY
     */
    function showBackgroundToolbar(clientX, clientY) {
        if (dragState && dragState.dragging) {
            hideBackgroundToolbar();
            return;
        }
        // Do not show page menu while a property form / component preview is open
        if (isPropertyPanelOpen()) {
            hideBackgroundToolbar();
            return;
        }
        hideSelectionToolbar();
        let bar = ensureBackgroundToolbar();
        updateBackgroundToolbarPasteState();
        // Refresh dual icons if theme changed while bar was hidden
        if (typeof window !== "undefined" && window.HThemeMode
            && typeof window.HThemeMode.refreshUiIcons === "function") {
            window.HThemeMode.refreshUiIcons(bar);
        }
        bar.hidden = false;
        let barW = bar.offsetWidth || 88;
        let barH = bar.offsetHeight || 32;
        // Place slightly above / to the right of the cursor
        let left = (typeof clientX === "number" ? clientX : 0) + 8;
        let top = (typeof clientY === "number" ? clientY : 0) - barH - 6;
        let maxLeft = window.innerWidth - barW - 4;
        let maxTop = window.innerHeight - barH - 4;
        left = Math.max(4, Math.min(left, maxLeft));
        top = Math.max(4, Math.min(top, maxTop));
        bar.style.left = Math.round(left) + "px";
        bar.style.top = Math.round(top) + "px";
    }

    /**
     * True when the component (or other) property side panel is open for edit/preview.
     * The floating selection toolbar is suppressed in that state so it does not cover
     * the form or the isolated component preview.
     */
    function isPropertyPanelOpen() {
        return !!(document.body && document.body.classList.contains("property-panel-open"));
    }

    /**
     * Show and position the toolbar above the selected component (canvas coords).
     * Hidden while dragging/resizing, while the property panel is open (edit/preview),
     * or when there is no selection geometry.
     *
     * Important: do not hide the page-background menu when there is no selection.
     * drawOverlays() calls this on every redraw; hiding the background menu here
     * would dismiss it immediately after a background click (clearSelection → redraw).
     */
    function updateSelectionToolbar() {
        if (!selectedComponentName) {
            hideSelectionToolbar();
            return;
        }
        // Selecting a component supersedes the page background menu
        hideBackgroundToolbar();
        if (dragState && dragState.dragging) {
            hideSelectionToolbar();
            return;
        }
        // Hide while editing/previewing the component in the side panel
        if (isPropertyPanelOpen()) {
            hideSelectionToolbar();
            return;
        }
        let canvasEl = document.getElementById("svgCanvas");
        if (!canvasEl || typeof scale !== "number" || !(scale > 0)) {
            hideSelectionToolbar();
            return;
        }
        let entry = findGeometry(selectedComponentName);
        if (!entry || !entry.geometry) {
            hideSelectionToolbar();
            return;
        }
        let geo = entry.geometry;
        let sc = scale;
        let off = (typeof offset !== "undefined" && offset) ? offset : {x: 0, y: 0};
        let iconBand = (typeof pageContentYOffset === "function")
            ? pageContentYOffset()
            : ((typeof ICON_SIZE === "number") ? ICON_SIZE : 28);

        let cssX = (geo.x - off.x) * sc;
        let cssY = iconBand + (geo.y - off.y) * sc;
        let cssW = Math.max(2, (geo.width > 0 ? geo.width : 2) * sc);

        let bar = ensureSelectionToolbar();
        updateSelectionToolbarPasteState();
        bar.hidden = false;

        // Viewport (fixed) coords so we are not sensitive to which ancestor is positioned
        let canvasRect = canvasEl.getBoundingClientRect();
        let barW = bar.offsetWidth || 120;
        let barH = bar.offsetHeight || 32;

        let left = canvasRect.left + cssX + cssW / 2 - barW / 2;
        let top = canvasRect.top + cssY - barH - 6;

        // If not enough room above (under page toolbar band), place just below the top edge
        let minTop = canvasRect.top + iconBand + 2;
        if (top < minTop) {
            top = canvasRect.top + cssY + 4;
        }

        let maxLeft = window.innerWidth - barW - 4;
        left = Math.max(4, Math.min(left, maxLeft));
        let maxTop = window.innerHeight - barH - 4;
        top = Math.max(4, Math.min(top, maxTop));

        bar.style.left = Math.round(left) + "px";
        bar.style.top = Math.round(top) + "px";
    }

    function updateListToolbarState() {
        let has = !!selectedComponentName;
        let editBtn = document.getElementById("btnComponentEdit");
        let delBtn = document.getElementById("btnComponentDelete");
        if (editBtn) {
            editBtn.disabled = !has;
        }
        if (delBtn) {
            delBtn.disabled = !has;
        }
        let addBtn = document.getElementById("btnComponentAdd");
        if (addBtn) {
            // Enabled once catalog is loaded (or always — picker falls back)
            addBtn.disabled = false;
        }
    }

    function wireListToolbar() {
        let editBtn = document.getElementById("btnComponentEdit");
        if (editBtn) {
            editBtn.onclick = function () {
                if (!selectedComponentName) {
                    return;
                }
                openPropertiesForComponent(selectedComponentName);
            };
        }
        let delBtn = document.getElementById("btnComponentDelete");
        if (delBtn) {
            delBtn.onclick = function () {
                if (!selectedComponentName) {
                    return;
                }
                deleteSelectedComponent();
            };
        }
        let addBtn = document.getElementById("btnComponentAdd");
        if (addBtn) {
            addBtn.onclick = function () {
                promptAddComponent();
            };
            addBtn.disabled = false;
        }
    }

    /**
     * Toolbar + : place a component without drag (dialog for type, fixed offset on page).
     */
    function promptAddComponent() {
        if (!componentPluginCatalog || componentPluginCatalog.length === 0) {
            alert("Component types are still loading or unavailable.");
            return;
        }
        let lines = [];
        for (let i = 0; i < componentPluginCatalog.length; i++) {
            let p = componentPluginCatalog[i];
            lines.push((i + 1) + ". " + (p.name || p.id) + " (" + p.id + ")");
        }
        let answer = prompt(
            "Add component type (number or plugin id):\n\n" + lines.join("\n"),
            "1"
        );
        if (answer === null) {
            return;
        }
        answer = String(answer).trim();
        let pluginId = null;
        let asNum = parseInt(answer, 10);
        if (!isNaN(asNum) && asNum >= 1 && asNum <= componentPluginCatalog.length) {
            pluginId = componentPluginCatalog[asNum - 1].id;
        } else {
            for (let i = 0; i < componentPluginCatalog.length; i++) {
                if (componentPluginCatalog[i].id === answer
                    || (componentPluginCatalog[i].name || "").toLowerCase() === answer.toLowerCase()) {
                    pluginId = componentPluginCatalog[i].id;
                    break;
                }
            }
        }
        if (!pluginId) {
            alert("Unknown component type: " + answer);
            return;
        }
        // Place near top-left of the content area (page space)
        addComponentAt(pluginId, 50, 50, true);
    }

    /**
     * Create component via server API, soft-reload, select (and optionally open properties).
     */
    /**
     * @param {string} pluginId
     * @param {number} pageX
     * @param {number} pageY
     * @param {boolean} openProps
     * @param {string} [region] header | content | footer (default content)
     */
    function addComponentAt(pluginId, pageX, pageY, openProps, region) {
        if (!pluginId || typeof presentationName === "undefined") {
            return;
        }
        let x = Math.round(pageX);
        let y = Math.round(pageY);
        if (isNaN(x)) {
            x = 50;
        }
        if (isNaN(y)) {
            y = 50;
        }
        // Prefer by-render so the body page matches the canvas the user dropped on
        let url = API_BASE + "edit/presentation/by-render/" + encodeURIComponent(renderId)
            + "/pages/" + encodeURIComponent(renderPageNumber0) + "/components/";
        let payload = {
            pluginId: pluginId,
            x: x,
            y: y
        };
        if (region === "header" || region === "footer") {
            payload.pageRole = region;
        }
        $.ajax({
            url: url,
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(payload),
            dataType: "json",
            success: function (data) {
                let newName = data && data.name ? data.name : null;
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(newName);
                } else if (typeof reloadPresentation === "function") {
                    reloadPresentation();
                }
                if (newName) {
                    // After soft reload refreshes list asynchronously; also select now
                    pendingSelectName = newName;
                    if (openProps) {
                        // Defer properties until geometries/list refresh settles
                        setTimeout(function () {
                            openPropertiesForComponent(newName);
                        }, 350);
                    }
                }
            },
            error: function (xhr) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Could not add component", xhr);
                } else {
                    alert("Could not add component: " + (xhr.responseText || xhr.status));
                }
            }
        });
    }

    function openPropertiesForComponent(name) {
        if (typeof openComponentPropertiesByName === "function") {
            openComponentPropertiesByName(name);
            return;
        }
        // Fallback: geometry-center hit
        let entry = findGeometry(name);
        let x = 1;
        let y = 1;
        if (entry && entry.geometry) {
            x = entry.geometry.x + Math.max(1, Math.floor(entry.geometry.width / 2));
            y = entry.geometry.y + Math.max(1, Math.floor(entry.geometry.height / 2));
        }
        onCtrlLeftClick({
            renderId: renderId,
            pageNumber: renderPageNumber0,
            x: x,
            y: y
        });
    }

    /**
     * @param {object} [options]
     * @param {boolean} [options.skipConfirm] skip the confirm dialog (cut)
     * @param {function():void} [options.onSuccess] after successful delete
     * @param {boolean} [options.keepSelection] do not clear selection (unused; cut clears)
     */
    function deleteSelectedComponent(options) {
        options = options || {};
        let name = selectedComponentName;
        if (!name) {
            return;
        }
        if (!options.skipConfirm
            && !confirm("Delete component '" + name + "' from this presentation?")) {
            return;
        }
        let keepPagePanel = !!document.getElementById("pagePropSave");
        let pageIdx = resolveListLogicalPageIndex();
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/components/" + encodeURIComponent(name) + "/",
            type: "DELETE",
            dataType: "text",
            success: function () {
                clearSelection();
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(null);
                } else if (typeof reloadPresentation === "function") {
                    reloadPresentation();
                }
                if (keepPagePanel && typeof openPageProperties === "function") {
                    // Stay on page properties with an updated component list
                    openPageProperties(pageIdx);
                } else if (typeof setSidePanelOpen === "function") {
                    setSidePanelOpen(false);
                }
                if (typeof options.onSuccess === "function") {
                    options.onSuccess();
                }
            },
            error: function (xhr, status, error) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Failed to delete component '" + name + "'", xhr, status, error);
                } else {
                    alert("Delete failed: " + (xhr.responseText || xhr.status));
                }
            }
        });
    }

    /**
     * Load selected component JSON into the in-memory clipboard.
     * @param {function():void} [onDone]
     */
    function copySelectedComponent(onDone) {
        let name = selectedComponentName;
        if (!name || typeof presentationName === "undefined") {
            return;
        }
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/components/" + encodeURIComponent(name) + "/",
            type: "GET",
            dataType: "json",
            success: function (payload) {
                if (!payload || !payload.component) {
                    if (typeof showErrorDialog === "function") {
                        showErrorDialog("Copy failed", "No component payload for '" + name + "'");
                    } else {
                        alert("Copy failed: no component payload");
                    }
                    return;
                }
                componentClipboard = {
                    componentJson: payload.component,
                    pageRole: payload.pageRole || selectedPageRole || "page",
                    sourcePresentation: presentationName
                };
                updateSelectionToolbarPasteState();
                if (typeof onDone === "function") {
                    onDone();
                }
            },
            error: function (xhr, status, error) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Failed to copy component '" + name + "'", xhr, status, error);
                } else {
                    alert("Copy failed: " + (xhr.responseText || xhr.status));
                }
            }
        });
    }

    function cutSelectedComponent() {
        if (!selectedComponentName) {
            return;
        }
        copySelectedComponent(function () {
            updateSelectionToolbarPasteState();
            deleteSelectedComponent({skipConfirm: true});
        });
    }

    /**
     * @returns {null|{x:number,y:number}} recent page-space pointer, or null
     */
    function getPastePagePoint() {
        if (!lastPagePointer) {
            return null;
        }
        let now = (typeof performance !== "undefined" && performance.now)
            ? performance.now() : Date.now();
        if (now - lastPagePointer.ts > PASTE_POINTER_MAX_AGE_MS) {
            return null;
        }
        return {x: Math.round(lastPagePointer.x), y: Math.round(lastPagePointer.y)};
    }

    /**
     * Paste clipboard component onto the current page.
     * When the mouse was recently over the canvas (Ctrl+V), places the clone at the cursor;
     * otherwise offsets from the original by {@link PASTE_OFFSET_PX} (toolbar Paste).
     * @param {{atCursor?:boolean}} [opts]
     */
    function pasteComponent(opts) {
        if (!componentClipboard || !componentClipboard.componentJson) {
            return;
        }
        if (typeof presentationName === "undefined" || !presentationName) {
            return;
        }
        let pageRole = componentClipboard.pageRole || "page";
        // Prefer body page when pasting at cursor over the canvas (header/footer geometry differs)
        let atCursor = !!(opts && opts.atCursor);
        let pastePoint = atCursor ? getPastePagePoint() : null;
        if (pastePoint && pageRole !== "page") {
            // Cursor is on the body canvas — paste onto the body page
            pageRole = "page";
        }
        // If header/footer no longer exists, fall back to body page
        if ((pageRole === "header" || pageRole === "footer")
            && typeof window.hopperEdit !== "undefined") {
            // Keep role; server returns an error if missing — client falls back below on error
        }
        let url;
        if (typeof renderId !== "undefined" && renderId) {
            url = API_BASE + "edit/presentation/by-render/" + encodeURIComponent(renderId)
                + "/pages/" + encodeURIComponent(renderPageNumber0) + "/components/paste/";
        } else {
            let logicalIndex = resolveListLogicalPageIndex();
            url = API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/pages/" + encodeURIComponent(logicalIndex) + "/components/paste/";
        }
        let body = {
            hopperComponentJson: JSON.stringify(componentClipboard.componentJson),
            pageRole: pageRole
        };
        if (pastePoint) {
            body.x = pastePoint.x;
            body.y = pastePoint.y;
        } else {
            body.dx = PASTE_OFFSET_PX;
            body.dy = PASTE_OFFSET_PX;
        }
        $.ajax({
            url: url,
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(body),
            dataType: "json",
            success: function (data) {
                let newName = data && data.name ? data.name : null;
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(newName);
                } else if (typeof reloadPresentation === "function") {
                    reloadPresentation();
                }
                if (newName) {
                    pendingSelectName = newName;
                    selectComponent(newName, false);
                }
            },
            error: function (xhr) {
                // Retry as body page if header/footer paste failed
                if (pageRole !== "page" && xhr && xhr.status >= 400) {
                    body.pageRole = "page";
                    $.ajax({
                        url: url,
                        type: "POST",
                        contentType: "application/json; charset=utf-8",
                        data: JSON.stringify(body),
                        dataType: "json",
                        success: function (data) {
                            let newName = data && data.name ? data.name : null;
                            if (typeof softReloadEditor === "function") {
                                softReloadEditor(newName);
                            } else if (typeof reloadPresentation === "function") {
                                reloadPresentation();
                            }
                            if (newName) {
                                pendingSelectName = newName;
                                selectComponent(newName, false);
                            }
                        },
                        error: function (xhr2) {
                            if (typeof showAjaxError === "function") {
                                showAjaxError("Paste component failed", xhr2);
                            } else {
                                alert("Paste failed: " + (xhr2.responseText || xhr2.status));
                            }
                        }
                    });
                    return;
                }
                if (typeof showAjaxError === "function") {
                    showAjaxError("Paste component failed", xhr);
                } else {
                    alert("Paste failed: " + (xhr.responseText || xhr.status));
                }
            }
        });
    }

    // ── Mouse + overlay drawing ──────────────────────────────────────────

    /** Record page-space pointer for Ctrl+V paste-at-cursor. */
    function notePagePointer(pageX, pageY) {
        if (pageX === null || pageX === undefined || isNaN(pageX)
            || pageY === null || pageY === undefined || isNaN(pageY)) {
            return;
        }
        lastPagePointer = {
            x: pageX,
            y: pageY,
            ts: (typeof performance !== "undefined" && performance.now)
                ? performance.now() : Date.now()
        };
    }

    function onPageMouseMove(pageX, pageY) {
        notePagePointer(pageX, pageY);
        // While dragging/resizing, cursor/hover are owned by the interaction
        if (dragState && dragState.dragging) {
            return;
        }
        if (pageX === null || pageX === undefined) {
            if (hoverComponentName !== null || lastHoverEdges !== null) {
                hoverComponentName = null;
                lastHoverName = null;
                lastHoverEdges = null;
                $("#svgCanvas").css("cursor", "default");
                scheduleRedraw();
            }
            return;
        }
        // Edge hit takes priority for resize cursors (selected edges, then top-most)
        let resizeHit = hitTestResize(pageX, pageY);
        if (resizeHit) {
            let name = resizeHit.entry.componentName;
            let edges = resizeHit.edges;
            let cursor = cursorForEdges(edges) || "grab";
            let nameChanged = name !== lastHoverName;
            let edgesChanged = !edgesEqual(edges, lastHoverEdges);
            if (nameChanged || edgesChanged) {
                lastHoverName = name;
                hoverComponentName = name;
                lastHoverEdges = edges;
                $("#svgCanvas").css("cursor", cursor);
                if (nameChanged) {
                    scheduleRedraw();
                }
            } else {
                $("#svgCanvas").css("cursor", cursor);
            }
            return;
        }
        lastHoverEdges = null;
        let hit = hitTest(pageX, pageY);
        let name = hit ? hit.componentName : null;
        if (name !== lastHoverName) {
            lastHoverName = name;
            hoverComponentName = name;
            $("#svgCanvas").css("cursor", name ? "grab" : "default");
            scheduleRedraw();
        } else {
            $("#svgCanvas").css("cursor", name ? "grab" : "default");
        }
    }

    function scheduleRedraw() {
        if (redrawScheduled) {
            return;
        }
        redrawScheduled = true;
        requestAnimationFrame(function () {
            redrawScheduled = false;
            if (typeof drawSvg === "function" && typeof image !== "undefined" && image) {
                drawSvg();
            }
        });
    }

    /**
     * Banner above/below the page while dragging a component toward another page.
     */
    function drawPageTransferIndicator(gcCtx, sc, off, region) {
        let page = null;
        let regions = getPageRegions();
        if (regions && regions.page) {
            page = regions.page;
        } else if (typeof pageLogicalSize === "function") {
            let sz = pageLogicalSize();
            if (sz && sz.width > 0 && sz.height > 0) {
                page = {x: 0, y: 0, width: sz.width, height: sz.height};
            }
        }
        if (!page || !gcCtx) {
            return;
        }
        let bandH = Math.max(28, PAGE_TRANSFER_ZONE_PX);
        let rect;
        let label;
        if (region === "prev-page") {
            rect = {
                x: page.x,
                y: page.y - bandH,
                width: page.width,
                height: bandH
            };
            label = "↑ Move to previous page (creates one if needed)";
        } else {
            rect = {
                x: page.x,
                y: page.y + page.height,
                width: page.width,
                height: bandH
            };
            label = "↓ Move to next page (creates one if needed)";
        }
        let x = (rect.x - off.x) * sc;
        let y = (rect.y - off.y) * sc;
        let w = rect.width * sc;
        let h = rect.height * sc;
        let dark = typeof isUiDarkMode === "function" && isUiDarkMode();
        gcCtx.save();
        gcCtx.fillStyle = dark
            ? "rgba(59, 130, 246, 0.28)"
            : "rgba(37, 99, 235, 0.22)";
        gcCtx.strokeStyle = dark
            ? "rgba(147, 197, 253, 0.95)"
            : "rgba(37, 99, 235, 0.9)";
        gcCtx.lineWidth = 2;
        gcCtx.setLineDash([8, 5]);
        gcCtx.fillRect(x, y, w, h);
        gcCtx.strokeRect(x + 0.5, y + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));
        gcCtx.setLineDash([]);
        gcCtx.fillStyle = dark ? "#e8eef9" : "#0e3a5a";
        gcCtx.font = "12px system-ui, -apple-system, Segoe UI, sans-serif";
        gcCtx.textAlign = "center";
        gcCtx.textBaseline = "middle";
        gcCtx.fillText(label, x + w / 2, y + h / 2);
        gcCtx.restore();
    }

    function drawOverlays(gcCtx, sc, off) {
        // Live move / resize outline (ghost)
        if (dragState && dragState.dragging && dragState.originGeo) {
            let g;
            if (dragState.mode === "resize" && dragState.liveGeo) {
                g = dragState.liveGeo;
            } else {
                g = {
                    x: dragState.originGeo.x + dragState.dx,
                    y: dragState.originGeo.y + dragState.dy,
                    width: dragState.originGeo.width,
                    height: dragState.originGeo.height
                };
            }
            // Page-transfer drop bands (above / below page)
            if (dragState.mode === "move"
                && (activeDropRegion === "prev-page" || activeDropRegion === "next-page")) {
                drawPageTransferIndicator(gcCtx, sc, off, activeDropRegion);
            }
            strokePageRect(gcCtx, g, sc, off, "rgba(30, 90, 200, 0.95)", 2, false, true);
            // Dim original position / size
            strokePageRect(
                gcCtx, dragState.originGeo, sc, off, "rgba(30, 90, 200, 0.35)", 1, false, true);
            hideSelectionToolbar();
            return;
        }
        if (selectedComponentName) {
            let sel = findGeometry(selectedComponentName);
            if (sel && sel.geometry && (sel.geometry.width > 0 || sel.geometry.height > 0)) {
                let overflow = !!sel.overflowsPage;
                strokePageRect(
                    gcCtx,
                    sel.geometry,
                    sc,
                    off,
                    overflow ? "rgba(220, 100, 30, 0.95)" : "rgba(30, 90, 200, 0.95)",
                    2.5,
                    true
                );
                if (overflow) {
                    drawOverflowBadge(gcCtx, sel.geometry, sc, off);
                }
            }
        }
        if (hoverComponentName && hoverComponentName !== selectedComponentName) {
            let hov = findGeometry(hoverComponentName);
            if (hov && hov.geometry && (hov.geometry.width > 0 || hov.geometry.height > 0)) {
                strokePageRect(gcCtx, hov.geometry, sc, off, "rgba(40, 120, 220, 0.55)", 1.5, false);
            }
        }
        // Keep floating clipboard toolbar aligned with selection (zoom/pan/redraw)
        updateSelectionToolbar();
    }

    /** Amber badge when a component extends past the usable page bottom (editor overflow policy). */
    function drawOverflowBadge(gcCtx, geo, sc, off) {
        if (!gcCtx || !geo) {
            return;
        }
        let x = (geo.x - off.x) * sc;
        let y = (geo.y - off.y) * sc;
        let label = "Extends past page bottom";
        gcCtx.save();
        gcCtx.font = "10px system-ui, -apple-system, Segoe UI, sans-serif";
        gcCtx.textAlign = "left";
        gcCtx.textBaseline = "bottom";
        gcCtx.fillStyle = "rgba(180, 70, 20, 0.95)";
        gcCtx.fillText(label, x + 2, y - 2);
        gcCtx.restore();
    }

    /**
     * Draw a rectangle in page space (same coords as DrawnItem geometry / correctX/Y).
     */
    function strokePageRect(gcCtx, geo, sc, off, color, lineWidth, fill, dashed) {
        let w = Math.max(0, geo.width);
        let h = Math.max(0, geo.height);
        if (w <= 0 && h <= 0) {
            return;
        }
        // Zero-width/height becomes a thin visible edge for debugging incomplete layouts
        if (w <= 0) {
            w = 2;
        }
        if (h <= 0) {
            h = 2;
        }
        let x = (geo.x - off.x) * sc;
        let y = (geo.y - off.y) * sc;
        w = w * sc;
        h = h * sc;
        gcCtx.save();
        gcCtx.strokeStyle = color;
        gcCtx.lineWidth = lineWidth;
        if (dashed) {
            gcCtx.setLineDash([6, 4]);
        } else {
            gcCtx.setLineDash([]);
        }
        gcCtx.strokeRect(x + 0.5, y + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));
        if (fill) {
            gcCtx.fillStyle = "rgba(30, 90, 200, 0.08)";
            gcCtx.fillRect(x, y, w, h);
        }
        gcCtx.restore();
    }

    /**
     * mousedown on canvas (edit mode): start potential move / resize / select.
     * Simple click selects only (blue border); double-click opens properties.
     * Drag past threshold moves or resizes.
     */
    function handleCanvasMouseDown(e, pageX, pageY, requestData) {
        if (document.body.classList.contains("property-panel-open")) {
            // Property panel open: keep click-to-edit behavior only
            if (typeof onCtrlLeftClick === "function") {
                onCtrlLeftClick(requestData);
            }
            return;
        }
        let toolH = (typeof pageContentYOffset === "function")
            ? pageContentYOffset()
            : ((typeof ICON_SIZE === "number") ? ICON_SIZE : 0);
        if (toolH > 0 && e.offsetY < toolH) {
            return;
        }
        // Resize takes priority when the pointer is on an edge/corner
        // (Shift suppresses resize so tiny components can still be moved)
        if (e && e.shiftKey) {
            shiftSuppressesResize = true;
        }
        let resizeHit = hitTestResize(pageX, pageY);
        if (resizeHit) {
            let hit = resizeHit.entry;
            if (hit.pageRole) {
                selectedPageRole = hit.pageRole;
            }
            selectComponent(hit.componentName, false);
            let geo = hit.geometry || {x: pageX, y: pageY, width: 40, height: 40};
            dragState = {
                mode: "resize",
                drawnName: hit.componentName,
                metadataName: hit.metadataName || hit.componentName,
                pageRole: hit.pageRole || "page",
                startPageX: pageX,
                startPageY: pageY,
                originGeo: {
                    x: geo.x,
                    y: geo.y,
                    width: geo.width,
                    height: geo.height
                },
                edges: {
                    n: !!resizeHit.edges.n,
                    s: !!resizeHit.edges.s,
                    e: !!resizeHit.edges.e,
                    w: !!resizeHit.edges.w
                },
                liveGeo: {
                    x: geo.x,
                    y: geo.y,
                    width: geo.width,
                    height: geo.height
                },
                dx: 0,
                dy: 0,
                dragging: false,
                openPropsOnUp: false,
                requestData: requestData
            };
            if (canvas) {
                canvas.style.cursor = cursorForEdges(dragState.edges) || "grabbing";
            }
            return;
        }

        let hit = hitTest(pageX, pageY);
        if (!hit) {
            // Background click: clear component selection and show page menu
            notePagePointer(pageX, pageY);
            clearSelection({keepBackgroundMenu: true});
            showBackgroundToolbar(e.clientX, e.clientY);
            return;
        }
        hideBackgroundToolbar();
        if (hit.pageRole) {
            selectedPageRole = hit.pageRole;
        }
        selectComponent(hit.componentName, false);
        let geo = hit.geometry || {x: pageX, y: pageY, width: 40, height: 40};
        dragState = {
            mode: "move",
            drawnName: hit.componentName,
            metadataName: hit.metadataName || hit.componentName,
            pageRole: hit.pageRole || "page",
            startPageX: pageX,
            startPageY: pageY,
            originGeo: {
                x: geo.x,
                y: geo.y,
                width: geo.width,
                height: geo.height
            },
            dx: 0,
            dy: 0,
            dragging: false,
            openPropsOnUp: false,
            requestData: requestData
        };
        if (canvas) {
            canvas.style.cursor = "grabbing";
        }
    }

    /**
     * Double-click on canvas: open properties for the component under the pointer.
     */
    function handleCanvasDoubleClick(e, pageX, pageY, requestData) {
        if (document.body.classList.contains("property-panel-open")) {
            // Already editing — treat as select/open for the hit component
            if (typeof onCtrlLeftClick === "function") {
                onCtrlLeftClick(requestData);
            }
            return;
        }
        let toolH2 = (typeof pageContentYOffset === "function")
            ? pageContentYOffset()
            : ((typeof ICON_SIZE === "number") ? ICON_SIZE : 0);
        if (toolH2 > 0 && e.offsetY < toolH2) {
            return;
        }
        let hit = hitTest(pageX, pageY);
        if (!hit) {
            return;
        }
        selectComponent(hit.componentName, false);
        if (typeof onCtrlLeftClick === "function" && requestData) {
            onCtrlLeftClick(requestData);
        } else {
            openPropertiesForComponent(hit.componentName);
        }
    }

    /**
     * @returns {boolean} true if the move was consumed by an active drag/resize
     */
    function onCanvasMouseMove(event, pageX, pageY) {
        if (!dragState) {
            return false;
        }
        // When pointer leaves the canvas, derive page coords from client position
        if (pageX === null || pageX === undefined || isNaN(pageX)) {
            if (typeof canvas === "undefined" || !canvas) {
                return true;
            }
            let rect = canvas.getBoundingClientRect();
            let ox = event.clientX - rect.left;
            let oy = event.clientY - rect.top;
            pageX = typeof correctX === "function" ? correctX(ox) : ox;
            pageY = typeof correctY === "function" ? correctY(oy) : oy;
        }
        let dx = Math.round(pageX - dragState.startPageX);
        let dy = Math.round(pageY - dragState.startPageY);
        if (!dragState.dragging
            && (Math.abs(dx) >= DRAG_THRESHOLD_PX || Math.abs(dy) >= DRAG_THRESHOLD_PX)) {
            dragState.dragging = true;
            dragState.openPropsOnUp = false;
            hideSelectionToolbar();
        }
        if (dragState.dragging) {
            dragState.dx = dx;
            dragState.dy = dy;
            notePagePointer(pageX, pageY);
            if (dragState.mode === "resize" && dragState.edges) {
                dragState.liveGeo = computeResizeGeo(
                    dragState.originGeo, dragState.edges, dx, dy);
                if (canvas) {
                    canvas.style.cursor = cursorForEdges(dragState.edges) || "grabbing";
                }
            } else {
                // Highlight target band under pointer; page-edge zones = move to adjacent page
                let transferOk = dragState.pageRole === "page" || !dragState.pageRole;
                setActiveDropRegion(hitTestPageRegion(pageX, pageY, {
                    allowPageTransfer: transferOk && dragState.mode === "move"
                }));
            }
            scheduleRedraw();
            return true;
        }
        return false;
    }

    function handleCanvasMouseUp(e) {
        if (!dragState) {
            return;
        }
        let state = dragState;
        // Capture transfer region before clear
        let transferRegion = activeDropRegion;
        dragState = null;
        clearActiveDropRegion();
        if (canvas) {
            canvas.style.cursor = "";
        }
        if (state.dragging) {
            if (state.mode === "resize" && state.liveGeo && state.originGeo) {
                let og = state.originGeo;
                let lg = state.liveGeo;
                if (lg.x !== og.x || lg.y !== og.y
                    || lg.width !== og.width || lg.height !== og.height) {
                    resizeComponentOnServer(state);
                    return;
                }
            } else if (state.mode === "move"
                && (transferRegion === "prev-page" || transferRegion === "next-page")
                && (state.pageRole === "page" || !state.pageRole)) {
                moveComponentToAdjacentPageOnServer(
                    state,
                    transferRegion === "next-page" ? "next" : "previous"
                );
                return;
            } else if (state.mode === "move" && (state.dx !== 0 || state.dy !== 0)) {
                nudgeComponentOnServer(state);
                return;
            }
        }
        // Simple click: keep selection only (edit is double-click / Enter / Edit button)
        if (state.openPropsOnUp && state.requestData) {
            if (typeof onCtrlLeftClick === "function") {
                onCtrlLeftClick(state.requestData);
            } else {
                openPropertiesForComponent(state.drawnName);
            }
        }
        scheduleRedraw();
        requestAnimationFrame(function () {
            updateSelectionToolbar();
        });
    }

    /**
     * Apply move/resize to local geometry immediately so hit-test and selection feel
     * instant. Soft-reload still replaces the SVG in the background (~1s decode).
     */
    function applyOptimisticGeometry(drawnName, metadataName, geoMutator) {
        let names = {};
        if (drawnName) {
            names[drawnName] = true;
        }
        if (metadataName) {
            names[metadataName] = true;
        }
        for (let i = 0; i < componentGeometries.length; i++) {
            let entry = componentGeometries[i];
            if (!entry || !entry.geometry) {
                continue;
            }
            if (!names[entry.componentName]
                && !(metadataName && entry.metadataName === metadataName)) {
                continue;
            }
            geoMutator(entry.geometry);
        }
    }

    function nudgeComponentOnServer(state) {
        let nameForApi = state.metadataName || state.drawnName;
        // Prefer drawn name for nested resolution (ComponentLookup handles both)
        let pathName = state.drawnName || nameForApi;
        let dx = state.dx;
        let dy = state.dy;
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/components/" + encodeURIComponent(pathName) + "/nudge/",
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({dx: dx, dy: dy}),
            dataType: "json",
            success: function (result) {
                let keep = (result && result.name) ? result.name : nameForApi;
                // Instant local feedback — do not wait for full page SVG re-decode
                applyOptimisticGeometry(state.drawnName, state.metadataName, function (g) {
                    g.x = (g.x || 0) + dx;
                    g.y = (g.y || 0) + dy;
                });
                if (state.drawnName) {
                    selectedComponentName = state.drawnName;
                } else if (keep) {
                    selectedComponentName = keep;
                }
                scheduleRedraw();
                if (typeof updateSelectionToolbar === "function") {
                    updateSelectionToolbar();
                }
                // Background: server SVG with correct content (may take ~1s to decode)
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(keep);
                } else if (typeof reloadPresentation === "function") {
                    reloadPresentation();
                }
            },
            error: function (xhr, status, error) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Move component failed", xhr, status, error);
                } else {
                    alert("Move failed: " + (xhr.responseText || status));
                }
                // Re-sync from server on failure
                if (typeof loadComponentGeometries === "function") {
                    loadComponentGeometries();
                }
                scheduleRedraw();
            }
        });
    }

    /**
     * Move component to previous/next logical page (server creates the page if needed),
     * then open that page in the editor with the component selected.
     */
    function moveComponentToAdjacentPageOnServer(state, direction) {
        let nameForApi = state.metadataName || state.drawnName;
        let pathName = state.drawnName || nameForApi;
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/components/" + encodeURIComponent(pathName) + "/move-page/",
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({direction: direction}),
            dataType: "json",
            success: function (result) {
                let keep = (result && result.name) ? result.name : nameForApi;
                let targetPage = result && typeof result.logicalPageNumber === "number"
                    ? result.logicalPageNumber
                    : -1;
                try {
                    if (keep) {
                        sessionStorage.setItem("hopperPendingSelect", keep);
                    }
                } catch (e) { /* ignore */ }
                // Navigate to the destination page (full editor URL so render id/page count refresh)
                if (targetPage >= 0 && typeof presentationName !== "undefined" && presentationName) {
                    let cm = typeof currentColorMode === "function" ? currentColorMode() : "light";
                    window.open(
                        API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                            + "/page/" + targetPage + "/?reload=true&colorMode="
                            + encodeURIComponent(cm),
                        "_self"
                    );
                    return;
                }
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(keep);
                } else if (typeof reloadPresentation === "function") {
                    reloadPresentation();
                }
            },
            error: function (xhr, status, error) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Move to " + direction + " page failed", xhr, status, error);
                } else {
                    alert("Move to page failed: " + (xhr.responseText || status));
                }
                if (typeof loadComponentGeometries === "function") {
                    loadComponentGeometries();
                }
                scheduleRedraw();
            }
        });
    }

    function resizeComponentOnServer(state) {
        let nameForApi = state.metadataName || state.drawnName;
        let pathName = state.drawnName || nameForApi;
        let og = state.originGeo;
        let lg = state.liveGeo;
        // Edge deltas in page space (what the server applies to layout attachments)
        let dLeft = lg.x - og.x;
        let dTop = lg.y - og.y;
        let dRight = (lg.x + lg.width) - (og.x + og.width);
        let dBottom = (lg.y + lg.height) - (og.y + og.height);
        $.ajax({
            url: API_BASE + "edit/presentation/" + encodeURIComponent(presentationName)
                + "/components/" + encodeURIComponent(pathName) + "/resize/",
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({
                dLeft: dLeft,
                dTop: dTop,
                dRight: dRight,
                dBottom: dBottom,
                originX: og.x,
                originY: og.y,
                originWidth: og.width,
                originHeight: og.height
            }),
            dataType: "json",
            success: function (result) {
                let keep = (result && result.name) ? result.name : nameForApi;
                applyOptimisticGeometry(state.drawnName, state.metadataName, function (g) {
                    g.x = lg.x;
                    g.y = lg.y;
                    g.width = lg.width;
                    g.height = lg.height;
                });
                if (state.drawnName) {
                    selectedComponentName = state.drawnName;
                } else if (keep) {
                    selectedComponentName = keep;
                }
                scheduleRedraw();
                if (typeof updateSelectionToolbar === "function") {
                    updateSelectionToolbar();
                }
                if (typeof softReloadEditor === "function") {
                    softReloadEditor(keep);
                } else if (typeof reloadPresentation === "function") {
                    reloadPresentation();
                }
            },
            error: function (xhr, status, error) {
                if (typeof showAjaxError === "function") {
                    showAjaxError("Resize component failed", xhr, status, error);
                } else {
                    alert("Resize failed: " + (xhr.responseText || status));
                }
                if (typeof loadComponentGeometries === "function") {
                    loadComponentGeometries();
                }
                scheduleRedraw();
            }
        });
    }

    function wireCanvasDrop() {
        let canvasEl = document.getElementById("svgCanvas");
        if (!canvasEl) {
            return;
        }
        canvasEl.addEventListener("dragenter", function (e) {
            if (isPaletteDrag(e)) {
                e.preventDefault();
                paletteDragActive = true;
                canvasEl.classList.add("canvas-drop-target");
            }
        });
        canvasEl.addEventListener("dragleave", function (e) {
            // Only clear when leaving the canvas itself (not entering a child)
            if (e.target === canvasEl) {
                canvasEl.classList.remove("canvas-drop-target");
                clearActiveDropRegion();
            }
        });
        canvasEl.addEventListener("dragover", function (e) {
            if (isPaletteDrag(e)) {
                e.preventDefault();
                e.dataTransfer.dropEffect = "copy";
                paletteDragActive = true;
                canvasEl.classList.add("canvas-drop-target");
                let pageX = typeof correctX === "function" ? correctX(e.offsetX) : e.offsetX;
                let pageY = typeof correctY === "function" ? correctY(e.offsetY) : e.offsetY;
                setActiveDropRegion(hitTestPageRegion(pageX, pageY));
            }
        });
        canvasEl.addEventListener("drop", function (e) {
            e.preventDefault();
            canvasEl.classList.remove("canvas-drop-target");
            clearActiveDropRegion();
            // Ignore drops on the toolbar icon strip (inline only; sticky chrome is separate)
            let dropToolH = (typeof pageContentYOffset === "function")
                ? pageContentYOffset()
                : ((typeof ICON_SIZE === "number") ? ICON_SIZE : 0);
            if (dropToolH > 0 && e.offsetY < dropToolH) {
                return;
            }
            let pluginId = e.dataTransfer.getData("text/hopper-component-plugin")
                || e.dataTransfer.getData("text/plain");
            if (!pluginId) {
                return;
            }
            pluginId = String(pluginId).trim();
            // Map canvas pixel → page coordinates (same as hit-test / lookup)
            let pageX = typeof correctX === "function" ? correctX(e.offsetX) : e.offsetX;
            let pageY = typeof correctY === "function" ? correctY(e.offsetY) : e.offsetY;
            if (typeof invalidMouseLocation === "function" && invalidMouseLocation(pageX, pageY)) {
                // Still allow drop slightly outside content: clamp to ≥ 0
                pageX = Math.max(0, pageX);
                pageY = Math.max(0, pageY);
            }
            // Target band for future header/footer drop; body is still the default add target
            let region = hitTestPageRegion(pageX, pageY);
            addComponentAt(pluginId, pageX, pageY, true, region);
        });
        // Clear highlight if palette drag ends without drop
        document.addEventListener("dragend", function () {
            clearActiveDropRegion();
            if (canvasEl) {
                canvasEl.classList.remove("canvas-drop-target");
            }
        });
    }

    function isPaletteDrag(e) {
        if (!e.dataTransfer || !e.dataTransfer.types) {
            return false;
        }
        let types = e.dataTransfer.types;
        // DOMStringList or array
        for (let i = 0; i < types.length; i++) {
            let t = types[i];
            if (t === "text/hopper-component-plugin" || t === "text/plain" || t === "Text") {
                return true;
            }
        }
        return false;
    }

    /**
     * Hold Shift = move-only (no edge resize). Re-run hover hit-test so the cursor
     * updates immediately without requiring another mousemove.
     */
    function setShiftSuppressesResize(on) {
        let next = !!on;
        if (shiftSuppressesResize === next) {
            return;
        }
        shiftSuppressesResize = next;
        // Not mid-drag: refresh cursor / hover from last known page pointer
        if (!(dragState && dragState.dragging) && lastPagePointer
            && typeof lastPagePointer.x === "number") {
            onPageMouseMove(lastPagePointer.x, lastPagePointer.y);
        } else if (!(dragState && dragState.dragging) && shiftSuppressesResize
            && lastHoverEdges) {
            lastHoverEdges = null;
            if (canvas) {
                canvas.style.cursor = hoverComponentName ? "grab" : "default";
            }
        }
    }

    document.addEventListener("keydown", function (e) {
        if (e.key === "Shift") {
            setShiftSuppressesResize(true);
        }
    }, true);
    document.addEventListener("keyup", function (e) {
        if (e.key === "Shift") {
            setShiftSuppressesResize(false);
        }
    }, true);
    window.addEventListener("blur", function () {
        setShiftSuppressesResize(false);
    });

    // Keyboard: cut/copy/paste/delete, Enter, Escape; Ctrl+Z/Y undo/redo (not in form fields)
    document.addEventListener("keydown", function (e) {
        let tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : "";
        let inField = tag === "input" || tag === "textarea" || tag === "select"
            || e.target.isContentEditable;
        if (inField) {
            return;
        }
        let mod = e.ctrlKey || e.metaKey;
        if (mod && !e.altKey) {
            let key = (e.key || "").toLowerCase();
            if (key === "z" && !e.shiftKey) {
                e.preventDefault();
                if (typeof presentationUndo === "function") {
                    presentationUndo();
                }
                return;
            }
            if (key === "y" || (key === "z" && e.shiftKey)) {
                e.preventDefault();
                if (typeof presentationRedo === "function") {
                    presentationRedo();
                }
                return;
            }
            if (key === "c") {
                if (selectedComponentName) {
                    e.preventDefault();
                    copySelectedComponent();
                }
                return;
            }
            if (key === "x") {
                if (selectedComponentName) {
                    e.preventDefault();
                    cutSelectedComponent();
                }
                return;
            }
            if (key === "v") {
                if (componentClipboard) {
                    e.preventDefault();
                    // Paste at last mouse position over the page (tracked on mousemove)
                    pasteComponent({atCursor: true});
                }
                return;
            }
        }
        if (e.key === "Escape") {
            if (typeof setSidePanelOpen === "function") {
                setSidePanelOpen(false);
            }
            hideBackgroundToolbar();
            clearSelection();
            return;
        }
        if (!selectedComponentName) {
            return;
        }
        if (e.key === "Delete" || e.key === "Backspace") {
            e.preventDefault();
            deleteSelectedComponent();
        } else if (e.key === "Enter") {
            e.preventDefault();
            openPropertiesForComponent(selectedComponentName);
        }
    });

    $(document).ready(function () {
        initEditShell();
        setTimeout(function () {
            loadComponentGeometries();
        }, 200);
    });

    let _origLoadDraw = typeof loadDrawSvgPage === "function" ? loadDrawSvgPage : null;
    if (_origLoadDraw) {
        // Delegate to presentation loader (PNG/SVG inline) then load geometries.
        // Forward pagePngScale so HiDPI soft-reload bitmaps map to presentation units.
        window.loadDrawSvgPage = function (inlineSvgXml, inlinePngBase64, pagePngScale) {
            let userCb = typeof _onPageSvgPainted === "function" ? _onPageSvgPainted : null;
            _onPageSvgPainted = function (svgParts) {
                let tGeo0 = (typeof performance !== "undefined" && performance.now)
                    ? performance.now() : Date.now();
                loadComponentGeometries();
                let tGeo1 = (typeof performance !== "undefined" && performance.now)
                    ? performance.now() : Date.now();
                if (svgParts && typeof svgParts === "object") {
                    svgParts.geometriesMs = Math.round(tGeo1 - tGeo0);
                }
                if (typeof userCb === "function") {
                    userCb(svgParts);
                }
            };
            _origLoadDraw(inlineSvgXml, inlinePngBase64, pagePngScale);
        };
    }

    window.hopperEdit = {
        reloadList: loadPageComponentList,
        /** Render a preloaded component list into #pageComponentList (page properties panel). */
        fillComponentList: function (rows) {
            loadPageComponentList(rows || []);
        },
        /** Wire Edit/Delete toolbar buttons if present in the current panel. */
        wireListToolbar: wireListToolbar,
        reloadGeometries: loadComponentGeometries,
        refresh: refreshEditorState,
        refreshHeaderFooter: loadHeaderFooterState,
        getSelectedName: function () {
            return selectedComponentName;
        },
        /** Component names on the current page (for interaction location pickers). */
        getComponentNames: function () {
            let names = [];
            for (let i = 0; i < pageComponents.length; i++) {
                if (pageComponents[i] && pageComponents[i].name) {
                    names.push(pageComponents[i].name);
                }
            }
            return names;
        },
        /** { name, pluginId } rows for the current page. */
        getPageComponents: function () {
            return pageComponents.slice();
        },
        selectComponent: selectComponent,
        clearSelection: clearSelection,
        hitTest: hitTest,
        getCatalog: function () {
            return componentPluginCatalog;
        },
        getGeometries: function () {
            return componentGeometries;
        },
        onPageMouseMove: onPageMouseMove,
        notePagePointer: notePagePointer,
        onCanvasMouseMove: onCanvasMouseMove,
        handleCanvasMouseDown: handleCanvasMouseDown,
        handleCanvasMouseUp: handleCanvasMouseUp,
        handleCanvasDoubleClick: handleCanvasDoubleClick,
        isDragging: function () {
            return !!(dragState && dragState.dragging);
        },
        isResizing: function () {
            return !!(dragState && dragState.dragging && dragState.mode === "resize");
        },
        getPageRegions: getPageRegions,
        getActiveDropRegion: getActiveDropRegion,
        drawOverlays: drawOverlays,
        openPropertiesForComponent: openPropertiesForComponent,
        deleteSelectedComponent: deleteSelectedComponent,
        copySelectedComponent: copySelectedComponent,
        cutSelectedComponent: cutSelectedComponent,
        pasteComponent: pasteComponent,
        updateSelectionToolbar: updateSelectionToolbar,
        hideSelectionToolbar: hideSelectionToolbar,
        hideBackgroundToolbar: hideBackgroundToolbar,
        addComponentAt: addComponentAt,
        promptAddComponent: promptAddComponent
    };

    // Reposition toolbar on window resize / zoom that redraws the canvas
    window.addEventListener("resize", function () {
        updateSelectionToolbar();
    });
})();
