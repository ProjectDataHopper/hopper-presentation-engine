(function () {
  "use strict";
  var A = window.HAdmin;
  var esc = A.esc;

  A.register("overview", function ($el) {
    return Promise.all([
      A.api("admin/oauth/status"),
      A.api("admin/settings"),
      A.api("admin/usage/sessions"),
      A.api("admin/usage/active"),
      A.api("admin/roles"),
      A.api("admin/users"),
    ]).then(function (results) {
      var oauth = results[0].ok ? results[0].data : {};
      var settings = results[1].ok ? results[1].data : {};
      var sessions = results[2].ok ? results[2].data : {};
      var renders = results[3].ok ? results[3].data : {};
      var roles = results[4].ok ? results[4].data : {};
      var users = results[5].ok ? results[5].data : {};

      var mode = oauth.authMode || "—";
      var enabled = oauth.authEnabled ? "enabled" : "disabled";
      var provider = oauth.inferredProvider || "none";

      var html = "";
      html +=
        '<p class="admin-muted">Server security and configuration at a glance. Use the sidebar for detailed management.</p>';
      html += '<div class="admin-grid">';
      html += card("Auth", enabled + " / " + mode);
      html += card("Provider", provider);
      html += card("Issuer", oauth.issuerUri || "—");
      html += card("Client ID", oauth.clientId || "—");
      html += card(
        "OIDC browser",
        oauth.oidcBrowserConfigured ? "configured" : "not ready"
      );
      html += card("Sessions", String(sessions.count != null ? sessions.count : "—"));
      html += card("Active renders", String(renders.count != null ? renders.count : "—"));
      html += card(
        "Setting overrides",
        String(settings.overrideCount != null ? settings.overrideCount : "—")
      );
      html += card("Roles", String(roles.count != null ? roles.count : "—"));
      html += card("Users", String(users.count != null ? users.count : "—"));
      html += "</div>";

      html += '<div class="admin-section-title">Quick links</div>';
      html += '<div class="admin-toolbar">';
      html +=
        '<button type="button" class="admin-btn admin-btn-primary" data-go="oauth">Configure OAuth</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="roles">Manage roles</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="users">Assign users</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="acls">Edit ACLs</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="settings">All settings</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="connectors">Connectors</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="connections">DB connections</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="themes">Themes</button>';
      html +=
        '<button type="button" class="admin-btn" data-go="server">Server ops</button>';
      html += "</div>";

      if (oauth.clientSecretConfigured === false && oauth.authMode === "OAUTH2") {
        html +=
          '<div class="admin-banner warn" style="display:block">OAuth2 is on but no client secret is configured. Set an env ref in Auth &amp; OAuth.</div>';
      }

      html += '<div class="admin-section-title">Redirect URI (copy for IdP console)</div>';
      html +=
        '<p><code class="admin-code">' +
        esc(oauth.redirectUri || "http://localhost:8080/hopper/api/auth/callback") +
        "</code></p>";

      $el.html(html);
      $el.find("[data-go]").on("click", function () {
        A.showPage($(this).data("go"));
      });
    });
  });

  function card(label, value) {
    return (
      '<div class="admin-card"><div class="label">' +
      esc(label) +
      '</div><div class="value">' +
      esc(value) +
      "</div></div>"
    );
  }
})();
