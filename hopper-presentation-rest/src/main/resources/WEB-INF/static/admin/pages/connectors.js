(function () {
  "use strict";
  var A = window.HAdmin;

  A.register("connectors", function ($el) {
    $el.html('<p class="admin-muted">Loading connector studio…</p>');
    return window.HAdminMetadataHost.open("connectors", $el).catch(function (err) {
      $el.html(
        '<div class="admin-banner error" style="display:block">Failed to load connector UI: ' +
          A.esc(err && err.message ? err.message : err) +
          "</div>"
      );
    });
  });
})();
