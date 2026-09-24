package org.hopper.presentation.datacontext;

/**
 * Thread-local progress plus a cancel hook the UI thread can run while the worker is blocked in
 * JDBC.
 */
final class HQueryProgressScope {

  private static final ThreadLocal<HQueryProgress> CURRENT = new ThreadLocal<>();
  private static volatile Runnable activeCancel;
  private static volatile boolean cancelRequested;

  private HQueryProgressScope() {}

  static void bind(HQueryProgress progress) {
    cancelRequested = false;
    activeCancel = null;
    if (progress == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(progress);
    }
  }

  static void clear() {
    CURRENT.remove();
    activeCancel = null;
    cancelRequested = false;
  }

  static HQueryProgress current() {
    return CURRENT.get();
  }

  static boolean isCancelRequested() {
    return cancelRequested;
  }

  /** Registers the JDBC cancel action. Ignored when no progress is bound. */
  static void registerCancel(Runnable cancel) {
    if (current() == null) {
      return;
    }
    activeCancel = cancel;
  }

  static void clearCancel() {
    activeCancel = null;
  }

  /** Called from the UI thread. Unblocks a running statement and marks the query cancelled. */
  static void requestCancel() {
    cancelRequested = true;
    Runnable cancel = activeCancel;
    if (cancel == null) {
      return;
    }
    try {
      cancel.run();
    } catch (RuntimeException ignored) {
      // The worker observes the cancel flag and closes the statement.
    }
  }
}
