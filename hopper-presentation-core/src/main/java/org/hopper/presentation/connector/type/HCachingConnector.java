package org.hopper.presentation.connector.type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.datacontext.HConnectorResultCache;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Wraps a catalog connector so the first {@link #startStreaming} in a layout fills a {@link
 * HConnectorResultCache} entry and later streams of the same name replay cached rows.
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
    return delegate.describeOutput(dataContext);
  }

  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    replayedFromCache = false;
    activeCollector = null;
    org.apache.hop.core.logging.ILogChannel log =
        dataContext != null ? dataContext.getLogChannel() : null;

    HConnectorResultCache cache = cacheOf(dataContext);
    if (cache != null && cache.isEnabled()) {
      HConnectorResultCache.Entry hit = cache.get(catalogName);
      if (hit != null) {
        replayedFromCache = true;
        org.hopper.core.log.HMetricsUtil.start(
            log,
            org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_CACHE_REPLAY,
            "Connector cache replay",
            catalogName);
        try {
          replay(hit);
        } finally {
          org.hopper.core.log.HMetricsUtil.stop(
              log,
              org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_CACHE_REPLAY,
              "Connector cache replay",
              catalogName);
        }
        return;
      }
      cache.recordMiss();
    }

    // Live stream: forward rows to our listeners and optionally fill the cache
    org.hopper.core.log.HMetricsUtil.start(
        log,
        org.hopper.core.log.HMetricsUtil.CODE_CONNECTOR_RETRIEVE,
        "Connector retrieve rows",
        catalogName);
    liveStreamMetricsOpen = true;
    liveStreamLog = log;
    CollectingListener collector = new CollectingListener(cache);
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
    if (collector == null) {
      return;
    }
    HConnectorResultCache cache = collector.cache;
    if (cache == null || !cache.isEnabled() || collector.overflow.get()) {
      return;
    }
    IRowMeta meta = collector.rowMeta.get();
    if (meta == null) {
      // Empty result still cacheable with empty meta from a successful stream
      meta = new org.apache.hop.core.row.RowMeta();
    }
    cache.putIfFits(catalogName, meta, collector.rows);
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

  private void replay(HConnectorResultCache.Entry hit) throws HException {
    IRowMeta meta = hit.getRowMeta();
    for (Object[] row : hit.getRows()) {
      for (IHRowListener listener : rowListeners) {
        listener.rowReceived(meta, row);
      }
    }
    // End-of-stream signal
    for (IHRowListener listener : rowListeners) {
      listener.rowReceived(null, null);
    }
  }

  private static HConnectorResultCache cacheOf(IDataContext dataContext) {
    return dataContext != null ? dataContext.getConnectorResultCache() : null;
  }

  private final class CollectingListener implements IHRowListener {
    private final HConnectorResultCache cache;
    private final List<Object[]> rows = new ArrayList<>();
    private final AtomicReference<IRowMeta> rowMeta = new AtomicReference<>();
    private final AtomicBoolean overflow = new AtomicBoolean(false);

    private CollectingListener(HConnectorResultCache cache) {
      this.cache = cache;
    }

    @Override
    public void rowReceived(IRowMeta meta, Object[] data) throws HException {
      if (data != null) {
        if (rowMeta.get() == null && meta != null) {
          rowMeta.set(meta);
        }
        if (cache != null && cache.isEnabled() && !overflow.get()) {
          if (rows.size() < cache.getMaxRows()) {
            rows.add(data);
          } else {
            // Over max: stop collecting; do not cache a partial result
            overflow.set(true);
            rows.clear();
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
