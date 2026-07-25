package org.hopper.presentation.connector.type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.row.HHopRowsFile;
import org.hopper.presentation.datacontext.HConnectorDiskCache;
import org.hopper.presentation.datacontext.HConnectorResultCache;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Wraps a catalog connector so the first {@link #startStreaming} in a layout fills a {@link
 * HConnectorResultCache} entry and later streams of the same name replay cached rows. Optionally
 * persists results to disk when the delegate has {@link IHConnector#isCacheOnDisk()}.
 *
 * <p>Not serializable metadata — created only at runtime by {@code PresentationDataContext}.
 */
public final class HCachingConnector implements IHConnector {

  private final String catalogName;
  private final IHConnector delegate;
  private final List<IHRowListener> rowListeners = new ArrayList<>();

  /** Set while a live (non-cache) stream is in progress. */
  private transient CollectingListener activeCollector;

  private transient boolean replayedFromCache;

  /** Live-stream metric open between startStreaming and waitUntilFinished. */
  private transient boolean liveStreamMetricsOpen;

  private transient org.apache.hop.core.logging.ILogChannel liveStreamLog;

  /** Fingerprint used for disk cache write after a live stream. */
  private transient String activeDiskFingerprint;

  public HCachingConnector(String catalogName, IHConnector delegate) {
    this.catalogName = catalogName;
    this.delegate = delegate;
  }

  public static IHConnector wrapIfNeeded(String catalogName, IHConnector delegate) {
    if (delegate == null || catalogName == null || catalogName.isBlank()) {
      return delegate;
    }
    if (delegate instanceof HCachingConnector) {
      return delegate;
    }
    return new HCachingConnector(catalogName, delegate);
  }

  public String getCatalogName() {
    return catalogName;
  }

  public IHConnector getDelegate() {
    return delegate;
  }

  @Override
  public String getPluginId() {
    return delegate != null ? delegate.getPluginId() : null;
  }

  @Override
  public void setPluginId(String pluginId) {
    if (delegate != null) {
      delegate.setPluginId(pluginId);
    }
  }

  @Override
  public List<IHRowListener> getRowListeners() {
    return rowListeners;
  }

  @Override
  public void setRowListeners(List<IHRowListener> rowListeners) {
    this.rowListeners.clear();
    if (rowListeners != null) {
      this.rowListeners.addAll(rowListeners);
    }
  }

  @Override
  public String getSourceConnectorName() {
    return delegate != null ? delegate.getSourceConnectorName() : null;
  }

  @Override
  public void setSourceConnectorName(String sourceConnectorName) {
    if (delegate != null) {
      delegate.setSourceConnectorName(sourceConnectorName);
    }
  }

  @Override
  public boolean isCacheOnDisk() {
    return delegate != null && delegate.isCacheOnDisk();
  }

  @Override
  public void setCacheOnDisk(boolean cacheOnDisk) {
    if (delegate != null) {
      delegate.setCacheOnDisk(cacheOnDisk);
    }
  }

  @Override
  public IHConnector clone() {
    IHConnector clonedDelegate = delegate != null ? delegate.clone() : null;
    return new HCachingConnector(catalogName, clonedDelegate);
  }

  @Override
  public String getDialogClassname() {
    return delegate != null ? delegate.getDialogClassname() : null;
  }

  @Override
  public void addRowListener(IHRowListener rowListener) throws HException {
    if (rowListener != null) {
      rowListeners.add(rowListener);
    }
  }

  @Override
  public void removeDataListener(IHRowListener rowListener) {
    rowListeners.remove(rowListener);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    HConnectorResultCache cache = cacheOf(dataContext);
    if (cache != null && cache.isEnabled()) {
      HConnectorResultCache.Entry hit = cache.peek(catalogName);
      if (hit != null && hit.getRowMeta() != null) {
        return hit.getRowMeta();
      }
    }
    if (HConnectorDiskCache.isEnabledFor(delegate)
        && dataContext != null
        && !dataContext.isForceReload()) {
      try {
        String fp = diskFingerprint(dataContext);
        HHopRowsFile.Snapshot snap = HConnectorDiskCache.load(catalogName, fp);
        if (snap != null && snap.getRowMeta() != null) {
          return snap.getRowMeta();
        }
      } catch (HException ignored) {
        // fall through to live describe
      }
    }
    return delegate.describeOutput(dataContext);
  }

  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    replayedFromCache = false;
    activeCollector = null;
    activeDiskFingerprint = null;
    org.apache.hop.core.logging.ILogChannel log =
        dataContext != null ? dataContext.getLogChannel() : null;

    HConnectorResultCache cache = cacheOf(dataContext);
    if (cache != null && cache.isEnabled()) {
      HConnectorResultCache.Entry hit = cache.get(catalogName);
      if (hit != null) {
        replayedFromCache = true;
        replayWithMetrics(log, hit.getRowMeta(), hit.getRows(), "memory");
        return;
      }
      cache.recordMiss();
    }

    // Disk cache: only when not force-reload and connector opted in
    boolean forceReload = dataContext != null && dataContext.isForceReload();
    if (!forceReload && HConnectorDiskCache.isEnabledFor(delegate)) {
      String fp = diskFingerprint(dataContext);
      activeDiskFingerprint = fp;
      try {
        HHopRowsFile.Snapshot snap = HConnectorDiskCache.load(catalogName, fp);
        if (snap != null) {
          // Seed memory cache for other components in this layout
          if (cache != null && cache.isEnabled()) {
            cache.putIfFits(catalogName, snap.getRowMeta(), snap.getRows());
          }
          replayedFromCache = true;
          replayWithMetrics(log, snap.getRowMeta(), snap.getRows(), "disk");
          return;
        }
      } catch (HException e) {
        // Corrupt file → live stream
        if (log != null) {
          log.logError("Disk connector cache read failed for '" + catalogName + "': " + e.getMessage());
        }
      }
    } else if (HConnectorDiskCache.isEnabledFor(delegate)) {
      activeDiskFingerprint = diskFingerprint(dataContext);
    }

    // Live stream: forward rows to our listeners and optionally fill the cache
    org.hopper.core.log.HMetricsUtil.start(
        log,
        org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_RETRIEVE,
        "Connector retrieve rows",
        catalogName);
    liveStreamMetricsOpen = true;
    liveStreamLog = log;
    boolean collect =
        (cache != null && cache.isEnabled()) || HConnectorDiskCache.isEnabledFor(delegate);
    CollectingListener collector = new CollectingListener(cache, collect);
    activeCollector = collector;
    delegate.addRowListener(collector);
    try {
      delegate.startStreaming(dataContext);
    } catch (HException | RuntimeException e) {
      safeDetachCollector();
      activeCollector = null;
      stopLiveStreamMetric();
      throw e;
    }
  }

  @Override
  public void waitUntilFinished() throws HException {
    if (replayedFromCache) {
      return;
    }
    try {
      if (delegate != null) {
        delegate.waitUntilFinished();
      }
    } finally {
      finalizeCacheAfterStream();
      stopLiveStreamMetric();
    }
  }

  private void replayWithMetrics(
      org.apache.hop.core.logging.ILogChannel log,
      IRowMeta meta,
      List<Object[]> rows,
      String source)
      throws HException {
    org.hopper.core.log.HMetricsUtil.start(
        log,
        org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_CACHE_REPLAY,
        "Connector cache replay (" + source + ")",
        catalogName);
    try {
      replay(meta, rows);
    } finally {
      org.hopper.core.log.HMetricsUtil.stop(
          log,
          org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_CACHE_REPLAY,
          "Connector cache replay (" + source + ")",
          catalogName);
    }
  }

  private void stopLiveStreamMetric() {
    if (!liveStreamMetricsOpen) {
      return;
    }
    liveStreamMetricsOpen = false;
    org.hopper.core.log.HMetricsUtil.stop(
        liveStreamLog,
        org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_RETRIEVE,
        "Connector retrieve rows",
        catalogName);
    liveStreamLog = null;
  }

  private void finalizeCacheAfterStream() {
    CollectingListener collector = activeCollector;
    safeDetachCollector();
    activeCollector = null;
    if (collector == null || !collector.collecting || collector.overflow.get()) {
      return;
    }
    IRowMeta meta = collector.rowMeta.get();
    if (meta == null) {
      meta = new org.apache.hop.core.row.RowMeta();
    }
    HConnectorResultCache cache = collector.cache;
    if (cache != null && cache.isEnabled()) {
      cache.putIfFits(catalogName, meta, collector.rows);
    }
    if (HConnectorDiskCache.isEnabledFor(delegate) && activeDiskFingerprint != null) {
      try {
        HConnectorDiskCache.store(catalogName, activeDiskFingerprint, meta, collector.rows);
      } catch (HException e) {
        // non-fatal
        if (liveStreamLog != null) {
          liveStreamLog.logError(
              "Disk connector cache write failed for '" + catalogName + "': " + e.getMessage());
        }
      }
    }
  }

  private void safeDetachCollector() {
    if (activeCollector != null && delegate != null) {
      try {
        delegate.removeDataListener(activeCollector);
      } catch (Exception ignored) {
        // best effort
      }
    }
  }

  private void replay(IRowMeta meta, List<Object[]> rows) throws HException {
    if (rows != null) {
      for (Object[] row : rows) {
        for (IHRowListener listener : rowListeners) {
          listener.rowReceived(meta, row);
        }
      }
    }
    for (IHRowListener listener : rowListeners) {
      listener.rowReceived(null, null);
    }
  }

  private String diskFingerprint(IDataContext dataContext) {
    String varFp = "";
    if (dataContext != null && dataContext.getVariables() != null) {
      // Lightweight: presentation name + a few common params if set
      try {
        varFp =
            String.valueOf(dataContext.getVariables().getVariable("PRESENTATION_NAME"))
                + "|"
                + String.valueOf(dataContext.getVariables().getVariable("HOPPER_SHIP_API_URL"));
      } catch (Exception ignored) {
        varFp = "";
      }
    }
    return HConnectorDiskCache.fingerprint(delegate, varFp);
  }

  private static HConnectorResultCache cacheOf(IDataContext dataContext) {
    return dataContext != null ? dataContext.getConnectorResultCache() : null;
  }

  private final class CollectingListener implements IHRowListener {
    private final HConnectorResultCache cache;
    private final boolean collecting;
    private final List<Object[]> rows = new ArrayList<>();
    private final AtomicReference<IRowMeta> rowMeta = new AtomicReference<>();
    private final AtomicBoolean overflow = new AtomicBoolean(false);

    private CollectingListener(HConnectorResultCache cache, boolean collecting) {
      this.cache = cache;
      this.collecting = collecting;
    }

    @Override
    public void rowReceived(IRowMeta meta, Object[] data) throws HException {
      if (data != null && collecting && !overflow.get()) {
        if (rowMeta.get() == null && meta != null) {
          rowMeta.set(meta);
        }
        int max =
            cache != null
                ? cache.getMaxRows()
                : org.hopper.presentation.datacontext.HConnectorCacheSettings.getMaxRows();
        if (max <= 0) {
          overflow.set(true);
          rows.clear();
        } else if (rows.size() < max) {
          rows.add(data);
        } else {
          overflow.set(true);
          rows.clear();
          if (cache != null) {
            cache.recordSkippedTooLarge();
          }
        }
      }
      for (IHRowListener listener : rowListeners) {
        listener.rowReceived(meta, data);
      }
    }
  }
}
