(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var dirty = {};

  A.register("settings", function ($el) {
    dirty = {};
    return A.api("admin/settings").then(function (r) {
      if (!r.ok) {
        $el.html(
          '<div class="admin-banner error" style="display:block">Failed to load settings (' +
            r.status +
            ")</div>"
        );
        return;
      }
      var settings = r.data.settings || [];
      var byCat = {};
      settings.forEach(function (s) {
        var c = s.category || "SERVER";
        if (!byCat[c]) byCat[c] = [];
        byCat[c].push(s);
      });
      var cats = r.data.categories || Object.keys(byCat);
      var html =
        '<p class="admin-muted">Effective configuration (defaults + bootstrap file + runtime overrides). Sensitive values are redacted. Read-only keys cannot be changed here.</p>';
      html +=
        '<div class="admin-toolbar"><button type="button" class="admin-btn admin-btn-primary" id="btnSettingsApply" disabled>Apply changes</button>';
      html +=
        '<span class="admin-muted" id="settingsDirtyHint"></span></div>';
      html +=
        '<div class="admin-form-row"><label>Filter</label><input type="text" id="settingsFilter" placeholder="auth.mode, audit…"></div>';

      cats.forEach(function (cat) {
        var list = byCat[cat] || [];
        if (!list.length) return;
        html +=
          '<div class="admin-field-group" data-cat="' +
          esc(cat) +
          '"><h3>' +
          esc(cat) +
          "</h3>";
        html +=
          '<table class="admin-table"><thead><tr><th>Key</th><th>Value</th><th>Source</th></tr></thead><tbody>';
        list.forEach(function (s) {
          var ro = s.readOnly;
          var sens = s.sensitive;
          html +=
            '<tr data-key="' +
            esc(s.key) +
            '"><td><code class="admin-code">' +
            esc(s.key) +
            "</code>";
          if (s.restartRequired) {
            html += ' <span class="admin-badge warn">restart</span>';
          }
          if (ro) {
            html += ' <span class="admin-badge system">read-only</span>';
          }
          html +=
            '<div class="hint" style="font-weight:normal;margin-top:0.15rem">' +
            esc(s.description || "") +
            "</div></td><td>";
          if (ro || (sens && s.value === "***")) {
            html +=
              '<code class="admin-code">' +
              esc(s.value) +
              "</code>" +
              (sens ? ' <span class="admin-muted">(masked)</span>' : "");
          } else if (s.type === "BOOLEAN") {
            html +=
              '<select data-setting="' +
              esc(s.key) +
              '"><option value="true"' +
              (String(s.value).toLowerCase() === "true" ? " selected" : "") +
              '>true</option><option value="false"' +
              (String(s.value).toLowerCase() !== "true" ? " selected" : "") +
              ">false</option></select>";
          } else {
            html +=
              '<input type="text" data-setting="' +
              esc(s.key) +
              '" value="' +
              esc(s.value) +
              '" style="max-width:100%;width:100%">';
          }
          html +=
            "</td><td><span class=\"admin-badge\">" +
            esc(s.source || "") +
            "</span></td></tr>";
        });
        html += "</tbody></table></div>";
      });

      $el.html(html);

      $el.on("input change", "[data-setting]", function () {
        var key = $(this).data("setting");
        dirty[key] = $(this).val();
        $("#btnSettingsApply").prop("disabled", Object.keys(dirty).length === 0);
        $("#settingsDirtyHint").text(
          Object.keys(dirty).length + " change(s) pending"
        );
      });

      $("#settingsFilter").on("input", function () {
        var q = $(this).val().toLowerCase();
        $el.find("tr[data-key]").each(function () {
          var k = String($(this).data("key")).toLowerCase();
          $(this).toggle(!q || k.indexOf(q) >= 0);
        });
      });

      $("#btnSettingsApply").on("click", function () {
        var patch = dirty;
        if (!Object.keys(patch).length) return;
        A.api("admin/settings/apply", {
          method: "POST",
          body: { settings: patch },
        }).then(function (res) {
          if (res.ok && res.data && res.data.success) {
            A.banner(
              "ok",
              "Applied " +
                (res.data.applied || []).length +
                " setting(s)." +
                (res.data.restartRequired && res.data.restartRequired.length
                  ? " Restart recommended: " + res.data.restartRequired.join(", ")
                  : "")
            );
            dirty = {};
            A.showPage("settings");
          } else {
            A.banner(
              "error",
              esc(
                (res.data && res.data.errors && res.data.errors.join("; ")) ||
                  res.text
              )
            );
          }
        });
      });
    });
  });
})();
