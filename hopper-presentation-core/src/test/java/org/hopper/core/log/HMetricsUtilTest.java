package org.hopper.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.logging.LogChannel;
import org.junit.jupiter.api.Test;

class HMetricsUtilTest {

  @Test
  void startStopSameCodeProducesLastMsAndSpans() {
    LogChannel log = new LogChannel("metrics-test", true);
    assertTrue(log.isGatheringMetrics());

    HMetricsUtil.start(log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Layout component", "Box");
    try {
      Thread.sleep(15);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    HMetricsUtil.stop(log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Layout component", "Box");

    Long ms = HMetricsUtil.lastMs(log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Box");
    assertNotNull(ms);
    assertTrue(ms >= 10, "expected at least ~10ms, got " + ms);

    Long msAny = HMetricsUtil.lastMs(log, HMetricsUtil.CODE_LAYOUT_COMPONENT);
    assertNotNull(msAny);
    assertTrue(msAny >= 10);

    List<Map<String, Object>> spans = HMetricsUtil.collectSpans(log);
    assertFalse(spans.isEmpty());
    Map<String, Object> last = spans.get(spans.size() - 1);
    assertEquals(HMetricsUtil.CODE_LAYOUT_COMPONENT, last.get("code"));
    assertEquals("Box", last.get("subject"));
    assertTrue(((Number) last.get("ms")).longValue() >= 10);
    assertNotNull(last.get("startMs"));
    assertNotNull(last.get("endMs"));
  }

  @Test
  void buildTimingsSummaryIncludesCoarseTotalsAndTop() {
    LogChannel log = new LogChannel("metrics-summary", true);
    HMetricsUtil.start(log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
    HMetricsUtil.start(log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Layout component", "A");
    HMetricsUtil.stop(log, HMetricsUtil.CODE_LAYOUT_COMPONENT, "Layout component", "A");
    HMetricsUtil.stop(log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
    HMetricsUtil.start(log, HMetricsUtil.CODE_PRESENTATION_RENDER, "Presentation render");
    HMetricsUtil.stop(log, HMetricsUtil.CODE_PRESENTATION_RENDER, "Presentation render");

    Map<String, Object> summary = HMetricsUtil.buildTimingsSummary(log, 10);
    assertNotNull(summary.get("layoutMs"));
    assertTrue(((Number) summary.get("layoutMs")).longValue() >= 0);
    assertTrue(summary.containsKey("top"));
    assertTrue(summary.containsKey("spans"));
    // totalMs must be layout+render, not min→max epoch across history
    long layout = ((Number) summary.get("layoutMs")).longValue();
    long render = ((Number) summary.get("renderMs")).longValue();
    assertEquals(layout + render, ((Number) summary.get("totalMs")).longValue());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> top = (List<Map<String, Object>>) summary.get("top");
    assertFalse(top.isEmpty());
  }

  @Test
  void totalMsDoesNotSpanHistoricalEpochs() throws Exception {
    LogChannel log = new LogChannel("metrics-history", true);
    // First soft-reload (old)
    HMetricsUtil.start(log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
    Thread.sleep(5);
    HMetricsUtil.stop(log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
    Thread.sleep(30);
    // Second soft-reload (current)
    HMetricsUtil.start(log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
    Thread.sleep(5);
    HMetricsUtil.stop(log, HMetricsUtil.CODE_PRESENTATION_LAYOUT, "Presentation layout");
    HMetricsUtil.start(log, HMetricsUtil.CODE_PRESENTATION_RENDER, "Presentation render");
    HMetricsUtil.stop(log, HMetricsUtil.CODE_PRESENTATION_RENDER, "Presentation render");

    Map<String, Object> summary = HMetricsUtil.buildTimingsSummary(log, 20);
    long total = ((Number) summary.get("totalMs")).longValue();
    // Must not include the 30ms gap between reloads
    assertTrue(total < 50, "totalMs inflated by history: " + total);
  }
}
