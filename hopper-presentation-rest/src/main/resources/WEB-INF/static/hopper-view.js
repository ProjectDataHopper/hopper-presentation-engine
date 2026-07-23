/**
 * View-mode helpers for hopper-presentation.
 * Loaded after hopper-presentation.js when hopperMode === 'view'.
 *
 * View is intentionally thin: canvas, zoom, page nav, and Data Hopper interactions.
 * Structural editing lives in hopper-edit.js.
 */
(function () {
    if (typeof hopperMode === "undefined" || hopperMode !== "view") {
        return;
    }
    console.log("Hopper view mode ready for presentation:", presentationName);
})();
