package org.hopper.presentation.component.types.pictorial;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.StepQuantization;

class HPictorialSeriesTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void testSeriesGetImageForPercentageNearest() {
    HPictorialSeries series = new HPictorialSeries("beer-gauge", "Beer glass series", RenderMode.STEP_IMAGES);
    series.setStepQuantization(StepQuantization.NEAREST);

    Map<String, String> map = new LinkedHashMap<>();
    map.put("0", "/assets/step_0.png");
    map.put("50", "/assets/step_50.png");
    map.put("100", "/assets/step_100.png");
    series.setImageMap(map);

    assertEquals("/assets/step_0.png", series.getImageForPercentage(10.0));
    assertEquals("/assets/step_50.png", series.getImageForPercentage(45.0));
    assertEquals("/assets/step_100.png", series.getImageForPercentage(90.0));
  }

  @Test
  void testSeriesGetImageForPercentageFloor() {
    HPictorialSeries series = new HPictorialSeries("beer-gauge", "Beer glass series", RenderMode.STEP_IMAGES);
    series.setStepQuantization(StepQuantization.FLOOR);

    Map<String, String> map = new LinkedHashMap<>();
    map.put("0", "/assets/step_0.png");
    map.put("50", "/assets/step_50.png");
    map.put("100", "/assets/step_100.png");
    series.setImageMap(map);

    assertEquals("/assets/step_0.png", series.getImageForPercentage(49.0));
    assertEquals("/assets/step_50.png", series.getImageForPercentage(50.0));
    assertEquals("/assets/step_50.png", series.getImageForPercentage(99.0));
  }

  @Test
  void testSignedStepsBrokenAndOverflow() {
    HPictorialSeries series =
        new HPictorialSeries("beer-growth", "Growth glasses", RenderMode.STEP_IMAGES);
    series.setStepQuantization(StepQuantization.NEAREST);
    Map<String, String> map = new LinkedHashMap<>();
    map.put("-100", "/a/broken.jpg");
    map.put("-50", "/a/half-broken.jpg");
    map.put("0", "/a/empty.jpg");
    map.put("100", "/a/full.jpg");
    map.put("150", "/a/over.jpg");
    map.put("200", "/a/flood.jpg");
    series.setImageMap(map);

    assertEquals("/a/broken.jpg", series.getImageForPercentage(-100));
    assertEquals("/a/broken.jpg", series.getImageForPercentage(-90));
    assertEquals("/a/half-broken.jpg", series.getImageForPercentage(-50));
    assertEquals("/a/full.jpg", series.getImageForPercentage(100));
    assertEquals("/a/flood.jpg", series.getImageForPercentage(200));
    assertEquals("/a/flood.jpg", series.getImageForPercentage(250)); // nearest overflow end
    assertEquals("/a/over.jpg", series.getImageForPercentage(140));
  }

  @Test
  void testOverHundredDoesNotPickFullGlassWhenOverflowKeyExists() {
    // Real demo bug: 150% was equidistant from 100 and 200 → old tie-break picked 100
    Map<String, String> map = new LinkedHashMap<>();
    map.put("0", "/a/empty.jpg");
    map.put("50", "/a/half.jpg");
    map.put("100", "/a/full.jpg");
    map.put("-100", "/a/broken.jpg");
    map.put("200", "/a/overflow.jpg");

    assertEquals(
        "/a/overflow.jpg",
        HPictorialSeries.resolveStepPath(map, 150.0, StepQuantization.NEAREST));
    assertEquals(
        "/a/overflow.jpg",
        HPictorialSeries.resolveStepPath(map, 120.0, StepQuantization.NEAREST));
    assertEquals(
        "/a/overflow.jpg",
        HPictorialSeries.resolveStepPath(map, 199.0, StepQuantization.NEAREST));
    // Still uses full for on-target
    assertEquals(
        "/a/full.jpg", HPictorialSeries.resolveStepPath(map, 100.0, StepQuantization.NEAREST));
    // Negative never picks empty when broken key exists
    assertEquals(
        "/a/broken.jpg",
        HPictorialSeries.resolveStepPath(map, -50.0, StepQuantization.NEAREST));
  }
}
