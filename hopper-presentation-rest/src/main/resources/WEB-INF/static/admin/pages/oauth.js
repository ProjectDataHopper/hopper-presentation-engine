(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var presets = [];
  var selectedId = "google";

  A.register("oauth", function ($el) {
    return Promise.all([A.api("admin/oauth/presets"), A.api("admin/oauth/status")]).then(
      function (results) {
        if (!results[0].ok) {
          $el.html(
            '<div class="admin-banner error" style="display:block">Cannot load presets (' +
              results[0].status +
              ")</div>"
          );
          return;
        }
        presets = results[0].data.presets || [];
        var status = results[1].ok ? results[1].data : {};
        if (status.inferredProvider && status.inferredProvider !== "none") {
          selectedId = status.inferredProvider;
        }
        render($el, status);
      }
    );
  });

  function render($el, status) {
    var html = "";
    html +=
      '<p class="admin-muted">Configure browser OIDC (PKCE) and JWT resource-server settings using a provider wizard. Secrets should be environment variable references only.</p>';

    html += '<div class="admin-section-title">Current status</div>';
    html += '<div class="admin-grid">';
    html += statusCard("Mode", (status.authEnabled ? "on / " : "off / ") + (status.authMode || "—"));
    html += statusCard("Provider", status.inferredProvider || "none");
    html += statusCard("Issuer", status.issuerUri || "—");
    html += statusCard("Client ID", status.clientId || "—");
    html += statusCard(
      "Secret",
      status.clientSecretConfigured
        ? status.clientSecretRef || "configured"
        : "not set"
    );
    html += statusCard("OIDC ready", status.oidcBrowserConfigured ? "yes" : "no");
    html += "</div>";

    html += '<div class="admin-section-title">Provider wizard</div>';
    html += '<div class="admin-form-row"><label>Provider</label>';
    html += '<select id="oauthProvider">';
    presets.forEach(function (p) {
      html +=
        '<option value="' +
        esc(p.id) +
        '"' +
        (p.id === selectedId ? " selected" : "") +
        ">" +
        esc(p.label) +
        "</option>";
    });
    html += "</select>";
    html +=
      '<div class="hint" id="oauthProviderDesc"></div></div>';
    html += '<div id="oauthFields"></div>';
    html += '<div class="admin-toolbar">';
    html +=
      '<button type="button" class="admin-btn" id="btnOAuthTest">Test connection</button>';
    html +=
      '<button type="button" class="admin-btn" id="btnOAuthPreview">Preview settings</button>';
    html +=
      '<button type="button" class="admin-btn admin-btn-primary" id="btnOAuthApply">Apply</button>';
    html +=
      '<label style="font-size:0.85rem;display:flex;gap:0.35rem;align-items:center">' +
      '<input type="checkbox" id="oauthRequireTest" checked> Require discovery test before apply</label>';
    html += "</div>";
    html += '<div id="oauthResult"></div>';

    $el.html(html);
    $("#oauthProvider").on("change", function () {
      selectedId = $(this).val();
      fillFields();
    });
    fillFields();
    $("#btnOAuthTest").on("click", function () {
      runTest($el);
    });
    $("#btnOAuthPreview").on("click", function () {
      runPreview($el);
    });
    $("#btnOAuthApply").on("click", function () {
      runApply($el);
    });
  }

  function statusCard(label, value) {
    return (
      '<div class="admin-card"><div class="label">' +
      esc(label) +
      '</div><div class="value">' +
      esc(value) +
      "</div></div>"
    );
  }

  function currentPreset() {
    for (var i = 0; i < presets.length; i++) {
      if (presets[i].id === selectedId) return presets[i];
    }
    return presets[0];
  }

  function fillFields() {
    var p = currentPreset();
    if (!p) return;
    $("#oauthProviderDesc").text(p.description || "");
    var html = "";
    (p.fields || []).forEach(function (f) {
      var val = f.defaultValue || "";
      var type = f.type === "secret-ref" ? "text" : "text";
      html +=
        '<div class="admin-form-row"><label>' +
        esc(f.label) +
        (f.required ? " *" : "") +
        "</label>";
      html +=
        '<input type="' +
        type +
        '" data-field="' +
        esc(f.name) +
        '" value="' +
        esc(val) +
        '" placeholder="' +
        esc(f.placeholder || "") +
        '">';
      if (f.description) {
        html += '<div class="hint">' + esc(f.description) + "</div>";
      }
      html += "</div>";
    });
    $("#oauthFields").html(html);
  }

  function collectInputs() {
    var inputs = {};
    $("#oauthFields [data-field]").each(function () {
      inputs[$(this).data("field")] = $(this).val();
    });
    return inputs;
  }

  function runTest($el) {
    A.banner(null);
    $("#oauthResult").html('<p class="admin-muted">Testing discovery…</p>');
    A.api("admin/oauth/test", {
      method: "POST",
      body: { provider: selectedId, inputs: collectInputs() },
    }).then(function (r) {
      showResult(r.data, "test");
    });
  }

  function runPreview($el) {
    A.banner(null);
    A.api("admin/oauth/preview", {
      method: "POST",
      body: { provider: selectedId, inputs: collectInputs() },
    }).then(function (r) {
      if (!r.ok) {
        A.banner("error", esc((r.data && r.data.error) || r.text));
        return;
      }
      showResult(r.data, "preview");
    });
  }

  function runApply($el) {
    A.banner(null);
    var requireTest = $("#oauthRequireTest").is(":checked");
    $("#oauthResult").html('<p class="admin-muted">Applying…</p>');
    A.api("admin/oauth/apply", {
      method: "POST",
      body: {
        provider: selectedId,
        inputs: collectInputs(),
        requireTest: requireTest,
      },
    }).then(function (r) {
      if (r.ok && r.data && r.data.success) {
        A.banner("ok", "OAuth configuration applied. Sign out and back in to use the new IdP session.");
      } else {
        A.banner(
          "error",
          esc((r.data && (r.data.error || (r.data.errors && r.data.errors.join("; ")))) || r.text)
        );
      }
      showResult(r.data, "apply");
    });
  }

  function showResult(data, kind) {
    if (!data) {
      $("#oauthResult").html("");
      return;
    }
    var html = '<div class="admin-section-title">Result (' + esc(kind) + ")</div>";
    if (data.success === false) {
      html +=
        '<div class="admin-banner error" style="display:block">' +
        esc(data.error || "Failed") +
        "</div>";
    } else if (data.success === true && kind === "test") {
      html +=
        '<div class="admin-banner ok" style="display:block">Discovery OK' +
        (data.issuer ? " — issuer " + esc(data.issuer) : "") +
        "</div>";
    }
    html += '<pre class="admin-pre">' + esc(JSON.stringify(data, null, 2)) + "</pre>";
    $("#oauthResult").html(html);
  }
})();
