package org.hopper.rest.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.variable.HParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderCacheTest {

  @BeforeEach
  void reset() {
    RenderCache.getInstance().clear();
    RenderCache.getInstance().configure(60, 200);
  }

  @Test
  void putGetRemove() {
    IRendering r = stub("id-1", "Sales");
    RenderCache.getInstance().put(r);
    assertEquals(1, RenderCache.getInstance().size());
    assertNotNull(RenderCache.getInstance().get("id-1"));
    RenderCache.getInstance().remove("id-1");
    assertNull(RenderCache.getInstance().get("id-1"));
  }

  @Test
  void maxEntriesEvictsLru() {
    RenderCache.getInstance().configure(60, 2);
    RenderCache.getInstance().put(stub("a", "A"));
    RenderCache.getInstance().put(stub("b", "B"));
    // touch a so b is older on next put
    assertNotNull(RenderCache.getInstance().get("a"));
    RenderCache.getInstance().put(stub("c", "C"));
    assertEquals(2, RenderCache.getInstance().size());
    assertNull(RenderCache.getInstance().get("b"));
    assertNotNull(RenderCache.getInstance().get("a"));
    assertNotNull(RenderCache.getInstance().get("c"));
    assertTrue((Long) RenderCache.getInstance().stats().get("evictedLru") >= 1);
  }

  @Test
  void purgeExpiredRemovesIdle() throws Exception {
    RenderCache cache = RenderCache.getInstance();
    cache.configure(1, 100); // 1 minute — we'll force-expire via reflection on Entry
    cache.put(stub("old", "Old"));
    // Manually set lastAccess far in the past
    var field = RenderCache.class.getDeclaredField("cache");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    var map = (java.util.Map<String, Object>) field.get(cache);
    Object entry = map.get("old");
    var lastAccess = entry.getClass().getDeclaredField("lastAccessAt");
    lastAccess.setAccessible(true);
    lastAccess.set(entry, java.time.Instant.now().minusSeconds(120));
    int purged = cache.purgeExpired();
    assertEquals(1, purged);
    assertNull(cache.get("old"));
  }

  private static IRendering stub(String id, String name) {
    return new IRendering() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public HPresentation getPresentation() {
        return null;
      }

      @Override
      public String getPresentationName() {
        return name;
      }

      @Override
      public Date getRenderDate() {
        return new Date();
      }

      @Override
      public List<HParameter> getParameters() {
        return List.of();
      }

      @Override
      public HLayoutResults getLayoutResults() {
        return null;
      }
    };
  }
}
