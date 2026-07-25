package org.hopper.rest.render;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.rest.HRest;
import org.hopper.rest.render.svg.PresentationSvgRendering;
import org.hopper.rest.security.HRenderSession;

/**
 * Renderings must not be shared across browser sessions even for the same presentation name.
 */
class RenderSessionIsolationTest {

  @BeforeEach
  void setUp() {
    HRest.getInstance().clearRenderings();
    HRenderSession.clear();
  }

  @AfterEach
  void tearDown() {
    HRest.getInstance().clearRenderings();
    HRenderSession.clear();
  }

  @Test
  void findRendering_isSessionScoped() {
    PresentationSvgRendering a = new PresentationSvgRendering();
    a.setPresentationName("Sales");
    a.setParameters(Collections.emptyList());
    a.setSessionId("session-A");
    a.setLayoutResults(new HLayoutResults(null));
    HRest.getInstance().storeRendering(a);

    PresentationSvgRendering b = new PresentationSvgRendering();
    b.setPresentationName("Sales");
    b.setParameters(Collections.emptyList());
    b.setSessionId("session-B");
    b.setLayoutResults(new HLayoutResults(null));
    HRest.getInstance().storeRendering(b);

    HRenderSession.setCurrentForTests("session-A");
    IRendering foundA = HRest.getInstance().findRendering("Sales", Collections.emptyList());
    assertNotNull(foundA);

    HRenderSession.setCurrentForTests("session-B");
    IRendering foundB = HRest.getInstance().findRendering("Sales", Collections.emptyList());
    assertNotNull(foundB);
    assertNotEquals(foundA.getId(), foundB.getId());

    // UUID of A is not readable from session B
    assertNull(HRest.getInstance().getRendering(foundA.getId()));
    assertNotNull(HRest.getInstance().getRendering(foundB.getId()));
  }

  @Test
  void legacyRenderingWithoutSessionId_notReadable() {
    PresentationSvgRendering legacy = new PresentationSvgRendering();
    legacy.setPresentationName("Old");
    legacy.setParameters(Collections.emptyList());
    legacy.setSessionId(null);
    legacy.setLayoutResults(new HLayoutResults(null));
    // Bypass storeRendering stamping by putting directly
    RenderCache.getInstance().put(legacy);

    HRenderSession.setCurrentForTests("session-A");
    assertNull(HRest.getInstance().getRendering(legacy.getId()));
    assertNull(HRest.getInstance().findRendering("Old", Collections.emptyList()));
  }
}
