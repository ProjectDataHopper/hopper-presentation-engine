package org.hopper.presentation.component.types.pictorial;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.ClipDirection;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.StepQuantization;

class HPictorialChartComponentTest {

  @TempDir File tempDir;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void testMetadataCodecSerialization() throws Exception {
    HPictorialChartComponent component = new HPictorialChartComponent();
    component.setRenderMode(RenderMode.STEP_IMAGES);
    component.setCategoryColumn("region");
    component.setValueColumn("percentage");
    component.setDomainMin("0");
    component.setDomainMax("100");
    component.setStepQuantization(StepQuantization.NEAREST);
    component.setSeriesName("beer-series");
    component.setShowCategoryLabel(true);

    Map<String, String> map = new LinkedHashMap<>();
    map.put("0", "images/beer_0.png");
    map.put("50", "images/beer_50.png");
    map.put("100", "images/beer_100.png");
    component.setImageMap(map);

    HComponent wrapper = new HComponent();
    wrapper.setName("BeerGauge");
    wrapper.setComponent(component);

    String json = org.hopper.core.HJson.createMapper().writeValueAsString(wrapper);
    assertNotNull(json);
    assertTrue(json.contains("HPictorialChartComponent"));
    assertTrue(json.contains("beer_50.png"));
    assertTrue(json.contains("beer-series"));
    assertTrue(json.contains("region"));
  }

  @Test
  void testClippedLayersProperties() {
    HPictorialChartComponent component = new HPictorialChartComponent();
    component.setRenderMode(RenderMode.CLIPPED_LAYERS);
    component.setBackgroundImage("images/glass_empty.png");
    component.setFillImage("images/glass_full.png");
    component.setClipDirection(ClipDirection.BOTTOM_TO_TOP);

    assertEquals(RenderMode.CLIPPED_LAYERS, component.getRenderMode());
    assertEquals("images/glass_empty.png", component.getBackgroundImage());
    assertEquals("images/glass_full.png", component.getFillImage());
    assertEquals(ClipDirection.BOTTOM_TO_TOP, component.getClipDirection());
  }

  @Test
  void testClonePreservesSeriesAndCategory() {
    HPictorialChartComponent original = new HPictorialChartComponent();
    original.setSeriesName("beer-series");
    original.setCategoryColumn("region");
    original.setRenderMode(RenderMode.CLIPPED_LAYERS);
    original.setValueColumn("metric");
    original.setBackgroundImage("bg.png");
    original.setFillImage("fill.png");
    original.setShowCategoryLabel(true);

    HPictorialChartComponent clone = original.clone();
    assertEquals("beer-series", clone.getSeriesName());
    assertEquals("region", clone.getCategoryColumn());
    assertTrue(clone.isShowCategoryLabel());
    assertEquals(original.getRenderMode(), clone.getRenderMode());
    assertEquals(original.getValueColumn(), clone.getValueColumn());
    assertEquals(original.getBackgroundImage(), clone.getBackgroundImage());
    assertEquals(original.getFillImage(), clone.getFillImage());
  }

  @Test
  void testResolveImagePathVariableAndLegacyHttp() {
    Variables vars = new Variables();
    vars.setVariable("HOPPER_METADATA_PATH", "/meta/root");

    assertEquals(
        "/meta/root/assets/pictorial-series/beer/step_50.png",
        HPictorialChartComponent.resolveImagePath(
            "${HOPPER_METADATA_PATH}/assets/pictorial-series/beer/step_50.png", vars));

    assertEquals(
        "/meta/root/assets/pictorial-series/beer/step_0.png",
        HPictorialChartComponent.resolveImagePath(
            "/hopper/api/assets/pictorial-series/beer/step_0.png", vars));
  }

  @Test
  void testResolveColumnsPreferPctNotYear() throws Exception {
    org.apache.hop.core.row.RowMeta meta = new org.apache.hop.core.row.RowMeta();
    meta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaInteger("year"));
    meta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("region"));
    meta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaNumber("pct_of_target"));

    HPictorialChartComponent component = new HPictorialChartComponent();
    // Blank config → auto-pick
    assertEquals("region", component.resolveCategoryColumn(meta));
    assertEquals("pct_of_target", component.resolveValueColumn(meta));

    component.setCategoryColumn("region");
    component.setValueColumn("pct_of_target");
    assertEquals("region", component.resolveCategoryColumn(meta));
    assertEquals("pct_of_target", component.resolveValueColumn(meta));
  }

  @Test
  void testLoadImageWithResolvedMetadataPath() throws Exception {
    File assets = new File(tempDir, "assets/pictorial-series/beer");
    assertTrue(assets.mkdirs());
    File step = new File(assets, "step_50.png");
    BufferedImage img = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
    ImageIO.write(img, "png", step);

    System.setProperty("HOPPER_METADATA_PATH", tempDir.getAbsolutePath());
    try {
      Variables vars = new Variables();
      vars.setVariable("HOPPER_METADATA_PATH", tempDir.getAbsolutePath());

      HPictorialChartComponent component = new HPictorialChartComponent();
      BufferedImage loaded =
          component.loadImage(
              "${HOPPER_METADATA_PATH}/assets/pictorial-series/beer/step_50.png", vars);
      assertNotNull(loaded);
      assertEquals(20, loaded.getWidth());
      assertEquals(30, loaded.getHeight());

      BufferedImage loadedLegacy =
          component.loadImage("/hopper/api/assets/pictorial-series/beer/step_50.png", vars);
      assertNotNull(loadedLegacy);
      assertEquals(20, loadedLegacy.getWidth());
    } finally {
      System.clearProperty("HOPPER_METADATA_PATH");
    }
  }
}
