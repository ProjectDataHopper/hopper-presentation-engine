package org.hopper.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class HAuditRedactorTest {

  @Test
  public void redactsNamedParameters() {
    HAuditConfig config =
        HAuditConfig.builder()
            .redactParameterValues(false)
            .redactParameterNames(Set.of("ssn", "email"))
            .build();
    HAuditRedactor redactor = new HAuditRedactor(config);

    HAuditEvent event = HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("REGION", Map.of("value", "EMEA", "redacted", false));
    params.put("ssn", Map.of("value", "123-45-6789", "redacted", false));
    event.setDesign(Map.of("parameters", params));

    redactor.redact(event);

    @SuppressWarnings("unchecked")
    Map<String, Object> out = (Map<String, Object>) event.getDesign().get("parameters");
    @SuppressWarnings("unchecked")
    Map<String, Object> region = (Map<String, Object>) out.get("REGION");
    @SuppressWarnings("unchecked")
    Map<String, Object> ssn = (Map<String, Object>) out.get("ssn");
    assertEquals("EMEA", region.get("value"));
    assertEquals(HAuditRedactor.REDACTED, ssn.get("value"));
    assertEquals(true, ssn.get("redacted"));
  }

  @Test
  public void stripsSqlTextWhenDisabled() {
    HAuditConfig config = HAuditConfig.builder().includeSqlText(false).build();
    HAuditRedactor redactor = new HAuditRedactor(config);

    HAuditEvent event = HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER);
    Map<String, Object> run = new LinkedHashMap<>();
    run.put("statementText", "select * from secret");
    run.put("statementFingerprint", "sha256:abc");
    event.setExecution(Map.of("connectorRuns", List.of(run)));

    redactor.redact(event);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> runs =
        (List<Map<String, Object>>) event.getExecution().get("connectorRuns");
    assertNull(runs.get(0).get("statementText"));
    assertEquals("sha256:abc", runs.get(0).get("statementFingerprint"));
  }

  @Test
  public void truncatesLongSql() {
    HAuditConfig config =
        HAuditConfig.builder().includeSqlText(true).maxStatementLength(10).build();
    HAuditRedactor redactor = new HAuditRedactor(config);
    HAuditEvent event = HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER);
    Map<String, Object> run = new LinkedHashMap<>();
    run.put("statementText", "0123456789ABCDEF");
    event.setExecution(Map.of("connectorRuns", List.of(run)));

    redactor.redact(event);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> runs =
        (List<Map<String, Object>>) event.getExecution().get("connectorRuns");
    String text = (String) runs.get(0).get("statementText");
    assertTrue(text.startsWith("0123456789"));
    assertTrue(text.endsWith("…"));
  }
}
