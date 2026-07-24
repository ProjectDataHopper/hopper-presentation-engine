/**
 * Hopper admin shell: routing, API helpers, banners, admin gate.
 */
(function (global) {
  "use strict";

  var API =
    typeof global.API_BASE === "string" && global.API_BASE
      ? global.API_BASE
      : "/hopper/api/";
  if (API.slice(-1) !== "/") {
    API = API + "/";
  }

  var pages = {};
  var currentPage = "overview";
  var pageTitles = {
    overview: "Overview",
    oauth: "Auth & OAuth",
    settings: "Settings",
    roles: "Roles",
    users: "Users",
    acls: "Access control lists",
    server: "Server ops",
    usage: "Live usage",
  };

  function abs(path) {
    if (!path) return API;
    if (path.indexOf("http") === 0 || path.charAt(0) === "/") return path;
    return API + path.replace(/^\//, "");
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function hasRole(role) {
    var me = global.HAuth && global.HAuth.state && global.HAuth.state.me;
    if (!me || !me.roles) return false;
    var want = String(role).toUpperCase();
    return me.roles.some(function (r) {
      return String(r).toUpperCase() === want;
    });
  }

  function isAdmin() {
    var config = global.HAuth && global.HAuth.state && global.HAuth.state.config;
    // Open API (auth disabled): allow admin UI for local demos
    if (config && config.authEnabled === false) {
      return true;
    }
    return hasRole("ADMIN");
  }

  function gateAdmin() {
    if (isAdmin()) {
      return true;
    }
    var $c = $("#adminContent");
    $("#adminTitle").text("Access denied");
    $c.html(
      '<div class="admin-banner error" style="display:block">' +
        "This panel requires the <strong>ADMIN</strong> role (security.admin)." +
        ' <a href="/hopper/api/render/main/">Return home</a></div>'
    );
    $(".admin-nav-link").addClass("disabled").css("pointer-events", "none");
    return false;
  }

  function banner(type, message) {
    var $b = $("#adminBanner");
    if (!message) {
      $b.addClass("hidden").removeClass("error ok warn").text("");
      return;
    }
    $b.removeClass("hidden error ok warn")
      .addClass(type || "ok")
      .html(message);
  }

  function api(path, options) {
    options = options || {};
    var method = (options.method || "GET").toUpperCase();
    var opts = {
      method: method,
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
    };
    if (options.body != null) {
      opts.headers["Content-Type"] = "application/json";
      opts.body =
        typeof options.body === "string" ? options.body : JSON.stringify(options.body);
    }
    return fetch(abs(path), opts).then(function (res) {
      return res.text().then(function (text) {
        var data = null;
        try {
          data = text ? JSON.parse(text) : null;
        } catch (e) {
          data = { raw: text };
        }
        if (res.status === 401 && global.HAuth) {
          window.location.href = global.HAuth.loginUrl();
        }
        return { ok: res.ok, status: res.status, data: data, text: text };
      });
    });
  }

  function register(name, handler) {
    pages[name] = handler;
  }

  function setActiveNav(name) {
    $(".admin-nav-link").removeClass("active");
    $('.admin-nav-link[data-page="' + name + '"]').addClass("active");
    $("#adminTitle").text(pageTitles[name] || name);
  }

  function showPage(name) {
    currentPage = name || "overview";
    if (!pages[currentPage]) {
      currentPage = "overview";
    }
    setActiveNav(currentPage);
    banner(null);
    var $c = $("#adminContent");
    $c.html('<p class="admin-muted">Loading…</p>');
    var handler = pages[currentPage];
    Promise.resolve(handler($c))
      .catch(function (err) {
        $c.html(
          '<div class="admin-banner error" style="display:block">Failed to load page: ' +
            esc(err && err.message ? err.message : err) +
            "</div>"
        );
      });
    if (history.replaceState) {
      history.replaceState(null, "", "#" + currentPage);
    }
  }

  function boot() {
    $(".admin-nav-link").on("click", function (e) {
      e.preventDefault();
      var page = $(this).data("page");
      showPage(page);
    });
    $("#btnAdminRefresh").on("click", function () {
      showPage(currentPage);
    });
    var hash = (window.location.hash || "#overview").replace(/^#/, "");
    showPage(hash || "overview");
  }

  function formRow(label, inputHtml, hint) {
    return (
      '<div class="admin-form-row"><label>' +
      esc(label) +
      "</label>" +
      inputHtml +
      (hint ? '<div class="hint">' + esc(hint) + "</div>" : "") +
      "</div>"
    );
  }

  function inputText(name, value, placeholder) {
    return (
      '<input type="text" name="' +
      esc(name) +
      '" value="' +
      esc(value || "") +
      '" placeholder="' +
      esc(placeholder || "") +
      '">'
    );
  }

  global.HAdmin = {
    API: API,
    abs: abs,
    esc: esc,
    api: api,
    banner: banner,
    register: register,
    showPage: showPage,
    boot: boot,
    gateAdmin: gateAdmin,
    isAdmin: isAdmin,
    hasRole: hasRole,
    formRow: formRow,
    inputText: inputText,
    currentPage: function () {
      return currentPage;
    },
  };
})(window);
