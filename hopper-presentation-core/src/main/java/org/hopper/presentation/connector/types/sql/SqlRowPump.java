package org.hopper.presentation.connector.types.sql;

import org.hopper.core.exception.HException;
import org.hopper.core.exception.HQueryCancelledException;
import org.hopper.presentation.datacontext.HQueryProgress;

/** Reads a JDBC result until it ends or the user cancels the presentation query. */
final class SqlRowPump {

  @FunctionalInterface
  interface Next {
    Object[] next() throws Exception;
  }

  @FunctionalInterface
  interface OnRow {
    void row(Object[] row) throws HException;
  }

  private SqlRowPump() {}

  static long pump(Next next, OnRow onRow) throws HException {
    HQueryProgress progress = HQueryProgress.current();
    long rows = 0;
    try {
      Object[] row = next.next();
      while (row != null) {
        if (cancelled(progress)) {
          throw new HQueryCancelledException();
        }
        onRow.row(row);
        rows++;
        if (progress != null && (rows == 1 || rows % 1000L == 0L)) {
          progress.rowsRead(rows);
        }
        if (cancelled(progress)) {
          throw new HQueryCancelledException();
        }
        row = next.next();
      }
      if (progress != null) {
        progress.rowsRead(rows);
        progress.rendering();
      }
      return rows;
    } catch (HQueryCancelledException e) {
      throw e;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      if (cancelled(progress)) {
        throw new HQueryCancelledException();
      }
      throw new HException(e.getMessage(), e);
    }
  }

  private static boolean cancelled(HQueryProgress progress) {
    return HQueryProgress.isCancelRequested() || (progress != null && progress.isCanceled());
  }
}
