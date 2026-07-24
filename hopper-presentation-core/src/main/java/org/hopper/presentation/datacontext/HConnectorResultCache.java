package org.hopper.presentation.datacontext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;

/**
 * Per-layout cache of named connector results (metadata catalog name → rows).
 *
 * <p>Scoped to a single {@link PresentationDataContext} so multiple components that read the same
 * connector during one presentation layout share one query/stream. Not shared across requests.
 *
 * <p>Results larger than {@link #maxRows} are streamed but <em>not</em> stored, so later consumers
 * re-query rather than seeing a truncated set.
 */
public final class HConnectorResultCache {

  private final int maxRows;
  private final boolean enabled;
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  private int hits;
  private int misses;
  private int skippedTooLarge;

  public HConnectorResultCache(boolean enabled, int maxRows) {
    this.enabled = enabled;
    this.maxRows = Math.max(0, maxRows);
  }

  public static HConnectorResultCache fromRuntimeSettings() {
    return new HConnectorResultCache(
        HConnectorCacheSettings.isEnabled(), HConnectorCacheSettings.getMaxRows());
  }

  public boolean isEnabled() {
    return enabled && maxRows > 0;
  }

  public int getMaxRows() {
    return maxRows;
  }

  /** Lookup without updating hit counters (e.g. describeOutput). */
  public Entry peek(String connectorName) {
    if (!isEnabled() || StringUtils.isBlank(connectorName)) {
      return null;
    }
    return entries.get(connectorName.trim());
  }

  /** Lookup for streaming; increments hit counter when present. */
  public Entry get(String connectorName) {
    Entry e = peek(connectorName);
    if (e != null) {
      hits++;
    }
    return e;
  }

  /**
   * Store a complete result if it fits under {@link #maxRows}. No-op when disabled, blank name, or
   * too many rows. Computes a cheap content fingerprint once for layout-cache keys.
   */
  public void putIfFits(String connectorName, IRowMeta rowMeta, List<Object[]> rows) {
    if (!isEnabled() || StringUtils.isBlank(connectorName)) {
      return;
    }
    if (rowMeta == null || rows == null) {
      return;
    }
    if (rows.size() > maxRows) {
      skippedTooLarge++;
      return;
    }
    List<Object[]> copy = new ArrayList<>(rows.size());
    copy.addAll(rows);
    IRowMeta metaCopy = cloneMeta(rowMeta);
    String fp =
        org.hopper.presentation.layout.HLayoutFingerprint.connectorRowsSummary(
            metaCopy, copy.size());
    entries.put(
        connectorName.trim(), new Entry(metaCopy, Collections.unmodifiableList(copy), fp));
  }

  public void recordMiss() {
    misses++;
  }

  public void recordSkippedTooLarge() {
    skippedTooLarge++;
  }

  public int size() {
    return entries.size();
  }

  public int getHits() {
    return hits;
  }

  public int getMisses() {
    return misses;
  }

  public int getSkippedTooLarge() {
    return skippedTooLarge;
  }

  private static IRowMeta cloneMeta(IRowMeta rowMeta) {
    try {
      Object cloned = rowMeta.clone();
      if (cloned instanceof IRowMeta m) {
        return m;
      }
    } catch (Exception ignored) {
      // fall through
    }
    return rowMeta;
  }

  /** Cached stream payload. */
  public static final class Entry {
    private final IRowMeta rowMeta;
    private final List<Object[]> rows;
    /** Meta + row-count fingerprint; computed once at cache put. */
    private final String contentFingerprint;

    public Entry(IRowMeta rowMeta, List<Object[]> rows) {
      this(rowMeta, rows, null);
    }

    public Entry(IRowMeta rowMeta, List<Object[]> rows, String contentFingerprint) {
      this.rowMeta = rowMeta;
      this.rows = rows;
      this.contentFingerprint = contentFingerprint;
    }

    public IRowMeta getRowMeta() {
      return rowMeta;
    }

    public List<Object[]> getRows() {
      return rows;
    }

    public String getContentFingerprint() {
      return contentFingerprint;
    }

    public int size() {
      return rows != null ? rows.size() : 0;
    }
  }
}
