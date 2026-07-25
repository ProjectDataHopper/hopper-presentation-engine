package org.hopper.presentation.component.types.chart;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDimension;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFact;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.BasePresentationUtil;

/**
 * Company Ships Detail regression: after clone / soft re-render, pivotMapList can be null if no
 * rows were pivoted. Bar/line render must not NPE on pivotMapList.iterator().
 */
class HBarChartPivotNullSafetyTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void renderWithNullPivotMap_doesNotThrow() throws Exception {
    HBarChartComponent chart = new HBarChartComponent("any-connector");
    chart.setHorizontalDimensions(
        Arrays.asList(
            new HDimension(
                "ship_type", "Type", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP)));
    chart.setFacts(
        Arrays.asList(
            new HFact(
                "capacity_teu",
                "Capacity",
                HHorizontalAlignment.RIGHT,
                HVerticalAlignment.MIDDLE,
                AggregationMethod.SUM,
                "0")));
    chart.setTitle("Vessel Capacity");
    // Simulate clone / JSON load: configured but never processSourceData
    assertNull(chart.getPivotMapList());
    assertTrue(chart.isIncompleteChartConfig());

    HPresentation presentation = new HPresentation();
    presentation.setName("pivot-null-safety");
    presentation.setDefaultThemeName("Default");
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(HTheme.class).save(HTheme.getDefault());

    HComponent wrapper = new HComponent("ship-type-chart", chart);
    wrapper.setLayout(new HLayoutBuilder().all(10).build());
    presentation.getPages().add(HPage.getA4(false));
    presentation.getPages().get(0).getComponents().add(wrapper);

    PresentationRenderContext rc = new PresentationRenderContext(presentation, provider);
    // Full layout+render without a real connector: processSourceData returns early, pivot stays
    // null — render must use incomplete placeholder, not NPE.
    assertDoesNotThrow(
        () -> {
          HLayoutResults layout =
              presentation.doLayout(new LoggingObject("pivot-null"), rc, provider, List.of());
          presentation.render(layout, provider, rc);
        });
  }

  @Test
  void processSourceData_zeroRows_doesNotThrowOnRender() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    // Empty list: stream finishes with no pivotRow calls
    HListConnector list = new HListConnector("color", List.of());
    HConnector connector = new HConnector("empty-colors", list);
    provider.getSerializer(HConnector.class).save(connector);
    provider.getSerializer(HTheme.class).save(HTheme.getDefault());

    HBarChartComponent chart = new HBarChartComponent("empty-colors");
    chart.setHorizontalDimensions(
        Arrays.asList(
            new HDimension("color", "Color", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP)));
    chart.setFacts(
        Arrays.asList(
            new HFact(
                "color",
                "N",
                HHorizontalAlignment.RIGHT,
                HVerticalAlignment.MIDDLE,
                AggregationMethod.COUNT,
                "0")));

    HPresentation presentation = new HPresentation();
    presentation.setName("zero-rows-chart");
    presentation.setDefaultThemeName("Default");
    HComponent wrapper = new HComponent("chart", chart);
    wrapper.setLayout(new HLayoutBuilder().all(10).build());
    presentation.getPages().add(HPage.getA4(false));
    presentation.getPages().get(0).getComponents().add(wrapper);

    PresentationRenderContext rc = new PresentationRenderContext(presentation, provider);
    // Zero rows must not NPE during layout/render (empty chart or incomplete placeholder)
    assertDoesNotThrow(
        () -> {
          HLayoutResults layout =
              presentation.doLayout(new LoggingObject("zero-rows"), rc, provider, List.of());
          presentation.render(layout, provider, rc);
        });
  }

  @Test
  void cloneClearsPivot_reprocessRepopulates() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    HListConnector list =
        new HListConnector("color", Arrays.asList("Red", "Green", "Blue"));
    HConnector connector = new HConnector("colors", list);
    provider.getSerializer(HConnector.class).save(connector);
    provider.getSerializer(HTheme.class).save(HTheme.getDefault());

    HBarChartComponent original = new HBarChartComponent("colors");
    original.setHorizontalDimensions(
        Arrays.asList(
            new HDimension("color", "Color", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP)));
    original.setFacts(
        Arrays.asList(
            new HFact(
                "color",
                "N",
                HHorizontalAlignment.RIGHT,
                HVerticalAlignment.MIDDLE,
                AggregationMethod.COUNT,
                "0")));

    HPresentation p1 = new HPresentation();
    p1.setName("orig");
    p1.setDefaultThemeName("Default");
    HComponent w1 = new HComponent("c", original);
    w1.setLayout(new HLayoutBuilder().all(5).build());
    p1.getPages().add(HPage.getA4(false));
    p1.getPages().get(0).getComponents().add(w1);

    PresentationRenderContext rc1 = new PresentationRenderContext(p1, provider);
    p1.doLayout(new LoggingObject("orig"), rc1, provider, List.of());
    assertNotNull(original.getPivotMapList());

    // Clone as editor preview / HComponent copy does
    HBarChartComponent clone = original.clone();
    assertNull(clone.getPivotMapList(), "clone must clear transient pivot state");

    HPresentation p2 = new HPresentation();
    p2.setName("clone");
    p2.setDefaultThemeName("Default");
    HComponent w2 = new HComponent("c", clone);
    w2.setLayout(new HLayoutBuilder().all(5).build());
    p2.getPages().add(HPage.getA4(false));
    p2.getPages().get(0).getComponents().add(w2);

    PresentationRenderContext rc2 = new PresentationRenderContext(p2, provider);
    assertDoesNotThrow(
        () -> {
          HLayoutResults layout =
              p2.doLayout(new LoggingObject("clone"), rc2, provider, List.of());
          p2.render(layout, provider, rc2);
        });
    assertNotNull(clone.getPivotMapList());
  }
}
