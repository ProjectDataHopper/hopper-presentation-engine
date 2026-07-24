/**
 * Color mode (light / dark / system) for Hopper UI chrome and presentation re-renders.
 */
(function (global) {
    "use strict";

    var STORAGE_KEY = "hopperColorMode";
    var listeners = [];

    function systemPrefersDark() {
        try {
            return global.matchMedia && global.matchMedia("(prefers-color-scheme: dark)").matches;
        } catch (e) {
            return false;
        }
    }

    function getPreference() {
        try {
            var v = global.localStorage && global.localStorage.getItem(STORAGE_KEY);
            if (v === "light" || v === "dark" || v === "system") {
                return v;
            }
        } catch (e) {
            // ignore
        }
        return "system";
    }

    function setPreference(mode) {
        if (mode !== "light" && mode !== "dark" && mode !== "system") {
            mode = "system";
        }
        try {
            if (global.localStorage) {
                global.localStorage.setItem(STORAGE_KEY, mode);
            }
        } catch (e) {
            // ignore
        }
        apply();
    }

    /** Resolved mode for server requests: light | dark */
    function getResolvedMode() {
        var pref = getPreference();
        if (pref === "dark") {
            return "dark";
        }
        if (pref === "light") {
            return "light";
        }
        return systemPrefersDark() ? "dark" : "light";
    }

    function apply() {
        var resolved = getResolvedMode();
        var root = global.document && global.document.documentElement;
        if (root) {
            root.setAttribute("data-theme", resolved);
            root.setAttribute("data-theme-pref", getPreference());
        }
        var body = global.document && global.document.body;
        if (body) {
            body.setAttribute("data-theme", resolved);
        }
        refreshUiIcons(global.document);
        for (var i = 0; i < listeners.length; i++) {
            try {
                listeners[i](resolved, getPreference());
            } catch (e) {
                // ignore listener errors
            }
        }
    }

    function onChange(fn) {
        if (typeof fn === "function") {
            listeners.push(fn);
        }
    }

    function cyclePreference() {
        var pref = getPreference();
        if (pref === "system") {
            setPreference("light");
        } else if (pref === "light") {
            setPreference("dark");
        } else {
            setPreference("system");
        }
    }

    function installToggle(hostEl) {
        if (!hostEl || hostEl.querySelector(".hopper-theme-toggle")) {
            return;
        }
        var btn = global.document.createElement("button");
        btn.type = "button";
        btn.className = "hopper-theme-toggle home-btn home-btn-small";
        btn.title = "Color mode: system / light / dark";
        function label() {
            var pref = getPreference();
            var res = getResolvedMode();
            if (pref === "system") {
                return "Theme: Auto (" + res + ")";
            }
            return "Theme: " + pref;
        }
        btn.textContent = label();
        btn.onclick = function () {
            cyclePreference();
            btn.textContent = label();
        };
        onChange(function () {
            btn.textContent = label();
        });
        hostEl.appendChild(btn);
    }

    /**
     * URL for a monochrome UI icon under /hopper/api/static/images/.
     * Dark mode uses pre-generated images/dark/&lt;file&gt; (see scripts/generate-dark-icons.py).
     *
     * @param {string} fileName e.g. "delete.svg" or "images/delete.svg" or full /hopper/api/static/images/...
     * @param {string} [mode] optional "light"|"dark" (default: resolved preference)
     */
    function uiIconUrl(fileName, mode) {
        if (!fileName) {
            return fileName;
        }
        var bare = String(fileName)
            .replace(/^.*\/static\/images\//, "")
            .replace(/^images\//, "")
            .replace(/^\/+/, "");
        // Already a dark/ path or non-images URL
        if (bare.indexOf("dark/") === 0) {
            bare = bare.substring(5);
        }
        // Logos: no dual asset
        if (bare === "hopper-presentation-logo.svg" || bare === "hopper-presentation.svg") {
            return "/hopper/api/static/images/" + bare;
        }
        var resolved = mode === "light" || mode === "dark" ? mode : getResolvedMode();
        if (resolved === "dark") {
            return "/hopper/api/static/images/dark/" + bare;
        }
        return "/hopper/api/static/images/" + bare;
    }

    /** Update all img[data-ui-icon] to match current mode. */
    function refreshUiIcons(root) {
        var scope = root || global.document;
        if (!scope || !scope.querySelectorAll) {
            return;
        }
        var imgs = scope.querySelectorAll("img[data-ui-icon]");
        for (var i = 0; i < imgs.length; i++) {
            var img = imgs[i];
            var name = img.getAttribute("data-ui-icon");
            if (name) {
                img.src = uiIconUrl(name);
            }
        }
    }

    // Early apply if DOM ready
    if (global.document) {
        if (global.document.documentElement) {
            apply();
        }
        if (global.document.readyState === "loading") {
            global.document.addEventListener("DOMContentLoaded", apply);
        } else {
            apply();
        }
        try {
            if (global.matchMedia) {
                global.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", function () {
                    if (getPreference() === "system") {
                        apply();
                    }
                });
            }
        } catch (e) {
            // ignore
        }
    }

    global.HThemeMode = {
        getPreference: getPreference,
        setPreference: setPreference,
        getResolvedMode: getResolvedMode,
        apply: apply,
        onChange: onChange,
        cyclePreference: cyclePreference,
        installToggle: installToggle,
        uiIconUrl: uiIconUrl,
        refreshUiIcons: refreshUiIcons
    };

    // Global convenience for templates / hopper-presentation.js
    global.uiIconUrl = uiIconUrl;
})(typeof window !== "undefined" ? window : this);
