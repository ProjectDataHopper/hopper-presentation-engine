package org.hopper.presentation.component.types.pictorial;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.GenerationRequest;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;
import org.hopper.presentation.component.types.pictorial.HPictorialSeries;

class HAiImageGeneratorServiceTest {

  @TempDir File tempDir;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void testGenerateStepImagesAreCompactJpeg() throws Exception {
    HPictorialChartComponent component = new HPictorialChartComponent();
    GenerationRequest req = new GenerationRequest();
    req.renderMode = RenderMode.STEP_IMAGES;
    req.stepSize = 50;
    req.width = 200;
    req.height = 300;
    req.outputDirectory = tempDir;
    req.relativeAssetUrlPrefix = "${HOPPER_METADATA_PATH}/assets/test";
    req.outputFormat = "jpeg";
    req.maxBytes = 100_000;

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    service.generateAssets(component, req);

    assertFalse(component.getImageMap().isEmpty());
    for (String path : component.getImageMap().values()) {
      assertTrue(path.endsWith(".jpg"), "expected jpeg path: " + path);
    }
    File[] files = tempDir.listFiles((d, n) -> n.startsWith("step_") && n.endsWith(".jpg"));
    assertNotNull(files);
    assertTrue(files.length >= 3);
    for (File f : files) {
      assertTrue(f.length() < 100_000, f.getName() + " size " + f.length());
    }
  }

  @Test
  void testGenerateFillLadderPlusSingleExtremesAtCatalogSize() throws Exception {
    HPictorialSeries series =
        new HPictorialSeries("growth", "Growth", RenderMode.STEP_IMAGES);
    GenerationRequest req = new GenerationRequest();
    req.renderMode = RenderMode.STEP_IMAGES;
    req.stepSize = 50; // 0, 50, 100
    req.includeNegativeExtreme = true;
    req.includeOverflowExtreme = true;
    req.negativeStepKey = -100;
    req.overflowStepKey = 200;
    req.aspectPreset = "PORTRAIT_3_4";
    req.resolutionTier = "MEDIUM";
    req.providerConfig = new HAiProviderConfig();
    req.outputDirectory = tempDir;
    req.relativeAssetUrlPrefix = "${HOPPER_METADATA_PATH}/assets/growth";
    req.prompt = "fill {percentage}";
    req.negativePrompt = "broken {percentage}";
    req.overflowPrompt = "overflow {percentage}";

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    service.generateSeriesAssets(series, req);

    assertEquals(5, series.getImageMap().size(), series.getImageMap().keySet().toString());
    assertTrue(series.getImageMap().containsKey("-100"));
    assertTrue(series.getImageMap().containsKey("0"));
    assertTrue(series.getImageMap().containsKey("50"));
    assertTrue(series.getImageMap().containsKey("100"));
    assertTrue(series.getImageMap().containsKey("200"));

    File mid = new File(tempDir, "step_50.jpg");
    assertTrue(mid.exists());
    BufferedImage img = ImageIO.read(mid);
    assertEquals(576, img.getWidth(), "catalog MEDIUM 3:4 width");
    assertEquals(768, img.getHeight(), "catalog MEDIUM 3:4 height");
    assertEquals("broken {percentage}", series.getNegativePrompt());
  }

  @Test
  void testNoManyNegativeFramesWhenRangeWouldHaveSpannedNegatives() throws Exception {
    // Old UI sent stepMin=-100 stepMax=200; generator must still only emit one under + one over
    HPictorialSeries series = new HPictorialSeries("x", "x", RenderMode.STEP_IMAGES);
    GenerationRequest req = new GenerationRequest();
    req.stepSize = 10;
    req.stepMin = -100; // ignored for ladder
    req.stepMax = 200; // ignored for ladder
    req.includeNegativeExtreme = true;
    req.includeOverflowExtreme = true;
    req.width = 80;
    req.height = 100;
    req.outputDirectory = tempDir;

    new HAiImageGeneratorService().generateSeriesAssets(series, req);

    long negativeKeys =
        series.getImageMap().keySet().stream().filter(k -> Integer.parseInt(k) < 0).count();
    long overKeys =
        series.getImageMap().keySet().stream().filter(k -> Integer.parseInt(k) > 100).count();
    assertEquals(1, negativeKeys);
    assertEquals(1, overKeys);
  }

  @Test
  void testGenerateSingleStepReplacesOneKey() throws Exception {
    GenerationRequest req = new GenerationRequest();
    req.aspectPreset = "SQUARE_1_1";
    req.resolutionTier = "SMALL";
    req.providerConfig = new HAiProviderConfig();
    req.outputDirectory = tempDir;
    req.relativeAssetUrlPrefix = "${HOPPER_METADATA_PATH}/assets/x";
    req.prompt = "fill {percentage}";

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    String path = service.generateSingleStepImage(req, 10);
    assertTrue(path.contains("step_10"));
    assertTrue(new File(tempDir, "step_10.jpg").exists());
    long firstLen = new File(tempDir, "step_10.jpg").length();
    assertTrue(firstLen > 0);

    String path2 = service.generateSingleStepImage(req, 10);
    assertTrue(path2.contains("step_10"));
    assertTrue(new File(tempDir, "step_10.jpg").length() > 0);
  }

  @Test
  void testChoosePromptSelectsNegativeAndOverflow() {
    GenerationRequest req = new GenerationRequest();
    req.prompt = "normal";
    req.negativePrompt = "neg";
    req.overflowPrompt = "over";
    assertEquals("neg", HAiImageGeneratorService.choosePrompt(req, -50));
    assertEquals("normal", HAiImageGeneratorService.choosePrompt(req, 50));
    assertEquals("over", HAiImageGeneratorService.choosePrompt(req, 150));
  }

  @Test
  void testLargeSourceImageCoverCroppedToCatalogSize() throws Exception {
    BufferedImage huge = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = huge.createGraphics();
    g.setColor(Color.ORANGE);
    g.fillRect(0, 0, 2000, 2000);
    g.dispose();

    GenerationRequest req = new GenerationRequest();
    req.aspectPreset = "PORTRAIT_3_4";
    req.resolutionTier = "MEDIUM";
    req.providerConfig = new HAiProviderConfig();
    req.outputDirectory = tempDir;
    req.outputFormat = "jpeg";
    req.jpegQuality = 0.82f;
    req.maxBytes = 100_000;

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    File out = service.writeCompactAsset(huge, req, "step_50");
    assertTrue(out.exists());

    BufferedImage written = ImageIO.read(out);
    assertEquals(576, written.getWidth());
    assertEquals(768, written.getHeight());
    // Cover crop of solid orange — no white letterbox (JPEG may shift by 1–2)
    int rgb = written.getRGB(10, 10);
    int r = (rgb >> 16) & 0xff;
    int gch = (rgb >> 8) & 0xff;
    int b = rgb & 0xff;
    assertTrue(r > 200 && gch > 100 && b < 80, "expected orange-ish, got " + r + "," + gch + "," + b);
  }
}
