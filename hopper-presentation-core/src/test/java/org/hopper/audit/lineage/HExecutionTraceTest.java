package org.hopper.audit.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.HAuditOutcome;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.hopper.security.HSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HExecutionTraceTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @AfterEach
  void clear() {
    HSecurityContext.clear();
  }

  @Test
  void fingerprintIsStable() {
    String a = HExecutionTrace.fingerprintStatement("SELECT  *\nFROM customers");
    String b = HExecutionTrace.fingerprintStatement("select * from customers");
    assertNotNull(a);
    assertEquals(a, b);
    assertTrue(a.startsWith("sha256:"));
  }

  @Test
  void sampleDataStreamingRecordsRowCountAndPlugin() throws Exception {
    HExecutionTrace trace = HExecutionTrace.create();
    HPresentation presentation = new HPresentation();
    presentation.setName("lineage-test");
    presentation.getPages().add(HPage.getA4(false));

    PresentationDataContext ctx =
        new PresentationDataContext(presentation, new MemoryMetadataProvider());
    ctx.setExecutionTrace(trace);

    HSampleDataConnector sample = new HSampleDataConnector(7);
    HConnector connector = new HConnector("sample-rows", sample);
    List<?> rows = connector.retrieveRows(ctx);

    assertEquals(7, rows.size());
    assertEquals(1, trace.getConnectorRuns().size());
    HConnectorRun run = trace.getConnectorRuns().get(0);
    assertEquals("sample-rows", run.getConnectorName());
    assertEquals("SampleDataConnector", run.getPluginId());
    assertEquals(7, run.getRowCount());
    assertEquals(HAuditOutcome.SUCCESS, run.getOutcome());
  }

  @Test
  void usageAuditPresentationRenderIncludesDesignAndExecution() {
    HSecurityContext.setPrincipal(
        HPrincipal.builder()
            .username("alice")
            .role(HRole.VIEWER.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build());

    HPresentation presentation = new HPresentation();
    presentation.setName("Sales");
    presentation.setDescription("Sales board");
    presentation.getPages().add(HPage.getA4(false));

    HExecutionTrace trace = HExecutionTrace.create();
    HConnectorRun run = trace.beginConnectorRun("SqlConnector", null);
    run.setConnectorName("sales-sql");
    run.setDatabaseConnectionName("edw");
    run.setStatementText("select 1");
    run.setStatementFingerprint(HExecutionTrace.fingerprintStatement("select 1"));
    run.incrementRowCount();
    run.completeSuccess();
    trace.setLayoutMs(10);
    trace.setRenderMs(5);
    trace.setPageCount(1);
    trace.finishSuccess();

    HAuditEvent event =
        HUsageAudit.presentationRender(
            presentation, List.of(new HParameter("REGION", "EMEA")), trace, "render-id-1");

    assertEquals(HAuditEventType.PRESENTATION_RENDER, event.getEventType());
    assertEquals("alice", event.getActorUsername());
    assertEquals("Sales", event.getResourceName());
    assertEquals("render-id-1", event.getRenderId());
    assertFalse(event.getDesign().isEmpty());
    assertEquals("Sales", event.getDesign().get("presentationName"));
    @SuppressWarnings("unchecked")
    List<String> paramNames = (List<String>) event.getDesign().get("parameterNames");
    assertTrue(paramNames.contains("REGION"));
    @SuppressWarnings("unchecked")
    Map<String, Object> execution = event.getExecution();
    assertEquals(1, ((List<?>) execution.get("connectorRuns")).size());
    assertEquals(HAuditOutcome.SUCCESS, event.getOutcome());
  }

  @Test
  void noopTraceDoesNotCollectRuns() {
    HExecutionTrace noop = HExecutionTrace.noop();
    assertTrue(noop.isNoop());
    HConnectorRun run = noop.beginConnectorRun("x", null);
    assertNotNull(run);
    assertTrue(noop.getConnectorRuns().isEmpty());
  }
}
