(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var rows = [];

  function emptyRow() {
    return { name: "", value: "" };
  }

  function renderTable($el) {
    var html =
      '<p class="admin-muted">System variables are inherited by every presentation and connector as <code>${NAME}</code>. ' +
      "Presentation parameters override these values. Use the lock button to encrypt secrets with Hop " +
      "<code>Encr.encryptPasswordIfNotUsingVariables()</code> (values that already use <code>${…}</code> or <code>#{…}</code> are left unchanged).</p>";
    html +=
      '<div class="admin-toolbar">' +
      '<button type="button" class="admin-btn" id="btnVarAdd">Add variable</button> ' +
      '<button type="button" class="admin-btn admin-btn-primary" id="btnVarSave">Save</button>' +
      "</div>";
    html +=
      '<table class="admin-table admin-vars-table"><thead><tr><th style="width:28%">Name</th><th>Value</th><th style="width:7rem">Actions</th></tr></thead><tbody id="varsBody">';

    if (!rows.length) {
      rows = [emptyRow()];
    }
    rows.forEach(function (row, idx) {
      html += '<tr data-idx="' + idx + '">';
      html +=
        '<td><input type="text" class="var-name" value="' +
        esc(row.name || "") +
        '" placeholder="MY_VARIABLE" autocomplete="off"></td>';
      html +=
        '<td><div class="admin-var-value-wrap">' +
        '<input type="text" class="var-value" value="' +
        esc(row.value || "") +
        '" placeholder="value or ${ENV} or #{resolver:path:key}" autocomplete="off">' +
        '<button type="button" class="admin-icon-btn btn-var-encrypt" title="Encrypt value (Hop Encrypted)">' +
        "🔒</button></div></td>";
      html +=
        '<td><button type="button" class="admin-icon-btn btn-var-delete" title="Delete">' +
        '<img src="' +
        (typeof uiIconUrl === "function"
          ? uiIconUrl("delete.svg")
          : "/hopper/api/static/images/delete.svg") +
        '" data-ui-icon="delete.svg" alt="Delete" width="16" height="16">' +
        "</button></td>";
      html += "</tr>";
    });
    html += "</tbody></table>";
    $el.html(html);

    $el.find("#btnVarAdd").on("click", function () {
      collectRows($el);
      rows.push(emptyRow());
      renderTable($el);
    });
    $el.find("#btnVarSave").on("click", function () {
      collectRows($el);
      var payload = {
        variables: rows.filter(function (r) {
          return r.name && String(r.name).trim();
        }),
      };
      A.api("admin/variables", { method: "PUT", body: payload }).then(function (r) {
        if (!r.ok) {
          A.banner("error", "Save failed (" + r.status + "): " + (r.text || ""));
          return;
        }
        A.banner("ok", "Saved " + (r.data && r.data.count != null ? r.data.count : "") + " system variable(s).");
        A.showPage("variables");
      });
    });
    $el.find(".btn-var-delete").on("click", function () {
      var idx = parseInt($(this).closest("tr").attr("data-idx"), 10);
      collectRows($el);
      rows.splice(idx, 1);
      if (!rows.length) {
        rows = [emptyRow()];
      }
      renderTable($el);
    });
    $el.find(".btn-var-encrypt").on("click", function () {
      var $tr = $(this).closest("tr");
      var $input = $tr.find(".var-value");
      var value = $input.val() || "";
      A.api("admin/variables/encrypt", {
        method: "POST",
        body: { value: value },
      }).then(function (r) {
        if (!r.ok) {
          A.banner("error", "Encrypt failed (" + r.status + ")");
          return;
        }
        $input.val((r.data && r.data.value) || value);
        A.banner("ok", "Value encrypted (or left unchanged if it uses variables/resolvers).");
      });
    });
  }

  function collectRows($el) {
    var next = [];
    $el.find("#varsBody tr").each(function () {
      next.push({
        name: $(this).find(".var-name").val() || "",
        value: $(this).find(".var-value").val() || "",
      });
    });
    rows = next;
  }

  A.register("variables", function ($el) {
    rows = [];
    return A.api("admin/variables").then(function (r) {
      if (!r.ok) {
        $el.html(
          '<div class="admin-banner error" style="display:block">Failed to load system variables (' +
            r.status +
            ")</div>"
        );
        return;
      }
      rows = (r.data && r.data.variables) || [];
      if (!rows.length) {
        rows = [emptyRow()];
      }
      renderTable($el);
    });
  });
})();
