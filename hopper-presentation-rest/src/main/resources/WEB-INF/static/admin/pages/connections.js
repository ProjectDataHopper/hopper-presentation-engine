(function () {
  "use strict";
  var A = window.HAdmin;

  A.register("connections", function ($el) {
    $el.html('<p class="admin-muted">Loading database connections…</p>');
    return window.HAdminMetadataHost.open("database", $el).catch(function (err) {
      $el.html(
        '<div class="admin-banner error" style="display:block">Failed to load database UI: ' +
          A.esc(err && err.message ? err.message : err) +
          "</div>"
      );
    });
  });
})();
