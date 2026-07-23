/**
 * Visual Chain connector builder: left palette, top icon pipeline, step properties below.
 * Hooks into setNestedConnectorList / getNestedConnectorList for ChainConnector only.
 */
(function (global) {
    "use strict";

    const MIME_PLUGIN = "text/hopper-connector-plugin";
    const MIME_STEP = "text/hopper-chain-step-index";
    const EXCLUDE_FROM_PALETTE = { ChainConnector: true };

    /** @type {null|{
     *   active:boolean,
     *   fieldName:string,
     *   prefix:string,
     *   steps:Array.<object>,
     *   selectedIndex:number,
     *   selectedIsSource:boolean,
     *   dragFromIndex:number|null
     * }} */
    let state = null;

    function isChainConnectorForm() {
        return typeof connectorPluginId !== "undefined" && connectorPluginId === "ChainConnector";
    }

    function pluginLabel(pluginId) {
        if (typeof connectorCatalogById === "function") {
            let info = connectorCatalogById(pluginId);
            if (info && info.name) {
                return info.name;
            }
        }
        return pluginId || "Step";
    }

    function pluginIcon(pluginId) {
        if (typeof connectorPluginIconUrl === "function") {
            return connectorPluginIconUrl(pluginId);
        }
        return (typeof API_BASE !== "undefined" ? API_BASE : "/hopper/api/")
            + "static/images/connector.svg";
    }

    function wrapStep(flat) {
        if (!flat || !flat.pluginId) {
            return null;
        }
        let pluginId = flat.pluginId;
        let inner = Object.assign({}, flat);
        inner.pluginId = pluginId;
        inner.sourceConnectorName = null;
        let wrapped = {};
        wrapped[pluginId] = inner;
        return wrapped;
    }

    function defaultStep(pluginId) {
        return {
            pluginId: pluginId,
            sourceConnectorName: null
        };
    }

    function getChainVirtualPath() {
        let el = document.getElementById("connectorVirtualPath")
            || document.getElementById("chainEditorVirtualPath");
        return el ? (el.value || "").trim() : "";
    }

    function setChainVirtualPath(path) {
        let a = document.getElementById("connectorVirtualPath");
        let b = document.getElementById("chainEditorVirtualPath");
        if (a) {
            a.value = path || "";
        }
        if (b) {
            b.value = path || "";
        }
        if (typeof connectorJson !== "undefined" && connectorJson) {
            connectorJson.virtualPath = path || "";
        }
    }

    /**
     * Called from setNestedConnectorList when editing a ChainConnector.
     */
    function activate(parentObj, fieldName, prefix) {
        if (!isChainConnectorForm()) {
            return false;
        }
        let list = parentObj && parentObj[fieldName];
        let steps = [];
        if (Array.isArray(list)) {
            for (let i = 0; i < list.length; i++) {
                let flat = typeof unwrapConnectorStep === "function"
                    ? unwrapConnectorStep(list[i])
                    : list[i];
                if (flat && flat.pluginId) {
                    steps.push(flat);
                }
            }
        }
        state = {
            active: true,
            fieldName: fieldName || "connectors",
            prefix: prefix || "connectors",
            steps: steps,
            selectedIndex: steps.length ? 0 : -1,
            selectedIsSource: false,
            dragFromIndex: null
        };
        mountUi(prefix);
        renderAll();
        if (state.selectedIndex >= 0) {
            selectStep(state.selectedIndex, true);
        } else {
            selectSourceNode(true);
        }
        return true;
    }

    function deactivate() {
        state = null;
    }

    function isActive() {
        return !!(state && state.active);
    }

    /**
     * Flush selection + return wrapped steps for getNestedConnectorList.
     */
    function collectSteps() {
        if (!state) {
            return [];
        }
        flushSelectedStepForm();
        let out = [];
        for (let i = 0; i < state.steps.length; i++) {
            let w = wrapStep(state.steps[i]);
            if (w) {
                out.push(w);
            }
        }
        // Keep virtual path on the chain wrapper (shared default for the chain package)
        let vp = getChainVirtualPath();
        if (typeof connectorJson !== "undefined" && connectorJson) {
            connectorJson.virtualPath = vp;
        }
        return out;
    }

    function mountUi(prefix) {
        let items = document.getElementById(prefix + "_items");
        let fieldset = items ? items.closest(".nested-connector-list-fieldset") : null;
        if (!fieldset) {
            return;
        }
        // Hide legacy vertical list controls
        if (items) {
            items.style.display = "none";
        }
        let addBtn = document.getElementById(prefix + "_add");
        if (addBtn) {
            addBtn.style.display = "none";
        }
        let legend = fieldset.querySelector("legend");
        if (legend) {
            legend.textContent = "Chain pipeline";
        }
        let hint = fieldset.querySelector(".editor-hint");
        if (hint) {
            hint.style.display = "none";
        }

        let existing = document.getElementById("chainEditorRoot");
        if (existing) {
            existing.remove();
        }

        let root = document.createElement("div");
        root.id = "chainEditorRoot";
        root.className = "chain-editor";
        root.innerHTML = ""
            + '<div class="chain-editor-topbar">'
            + '  <label for="chainEditorSource" title="Outer input for the first step of the chain">'
            + "Source</label>"
            + '  <select id="chainEditorSource" class="chain-editor-source-select" '
            + 'title="Outer source connector for the first step"></select>'
            + '  <label for="chainEditorVirtualPath" title="Folder classification for this chain connector. '
            + 'Nested steps are stored inside the chain and share this path by default.">'
            + "Virtual path</label>"
            + '  <input type="text" id="chainEditorVirtualPath" class="chain-editor-virtual-path" '
            + 'placeholder="e.g. etl / sales" autocomplete="off">'
            + '  <span class="chain-editor-topbar-hint">Shared path for this chain</span>'
            + "</div>"
            + '<div class="chain-editor-body">'
            + '  <aside class="chain-palette" id="chainPalette" aria-label="Connector types">'
            + '    <h4 class="chain-palette-title">Connectors</h4>'
            + '    <p class="chain-palette-hint">Drag onto the pipeline</p>'
            + '    <div class="chain-palette-list" id="chainPaletteList"></div>'
            + "  </aside>"
            + '  <div class="chain-editor-main">'
            + '    <div class="chain-pipeline-scroll">'
            + '      <div class="chain-pipeline" id="chainPipeline" role="list"></div>'
            + "    </div>"
            + '    <div class="chain-step-props" id="chainStepProps">'
            + '      <p class="editor-hint" id="chainStepPropsHint">Select a step to edit its settings</p>'
            + '      <div id="chainStepPropsForm" class="chain-step-props-form"></div>'
            + "    </div>"
            + "  </div>"
            + "</div>";

        fieldset.appendChild(root);

        // Sync virtual path with existing form field
        let formVp = document.getElementById("connectorVirtualPath");
        let chainVp = document.getElementById("chainEditorVirtualPath");
        let initialVp = formVp ? (formVp.value || "") : (
            typeof connectorJson !== "undefined" && connectorJson
                ? (connectorJson.virtualPath || "")
                : ""
        );
        if (chainVp) {
            chainVp.value = initialVp;
            chainVp.addEventListener("input", function () {
                setChainVirtualPath(chainVp.value);
            });
            chainVp.addEventListener("change", function () {
                setChainVirtualPath(chainVp.value);
            });
        }
        // Virtual path lives in the chain top bar; hide the duplicate form field (name stays)
        if (formVp) {
            formVp.value = initialVp;
            hideFormControl("connectorVirtualPath");
        }
        // Source is edited via the top-bar combo (and Source node); hide generated control
        hideFormControl("sourceConnectorName");
        wireSourceSelect();

        // Widen studio panel for chain editing
        document.body.classList.add("chain-editor-open");
        if (typeof setSidePanelOpen === "function") {
            try {
                setSidePanelOpen(true, { connectorStudio: true, chainEditor: true });
            } catch (e) { /* ignore */ }
        }

        buildPalette();
        wireKeyboard();
    }

    /**
     * Populate the always-visible Source combo and keep #sourceConnectorName in sync.
     */
    function wireSourceSelect() {
        let local = document.getElementById("chainEditorSource");
        let sourceEl = document.getElementById("sourceConnectorName");
        if (!local) {
            return;
        }

        // Prefer current value from the original form field or connector JSON
        let current = "";
        if (sourceEl && sourceEl.value) {
            current = sourceEl.value;
        } else if (typeof connectorJson !== "undefined" && connectorJson
            && connectorJson.connector && connectorJson.connector.ChainConnector) {
            current = connectorJson.connector.ChainConnector.sourceConnectorName || "";
        }

        populateSourceOptions(local, current);

        // Also ensure the hidden original select has the same options (for save scripts)
        if (sourceEl) {
            populateSourceOptions(sourceEl, current);
            sourceEl.value = current || "";
        }

        local.addEventListener("change", function () {
            applySourceValue(local.value);
        });
    }

    function populateSourceOptions(selectEl, selectedValue) {
        if (!selectEl) {
            return;
        }
        // Warm caches then resolve connector names
        if (typeof ensureFormMetadataCaches === "function") {
            try {
                ensureFormMetadataCaches();
            } catch (e) { /* ignore */ }
        }
        let names = [];
        if (typeof resolveSelectSourceValues === "function") {
            names = resolveSelectSourceValues("connectors", {}) || [];
        } else if (typeof connectorNames !== "undefined" && connectorNames) {
            names = connectorNames.slice();
        } else if (typeof getPresentationConnectorNames === "function") {
            names = getPresentationConnectorNames() || [];
        } else if (typeof getConnectorNames === "function") {
            names = getConnectorNames() || [];
        }
        // Exclude the chain being edited (can't source itself)
        let selfName = "";
        if (typeof connectorJson !== "undefined" && connectorJson && connectorJson.name) {
            selfName = connectorJson.name;
        }
        let nameEl = document.getElementById("connectorName");
        if (nameEl && nameEl.value) {
            selfName = nameEl.value;
        }

        selectEl.innerHTML = "";
        let empty = document.createElement("option");
        empty.value = "";
        empty.textContent = "(none)";
        selectEl.appendChild(empty);

        let seen = {};
        for (let i = 0; i < names.length; i++) {
            let n = names[i];
            if (n == null || n === "" || seen[n] || n === selfName) {
                continue;
            }
            seen[n] = true;
            let opt = document.createElement("option");
            opt.value = n;
            opt.textContent = n;
            selectEl.appendChild(opt);
        }
        // Keep current selection even if not in list (renamed / local)
        if (selectedValue && !seen[selectedValue] && selectedValue !== selfName) {
            let missing = document.createElement("option");
            missing.value = selectedValue;
            missing.textContent = selectedValue;
            selectEl.appendChild(missing);
        }
        if (selectedValue) {
            selectEl.value = selectedValue;
        } else {
            selectEl.value = "";
        }
    }

    function applySourceValue(value) {
        let v = (value || "").trim();
        let sourceEl = document.getElementById("sourceConnectorName");
        if (sourceEl) {
            // Ensure option exists on the hidden select for form save
            let found = false;
            for (let i = 0; i < sourceEl.options.length; i++) {
                if (sourceEl.options[i].value === v) {
                    found = true;
                    break;
                }
            }
            if (!found && v) {
                let o = document.createElement("option");
                o.value = v;
                o.textContent = v;
                sourceEl.appendChild(o);
            }
            sourceEl.value = v;
            try {
                sourceEl.dispatchEvent(new Event("change", { bubbles: true }));
            } catch (e) { /* ignore */ }
        }
        if (typeof connectorJson !== "undefined" && connectorJson
            && connectorJson.connector && connectorJson.connector.ChainConnector) {
            connectorJson.connector.ChainConnector.sourceConnectorName = v || null;
        }
        let top = document.getElementById("chainEditorSource");
        if (top && top.value !== v) {
            top.value = v;
        }
        renderPipeline();
        if (typeof scheduleConnectorPreview === "function") {
            scheduleConnectorPreview(300);
        }
    }

    function getSourceValue() {
        let top = document.getElementById("chainEditorSource");
        if (top && top.value) {
            return top.value;
        }
        let sourceEl = document.getElementById("sourceConnectorName");
        if (sourceEl && sourceEl.value) {
            return sourceEl.value;
        }
        if (typeof connectorJson !== "undefined" && connectorJson
            && connectorJson.connector && connectorJson.connector.ChainConnector) {
            return connectorJson.connector.ChainConnector.sourceConnectorName || "";
        }
        return "";
    }

    function hideFormControl(id) {
        let el = document.getElementById(id);
        if (!el) {
            return;
        }
        el.style.display = "none";
        let lab = document.querySelector('label[for="' + id + '"]');
        if (lab) {
            lab.style.display = "none";
        }
        // Hide adjacent labels used for source-connector-row layout
        let row = el.closest(".source-connector-row") || el.closest(".form-field") || el.parentElement;
        if (row && row.classList && row.classList.contains("source-connector-row")) {
            row.style.display = "none";
        }
        let n = el.nextSibling;
        if (n && n.nodeName === "BR") {
            n.style.display = "none";
        }
    }

    function buildPalette() {
        let list = document.getElementById("chainPaletteList");
        if (!list) {
            return;
        }
        list.innerHTML = "";
        let ids = typeof connectorCatalogPluginIds === "function"
            ? connectorCatalogPluginIds()
            : [];
        if (!ids.length && typeof ensureConnectorPluginList === "function") {
            // catalog may populate async; try known fallbacks
            ids = ["SampleDataConnector", "CsvConnector", "SqlConnector", "HRestConnector",
                "HListConnector", "SelectionConnector", "SimpleFilterConnector", "SortConnector",
                "DistinctConnector", "AggregateConnector", "PassthroughConnector"];
        }
        // Prefer transforms after sources for visual grouping
        let preferred = [
            "SampleDataConnector", "CsvConnector", "SqlConnector", "HRestConnector", "HListConnector",
            "SelectionConnector", "SimpleFilterConnector", "SortConnector", "DistinctConnector",
            "AggregateConnector", "PassthroughConnector"
        ];
        let ordered = [];
        preferred.forEach(function (id) {
            if (ids.indexOf(id) >= 0 && !EXCLUDE_FROM_PALETTE[id]) {
                ordered.push(id);
            }
        });
        ids.forEach(function (id) {
            if (ordered.indexOf(id) < 0 && !EXCLUDE_FROM_PALETTE[id]) {
                ordered.push(id);
            }
        });

        ordered.forEach(function (pluginId) {
            let btn = document.createElement("div");
            btn.className = "chain-palette-item";
            btn.draggable = true;
            btn.setAttribute("data-plugin-id", pluginId);
            btn.title = pluginLabel(pluginId) + " — drag onto the pipeline";
            btn.innerHTML = ""
                + '<img class="chain-palette-icon" src="' + pluginIcon(pluginId)
                + '" width="28" height="28" alt="" draggable="false">'
                + '<span class="chain-palette-label">' + escapeHtml(pluginLabel(pluginId)) + "</span>";
            btn.addEventListener("dragstart", function (e) {
                e.dataTransfer.setData(MIME_PLUGIN, pluginId);
                e.dataTransfer.setData("text/plain", pluginId);
                e.dataTransfer.effectAllowed = "copy";
                btn.classList.add("chain-palette-item-dragging");
            });
            btn.addEventListener("dragend", function () {
                btn.classList.remove("chain-palette-item-dragging");
                clearDropHighlights();
            });
            list.appendChild(btn);
        });
    }

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function renderAll() {
        renderPipeline();
    }

    function renderPipeline() {
        let pipe = document.getElementById("chainPipeline");
        if (!pipe || !state) {
            return;
        }
        pipe.innerHTML = "";

        // Leading source node (chain outer source)
        pipe.appendChild(buildSourceNode());
        pipe.appendChild(buildGap(0));

        if (!state.steps.length) {
            let empty = document.createElement("div");
            empty.className = "chain-pipeline-empty";
            empty.textContent = "Drop a connector here";
            wireDropTarget(empty, state.steps.length);
            pipe.appendChild(empty);
            pipe.appendChild(buildEndZone());
            return;
        }

        for (let i = 0; i < state.steps.length; i++) {
            pipe.appendChild(buildStepNode(i));
            if (i < state.steps.length - 1) {
                pipe.appendChild(buildGap(i + 1));
            }
        }
        pipe.appendChild(buildGap(state.steps.length));
        pipe.appendChild(buildEndZone());
    }

    function buildSourceNode() {
        let node = document.createElement("div");
        node.className = "chain-node chain-node-source"
            + (state.selectedIsSource ? " chain-node-selected" : "");
        node.setAttribute("role", "listitem");
        node.title = "Outer source connector for the first step";
        let sourceName = getSourceValue();
        let label = sourceName || "(none)";
        node.innerHTML = ""
            + '<div class="chain-node-icon-wrap">'
            + '  <img class="chain-node-icon" src="'
            + (typeof API_BASE !== "undefined" ? API_BASE : "/hopper/api/")
            + 'static/images/connector.svg" width="48" height="48" alt="Source">'
            + "</div>"
            + '<div class="chain-node-label">Source</div>'
            + '<div class="chain-node-sublabel" title="' + escapeHtml(label) + '">'
            + escapeHtml(label) + "</div>";
        node.addEventListener("click", function (e) {
            e.stopPropagation();
            selectSourceNode(false);
            // Focus the always-visible top-bar source combo
            let top = document.getElementById("chainEditorSource");
            if (top) {
                top.focus();
            }
        });
        return node;
    }

    function buildStepNode(index) {
        let step = state.steps[index];
        let pluginId = step.pluginId;
        let node = document.createElement("div");
        node.className = "chain-node chain-node-step"
            + (state.selectedIndex === index && !state.selectedIsSource ? " chain-node-selected" : "");
        node.draggable = true;
        node.setAttribute("data-step-index", String(index));
        node.setAttribute("role", "listitem");
        node.title = pluginLabel(pluginId);
        node.innerHTML = ""
            + '<div class="chain-node-icon-wrap">'
            + '  <img class="chain-node-icon" src="' + pluginIcon(pluginId)
            + '" width="48" height="48" alt="" draggable="false">'
            + "</div>"
            + '<div class="chain-node-label">' + escapeHtml(pluginLabel(pluginId)) + "</div>"
            + '<button type="button" class="chain-node-delete" title="Remove step" data-index="'
            + index + '">×</button>';

        node.addEventListener("click", function (e) {
            if (e.target && e.target.classList.contains("chain-node-delete")) {
                return;
            }
            e.stopPropagation();
            selectStep(index, false);
        });
        let del = node.querySelector(".chain-node-delete");
        if (del) {
            del.addEventListener("click", function (e) {
                e.stopPropagation();
                removeStep(index);
            });
        }
        node.addEventListener("dragstart", function (e) {
            state.dragFromIndex = index;
            e.dataTransfer.setData(MIME_STEP, String(index));
            e.dataTransfer.setData("text/plain", "step:" + index);
            e.dataTransfer.effectAllowed = "move";
            node.classList.add("chain-node-dragging");
        });
        node.addEventListener("dragend", function () {
            state.dragFromIndex = null;
            node.classList.remove("chain-node-dragging");
            clearDropHighlights();
        });
        // Drop on node = insert before this index
        wireDropTarget(node, index);
        return node;
    }

    function buildGap(insertIndex) {
        let gap = document.createElement("div");
        gap.className = "chain-gap";
        gap.setAttribute("data-insert-index", String(insertIndex));
        gap.innerHTML = '<span class="chain-gap-line"></span><span class="chain-gap-arrow">›</span>';
        wireDropTarget(gap, insertIndex);
        return gap;
    }

    function buildEndZone() {
        let end = document.createElement("div");
        end.className = "chain-end-zone";
        end.setAttribute("data-insert-index", String(state.steps.length));
        end.innerHTML = '<span class="chain-end-plus" title="Drop here to append">+</span>';
        wireDropTarget(end, state.steps.length);
        return end;
    }

    function wireDropTarget(el, insertIndex) {
        el.addEventListener("dragover", function (e) {
            e.preventDefault();
            e.stopPropagation();
            let types = e.dataTransfer.types;
            let isStep = false;
            for (let i = 0; i < types.length; i++) {
                if (types[i] === MIME_STEP || types[i] === "text/plain") {
                    isStep = true;
                }
            }
            e.dataTransfer.dropEffect = state && state.dragFromIndex != null ? "move" : "copy";
            el.classList.add("chain-drop-hover");
        });
        el.addEventListener("dragleave", function () {
            el.classList.remove("chain-drop-hover");
        });
        el.addEventListener("drop", function (e) {
            e.preventDefault();
            e.stopPropagation();
            el.classList.remove("chain-drop-hover");
            handleDrop(e, insertIndex);
        });
    }

    function clearDropHighlights() {
        let root = document.getElementById("chainEditorRoot");
        if (!root) {
            return;
        }
        root.querySelectorAll(".chain-drop-hover").forEach(function (el) {
            el.classList.remove("chain-drop-hover");
        });
    }

    function handleDrop(e, insertIndex) {
        if (!state) {
            return;
        }
        let pluginId = e.dataTransfer.getData(MIME_PLUGIN);
        let stepIdxStr = e.dataTransfer.getData(MIME_STEP);
        if (!pluginId && e.dataTransfer.getData("text/plain")) {
            let plain = e.dataTransfer.getData("text/plain");
            if (plain.indexOf("step:") === 0) {
                stepIdxStr = plain.substring(5);
            } else if (plain && plain.indexOf("Connector") >= 0) {
                pluginId = plain;
            }
        }

        if (stepIdxStr !== "" && stepIdxStr != null && !pluginId) {
            let from = parseInt(stepIdxStr, 10);
            if (!isNaN(from)) {
                moveStep(from, insertIndex);
                return;
            }
        }
        if (pluginId && !EXCLUDE_FROM_PALETTE[pluginId]) {
            insertStep(insertIndex, pluginId);
        }
    }

    function flushSelectedStepForm() {
        if (!state || state.selectedIsSource || state.selectedIndex < 0) {
            return;
        }
        let prefix = "chainStep";
        let formHost = document.getElementById("chainStepPropsForm");
        if (!formHost || !formHost.querySelector("#" + prefix + "_pluginId")) {
            // Form may use hidden type field
        }
        if (typeof readNestedConnectorFromPanel === "function") {
            // Ensure type select exists for reader
            let typeEl = document.getElementById(prefix + "_pluginId");
            if (typeEl) {
                let wrapped = readNestedConnectorFromPanel(prefix);
                if (wrapped) {
                    let flat = typeof unwrapConnectorStep === "function"
                        ? unwrapConnectorStep(wrapped)
                        : wrapped;
                    if (flat && flat.pluginId) {
                        state.steps[state.selectedIndex] = flat;
                    }
                }
            }
        }
    }

    function selectSourceNode(skipFlush) {
        if (!state) {
            return;
        }
        if (!skipFlush) {
            flushSelectedStepForm();
        }
        state.selectedIsSource = true;
        state.selectedIndex = -1;
        renderPipeline();
        let form = document.getElementById("chainStepPropsForm");
        let hint = document.getElementById("chainStepPropsHint");
        if (hint) {
            hint.hidden = false;
            hint.textContent = "Chain source: use the Source dropdown in the bar above "
                + "(or below) to choose the outer input connector for the first step.";
        }
        if (form) {
            form.innerHTML = "";
            let wrap = document.createElement("div");
            wrap.className = "chain-source-props";
            wrap.innerHTML = ""
                + "<label for=\"chainSourceConnectorName\">Source connector</label>"
                + "<select id=\"chainSourceConnectorName\" class=\"chain-source-select\"></select>"
                + "<p class=\"editor-hint\">This is the outer input for the first pipeline step. "
                + "It is also available in the top bar.</p>";
            form.appendChild(wrap);
            let local = document.getElementById("chainSourceConnectorName");
            let current = getSourceValue();
            populateSourceOptions(local, current);
            if (local) {
                local.addEventListener("change", function () {
                    applySourceValue(local.value);
                    // Keep top-bar select in sync
                    let top = document.getElementById("chainEditorSource");
                    if (top) {
                        top.value = local.value;
                    }
                });
            }
            // Keep top bar in sync / focused
            let top = document.getElementById("chainEditorSource");
            if (top) {
                populateSourceOptions(top, current);
            }
        }
    }

    function selectStep(index, skipFlush) {
        if (!state || index < 0 || index >= state.steps.length) {
            return;
        }
        if (!skipFlush) {
            flushSelectedStepForm();
        }
        state.selectedIsSource = false;
        state.selectedIndex = index;
        renderPipeline();
        showStepForm(index);
    }

    function showStepForm(index) {
        let form = document.getElementById("chainStepPropsForm");
        let hint = document.getElementById("chainStepPropsHint");
        if (!form || !state) {
            return;
        }
        if (hint) {
            hint.hidden = true;
        }
        form.innerHTML = "";
        let step = state.steps[index];
        let pluginId = step.pluginId;
        let prefix = "chainStep";

        // Hidden type select for readNestedConnectorFromPanel compatibility
        let typeSelect = document.createElement("select");
        typeSelect.id = prefix + "_pluginId";
        typeSelect.style.display = "none";
        let opt = document.createElement("option");
        opt.value = pluginId;
        opt.textContent = pluginLabel(pluginId);
        typeSelect.appendChild(opt);
        typeSelect.value = pluginId;
        form.appendChild(typeSelect);

        let header = document.createElement("div");
        header.className = "chain-step-props-header";
        header.innerHTML = ""
            + '<img src="' + pluginIcon(pluginId) + '" width="24" height="24" alt="">'
            + "<strong>" + escapeHtml(pluginLabel(pluginId)) + "</strong>"
            + ' <label class="chain-step-type-change">Change type '
            + '<select id="chainStepTypeChange"></select></label>';
        form.appendChild(header);

        let typeChange = header.querySelector("#chainStepTypeChange");
        if (typeChange && typeof connectorCatalogPluginIds === "function") {
            let ids = connectorCatalogPluginIds();
            ids.forEach(function (id) {
                if (EXCLUDE_FROM_PALETTE[id]) {
                    return;
                }
                let o = document.createElement("option");
                o.value = id;
                o.textContent = pluginLabel(id);
                if (id === pluginId) {
                    o.selected = true;
                }
                typeChange.appendChild(o);
            });
            typeChange.addEventListener("change", function () {
                let newId = typeChange.value;
                if (!newId || newId === pluginId) {
                    return;
                }
                flushSelectedStepForm();
                state.steps[index] = defaultStep(newId);
                // Virtual path remains on the chain wrapper only
                selectStep(index, true);
            });
        }

        let fieldsHost = document.createElement("div");
        fieldsHost.id = prefix + "_pluginFields";
        fieldsHost.className = "nested-connector-plugin-fields chain-step-fields";
        form.appendChild(fieldsHost);

        if (typeof rebuildNestedConnectorPluginFields === "function") {
            let upstreamCols = getUpstreamColumnNames(index);
            rebuildNestedConnectorPluginFields(prefix, pluginId, step, upstreamCols);
        }
    }

    /**
     * Columns available as input to step at {@code stepIndex} (output of previous steps / outer source).
     * @param {number} stepIndex
     * @returns {Array.<string>}
     */
    function getUpstreamColumnNames(stepIndex) {
        if (!state) {
            return [];
        }
        // First step: only the outer source connector
        if (stepIndex <= 0) {
            let src = getSourceValue();
            if (src && typeof getConnectorColumnNames === "function") {
                return getConnectorColumnNames(src) || [];
            }
            return [];
        }
        // Later steps: describe a partial chain with only previous nested steps.
        // Do not flush the step form here — showStepForm may have already cleared the DOM.
        // selectStep() flushes the previous step before changing selection.
        let prevSteps = [];
        for (let i = 0; i < stepIndex; i++) {
            let w = wrapStep(state.steps[i]);
            if (w) {
                prevSteps.push(w);
            }
        }
        if (!prevSteps.length) {
            let src = getSourceValue();
            return (src && typeof getConnectorColumnNames === "function")
                ? (getConnectorColumnNames(src) || [])
                : [];
        }
        let partial = {
            name: (typeof connectorJson !== "undefined" && connectorJson && connectorJson.name)
                ? connectorJson.name
                : "_chain_partial",
            virtualPath: getChainVirtualPath(),
            connector: {
                ChainConnector: {
                    pluginId: "ChainConnector",
                    sourceConnectorName: getSourceValue() || null,
                    connectors: prevSteps
                }
            }
        };
        if (typeof describeInlineConnectorColumnNames === "function") {
            let names = describeInlineConnectorColumnNames(partial) || [];
            if (names.length) {
                return names;
            }
        }
        // Fallback: last previous step if it is Aggregate — derive from group + aggregate headers
        let last = state.steps[stepIndex - 1];
        if (last && last.pluginId === "AggregateConnector") {
            return deriveAggregateOutputNames(last);
        }
        return [];
    }

    function deriveAggregateOutputNames(step) {
        let names = [];
        let groups = step.groupColumns || [];
        for (let i = 0; i < groups.length; i++) {
            let g = groups[i];
            if (!g) {
                continue;
            }
            let n = (g.headerValue && String(g.headerValue).trim())
                ? g.headerValue
                : g.columnName;
            if (n) {
                names.push(n);
            }
        }
        let aggs = step.aggregates || [];
        for (let i = 0; i < aggs.length; i++) {
            let a = aggs[i];
            if (!a) {
                continue;
            }
            let n = (a.headerValue && String(a.headerValue).trim())
                ? a.headerValue
                : a.columnName;
            if (n) {
                names.push(n);
            }
        }
        return names;
    }

    function insertStep(insertIndex, pluginId) {
        if (!state) {
            return;
        }
        flushSelectedStepForm();
        let idx = Math.max(0, Math.min(insertIndex, state.steps.length));
        let step = defaultStep(pluginId);
        state.steps.splice(idx, 0, step);
        selectStep(idx, true);
        if (typeof scheduleConnectorPreview === "function") {
            scheduleConnectorPreview(400);
        }
    }

    function moveStep(fromIndex, toIndex) {
        if (!state || fromIndex < 0 || fromIndex >= state.steps.length) {
            return;
        }
        flushSelectedStepForm();
        let step = state.steps[fromIndex];
        state.steps.splice(fromIndex, 1);
        let dest = toIndex;
        if (fromIndex < toIndex) {
            dest = toIndex - 1;
        }
        dest = Math.max(0, Math.min(dest, state.steps.length));
        state.steps.splice(dest, 0, step);
        selectStep(dest, true);
        if (typeof scheduleConnectorPreview === "function") {
            scheduleConnectorPreview(400);
        }
    }

    function removeStep(index) {
        if (!state || index < 0 || index >= state.steps.length) {
            return;
        }
        flushSelectedStepForm();
        state.steps.splice(index, 1);
        if (!state.steps.length) {
            state.selectedIndex = -1;
            selectSourceNode(true);
        } else {
            let next = Math.min(index, state.steps.length - 1);
            selectStep(next, true);
        }
        if (typeof scheduleConnectorPreview === "function") {
            scheduleConnectorPreview(400);
        }
    }

    function wireKeyboard() {
        let root = document.getElementById("chainEditorRoot");
        if (!root || root._chainKeysWired) {
            return;
        }
        root._chainKeysWired = true;
        root.tabIndex = 0;
        root.addEventListener("keydown", function (e) {
            if (!state || state.selectedIsSource) {
                return;
            }
            if (e.key === "Delete" || e.key === "Backspace") {
                let t = e.target;
                if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.tagName === "SELECT")) {
                    return;
                }
                if (state.selectedIndex >= 0) {
                    e.preventDefault();
                    removeStep(state.selectedIndex);
                }
            }
            if (e.key === "ArrowLeft" && state.selectedIndex > 0) {
                e.preventDefault();
                selectStep(state.selectedIndex - 1, false);
            }
            if (e.key === "ArrowRight" && state.selectedIndex < state.steps.length - 1) {
                e.preventDefault();
                selectStep(state.selectedIndex + 1, false);
            }
        });
    }

    function onStudioClosed() {
        document.body.classList.remove("chain-editor-open");
        deactivate();
    }

    // Public API
    global.HopperChainEditor = {
        activate: activate,
        isActive: isActive,
        collectSteps: collectSteps,
        flushSelectedStepForm: flushSelectedStepForm,
        onStudioClosed: onStudioClosed,
        getState: function () {
            return state;
        }
    };
})(window);
