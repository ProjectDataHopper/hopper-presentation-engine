package org.hopper.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.audit.plugin.IAuditSink;
import org.hopper.core.HEnvironment;
import org.hopper.security.HPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HAuditSinkLoaderTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initEnv() throws Exception {
    HEnvironment.init();
  }

  @BeforeEach
  @AfterEach
  void reset() {
    HAuditEmitter.getInstance().resetForTests();
  }

  @Test
  public void bootstrapRegistersLoggingAndJsonl() throws Exception {
    Path jsonl = tempDir.resolve("boot.jsonl");
    HAuditConfig config =
        HAuditConfig.builder()
            .enabled(true)
            .async(false)
            .bootstrapLogging(true)
            .bootstrapJsonlPath(jsonl.toAbsolutePath().toString())
            .build();

    HAuditSinkLoader.bootstrap(
        HAuditEmitter.getInstance(), config, new MemoryMetadataProvider(), new Variables());

    assertTrue(HAuditEmitter.getInstance().getSinks().size() >= 2);

    HAuditEmitter.getInstance()
        .emit(
            HAuditEvent.of(HAuditEventType.PRESENTATION_OPEN)
                .actor(HPrincipal.system())
                .actionCode("presentation.read"));

    assertTrue(Files.exists(jsonl));
    String content = Files.readString(jsonl);
    assertTrue(content.contains("PRESENTATION_OPEN"));
  }

  @Test
  public void metadataSinkWithEventFilter() throws Exception {
    Path jsonl = tempDir.resolve("meta.jsonl");
    MemoryMetadataProvider provider = new MemoryMetadataProvider();

    HAuditSinkMeta meta = new HAuditSinkMeta("renders-only", "JsonlFileAuditSink");
    meta.setEnabled(true);
    meta.setEventTypes(List.of("PRESENTATION_RENDER"));
    meta.setProperties(List.of(new HAuditSinkProperty("path", jsonl.toAbsolutePath().toString())));
    provider.getSerializer(HAuditSinkMeta.class).save(meta);

    HAuditConfig config =
        HAuditConfig.builder().enabled(true).async(false).bootstrapLogging(false).build();
    HAuditSinkLoader.bootstrap(HAuditEmitter.getInstance(), config, provider, new Variables());

    assertEquals(1, HAuditEmitter.getInstance().getSinks().size());

    HAuditEmitter.getInstance().emit(HAuditEvent.of(HAuditEventType.AUTHZ_DENY));
    HAuditEmitter.getInstance()
        .emit(HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER).actionCode("presentation.render"));

    assertTrue(Files.exists(jsonl));
    String content = Files.readString(jsonl);
    assertTrue(content.contains("PRESENTATION_RENDER"));
    assertTrue(!content.contains("AUTHZ_DENY"));
  }

  @Test
  public void createSinkFallbackWithoutRegistryPlugin() throws Exception {
    HAuditSinkMeta meta = new HAuditSinkMeta("log", "LoggingAuditSink");
    IAuditSink sink = HAuditSinkLoader.createSink(meta, new Variables());
    List<HAuditEvent> captured = new CopyOnWriteArrayList<>();
    // just ensure it does not throw
    sink.emit(HAuditEvent.of(HAuditEventType.EXPORT));
    assertTrue(sink.accepts(HAuditEvent.of(HAuditEventType.EXPORT)));
  }
}
