package org.hopper.core.db;

import org.apache.hop.core.database.Database;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.variables.IVariables;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.exception.HException;

/**
 * AutoCloseable handle around a pooled {@link Database}. Prefer try-with-resources so connections
 * always return to {@link HDatabaseConnectionPool}.
 */
public final class HPooledDatabase implements AutoCloseable {

  private final Database database;
  private boolean closed;

  private HPooledDatabase(Database database) {
    this.database = database;
  }

  public static HPooledDatabase open(
      HDatabaseConnection connectionMeta, IVariables variables, ILoggingObject parentLog)
      throws HException {
    return new HPooledDatabase(
        HDatabaseConnectionPool.borrow(connectionMeta, variables, parentLog));
  }

  public Database getDatabase() {
    return database;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    HDatabaseConnectionPool.release(database);
  }
}
