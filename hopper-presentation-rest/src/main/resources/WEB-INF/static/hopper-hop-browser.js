/**
 * Hop project browser for remote Hop Server mode.
 *
 * Opens a modal to pick pipelines/workflows/folders; fills a form field path.
 * Also refreshes pipeline run-configuration datalists.
 *
 * API (via presentation proxy): GET /hopper/api/hop/status|listProject|describePipeline
 */
(function (global) {
  "use strict";

  var API = "/hopper/api/hop";
  var state = {
    path: "",
    type: "pipeline",
    fieldId: null,
    pluginId: null,
    status: null,
  };

  function apiUrl(path, query) {
    var q = [];
    if (query) {
      Object.keys(query).forEach(function (k) {
        if (query[k] !== undefined && query[k] !== null) {
          q.push(encodeURIComponent(k) + "=" + encodeURIComponent(query[k]));
        }
      });
    }
    return API + path + (q.length ? "?" + q.join("&") : "");
  }

  function fetchJson(url) {
    return fetch(url, { credentials: "same-origin", headers: { Accept: "application/json" } }).then(
      function (r) {
        return r.json().then(function (body) {
          if (!r.ok) {
            var msg = (body && body.message) || r.statusText || "Request failed";
            throw new Error(msg);
          }
          return body;
        });
      }
    );
  }

  function ensureModal() {
    var el = document.getElementById("hopProjectBrowserModal");
    if (el) {
      return el;
    }
    el = document.createElement("div");
    el.id = "hopProjectBrowserModal";
    el.className = "hop-browser-modal";
    el.hidden = true;
    el.innerHTML =
      '<div class="hop-browser-backdrop" data-hop-close="1"></div>' +
      '<div class="hop-browser-dialog" role="dialog" aria-modal="true" aria-labelledby="hopBrowserTitle">' +
      '  <header class="hop-browser-header">' +
      '    <h2 id="hopBrowserTitle">Hop project</h2>' +
      '    <button type="button" class="hop-browser-close" data-hop-close="1" title="Close">×</button>' +
      "  </header>" +
      '  <div class="hop-browser-toolbar">' +
      '    <button type="button" id="hopBrowserUp" class="hop-browser-btn" title="Parent folder">↑ Up</button>' +
      '    <span id="hopBrowserPath" class="hop-browser-path"></span>' +
      '    <span id="hopBrowserStatus" class="hop-browser-status"></span>' +
      "  </div>" +
      '  <div id="hopBrowserList" class="hop-browser-list"></div>' +
      '  <footer class="hop-browser-footer">' +
      '    <label class="hop-browser-selected-label">Selected: ' +
      '      <input type="text" id="hopBrowserSelected" class="hop-browser-selected" readonly>' +
      "    </label>" +
      '    <button type="button" id="hopBrowserCancel" class="hop-browser-btn" data-hop-close="1">Cancel</button>' +
      '    <button type="button" id="hopBrowserOk" class="hop-browser-btn hop-browser-btn-primary">Select</button>' +
      "  </footer>" +
      "</div>";
    document.body.appendChild(el);

    el.addEventListener("click", function (ev) {
      if (ev.target && ev.target.getAttribute("data-hop-close") === "1") {
        closeModal();
      }
    });
    el.querySelector("#hopBrowserUp").addEventListener("click", function () {
      goUp();
    });
    el.querySelector("#hopBrowserOk").addEventListener("click", function () {
      applySelection();
    });
    return el;
  }

  function closeModal() {
    var el = document.getElementById("hopProjectBrowserModal");
    if (el) {
      el.hidden = true;
    }
  }

  function openModal() {
    ensureModal().hidden = false;
  }

  function parentPath(path) {
    if (!path) {
      return "";
    }
    var i = path.replace(/\/+$/, "").lastIndexOf("/");
    if (i < 0) {
      return "";
    }
    return path.substring(0, i);
  }

  function goUp() {
    state.path = parentPath(state.path);
    loadList();
  }

  function setStatus(msg, isError) {
    var s = document.getElementById("hopBrowserStatus");
    if (!s) {
      return;
    }
    s.textContent = msg || "";
    s.classList.toggle("hop-browser-status-error", !!isError);
  }

  function loadList() {
    setStatus("Loading…", false);
    var listType = state.type === "folder" ? "all" : state.type === "all" ? "all" : state.type;
    // Always include folders for navigation when browsing files
    var typeParam = state.type === "pipeline" || state.type === "workflow" ? "all" : listType;
    fetchJson(
      apiUrl("/listProject", {
        path: state.path || "",
        type: typeParam,
        depth: "1",
      })
    )
      .then(function (data) {
        var title = document.getElementById("hopBrowserTitle");
        var project =
          data.projectName ||
          (data.projectHome ? data.projectHome.split("/").pop() : "Hop project");
        if (title) {
          title.textContent = "Hop project — " + project;
        }
        var pathEl = document.getElementById("hopBrowserPath");
        if (pathEl) {
          pathEl.textContent = "/" + (state.path || "");
        }
        renderEntries(data.entries || [], state.type);
        setStatus(
          (data.entries ? data.entries.length : 0) + " item(s)",
          false
        );
        // Cache run configs for later
        if (data.runConfigurations) {
          state.lastRunConfigs = data.runConfigurations;
        }
      })
      .catch(function (err) {
        renderEntries([], state.type);
        setStatus(err.message || String(err), true);
      });
  }

  function renderEntries(entries, filterType) {
    var list = document.getElementById("hopBrowserList");
    if (!list) {
      return;
    }
    list.innerHTML = "";
    var filtered = entries.filter(function (e) {
      if (!e || !e.type) {
        return false;
      }
      if (e.type === "folder") {
        return true;
      }
      if (filterType === "pipeline") {
        return e.type === "pipeline";
      }
      if (filterType === "workflow") {
        return e.type === "workflow";
      }
      if (filterType === "folder") {
        return false; // only navigate folders; select current path via Ok empty?
      }
      return e.type === "pipeline" || e.type === "workflow";
    });

    if (!filtered.length) {
      var empty = document.createElement("div");
      empty.className = "hop-browser-empty";
      empty.textContent = "No matching items in this folder.";
      list.appendChild(empty);
      return;
    }

    filtered.forEach(function (e) {
      var row = document.createElement("button");
      row.type = "button";
      row.className = "hop-browser-item hop-browser-item-" + e.type;
      var icon =
        e.type === "folder" ? "📁" : e.type === "workflow" ? "⚙" : "⛓";
      row.innerHTML =
        '<span class="hop-browser-item-icon">' +
        icon +
        '</span><span class="hop-browser-item-name"></span>' +
        '<span class="hop-browser-item-type"></span>';
      row.querySelector(".hop-browser-item-name").textContent = e.name || e.path;
      row.querySelector(".hop-browser-item-type").textContent = e.type;
      row.title = e.path || e.name;
      row.addEventListener("click", function () {
        if (e.type === "folder") {
          state.path = e.path || "";
          document.getElementById("hopBrowserSelected").value = "";
          loadList();
        } else {
          document.getElementById("hopBrowserSelected").value = e.path || e.name;
          list.querySelectorAll(".hop-browser-item-selected").forEach(function (n) {
            n.classList.remove("hop-browser-item-selected");
          });
          row.classList.add("hop-browser-item-selected");
        }
      });
      row.addEventListener("dblclick", function () {
        if (e.type !== "folder") {
          document.getElementById("hopBrowserSelected").value = e.path || e.name;
          applySelection();
        }
      });
      list.appendChild(row);
    });
  }

  function applySelection() {
    var sel = document.getElementById("hopBrowserSelected");
    var value = sel ? sel.value.trim() : "";
    if (!value) {
      // Allow selecting current folder for folder browse type
      if (state.type === "folder") {
        value = state.path || "";
      } else {
        setStatus("Select a file first", true);
        return;
      }
    }
    var input = state.fieldId ? document.getElementById(state.fieldId) : null;
    if (input) {
      input.value = value;
      input.dispatchEvent(new Event("change", { bubbles: true }));
      input.dispatchEvent(new Event("input", { bubbles: true }));
    }
    closeModal();
  }

  /**
   * @param {string} fieldId
   * @param {string} browseType pipeline|workflow|folder|all
   * @param {string} [pluginId]
   */
  function hopperBrowseHopProject(fieldId, browseType, pluginId) {
    state.fieldId = fieldId;
    state.pluginId = pluginId || "";
    state.type = browseType || "pipeline";
    state.path = "";

    // Start path from current field value's parent if any
    var input = fieldId ? document.getElementById(fieldId) : null;
    if (input && input.value) {
      var cur = input.value.trim().replace(/\\/g, "/");
      if (cur.indexOf("/") >= 0) {
        state.path = parentPath(cur);
      }
      var sel = document.getElementById("hopBrowserSelected");
      if (sel && !cur.endsWith("/")) {
        // preselect later after list loads if matches
      }
    }

    openModal();
    fetchJson(apiUrl("/status", null))
      .then(function (st) {
        state.status = st;
        if (!st.remoteConfigured) {
          setStatus(
            st.mode === "remote"
              ? "Hop server URL is not configured."
              : "Hop is in embedded mode — set HOPPER_HOP_MODE=remote to browse a project.",
            true
          );
          renderEntries([], state.type);
          return;
        }
        if (st.healthError) {
          setStatus("Hop Server unreachable: " + st.healthError, true);
        }
        loadList();
      })
      .catch(function (err) {
        setStatus(err.message || String(err), true);
      });
  }

  /**
   * Fill datalist for pipeline/workflow run configurations.
   * @param {string} fieldId
   * @param {string} [pluginId]
   */
  function hopperRefreshHopRunConfigs(fieldId, pluginId) {
    var input = document.getElementById(fieldId);
    var listId = fieldId + "-list";
    var datalist = document.getElementById(listId);
    if (!datalist) {
      return Promise.resolve();
    }
    var kind =
      pluginId && String(pluginId).toLowerCase().indexOf("workflow") >= 0
        ? "workflow"
        : "pipeline";

    return fetchJson(apiUrl("/status", null))
      .then(function (st) {
        if (!st.remoteConfigured) {
          return null;
        }
        return fetchJson(apiUrl("/listProject", { path: "", type: "folder", depth: "1" }));
      })
      .then(function (data) {
        if (!data) {
          return;
        }
        var names =
          (data.runConfigurations && data.runConfigurations[kind]) ||
          (data.metadata &&
            data.metadata[
              kind === "workflow"
                ? "workflow-run-configuration"
                : "pipeline-run-configuration"
            ]) ||
          [];
        datalist.innerHTML = "";
        names.forEach(function (n) {
          var opt = document.createElement("option");
          opt.value = n;
          datalist.appendChild(opt);
        });
        if (input) {
          input.title =
            names.length > 0
              ? "Run configurations: " + names.join(", ")
              : "No run configurations returned";
        }
      })
      .catch(function () {
        /* ignore — field still editable as free text */
      });
  }

  /** Auto-refresh run config datalists when a form is shown. */
  function hopperEnhanceHopFormFields() {
    document.querySelectorAll("input[data-hop-run-config='true']").forEach(function (inp) {
      if (inp.dataset.hopEnhanced === "1") {
        return;
      }
      inp.dataset.hopEnhanced = "1";
      hopperRefreshHopRunConfigs(inp.id, inp.getAttribute("data-hop-plugin") || "");
    });
  }

  // Observe side panel form injections
  if (typeof MutationObserver !== "undefined") {
    var obs = new MutationObserver(function () {
      hopperEnhanceHopFormFields();
    });
    if (document.body) {
      obs.observe(document.body, { childList: true, subtree: true });
    } else {
      document.addEventListener("DOMContentLoaded", function () {
        obs.observe(document.body, { childList: true, subtree: true });
      });
    }
  }

  global.hopperBrowseHopProject = hopperBrowseHopProject;
  global.hopperRefreshHopRunConfigs = hopperRefreshHopRunConfigs;
  global.hopperEnhanceHopFormFields = hopperEnhanceHopFormFields;
})(typeof window !== "undefined" ? window : this);
