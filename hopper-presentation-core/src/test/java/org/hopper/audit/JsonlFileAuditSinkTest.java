package org.hopper.audit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.hopper.audit.plugin.JsonlFileAuditSink;
import org.hopper.core.HEnvironment;
import org.hopper.security.HPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JsonlFileAuditSinkTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  public void appendsJsonLines() throws Exception {
    Path file = tempDir.resolve("audit.jsonl");
    JsonlFileAuditSink sink = new JsonlFileAuditSink();
    sink.init(Map.of("path", file.toAbsolutePath().toString(), "append", "true"), new Variables());

    HAuditEvent e1 =
        HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER)
            .actor(HPrincipal.system())
            .actionCode("presentation.render");
    e1.setResourceName("Sales");
    HAuditEvent e2 = HAuditEvent.of(HAuditEventType.METADATA_DELETE).actionCode("presentation.delete");
    e2.setResourceName("Old");

    sink.emit(e1);
    sink.emit(e2);

    List<String> lines = Files.readAllLines(file);
    assertTrue(lines.size() >= 2);
    assertTrue(lines.get(0).contains("PRESENTATION_RENDER"));
    assertTrue(lines.get(0).contains("Sales"));
    assertTrue(lines.get(1).contains("METADATA_DELETE"));
  }
}
