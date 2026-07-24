package org.hopper.presentation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HAttachment;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.datacontext.HConnectorCacheSettings;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.render.context.PresentationRenderContext;

class HPresentationLayoutCacheTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    HLayoutCacheSettings.setForTests(true, 100);
    HConnectorCacheSettings.setForTests(true, 10_000);
    HPresentationLayoutCache.getInstance().invalidateAll();
    HPresentationLayoutCache.getInstance().resetStats();
  }

  @AfterEach
  void tearDown() {
    HLayoutCacheSettings.resetToDefaults();
    HConnectorCacheSettings.resetToDefaults();
    HPresentationLayoutCache.getInstance().invalidateAll();
  }

  @Test
  void secondLayoutHitsCacheForUnchangedLabel() throws Exception {
    IHopMetadataProvider provider = new MemoryMetadataProvider();
    HPresentation presentation = buildPresentation(provider, "layout-cache-demo");

    PresentationRenderContext rc = new PresentationRenderContext(presentation, provider);
    LoggingObject log = new LoggingObject("test");

    HLayoutResults first =
        presentation.doLayout(log, rc, provider, new ArrayList<HParameter>());
    assertNotNull(first.findGeometry("Box"));
    assertTrue(
        HPresentationLayoutCache.getInstance().getStores() >= 1,
        "first layout should store a snapshot");

    int hitsBefore = HPresentationLayoutCache.getInstance().getHits();
    HLayoutResults second =
        presentation.doLayout(log, rc, provider, new ArrayList<HParameter>());
    assertNotNull(second.findGeometry("Box"));
    assertEquals(
        first.findGeometry("Box").getWidth(),
        second.findGeometry("Box").getWidth());
    assertTrue(
        HPresentationLayoutCache.getInstance().getHits() > hitsBefore,
        "second layout must hit layout cache");
  }

  @Test
  void invalidateForcesMiss() throws Exception {
    IHopMetadataProvider provider = new MemoryMetadataProvider();
    HPresentation presentation = buildPresentation(provider, "layout-cache-invalidate");
    PresentationRenderContext rc = new PresentationRenderContext(presentation, provider);
    LoggingObject log = new LoggingObject("test");

    presentation.doLayout(log, rc, provider, new ArrayList<HParameter>());
    assertTrue(HPresentationLayoutCache.getInstance().getStores() >= 1);

    HPresentationLayoutCache.getInstance().invalidatePresentation(presentation.getName());
    int hitsBefore = HPresentationLayoutCache.getInstance().getHits();
    presentation.doLayout(log, rc, provider, new ArrayList<HParameter>());
    // After invalidate, first access is a miss then store again — hits should not increase
    assertEquals(hitsBefore, HPresentationLayoutCache.getInstance().getHits());
    assertTrue(HPresentationLayoutCache.getInstance().getMisses() >= 1);
  }

  private static HPresentation buildPresentation(IHopMetadataProvider provider, String name) {
    HLabelComponent plugin = new HLabelComponent("Hello");
    HComponent component = new HComponent("Box", plugin);
    HLayout layout = new HLayout();
    layout.setLeft(new HAttachment(null, 0, 10, HAttachment.Alignment.LEFT));
    layout.setTop(new HAttachment(null, 0, 10, HAttachment.Alignment.TOP));
    // Labels often size from text; absolute box still fine for geometry registration
    component.setLayout(layout);

    HPage page = new HPage();
    page.setWidth(800);
    page.setHeight(600);
    page.setTopMargin(20);
    page.setLeftMargin(20);
    page.setBottomMargin(20);
    page.setRightMargin(20);
    page.setComponents(new ArrayList<>(List.of(component)));

    HPresentation presentation = new HPresentation();
    presentation.setName(name);
    presentation.setPages(new ArrayList<>(List.of(page)));
    return presentation;
  }
}
