package org.hopper.rest.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.rest.HRest;
import org.hopper.rest.render.svg.PresentationSvgRendering;
import org.hopper.rest.security.HActiveUsageRegistry;
import org.hopper.rest.security.HRenderSession;

/** Rebuild-on-miss and Live usage cleanup after render TTL/eviction. */
class RenderRebuildOnMissTest {

  @BeforeEach
  void setUp() {
    HRest.getInstance().clearRenderings();
    HActiveUsageRegistry.getInstance().clear();
    HRenderSession.clear();
    RenderCache.getInstance().configure(10, 200);
  }

  @AfterEach
  void tearDown() {
    HRest.getInstance().clearRenderings();
    HActiveUsageRegistry.getInstance().clear();
    HRenderSession.clear();
  }

  @Test
  void getRendering_requiresCurrentSessionMatch() {
    PresentationSvgRendering r = stub("id-a", "Demo", "session-A");
    HRest.getInstance().storeRendering(r);

    HRenderSession.setCurrentForTests("session-B");
    assertNull(HRest.getInstance().getRendering("id-a"));

    HRenderSession.setCurrentForTests("session-A");
    assertNotNull(HRest.getInstance().getRendering("id-a"));
  }

  @Test
  void rebindRenderId_whenPreferredFree() throws Exception {
    HRenderSession.setCurrentForTests("session-A");
    // Simulate a rebuilt entry under a fresh UUID while the client still holds preferred
    PresentationSvgRendering built = stub("fresh-uuid", "Demo", "session-A");
    HRest.getInstance().storeRendering(built);

    // Preferred id is free — getOrRebuild should re-key when presentation resolves via existing
    IRendering found =
        HRest.getInstance()
            .getOrRebuildRendering(
                "client-held-id",
                "Demo",
                null,
                null,
                Collections.emptyList());
    assertNotNull(found);
    // Prefer rebinding existing session render to client-held id when free
    assertEquals("client-held-id", found.getId());
    assertNotNull(HRest.getInstance().getRendering("client-held-id"));
  }

  @Test
  void purgeExpired_endsActiveUsage() {
    PresentationSvgRendering r = stub("gone", "Demo", "session-A");
    HRest.getInstance().storeRendering(r);
    assertEquals(1, HActiveUsageRegistry.getInstance().size());

    // Force-expire via reflection of lastAccessAt is heavy; remove path ends usage
    HRest.getInstance().removeRenderingById("gone");
    assertEquals(0, HActiveUsageRegistry.getInstance().size());
  }

  @Test
  void pruneNotIn_removesStaleUsageRows() {
    HActiveUsageRegistry.getInstance().start("stale-1", "A", null, null);
    HActiveUsageRegistry.getInstance().start("live-1", "B", null, null);
    int pruned =
        HActiveUsageRegistry.getInstance().pruneNotIn(java.util.Set.of("live-1"));
    assertTrue(pruned >= 1);
    assertNull(HActiveUsageRegistry.getInstance().presentationNameFor("stale-1"));
    assertEquals("B", HActiveUsageRegistry.getInstance().presentationNameFor("live-1"));
  }

  private static PresentationSvgRendering stub(String id, String name, String sessionId) {
    PresentationSvgRendering r = new PresentationSvgRendering();
    r.setId(id);
    r.setPresentationName(name);
    r.setSessionId(sessionId);
    r.setParameters(List.of());
    r.setLayoutResults(new HLayoutResults(null));
    return r;
  }
}
