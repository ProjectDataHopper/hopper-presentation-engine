package org.hopper.rest.render;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * In-memory rendering cache with idle TTL and max-entry LRU eviction.
 *
 * <p>Used by {@link org.hopper.rest.HRest} for live presentation renderings. Single-node only.
 */
public class RenderCache {

  private static final Logger LOG = Logger.getLogger(RenderCache.class.getName());
  private static final RenderCache INSTANCE = new RenderCache();

  private final Map<String, Entry> cache = new ConcurrentHashMap<>();
  private volatile int ttlMinutes = 60;
  private volatile int maxEntries = 200;
  private final AtomicLong evictedTtl = new AtomicLong();
  private final AtomicLong evictedLru = new AtomicLong();
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();

  public static RenderCache getInstance() {
    return INSTANCE;
  }

  private RenderCache() {}

  public void configure(int ttlMinutes, int maxEntries) {
    if (ttlMinutes > 0) {
      this.ttlMinutes = ttlMinutes;
    }
    if (maxEntries > 0) {
      this.maxEntries = maxEntries;
    }
    LOG.info(
        () ->
            "RenderCache configured ttlMinutes="
                + this.ttlMinutes
                + " maxEntries="
                + this.maxEntries);
  }

  public int getTtlMinutes() {
    return ttlMinutes;
  }

  public int getMaxEntries() {
    return maxEntries;
  }

  public IRendering get(String id) {
    if (id == null || id.isBlank()) {
      misses.incrementAndGet();
      return null;
    }
    Entry e = cache.get(id);
    if (e == null) {
      misses.incrementAndGet();
      return null;
    }
    if (isExpired(e)) {
      cache.remove(id, e);
      evictedTtl.incrementAndGet();
      misses.incrementAndGet();
      return null;
    }
    e.touch();
    hits.incrementAndGet();
    return e.rendering;
  }

  public void put(IRendering rendering) {
    if (rendering == null || rendering.getId() == null) {
      return;
    }
    cache.put(rendering.getId(), new Entry(rendering));
    enforceMaxEntries();
  }

  public IRendering remove(String id) {
    if (id == null) {
      return null;
    }
    Entry e = cache.remove(id);
    return e != null ? e.rendering : null;
  }

  public IRendering remove(IRendering rendering) {
    if (rendering == null) {
      return null;
    }
    return remove(rendering.getId());
  }

  public void clear() {
    cache.clear();
  }

  public int size() {
    return cache.size();
  }

  /** Remove idle entries past TTL. Returns number removed. */
  public int purgeExpired() {
    int removed = 0;
    for (Map.Entry<String, Entry> me : cache.entrySet()) {
      if (isExpired(me.getValue())) {
        if (cache.remove(me.getKey(), me.getValue())) {
          removed++;
          evictedTtl.incrementAndGet();
        }
      }
    }
    return removed;
  }

  public List<IRendering> values() {
    List<IRendering> list = new ArrayList<>();
    for (Entry e : cache.values()) {
      if (!isExpired(e)) {
        list.add(e.rendering);
      }
    }
    return list;
  }

  public Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("size", cache.size());
    m.put("ttlMinutes", ttlMinutes);
    m.put("maxEntries", maxEntries);
    m.put("hits", hits.get());
    m.put("misses", misses.get());
    m.put("evictedTtl", evictedTtl.get());
    m.put("evictedLru", evictedLru.get());
    return m;
  }

  public List<Map<String, Object>> listEntries() {
    List<Map<String, Object>> list = new ArrayList<>();
    Instant now = Instant.now();
    for (Entry e : cache.values()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.rendering.getId());
      m.put("presentationName", e.rendering.getPresentationName());
      m.put("createdAt", e.createdAt.toString());
      m.put("lastAccessAt", e.lastAccessAt.toString());
      m.put("idleSeconds", Duration.between(e.lastAccessAt, now).getSeconds());
      m.put(
          "ageSeconds", Duration.between(e.createdAt, now).getSeconds());
      list.add(m);
    }
    list.sort(Comparator.comparing(m -> String.valueOf(m.get("lastAccessAt"))));
    return list;
  }

  /** Static helpers matching the previous API. */
  public static IRendering getRendering(String id) {
    return getInstance().get(id);
  }

  public static void addRendering(IRendering rendering) {
    getInstance().put(rendering);
  }

  public static IRendering removeRendering(String id) {
    return getInstance().remove(id);
  }

  private boolean isExpired(Entry e) {
    if (ttlMinutes <= 0) {
      return false;
    }
    return e.lastAccessAt
        .plus(Duration.ofMinutes(ttlMinutes))
        .isBefore(Instant.now());
  }

  private void enforceMaxEntries() {
    int max = maxEntries;
    if (max <= 0 || cache.size() <= max) {
      return;
    }
    // Evict least-recently-accessed until under max
    List<Entry> entries = new ArrayList<>(cache.values());
    entries.sort(Comparator.comparing(en -> en.lastAccessAt));
    int toRemove = cache.size() - max;
    for (int i = 0; i < toRemove && i < entries.size(); i++) {
      Entry e = entries.get(i);
      if (cache.remove(e.rendering.getId(), e)) {
        evictedLru.incrementAndGet();
        try {
          org.hopper.rest.security.HActiveUsageRegistry.getInstance().end(e.rendering.getId());
        } catch (Exception ignored) {
          // optional registry may not care
        }
      }
    }
  }

  static final class Entry {
    final IRendering rendering;
    final Instant createdAt;
    volatile Instant lastAccessAt;

    Entry(IRendering rendering) {
      this.rendering = Objects.requireNonNull(rendering);
      Instant now = Instant.now();
      this.createdAt = now;
      this.lastAccessAt = now;
    }

    void touch() {
      this.lastAccessAt = Instant.now();
    }
  }
}
