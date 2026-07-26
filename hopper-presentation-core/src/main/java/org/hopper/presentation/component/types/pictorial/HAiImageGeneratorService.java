package org.hopper.presentation.component.types.pictorial;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.ClipDirection;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;

/**
 * Service providing design-time generation of pictorial chart image sequences (`STEP_IMAGES`)
 * and pixel-stable empty/fill layer pairs (`CLIPPED_LAYERS`).
 */
public class HAiImageGeneratorService {

  /** Default max encoded asset size (~JPEG under 100 KB as in the issue). */
  public static final int DEFAULT_MAX_BYTES = 100_000;

  public static class GenerationRequest {
    public String presentationName;
    public String componentName;
    /** Prompt for normal 0–100% fill steps ({@code {percentage}} placeholder). */
    public String prompt;
    /** Prompt for the single under-target image (any value &lt; 0). */
    public String negativePrompt;
    /** Prompt for the single over-target image (any value &gt; 100). */
    public String overflowPrompt;
    public RenderMode renderMode = RenderMode.STEP_IMAGES;
    /**
     * Spacing between normal fill levels from 0% to 100% (e.g. 10 → 0,10,…,100).
     * Always the main series; extremes are separate single images.
     */
    public int stepSize = 10;
    /** @deprecated ignored for generation; normal range is always 0–100 */
    public int stepMin = 0;
    /** @deprecated ignored for generation; normal range is always 0–100 */
    public int stepMax = 100;
    /** Generate exactly one image for values &lt; 0 (key {@link #negativeStepKey}). */
    public boolean includeNegativeExtreme = true;
    /** Generate exactly one image for values &gt; 100 (key {@link #overflowStepKey}). */
    public boolean includeOverflowExtreme = true;
    /** Map key for the under-target image (default −100). */
    public int negativeStepKey = -100;
    /** Map key for the over-target image (default 200). */
    public int overflowStepKey = 200;
    public String generationStyle = "STABLE_INPAINT"; // STABLE_INPAINT vs DISCRETE
    /**
     * Aspect preset id ({@link HImageSizeCatalog.AspectPreset}); preferred over free width/height
     * for external AI providers.
     */
    public String aspectPreset = HImageSizeCatalog.AspectPreset.PORTRAIT_3_4.name();
    /** {@link HImageSizeCatalog.ResolutionTier} id. */
    public String resolutionTier = HImageSizeCatalog.ResolutionTier.MEDIUM.name();
    /**
     * Resolved output pixel size (filled from catalog when aspectPreset is set). Free W×H only
     * trusted for BUILTIN if no preset is provided.
     */
    public int width = 576;
    public int height = 768;
    public File outputDirectory;
    public String relativeAssetUrlPrefix = "";
    public HAiProviderConfig providerConfig = new HAiProviderConfig();
    /** {@code jpeg} (default, compact) or {@code png} (transparency for clipped layers). */
    public String outputFormat = "jpeg";
    /** JPEG quality 0..1 (default 0.82). */
    public float jpegQuality = 0.82f;
    /** Soft size cap; quality reduced if exceeded (dimensions stay as requested). */
    public int maxBytes = DEFAULT_MAX_BYTES;
  }

  public static class GenerationResult {
    public HPictorialChartComponent updatedComponent;
    public Map<String, String> generatedFiles = new LinkedHashMap<>();
    public String backgroundImagePath;
    public String fillImagePath;
  }

  /**
   * Generates step images or layer pair according to request parameters, writes files to disk,
   * and populates the given component's configuration properties.
   */
  public GenerationResult generateAssets(
      HPictorialChartComponent component, GenerationRequest req) throws HException {

    if (component == null) {
      throw new HException("Component cannot be null");
    }
    if (req == null || req.outputDirectory == null) {
      throw new HException("Generation request and output directory are required");
    }

    if (!req.outputDirectory.exists()) {
      req.outputDirectory.mkdirs();
    }

    GenerationResult result = new GenerationResult();
    result.updatedComponent = component;

    RenderMode mode = req.renderMode != null ? req.renderMode : RenderMode.STEP_IMAGES;
    component.setRenderMode(mode);

    if (mode == RenderMode.STEP_IMAGES) {
      Map<String, String> imageMap = generateStepImageMap(req, result);
      component.setImageMap(imageMap);
    } else { // CLIPPED_LAYERS — prefer PNG for transparency
      GenerationRequest layerReq = copyForPng(req);
      applyResolvedSize(req);
      layerReq.width = req.width;
      layerReq.height = req.height;
      layerReq.aspectPreset = req.aspectPreset;
      layerReq.resolutionTier = req.resolutionTier;
      BufferedImage bgImg = generateContainerLayer(req.prompt, false, layerReq);
      BufferedImage fillImg = generateContainerLayer(req.prompt, true, layerReq);

      File bgFile = writeCompactAsset(bgImg, layerReq, "bg_empty");
      File fillFile = writeCompactAsset(fillImg, layerReq, "fill_full");

      String bgPath = buildAssetPath(req.relativeAssetUrlPrefix, bgFile.getName());
      String fillPath = buildAssetPath(req.relativeAssetUrlPrefix, fillFile.getName());

      component.setBackgroundImage(bgPath);
      component.setFillImage(fillPath);
      component.setClipDirection(ClipDirection.BOTTOM_TO_TOP);

      result.backgroundImagePath = bgFile.getAbsolutePath();
      result.fillImagePath = fillFile.getAbsolutePath();
    }

    return result;
  }

  public GenerationResult generateSeriesAssets(
      HPictorialSeries series, GenerationRequest req) throws HException {

    if (series == null) {
      throw new HException("Pictorial series cannot be null");
    }
    if (req == null || req.outputDirectory == null) {
      throw new HException("Generation request and output directory are required");
    }

    if (!req.outputDirectory.exists()) {
      req.outputDirectory.mkdirs();
    }

    GenerationResult result = new GenerationResult();

    RenderMode mode = req.renderMode != null ? req.renderMode : RenderMode.STEP_IMAGES;
    series.setRenderMode(mode);

    if (mode == RenderMode.STEP_IMAGES) {
      Map<String, String> imageMap = generateStepImageMap(req, result);
      series.setImageMap(imageMap);
      series.setStepMin(req.includeNegativeExtreme ? req.negativeStepKey : 0);
      series.setStepMax(req.includeOverflowExtreme ? req.overflowStepKey : 100);
      series.setStepSize(req.stepSize);
      if (StringUtils.isNotBlank(req.prompt)) {
        series.setPrompt(req.prompt);
      }
      if (StringUtils.isNotBlank(req.negativePrompt)) {
        series.setNegativePrompt(req.negativePrompt);
      }
      if (StringUtils.isNotBlank(req.overflowPrompt)) {
        series.setOverflowPrompt(req.overflowPrompt);
      }
    } else {
      GenerationRequest layerReq = copyForPng(req);
      applyResolvedSize(req);
      layerReq.width = req.width;
      layerReq.height = req.height;
      layerReq.aspectPreset = req.aspectPreset;
      layerReq.resolutionTier = req.resolutionTier;
      BufferedImage bgImg = generateContainerLayer(req.prompt, false, layerReq);
      BufferedImage fillImg = generateContainerLayer(req.prompt, true, layerReq);

      File bgFile = writeCompactAsset(bgImg, layerReq, "bg_empty");
      File fillFile = writeCompactAsset(fillImg, layerReq, "fill_full");

      String bgPath = buildAssetPath(req.relativeAssetUrlPrefix, bgFile.getName());
      String fillPath = buildAssetPath(req.relativeAssetUrlPrefix, fillFile.getName());

      series.setBackgroundImage(bgPath);
      series.setFillImage(fillPath);

      result.backgroundImagePath = bgFile.getAbsolutePath();
      result.fillImagePath = fillFile.getAbsolutePath();
    }

    return result;
  }

  /**
   * Normal series is always 0…100 by {@code stepSize}, plus optional single under/over images.
   *
   * <p>Does <strong>not</strong> generate a ladder of many negative or many overflow frames —
   * one broken-glass image covers all &lt;0, one overflow image covers all &gt;100.
   */
  private Map<String, String> generateStepImageMap(GenerationRequest req, GenerationResult result)
      throws HException {
    int step = Math.max(1, Math.min(100, req.stepSize > 0 ? req.stepSize : 10));
    applyResolvedSize(req);
    int w = req.width;
    int h = req.height;

    Map<String, String> imageMap = new LinkedHashMap<>();
    java.util.TreeSet<Integer> keys = new java.util.TreeSet<>();

    // Always 0% … 100%
    for (int pct = 0; pct <= 100; pct += step) {
      keys.add(pct);
    }
    keys.add(0);
    keys.add(100);

    // Exactly one under-target + one over-target image when enabled
    if (req.includeNegativeExtreme) {
      keys.add(req.negativeStepKey != 0 ? req.negativeStepKey : -100);
    }
    if (req.includeOverflowExtreme) {
      int over = req.overflowStepKey > 100 ? req.overflowStepKey : 200;
      keys.add(over);
    }

    for (int pct : keys) {
      String chosenPrompt = choosePrompt(req, pct);
      BufferedImage img = generateStepImage(chosenPrompt, pct, req);
      String baseName = "step_" + pct;
      File file = writeCompactAsset(img, req, baseName);
      String assetPath = buildAssetPath(req.relativeAssetUrlPrefix, file.getName());
      imageMap.put(String.valueOf(pct), assetPath);
      result.generatedFiles.put(String.valueOf(pct), file.getAbsolutePath());
    }
    return imageMap;
  }

  static String choosePrompt(GenerationRequest req, int pct) {
    if (pct < 0) {
      if (StringUtils.isNotBlank(req.negativePrompt)) {
        return req.negativePrompt;
      }
      return StringUtils.defaultIfBlank(req.prompt, "metric at {percentage}%")
          + ", broken damaged vessel, negative result";
    }
    if (pct > 100) {
      if (StringUtils.isNotBlank(req.overflowPrompt)) {
        return req.overflowPrompt;
      }
      return StringUtils.defaultIfBlank(req.prompt, "metric at {percentage}%")
          + ", overflowing vessel with spill puddle";
    }
    return StringUtils.defaultIfBlank(req.prompt, "A vessel filled to {percentage}%");
  }

  /**
   * Generate (or replace) a single step image for the given percentage key and return its asset
   * path (using {@link GenerationRequest#relativeAssetUrlPrefix}).
   */
  public String generateSingleStepImage(GenerationRequest req, int percentage) throws HException {
    if (req == null || req.outputDirectory == null) {
      throw new HException("Generation request and output directory are required");
    }
    if (!req.outputDirectory.exists() && !req.outputDirectory.mkdirs()) {
      throw new HException("Cannot create output directory " + req.outputDirectory);
    }
    applyResolvedSize(req);
    String chosenPrompt = choosePrompt(req, percentage);
    BufferedImage img = generateStepImage(chosenPrompt, percentage, req);
    String baseName = "step_" + percentage;
    File file = writeCompactAsset(img, req, baseName);
    return buildAssetPath(req.relativeAssetUrlPrefix, file.getName());
  }

  private static GenerationRequest copyForPng(GenerationRequest req) {
    GenerationRequest copy = new GenerationRequest();
    copy.presentationName = req.presentationName;
    copy.componentName = req.componentName;
    copy.prompt = req.prompt;
    copy.negativePrompt = req.negativePrompt;
    copy.overflowPrompt = req.overflowPrompt;
    copy.renderMode = req.renderMode;
    copy.stepSize = req.stepSize;
    copy.stepMin = req.stepMin;
    copy.stepMax = req.stepMax;
    copy.includeNegativeExtreme = req.includeNegativeExtreme;
    copy.includeOverflowExtreme = req.includeOverflowExtreme;
    copy.negativeStepKey = req.negativeStepKey;
    copy.overflowStepKey = req.overflowStepKey;
    copy.generationStyle = req.generationStyle;
    copy.aspectPreset = req.aspectPreset;
    copy.resolutionTier = req.resolutionTier;
    copy.width = req.width;
    copy.height = req.height;
    copy.outputDirectory = req.outputDirectory;
    copy.relativeAssetUrlPrefix = req.relativeAssetUrlPrefix;
    copy.providerConfig = req.providerConfig;
    copy.outputFormat = "png";
    copy.jpegQuality = req.jpegQuality;
    copy.maxBytes = req.maxBytes;
    return copy;
  }

  private String buildAssetPath(String prefix, String fileName) {
    if (StringUtils.isBlank(prefix)) {
      return fileName;
    }
    return prefix.endsWith("/") ? prefix + fileName : prefix + "/" + fileName;
  }

  /** Resolve catalog size onto the request (provider-safe). */
  public static void applyResolvedSize(GenerationRequest req) {
    HAiProviderConfig.ProviderType provider =
        req.providerConfig != null && req.providerConfig.getProviderType() != null
            ? req.providerConfig.getProviderType()
            : HAiProviderConfig.ProviderType.BUILTIN;
    HImageSizeCatalog.AspectPreset preset =
        HImageSizeCatalog.AspectPreset.fromId(req.aspectPreset);
    HImageSizeCatalog.ResolutionTier tier =
        HImageSizeCatalog.ResolutionTier.fromId(req.resolutionTier);
    HImageSizeCatalog.ResolvedSize resolved = HImageSizeCatalog.resolve(provider, preset, tier);
    req.aspectPreset = resolved.preset.name();
    req.resolutionTier = resolved.tier.name();
    req.width = resolved.width;
    req.height = resolved.height;
  }

  /**
   * Write image at catalog width×height using <strong>cover + center crop</strong> (no white
   * letterbox bands). JPEG quality may be reduced if the file exceeds {@code maxBytes}.
   */
  File writeCompactAsset(BufferedImage source, GenerationRequest req, String baseName)
      throws HException {
    if (source == null) {
      throw new HException("Cannot write null image for " + baseName);
    }
    applyResolvedSize(req);
    int targetW = Math.max(16, req.width);
    int targetH = Math.max(16, req.height);
    float quality = req.jpegQuality > 0 && req.jpegQuality <= 1f ? req.jpegQuality : 0.82f;
    int maxBytes = req.maxBytes > 0 ? req.maxBytes : DEFAULT_MAX_BYTES;
    boolean jpeg = !"png".equalsIgnoreCase(StringUtils.defaultIfBlank(req.outputFormat, "jpeg"));

    // Already exact size (builtin) → keep; otherwise cover-crop into target
    BufferedImage sized;
    if (source.getWidth() == targetW && source.getHeight() == targetH) {
      sized = source;
    } else {
      sized = scaleToCover(source, targetW, targetH, !jpeg);
    }
    // JPEG cannot encode ARGB — flatten to RGB
    if (jpeg && sized.getType() != BufferedImage.TYPE_INT_RGB) {
      BufferedImage rgb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = rgb.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, targetW, targetH);
      g.drawImage(sized, 0, 0, null);
      g.dispose();
      sized = rgb;
    }
    String ext = jpeg ? ".jpg" : ".png";
    File file = new File(req.outputDirectory, baseName + ext);

    try {
      writeImageFile(sized, file, jpeg, quality);
      float q = quality;
      while (jpeg && file.length() > maxBytes && q > 0.45f) {
        q = Math.max(0.45f, q - 0.12f);
        writeImageFile(sized, file, true, q);
      }
    } catch (HException he) {
      throw he;
    } catch (Exception e) {
      throw new HException("Failed to write compact asset " + file.getAbsolutePath(), e);
    }
    return file;
  }

  /**
   * Scale so the source <strong>covers</strong> the target, then center-crop. No letterbox / no
   * white bands.
   */
  static BufferedImage scaleToCover(
      BufferedImage source, int targetW, int targetH, boolean keepAlpha) {
    int sw = source.getWidth();
    int sh = source.getHeight();
    double scale = Math.max((double) targetW / sw, (double) targetH / sh);
    int dw = Math.max(1, (int) Math.round(sw * scale));
    int dh = Math.max(1, (int) Math.round(sh * scale));
    int type = keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    BufferedImage scaled = new BufferedImage(dw, dh, type);
    Graphics2D g = scaled.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    if (!keepAlpha) {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, dw, dh);
    }
    g.drawImage(source, 0, 0, dw, dh, null);
    g.dispose();

    int x = Math.max(0, (dw - targetW) / 2);
    int y = Math.max(0, (dh - targetH) / 2);
    BufferedImage out = new BufferedImage(targetW, targetH, type);
    Graphics2D g2 = out.createGraphics();
    if (!keepAlpha) {
      g2.setColor(Color.WHITE);
      g2.fillRect(0, 0, targetW, targetH);
    }
    g2.drawImage(scaled, 0, 0, targetW, targetH, x, y, x + targetW, y + targetH, null);
    g2.dispose();
    return out;
  }

  /** Letterbox contain — only for tests/legacy; prefer {@link #scaleToCover}. */
  static BufferedImage scaleToExactSize(
      BufferedImage source, int targetW, int targetH, boolean keepAlpha) {
    return scaleToCover(source, targetW, targetH, keepAlpha);
  }

  static BufferedImage scaleToFit(BufferedImage source, int maxW, int maxH, boolean keepAlpha) {
    return scaleToCover(source, maxW, maxH, keepAlpha);
  }

  private static void writeImageFile(BufferedImage img, File file, boolean jpeg, float quality)
      throws Exception {
    if (jpeg) {
      javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
        writer.setOutput(ios);
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
          param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
          param.setCompressionQuality(quality);
        }
        writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
      } finally {
        writer.dispose();
      }
    } else {
      ImageIO.write(img, "png", file);
    }
  }

  private BufferedImage generateStepImage(String prompt, int pct, GenerationRequest req) {
    HAiProviderConfig providerConfig = req.providerConfig;
    String style = req.generationStyle;
    int w = req.width;
    int h = req.height;

    if (providerConfig != null
        && providerConfig.getProviderType() != HAiProviderConfig.ProviderType.BUILTIN
        && StringUtils.isNotBlank(providerConfig.getDecryptedApiKey())) {
      try {
        String framed =
            (prompt == null ? "" : prompt)
                + ", full-bleed product photo, subject fills the frame, no empty borders, no letterboxing";
        BufferedImage externalImg = callExternalAiProvider(framed, pct, req, providerConfig);
        if (externalImg != null) {
          return externalImg;
        }
      } catch (Exception e) {
        System.err.println(
            "[HAiImageGeneratorService] Call to "
                + providerConfig.getProviderType()
                + " failed: "
                + e.getMessage());
        throw new RuntimeException(
            providerConfig.getProviderType() + " API error: " + e.getMessage(), e);
      }
    }

    int width = Math.max(40, w);
    int height = Math.max(60, h);
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g.setColor(new Color(240, 244, 248));
    g.fillRect(0, 0, width, height);

    // Full-bleed vessel: large glass in frame (not a tiny stamp on a tall canvas)
    int marginX = Math.max(8, width / 10);
    int marginY = Math.max(8, height / 12);
    int floor = Math.max(10, height / 14); // puddle / shards strip
    int vesselW = width - 2 * marginX;
    int vesselH = height - 2 * marginY - floor;

    if (pct < 0) {
      drawBrokenGlass(g, marginX, marginY, vesselW, vesselH, pct);
    } else if (pct > 100) {
      drawOverflowGlass(g, marginX, marginY, vesselW, vesselH, pct, style);
    } else {
      drawNormalFill(g, marginX, marginY, vesselW, vesselH, pct, style);
    }

    g.setColor(new Color(80, 80, 80));
    g.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, width / 18)));
    g.drawString(pct + "%", marginX + 2, marginY + Math.max(12, height / 24));

    g.dispose();
    return img;
  }

  private static void drawNormalFill(
      Graphics2D g, int marginX, int marginY, int vesselW, int vesselH, int pct, String style) {
    double fillFrac = Math.max(0, Math.min(1.0, pct / 100.0));
    int fillH = (int) (vesselH * fillFrac);
    int fillY = marginY + (vesselH - fillH);
    if (fillH > 0) {
      Color liquidColor =
          "DISCRETE".equalsIgnoreCase(style)
              ? new Color(30 + Math.floorMod(pct * 2, 200), 100 + Math.floorMod(pct, 100), 220)
              : new Color(212, 165, 55); // beer gold
      g.setColor(liquidColor);
      g.fillRect(marginX, fillY, vesselW, fillH);
    }
    g.setColor(new Color(44, 62, 80));
    g.drawRect(marginX, marginY, vesselW, vesselH);
  }

  /** Negative attainment: cracked glass, shards, little liquid. */
  private static void drawBrokenGlass(
      Graphics2D g, int marginX, int marginY, int vesselW, int vesselH, int pct) {
    g.setColor(new Color(220, 80, 80, 40));
    g.fillRect(marginX - 4, marginY - 4, vesselW + 8, vesselH + 20);

    // Fragmented vessel outline
    g.setColor(new Color(44, 62, 80));
    int[] xPoints = {
      marginX,
      marginX + vesselW / 3,
      marginX + vesselW / 2,
      marginX + vesselW * 2 / 3,
      marginX + vesselW,
      marginX + vesselW - 4,
      marginX + 4
    };
    int[] yPoints = {
      marginY + 8,
      marginY,
      marginY + vesselH / 2,
      marginY + 4,
      marginY + 20,
      marginY + vesselH,
      marginY + vesselH - 6
    };
    g.drawPolygon(xPoints, yPoints, xPoints.length);

    // Crack lines
    g.setColor(new Color(192, 57, 43));
    g.drawLine(marginX + vesselW / 2, marginY, marginX + vesselW / 3, marginY + vesselH);
    g.drawLine(marginX + vesselW / 2, marginY + vesselH / 3, marginX + vesselW - 4, marginY + vesselH / 2);
    g.drawLine(marginX + 6, marginY + vesselH / 2, marginX + vesselW / 2, marginY + vesselH - 4);

    // Shards on the floor
    g.setColor(new Color(149, 165, 166));
    g.fillPolygon(
        new int[] {marginX - 6, marginX + 10, marginX + 4},
        new int[] {marginY + vesselH + 12, marginY + vesselH + 8, marginY + vesselH + 18},
        3);
    g.fillPolygon(
        new int[] {marginX + vesselW - 8, marginX + vesselW + 6, marginX + vesselW},
        new int[] {marginY + vesselH + 6, marginY + vesselH + 10, marginY + vesselH + 18},
        3);

    // Small puddle of spilled beer
    g.setColor(new Color(212, 165, 55, 120));
    g.fillOval(marginX + 8, marginY + vesselH + 4, vesselW - 16, 12);
  }

  /** Over 100%: full glass + overflow foam + puddle. */
  private static void drawOverflowGlass(
      Graphics2D g, int marginX, int marginY, int vesselW, int vesselH, int pct, String style) {
    // Puddle first (behind glass)
    double overflow = Math.min(2.0, pct / 100.0); // 100→1, 200→2
    int puddleW = (int) (vesselW * (0.9 + 0.4 * (overflow - 1)));
    int puddleH = (int) (10 + 14 * (overflow - 1));
    g.setColor(new Color(212, 165, 55, 160));
    g.fillOval(
        marginX + (vesselW - puddleW) / 2,
        marginY + vesselH + 2,
        puddleW,
        puddleH);

    // Full liquid
    g.setColor(new Color(212, 165, 55));
    g.fillRect(marginX, marginY, vesselW, vesselH);

    // Foam overflow above rim
    int foamH = (int) (8 + 12 * Math.min(1.0, overflow - 1));
    g.setColor(new Color(255, 250, 230));
    g.fillOval(marginX - 4, marginY - foamH, vesselW + 8, foamH * 2);
    g.fillRect(marginX, marginY - foamH / 2, vesselW, foamH);

    // Drips
    g.setColor(new Color(212, 165, 55, 200));
    g.fillRect(marginX + 4, marginY + vesselH - 4, 6, 14);
    g.fillRect(marginX + vesselW - 12, marginY + vesselH - 2, 6, 12);

    g.setColor(new Color(44, 62, 80));
    g.drawRect(marginX, marginY, vesselW, vesselH);
  }

  private BufferedImage generateContainerLayer(
      String prompt, boolean isFill, GenerationRequest req) {
    HAiProviderConfig providerConfig = req.providerConfig;
    int w = req.width;
    int h = req.height;
    if (providerConfig != null
        && providerConfig.getProviderType() != HAiProviderConfig.ProviderType.BUILTIN
        && StringUtils.isNotBlank(providerConfig.getDecryptedApiKey())) {
      try {
        int pct = isFill ? 100 : 0;
        BufferedImage externalImg = callExternalAiProvider(prompt, pct, req, providerConfig);
        if (externalImg != null) {
          return externalImg;
        }
      } catch (Exception e) {
        System.err.println(
            "[HAiImageGeneratorService] Call to "
                + providerConfig.getProviderType()
                + " failed: "
                + e.getMessage());
        throw new RuntimeException(
            providerConfig.getProviderType() + " API error: " + e.getMessage(), e);
      }
    }

    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int marginX = Math.max(8, w / 10);
    int marginY = Math.max(8, h / 12);
    int vesselW = w - 2 * marginX;
    int vesselH = h - 2 * marginY;

    if (isFill) {
      g.setColor(new Color(41, 128, 185, 230));
      g.fillRect(marginX, marginY, vesselW, vesselH);
    } else {
      g.setColor(new Color(236, 240, 241, 200));
      g.fillRect(marginX, marginY, vesselW, vesselH);
      g.setColor(new Color(44, 62, 80));
      g.drawRect(marginX, marginY, vesselW, vesselH);
    }

    g.dispose();
    return img;
  }

  private BufferedImage callExternalAiProvider(
      String basePrompt, int percentage, GenerationRequest genReq, HAiProviderConfig config)
      throws Exception {
    String apiKey = config.getDecryptedApiKey();
    if (StringUtils.isBlank(apiKey)) {
      return null;
    }
    String prompt = basePrompt.replace("{percentage}", String.valueOf(percentage));
    String endpointUrl = config.getEffectiveEndpointUrl();
    String modelName = config.getEffectiveModelName();

    java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30))
        .build();

    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.node.ObjectNode bodyNode = mapper.createObjectNode();

    java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
        .timeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30))
        .header("Content-Type", "application/json");

    HImageSizeCatalog.AspectPreset preset =
        HImageSizeCatalog.AspectPreset.fromId(
            genReq != null ? genReq.aspectPreset : null);
    HImageSizeCatalog.ResolvedSize apiSize =
        HImageSizeCatalog.resolve(
            config.getProviderType(),
            preset,
            HImageSizeCatalog.ResolutionTier.LARGE);

    if (config.getProviderType() == HAiProviderConfig.ProviderType.GOOGLE_IMAGEN) {
      if (!endpointUrl.contains("key=")) {
        endpointUrl += (endpointUrl.contains("?") ? "&" : "?") + "key=" + apiKey;
      }
      com.fasterxml.jackson.databind.node.ObjectNode configNode = mapper.createObjectNode();
      configNode.put("numberOfImages", 1);
      configNode.put("outputMimeType", "image/png");
      if (apiSize.aspectRatioApi != null) {
        configNode.put("aspectRatio", apiSize.aspectRatioApi);
      }
      bodyNode.put("prompt", prompt);
      bodyNode.set("config", configNode);

    } else if (config.getProviderType() == HAiProviderConfig.ProviderType.XAI_GROK) {
      reqBuilder.header("Authorization", "Bearer " + apiKey);
      bodyNode.put("prompt", prompt);
      bodyNode.put("model", modelName);
      bodyNode.put("n", 1);
      // Grok is treated as square-capable; avoid requesting unsupported tall sizes
      bodyNode.put("aspect_ratio", "1:1");

    } else if (config.getProviderType() == HAiProviderConfig.ProviderType.OPENAI_DALLE) {
      reqBuilder.header("Authorization", "Bearer " + apiKey);
      bodyNode.put("prompt", prompt);
      bodyNode.put("model", modelName);
      bodyNode.put("n", 1);
      bodyNode.put("response_format", "b64_json");
      bodyNode.put(
          "size",
          StringUtils.defaultIfBlank(apiSize.openaiSize, "1024x1024"));
    } else {
      return null;
    }

    reqBuilder.uri(java.net.URI.create(endpointUrl));
    reqBuilder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(bodyNode)));

    java.net.http.HttpResponse<String> response = client.send(reqBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new HException("AI Provider HTTP error " + response.statusCode() + ": " + response.body());
    }

    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(response.body());
    String b64Data = null;
    String imgUrl = null;

    if (config.getProviderType() == HAiProviderConfig.ProviderType.GOOGLE_IMAGEN) {
      if (json.has("predictions") && json.get("predictions").isArray() && json.get("predictions").size() > 0) {
        com.fasterxml.jackson.databind.JsonNode pred = json.get("predictions").get(0);
        if (pred.has("bytesBase64Encoded")) {
          b64Data = pred.get("bytesBase64Encoded").asText();
        }
      } else if (json.has("generatedImages") && json.get("generatedImages").isArray() && json.get("generatedImages").size() > 0) {
        com.fasterxml.jackson.databind.JsonNode imgNode = json.get("generatedImages").get(0);
        if (imgNode.has("image") && imgNode.get("image").has("imageBytes")) {
          b64Data = imgNode.get("image").get("imageBytes").asText();
        }
      }
    } else { // XAI_GROK / OPENAI_DALLE / generic
      if (json.has("data") && json.get("data").isArray() && json.get("data").size() > 0) {
        com.fasterxml.jackson.databind.JsonNode d = json.get("data").get(0);
        if (d.has("b64_json") && StringUtils.isNotBlank(d.get("b64_json").asText())) {
          b64Data = d.get("b64_json").asText();
        } else if (d.has("url") && StringUtils.isNotBlank(d.get("url").asText())) {
          imgUrl = d.get("url").asText();
        } else if (d.has("base64") && StringUtils.isNotBlank(d.get("base64").asText())) {
          b64Data = d.get("base64").asText();
        }
      }
      if (b64Data == null && imgUrl == null && json.has("images") && json.get("images").isArray() && json.get("images").size() > 0) {
        com.fasterxml.jackson.databind.JsonNode d = json.get("images").get(0);
        if (d.has("url") && StringUtils.isNotBlank(d.get("url").asText())) {
          imgUrl = d.get("url").asText();
        } else if (d.has("b64_json") && StringUtils.isNotBlank(d.get("b64_json").asText())) {
          b64Data = d.get("b64_json").asText();
        } else if (d.has("base64") && StringUtils.isNotBlank(d.get("base64").asText())) {
          b64Data = d.get("base64").asText();
        }
      }
    }

    if (StringUtils.isNotBlank(b64Data)) {
      byte[] imageBytes = java.util.Base64.getDecoder().decode(b64Data);
      return ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
    }

    if (StringUtils.isNotBlank(imgUrl)) {
      java.net.http.HttpRequest downloadReq = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(imgUrl)).GET().build();
      java.net.http.HttpResponse<byte[]> dlResp = client.send(downloadReq, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
      if (dlResp.statusCode() == 200) {
        return ImageIO.read(new java.io.ByteArrayInputStream(dlResp.body()));
      }
    }

    return null;
  }
}
