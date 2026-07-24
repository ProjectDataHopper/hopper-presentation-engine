(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;

  A.register("server", function ($el) {
    return load($el);
  });

  function load($el) {
    return A.api("admin/server/status").then(function (r) {
      if (!r.ok) {
        $el.html(
          '<div class="admin-banner error" style="display:block">Failed to load server status (' +
            r.status +
            ")</div>"
        );
        return;
      }
      render($el, r.data);
    });
  }

  function render($el, data) {
    var rc = data.renderCache || {};
    var hk = data.housekeeping || {};
    var settings = data.settings || {};
    var entries = data.renderEntries || [];

    var html =
      '<p class="admin-muted">Render cache TTL, size limits, and background housekeeping. Change limits under Settings (server.render.* / server.session.*); they apply immediately.</p>';

    html += '<div class="admin-section-title">Configuration</div>';
    html += '<div class="admin-grid">';
    html += card("Render TTL (min)", settings["server.render.ttl-minutes"]);
    html += card("Max renderings", settings["server.render.max-entries"]);
    html += card("Sweep interval (s)", settings["server.session.sweep-interval-seconds"]);
    html += card("Housekeeping", hk.started ? "running" : "stopped");
    html += "</div>";

    html += '<div class="admin-section-title">Render cache</div>';
    html += '<div class="admin-grid">';
    html += card("Size", rc.size);
    html += card("Hits", rc.hits);
    html += card("Misses", rc.misses);
    html += card("Evicted (TTL)", rc.evictedTtl);
    html += card("Evicted (LRU)", rc.evictedLru);
    html += card("Sessions", data.sessions && data.sessions.count);
    html += "</div>";

    html += '<div class="admin-toolbar">';
    html +=
      '<button type="button" class="admin-btn" id="btnHkRun">Run housekeeping now</button>';
    html +=
      '<button type="button" class="admin-btn admin-btn-danger" id="btnEvictAll">Evict all renders</button>';
    html +=
      '<button type="button" class="admin-btn" id="btnOpenSettings">Edit server settings…</button>';
    html += "</div>";

    if (hk.lastRunEpochMs) {
      html +=
        '<p class="admin-muted">Last sweep: ' +
        esc(new Date(hk.lastRunEpochMs).toISOString()) +
        " (sessions purged " +
        esc(hk.lastSessionsPurged) +
        ", renders " +
        esc(hk.lastRendersPurged) +
        ", runs " +
        esc(hk.runCount) +
        ")</p>";
    }

    html +=
      '<div class="admin-section-title">Cached renderings (' +
      entries.length +
      ")</div>";
    if (!entries.length) {
      html += '<p class="admin-muted">Cache is empty.</p>';
    } else {
      html +=
        '<table class="admin-table"><thead><tr><th>Presentation</th><th>ID</th><th>Idle</th><th>Age</th><th></th></tr></thead><tbody>';
      entries.forEach(function (e) {
        html +=
          "<tr><td>" +
          esc(e.presentationName) +
          '</td><td><code class="admin-code">' +
          esc((e.id || "").slice(0, 10)) +
          "…</code></td><td>" +
          esc(e.idleSeconds) +
          "s</td><td>" +
          esc(e.ageSeconds) +
          's</td><td><button type="button" class="admin-btn admin-btn-danger" data-evict="' +
          esc(e.id) +
          '">Evict</button></td></tr>';
      });
      html += "</tbody></table>";
    }

    html +=
      '<div class="admin-section-title">Audit settings</div>' +
      '<p class="admin-muted">Audit enablement, redaction, and queue options are under <strong>Settings → AUDIT</strong>. Sink plugins remain metadata type <code class="admin-code">audit-sink</code>.</p>' +
      '<button type="button" class="admin-btn" id="btnAuditSettings">Open audit settings</button>';

    $el.html(html);

    $("#btnHkRun").on("click", function () {
      A.api("admin/server/housekeeping/run", { method: "POST" }).then(function (r) {
        if (r.ok) {
          A.banner("ok", "Housekeeping finished");
          A.showPage("server");
        } else {
          A.banner("error", esc(r.text));
        }
      });
    });
    $("#btnEvictAll").on("click", function () {
      if (!confirm("Evict all cached renderings?")) return;
      A.api("admin/server/renders", { method: "DELETE" }).then(function (r) {
        if (r.ok) {
          A.banner("ok", "Evicted " + (r.data && r.data.evicted) + " rendering(s)");
          A.showPage("server");
        } else {
          A.banner("error", esc(r.text));
        }
      });
    });
    $el.find("[data-evict]").on("click", function () {
      var id = $(this).data("evict");
      A.api("admin/server/renders/" + encodeURIComponent(id), { method: "DELETE" }).then(
        function (r) {
          if (r.ok) {
            A.banner("ok", "Evicted rendering");
            A.showPage("server");
          } else {
            A.banner("error", esc(r.text));
          }
        }
      );
    });
    $("#btnOpenSettings, #btnAuditSettings").on("click", function () {
      A.showPage("settings");
    });
  }

  function card(label, value) {
    return (
      '<div class="admin-card"><div class="label">' +
      esc(label) +
      '</div><div class="value">' +
      esc(value != null ? value : "—") +
      "</div></div>"
    );
  }
})();
