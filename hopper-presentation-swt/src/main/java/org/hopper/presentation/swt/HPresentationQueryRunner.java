package org.hopper.presentation.swt;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ProgressMonitorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.hopper.core.exception.HException;
import org.hopper.core.exception.HQueryCancelledException;
import org.hopper.presentation.datacontext.HQueryProgress;

/**
 * Runs a presentation layout under a cancelable progress dialog. The dialog stays up while the
 * warehouse query streams rows. Cancel stops the JDBC statement.
 */
public final class HPresentationQueryRunner {

  private static final Class<?> PKG = HPresentationQueryRunner.class;

  @FunctionalInterface
  public interface QueryLayout {
    void run() throws HException;
  }

  private HPresentationQueryRunner() {}

  /**
   * @return {@code false} when the user cancelled. {@code onSuccess} is not invoked in that case.
   */
  public static boolean run(Shell shell, QueryLayout layout) throws HException {
    if (layout == null) {
      return true;
    }
    if (shell == null || shell.isDisposed() || EnvironmentUtils.getInstance().isWeb()) {
      layout.run();
      return true;
    }
    Shell parent = dialogParent(shell);
    AtomicBoolean cancelled = new AtomicBoolean(false);
    ProgressMonitorDialog dialog = new ProgressMonitorDialog(parent);
    try {
      dialog.run(
          true,
          monitor -> {
            HQueryProgress.bind(new MonitorQueryProgress(monitor));
            AtomicBoolean stopPoll = new AtomicBoolean(false);
            Display display = parent.getDisplay();
            Runnable poll =
                new Runnable() {
                  @Override
                  public void run() {
                    if (stopPoll.get() || display.isDisposed()) {
                      return;
                    }
                    if (monitor.isCanceled()) {
                      HQueryProgress.requestCancel();
                    }
                    display.timerExec(150, this);
                  }
                };
            display.asyncExec(
                () -> {
                  if (!display.isDisposed()) {
                    display.timerExec(150, poll);
                  }
                });
            try {
              monitor.beginTask(BaseMessages.getString(PKG, "HPresentationQueryRunner.Task"), 1000);
              layout.run();
            } catch (HException e) {
              if (isCancellation(e)) {
                cancelled.set(true);
              } else {
                throw new InvocationTargetException(e);
              }
            } finally {
              stopPoll.set(true);
              HQueryProgress.clear();
            }
          });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HException("Interrupted while running the presentation query", e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (isCancellation(cause)) {
        cancelled.set(true);
      } else if (cause instanceof HException he) {
        throw he;
      } else {
        throw new HException(cause.getMessage(), cause);
      }
    }
    boolean userCancelled =
        cancelled.get()
            || (dialog.getProgressMonitor() != null && dialog.getProgressMonitor().isCanceled());
    return !userCancelled;
  }

  public static boolean isCancellation(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof HQueryCancelledException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * The presentation shell is still closed while the first query runs. A dialog parented on that
   * shell never appears. Use a shell that is already on screen, preferring the Hop window.
   */
  private static Shell dialogParent(Shell preferred) {
    if (preferred != null && !preferred.isDisposed() && preferred.isVisible()) {
      return preferred;
    }
    Shell hop = hopShell();
    if (hop != null) {
      return hop;
    }
    Display display =
        preferred != null && !preferred.isDisposed() ? preferred.getDisplay() : Display.getCurrent();
    if (display != null && !display.isDisposed()) {
      Shell active = display.getActiveShell();
      if (active != null && !active.isDisposed() && active.isVisible()) {
        return active;
      }
    }
    return preferred != null ? preferred : hop;
  }

  private static Shell hopShell() {
    try {
      HopGui hopGui = HopGui.getInstance();
      if (hopGui == null) {
        return null;
      }
      Shell shell = hopGui.getShell();
      if (shell != null && !shell.isDisposed() && shell.isVisible()) {
        return shell;
      }
    } catch (RuntimeException ignored) {
      // Hop GUI is not running (unit tests, headless render).
    }
    return null;
  }

  private static final class MonitorQueryProgress implements HQueryProgress {
    private final IProgressMonitor monitor;

    private MonitorQueryProgress(IProgressMonitor monitor) {
      this.monitor = monitor;
    }

    @Override
    public void preparing() {
      monitor.subTask(BaseMessages.getString(PKG, "HPresentationQueryRunner.Preparing"));
    }

    @Override
    public void runningQuery() {
      monitor.subTask(BaseMessages.getString(PKG, "HPresentationQueryRunner.Running"));
    }

    @Override
    public void rowsRead(long rows) {
      monitor.subTask(BaseMessages.getString(PKG, "HPresentationQueryRunner.Rows", rows));
      monitor.worked(1);
    }

    @Override
    public void rendering() {
      monitor.subTask(BaseMessages.getString(PKG, "HPresentationQueryRunner.Rendering"));
    }

    @Override
    public boolean isCanceled() {
      return monitor.isCanceled() || HQueryProgress.isCancelRequested();
    }
  }
}
