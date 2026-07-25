package org.hopper.presentation.component.types.chart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;
import org.hopper.util.BasePresentationUtil;

class HGanttChartComponentTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void formatDuration_scales() {
    assertEquals("0ms", HGanttChartComponent.formatDuration(0));
    assertEquals("250ms", HGanttChartComponent.formatDuration(250));
    assertTrue(HGanttChartComponent.formatDuration(1500).endsWith("s"));
    assertTrue(HGanttChartComponent.formatDuration(65_000).contains("m"));
  }

  @Test
  void ganttDetails_recomputeBounds() {
    GanttDetails d = new GanttDetails();
    d.tasks.add(new GanttTask("A", 10, 50, null, null));
    d.tasks.add(new GanttTask("B", 0, 20, null, null));
    d.recomputeBounds();
    assertEquals(0L, d.minStart);
    assertEquals(50L, d.maxEnd);
    assertEquals(50L, d.span());
  }

  @Test
  void durationOnly_waterfallLayout() throws Exception {
    // Sequential bars from duration column when start is blank
    HGanttChartComponent gantt = new HGanttChartComponent();
    gantt.setTitle("Durations");
    gantt.setInlineTasks(null);
    // Seed via embedded simulating duration-only result
    gantt.setEmbeddedTasks(
        Arrays.asList(
            new GanttTask("A", 0, 100, "G", "a"),
            new GanttTask("B", 100, 150, "G", "b")));
    HComponent wrapper = new HComponent("DurGantt", gantt);
    wrapper.setLayout(HLayout.fullPage());
    String svg = wrapper.getSvgXml(400, 200, new MemoryMetadataProvider());
    assertTrue(svg.contains("A") || svg.contains("Durations"), svg);
  }

  @Test
  void embeddedTasks_roundTripThroughHopMetadata() throws Exception {
    // Hop JSON metadata supports long but not double; regression for start/end persistence.
    HGanttChartComponent gantt = new HGanttChartComponent();
    gantt.setTitle("Round trip");
    gantt.setEmbeddedTasks(
        Arrays.asList(
            new GanttTask("Layout", 0L, 120L, "Layout", "layout"),
            new GanttTask("Render", 80L, 200L, "Render", "render")));

    HPresentation presentation = new HPresentation();
    presentation.setName("Gantt RoundTrip");
    HPage page = HPage.getA4(false);
    page.getComponents().add(new HComponent("Gantt", gantt));
    presentation.getPages().add(page);

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HPresentation.class).save(presentation);

    HPresentation loaded = provider.getSerializer(HPresentation.class).load("Gantt RoundTrip");
    assertNotNull(loaded);
    HComponent loadedComp = loaded.getPages().get(0).getComponents().get(0);
    assertTrue(loadedComp.getComponent() instanceof HGanttChartComponent);
    HGanttChartComponent loadedGantt = (HGanttChartComponent) loadedComp.getComponent();
    assertNotNull(loadedGantt.getEmbeddedTasks());
    assertEquals(2, loadedGantt.getEmbeddedTasks().size());
    assertEquals(0L, loadedGantt.getEmbeddedTasks().get(0).getStart());
    assertEquals(120L, loadedGantt.getEmbeddedTasks().get(0).getEnd());
    assertEquals("Layout", loadedGantt.getEmbeddedTasks().get(0).getLabel());
    assertEquals(80L, loadedGantt.getEmbeddedTasks().get(1).getStart());
    assertEquals(200L, loadedGantt.getEmbeddedTasks().get(1).getEnd());
  }

  @Test
  void inlineTasks_renderSvg() throws Exception {
    HGanttChartComponent gantt = new HGanttChartComponent();
    gantt.setTitle("Refresh timings");
    gantt.setShowingTitle(true);
    gantt.setShowingAxisTicks(true);
    gantt.setShowingDurationLabels(true);
    gantt.setInlineTasks(
        Arrays.asList(
            new GanttTask("Connector SQL", 0, 120, "Connector", "sql"),
            new GanttTask("Layout Line chart", 20, 80, "Layout", "layout"),
            new GanttTask("Render page", 80, 200, "Render", "render")));

    HComponent wrapper = new HComponent("TimingsGantt", gantt);
    wrapper.setLayout(HLayout.fullPage());

    String svg =
        wrapper.getSvgXml(640, 280, new MemoryMetadataProvider(), null, null);
    assertNotNull(svg);
    assertTrue(svg.contains("<svg") || svg.contains("<svg:"), "expected SVG root");
    assertTrue(svg.length() > 200, "SVG too small: " + svg.length());
    // Labels should appear as text nodes
    assertTrue(svg.contains("Connector SQL") || svg.contains("Timings"), svg);
  }

  @Test
  void embeddedTasks_preferredOverInline() throws Exception {
    HGanttChartComponent gantt = new HGanttChartComponent();
    gantt.setEmbeddedTasks(
        Arrays.asList(new GanttTask("Embedded task", 0, 50, "Layout", "e")));
    gantt.setInlineTasks(
        Arrays.asList(new GanttTask("Inline only", 0, 50, "Layout", "i")));
    HComponent wrapper = new HComponent("EmbedGantt", gantt);
    wrapper.setLayout(HLayout.fullPage());
    String svg = wrapper.getSvgXml(400, 200, new MemoryMetadataProvider());
    assertTrue(svg.contains("Embedded task"), svg);
    assertFalse(svg.contains("Inline only"), svg);
  }

  @Test
  void emptyGantt_stillRenders() throws Exception {
    HGanttChartComponent gantt = new HGanttChartComponent();
    gantt.setInlineTasks(Collections.emptyList());
    // No connector either
    HComponent wrapper = new HComponent("EmptyGantt", gantt);
    wrapper.setLayout(new HLayoutBuilder().all(10).build());

    String svg = wrapper.getSvgXml(400, 200, new MemoryMetadataProvider());
    assertNotNull(svg);
    assertTrue(svg.contains("<svg") || svg.contains("<svg:"));
  }

  @Test
  void clone_copiesColumns() {
    HGanttChartComponent a = new HGanttChartComponent("src");
    a.setTaskColumn("task");
    a.setStartColumn("start");
    a.setEndColumn("end");
    a.setTitle("T");
    HGanttChartComponent b = a.clone();
    assertEquals("src", b.getSourceConnectorName());
    assertEquals("task", b.getTaskColumn());
    assertEquals("start", b.getStartColumn());
    assertEquals("end", b.getEndColumn());
    assertEquals("T", b.getTitle());
    assertFalse(a == b);
  }

  @Test
  void pluginRegistered() throws Exception {
    // Touch environment so plugins load
    assertNotNull(new HGanttChartComponent().getPluginId());
    assertEquals("HGanttChartComponent", new HGanttChartComponent().getPluginId());
  }
}
