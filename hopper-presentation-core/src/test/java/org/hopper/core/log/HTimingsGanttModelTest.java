package org.hopper.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.component.types.chart.GanttTask;

class HTimingsGanttModelTest {

  @Test
  void fromTimings_relativeTimelineAndLabels() {
    Map<String, Object> timings = new LinkedHashMap<>();
    List<Map<String, Object>> spans = new ArrayList<>();
    spans.add(span("HOPPER_CONNECTOR_RETRIEVE", "EDW", "Retrieve rows", 1000, 1200));
    spans.add(span("HOPPER_LAYOUT_COMPONENT", "Chart", "Layout component", 1100, 1300));
    spans.add(span("HOPPER_PRESENTATION_RENDER", null, "Presentation render", 1300, 1500));
    timings.put("spans", spans);
    timings.put("layoutMs", 300L);
    timings.put("renderMs", 200L);

    List<GanttTask> tasks = HTimingsGanttModel.fromTimings(timings, null, 40);
    assertEquals(3, tasks.size());
    // Relative to origin 1000
    assertEquals(0L, tasks.get(0).getStart());
    assertEquals(200L, tasks.get(0).getEnd());
    assertTrue(tasks.get(0).getLabel().contains("EDW") || tasks.get(0).getLabel().contains("Retrieve"));
    assertEquals("Connector", tasks.get(0).getGroup());
    assertEquals("Layout", tasks.get(1).getGroup());
    assertEquals("Render", tasks.get(2).getGroup());
  }

  @Test
  void fromTimings_appendsClientPhases() {
    Map<String, Object> timings = new LinkedHashMap<>();
    List<Map<String, Object>> spans = new ArrayList<>();
    spans.add(span("HOPPER_PRESENTATION_LAYOUT", null, "Layout", 0, 100));
    timings.put("spans", spans);

    Map<String, Object> client = new HashMap<>();
    client.put("xhrMs", 50);
    client.put("paintMs", 10);

    List<GanttTask> tasks = HTimingsGanttModel.fromTimings(timings, client, 40);
    assertTrue(tasks.size() >= 3);
    boolean hasClient = tasks.stream().anyMatch(t -> "Client".equals(t.getGroup()));
    assertTrue(hasClient);
  }

  @Test
  void fromTimings_capsBars() {
    Map<String, Object> timings = new LinkedHashMap<>();
    List<Map<String, Object>> spans = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      spans.add(span("HOPPER_LAYOUT_COMPONENT", "C" + i, "Layout", i * 10L, i * 10L + 5));
    }
    timings.put("spans", spans);
    List<GanttTask> tasks = HTimingsGanttModel.fromTimings(timings, null, 10);
    assertEquals(10, tasks.size());
  }

  @Test
  void emptyInput_emptyList() {
    assertTrue(HTimingsGanttModel.fromTimings(null, null, 10).isEmpty());
    assertTrue(HTimingsGanttModel.fromTimings(new HashMap<>(), null, 10).isEmpty());
  }

  @Test
  void groupForCode() {
    assertEquals("Connector", HTimingsGanttModel.groupForCode("HOPPER_CONNECTOR_RETRIEVE"));
    assertEquals("Layout", HTimingsGanttModel.groupForCode("HOPPER_LAYOUT_COMPONENT"));
    assertEquals("Render", HTimingsGanttModel.groupForCode("HOPPER_PRESENTATION_RENDER"));
    assertEquals("Other", HTimingsGanttModel.groupForCode("FOO"));
  }

  private static Map<String, Object> span(
      String code, String subject, String description, long start, long end) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("subject", subject);
    m.put("description", description);
    m.put("startMs", start);
    m.put("endMs", end);
    m.put("ms", end - start);
    return m;
  }
}
