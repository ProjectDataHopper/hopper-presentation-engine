package org.hopper.presentation.datacontext;

/**
 * Progress reported by a connector while a presentation query runs. Bound on the worker thread for
 * the duration of one layout. Cancel is process-wide so the UI thread can stop a blocked JDBC
 * statement.
 */
public interface HQueryProgress {

  void preparing();

  void runningQuery();

  void rowsRead(long rows);

  void rendering();

  boolean isCanceled();

  /** Binds progress for the current thread and clears any previous cancel. */
  static void bind(HQueryProgress progress) {
    HQueryProgressScope.bind(progress);
  }

  static void clear() {
    HQueryProgressScope.clear();
  }

  static HQueryProgress current() {
    return HQueryProgressScope.current();
  }

  /** JDBC cancel action for the query that is about to run. No-op when nothing is bound. */
  static void registerCancel(Runnable cancel) {
    HQueryProgressScope.registerCancel(cancel);
  }

  static void clearCancel() {
    HQueryProgressScope.clearCancel();
  }

  /** UI thread: unblock the running statement and mark the query cancelled. */
  public static void requestCancel() {
    HQueryProgressScope.requestCancel();
  }

  public static boolean isCancelRequested() {
    return HQueryProgressScope.isCancelRequested();
  }
}
