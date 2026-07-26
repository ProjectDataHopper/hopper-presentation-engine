package org.hopper.presentation.component.types.pictorial;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.metastore.IHasIdentity;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.ClipDirection;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.StepQuantization;

/**
 * Hop Metadata definition for a re-usable Pictorial Series.
 *
 * <p>Step keys are integer percentages and may be <strong>negative</strong> (e.g. broken glass at
 * −100) or <strong>over 100</strong> (e.g. overflowing glass at 200). AI generation uses three
 * prompts: baseline fill, negative extremes, and overflow extremes.
 */
@Getter
@Setter
@NoArgsConstructor
@HopMetadata(
    key = "pictorial-series",
    name = "Pictorial Series",
    description =
        "Image series for pictorial charts: step keys may be negative or over 100% (broken / overflow)")
public class HPictorialSeries extends HopMetadataBase implements IHopMetadata, IHasIdentity {

  @HopMetadataProperty
  @HWidgetElement(
      id = "description",
      label = "Description",
      type = HWidgetType.TEXT,
      order = "10")
  private String description;

  @HopMetadataProperty
  @HWidgetElement(
      id = "renderMode",
      label = "Render Mode",
      type = HWidgetType.COMBO,
      order = "20")
  private RenderMode renderMode = RenderMode.STEP_IMAGES;

  @HopMetadataProperty
  @HWidgetElement(
      id = "stepQuantization",
      label = "Step Quantization",
      type = HWidgetType.COMBO,
      order = "30")
  private StepQuantization stepQuantization = StepQuantization.NEAREST;

  @HopMetadataProperty
  @HWidgetElement(
      id = "clipDirection",
      label = "Clip Direction",
      type = HWidgetType.COMBO,
      order = "40")
  private ClipDirection clipDirection = ClipDirection.BOTTOM_TO_TOP;

  @HopMetadataProperty
  @HWidgetElement(
      id = "stepMin",
      label = "Step min (%)",
      type = HWidgetType.TEXT,
      order = "42",
      toolTip = "Lowest step key for generation (e.g. -100 for broken-glass extreme)")
  private int stepMin = 0;

  @HopMetadataProperty
  @HWidgetElement(
      id = "stepMax",
      label = "Step max (%)",
      type = HWidgetType.TEXT,
      order = "43",
      toolTip = "Highest step key for generation (e.g. 200 for overflowing glass)")
  private int stepMax = 100;

  @HopMetadataProperty
  @HWidgetElement(
      id = "stepSize",
      label = "Step size (%)",
      type = HWidgetType.TEXT,
      order = "44")
  private int stepSize = 10;

  @HopMetadataProperty
  @HWidgetElement(
      id = "prompt",
      label = "Prompt (0–100%)",
      type = HWidgetType.TEXT,
      order = "45",
      toolTip = "AI prompt for normal fill levels; use {percentage} placeholder")
  private String prompt;

  @HopMetadataProperty
  @HWidgetElement(
      id = "negativePrompt",
      label = "Negative prompt (< 0%)",
      type = HWidgetType.TEXT,
      order = "46",
      toolTip = "AI prompt for negative attainment (e.g. broken glass); {percentage} allowed")
  private String negativePrompt;

  @HopMetadataProperty
  @HWidgetElement(
      id = "overflowPrompt",
      label = "Overflow prompt (> 100%)",
      type = HWidgetType.TEXT,
      order = "47",
      toolTip = "AI prompt for over-target levels (e.g. overflowing glass + puddle)")
  private String overflowPrompt;

  @HopMetadataProperty
  @HWidgetElement(
      id = "backgroundImage",
      label = "Background Container Image",
      type = HWidgetType.TEXT,
      order = "50")
  private String backgroundImage;

  @HopMetadataProperty
  @HWidgetElement(
      id = "fillImage",
      label = "Fill Layer Image",
      type = HWidgetType.TEXT,
      order = "60")
  private String fillImage;

  /** Percentage key (may be negative or &gt; 100) → image path. */
  @HopMetadataProperty
  private Map<String, String> imageMap = new LinkedHashMap<>();

  public HPictorialSeries(String name, String description, RenderMode renderMode) {
    this.name = name;
    this.description = description;
    this.renderMode = renderMode;
  }

  /**
   * Resolve the best step image for a signed percentage (not clamped to 0–100).
   *
   * <p>Keys are integer strings such as {@code "-100"}, {@code "50"}, {@code "200"}.
   */
  public String getImageForPercentage(double percentage) {
    return resolveStepPath(imageMap, percentage, stepQuantization);
  }

  /**
   * Shared step lookup for series and inline component maps.
   *
   * <p>Semantics for extremes (matches generation: one under + one over image):
   *
   * <ul>
   *   <li>target &gt; 100 → prefer keys &gt; 100 (overflow glass), never pick a ≤100 step when an
   *       overflow key exists
   *   <li>target &lt; 0 → prefer keys &lt; 0 (broken glass)
   *   <li>otherwise use the 0–100 ladder (and only fall back to extremes if no in-range keys)
   * </ul>
   *
   * <p>Package-visible for tests.
   */
  public static String resolveStepPath(
      Map<String, String> map, double percentage, StepQuantization stepQuantization) {
    if (map == null || map.isEmpty()) {
      return null;
    }

    int targetPct = (int) Math.round(percentage);
    String exact = String.valueOf(targetPct);
    if (map.containsKey(exact)) {
      return map.get(exact);
    }

    StepQuantization quant =
        stepQuantization != null ? stepQuantization : StepQuantization.NEAREST;

    java.util.List<Integer> keys = new java.util.ArrayList<>();
    for (String keyStr : map.keySet()) {
      try {
        keys.add(Integer.parseInt(keyStr.trim()));
      } catch (NumberFormatException ignored) {
      }
    }
    if (keys.isEmpty()) {
      return map.values().iterator().next();
    }

    java.util.List<Integer> pool;
    if (targetPct > 100) {
      pool = keys.stream().filter(k -> k > 100).collect(java.util.stream.Collectors.toList());
      if (pool.isEmpty()) {
        pool = keys; // no overflow asset — fall back to nearest overall
      }
    } else if (targetPct < 0) {
      pool = keys.stream().filter(k -> k < 0).collect(java.util.stream.Collectors.toList());
      if (pool.isEmpty()) {
        pool = keys;
      }
    } else {
      // Normal fill: prefer keys in [0, 100]
      pool =
          keys.stream()
              .filter(k -> k >= 0 && k <= 100)
              .collect(java.util.stream.Collectors.toList());
      if (pool.isEmpty()) {
        pool = keys;
      }
    }

    Integer bestKey = pickNearest(pool, targetPct, quant);
    if (bestKey == null) {
      return map.values().iterator().next();
    }
    return pathForKey(map, bestKey);
  }

  private static Integer pickNearest(
      java.util.List<Integer> pool, int targetPct, StepQuantization quant) {
    Integer bestKey = null;
    int minDiff = Integer.MAX_VALUE;
    for (int keyPct : pool) {
      if (quant == StepQuantization.FLOOR && keyPct > targetPct) {
        continue;
      }
      if (quant == StepQuantization.CEIL && keyPct < targetPct) {
        continue;
      }
      int diff = Math.abs(keyPct - targetPct);
      if (bestKey == null || diff < minDiff) {
        minDiff = diff;
        bestKey = keyPct;
      } else if (diff == minDiff) {
        // Tie-break: outside 0–100 favor the extreme side; inside favor half-up (higher key)
        if (targetPct > 100 && keyPct > bestKey) {
          bestKey = keyPct;
        } else if (targetPct < 0 && keyPct < bestKey) {
          bestKey = keyPct;
        } else if (targetPct >= 0 && targetPct <= 100 && keyPct > bestKey) {
          bestKey = keyPct;
        }
      }
    }
    // FLOOR/CEIL with empty candidate set → fall back to unrestricted nearest
    if (bestKey == null && quant != StepQuantization.NEAREST) {
      return pickNearest(pool, targetPct, StepQuantization.NEAREST);
    }
    return bestKey;
  }

  private static String pathForKey(Map<String, String> map, int key) {
    String k = String.valueOf(key);
    if (map.containsKey(k)) {
      return map.get(k);
    }
    for (Map.Entry<String, String> e : map.entrySet()) {
      try {
        if (Integer.parseInt(e.getKey().trim()) == key) {
          return e.getValue();
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return map.values().iterator().next();
  }
}
