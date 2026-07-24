/**
 * Shared metadata list chrome: filter, virtual-path groups, icon + name button + actions.
 * Used by home-page and hopper-presentation-rest edit side panels. Depends on optional global API_BASE.
 */
(function (global) {
    "use strict";

    function apiBase() {
        if (typeof global.API_BASE === "string" && global.API_BASE) {
            return global.API_BASE;
        }
        return "/hopper/api/";
    }

    function escapeHtmlText(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function escapeHtmlAttribute(value) {
        return escapeHtmlText(value).replace(/'/g, "&#39;");
    }

    function normalizeVirtualPath(p) {
        if (p == null) {
            return "";
        }
        return String(p).trim();
    }

    /**
     * @param {Array.<Object>} rows
     * @param {string} query
     * @returns {Array.<Object>}
     */
    function filterMetadataRows(rows, query) {
        let list = Array.isArray(rows) ? rows.slice() : [];
        let q = (query == null ? "" : String(query)).trim().toLowerCase();
        if (!q) {
            return list;
        }
        return list.filter(function (row) {
            if (!row) {
                return false;
            }
            let name = String(row.name || "").toLowerCase();
            let desc = String(row.description || "").toLowerCase();
            let path = normalizeVirtualPath(row.virtualPath).toLowerCase();
            let full = path ? (path + "/" + name) : name;
            return name.indexOf(q) >= 0
                || desc.indexOf(q) >= 0
                || path.indexOf(q) >= 0
                || full.indexOf(q) >= 0;
        });
    }

    /**
     * @param {Array.<Object>} rows
     * @returns {{grouped:boolean, groups:Array.<{path:string,label:string,items:Array}>}}
     */
    function groupMetadataRows(rows) {
        let list = Array.isArray(rows) ? rows.slice() : [];
        list.sort(function (a, b) {
            let an = String((a && a.name) || "");
            let bn = String((b && b.name) || "");
            return an.localeCompare(bn, undefined, {sensitivity: "base"});
        });
        let hasAnyPath = false;
        for (let i = 0; i < list.length; i++) {
            if (normalizeVirtualPath(list[i] && list[i].virtualPath)) {
                hasAnyPath = true;
                break;
            }
        }
        if (!hasAnyPath) {
            return {grouped: false, groups: [{path: "", label: "", items: list}]};
        }
        let buckets = {};
        let order = [];
        for (let j = 0; j < list.length; j++) {
            let row = list[j];
            let path = normalizeVirtualPath(row && row.virtualPath);
            if (!Object.prototype.hasOwnProperty.call(buckets, path)) {
                buckets[path] = [];
                order.push(path);
            }
            buckets[path].push(row);
        }
        order.sort(function (a, b) {
            if (a === "" && b !== "") {
                return -1;
            }
            if (b === "" && a !== "") {
                return 1;
            }
            return a.localeCompare(b, undefined, {sensitivity: "base"});
        });
        let groups = [];
        for (let k = 0; k < order.length; k++) {
            let p = order[k];
            groups.push({
                path: p,
                label: p === "" ? "(root)" : p,
                items: buckets[p]
            });
        }
        return {grouped: true, groups: groups};
    }

    /**
     * Fetch summary rows for a metadata key.
     * @param {string} key e.g. presentation, connector, theme, hopper-database-connection
     * @param {function(Array)|null} done async callback; if omitted, sync XHR returns array
     * @returns {Array|undefined}
     */
    function fetchMetadataSummary(key, done) {
        let url = apiBase() + "metadata/summary/" + encodeURIComponent(key) + "/";
        if (typeof done === "function") {
            if (typeof global.jQuery !== "undefined" || typeof global.$ !== "undefined") {
                let $ = global.jQuery || global.$;
                $.ajax({
                    url: url,
                    type: "GET",
                    dataType: "json",
                    success: function (list) {
                        done(list || []);
                    },
                    error: function () {
                        done([]);
                    }
                });
                return;
            }
            fetch(url)
                .then(function (r) {
                    return r.ok ? r.json() : [];
                })
                .then(function (list) {
                    done(Array.isArray(list) ? list : []);
                })
                .catch(function () {
                    done([]);
                });
            return;
        }
        // Synchronous fallback (edit side panels historically use async:false)
        let rows = [];
        if (typeof global.jQuery !== "undefined" || typeof global.$ !== "undefined") {
            let $jq = global.jQuery || global.$;
            $jq.ajax({
                url: url,
                type: "GET",
                dataType: "json",
                async: false,
                success: function (list) {
                    rows = list || [];
                },
                error: function () {
                    rows = [];
                }
            });
        }
        return rows;
    }

    /**
     * Build list table HTML (no outer chrome title).
     *
     * options:
     *  - rows: array
     *  - filterQuery: string
     *  - iconForRow(row) → { url, title? } or string url
     *  - actions: array of { id, iconUrl, title, className? }
     *      id used in data-meta-action; built-ins: primary uses name button
     *  - emptyMessage
     *  - listId: id for table element
     *
     * Each row gets data-meta-name; action buttons get data-meta-action + data-meta-name.
     */
    function buildMetadataListTableHtml(options) {
        options = options || {};
        let rows = filterMetadataRows(options.rows || [], options.filterQuery || "");
        let grouped = groupMetadataRows(rows);
        let listId = options.listId || "metaListTable";
        let emptyMessage = options.emptyMessage || "No items found.";
        let actions = options.actions || [];
        let actionColCount = actions.length;
        let colCount = 2 + actionColCount; // icon + name + actions

        let html = "";
        html += "<div class=\"meta-list-table-wrap\">";
        html += "<table class=\"meta-list-table\" id=\"" + escapeHtmlAttribute(listId) + "\">";
        html += "<thead><tr>";
        html += "<th class=\"meta-list-col-icon\"></th>";
        html += "<th>Name</th>";
        if (actionColCount > 0) {
            html += "<th class=\"meta-list-col-actions\" colspan=\"" + actionColCount + "\"></th>";
        }
        html += "</tr></thead><tbody>";

        let rowCount = 0;
        for (let g = 0; g < grouped.groups.length; g++) {
            let group = grouped.groups[g];
            if (grouped.grouped) {
                html += "<tr class=\"meta-list-group-row\"><td colspan=\"" + colCount + "\">"
                    + "<div class=\"meta-list-group-label\">"
                    + escapeHtmlText(group.label)
                    + "</div></td></tr>";
            }
            for (let i = 0; i < group.items.length; i++) {
                let row = group.items[i];
                let name = row && row.name;
                if (name == null || name === "") {
                    continue;
                }
                rowCount++;
                let desc = row.description || "";
                let tip = desc ? String(desc) : String(name);
                let iconInfo = typeof options.iconForRow === "function"
                    ? options.iconForRow(row)
                    : (options.defaultIconUrl || "");
                let iconUrl = "";
                let iconTitle = "";
                if (iconInfo && typeof iconInfo === "object") {
                    iconUrl = iconInfo.url || "";
                    iconTitle = iconInfo.title || "";
                } else {
                    iconUrl = iconInfo || "";
                }

                html += "<tr class=\"meta-list-data-row\" data-meta-name=\""
                    + escapeHtmlAttribute(name) + "\">";
                html += "<td class=\"meta-list-col-icon\">";
                if (iconUrl) {
                    html += "<img class=\"meta-list-type-icon\" src=\""
                        + escapeHtmlAttribute(iconUrl) + "\" width=\"20\" height=\"20\" alt=\"\" "
                        + "title=\"" + escapeHtmlAttribute(iconTitle || tip) + "\">";
                }
                html += "</td>";
                html += "<td><button type=\"button\" class=\"meta-list-name-btn\" "
                    + "data-meta-name=\"" + escapeHtmlAttribute(name) + "\" "
                    + "data-meta-action=\"primary\" "
                    + "title=\"" + escapeHtmlAttribute(tip) + "\">"
                    + escapeHtmlText(name) + "</button></td>";
                for (let a = 0; a < actions.length; a++) {
                    let act = actions[a];
                    html += "<td class=\"meta-list-col-action\">";
                    html += "<button type=\"button\" class=\"list-row-btn meta-list-action-btn "
                        + escapeHtmlAttribute(act.className || "") + "\" "
                        + "data-meta-name=\"" + escapeHtmlAttribute(name) + "\" "
                        + "data-meta-action=\"" + escapeHtmlAttribute(act.id) + "\" "
                        + "title=\"" + escapeHtmlAttribute(act.title || act.id) + "\">";
                    let actIconBare = bareUiIconName(act.iconName || act.iconUrl);
                    html += "<img src=\"" + escapeHtmlAttribute(act.iconUrl) + "\" "
                        + (actIconBare ? "data-ui-icon=\"" + escapeHtmlAttribute(actIconBare) + "\" " : "")
                        + "alt=\"" + escapeHtmlAttribute(act.title || act.id)
                        + "\" width=\"16\" height=\"16\">";
                    html += "</button></td>";
                }
                html += "</tr>";
            }
        }
        if (rowCount === 0) {
            html += "<tr><td colspan=\"" + colCount + "\" class=\"meta-list-empty\">"
                + escapeHtmlText(emptyMessage) + "</td></tr>";
        }
        html += "</tbody></table></div>";
        return html;
    }

    /**
     * Full panel HTML: title, filter, optional create slot, table, footer actions.
     *
     * options: title, hint, createHtml, footerHtml, filterId, filterPlaceholder,
     *          plus buildMetadataListTableHtml options
     */
    function buildMetadataListPanelHtml(options) {
        options = options || {};
        let filterId = options.filterId || "metaListFilter";
        let placeholder = options.filterPlaceholder
            || "Filter by name, description, or path…";
        let html = "";
        if (options.title) {
            html += "<h3>" + escapeHtmlText(options.title) + "</h3>";
        }
        if (options.hint) {
            html += "<p class=\"editor-hint\">" + options.hint + "</p>";
        }
        html += "<div class=\"meta-list-filter-row\">";
        html += "<input type=\"search\" class=\"meta-list-filter\" id=\""
            + escapeHtmlAttribute(filterId) + "\" placeholder=\""
            + escapeHtmlAttribute(placeholder) + "\" value=\""
            + escapeHtmlAttribute(options.filterQuery || "") + "\" autocomplete=\"off\">";
        html += "</div>";
        if (options.createHtml) {
            html += "<div class=\"meta-list-create\">" + options.createHtml + "</div>";
        }
        html += "<div class=\"meta-list-body\" id=\""
            + escapeHtmlAttribute(options.bodyId || "metaListBody") + "\">";
        html += buildMetadataListTableHtml(options);
        html += "</div>";
        if (options.footerHtml) {
            html += "<div class=\"admin-list-actions meta-list-footer\">"
                + options.footerHtml + "</div>";
        }
        return html;
    }

    /**
     * Bind click delegation on a root element for data-meta-action buttons.
     * handlers: { primary: fn(name, row), edit: fn, view: fn, delete: fn, ... }
     * rowsByName: map name → row for richer callbacks (optional)
     *
     * Safe to call repeatedly on the same root (e.g. after list refresh): handlers are
     * replaced; only one click listener is attached so delete confirm does not fire N times.
     */
    function bindMetadataListHandlers(root, handlers, rowsByName) {
        if (!root || !handlers) {
            return;
        }
        root._hopperMetaListHandlers = handlers;
        root._hopperMetaListRowsByName = rowsByName || null;
        if (root._hopperMetaListClickBound) {
            return;
        }
        root._hopperMetaListClickBound = true;
        root.addEventListener("click", function (e) {
            let btn = e.target.closest("button[data-meta-action]");
            if (!btn || !root.contains(btn)) {
                return;
            }
            e.preventDefault();
            e.stopPropagation();
            let action = btn.getAttribute("data-meta-action");
            let name = btn.getAttribute("data-meta-name");
            if (!action || !name) {
                return;
            }
            let h = root._hopperMetaListHandlers || {};
            let byName = root._hopperMetaListRowsByName;
            let row = byName && byName[name] ? byName[name] : {name: name};
            let fn = h[action];
            if (typeof fn === "function") {
                fn(name, row, btn);
            }
        });
    }

    /**
     * Copy-friendly error dialog (browser alert truncates and is hard to select/copy).
     * @param {string} title
     * @param {string|*} detail
     */
    function showErrorDialog(title, detail) {
        let text = "";
        if (detail === null || detail === undefined) {
            text = "";
        } else if (typeof detail === "string") {
            text = detail;
        } else if (detail instanceof Error) {
            text = detail.stack || detail.message || String(detail);
        } else {
            try {
                text = JSON.stringify(detail, null, 2);
            } catch (e) {
                text = String(detail);
            }
        }
        let existing = document.getElementById("hopperErrorDialog");
        if (existing) {
            existing.remove();
        }
        let overlay = document.createElement("div");
        overlay.id = "hopperErrorDialog";
        overlay.className = "hopper-error-overlay";
        overlay.innerHTML =
            '<div class="hopper-error-dialog" role="dialog" aria-modal="true">'
            + '<div class="hopper-error-title"></div>'
            + '<textarea class="hopper-error-body" readonly rows="14" spellcheck="false"></textarea>'
            + '<div class="hopper-error-actions">'
            + '<button type="button" class="hopper-error-copy">Copy</button>'
            + '<button type="button" class="hopper-error-close">Close</button>'
            + "</div></div>";
        overlay.querySelector(".hopper-error-title").textContent = title || "Error";
        let ta = overlay.querySelector(".hopper-error-body");
        ta.value = text;
        function close() {
            overlay.remove();
            document.removeEventListener("keydown", onKey);
        }
        function onKey(e) {
            if (e.key === "Escape") {
                close();
            }
        }
        overlay.querySelector(".hopper-error-close").onclick = close;
        overlay.querySelector(".hopper-error-copy").onclick = function () {
            ta.select();
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(ta.value);
                } else {
                    document.execCommand("copy");
                }
                this.textContent = "Copied";
                let btn = this;
                setTimeout(function () {
                    btn.textContent = "Copy";
                }, 1500);
            } catch (err) {
                // leave selection for Ctrl+C
            }
        };
        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) {
                close();
            }
        });
        document.addEventListener("keydown", onKey);
        document.body.appendChild(overlay);
        setTimeout(function () {
            ta.focus();
            ta.select();
        }, 0);
    }

    /**
     * Format an AJAX failure into the error dialog.
     */
    function showAjaxError(title, xhr, status, error) {
        let body = "";
        if (xhr) {
            if (xhr.responseText) {
                body = xhr.responseText;
            } else if (xhr.status) {
                body = "HTTP " + xhr.status + (xhr.statusText ? " " + xhr.statusText : "");
            }
        }
        if (status) {
            body = (body ? body + "\n\n" : "") + "status: " + status;
        }
        if (error) {
            body = (body ? body + "\n" : "") + "error: " + error;
        }
        if (!body) {
            body = "(no details)";
        }
        showErrorDialog(title || "Request failed", body);
    }

    /**
     * Wire filter input to re-render table body.
     */
    function bindMetadataListFilter(filterEl, bodyEl, optionsFactory) {
        if (!filterEl || !bodyEl || typeof optionsFactory !== "function") {
            return;
        }
        let timer = null;
        function refresh() {
            let opts = optionsFactory(filterEl.value || "");
            bodyEl.innerHTML = buildMetadataListTableHtml(opts);
        }
        filterEl.addEventListener("input", function () {
            if (timer) {
                clearTimeout(timer);
            }
            timer = setTimeout(refresh, 120);
        });
    }

    function rowsByNameMap(rows) {
        let map = {};
        let list = rows || [];
        for (let i = 0; i < list.length; i++) {
            if (list[i] && list[i].name) {
                map[list[i].name] = list[i];
            }
        }
        return map;
    }

    function bareUiIconName(nameOrUrl) {
        if (!nameOrUrl) {
            return "";
        }
        let s = String(nameOrUrl);
        // Plugin / absolute non-chrome icons: do not dual-route
        if (s.indexOf("plugin") >= 0 || s.indexOf("http") === 0) {
            return "";
        }
        return s
            .replace(/^.*\/static\/images\//, "")
            .replace(/^images\//, "")
            .replace(/^dark\//, "")
            .replace(/^\/+/, "");
    }

    function staticImage(name) {
        let bare = bareUiIconName(name) || String(name || "").replace(/^.*\//, "");
        if (typeof uiIconUrl === "function") {
            return uiIconUrl(bare);
        }
        if (typeof window !== "undefined" && window.HThemeMode && window.HThemeMode.uiIconUrl) {
            return window.HThemeMode.uiIconUrl(bare);
        }
        return apiBase() + "static/images/" + bare;
    }

    /**
     * Suggest a unique-looking copy name (server will still unique-check via exists).
     * @param {string} sourceName
     * @returns {string}
     */
    function suggestCopyName(sourceName) {
        let base = String(sourceName == null ? "" : sourceName).trim();
        if (!base) {
            return "Copy";
        }
        if (/\scopy$/i.test(base) || /\s\(copy\)$/i.test(base)) {
            return base + " 2";
        }
        return base + " copy";
    }

    /**
     * Clone a metadata document under a new name (GET + set name + POST).
     * Refuses if the target name already exists in the current summary list when provided.
     *
     * @param {string} key metadata type key
     * @param {string} sourceName
     * @param {object} [options]
     * @param {Array} [options.existingRows] summary rows to check for name collision
     * @param {function(string):void} [options.onSuccess] called with new name
     * @param {function(string):void} [options.onCancel]
     */
    function copyMetadataAs(key, sourceName, options) {
        options = options || {};
        if (!key || !sourceName) {
            return;
        }
        let suggested = suggestCopyName(sourceName);
        let answer = window.prompt(
            "Copy \"" + sourceName + "\" as…",
            suggested
        );
        if (answer === null) {
            if (typeof options.onCancel === "function") {
                options.onCancel();
            }
            return;
        }
        let newName = String(answer).trim();
        if (!newName) {
            alert("Name is required");
            return;
        }
        if (newName === sourceName) {
            alert("Choose a different name for the copy");
            return;
        }
        let existing = options.existingRows || [];
        for (let i = 0; i < existing.length; i++) {
            let n = existing[i] && existing[i].name;
            if (n && String(n).toLowerCase() === newName.toLowerCase()) {
                alert("An item named \"" + newName + "\" already exists");
                return;
            }
        }

        let getUrl = apiBase() + "metadata/" + encodeURIComponent(key) + "/"
            + encodeURIComponent(sourceName);
        let postUrl = apiBase() + "metadata/" + encodeURIComponent(key) + "/";

        function fail(title, xhr, status, error) {
            if (typeof showAjaxError === "function") {
                showAjaxError(title, xhr, status, error);
            } else {
                alert(title + ": " + ((xhr && xhr.responseText) || status || error || "failed"));
            }
        }

        function doSave(doc) {
            if (!doc || typeof doc !== "object") {
                alert("Could not load source metadata");
                return;
            }
            doc.name = newName;
            // Hop JSON sometimes nests name only at top level; keep description/path
            let body = JSON.stringify(doc);
            if (typeof global.jQuery !== "undefined" || typeof global.$ !== "undefined") {
                let $ = global.jQuery || global.$;
                $.ajax({
                    url: postUrl,
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: body,
                    dataType: "text",
                    success: function () {
                        if (typeof options.onSuccess === "function") {
                            options.onSuccess(newName);
                        }
                    },
                    error: function (xhr, status, error) {
                        fail("Failed to save copy \"" + newName + "\"", xhr, status, error);
                    }
                });
                return;
            }
            fetch(postUrl, {
                method: "POST",
                headers: {"Content-Type": "application/json; charset=utf-8", Accept: "text/plain"},
                body: body
            }).then(function (r) {
                if (!r.ok) {
                    return r.text().then(function (t) {
                        throw new Error(t || ("HTTP " + r.status));
                    });
                }
                if (typeof options.onSuccess === "function") {
                    options.onSuccess(newName);
                }
            }).catch(function (err) {
                alert("Failed to save copy: " + (err && err.message ? err.message : err));
            });
        }

        if (typeof global.jQuery !== "undefined" || typeof global.$ !== "undefined") {
            let $jq = global.jQuery || global.$;
            $jq.ajax({
                url: getUrl,
                type: "GET",
                dataType: "json",
                success: function (doc) {
                    doSave(doc);
                },
                error: function (xhr, status, error) {
                    fail("Failed to load \"" + sourceName + "\"", xhr, status, error);
                }
            });
            return;
        }
        fetch(getUrl)
            .then(function (r) {
                if (!r.ok) {
                    throw new Error("HTTP " + r.status);
                }
                return r.json();
            })
            .then(doSave)
            .catch(function (err) {
                alert("Failed to load source: " + (err && err.message ? err.message : err));
            });
    }

    global.HMetadataList = {
        escapeHtmlText: escapeHtmlText,
        escapeHtmlAttribute: escapeHtmlAttribute,
        normalizeVirtualPath: normalizeVirtualPath,
        filterMetadataRows: filterMetadataRows,
        groupMetadataRows: groupMetadataRows,
        fetchMetadataSummary: fetchMetadataSummary,
        buildMetadataListTableHtml: buildMetadataListTableHtml,
        buildMetadataListPanelHtml: buildMetadataListPanelHtml,
        bindMetadataListHandlers: bindMetadataListHandlers,
        bindMetadataListFilter: bindMetadataListFilter,
        rowsByNameMap: rowsByNameMap,
        staticImage: staticImage,
        suggestCopyName: suggestCopyName,
        copyMetadataAs: copyMetadataAs,
        showErrorDialog: showErrorDialog,
        showAjaxError: showAjaxError
    };

    // Global helpers for pages that load this script (home + edit)
    global.showErrorDialog = showErrorDialog;
    global.showAjaxError = showAjaxError;
})(typeof window !== "undefined" ? window : this);
