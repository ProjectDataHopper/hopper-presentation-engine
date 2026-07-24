(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;

  A.register("usage", function ($el) {
    return load($el);
  });

  function age(ms) {
    if (ms == null) return "";
    var s = Math.floor(ms / 1000);
    if (s < 60) return s + "s";
    return Math.floor(s / 60) + "m " + (s % 60) + "s";
  }

  function load($el) {
    return Promise.all([
      A.api("admin/usage/active"),
      A.api("admin/usage/sessions"),
    ]).then(function (results) {
      var renders = results[0].ok ? results[0].data : { activeRenders: [] };
      var sessions = results[1].ok ? results[1].data : { sessions: [] };
      var list = renders.activeRenders || [];
      var sess = sessions.sessions || [];

      var html =
        '<p class="admin-muted">In-memory snapshot of who is rendering what (single node). Also available as a standalone page.</p>';
      html +=
        '<p><a href="/hopper/api/static/admin-usage.html">Open standalone live usage →</a></p>';

      html +=
        '<div class="admin-section-title">Active renders (' +
        list.length +
        ")</div>";
      if (!list.length) {
        html += '<p class="admin-muted">No active renders.</p>';
      } else {
        html +=
          '<table class="admin-table"><thead><tr><th>User</th><th>Presentation</th><th>Render ID</th><th>Age</th></tr></thead><tbody>';
        list.forEach(function (u) {
          html +=
            "<tr><td>" +
            esc(u.username) +
            "</td><td>" +
            esc(u.presentationName) +
            "</td><td><code class=\"admin-code\">" +
            esc((u.renderId || "").slice(0, 8)) +
            "…</code></td><td>" +
            esc(age(u.ageMs)) +
            "</td></tr>";
        });
        html += "</tbody></table>";
      }

      html +=
        '<div class="admin-section-title">Browser sessions (' +
        sess.length +
        ")</div>";
      if (!sess.length) {
        html += '<p class="admin-muted">No browser sessions.</p>';
      } else {
        html +=
          '<table class="admin-table"><thead><tr><th>User</th><th>Roles</th><th>Method</th><th>Last access</th></tr></thead><tbody>';
        sess.forEach(function (s) {
          html +=
            "<tr><td>" +
            esc(s.username) +
            "</td><td>" +
            esc((s.roles || []).join(", ")) +
            "</td><td>" +
            esc(s.authMethod) +
            "</td><td>" +
            esc(s.lastAccessAt) +
            "</td></tr>";
        });
        html += "</tbody></table>";
      }

      $el.html(html);
    });
  }
})();
