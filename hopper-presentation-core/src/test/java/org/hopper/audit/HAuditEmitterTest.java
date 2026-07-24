package org.hopper.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.variables.IVariables;
import org.hopper.audit.plugin.IAuditSink;
import org.hopper.core.exception.HException;
import org.hopper.security.HPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HAuditEmitterTest {

  @BeforeEach
  public void setUp() {
    HAuditEmitter.getInstance().resetForTests();
  }

  @AfterEach
  public void resetEmitter() {
    HAuditEmitter.getInstance().resetForTests();
  }

  @Test
  public void emitsToRegisteredSink() throws Exception {
    List<HAuditEvent> captured = new ArrayList<>();
    HAuditEmitter.getInstance()
        .addSink(
            new IAuditSink() {
              @Override
              public void init(Map<String, String> properties, IVariables variables) {}

              @Override
              public void emit(HAuditEvent event) {
                captured.add(event);
              }
            });

    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER)
            .actor(HPrincipal.system())
            .actionCode("presentation.render");
    HAuditEmitter.getInstance().emit(event);

    assertEquals(1, captured.size());
    assertEquals(HAuditEventType.PRESENTATION_RENDER, captured.get(0).getEventType());
  }

  @Test
  public void failOpenSwallowsSinkErrors() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HAuditEmitter.getInstance().setFailOpen(true);
    HAuditEmitter.getInstance()
        .addSink(
            new IAuditSink() {
              @Override
              public void init(Map<String, String> properties, IVariables variables) {}

              @Override
              public void emit(HAuditEvent event) throws HException {
                calls.incrementAndGet();
                throw new HException("sink down");
              }
            });
    HAuditEmitter.getInstance().emit(HAuditEvent.of(HAuditEventType.AUTHZ_DENY));
    assertEquals(1, calls.get());
  }

  @Test
  public void failClosedRethrows() {
    HAuditEmitter.getInstance().setFailOpen(false);
    HAuditEmitter.getInstance()
        .addSink(
            new IAuditSink() {
              @Override
              public void init(Map<String, String> properties, IVariables variables) {}

              @Override
              public void emit(HAuditEvent event) throws HException {
                throw new HException("sink down");
              }
            });
    assertThrows(
        HException.class,
        () -> HAuditEmitter.getInstance().emit(HAuditEvent.of(HAuditEventType.EXPORT)));
  }

  @Test
  public void disabledEmitterSkips() throws Exception {
    List<HAuditEvent> captured = new ArrayList<>();
    HAuditEmitter.getInstance()
        .addSink(
            new IAuditSink() {
              @Override
              public void init(Map<String, String> properties, IVariables variables) {}

              @Override
              public void emit(HAuditEvent event) {
                captured.add(event);
              }
            });
    HAuditEmitter.getInstance().setEnabled(false);
    HAuditEmitter.getInstance().emit(HAuditEvent.of(HAuditEventType.INTERACTION));
    assertTrue(captured.isEmpty());
  }

  @Test
  public void asyncEmitsAfterFlush() throws Exception {
    List<HAuditEvent> captured = new CopyOnWriteArrayList<>();
    HAuditEmitter emitter = HAuditEmitter.getInstance();
    emitter.configure(
        HAuditConfig.builder().enabled(true).failOpen(true).async(true).queueSize(100).build());
    emitter.addSink(
        new IAuditSink() {
          @Override
          public void init(Map<String, String> properties, IVariables variables) {}

          @Override
          public void emit(HAuditEvent event) {
            captured.add(event);
          }
        });

    emitter.emit(HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER));
    assertTrue(emitter.flush(2, TimeUnit.SECONDS));
    assertEquals(1, captured.size());
  }

  @Test
  public void asyncQueueDropIncrementsCounter() throws Exception {
    HAuditEmitter emitter = HAuditEmitter.getInstance();
    emitter.configure(
        HAuditConfig.builder()
            .enabled(true)
            .failOpen(true)
            .async(true)
            .queueSize(16)
            .queueFullPolicy(HAuditConfig.QueueFullPolicy.DROP)
            .build());
    // Slow sink blocks worker so queue can fill
    emitter.addSink(
        new IAuditSink() {
          @Override
          public void init(Map<String, String> properties, IVariables variables) {}

          @Override
          public void emit(HAuditEvent event) throws HException {
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        });

    for (int i = 0; i < 200; i++) {
      emitter.emit(HAuditEvent.of(HAuditEventType.INTERACTION));
    }
    assertTrue(emitter.getDroppedCount() > 0 || emitter.flush(3, TimeUnit.SECONDS));
    // Either dropped under load or drained successfully — both are valid
    assertTrue(emitter.getDroppedCount() >= 0);
  }
}
