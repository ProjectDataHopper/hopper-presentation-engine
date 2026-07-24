/**
 * Host the presentation-editor catalog UIs (connectors, DB connections, themes)
 * inside the admin SPA by mounting #editSidePanel / #editArea and calling
 * hopper-presentation.js entry points.
 */
(function (global) {
  "use strict";

  var SCRIPT_BASE = "/hopper/api/static/";
  var loading = null;

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var existing = document.querySelector('script[src="' + src + '"]');
      if (existing) {
        resolve();
        return;
      }
      var s = document.createElement("script");
      s.src = src;
      s.async = false;
      s.onload = function () {
        resolve();
      };
      s.onerror = function () {
        reject(new Error("Failed to load " + src));
      };
      document.head.appendChild(s);
    });
  }

  function ensureScripts() {
    if (typeof global.editConnectorsList === "function") {
      return Promise.resolve();
    }
    if (loading) {
      return loading;
    }
    // Presentation editor form sizing / connector studio expect hopperMode === 'edit'
    if (typeof global.hopperMode === "undefined") {
      global.hopperMode = "edit";
    }
    loading = loadScript(SCRIPT_BASE + "hopper-metadata-list.js")
      .then(function () {
        return loadScript(SCRIPT_BASE + "hopper-presentation.js");
      })
      .then(function () {
        return loadScript(SCRIPT_BASE + "hopper-chain-edit.js");
      })
      .catch(function (e) {
        loading = null;
        throw e;
      });
    return loading;
  }

  function mountShell($el) {
    $el.html(
      '<div id="editSidePanel" class="sidePanel property-side-panel admin-metadata-panel">' +
        '<div class="property-editor-shell">' +
        '<div class="property-form-column">' +
        '<div id="editArea" class="property-form-area"></div>' +
        "</div>" +
        '<div class="property-preview-column" id="propertyPreviewColumn" hidden>' +
        '<div class="property-preview-header">Preview</div>' +
        '<div class="property-preview-frame" id="componentPreviewFrame">' +
        '<img id="componentPreviewImg" class="property-preview-img" alt="Preview">' +
        '<p class="property-preview-empty" id="componentPreviewEmpty">No preview</p>' +
        "</div>" +
        '<p class="property-preview-meta" id="componentPreviewMeta"></p>' +
        "</div>" +
        "</div>" +
        "</div>"
    );
  }

  /**
   * @param {JQuery} $el admin content root
   * @param {"connectors"|"database"|"themes"} kind
   */
  function open(kind, $el) {
    document.body.classList.add("admin-metadata-host");
    document.body.dataset.adminMetadataKind = kind;
    mountShell($el);
    return ensureScripts().then(function () {
      if (typeof global.openMetadataAdmin === "function") {
        global.openMetadataAdmin(kind);
      } else if (kind === "database" && typeof global.editDatabaseConnectionsList === "function") {
        global.editDatabaseConnectionsList();
      } else if (kind === "themes" && typeof global.editThemesList === "function") {
        global.editThemesList();
      } else if (typeof global.editConnectorsList === "function") {
        global.editConnectorsList();
      } else {
        throw new Error("Presentation metadata UI failed to load");
      }
    });
  }

  function clearHostClass() {
    document.body.classList.remove("admin-metadata-host");
    document.body.classList.remove("property-panel-open");
    document.body.classList.remove("chain-editor-open");
    delete document.body.dataset.adminMetadataKind;
  }

  global.HAdminMetadataHost = {
    open: open,
    clearHostClass: clearHostClass,
  };
})(window);
