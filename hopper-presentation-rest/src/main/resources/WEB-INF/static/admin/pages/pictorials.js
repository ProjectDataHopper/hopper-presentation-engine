(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;

  A.register("pictorials", function ($el) {
    var html = '' +
      '<div class="admin-card">' +
      '  <h2>Pictorial Chart AI Generator</h2>' +
      '  <p class="admin-muted">Generate skeuomorphic image sequences (e.g. beer filling 0-100%) or continuous empty/fill layer pairs for <code>HPictorialChartComponent</code>.</p>' +
      '  <form id="formGeneratePictorial" class="admin-form">' +
      '    <div class="admin-form-row">' +
      '      <label>Presentation Name</label>' +
      '      <input type="text" id="picPresName" value="DemoPresentation" required>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Component Name</label>' +
      '      <input type="text" id="picCompName" value="BeerGauge" required>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Generation Prompt</label>' +
      '      <input type="text" id="picPrompt" value="A tall glass of Belgian wheat beer filled to {percentage}%" required>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Render Mode</label>' +
      '      <select id="picRenderMode">' +
      '        <option value="STEP_IMAGES" selected>STEP_IMAGES (Discrete Multi-Image Sequence)</option>' +
      '        <option value="CLIPPED_LAYERS">CLIPPED_LAYERS (Pixel-Stable Empty + Fill Pair)</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Step Size (%)</label>' +
      '      <input type="number" id="picStepSize" value="10" min="5" max="50" step="5">' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Generation Style</label>' +
      '      <select id="picStyle">' +
      '        <option value="STABLE_INPAINT" selected>STABLE_INPAINT (Fixed container geometry & background)</option>' +
      '        <option value="DISCRETE">DISCRETE (Individual frame renders)</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-toolbar">' +
      '      <button type="submit" class="admin-btn admin-btn-primary" id="btnGenPictorial">Generate Pictorial Assets</button>' +
      '    </div>' +
      '  </form>' +
      '</div>' +
      '<div id="pictorialResults" class="admin-card hidden">' +
      '  <h3>Generation Results & Preview</h3>' +
      '  <div id="pictorialGallery" class="admin-gallery" style="display:flex; flex-wrap:wrap; gap:12px; margin-top:12px;"></div>' +
      '</div>' +
      '<div class="admin-card" style="margin-top:20px;">' +
      '  <h2>Asset Library Browser</h2>' +
      '  <div id="assetLibraryContent"><p class="admin-muted">Loading asset library…</p></div>' +
      '</div>' +
      '<div class="admin-card" style="margin-top:20px;">' +
      '  <h2>AI Server Configuration</h2>' +
      '  <p class="admin-muted">Configure external AI providers (Google Imagen, xAI Grok, DALL-E). API keys are safely encrypted on disk via Hop <code>Encr</code>.</p>' +
      '  <form id="formAiSettings" class="admin-form">' +
      '    <div class="admin-form-row">' +
      '      <label>AI Provider Service</label>' +
      '      <select id="cfgProviderType">' +
      '        <option value="BUILTIN">BUILTIN (Local Generative Vector/Raster Renderer - Offline)</option>' +
      '        <option value="GOOGLE_IMAGEN">GOOGLE_IMAGEN (Google Vertex AI / Gemini Imagen 3)</option>' +
      '        <option value="XAI_GROK">XAI_GROK (xAI Grok Image API)</option>' +
      '        <option value="OPENAI_DALLE">OPENAI_DALLE (OpenAI DALL-E 3 API)</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>API Key</label>' +
      '      <input type="text" id="cfgApiKey" autocomplete="off" placeholder="${GOOGLE_AI_API_KEY} or #{gsm:my-ai-key} or paste secret">' +
      '      <small id="cfgKeyHint" class="admin-muted">Plain secrets are obfuscated on save. Prefer <code>${ENV}</code> or <code>#{resolver:secret-id}</code> (Google Secret Manager).</small>' +
      '      <br><small id="cfgKeyStatus" class="admin-muted"></small>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Model Name</label>' +
      '      <input type="text" id="cfgModelName" placeholder="imagen-3.0-generate-002, grok-2-image, dall-e-3">' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Endpoint URL (Optional Custom Override)</label>' +
      '      <input type="text" id="cfgEndpointUrl" placeholder="Default provider API endpoint">' +
      '    </div>' +
      '    <div class="admin-toolbar">' +
      '      <button type="submit" class="admin-btn admin-btn-primary" id="btnSaveAiSettings">Save AI Settings</button>' +
      '      <button type="button" class="admin-btn" id="btnTestAiConnection">Test Connection</button>' +
      '    </div>' +
      '    <div id="aiTestResult" class="admin-banner hidden" style="margin-top:10px;"></div>' +
      '  </form>' +
      '</div>';

    $el.html(html);

    loadAssets();

    $("#formGeneratePictorial").on("submit", function (e) {
      e.preventDefault();
      var $btn = $("#btnGenPictorial");
      $btn.prop("disabled", true).text("Generating assets…");

      var payload = {
        presentationName: $("#picPresName").val().trim(),
        componentName: $("#picCompName").val().trim(),
        prompt: $("#picPrompt").val().trim(),
        renderMode: $("#picRenderMode").val(),
        stepSize: parseInt($("#picStepSize").val(), 10) || 10,
        generationStyle: $("#picStyle").val(),
        width: 200,
        height: 300
      };

      A.api("admin/pictorials/generate", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      }).then(function (res) {
        $btn.prop("disabled", false).text("Generate Pictorial Assets");
        if (!res.ok) {
          alert("Generation failed: " + (res.data ? res.data.error : res.status));
          return;
        }

        $("#pictorialResults").removeClass("hidden");
        var $gallery = $("#pictorialGallery").empty();

        if (payload.renderMode === "STEP_IMAGES") {
          var files = res.data.generatedFiles || {};
          Object.keys(files).forEach(function (pct) {
            var url = "/hopper/api/assets/" + payload.presentationName + "/" + payload.componentName + "/step_" + pct + ".png";
            $gallery.append(
              '<div style="text-align:center; border:1px solid var(--border-color, #ccc); padding:8px; border-radius:6px; background:var(--card-bg, #fff);">' +
              '  <img src="' + url + '" width="80" height="120" style="display:block; margin:0 auto 4px;">' +
              '  <small><strong>' + pct + '%</strong></small>' +
              '</div>'
            );
          });
        } else {
          var bgUrl = "/hopper/api/assets/" + payload.presentationName + "/" + payload.componentName + "/bg_empty.png";
          var fillUrl = "/hopper/api/assets/" + payload.presentationName + "/" + payload.componentName + "/fill_full.png";
          $gallery.append(
            '<div style="text-align:center; border:1px solid var(--border-color, #ccc); padding:8px; border-radius:6px;">' +
            '  <img src="' + bgUrl + '" width="100" height="150" style="display:block; margin:0 auto 4px;">' +
            '  <small>Background Container (Empty)</small>' +
            '</div>' +
            '<div style="text-align:center; border:1px solid var(--border-color, #ccc); padding:8px; border-radius:6px;">' +
            '  <img src="' + fillUrl + '" width="100" height="150" style="display:block; margin:0 auto 4px;">' +
            '  <small>Fill Layer (Full)</small>' +
            '</div>'
          );
        }

        loadAssets();
      }).catch(function (err) {
        $btn.prop("disabled", false).text("Generate Pictorial Assets");
        alert("Error invoking generator: " + err);
      });
    });

    loadAiSettings();

    $("#formAiSettings").on("submit", function (e) {
      e.preventDefault();
      var payload = {
        providerType: $("#cfgProviderType").val(),
        rawApiKey: $("#cfgApiKey").val().trim(),
        modelName: $("#cfgModelName").val().trim(),
        endpointUrl: $("#cfgEndpointUrl").val().trim()
      };

      A.api("admin/pictorials/settings", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      }).then(function (res) {
        if (res.ok) {
          showBanner("AI configuration saved and key encrypted.", "success");
          loadAiSettings();
        } else {
          showBanner("Failed to save AI configuration", "error");
        }
      });
    });

    $("#btnTestAiConnection").on("click", function () {
      var payload = {
        providerType: $("#cfgProviderType").val(),
        rawApiKey: $("#cfgApiKey").val().trim()
      };

      A.api("admin/pictorials/test-connection", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      }).then(function (res) {
        if (res.ok && res.data.ok) {
          showBanner(res.data.message, "success");
        } else {
          showBanner(res.data ? res.data.message : "Connection failed", "error");
        }
      });
    });

    function loadAiSettings() {
      A.api("admin/pictorials/settings").then(function (res) {
        if (res.ok && res.data && res.data.settings) {
          var s = res.data.settings;
          $("#cfgProviderType").val(s.providerType || "BUILTIN");
          if (s.hasApiKey && s.apiKeyIsVariable) {
            $("#cfgApiKey").val(s.maskedApiKey || "");
            $("#cfgKeyStatus").text(
              "Stored as variable expression (not encrypted). Resolved at request time."
            );
          } else if (s.hasApiKey) {
            $("#cfgApiKey").val("");
            $("#cfgKeyStatus").text(
              "Obfuscated key active (" +
                (s.maskedApiKey || "Encrypted") +
                "). Leave blank to keep, or enter ${VAR} / #{resolver:secret}."
            );
          } else {
            $("#cfgApiKey").val("");
            $("#cfgKeyStatus").text("No API key set.");
          }
          $("#cfgModelName").val(s.modelName || s.effectiveModelName || "");
          $("#cfgEndpointUrl").val(s.endpointUrl || "");
        }
      });
    }

    function showBanner(msg, type) {
      var $b = $("#aiTestResult");
      $b.removeClass("hidden error success").addClass(type === "error" ? "error" : "success").text(msg).show();
      setTimeout(function () { $b.fadeOut(); }, 6000);
    }

    function loadAssets() {
      A.api("admin/pictorials/assets").then(function (res) {
        var $lib = $("#assetLibraryContent");
        if (!res.ok) {
          $lib.html('<p class="admin-muted">No assets found or failed to load.</p>');
          return;
        }
        var libs = res.data.assetLibraries || [];
        if (!libs.length) {
          $lib.html('<p class="admin-muted">No generated pictorial asset libraries stored yet.</p>');
          return;
        }

        var h = '<div class="admin-accordion">';
        libs.forEach(function (lib) {
          h += '<h3>Presentation: ' + esc(lib.presentationName) + '</h3><div>';
          (lib.components || []).forEach(function (c) {
            h += '<h4 style="margin-top:8px;">Component: ' + esc(c.componentName || "default") + '</h4>';
            h += '<div style="display:flex; flex-wrap:wrap; gap:8px;">';
            (c.files || []).forEach(function (f) {
              h += '<div style="font-size:11px; padding:4px 8px; background:var(--card-bg, #f5f5f5); border:1px solid #ddd; border-radius:4px;">';
              h += '<a href="' + esc(f.assetUrl) + '" target="_blank">' + esc(f.fileName) + '</a> (' + Math.round(f.sizeBytes / 1024) + ' KB)';
              h += '</div>';
            });
            h += '</div>';
          });
          h += '</div>';
        });
        h += '</div>';
        $lib.html(h);
      });
    }
  });
})();
