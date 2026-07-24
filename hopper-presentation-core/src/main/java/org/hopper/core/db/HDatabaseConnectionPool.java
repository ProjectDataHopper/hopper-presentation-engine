package org.hopper.core.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.exception.HException;

/**
 * Process-wide JDBC connection pool for Hopper's many short SQL bursts (connector chains, previews,
 * layout). Avoids repeating cloud-secret resolution and TCP handshakes on every SQL step.
 *
 * <p><strong>Important:</strong> pool lookup is keyed by connection <em>name</em> and the
 * <em>unresolved</em> credential template (e.g. the literal {@code #{gsm:…}} string). Variable /
 * secret resolution runs only when opening a <em>new</em> physical connection via Hop {@link
 * Database#connect()}, not on every borrow.
 *
 * <p>Pools {@link Connection} objects. Callers get a Hop {@link Database} with {@link
 * Database#setConnection(Connection)} already set — do <strong>not</strong> call {@code connect()}
 * or {@code disconnect()}; use {@link #release(Database)}.
 *
 * <p>Optional system properties: {@code hopper.db.pool.enabled} (default true), {@code
 * hopper.db.pool.maxPerKey} (default 8), {@code hopper.db.pool.maxIdleMs} (default 600000).
 */
public final class HDatabaseConnectionPool {

  private static final boolean ENABLED =
      !"false".equalsIgnoreCase(System.getProperty("hopper.db.pool.enabled", "true"));
  private static final int MAX_PER_KEY =
      Math.max(1, Integer.getInteger("hopper.db.pool.maxPerKey", 8));
  private static final long MAX_IDLE_MS =
      Math.max(10_000L, Long.getLong("hopper.db.pool.maxIdleMs", 600_000L));
  private static final int VALIDATION_TIMEOUT_SEC = 2;
  private static final long BORROW_TIMEOUT_MS = 30_000L;

  private static final ConcurrentHashMap<PoolKey, PoolBucket> BUCKETS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Database, Lease> LEASES = new ConcurrentHashMap<>();

  private static final AtomicLong STAT_CREATES = new AtomicLong();
  private static final AtomicLong STAT_HITS = new AtomicLong();
  private static final AtomicLong STAT_RELEASES = new AtomicLong();

  private HDatabaseConnectionPool() {}

  /**
   * Borrow a Hop {@link Database} bound to a pooled JDBC connection (or open a new one when the
   * pool is empty or disabled).
   *
   * <p>Does not resolve {@code #{…}} secrets unless a new physical connection must be opened.
   */
  public static Database borrow(
      HDatabaseConnection connectionMeta, IVariables variables, ILoggingObject parentLog)
      throws HException {
    if (connectionMeta == null) {
      throw new HException("Database connection metadata is required");
    }
    IVariables vars = variables != null ? variables : Variables.getADefaultVariableSpace();
    ILoggingObject log =
        parentLog != null
            ? parentLog
            : new LoggingObject("hopper-db-pool:" + connectionMeta.getName());

    try {
      // createDatabaseMeta keeps password as stored (e.g. "#{gsm:…}") — no secret resolve here
      DatabaseMeta databaseMeta = connectionMeta.createDatabaseMeta();

      if (!ENABLED) {
        Database database = new Database(log, vars, databaseMeta);
        database.connect(); // resolves secrets once here only
        LEASES.put(
            database, new Lease(null, database.getConnection(), connectionMeta.getName()));
        return database;
      }

      // Key without variables.resolve — secret templates stay literal
      PoolKey key = PoolKey.fromUnresolved(connectionMeta);
      PoolBucket bucket =
          BUCKETS.computeIfAbsent(key, k -> new PoolBucket(k, connectionMeta));
      Connection jdbc = bucket.borrow(vars, log);
      Database database = new Database(log, vars, databaseMeta);
      database.setConnection(jdbc);
      LEASES.put(database, new Lease(key, jdbc, connectionMeta.getName()));
      return database;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException(
          "Unable to open pooled connection for '" + connectionMeta.getName() + "'", e);
    }
  }

  /**
   * Return a connection from {@link #borrow}. Safe with {@code null}. Non-pooled leases fall back
   * to {@link Database#disconnect()}.
   */
  public static void release(Database database) {
    if (database == null) {
      return;
    }
    Lease lease = LEASES.remove(database);
    if (lease == null) {
      try {
        database.disconnect();
      } catch (Exception ignored) {
        // best effort
      }
      return;
    }
    try {
      database.setConnection(null);
    } catch (Exception ignored) {
      // ignore
    }
    STAT_RELEASES.incrementAndGet();
    if (lease.key == null || !ENABLED) {
      closeQuietly(lease.connection);
      return;
    }
    PoolBucket bucket = BUCKETS.get(lease.key);
    if (bucket == null) {
      closeQuietly(lease.connection);
      return;
    }
    bucket.release(lease.connection);
  }

  /** Drop all idle connections for this metadata connection name. */
  public static void invalidate(String connectionName) {
    if (StringUtils.isBlank(connectionName)) {
      return;
    }
    List<PoolKey> toRemove = new ArrayList<>();
    for (PoolKey key : BUCKETS.keySet()) {
      if (connectionName.equals(key.connectionName)) {
        toRemove.add(key);
      }
    }
    for (PoolKey key : toRemove) {
      PoolBucket bucket = BUCKETS.remove(key);
      if (bucket != null) {
        bucket.closeAll();
      }
    }
  }

  public static void invalidateAll() {
    for (PoolBucket bucket : BUCKETS.values()) {
      bucket.closeAll();
    }
    BUCKETS.clear();
  }

  public static long statsCreates() {
    return STAT_CREATES.get();
  }

  public static long statsHits() {
    return STAT_HITS.get();
  }

  public static long statsReleases() {
    return STAT_RELEASES.get();
  }

  public static void resetStats() {
    STAT_CREATES.set(0);
    STAT_HITS.set(0);
    STAT_RELEASES.set(0);
  }

  public static boolean isEnabled() {
    return ENABLED;
  }

  private static void closeQuietly(Connection connection) {
    if (connection == null) {
      return;
    }
    try {
      if (!connection.isClosed()) {
        connection.close();
      }
    } catch (SQLException ignored) {
      // ignore
    }
  }

  /** True if the connection is open. Does not call isValid (can be slow / flaky). */
  private static boolean isOpen(Connection connection) {
    if (connection == null) {
      return false;
    }
    try {
      return !connection.isClosed();
    } catch (SQLException e) {
      return false;
    }
  }

  /**
   * Validate before reusing a pooled connection. Falls back to {@link #isOpen} if {@link
   * Connection#isValid(int)} is unsupported.
   */
  private static boolean isUsable(Connection connection) {
    if (!isOpen(connection)) {
      return false;
    }
    try {
      return connection.isValid(VALIDATION_TIMEOUT_SEC);
    } catch (AbstractMethodError | Exception e) {
      // Older / minimal drivers: trust isOpen
      return true;
    }
  }

  private record Lease(PoolKey key, Connection connection, String connectionName) {}

  /**
   * Pool identity without calling {@link IVariables#resolve(String)} so cloud secret expressions
   * are not evaluated on every borrow.
   */
  private static final class PoolKey {
    final String connectionName;
    final String templateFingerprint;

    PoolKey(String connectionName, String templateFingerprint) {
      this.connectionName = connectionName;
      this.templateFingerprint = templateFingerprint;
    }

    /**
     * Build key from metadata fields as stored (may contain {@code ${…}} / {@code #{…}} literals).
     */
    static PoolKey fromUnresolved(HDatabaseConnection meta) {
      String name = meta.getName() != null ? meta.getName() : "";
      String fp =
          nvl(meta.getDatabaseTypeCode())
              + "|"
              + nvl(meta.getHostname())
              + "|"
              + nvl(meta.getPort())
              + "|"
              + nvl(meta.getDatabaseName())
              + "|"
              + nvl(meta.getUsername())
              + "|"
              + nvl(meta.getPassword());
      return new PoolKey(name, fp);
    }

    private static String nvl(String s) {
      return s != null ? s : "";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PoolKey other)) {
        return false;
      }
      return Objects.equals(connectionName, other.connectionName)
          && Objects.equals(templateFingerprint, other.templateFingerprint);
    }

    @Override
    public int hashCode() {
      return Objects.hash(connectionName, templateFingerprint);
    }
  }

  private static final class PoolBucket {
    private final PoolKey key;
    private final HDatabaseConnection connectionMeta;
    private final ArrayBlockingQueue<PooledEntry> idle = new ArrayBlockingQueue<>(MAX_PER_KEY);
    private final AtomicInteger live = new AtomicInteger(0);
    private final Object createLock = new Object();

    PoolBucket(PoolKey key, HDatabaseConnection connectionMeta) {
      this.key = key;
      this.connectionMeta = connectionMeta;
    }

    Connection borrow(IVariables variables, ILoggingObject log) throws Exception {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BORROW_TIMEOUT_MS);
      while (true) {
        evictIdle();
        PooledEntry entry = idle.poll();
        if (entry != null) {
          if (isUsable(entry.connection)) {
            STAT_HITS.incrementAndGet();
            return entry.connection;
          }
          closeQuietly(entry.connection);
          live.decrementAndGet();
          continue;
        }

        synchronized (createLock) {
          entry = idle.poll();
          if (entry != null) {
            if (isUsable(entry.connection)) {
              STAT_HITS.incrementAndGet();
              return entry.connection;
            }
            closeQuietly(entry.connection);
            live.decrementAndGet();
          }
          if (live.get() < MAX_PER_KEY) {
            // Secret resolve + TCP only on this path
            Connection created = openPhysical(connectionMeta, variables, log);
            live.incrementAndGet();
            STAT_CREATES.incrementAndGet();
            return created;
          }
        }

        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remainingMs <= 0) {
          throw new HException(
              "Timeout waiting for pooled connection '" + key.connectionName + "'");
        }
        entry = idle.poll(Math.min(remainingMs, 200L), TimeUnit.MILLISECONDS);
        if (entry != null) {
          if (isUsable(entry.connection)) {
            STAT_HITS.incrementAndGet();
            return entry.connection;
          }
          closeQuietly(entry.connection);
          live.decrementAndGet();
        }
      }
    }

    void release(Connection connection) {
      // Only require open on release — isValid on every return was discarding good connections
      // (and forcing a new connect + #{gsm:…} resolve on the next borrow).
      if (!isOpen(connection)) {
        closeQuietly(connection);
        live.decrementAndGet();
        return;
      }
      PooledEntry entry = new PooledEntry(connection, System.nanoTime());
      if (!idle.offer(entry)) {
        closeQuietly(connection);
        live.decrementAndGet();
      }
    }

    void closeAll() {
      List<PooledEntry> drained = new ArrayList<>();
      idle.drainTo(drained);
      for (PooledEntry e : drained) {
        closeQuietly(e.connection);
        live.decrementAndGet();
      }
    }

    private void evictIdle() {
      long cutoff = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(MAX_IDLE_MS);
      Iterator<PooledEntry> it = idle.iterator();
      while (it.hasNext()) {
        PooledEntry e = it.next();
        if (e.lastUsedNanos < cutoff && idle.remove(e)) {
          closeQuietly(e.connection);
          live.decrementAndGet();
        }
      }
    }
  }

  /**
   * Open a new physical JDBC connection via Hop {@link Database#connect()} (this is where {@code
   * #{…}} secrets are resolved), then detach the connection for pooling.
   */
  static Connection openPhysical(
      HDatabaseConnection connectionMeta, IVariables variables, ILoggingObject log)
      throws Exception {
    DatabaseMeta databaseMeta = connectionMeta.createDatabaseMeta();
    Database bootstrap = new Database(log, variables, databaseMeta);
    bootstrap.connect();
    Connection connection = bootstrap.getConnection();
    bootstrap.setConnection(null);
    return connection;
  }

  private record PooledEntry(Connection connection, long lastUsedNanos) {}
}
