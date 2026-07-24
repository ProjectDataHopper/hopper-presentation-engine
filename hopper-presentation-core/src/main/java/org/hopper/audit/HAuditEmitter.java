package org.hopper.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hopper.audit.plugin.IAuditSink;
import org.hopper.core.exception.HException;

/**
 * Fan-out of {@link HAuditEvent}s to registered {@link IAuditSink}s.
 *
 * <p>Supports synchronous or async (bounded queue) dispatch. Default is fail-open: sink failures
 * never propagate to the caller when {@link HAuditConfig#isFailOpen()} is true.
 */
public class HAuditEmitter {

  private static final Logger LOG = Logger.getLogger(HAuditEmitter.class.getName());
  private static final HAuditEmitter INSTANCE = new HAuditEmitter();

  private final List<IAuditSink> sinks = new CopyOnWriteArrayList<>();
  private volatile HAuditConfig config = HAuditConfig.defaults();
  private volatile HAuditRedactor redactor = new HAuditRedactor(config);

  private volatile BlockingQueue<HAuditEvent> queue;
  private volatile Thread worker;
  private volatile boolean workerRunning;

  private final AtomicLong droppedCount = new AtomicLong();
  private final AtomicLong emittedCount = new AtomicLong();

  public static HAuditEmitter getInstance() {
    return INSTANCE;
  }

  public synchronized void configure(HAuditConfig newConfig) {
    this.config = newConfig != null ? newConfig : HAuditConfig.defaults();
    this.redactor = new HAuditRedactor(this.config);
    if (this.config.isAsync()) {
      ensureWorker(this.config.getQueueSize());
    } else {
      stopWorker(false);
    }
  }

  public HAuditConfig getConfig() {
    return config;
  }

  public void setEnabled(boolean enabled) {
    configure(config.toBuilder().enabled(enabled).build());
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  public void setFailOpen(boolean failOpen) {
    configure(config.toBuilder().failOpen(failOpen).build());
  }

  public boolean isFailOpen() {
    return config.isFailOpen();
  }

  /** When false, emit() dispatches on the calling thread (useful for unit tests). */
  public void setAsync(boolean async) {
    configure(config.toBuilder().async(async).build());
  }

  public boolean isAsync() {
    return config.isAsync();
  }

  public void addSink(IAuditSink sink) {
    if (sink != null) {
      sinks.add(sink);
    }
  }

  public void removeSink(IAuditSink sink) {
    sinks.remove(sink);
  }

  public void clearSinks() {
    for (IAuditSink sink : sinks) {
      try {
        sink.close();
      } catch (Exception e) {
        LOG.log(Level.FINE, "Error closing audit sink", e);
      }
    }
    sinks.clear();
  }

  public List<IAuditSink> getSinks() {
    return new ArrayList<>(sinks);
  }

  public long getDroppedCount() {
    return droppedCount.get();
  }

  public long getEmittedCount() {
    return emittedCount.get();
  }

  /**
   * Emit an event to all sinks that accept it. Applies redaction first. Never throws when fail-open
   * is true (default).
   */
  public void emit(HAuditEvent event) throws HException {
    if (!config.isEnabled() || event == null) {
      return;
    }
    HAuditEvent toSend = redactor.redact(event);
    if (config.isAsync()) {
      enqueue(toSend);
    } else {
      dispatch(toSend);
    }
  }

  /** Fail-open convenience for call sites that must not break the user request. */
  public void emitSafely(HAuditEvent event) {
    try {
      emit(event);
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unexpected audit emit failure: " + e.getMessage(), e);
    }
  }

  /**
   * Blocks until the async queue is empty (or timeout). No-op when async is disabled. Useful for
   * tests and graceful shutdown.
   */
  public boolean flush(long timeout, TimeUnit unit) throws InterruptedException {
    if (!config.isAsync() || queue == null) {
      flushSinks();
      return true;
    }
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      if (queue.isEmpty()) {
        // Give the worker a moment to finish the last dispatch
        Thread.sleep(10);
        if (queue.isEmpty()) {
          flushSinks();
          return true;
        }
      }
      Thread.sleep(10);
    }
    return queue.isEmpty();
  }

  public void flushSinks() {
    for (IAuditSink sink : sinks) {
      try {
        sink.flush();
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Audit sink flush failed: " + e.getMessage(), e);
      }
    }
  }

  /** Stop the worker and close sinks. Resets to default config for a clean re-bootstrap. */
  public synchronized void shutdown() {
    stopWorker(true);
    clearSinks();
    droppedCount.set(0);
    emittedCount.set(0);
    this.config = HAuditConfig.defaults();
    this.redactor = new HAuditRedactor(this.config);
  }

  /** Test helper: sync mode, fail-open, empty sinks, fresh counters. */
  public synchronized void resetForTests() {
    stopWorker(false);
    clearSinks();
    droppedCount.set(0);
    emittedCount.set(0);
    configure(
        HAuditConfig.builder()
            .enabled(true)
            .failOpen(true)
            .async(false)
            .bootstrapLogging(false)
            .build());
  }

  private void enqueue(HAuditEvent event) throws HException {
    ensureWorker(config.getQueueSize());
    BlockingQueue<HAuditEvent> q = queue;
    if (q == null) {
      dispatch(event);
      return;
    }
    boolean offered = q.offer(event);
    if (offered) {
      return;
    }
    if (config.getQueueFullPolicy() == HAuditConfig.QueueFullPolicy.BLOCK) {
      try {
        q.put(event);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        droppedCount.incrementAndGet();
        if (!config.isFailOpen()) {
          throw new HException("Audit queue interrupted", e);
        }
        LOG.warning("Audit queue put interrupted; event dropped: " + event.getEventId());
      }
    } else {
      droppedCount.incrementAndGet();
      LOG.warning(
          "Audit queue full (policy=drop); event dropped: "
              + event.getEventId()
              + " totalDropped="
              + droppedCount.get());
    }
  }

  private void dispatch(HAuditEvent event) throws HException {
    for (IAuditSink sink : sinks) {
      try {
        if (sink.accepts(event)) {
          sink.emit(event);
        }
      } catch (Exception e) {
        LOG.log(
            Level.WARNING,
            "Audit sink failed for event " + event.getEventId() + ": " + e.getMessage(),
            e);
        if (!config.isFailOpen()) {
          if (e instanceof HException he) {
            throw he;
          }
          throw new HException("Audit sink failed", e);
        }
      }
    }
    emittedCount.incrementAndGet();
  }

  private synchronized void ensureWorker(int queueSize) {
    if (workerRunning && worker != null && queue != null) {
      return;
    }
    int size = Math.max(16, queueSize);
    queue = new ArrayBlockingQueue<>(size);
    workerRunning = true;
    worker =
        new Thread(
            () -> {
              while (workerRunning || (queue != null && !queue.isEmpty())) {
                try {
                  HAuditEvent event = queue.poll(200, TimeUnit.MILLISECONDS);
                  if (event != null) {
                    try {
                      dispatch(event);
                    } catch (Exception e) {
                      // fail-open already handled inside dispatch for HException; log others
                      LOG.log(Level.WARNING, "Audit worker dispatch error", e);
                    }
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                }
              }
            },
            "hopper-audit-emitter");
    worker.setDaemon(true);
    worker.start();
    LOG.info("Audit emitter async worker started (queueSize=" + size + ")");
  }

  private synchronized void stopWorker(boolean drain) {
    workerRunning = false;
    Thread t = worker;
    if (t != null) {
      t.interrupt();
      try {
        t.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    worker = null;
    if (drain && queue != null) {
      HAuditEvent leftover;
      while ((leftover = queue.poll()) != null) {
        try {
          dispatch(leftover);
        } catch (Exception e) {
          LOG.log(Level.FINE, "Error draining audit queue", e);
        }
      }
    }
    queue = null;
  }
}
