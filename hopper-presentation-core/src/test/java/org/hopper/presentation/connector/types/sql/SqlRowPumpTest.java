package org.hopper.presentation.connector.types.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.hopper.core.exception.HQueryCancelledException;
import org.hopper.presentation.datacontext.HQueryProgress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SqlRowPumpTest {

  @AfterEach
  void clearProgress() {
    HQueryProgress.clear();
  }

  @Test
  void readsUntilTheCursorEnds() throws Exception {
    AtomicInteger index = new AtomicInteger();
    Object[][] rows = {new Object[] {1}, new Object[] {2}, new Object[] {3}};
    List<Object> seen = new ArrayList<>();
    long count =
        SqlRowPump.pump(
            () -> {
              int at = index.getAndIncrement();
              return at < rows.length ? rows[at] : null;
            },
            row -> seen.add(row[0]));
    assertEquals(3, count);
    assertEquals(List.of(1, 2, 3), seen);
  }

  @Test
  void cancelStopsBeforeTheNextRow() {
    AtomicBoolean cancel = new AtomicBoolean();
    HQueryProgress.bind(
        new HQueryProgress() {
          @Override
          public void preparing() {}

          @Override
          public void runningQuery() {}

          @Override
          public void rowsRead(long rows) {
            if (rows >= 1) {
              cancel.set(true);
            }
          }

          @Override
          public void rendering() {}

          @Override
          public boolean isCanceled() {
            return cancel.get();
          }
        });
    AtomicInteger index = new AtomicInteger();
    List<Integer> seen = new ArrayList<>();
    assertThrows(
        HQueryCancelledException.class,
        () ->
            SqlRowPump.pump(
                () -> {
                  int at = index.getAndIncrement();
                  return at < 5 ? new Object[] {at} : null;
                },
                row -> seen.add((Integer) row[0])));
    assertEquals(List.of(0), seen);
  }

  @Test
  void blockedReadBecomesCancelWhenTheFlagIsSet() {
    AtomicBoolean hook = new AtomicBoolean();
    HQueryProgress.bind(
        new HQueryProgress() {
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
        });
    HQueryProgress.registerCancel(() -> hook.set(true));
    HQueryProgress.requestCancel();
    assertTrue(hook.get());
    assertThrows(
        HQueryCancelledException.class,
        () ->
            SqlRowPump.pump(
                () -> {
                  throw new IllegalStateException("statement cancelled");
                },
                row -> {}));
  }
}
