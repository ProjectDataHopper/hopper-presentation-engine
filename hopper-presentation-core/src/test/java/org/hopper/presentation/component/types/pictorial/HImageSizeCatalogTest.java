package org.hopper.presentation.component.types.pictorial;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType;
import org.hopper.presentation.component.types.pictorial.HImageSizeCatalog.AspectPreset;
import org.hopper.presentation.component.types.pictorial.HImageSizeCatalog.ResolutionTier;

class HImageSizeCatalogTest {

  @Test
  void grokOnlyAllowsSquare() {
    Set<AspectPreset> allowed = HImageSizeCatalog.allowedPresets(ProviderType.XAI_GROK);
    assertEquals(Set.of(AspectPreset.SQUARE_1_1), allowed);
    assertEquals(
        AspectPreset.SQUARE_1_1,
        HImageSizeCatalog.coercePreset(ProviderType.XAI_GROK, AspectPreset.PORTRAIT_3_4));
  }

  @Test
  void openaiMapsPortrait916ToApiSize() {
    var r =
        HImageSizeCatalog.resolve(
            ProviderType.OPENAI_DALLE, AspectPreset.PORTRAIT_9_16, ResolutionTier.LARGE);
    assertEquals("1024x1792", r.openaiSize);
    assertEquals(1024, r.width);
    assertEquals(1792, r.height);
  }

  @Test
  void scaleToCoverHasNoLetterboxOnFilledSource() {
    BufferedImage src = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = src.createGraphics();
    g.setColor(Color.ORANGE);
    g.fillRect(0, 0, 1024, 1024);
    g.dispose();

    BufferedImage out = HAiImageGeneratorService.scaleToCover(src, 576, 768, false);
    assertEquals(576, out.getWidth());
    assertEquals(768, out.getHeight());
    // Center and edges should be orange (cover-crop of solid orange), not white letterbox
    assertEquals(Color.ORANGE.getRGB(), out.getRGB(288, 384));
    assertEquals(Color.ORANGE.getRGB(), out.getRGB(10, 10));
    assertEquals(Color.ORANGE.getRGB(), out.getRGB(575, 767));
  }

  @Test
  void applyResolvedSizeUsesCatalogNotFreeformTall() {
    HAiImageGeneratorService.GenerationRequest req =
        new HAiImageGeneratorService.GenerationRequest();
    req.aspectPreset = "PORTRAIT_3_4";
    req.resolutionTier = "MEDIUM";
    req.width = 400; // should be overwritten
    req.height = 1200;
    req.providerConfig = new HAiProviderConfig(ProviderType.BUILTIN, null, null);
    HAiImageGeneratorService.applyResolvedSize(req);
    assertEquals(576, req.width);
    assertEquals(768, req.height);
  }
}
