/**
 * View-mode helpers for hopper-presentation.
 * Loaded after hopper-presentation.js when hopperMode === 'view'.
 *
 * View is intentionally thin: canvas, zoom, page nav (or continuous scroll), and
 * Data Hopper interactions. Structural editing lives in hopper-edit.js.
 *
 * Ops dashboards may set presentation.autoRefreshSeconds to re-render periodically.
 */
(function () {
    if (typeof hopperMode === "undefined" || hopperMode !== "view") {
        return;
    }
    var continuous =
        (typeof continuousScroll !== "undefined" && continuousScroll)
        || (typeof isContinuousView === "function" && isContinuousView());
    console.log(
        "Hopper view mode ready for presentation:",
        presentationName,
        continuous ? "(continuous scroll)" : "(paginated)"
    );

    // Banner when continuous layout hit the height/row cap
    if (continuous
        && typeof contentTruncated !== "undefined"
        && contentTruncated
        && document.body
        && !document.getElementById("continuousTruncatedBanner")) {
        try {
            var banner = document.createElement("div");
            banner.id = "continuousTruncatedBanner";
            banner.className = "hopper-continuous-truncated-banner";
            banner.setAttribute("role", "status");
            banner.textContent =
                "Content was truncated at the continuous height limit. "
                + "Reduce rows or use paginated layout for print.";
            document.body.appendChild(banner);
        } catch (e) {
            /* ignore */
        }
    }

    var seconds =
        typeof autoRefreshSeconds === "number" && !isNaN(autoRefreshSeconds)
            ? autoRefreshSeconds
            : 0;
    // URL override: ?refresh=15
    try {
        var q = new URLSearchParams(window.location.search || "");
        var fromQuery = parseInt(q.get("refresh") || q.get("autoRefresh"), 10);
        if (fromQuery > 0) {
            seconds = fromQuery;
        }
    } catch (e) {
        /* ignore */
    }
    if (seconds < 5) {
        return;
    }
    // Cap to avoid accidental tight loops
    if (seconds > 3600) {
        seconds = 3600;
    }
    console.log("Auto-refresh every", seconds, "s");
    window.setInterval(function () {
        if (typeof softReloadView === "function") {
            softReloadView(true);
        } else if (typeof reloadPresentation === "function") {
            reloadPresentation();
        } else if (typeof loadPage === "function") {
            loadPage(typeof renderPageNumber0 !== "undefined" ? renderPageNumber0 : 0);
        }
    }, seconds * 1000);
})();
