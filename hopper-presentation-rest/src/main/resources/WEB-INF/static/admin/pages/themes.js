(function () {
  "use strict";
  var A = window.HAdmin;

  A.register("themes", function ($el) {
    $el.html('<p class="admin-muted">Loading themes…</p>');
    return window.HAdminMetadataHost.open("themes", $el).catch(function (err) {
      $el.html(
        '<div class="admin-banner error" style="display:block">Failed to load theme UI: ' +
          A.esc(err && err.message ? err.message : err) +
          "</div>"
      );
    });
  });
})();
