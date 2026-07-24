/**
 * Browser auth helper for Hopper Presentation REST.
 * - Loads /auth/config and /auth/me
 * - On 401 (when auth enabled), redirects to OIDC/static-dev login
 * - Renders a small user chip when #hopperAuthUser is present
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

  var state = {
    config: null,
    me: null,
    ready: false,
  };

  function abs(path) {
    if (!path) {
      return API;
    }
    if (path.indexOf("http") === 0) {
      return path;
    }
    if (path.charAt(0) === "/") {
      return path;
    }
    return API + path.replace(/^\//, "");
  }

  function currentReturnTo() {
    return window.location.pathname + window.location.search + window.location.hash;
  }

  function loginUrl(returnTo) {
    var rt = encodeURIComponent(returnTo || currentReturnTo());
    return abs("auth/login?returnTo=" + rt);
  }

  function fetchJson(url, options) {
    options = options || {};
    options.credentials = options.credentials || "same-origin";
    options.headers = options.headers || {};
    if (!options.headers.Accept) {
      options.headers.Accept = "application/json";
    }
    return fetch(url, options).then(function (res) {
      return res.text().then(function (text) {
        var data = null;
        try {
          data = text ? JSON.parse(text) : null;
        } catch (e) {
          data = { raw: text };
        }
        return { ok: res.ok, status: res.status, data: data, response: res };
      });
    });
  }

  function loadConfig() {
    return fetchJson(abs("auth/config")).then(function (r) {
      state.config = r.data || { authEnabled: false };
      return state.config;
    });
  }

  function loadMe() {
    return fetchJson(abs("auth/me")).then(function (r) {
      if (r.status === 401) {
        state.me = { authenticated: false };
        return state.me;
      }
      state.me = r.data || { authenticated: false };
      return state.me;
    });
  }

  function ensureAuthenticated() {
    if (!state.config || !state.config.authEnabled) {
      return Promise.resolve(true);
    }
    if (state.me && state.me.authenticated) {
      return Promise.resolve(true);
    }
    // Redirect to login
    window.location.href = loginUrl();
    return Promise.resolve(false);
  }

  function logout() {
    return fetchJson(abs("auth/logout"), { method: "POST" }).then(function () {
      state.me = { authenticated: false };
      if (state.config && state.config.authEnabled) {
        window.location.href = loginUrl();
      } else {
        window.location.reload();
      }
    });
  }

  function renderUserChip() {
    var el = document.getElementById("hopperAuthUser");
    if (!el) {
      return;
    }
    if (!state.config) {
      el.innerHTML = "";
      return;
    }
    if (!state.config.authEnabled) {
      el.innerHTML =
        '<span class="hopper-auth-chip hopper-auth-open" title="Authentication disabled">open</span>';
      return;
    }
    if (state.me && state.me.authenticated) {
      var name = state.me.username || state.me.subject || "user";
      var roles = Array.isArray(state.me.roles) ? state.me.roles.join(", ") : "";
      var isAdmin =
        Array.isArray(state.me.roles) &&
        state.me.roles.some(function (r) {
          return String(r).toUpperCase() === "ADMIN";
        });
      var adminLink = isAdmin
        ? ' <a class="hopper-auth-admin" href="/hopper/api/static/admin/">Admin</a>'
        : "";
      el.innerHTML =
        '<span class="hopper-auth-chip" title="' +
        escapeAttr(roles) +
        '">' +
        escapeHtml(name) +
        "</span>" +
        adminLink +
        ' <button type="button" class="hopper-auth-logout" id="hopperAuthLogout">Sign out</button>';
      var btn = document.getElementById("hopperAuthLogout");
      if (btn) {
        btn.onclick = function () {
          logout();
        };
      }
    } else {
      el.innerHTML =
        '<a class="hopper-auth-login" href="' +
        escapeAttr(loginUrl()) +
        '">Sign in</a>';
    }
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function escapeAttr(s) {
    return escapeHtml(s).replace(/'/g, "&#39;");
  }

  function installAjaxAuth() {
    if (typeof global.jQuery === "undefined" && typeof global.$ === "undefined") {
      return;
    }
    var $ = global.jQuery || global.$;
    $.ajaxSetup({
      xhrFields: { withCredentials: true },
      statusCode: {
        401: function () {
          if (state.config && state.config.authEnabled) {
            window.location.href = loginUrl();
          }
        },
      },
    });
  }

  function init(options) {
    options = options || {};
    installAjaxAuth();
    return loadConfig()
      .then(function () {
        return loadMe();
      })
      .then(function () {
        state.ready = true;
        renderUserChip();
        if (options.requireAuth) {
          return ensureAuthenticated();
        }
        return true;
      })
      .catch(function (err) {
        console.warn("hopper-auth init failed", err);
        state.ready = true;
        return true;
      });
  }

  function hasRole(role) {
    if (!state.me || !Array.isArray(state.me.roles)) {
      return false;
    }
    var want = String(role || "").toUpperCase();
    return state.me.roles.some(function (r) {
      return String(r).toUpperCase() === want;
    });
  }

  global.HAuth = {
    init: init,
    state: state,
    loginUrl: loginUrl,
    logout: logout,
    ensureAuthenticated: ensureAuthenticated,
    loadMe: loadMe,
    renderUserChip: renderUserChip,
    hasRole: hasRole,
  };
})(window);
