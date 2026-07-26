(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;

  A.register("pictorial-series", function ($el) {
    var html = '' +
      '<div class="admin-card">' +
      '  <div class="admin-header-actions" style="float:right;">' +
      '    <button type="button" class="admin-btn admin-btn-primary" id="btnNewSeries">+ New Pictorial Series</button>' +
      '  </div>' +
      '  <h2>Pictorial Series Metadata</h2>' +
      '  <p class="admin-muted">Manage re-usable image series and skeuomorphic gauge layer definitions for <code>HPictorialChartComponent</code>.</p>' +
      '  <div id="seriesListContainer" style="margin-top:16px;"><p class="admin-muted">Loading pictorial series catalog…</p></div>' +
      '</div>' +

      '<div id="seriesEditorCard" class="admin-card hidden" style="margin-top:20px;">' +
      '  <h3 id="seriesEditorTitle">Edit Pictorial Series</h3>' +
      '  <form id="formSeriesEditor" class="admin-form">' +
      '    <div class="admin-form-row">' +
      '      <label>Series Name</label>' +
      '      <input type="text" id="srName" required placeholder="e.g. beer-glass-gauge">' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Description</label>' +
      '      <input type="text" id="srDescription" placeholder="Human readable description">' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Render Mode</label>' +
      '      <select id="srRenderMode">' +
      '        <option value="STEP_IMAGES" selected>STEP_IMAGES (Discrete Multi-Image Mapping)</option>' +
      '        <option value="CLIPPED_LAYERS">CLIPPED_LAYERS (Pixel-Stable Empty + Fill Pair)</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Step Quantization</label>' +
      '      <select id="srStepQuantization">' +
      '        <option value="NEAREST" selected>NEAREST</option>' +
      '        <option value="FLOOR">FLOOR</option>' +
      '        <option value="CEIL">CEIL</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Clip Direction</label>' +
      '      <select id="srClipDirection">' +
      '        <option value="BOTTOM_TO_TOP" selected>BOTTOM_TO_TOP</option>' +
      '        <option value="LEFT_TO_RIGHT">LEFT_TO_RIGHT</option>' +
      '        <option value="TOP_TO_BOTTOM">TOP_TO_BOTTOM</option>' +
      '        <option value="RIGHT_TO_LEFT">RIGHT_TO_LEFT</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-form-row sr-clipped-only hidden">' +
      '      <label>Background Container Image Path</label>' +
      '      <input type="text" id="srBgImage" placeholder="${HOPPER_METADATA_PATH}/assets/..."> ' +
      '    </div>' +
      '    <div class="admin-form-row sr-clipped-only hidden">' +
      '      <label>Fill Layer Image Path</label>' +
      '      <input type="text" id="srFillImage" placeholder="${HOPPER_METADATA_PATH}/assets/..."> ' +
      '    </div>' +

      '    <div class="sr-step-only">' +
      '      <h4 style="margin-top:16px;">Step Image Mapping (Percentage → Image Path/URL)</h4>' +
      '      <table class="admin-table" id="tableStepImages">' +
      '        <thead><tr><th>Percentage (%)</th><th>Image Path / URL</th><th>Preview</th><th>Actions</th></tr></thead>' +
      '        <tbody></tbody>' +
      '      </table>' +
      '      <button type="button" class="admin-btn" id="btnAddStepRow" style="margin-top:8px;">+ Add Step Image Row</button>' +
      '    </div>' +

      '    <div class="admin-toolbar" style="margin-top:20px;">' +
      '      <button type="submit" class="admin-btn admin-btn-primary">Save Series Metadata</button>' +
      '      <button type="button" class="admin-btn" id="btnAiPopulateSeries" style="background:var(--accent-color, #27ae60); color:#fff;">⚡ AI Helper: Auto-Populate Series</button>' +
      '      <button type="button" class="admin-btn" id="btnCancelSeries">Cancel</button>' +
      '    </div>' +
      '  </form>' +
      '</div>' +

      '<div id="aiPopulateModal" class="admin-card hidden" style="margin-top:20px; border:2px solid var(--accent-color, #27ae60);">' +
      '  <h3>AI Helper: Auto-Populate Pictorial Series</h3>' +
      '  <p class="admin-muted">Always builds a <strong>0% → 100%</strong> fill ladder. Optionally adds ' +
      '<strong>one</strong> under-target image (any value &lt; 0%) and <strong>one</strong> over-target image (any value &gt; 100%). ' +
      'Prompts may use <code>{percentage}</code>.</p>' +
      '  <div class="admin-form-row admin-form-row-wide">' +
      '    <label>1. Prompt for fill levels 0%–100%</label>' +
      '    <textarea id="aiHelperPrompt" class="admin-prompt-textarea" rows="4" spellcheck="true">' +
      'A clear beer glass filled to {percentage}% with golden beer and foam, side view, plain background' +
      '</textarea>' +
      '  </div>' +
      '  <div class="admin-form-row">' +
      '    <label><input type="checkbox" id="aiHelperIncludeNegative" checked> Also generate <strong>one</strong> under-target image (values &lt; 0%)</label>' +
      '  </div>' +
      '  <div class="admin-form-row admin-form-row-wide">' +
      '    <label>2. Prompt for that under-target image (e.g. broken glass)</label>' +
      '    <textarea id="aiHelperNegativePrompt" class="admin-prompt-textarea" rows="4" spellcheck="true">' +
      'A shattered broken beer glass on a wooden bar, spilled beer, shards, failure, side view, plain background' +
      '</textarea>' +
      '  </div>' +
      '  <div class="admin-form-row">' +
      '    <label><input type="checkbox" id="aiHelperIncludeOverflow" checked> Also generate <strong>one</strong> over-target image (values &gt; 100%)</label>' +
      '  </div>' +
      '  <div class="admin-form-row admin-form-row-wide">' +
      '    <label>3. Prompt for that over-target image (e.g. overflowing glass)</label>' +
      '    <textarea id="aiHelperOverflowPrompt" class="admin-prompt-textarea" rows="4" spellcheck="true">' +
      'A beer glass overflowing with foam and beer, large puddle around the base, overfilled, side view, plain background' +
      '</textarea>' +
      '  </div>' +
      '  <div class="admin-form-row">' +
      '    <label>Fill ladder: interval between 0% and 100%</label>' +
      '    Every <input type="number" id="aiHelperStepSize" value="10" min="1" max="100" step="1" style="width:70px;"> % ' +
      '    <span class="admin-muted">or</span> ' +
      '    <input type="number" id="aiHelperStepsInZeroToHundred" value="11" min="2" max="101" step="1" style="width:70px;"> images from 0 to 100 (incl. ends)' +
      '    <br><small class="admin-muted">Example: every <strong>10%</strong> → 0,10,20…100 (11 images). Every <strong>25%</strong> → 0,25,50,75,100 (5 images).</small>' +
      '  </div>' +
      '  <div class="admin-form-row">' +
      '    <label>Aspect ratio (must match the AI service)</label>' +
      '    <select id="aiHelperAspectPreset"></select>' +
      '    <br><small class="admin-muted" id="aiHelperAspectHint">Grok/DALL·E only support fixed shapes — free tall sizes like 400×1200 cause white bands and are disabled.</small>' +
      '  </div>' +
      '  <div class="admin-form-row">' +
      '    <label>Resolution</label>' +
      '    <select id="aiHelperResolutionTier"></select>' +
      '    <br><small class="admin-muted">Output is cover-cropped to this size (no white letterbox). Subject fills the frame.</small>' +
      '  </div>' +
      '  <p id="aiHelperStepPreview" class="admin-muted" style="margin:8px 0;"></p>' +
      '  <div class="admin-toolbar">' +
      '    <button type="button" class="admin-btn admin-btn-primary" id="btnRunAiPopulate">Generate &amp; Populate</button>' +
      '    <button type="button" class="admin-btn" id="btnCloseAiModal">Close</button>' +
      '  </div>' +
      '  <div id="aiPopulateStatus" class="admin-banner hidden" style="margin-top:10px;"></div>' +
      '</div>' +

      '<div class="admin-card" style="margin-top:24px;">' +
      '  <h2>AI Server Configuration</h2>' +
      '  <p class="admin-muted">Configure AI provider settings stored in server properties. API keys are safely encrypted on disk via Hop <code>Encr</code>.</p>' +
      '  <form id="formAiSettings" class="admin-form">' +
      '    <div class="admin-form-row">' +
      '      <label>AI Provider Service</label>' +
      '      <select id="cfgProviderType">' +
      '        <option value="BUILTIN">BUILTIN (Local Generative Renderer - Offline Baseline)</option>' +
      '        <option value="GOOGLE_IMAGEN">GOOGLE_IMAGEN (Google Vertex AI / Gemini Imagen 3)</option>' +
      '        <option value="XAI_GROK">XAI_GROK (xAI Grok Image API)</option>' +
      '        <option value="OPENAI_DALLE">OPENAI_DALLE (OpenAI DALL-E 3 API)</option>' +
      '      </select>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>API Key</label>' +
      '      <input type="text" id="cfgApiKey" autocomplete="off" placeholder="${GOOGLE_AI_API_KEY} or #{gsm:my-ai-key} or paste secret">' +
      '      <small id="cfgKeyHint" class="admin-muted">Plain secrets are obfuscated on save. Prefer variables: ' +
      '<code>${ENV_NAME}</code> or Hop resolver <code>#{resolver-name:secret-id}</code> (e.g. Google Secret Manager).</small>' +
      '      <br><small id="cfgKeyStatus" class="admin-muted"></small>' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Model Name</label>' +
      '      <input type="text" id="cfgModelName" placeholder="imagen-3.0-generate-002, grok-2-image, dall-e-3">' +
      '    </div>' +
      '    <div class="admin-form-row">' +
      '      <label>Endpoint URL (Optional Custom Override)</label>' +
      '      <input type="text" id="cfgEndpointUrl" placeholder="Default provider REST endpoint">' +
      '    </div>' +
      '    <div class="admin-toolbar">' +
      '      <button type="submit" class="admin-btn admin-btn-primary">Save AI Settings</button>' +
      '      <button type="button" class="admin-btn" id="btnTestAiConnection">Test Connection</button>' +
      '    </div>' +
      '    <div id="aiTestResult" class="admin-banner hidden" style="margin-top:10px;"></div>' +
      '  </form>' +
      '</div>';

    $el.html(html);

    loadSeriesCatalog();
    loadAiSettings();

    $("#srRenderMode").on("change", function () {
      var m = $(this).val();
      if (m === "CLIPPED_LAYERS") {
        $(".sr-clipped-only").removeClass("hidden");
        $(".sr-step-only").addClass("hidden");
      } else {
        $(".sr-clipped-only").addClass("hidden");
        $(".sr-step-only").removeClass("hidden");
      }
    });

    $("#btnNewSeries").on("click", function () {
      openSeriesEditor({
        name: "",
        description: "",
        renderMode: "STEP_IMAGES",
        stepQuantization: "NEAREST",
        clipDirection: "BOTTOM_TO_TOP",
        imageMap: { "0": "", "50": "", "100": "" }
      });
    });

    $("#btnAddStepRow").on("click", function () {
      addStepRow("", "");
    });

    $("#btnCancelSeries").on("click", function () {
      $("#seriesEditorCard").addClass("hidden");
    });

    $("#formSeriesEditor").on("submit", function (e) {
      e.preventDefault();
      var name = $("#srName").val().trim();
      var renderMode = $("#srRenderMode").val();
      var imageMap = {};

      if (renderMode === "STEP_IMAGES") {
        $("#tableStepImages tbody tr").each(function () {
          var pct = $(this).find(".step-pct").val().trim();
          var path = $(this).find(".step-path").val().trim();
          if (pct !== "") {
            imageMap[pct] = path;
          }
        });
      }

      var payload = {
        name: name,
        description: $("#srDescription").val().trim(),
        renderMode: renderMode,
        stepQuantization: $("#srStepQuantization").val(),
        clipDirection: $("#srClipDirection").val(),
        backgroundImage: $("#srBgImage").val().trim(),
        fillImage: $("#srFillImage").val().trim(),
        imageMap: imageMap,
        prompt: $("#aiHelperPrompt").val().trim(),
        negativePrompt: $("#aiHelperNegativePrompt").val().trim(),
        overflowPrompt: $("#aiHelperOverflowPrompt").val().trim(),
        stepMin: 0,
        stepMax: 100,
        stepSize: parseIntOr("#aiHelperStepSize", 10)
      };

      A.api("metadata/pictorial-series", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      }).then(function (res) {
        if (res.ok) {
          $("#seriesEditorCard").addClass("hidden");
          loadSeriesCatalog();
        } else {
          alert("Failed to save pictorial series: " + res.status);
        }
      });
    });

    function parseIntOr(id, fallback) {
      var v = parseInt($(id).val(), 10);
      return isNaN(v) ? fallback : v;
    }

    var sizeOptionsCache = null; // { providerType, aspectPresets: [...] }

    /**
     * Client-side catalog (mirrors HImageSizeCatalog) so dropdowns always fill even if the
     * size-options API is unavailable; server still enforces provider limits on generate.
     */
    var FALLBACK_SIZE_CATALOG = {
      BUILTIN: [
        { id: "SQUARE_1_1", ratio: "1:1", label: "Square (1:1)",
          tiers: { SMALL: { width: 512, height: 512, label: "Small (512×512)" },
            MEDIUM: { width: 768, height: 768, label: "Medium (768×768)" },
            LARGE: { width: 1024, height: 1024, label: "Large (1024×1024)" } } },
        { id: "PORTRAIT_3_4", ratio: "3:4", label: "Portrait 3:4 (recommended for glass)",
          tiers: { SMALL: { width: 384, height: 512, label: "Small (384×512)" },
            MEDIUM: { width: 576, height: 768, label: "Medium (576×768)" },
            LARGE: { width: 768, height: 1024, label: "Large (768×1024)" } } },
        { id: "PORTRAIT_2_3", ratio: "2:3", label: "Portrait 2:3",
          tiers: { SMALL: { width: 341, height: 512, label: "Small (341×512)" },
            MEDIUM: { width: 512, height: 768, label: "Medium (512×768)" },
            LARGE: { width: 682, height: 1024, label: "Large (682×1024)" } } },
        { id: "PORTRAIT_9_16", ratio: "9:16", label: "Portrait 9:16 (tall)",
          tiers: { SMALL: { width: 288, height: 512, label: "Small (288×512)" },
            MEDIUM: { width: 576, height: 1024, label: "Medium (576×1024)" },
            LARGE: { width: 1024, height: 1792, label: "Large (1024×1792)" } } },
        { id: "LANDSCAPE_16_9", ratio: "16:9", label: "Landscape 16:9",
          tiers: { SMALL: { width: 512, height: 288, label: "Small (512×288)" },
            MEDIUM: { width: 1024, height: 576, label: "Medium (1024×576)" },
            LARGE: { width: 1792, height: 1024, label: "Large (1792×1024)" } } }
      ],
      XAI_GROK: [
        { id: "SQUARE_1_1", ratio: "1:1", label: "Square (1:1) — Grok",
          tiers: { SMALL: { width: 512, height: 512, label: "Small (512×512)" },
            MEDIUM: { width: 768, height: 768, label: "Medium (768×768)" },
            LARGE: { width: 1024, height: 1024, label: "Large (1024×1024)" } } }
      ],
      OPENAI_DALLE: [
        { id: "SQUARE_1_1", ratio: "1:1", label: "Square (1:1)",
          tiers: { SMALL: { width: 512, height: 512, label: "Small (512×512)" },
            MEDIUM: { width: 768, height: 768, label: "Medium (768×768)" },
            LARGE: { width: 1024, height: 1024, label: "Large (1024×1024)" } } },
        { id: "PORTRAIT_9_16", ratio: "9:16", label: "Portrait 9:16 (tall)",
          tiers: { SMALL: { width: 288, height: 512, label: "Small (288×512)" },
            MEDIUM: { width: 576, height: 1024, label: "Medium (576×1024)" },
            LARGE: { width: 1024, height: 1792, label: "Large (1024×1792)" } } },
        { id: "LANDSCAPE_16_9", ratio: "16:9", label: "Landscape 16:9",
          tiers: { SMALL: { width: 512, height: 288, label: "Small (512×288)" },
            MEDIUM: { width: 1024, height: 576, label: "Medium (1024×576)" },
            LARGE: { width: 1792, height: 1024, label: "Large (1792×1024)" } } }
      ],
      GOOGLE_IMAGEN: null // filled same as BUILTIN below
    };
    FALLBACK_SIZE_CATALOG.GOOGLE_IMAGEN = FALLBACK_SIZE_CATALOG.BUILTIN.filter(function (p) {
      return p.id !== "PORTRAIT_2_3";
    });

    function countFillLadder(step) {
      if (step < 1) {
        step = 1;
      }
      var n = 0;
      for (var p = 0; p <= 100; p += step) {
        n++;
      }
      if ((100 % step) !== 0) {
        n++;
      }
      return n;
    }

    function currentSizeLabel() {
      var presetId = $("#aiHelperAspectPreset").val() || "PORTRAIT_3_4";
      var tierId = $("#aiHelperResolutionTier").val() || "MEDIUM";
      if (!sizeOptionsCache || !sizeOptionsCache.aspectPresets) {
        return presetId + " / " + tierId;
      }
      var preset = null;
      sizeOptionsCache.aspectPresets.forEach(function (p) {
        if (p.id === presetId) {
          preset = p;
        }
      });
      if (!preset || !preset.tiers || !preset.tiers[tierId]) {
        return presetId + " / " + tierId;
      }
      var t = preset.tiers[tierId];
      return (preset.ratio || presetId) + " · " + t.width + "×" + t.height + "px";
    }

    function populateTierForAspect() {
      var presetId = $("#aiHelperAspectPreset").val();
      var $tier = $("#aiHelperResolutionTier").empty();
      if (!sizeOptionsCache || !sizeOptionsCache.aspectPresets) {
        return;
      }
      var preset = null;
      sizeOptionsCache.aspectPresets.forEach(function (p) {
        if (p.id === presetId) {
          preset = p;
        }
      });
      if (!preset || !preset.tiers) {
        return;
      }
      ["SMALL", "MEDIUM", "LARGE"].forEach(function (tid) {
        var t = preset.tiers[tid];
        if (t) {
          $tier.append(
            $("<option></option>").attr("value", tid).text(t.label || tid)
          );
        }
      });
      if (!$tier.val()) {
        $tier.val("MEDIUM");
      }
    }

    function applySizeOptionsPayload(data) {
      if (!data) {
        return;
      }
      sizeOptionsCache = data;
      var $asp = $("#aiHelperAspectPreset");
      if (!$asp.length) {
        return;
      }
      var prev = $asp.val();
      $asp.empty();
      var presets = data.aspectPresets || [];
      if (!presets.length) {
        $asp.append($("<option></option>").attr("value", "SQUARE_1_1").text("Square (1:1)"));
        presets = [{ id: "SQUARE_1_1", label: "Square (1:1)",
          tiers: { MEDIUM: { width: 768, height: 768, label: "Medium (768×768)" } } }];
        sizeOptionsCache = { providerType: data.providerType || "BUILTIN", aspectPresets: presets };
      } else {
        presets.forEach(function (p) {
          $asp.append(
            $("<option></option>").attr("value", p.id).text(p.label || p.id)
          );
        });
      }
      var prefer =
        (prev && presets.some(function (p) { return p.id === prev; }) && prev) ||
        (presets.some(function (p) { return p.id === "PORTRAIT_3_4"; }) && "PORTRAIT_3_4") ||
        (presets[0] && presets[0].id);
      if (prefer) {
        $asp.val(prefer);
      }
      populateTierForAspect();
      if (data.note) {
        $("#aiHelperAspectHint").text(data.note + " (provider: " + (data.providerType || "?") + ")");
      } else if (data.providerType) {
        $("#aiHelperAspectHint").text(
          "Options for " + data.providerType + ". Cover-cropped; no white letterbox bands."
        );
      }
      updateStepPreview();
    }

    function fallbackSizeOptions(providerType) {
      var key = providerType || "BUILTIN";
      var presets = FALLBACK_SIZE_CATALOG[key] || FALLBACK_SIZE_CATALOG.BUILTIN;
      return {
        ok: true,
        providerType: key,
        aspectPresets: presets,
        note: "Using built-in size catalog for " + key + "."
      };
    }

    function loadSizeOptions() {
      var provider = ($("#cfgProviderType").val() || "").trim() || "BUILTIN";
      var url = "admin/pictorials/size-options?provider=" + encodeURIComponent(provider);
      return A.api(url)
        .then(function (res) {
          var data = res && res.data;
          // Handle accidental double-encoded JSON
          if (typeof data === "string") {
            try {
              data = JSON.parse(data);
            } catch (e) {
              data = null;
            }
          }
          if (res && res.ok && data && (data.aspectPresets || data.ok)) {
            applySizeOptionsPayload(data);
            return;
          }
          applySizeOptionsPayload(fallbackSizeOptions(provider));
        })
        .catch(function () {
          applySizeOptionsPayload(fallbackSizeOptions(provider));
        });
    }

    function updateStepPreview() {
      var step = parseIntOr("#aiHelperStepSize", 10);
      if (step < 1) {
        step = 1;
      }
      var fillN = countFillLadder(step);
      var sample = [];
      for (var p = 0; p <= 100 && sample.length < 8; p += step) {
        sample.push(p);
      }
      if (sample[sample.length - 1] !== 100) {
        sample.push(100);
      }
      var neg = $("#aiHelperIncludeNegative").is(":checked");
      var over = $("#aiHelperIncludeOverflow").is(":checked");
      var total = fillN + (neg ? 1 : 0) + (over ? 1 : 0);
      var extra = [];
      if (neg) {
        extra.push("1 under-target (&lt;0%)");
      }
      if (over) {
        extra.push("1 over-target (&gt;100%)");
      }
      $("#aiHelperStepPreview").html(
        "Will generate <strong>" +
          total +
          "</strong> images: fill ladder " +
          sample.join(", ") +
          (fillN > sample.length ? ", …" : "") +
          " (" +
          fillN +
          ")" +
          (extra.length ? " + " + extra.join(" + ") : "") +
          " · <strong>" +
          currentSizeLabel() +
          "</strong> · cover-crop (no white bands)"
      );
    }

    $("#aiHelperStepsInZeroToHundred").on("input change", function () {
      var count = parseIntOr("#aiHelperStepsInZeroToHundred", 11);
      if (count < 2) {
        count = 2;
      }
      var interval = Math.max(1, Math.round(100 / (count - 1)));
      $("#aiHelperStepSize").val(interval);
      updateStepPreview();
    });

    $("#aiHelperAspectPreset").on("change", function () {
      populateTierForAspect();
      updateStepPreview();
    });
    $("#aiHelperResolutionTier, #aiHelperStepSize, #aiHelperIncludeNegative, #aiHelperIncludeOverflow")
      .on("input change", updateStepPreview);

    $("#btnAiPopulateSeries").on("click", function () {
      $("#aiPopulateModal").removeClass("hidden");
      loadSizeOptions();
    });

    // Refresh aspect/resolution when AI provider changes
    $("#cfgProviderType").on("change", function () {
      loadSizeOptions();
    });

    // Preload so dropdowns are ready when the modal opens
    loadSizeOptions();

    $("#btnCloseAiModal").on("click", function () {
      $("#aiPopulateModal").addClass("hidden");
    });

    $("#btnRunAiPopulate").on("click", function () {
      var name = $("#srName").val().trim();
      if (!name) {
        alert("Please enter a Series Name first.");
        return;
      }

      var $btn = $(this);
      $btn.prop("disabled", true).text("Generating assets…");

      var payload = {
        seriesName: name,
        description: $("#srDescription").val().trim(),
        prompt: $("#aiHelperPrompt").val().trim(),
        negativePrompt: $("#aiHelperNegativePrompt").val().trim(),
        overflowPrompt: $("#aiHelperOverflowPrompt").val().trim(),
        renderMode: $("#srRenderMode").val(),
        stepSize: parseIntOr("#aiHelperStepSize", 10),
        includeNegativeExtreme: $("#aiHelperIncludeNegative").is(":checked"),
        includeOverflowExtreme: $("#aiHelperIncludeOverflow").is(":checked"),
        negativeStepKey: -100,
        overflowStepKey: 200,
        aspectPreset: $("#aiHelperAspectPreset").val() || "PORTRAIT_3_4",
        resolutionTier: $("#aiHelperResolutionTier").val() || "MEDIUM"
      };
      if (payload.stepSize < 1) {
        payload.stepSize = 1;
      }

      $("#aiPopulateStatus").addClass("hidden").empty();

      A.api("admin/pictorials/generate-series", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      }).then(function (res) {
        $btn.prop("disabled", false).text("Generate & Populate");
        var isOk = res.ok && res.data && res.data.ok !== false;
        if (!isOk) {
          var errMsg = (res.data && res.data.error)
            ? res.data.error
            : (res.data && res.data.raw)
              ? res.data.raw
              : ("HTTP " + res.status);
          $("#aiPopulateStatus")
            .removeClass("hidden")
            .html('<div class="admin-banner admin-banner-error" style="background:#f8d7da; color:#721c24; padding:10px; border-radius:4px; border:1px solid #f5c6cb;">' +
                  '<strong>AI Generation Failed:</strong> ' + esc(errMsg) + '</div>')
            .show();
          return;
        }

        $("#aiPopulateModal").addClass("hidden");
        var seriesObj = (res.data && res.data.series) ? res.data.series : res.data;
        if (seriesObj) {
          openSeriesEditor(seriesObj);
        }
        loadSeriesCatalog();
      }).catch(function (err) {
        $btn.prop("disabled", false).text("Generate & Populate");
        $("#aiPopulateStatus")
          .removeClass("hidden")
          .html('<div class="admin-banner admin-banner-error" style="background:#f8d7da; color:#721c24; padding:10px; border-radius:4px; border:1px solid #f5c6cb;">' +
                '<strong>Error:</strong> ' + esc(err) + '</div>')
          .show();
      });
    });

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
          showBanner("AI configuration saved successfully.", "success");
          loadAiSettings();
        } else {
          showBanner("Failed to save AI configuration", "error");
        }
      });
    });

    $("#btnTestAiConnection").on("click", function () {
      var $btn = $(this);
      $btn.prop("disabled", true).text("Testing…");
      var payload = {
        providerType: $("#cfgProviderType").val(),
        rawApiKey: $("#cfgApiKey").val().trim(),
        modelName: $("#cfgModelName").val().trim(),
        endpointUrl: $("#cfgEndpointUrl").val().trim()
      };

      A.api("admin/pictorials/test-connection", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      })
        .then(function (res) {
          $btn.prop("disabled", false).text("Test Connection");
          var data = res.data || {};
          // Handle double-encoded JSON if any
          if (typeof data === "string") {
            try {
              data = JSON.parse(data);
            } catch (e) {
              /* keep */
            }
          }
          if (data && data.ok) {
            showBanner(data.message || "OK", "success");
          } else {
            showBanner(
              (data && data.message) || "Connection / API key check failed",
              "error"
            );
          }
        })
        .catch(function (err) {
          $btn.prop("disabled", false).text("Test Connection");
          showBanner("Test failed: " + err, "error");
        });
    });

    /**
     * Convert stored asset paths to browser preview URLs.
     * ${HOPPER_METADATA_PATH}/assets/foo/bar.png → /hopper/api/assets/foo/bar.png
     */
    function toPreviewUrl(path) {
      if (!path) {
        return "";
      }
      var p = String(path).trim();
      if (p.indexOf("/hopper/api/assets/") === 0 || p.indexOf("http://") === 0 || p.indexOf("https://") === 0) {
        return p;
      }
      var marker = "/assets/";
      var idx = p.indexOf(marker);
      if (idx >= 0) {
        return "/hopper/api/assets/" + p.substring(idx + marker.length);
      }
      // bare relative under assets
      if (p.indexOf("assets/") === 0) {
        return "/hopper/api/" + p;
      }
      return p;
    }

    function loadSeriesCatalog() {
      A.api("metadata/summary/pictorial-series").then(function (res) {
        var $cont = $("#seriesListContainer");
        if (!res.ok) {
          $cont.html('<p class="admin-muted">No pictorial series registered yet.</p>');
          return;
        }

        var list = Array.isArray(res.data) ? res.data : [];
        var ML = window.HMetadataList;
        if (!ML) {
          // Fallback if metadata-list helper failed to load
          $cont.html('<p class="admin-muted">Metadata list helper not loaded.</p>');
          return;
        }

        if (!list.length) {
          $cont.html(
            '<p class="admin-muted">No pictorial series registered yet. Click <strong>+ New Pictorial Series</strong> to create one.</p>'
          );
          return;
        }

        var pictorialIcon =
          (window.API_BASE || "/hopper/api/") +
          "plugins/components/HPictorialChartComponent/image";

        var html = ML.buildMetadataListTableHtml({
          rows: list,
          listId: "pictorialSeriesListTable",
          emptyMessage: "No pictorial series registered yet.",
          iconForRow: function () {
            return { url: pictorialIcon, title: "Pictorial series" };
          },
          actions: [
            {
              id: "edit",
              iconUrl: ML.staticImage("edit.svg"),
              title: "Edit series"
            },
            {
              id: "delete",
              iconUrl: ML.staticImage("delete.svg"),
              title: "Delete series"
            }
          ]
        });
        $cont.html(html);

        ML.bindMetadataListHandlers($cont[0], {
          primary: function (name) {
            openSeriesByName(name);
          },
          edit: function (name) {
            openSeriesByName(name);
          },
          delete: function (name) {
            if (confirm("Delete pictorial series '" + name + "'?")) {
              A.api("metadata/pictorial-series/" + encodeURIComponent(name), {
                method: "DELETE"
              }).then(function () {
                loadSeriesCatalog();
              });
            }
          }
        }, ML.rowsByNameMap ? ML.rowsByNameMap(list) : null);
      });
    }

    function openSeriesByName(name) {
      A.api("metadata/pictorial-series/" + encodeURIComponent(name)).then(function (r) {
        if (r.ok) {
          openSeriesEditor(r.data);
        }
      });
    }

    function openSeriesEditor(item) {
      $("#seriesEditorCard").removeClass("hidden");
      $("#srName").val(item.name || "");
      $("#srDescription").val(item.description || "");
      $("#srRenderMode").val(item.renderMode || "STEP_IMAGES").trigger("change");
      $("#srStepQuantization").val(item.stepQuantization || "NEAREST");
      $("#srClipDirection").val(item.clipDirection || "BOTTOM_TO_TOP");
      $("#srBgImage").val(item.backgroundImage || "");
      $("#srFillImage").val(item.fillImage || "");
      if (item.prompt) {
        $("#aiHelperPrompt").val(item.prompt);
      }
      if (item.negativePrompt) {
        $("#aiHelperNegativePrompt").val(item.negativePrompt);
      }
      if (item.overflowPrompt) {
        $("#aiHelperOverflowPrompt").val(item.overflowPrompt);
      }
      if (item.stepSize != null) {
        $("#aiHelperStepSize").val(item.stepSize);
        var sz = parseInt(item.stepSize, 10);
        if (sz > 0) {
          $("#aiHelperStepsInZeroToHundred").val(Math.round(100 / sz) + 1);
        }
      }
      // Detect extremes from existing map keys
      var mapKeys = Object.keys(item.imageMap || {});
      var hasNeg = mapKeys.some(function (k) { return parseInt(k, 10) < 0; });
      var hasOver = mapKeys.some(function (k) { return parseInt(k, 10) > 100; });
      $("#aiHelperIncludeNegative").prop("checked", hasNeg || mapKeys.length === 0);
      $("#aiHelperIncludeOverflow").prop("checked", hasOver || mapKeys.length === 0);

      var $tbody = $("#tableStepImages tbody").empty();
      var map = item.imageMap || {};
      Object.keys(map)
        .sort(function (a, b) {
          return parseInt(a, 10) - parseInt(b, 10);
        })
        .forEach(function (pct) {
          addStepRow(pct, map[pct]);
        });
    }

    function ensureStepPreviewPopup() {
      var $p = $("#adminStepPreviewPopup");
      if ($p.length) {
        return $p;
      }
      $p = $(
        '<div id="adminStepPreviewPopup" class="admin-step-preview-popup" aria-hidden="true">' +
          '<img src="" alt="Step preview">' +
          '<div class="admin-step-preview-caption"></div>' +
          "</div>"
      );
      $("body").append($p);
      return $p;
    }

    function showStepPreviewPopup($thumb, url, caption) {
      var $p = ensureStepPreviewPopup();
      var $img = $p.find("img");
      $img.attr("src", url);
      $p.find(".admin-step-preview-caption").text(caption || "");
      $p.addClass("is-visible").attr("aria-hidden", "false");

      function position() {
        var rect = $thumb[0].getBoundingClientRect();
        var pw = $p.outerWidth() || 280;
        var ph = $p.outerHeight() || 320;
        var left = rect.right + 12;
        var top = rect.top;
        if (left + pw > window.innerWidth - 8) {
          left = rect.left - pw - 12;
        }
        if (left < 8) {
          left = 8;
        }
        if (top + ph > window.innerHeight - 8) {
          top = Math.max(8, window.innerHeight - ph - 8);
        }
        if (top < 8) {
          top = 8;
        }
        $p.css({ left: left + "px", top: top + "px" });
      }

      // Position after image may load (size changes)
      if ($img[0].complete) {
        position();
      } else {
        $img.one("load", position);
        position();
      }
    }

    function hideStepPreviewPopup() {
      $("#adminStepPreviewPopup")
        .removeClass("is-visible")
        .attr("aria-hidden", "true");
    }

    function addStepRow(pct, path) {
      var $tbody = $("#tableStepImages tbody");
      var preview = toPreviewUrl(path);
      // cache-buster so regenerate replaces the thumb immediately
      var previewSrc = preview
        ? preview + (preview.indexOf("?") >= 0 ? "&" : "?") + "t=" + Date.now()
        : "";
      var imgHtml = previewSrc
        ? '<div class="admin-step-preview-wrap">' +
          '<img class="admin-step-thumb" src="' +
          esc(previewSrc) +
          '" alt="Step ' +
          esc(String(pct)) +
          '%" data-full-src="' +
          esc(preview) +
          '">' +
          '<button type="button" class="admin-btn admin-btn-regen-step btn-regen-step" title="Re-generate this step with AI">↻ AI</button>' +
          "</div>"
        : '<div class="admin-step-preview-wrap">' +
          "<span>—</span>" +
          '<button type="button" class="admin-btn admin-btn-regen-step btn-regen-step" title="Generate this step with AI">↻ AI</button>' +
          "</div>";
      var tr =
        "<tr>" +
        '  <td><input type="number" class="step-pct" value="' +
        esc(pct) +
        '" style="width:70px;">%</td>' +
        '  <td><input type="text" class="step-path" value="' +
        esc(path) +
        '" style="width:100%;"></td>' +
        "  <td class=\"step-preview-cell\">" +
        imgHtml +
        "</td>" +
        '  <td><button type="button" class="admin-btn btn-del-step" style="color:#e74c3c;">✕</button></td>' +
        "</tr>";
      var $tr = $(tr);
      $tr.find(".btn-del-step").on("click", function () {
        hideStepPreviewPopup();
        $tr.remove();
      });
      $tr.find(".admin-step-thumb")
        .on("mouseenter", function () {
          var $img = $(this);
          var url = $img.attr("src") || $img.data("full-src");
          var p = $tr.find(".step-pct").val();
          showStepPreviewPopup($img, url, (p != null ? p : "") + "%");
        })
        .on("mouseleave", function () {
          hideStepPreviewPopup();
        })
        .on("mousemove", function () {
          // keep popup aligned if thumb scrolls under cursor
          var $img = $(this);
          if ($("#adminStepPreviewPopup").hasClass("is-visible")) {
            showStepPreviewPopup(
              $img,
              $img.attr("src") || $img.data("full-src"),
              ($tr.find(".step-pct").val() || "") + "%"
            );
          }
        });
      $tr.find(".btn-regen-step").on("click", function () {
        regenerateStepRow($tr);
      });
      $tbody.append($tr);
    }

    function regenerateStepRow($tr) {
      var name = $("#srName").val().trim();
      if (!name) {
        alert("Please enter a Series Name first (and save if new).");
        return;
      }
      var pctRaw = $tr.find(".step-pct").val();
      var percentage = parseInt(pctRaw, 10);
      if (isNaN(percentage)) {
        alert("Enter a valid percentage for this row before regenerating.");
        return;
      }
      var $btn = $tr.find(".btn-regen-step");
      $btn.prop("disabled", true).text("…");
      hideStepPreviewPopup();

      var payload = {
        seriesName: name,
        percentage: percentage,
        description: $("#srDescription").val().trim(),
        prompt: $("#aiHelperPrompt").val().trim(),
        negativePrompt: $("#aiHelperNegativePrompt").val().trim(),
        overflowPrompt: $("#aiHelperOverflowPrompt").val().trim(),
        aspectPreset: $("#aiHelperAspectPreset").val() || "PORTRAIT_3_4",
        resolutionTier: $("#aiHelperResolutionTier").val() || "MEDIUM"
      };

      A.api("admin/pictorials/generate-step", {
        method: "POST",
        body: JSON.stringify(payload),
        headers: { "Content-Type": "application/json" }
      })
        .then(function (res) {
          $btn.prop("disabled", false).text("↻ AI");
          var ok = res.ok && res.data && res.data.ok !== false;
          if (!ok) {
            var err =
              (res.data && res.data.error) ||
              (res.data && res.data.raw) ||
              "HTTP " + res.status;
            alert("Regenerate failed: " + err);
            return;
          }
          var assetPath = res.data.assetPath;
          if (assetPath) {
            $tr.find(".step-path").val(assetPath);
            var preview = toPreviewUrl(assetPath);
            var bust =
              preview + (preview.indexOf("?") >= 0 ? "&" : "?") + "t=" + Date.now();
            var $cell = $tr.find(".step-preview-cell");
            var $wrap = $cell.find(".admin-step-preview-wrap");
            if (!$wrap.length) {
              $cell.html(
                '<div class="admin-step-preview-wrap">' +
                  '<img class="admin-step-thumb" src="" alt="">' +
                  '<button type="button" class="admin-btn admin-btn-regen-step btn-regen-step" title="Re-generate this step with AI">↻ AI</button>' +
                  "</div>"
              );
              $wrap = $cell.find(".admin-step-preview-wrap");
              $wrap.find(".btn-regen-step").on("click", function () {
                regenerateStepRow($tr);
              });
            }
            var $thumb = $wrap.find(".admin-step-thumb");
            if (!$thumb.length) {
              $wrap.prepend(
                '<img class="admin-step-thumb" src="" alt="Step ' + percentage + '%">'
              );
              $thumb = $wrap.find(".admin-step-thumb");
              $thumb
                .on("mouseenter", function () {
                  showStepPreviewPopup(
                    $(this),
                    $(this).attr("src"),
                    percentage + "%"
                  );
                })
                .on("mouseleave", hideStepPreviewPopup);
            }
            $thumb
              .attr("src", bust)
              .attr("data-full-src", preview)
              .attr("alt", "Step " + percentage + "%");
          }
        })
        .catch(function (err) {
          $btn.prop("disabled", false).text("↻ AI");
          alert("Regenerate error: " + err);
        });
    }

    function loadAiSettings() {
      A.api("admin/pictorials/settings").then(function (res) {
        if (res.ok && res.data && res.data.settings) {
          var s = res.data.settings;
          $("#cfgProviderType").val(s.providerType || "BUILTIN");
          if (s.hasApiKey && s.apiKeyIsVariable) {
            // Show expression so admins can edit GSM/env refs
            $("#cfgApiKey").val(s.maskedApiKey || "");
            $("#cfgKeyStatus").text(
              "Stored as variable expression (not encrypted). Resolved at request time via Hop variables / resolvers."
            );
          } else if (s.hasApiKey) {
            $("#cfgApiKey").val("");
            $("#cfgKeyStatus").text(
              "Obfuscated API key active on server (" +
                (s.maskedApiKey || "Encrypted") +
                "). Leave blank to keep it, or enter a new secret / ${VAR} / #{resolver:secret}."
            );
          } else {
            $("#cfgApiKey").val("");
            $("#cfgKeyStatus").text("No API key set.");
          }
          $("#cfgModelName").val(s.modelName || s.effectiveModelName || "");
          $("#cfgEndpointUrl").val(s.endpointUrl || "");
          // Reload aspect/resolution for the configured provider
          loadSizeOptions();
        }
      });
    }

    function showBanner(msg, type) {
      var $b = $("#aiTestResult");
      $b.removeClass("hidden error success").addClass(type === "error" ? "error" : "success").text(msg).show();
      setTimeout(function () { $b.fadeOut(); }, 6000);
    }
  });
})();
