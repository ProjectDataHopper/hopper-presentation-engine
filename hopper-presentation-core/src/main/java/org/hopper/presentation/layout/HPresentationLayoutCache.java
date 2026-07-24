package org.hopper.presentation.layout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;

/**
 * Process-wide cache of component layout snapshots, keyed by presentation name + component name +
 * fingerprint.
 */
public final class HPresentationLayoutCache {

  private static final HPresentationLayoutCache INSTANCE = new HPresentationLayoutCache();

  /** presentationName → (componentName → (fingerprint → snapshot)) */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>> byPresentation =
      new ConcurrentHashMap<>();

  private final AtomicInteger hits = new AtomicInteger();
  private final AtomicInteger misses = new AtomicInteger();
  private final AtomicLong stores = new AtomicLong();

  private HPresentationLayoutCache() {}

  public static HPresentationLayoutCache getInstance() {
    return INSTANCE;
  }

  public HComponentLayoutSnapshot get(
      String presentationName, String componentName, String fingerprint) {
    if (!HLayoutCacheSettings.isEnabled()
        || StringUtils.isBlank(presentationName)
        || StringUtils.isBlank(componentName)
        || StringUtils.isBlank(fingerprint)) {
      return null;
    }
    ConcurrentHashMap<String, Entry> byComp = byPresentation.get(presentationName.trim());
    if (byComp == null) {
      misses.incrementAndGet();
      return null;
    }
    Entry entry = byComp.get(componentName.trim());
    if (entry == null || !fingerprint.equals(entry.fingerprint)) {
      misses.incrementAndGet();
      return null;
    }
    hits.incrementAndGet();
    entry.touch();
    return entry.snapshot;
  }

  public void put(
      String presentationName,
      String componentName,
      String fingerprint,
      HComponentLayoutSnapshot snapshot) {
    if (!HLayoutCacheSettings.isEnabled()
        || snapshot == null
        || StringUtils.isBlank(presentationName)
        || StringUtils.isBlank(componentName)
        || StringUtils.isBlank(fingerprint)) {
      return;
    }
    ConcurrentHashMap<String, Entry> byComp =
        byPresentation.computeIfAbsent(presentationName.trim(), k -> new ConcurrentHashMap<>());
    byComp.put(componentName.trim(), new Entry(fingerprint, snapshot));
    stores.incrementAndGet();
    evictIfNeeded();
  }

  public void invalidatePresentation(String presentationName) {
    if (StringUtils.isBlank(presentationName)) {
      return;
    }
    byPresentation.remove(presentationName.trim());
  }

  public void invalidateComponent(String presentationName, String componentName) {
    if (StringUtils.isBlank(presentationName) || StringUtils.isBlank(componentName)) {
      return;
    }
    ConcurrentHashMap<String, Entry> byComp = byPresentation.get(presentationName.trim());
    if (byComp != null) {
      byComp.remove(componentName.trim());
    }
  }

  /** Drop every snapshot that is not for {@code keepFingerprint} of the component (optional). */
  public void invalidateAll() {
    byPresentation.clear();
  }

  public int size() {
    int n = 0;
    for (ConcurrentHashMap<String, Entry> m : byPresentation.values()) {
      n += m.size();
    }
    return n;
  }

  public int getHits() {
    return hits.get();
  }

  public int getMisses() {
    return misses.get();
  }

  public long getStores() {
    return stores.get();
  }

  public void resetStats() {
    hits.set(0);
    misses.set(0);
    stores.set(0);
  }

  private void evictIfNeeded() {
    int max = HLayoutCacheSettings.getMaxComponents();
    while (size() > max) {
      // Evict oldest touched entry globally
      String victimPres = null;
      String victimComp = null;
      long oldest = Long.MAX_VALUE;
      for (Map.Entry<String, ConcurrentHashMap<String, Entry>> pe : byPresentation.entrySet()) {
        for (Map.Entry<String, Entry> ce : pe.getValue().entrySet()) {
          long t = ce.getValue().lastAccessNanos;
          if (t < oldest) {
            oldest = t;
            victimPres = pe.getKey();
            victimComp = ce.getKey();
          }
        }
      }
      if (victimPres == null) {
        break;
      }
      ConcurrentHashMap<String, Entry> m = byPresentation.get(victimPres);
      if (m != null) {
        m.remove(victimComp);
        if (m.isEmpty()) {
          byPresentation.remove(victimPres, m);
        }
      }
    }
  }

  private static final class Entry {
    final String fingerprint;
    final HComponentLayoutSnapshot snapshot;
    volatile long lastAccessNanos;

    Entry(String fingerprint, HComponentLayoutSnapshot snapshot) {
      this.fingerprint = fingerprint;
      this.snapshot = snapshot;
      this.lastAccessNanos = System.nanoTime();
    }

    void touch() {
      lastAccessNanos = System.nanoTime();
    }
  }
}
