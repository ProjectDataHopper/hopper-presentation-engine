(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var roleNames = [];

  A.register("users", function ($el) {
    return Promise.all([A.api("admin/users"), A.api("admin/roles")]).then(function (results) {
      if (!results[0].ok) {
        $el.html(
          '<div class="admin-banner error" style="display:block">Failed to load users</div>'
        );
        return;
      }
      roleNames = ((results[1].ok && results[1].data.roles) || []).map(function (r) {
        return r.name;
      });
      render($el, results[0].data.users || []);
    });
  });

  function render($el, users) {
    var html =
      '<p class="admin-muted">Hopper-side role assignments (additive to IdP claims). Observed session users without an assignment appear for convenience.</p>';
    html +=
      '<div class="admin-toolbar"><button type="button" class="admin-btn admin-btn-primary" id="btnNewUser">Assign user</button></div>';
    html +=
      '<table class="admin-table"><thead><tr><th>User</th><th>Roles</th><th>Status</th><th></th></tr></thead><tbody>';
    users.forEach(function (u) {
      html += "<tr><td><strong>" + esc(u.email || u.name) + "</strong>";
      if (u.displayName) {
        html +=
          '<div class="admin-muted">' + esc(u.displayName) + "</div>";
      }
      if (u.subject) {
        html +=
          '<div class="admin-muted"><code class="admin-code">' +
          esc(u.subject) +
          "</code></div>";
      }
      html += "</td><td>" + esc((u.roles || []).join(", ") || "—") + "</td><td>";
      if (u.disabled) {
        html += '<span class="admin-badge warn">disabled</span> ';
      }
      if (u.sessionActive) {
        html += '<span class="admin-badge ok">session</span> ';
      }
      if (!u.assignment) {
        html += '<span class="admin-badge system">observed</span>';
      }
      html += "</td><td>";
      html +=
        '<button type="button" class="admin-btn" data-edit="' +
        esc(u.name || u.email) +
        '">Edit</button> ';
      if (u.assignment) {
        html +=
          '<button type="button" class="admin-btn admin-btn-danger" data-del="' +
          esc(u.name) +
          '">Remove</button>';
      }
      html += "</td></tr>";
    });
    html += "</tbody></table>";
    html += '<div id="userEditor"></div>';
    $el.html(html);

    $("#btnNewUser").on("click", function () {
      showEditor($("#userEditor"), {
        name: "",
        email: "",
        roles: ["VIEWER"],
        disabled: false,
        notes: "",
      });
    });
    $el.find("[data-edit]").on("click", function () {
      var name = $(this).data("edit");
      var found = users.filter(function (u) {
        return u.name === name || u.email === name;
      })[0];
      if (found && found.assignment) {
        A.api("admin/users/" + encodeURIComponent(found.name)).then(function (r) {
          if (r.ok) showEditor($("#userEditor"), r.data);
        });
      } else {
        showEditor($("#userEditor"), {
          name: name,
          email: name,
          roles: [],
          disabled: false,
          notes: "",
        });
      }
    });
    $el.find("[data-del]").on("click", function () {
      var name = $(this).data("del");
      if (!confirm("Remove assignment for " + name + "?")) return;
      A.api("admin/users/" + encodeURIComponent(name), { method: "DELETE" }).then(
        function (r) {
          if (r.ok) {
            A.banner("ok", "Removed " + name);
            A.showPage("users");
          } else {
            A.banner("error", esc(r.text));
          }
        }
      );
    });
  }

  function showEditor($box, user) {
    var selected = {};
    (user.roles || []).forEach(function (r) {
      selected[String(r).toUpperCase()] = true;
    });
    var html = '<div class="admin-section-title">User assignment</div>';
    html += A.formRow(
      "Email / name",
      '<input type="text" id="userEmail" value="' +
        esc(user.email || user.name || "") +
        '" placeholder="user@example.com">'
    );
    html += A.formRow(
      "Subject (optional)",
      '<input type="text" id="userSubject" value="' + esc(user.subject || "") + '">'
    );
    html += A.formRow(
      "Display name",
      '<input type="text" id="userDisplay" value="' + esc(user.displayName || "") + '">'
    );
    html +=
      '<div class="admin-form-row"><label>Roles</label><div class="admin-check-grid" id="userRoles">';
    roleNames.forEach(function (rn) {
      html +=
        '<label><input type="checkbox" value="' +
        esc(rn) +
        '"' +
        (selected[rn] ? " checked" : "") +
        "> " +
        esc(rn) +
        "</label>";
    });
    html += "</div></div>";
    html +=
      '<div class="admin-form-row"><label><input type="checkbox" id="userDisabled"' +
      (user.disabled ? " checked" : "") +
      "> Disabled (blocks all data actions)</label></div>";
    html += A.formRow(
      "Notes",
      '<input type="text" id="userNotes" value="' + esc(user.notes || "") + '">'
    );
    html +=
      '<div class="admin-toolbar"><button type="button" class="admin-btn admin-btn-primary" id="btnSaveUser">Save assignment</button></div>';
    $box.html(html);
    $("#btnSaveUser").on("click", function () {
      var roles = [];
      $("#userRoles input:checked").each(function () {
        roles.push($(this).val());
      });
      var body = {
        email: $("#userEmail").val(),
        name: $("#userEmail").val(),
        subject: $("#userSubject").val(),
        displayName: $("#userDisplay").val(),
        roles: roles,
        disabled: $("#userDisabled").is(":checked"),
        notes: $("#userNotes").val(),
      };
      A.api("admin/users", { method: "POST", body: body }).then(function (r) {
        if (r.ok) {
          A.banner("ok", "Saved assignment for " + body.email);
          A.showPage("users");
        } else {
          A.banner("error", esc((r.data && r.data.error) || r.text));
        }
      });
    });
  }
})();
