(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var allActions = [];

  A.register("roles", function ($el) {
    return Promise.all([A.api("admin/roles"), A.api("admin/roles/actions")]).then(
      function (results) {
        if (!results[0].ok) {
          $el.html(
            '<div class="admin-banner error" style="display:block">Failed to load roles</div>'
          );
          return;
        }
        allActions = (results[1].ok && results[1].data.actions) || [];
        renderList($el, results[0].data.roles || []);
      }
    );
  });

  function renderList($el, roles) {
    var html =
      '<p class="admin-muted">Built-in roles have fixed grants. Custom roles store action codes (and optional inheritance) in metadata.</p>';
    html +=
      '<div class="admin-toolbar"><button type="button" class="admin-btn admin-btn-primary" id="btnNewRole">New custom role</button></div>';
    html +=
      '<table class="admin-table"><thead><tr><th>Name</th><th>Type</th><th>Actions</th><th></th></tr></thead><tbody>';
    roles.forEach(function (r) {
      var actionCount = (r.expandedActions || r.actions || []).length;
      html += "<tr><td><strong>" + esc(r.name) + "</strong>";
      if (r.description) {
        html +=
          '<div class="admin-muted" style="font-weight:normal">' +
          esc(r.description) +
          "</div>";
      }
      html += "</td><td>";
      html += r.system
        ? '<span class="admin-badge system">system</span>'
        : '<span class="admin-badge">custom</span>';
      html += "</td><td>" + actionCount + " grant(s)</td><td>";
      html +=
        '<button type="button" class="admin-btn" data-view="' +
        esc(r.name) +
        '">View</button> ';
      if (r.editable !== false && !r.system) {
        html +=
          '<button type="button" class="admin-btn" data-edit="' +
          esc(r.name) +
          '">Edit</button> ';
        html +=
          '<button type="button" class="admin-btn admin-btn-danger" data-del="' +
          esc(r.name) +
          '">Delete</button>';
      }
      html += "</td></tr>";
    });
    html += "</tbody></table>";
    html += '<div id="roleEditor"></div>';
    $el.html(html);

    $el.find("[data-view]").on("click", function () {
      var name = $(this).data("view");
      A.api("admin/roles/" + encodeURIComponent(name)).then(function (r) {
        if (!r.ok) {
          A.banner("error", "Load failed");
          return;
        }
        showDetail($("#roleEditor"), r.data, false);
      });
    });
    $el.find("[data-edit]").on("click", function () {
      var name = $(this).data("edit");
      A.api("admin/roles/" + encodeURIComponent(name)).then(function (r) {
        if (!r.ok) {
          A.banner("error", "Load failed");
          return;
        }
        showDetail($("#roleEditor"), r.data, true);
      });
    });
    $el.find("[data-del]").on("click", function () {
      var name = $(this).data("del");
      if (!confirm("Delete custom role " + name + "?")) return;
      A.api("admin/roles/" + encodeURIComponent(name), { method: "DELETE" }).then(
        function (r) {
          if (r.ok) {
            A.banner("ok", "Deleted " + name);
            A.showPage("roles");
          } else {
            A.banner("error", esc(r.text));
          }
        }
      );
    });
    $("#btnNewRole").on("click", function () {
      showDetail(
        $("#roleEditor"),
        { name: "", description: "", actions: [], inheritsFrom: [], system: false, editable: true },
        true
      );
    });
  }

  function showDetail($box, role, editable) {
    var selected = {};
    (role.actions || []).forEach(function (a) {
      selected[a] = true;
    });
    var html = '<div class="admin-section-title">' + (editable ? "Edit role" : "Role detail") + "</div>";
    if (!editable) {
      html +=
        "<p><strong>" +
        esc(role.name) +
        "</strong> " +
        (role.system
          ? '<span class="admin-badge system">system</span>'
          : '<span class="admin-badge">custom</span>') +
        "</p>";
      html += "<p>" + esc(role.description || "") + "</p>";
      html += "<p>Inherits: " + esc((role.inheritsFrom || []).join(", ") || "—") + "</p>";
      html += '<div class="admin-check-grid">';
      (role.expandedActions || role.actions || []).forEach(function (a) {
        html += "<div><code class=\"admin-code\">" + esc(a) + "</code></div>";
      });
      html += "</div>";
      $box.html(html);
      return;
    }

    html += A.formRow(
      "Name",
      '<input type="text" id="roleName" value="' +
        esc(role.name || "") +
        '" ' +
        (role.name ? "readonly" : "") +
        ' placeholder="HR_VIEWER">'
    );
    html += A.formRow(
      "Description",
      '<input type="text" id="roleDesc" value="' + esc(role.description || "") + '">'
    );
    html += A.formRow(
      "Inherits from (comma-separated)",
      '<input type="text" id="roleInherits" value="' +
        esc((role.inheritsFrom || []).join(", ")) +
        '" placeholder="VIEWER">'
    );
    html += '<div class="admin-form-row"><label>Actions</label><div class="admin-check-grid" id="roleActions">';
    var families = {};
    allActions.forEach(function (a) {
      var f = a.family || "other";
      if (!families[f]) families[f] = [];
      families[f].push(a);
    });
    Object.keys(families)
      .sort()
      .forEach(function (f) {
        html +=
          '<div style="grid-column:1/-1;font-weight:600;margin-top:0.35rem">' +
          esc(f) +
          "</div>";
        families[f].forEach(function (a) {
          html +=
            '<label><input type="checkbox" value="' +
            esc(a.code) +
            '"' +
            (selected[a.code] ? " checked" : "") +
            "> " +
            esc(a.code) +
            "</label>";
        });
      });
    html += "</div></div>";
    html +=
      '<div class="admin-toolbar"><button type="button" class="admin-btn admin-btn-primary" id="btnSaveRole">Save role</button></div>';
    $box.html(html);
    $("#btnSaveRole").on("click", function () {
      var actions = [];
      $("#roleActions input:checked").each(function () {
        actions.push($(this).val());
      });
      var inherits = ($("#roleInherits").val() || "")
        .split(/[,\s]+/)
        .filter(Boolean);
      var body = {
        name: $("#roleName").val(),
        description: $("#roleDesc").val(),
        actions: actions,
        inheritsFrom: inherits,
      };
      A.api("admin/roles", { method: "POST", body: body }).then(function (r) {
        if (r.ok) {
          A.banner("ok", "Saved role " + body.name);
          A.showPage("roles");
        } else {
          A.banner("error", esc((r.data && r.data.error) || r.text));
        }
      });
    });
  }
})();
