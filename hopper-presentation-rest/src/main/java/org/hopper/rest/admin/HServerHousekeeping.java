package org.hopper.rest.admin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hopper.rest.render.RenderCache;
import org.hopper.rest.security.HSessionStore;

/**
 * Periodic cleanup of expired browser sessions and idle renderings.
 *
 * <p>Interval and render TTL/max come from runtime settings ({@code
 * server.session.sweep-interval-seconds}, {@code server.render.*}).
 */
public final class HServerHousekeeping {

  private static final Logger LOG = Logger.getLogger(HServerHousekeeping.class.getName());
  private static final HServerHousekeeping INSTANCE = new HServerHousekeeping();

  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "hopper-housekeeping");
            t.setDaemon(true);
            return t;
          });

  private volatile int sweepIntervalSeconds = 60;
  private volatile boolean started;
  private ScheduledFuture<?> scheduled;
  private final AtomicLong lastRunEpochMs = new AtomicLong(0);
  private final AtomicInteger lastSessionsPurged = new AtomicInteger(0);
  private final AtomicInteger lastRendersPurged = new AtomicInteger(0);
  private final AtomicLong runCount = new AtomicLong(0);

  private HServerHousekeeping() {}

  public static HServerHousekeeping getInstance() {
    return INSTANCE;
  }

  public synchronized void start(int sweepIntervalSeconds) {
    configure(sweepIntervalSeconds);
    started = true;
    reschedule();
    LOG.info("Server housekeeping started (intervalSeconds=" + this.sweepIntervalSeconds + ")");
  }

  public synchronized void configure(int sweepIntervalSeconds) {
    if (sweepIntervalSeconds >= 10) {
      this.sweepIntervalSeconds = sweepIntervalSeconds;
    }
    if (started) {
      reschedule();
    }
  }

  public int getSweepIntervalSeconds() {
    return sweepIntervalSeconds;
  }

  /** Apply render cache settings from effective properties. */
  public void applyRenderSettings(int ttlMinutes, int maxEntries) {
    RenderCache.getInstance().configure(ttlMinutes, maxEntries);
  }

  public void runOnce() {
    try {
      int beforeSessions = HSessionStore.getInstance().size();
      // listActive purges expired sessions
      HSessionStore.getInstance().listActive();
      int afterSessions = HSessionStore.getInstance().size();
      int sessionsPurged = Math.max(0, beforeSessions - afterSessions);

      int rendersPurged = RenderCache.getInstance().purgeExpired();
      // Keep Live usage aligned with the render cache (TTL/LRU may have dropped entries)
      int usagePruned =
          org.hopper.rest.security.HActiveUsageRegistry.getInstance()
              .pruneNotIn(RenderCache.getInstance().liveIds());

      lastSessionsPurged.set(sessionsPurged);
      lastRendersPurged.set(rendersPurged);
      lastRunEpochMs.set(System.currentTimeMillis());
      runCount.incrementAndGet();

      if (sessionsPurged > 0 || rendersPurged > 0 || usagePruned > 0) {
        LOG.info(
            "Housekeeping purged sessions="
                + sessionsPurged
                + " renders="
                + rendersPurged
                + " usageStale="
                + usagePruned
                + " (cacheSize="
                + RenderCache.getInstance().size()
                + ")");
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Housekeeping run failed: " + e.getMessage(), e);
    }
  }

  public java.util.Map<String, Object> stats() {
    java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("sweepIntervalSeconds", sweepIntervalSeconds);
    m.put("started", started);
    m.put("runCount", runCount.get());
    m.put("lastRunEpochMs", lastRunEpochMs.get());
    m.put("lastSessionsPurged", lastSessionsPurged.get());
    m.put("lastRendersPurged", lastRendersPurged.get());
    return m;
  }

  private void reschedule() {
    if (scheduled != null) {
      scheduled.cancel(false);
    }
    scheduled =
        executor.scheduleWithFixedDelay(
            this::runOnce, sweepIntervalSeconds, sweepIntervalSeconds, TimeUnit.SECONDS);
  }
}
