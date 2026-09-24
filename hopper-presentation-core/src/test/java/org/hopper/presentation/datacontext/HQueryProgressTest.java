package org.hopper.presentation.datacontext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HQueryProgressTest {

  @AfterEach
  void clear() {
    HQueryProgress.clear();
  }

  @Test
  void cancelFromAnotherThreadRunsTheJdbcHook() throws Exception {
    AtomicBoolean cancelled = new AtomicBoolean();
    HQueryProgress.bind(idle());
    HQueryProgress.registerCancel(() -> cancelled.set(true));
    Thread ui = new Thread(HQueryProgress::requestCancel, "cancel-ui");
    ui.start();
    ui.join();
    assertTrue(cancelled.get());
    assertTrue(HQueryProgress.isCancelRequested());
  }

  @Test
  void unboundCancelHookIsIgnored() {
    AtomicBoolean cancelled = new AtomicBoolean();
    HQueryProgress.registerCancel(() -> cancelled.set(true));
    HQueryProgress.requestCancel();
    assertFalse(cancelled.get());
    assertNull(HQueryProgress.current());
  }

  private static HQueryProgress idle() {
    return new HQueryProgress() {
      @Override
      public void preparing() {}

      @Override
      public void runningQuery() {}

      @Override
      public void rowsRead(long rows) {}

      @Override
      public void rendering() {}

      @Override
      public boolean isCanceled() {
        return false;
      }
    };
  }
}
