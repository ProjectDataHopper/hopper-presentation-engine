(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;
  var plugins = [];
  var schemaCache = {};
  var editing = null;

  function pluginById(id) {
    for (var i = 0; i < plugins.length; i++) {
      if (plugins[i].id === id) return plugins[i];
    }
    return null;
  }

  function listView($el) {
    var html =
      '<p class="admin-muted">Hop variable resolvers implement expressions like <code>#{resolverName:path:key}</code>. ' +
      "Resolver types come from plugins on the server classpath (Vault, Azure, Hop Password, …). " +
      "If none appear below, add the corresponding Hop plugin JAR (with jandex index) to the server.</p>";
    html +=
      '<div class="admin-toolbar">' +
      '<button type="button" class="admin-btn admin-btn-primary" id="btnResNew">New resolver</button>' +
      "</div>";

    if (!plugins.length) {
      html +=
        '<div class="admin-banner warn" style="display:block">No variable-resolver plugins discovered on the classpath.</div>';
    }

    html +=
      '<table class="admin-table"><thead><tr><th>Name</th><th>Type</th><th>Description</th><th></th></tr></thead><tbody id="resListBody">';
    html +=
      '<tr><td colspan="4" class="admin-muted">Loading metadata…</td></tr></tbody></table>';
    $el.html(html);

    $el.find("#btnResNew").on("click", function () {
      if (!plugins.length) {
        A.banner("error", "No resolver plugins available on the classpath.");
        return;
      }
      editing = {
        name: "",
        description: "",
        pluginId: plugins[0].id,
        fields: {},
      };
      editView($el);
    });

    // List + load via admin API (backed by IHopMetadataProvider / JsonMetadataProvider)
    A.api("admin/variable-resolvers").then(function (r) {
      var names = (r.ok && r.data && r.data.names) || [];
      var $body = $el.find("#resListBody");
      if (!names.length) {
        $body.html(
          '<tr><td colspan="4" class="admin-muted">No variable resolvers defined yet.</td></tr>'
        );
        return;
      }
      Promise.all(
        names.map(function (n) {
          return A.api("admin/variable-resolvers/" + encodeURIComponent(n)).then(function (rr) {
            return { name: n, data: rr.ok ? rr.data : null };
          });
        })
      ).then(function (items) {
        var rowsHtml = "";
        items.forEach(function (item) {
          var typeName = "";
          var desc = "";
          if (item.data) {
            desc = item.data.description || "";
            var p = pluginById(item.data.pluginId);
            typeName = p ? p.name : item.data.pluginId || "";
          }
          rowsHtml += "<tr>";
          rowsHtml += "<td><strong>" + esc(item.name) + "</strong></td>";
          rowsHtml += "<td>" + esc(typeName) + "</td>";
          rowsHtml += "<td>" + esc(desc) + "</td>";
          rowsHtml +=
            '<td class="admin-row-actions">' +
            '<button type="button" class="admin-btn btn-res-edit" data-name="' +
            esc(item.name) +
            '">Edit</button> ' +
            '<button type="button" class="admin-icon-btn btn-res-del" data-name="' +
            esc(item.name) +
            '" title="Delete"><img src="' +
            (typeof uiIconUrl === "function"
              ? uiIconUrl("delete.svg")
              : "/hopper/api/static/images/delete.svg") +
            '" data-ui-icon="delete.svg" alt="Delete" width="16" height="16"></button>' +
            "</td>";
          rowsHtml += "</tr>";
        });
        $body.html(rowsHtml);
        $body.find(".btn-res-edit").on("click", function () {
          openEdit($el, $(this).data("name"));
        });
        $body.find(".btn-res-del").on("click", function () {
          var name = $(this).data("name");
          if (!confirm("Delete variable resolver '" + name + "'?")) return;
          A.api("admin/variable-resolvers/" + encodeURIComponent(name), {
            method: "DELETE",
          }).then(function (dr) {
            if (!dr.ok) {
              A.banner("error", "Delete failed (" + dr.status + ")");
              return;
            }
            A.banner("ok", "Deleted " + name);
            A.showPage("resolvers");
          });
        });
      });
    });
  }

  function openEdit($el, name) {
    // Load via IHopMetadataProvider (admin API flattens Hop polymorphic JSON for the form)
    A.api("admin/variable-resolvers/" + encodeURIComponent(name)).then(function (r) {
      if (!r.ok || !r.data) {
        A.banner("error", "Could not load resolver " + name);
        return;
      }
      var d = r.data;
      var pluginId = d.pluginId || "";
      if (pluginId && !pluginById(pluginId) && plugins.length) {
        plugins.forEach(function (p) {
          if (p.name === pluginId || p.id === pluginId) pluginId = p.id;
        });
      }
      if (!pluginId && plugins.length) {
        pluginId = plugins[0].id;
      }
      editing = {
        name: d.name || name,
        description: d.description || "",
        pluginId: pluginId,
        fields: d.fields || {},
        existingName: name,
      };
      editView($el);
    });
  }

  function loadSchema(pluginId) {
    if (schemaCache[pluginId]) {
      return Promise.resolve(schemaCache[pluginId]);
    }
    return A.api(
      "admin/variable-resolvers/schema/" + encodeURIComponent(pluginId)
    ).then(function (r) {
      if (r.ok) {
        schemaCache[pluginId] = r.data;
        return r.data;
      }
      return { sections: [], hasPluginWidgets: false };
    });
  }

  function fieldsFromSchema(schema) {
    var fields = [];
    if (!schema || !schema.sections) return fields;
    schema.sections.forEach(function (sec) {
      (sec.fields || []).forEach(function (f) {
        fields.push(f);
      });
    });
    return fields;
  }

  function renderPluginFields(schema, values) {
    var fields = fieldsFromSchema(schema);
    if (!fields.length) {
      return '<p class="admin-muted">This resolver type has no configurable fields.</p>';
    }
    var html = "";
    fields.forEach(function (f) {
      var id = f.fieldName || f.id;
      var label = f.label || id;
      var val = values[id] != null ? values[id] : values[f.id] != null ? values[f.id] : "";
      var type = (f.type || "TEXT").toUpperCase();
      html += '<div class="admin-form-row"><label>' + esc(label) + "</label>";
      if (type === "CHECKBOX") {
        var checked =
          val === true || val === "true" || val === "Y" || val === "yes" || val === "1";
        html +=
          '<input type="checkbox" class="res-field" data-field="' +
          esc(id) +
          '"' +
          (checked ? " checked" : "") +
          ">";
      } else if (type === "MULTI_LINE_TEXT") {
        html +=
          '<textarea class="res-field" data-field="' +
          esc(id) +
          '" rows="' +
          (f.multiLineTextHeight || 3) +
          '">' +
          esc(val) +
          "</textarea>";
      } else if (type === "PASSWORD") {
        html +=
          '<div class="admin-var-value-wrap"><input type="password" class="res-field" data-field="' +
          esc(id) +
          '" value="' +
          esc(val) +
          '" autocomplete="new-password">' +
          '<button type="button" class="admin-icon-btn btn-res-encrypt" data-field="' +
          esc(id) +
          '" title="Encrypt">🔒</button></div>';
      } else {
        html +=
          '<input type="text" class="res-field" data-field="' +
          esc(id) +
          '" value="' +
          esc(val) +
          '">';
      }
      if (f.toolTip) {
        html += '<div class="hint">' + esc(f.toolTip) + "</div>";
      }
      html += "</div>";
    });
    return html;
  }

  function collectFieldValues($el) {
    var fields = {};
    $el.find(".res-field").each(function () {
      var name = $(this).data("field");
      if ($(this).attr("type") === "checkbox") {
        fields[name] = $(this).is(":checked");
      } else {
        fields[name] = $(this).val();
      }
    });
    return fields;
  }

  function editView($el) {
    var html =
      '<div class="admin-toolbar"><button type="button" class="admin-btn" id="btnResBack">← Back</button></div>';
    html += "<h3>" + (editing.existingName ? "Edit" : "New") + " variable resolver</h3>";
    html +=
      '<div class="admin-form-row"><label>Name</label><input type="text" id="resName" value="' +
      esc(editing.name) +
      '"></div>';
    html +=
      '<div class="admin-form-row"><label>Description</label><input type="text" id="resDesc" value="' +
      esc(editing.description) +
      '"></div>';
    html += '<div class="admin-form-row"><label>Resolver type</label><select id="resType">';
    plugins.forEach(function (p) {
      html +=
        '<option value="' +
        esc(p.id) +
        '"' +
        (p.id === editing.pluginId ? " selected" : "") +
        ">" +
        esc(p.name) +
        "</option>";
    });
    html += "</select></div>";
    html += '<div id="resPluginFields"><p class="admin-muted">Loading fields…</p></div>';
    html +=
      '<div class="admin-form-row"><label>Test argument</label>' +
      '<input type="text" id="resTestArg" placeholder="my-secret-id  or  #{gsm:my-secret-id}" value="">' +
      '<div class="hint">Secret <em>id/path only</em> (e.g. <code>edw-db-password</code>). ' +
      "In pipelines/presentations you write <code>#{gsm:edw-db-password}</code> — " +
      "do not paste the full expression as the GSM secret name. " +
      "Optional <code>#{name:path:jsonKey}</code> form is accepted and stripped to <code>path</code>.</div></div>";
    html +=
      '<div class="admin-toolbar">' +
      '<button type="button" class="admin-btn admin-btn-primary" id="btnResSave">Save</button> ' +
      '<button type="button" class="admin-btn" id="btnResTest">Test</button>' +
      "</div>";
    html += '<pre id="resTestResult" class="admin-pre hidden"></pre>';
    $el.html(html);

    function refreshFields() {
      var pluginId = $el.find("#resType").val();
      editing.pluginId = pluginId;
      loadSchema(pluginId).then(function (schema) {
        $el.find("#resPluginFields").html(renderPluginFields(schema, editing.fields || {}));
        $el.find(".btn-res-encrypt").on("click", function () {
          var field = $(this).data("field");
          var $input = $el.find('.res-field[data-field="' + field + '"]');
          A.api("admin/variables/encrypt", {
            method: "POST",
            body: { value: $input.val() || "" },
          }).then(function (r) {
            if (r.ok && r.data) {
              $input.val(r.data.value);
            }
          });
        });
      });
    }

    $el.find("#btnResBack").on("click", function () {
      editing = null;
      listView($el);
    });
    $el.find("#resType").on("change", function () {
      editing.fields = collectFieldValues($el);
      refreshFields();
    });
    $el.find("#btnResSave").on("click", function () {
      var name = ($el.find("#resName").val() || "").trim();
      var description = $el.find("#resDesc").val() || "";
      var pluginId = $el.find("#resType").val();
      var fields = collectFieldValues($el);
      if (!name) {
        A.banner("error", "Name is required");
        return;
      }
      A.api("admin/variable-resolvers/save", {
        method: "POST",
        body: {
          name: name,
          description: description,
          pluginId: pluginId,
          fields: fields,
          previousName: editing.existingName || "",
        },
      }).then(function (saveR) {
        if (!saveR.ok) {
          A.banner("error", "Save failed (" + saveR.status + "): " + (saveR.text || ""));
          return;
        }
        A.banner("ok", "Saved resolver " + name);
        A.showPage("resolvers");
      });
    });

    $el.find("#btnResTest").on("click", function () {
      var name = ($el.find("#resName").val() || "").trim() || "_test";
      var description = $el.find("#resDesc").val() || "";
      var pluginId = $el.find("#resType").val();
      var fields = collectFieldValues($el);
      var argument = ($el.find("#resTestArg").val() || "").trim();
      var $out = $el.find("#resTestResult");
      $out.removeClass("hidden").text("Testing…");
      A.api("admin/variable-resolvers/test", {
        method: "POST",
        body: {
          name: name,
          description: description,
          pluginId: pluginId,
          fields: fields,
          argument: argument,
        },
      }).then(function (tr) {
        if (!tr.ok) {
          $out.text("Test failed (" + tr.status + "): " + (tr.text || ""));
          A.banner("error", "Resolver test failed");
          return;
        }
        var d = tr.data || {};
        if (d.ok) {
          $out.text(
            "OK\nsecretPath=" +
              JSON.stringify(d.secretPath) +
              (d.jsonKey ? "\njsonKey=" + JSON.stringify(d.jsonKey) : "") +
              (d.argumentNormalizedFrom
                ? "\n(normalized from " + JSON.stringify(d.argumentNormalizedFrom) + ")"
                : "") +
              "\nresolved=" +
              JSON.stringify(d.value) +
              (d.masked ? "\n(value redacted in UI)" : "")
          );
          A.banner("ok", "Resolver test succeeded");
        } else {
          $out.text(
            "Failed\nsecretPath=" +
              JSON.stringify(d.secretPath) +
              "\n" +
              (d.error || d.message || JSON.stringify(d, null, 2))
          );
          A.banner("warn", "Resolver returned no value or an error");
        }
      });
    });

    refreshFields();
  }

  A.register("resolvers", function ($el) {
    editing = null;
    schemaCache = {};
    return A.api("admin/variable-resolvers/plugins").then(function (r) {
      plugins = (r.ok && r.data && r.data.plugins) || [];
      listView($el);
    });
  });
})();
