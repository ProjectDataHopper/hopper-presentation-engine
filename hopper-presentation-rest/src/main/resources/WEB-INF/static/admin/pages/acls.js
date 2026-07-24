(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var actionCodes = [];

  A.register("acls", function ($el) {
    return Promise.all([A.api("security/acls"), A.api("admin/roles/actions")]).then(
      function (results) {
        if (!results[0].ok) {
          $el.html(
            '<div class="admin-banner error" style="display:block">Failed to load ACLs (' +
              results[0].status +
              "). Ensure you have security.admin.</div>"
          );
          return;
        }
        actionCodes = ((results[1].ok && results[1].data.actions) || []).map(function (a) {
          return a.code;
        });
        renderList($el, results[0].data || []);
      }
    );
  });

  function renderList($el, items) {
    if (!Array.isArray(items)) {
      items = [];
    }
    var html =
      '<p class="admin-muted">Resource ACLs grant or deny actions for a role or user on a named presentation, connector, connection, or theme. DENY wins over ALLOW; without an ACL, role grants apply (unless default-deny is on).</p>';
    html +=
      '<div class="admin-toolbar"><button type="button" class="admin-btn admin-btn-primary" id="btnNewAcl">New ACL</button></div>';
    html +=
      '<table class="admin-table"><thead><tr><th>Name</th><th>Resource</th><th>Entries</th><th></th></tr></thead><tbody>';
    items.forEach(function (item) {
      html +=
        "<tr><td><code class=\"admin-code\">" +
        esc(item.name) +
        "</code></td><td>" +
        esc(item.resourceType) +
        " / " +
        esc(item.resourceName) +
        "</td><td>" +
        esc(item.entryCount) +
        '</td><td><button type="button" class="admin-btn" data-edit="' +
        esc(item.name) +
        '">Edit</button> ' +
        '<button type="button" class="admin-btn admin-btn-danger" data-del="' +
        esc(item.name) +
        '">Delete</button></td></tr>';
    });
    html += "</tbody></table>";
    html += '<div id="aclEditor"></div>';
    $el.html(html);

    $("#btnNewAcl").on("click", function () {
      showEditor($("#aclEditor"), {
        name: "",
        resourceType: "PRESENTATION",
        resourceName: "",
        entries: [],
      });
    });
    $el.find("[data-edit]").on("click", function () {
      var name = $(this).data("edit");
      A.api("security/acls/" + encodeURIComponent(name)).then(function (r) {
        if (r.ok) showEditor($("#aclEditor"), r.data);
        else A.banner("error", "Load failed");
      });
    });
    $el.find("[data-del]").on("click", function () {
      var name = $(this).data("del");
      if (!confirm("Delete ACL " + name + "?")) return;
      A.api("security/acls/" + encodeURIComponent(name), { method: "DELETE" }).then(
        function (r) {
          if (r.ok) {
            A.banner("ok", "Deleted " + name);
            A.showPage("acls");
          } else {
            A.banner("error", esc(r.text));
          }
        }
      );
    });
  }

  function showEditor($box, acl) {
    var entries = (acl.entries || []).slice();
    var html = '<div class="admin-section-title">ACL editor</div>';
    html += A.formRow(
      "Resource type",
      '<select id="aclType">' +
        ["PRESENTATION", "CONNECTOR", "CONNECTION", "THEME"]
          .map(function (t) {
            return (
              '<option value="' +
              t +
              '"' +
              (acl.resourceType === t ? " selected" : "") +
              ">" +
              t +
              "</option>"
            );
          })
          .join("") +
        "</select>"
    );
    html += A.formRow(
      "Resource name",
      '<input type="text" id="aclResName" value="' +
        esc(acl.resourceName || "") +
        '" placeholder="HR Salary">'
    );
    html +=
      '<div class="admin-section-title">Entries</div><div id="aclEntries"></div>';
    html +=
      '<div class="admin-toolbar"><button type="button" class="admin-btn" id="btnAddEntry">Add entry</button> ' +
      '<button type="button" class="admin-btn admin-btn-primary" id="btnSaveAcl">Save ACL</button></div>';
    $box.html(html);

    function paintEntries() {
      var h = "";
      if (!entries.length) {
        h = '<p class="admin-muted">No entries yet.</p>';
      } else {
        entries.forEach(function (e, idx) {
          h +=
            '<div class="admin-field-group" data-idx="' +
            idx +
            '"><div class="admin-split">';
          h +=
            '<div class="admin-form-row"><label>Principal type</label><select class="e-ptype"><option value="ROLE"' +
            (e.principalType === "USER" ? "" : " selected") +
            '>ROLE</option><option value="USER"' +
            (e.principalType === "USER" ? " selected" : "") +
            ">USER</option></select></div>";
          h +=
            '<div class="admin-form-row"><label>Principal</label><input type="text" class="e-principal" value="' +
            esc(e.principal || "") +
            '" placeholder="VIEWER or user@example.com"></div>';
          h +=
            '<div class="admin-form-row"><label>Effect</label><select class="e-effect"><option value="ALLOW"' +
            (e.effect === "DENY" ? "" : " selected") +
            '>ALLOW</option><option value="DENY"' +
            (e.effect === "DENY" ? " selected" : "") +
            ">DENY</option></select></div>";
          h +=
            '<div class="admin-form-row"><label>Actions (comma or * / family.*)</label><input type="text" class="e-actions" value="' +
            esc((e.actions || []).join(", ")) +
            '" placeholder="presentation.render, presentation.*"></div>';
          h +=
            '</div><button type="button" class="admin-btn admin-btn-danger e-remove" data-idx="' +
            idx +
            '">Remove entry</button></div>';
        });
      }
      $("#aclEntries").html(h);
      $("#aclEntries .e-remove").on("click", function () {
        collectFromDom();
        entries.splice(parseInt($(this).data("idx"), 10), 1);
        paintEntries();
      });
    }

    function collectFromDom() {
      var next = [];
      $("#aclEntries .admin-field-group").each(function () {
        var $g = $(this);
        var actions = ($g.find(".e-actions").val() || "")
          .split(/[,\s]+/)
          .map(function (s) {
            return s.trim();
          })
          .filter(Boolean);
        next.push({
          principalType: $g.find(".e-ptype").val(),
          principal: $g.find(".e-principal").val(),
          effect: $g.find(".e-effect").val(),
          actions: actions,
        });
      });
      entries = next;
    }

    paintEntries();

    $("#btnAddEntry").on("click", function () {
      collectFromDom();
      entries.push({
        principalType: "ROLE",
        principal: "VIEWER",
        effect: "ALLOW",
        actions: ["presentation.render"],
      });
      paintEntries();
    });

    $("#btnSaveAcl").on("click", function () {
      collectFromDom();
      var body = {
        resourceType: $("#aclType").val(),
        resourceName: $("#aclResName").val(),
        entries: entries,
      };
      if (acl.name) {
        body.name = acl.name;
      }
      A.api("security/acls", { method: "POST", body: body }).then(function (r) {
        if (r.ok) {
          A.banner("ok", "Saved ACL " + (r.data || body.resourceName));
          A.showPage("acls");
        } else {
          A.banner("error", esc(r.text));
        }
      });
    });
  }
})();
