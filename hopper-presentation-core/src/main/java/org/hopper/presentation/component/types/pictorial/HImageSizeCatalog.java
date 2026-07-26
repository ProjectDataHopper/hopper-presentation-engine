package org.hopper.presentation.component.types.pictorial;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType;

/**
 * Discrete image sizes / aspect ratios supported for pictorial generation.
 *
 * <p>External AI APIs do not accept arbitrary pixels (e.g. 400×1200). The UI and generator must only
 * offer combinations that map cleanly to provider capabilities, then cover-crop to the catalog
 * output size (no white letterboxing).
 */
public final class HImageSizeCatalog {

  private HImageSizeCatalog() {}

  public enum AspectPreset {
    SQUARE_1_1("1:1", "Square (1:1)"),
    PORTRAIT_3_4("3:4", "Portrait 3:4 (recommended for glass)"),
    PORTRAIT_2_3("2:3", "Portrait 2:3"),
    PORTRAIT_9_16("9:16", "Portrait 9:16 (tall)"),
    LANDSCAPE_16_9("16:9", "Landscape 16:9");

    public final String ratioLabel;
    public final String displayName;

    AspectPreset(String ratioLabel, String displayName) {
      this.ratioLabel = ratioLabel;
      this.displayName = displayName;
    }

    public static AspectPreset fromId(String id) {
      if (id == null || id.isBlank()) {
        return PORTRAIT_3_4;
      }
      try {
        return AspectPreset.valueOf(id.trim().toUpperCase().replace('-', '_'));
      } catch (Exception e) {
        // also accept ratio labels
        for (AspectPreset p : values()) {
          if (p.ratioLabel.equals(id.trim()) || p.name().equalsIgnoreCase(id.trim())) {
            return p;
          }
        }
        return PORTRAIT_3_4;
      }
    }
  }

  public enum ResolutionTier {
    SMALL,
    MEDIUM,
    LARGE;

    public static ResolutionTier fromId(String id) {
      if (id == null || id.isBlank()) {
        return MEDIUM;
      }
      try {
        return ResolutionTier.valueOf(id.trim().toUpperCase());
      } catch (Exception e) {
        return MEDIUM;
      }
    }
  }

  public static final class ResolvedSize {
    public final AspectPreset preset;
    public final ResolutionTier tier;
    public final int width;
    public final int height;
    /** OpenAI DALL·E {@code size} string when applicable, else null. */
    public final String openaiSize;
    /** Imagen / generic aspect ratio string when applicable. */
    public final String aspectRatioApi;

    public ResolvedSize(
        AspectPreset preset,
        ResolutionTier tier,
        int width,
        int height,
        String openaiSize,
        String aspectRatioApi) {
      this.preset = preset;
      this.tier = tier;
      this.width = width;
      this.height = height;
      this.openaiSize = openaiSize;
      this.aspectRatioApi = aspectRatioApi;
    }
  }

  /** Presets allowed for a provider (Grok locked to square until aspect API is confirmed). */
  public static Set<AspectPreset> allowedPresets(ProviderType provider) {
    if (provider == null || provider == ProviderType.BUILTIN) {
      return EnumSet.allOf(AspectPreset.class);
    }
    switch (provider) {
      case OPENAI_DALLE:
        return EnumSet.of(
            AspectPreset.SQUARE_1_1, AspectPreset.PORTRAIT_9_16, AspectPreset.LANDSCAPE_16_9);
      case GOOGLE_IMAGEN:
        return EnumSet.of(
            AspectPreset.SQUARE_1_1,
            AspectPreset.PORTRAIT_3_4,
            AspectPreset.PORTRAIT_9_16,
            AspectPreset.LANDSCAPE_16_9);
      case XAI_GROK:
        // Grok image generations historically return square; avoid useless tall letterbox requests
        return EnumSet.of(AspectPreset.SQUARE_1_1);
      default:
        return EnumSet.of(AspectPreset.SQUARE_1_1);
    }
  }

  public static AspectPreset coercePreset(ProviderType provider, AspectPreset requested) {
    Set<AspectPreset> allowed = allowedPresets(provider);
    if (allowed.contains(requested)) {
      return requested;
    }
    // Prefer portrait glass shape when available
    if (allowed.contains(AspectPreset.PORTRAIT_3_4)) {
      return AspectPreset.PORTRAIT_3_4;
    }
    if (allowed.contains(AspectPreset.PORTRAIT_9_16)) {
      return AspectPreset.PORTRAIT_9_16;
    }
    return AspectPreset.SQUARE_1_1;
  }

  /**
   * Resolve output pixel size and provider API size hints for the given preset/tier/provider.
   */
  public static ResolvedSize resolve(
      ProviderType provider, AspectPreset preset, ResolutionTier tier) {
    ProviderType p = provider != null ? provider : ProviderType.BUILTIN;
    AspectPreset a = coercePreset(p, preset != null ? preset : AspectPreset.PORTRAIT_3_4);
    ResolutionTier t = tier != null ? tier : ResolutionTier.MEDIUM;

    int w;
    int h;
    String openai = null;
    String aspectApi = a.ratioLabel;

    switch (a) {
      case SQUARE_1_1:
        w = h = switch (t) {
          case SMALL -> 512;
          case LARGE -> 1024;
          default -> 768;
        };
        openai = "1024x1024";
        break;
      case PORTRAIT_3_4:
        switch (t) {
          case SMALL -> {
            w = 384;
            h = 512;
          }
          case LARGE -> {
            w = 768;
            h = 1024;
          }
          default -> {
            w = 576;
            h = 768;
          }
        }
        // DALL·E has no 3:4 — coerced away for OpenAI
        break;
      case PORTRAIT_2_3:
        switch (t) {
          case SMALL -> {
            w = 341;
            h = 512;
          }
          case LARGE -> {
            w = 682;
            h = 1024;
          }
          default -> {
            w = 512;
            h = 768;
          }
        }
        break;
      case PORTRAIT_9_16:
        switch (t) {
          case SMALL -> {
            w = 288;
            h = 512;
          }
          case LARGE -> {
            w = 1024;
            h = 1792;
          }
          default -> {
            w = 576;
            h = 1024;
          }
        }
        openai = "1024x1792";
        break;
      case LANDSCAPE_16_9:
        switch (t) {
          case SMALL -> {
            w = 512;
            h = 288;
          }
          case LARGE -> {
            w = 1792;
            h = 1024;
          }
          default -> {
            w = 1024;
            h = 576;
          }
        }
        openai = "1792x1024";
        break;
      default:
        w = 768;
        h = 768;
        openai = "1024x1024";
    }

    // OpenAI only accepts three sizes — force API size to match preset after coerce
    if (p == ProviderType.OPENAI_DALLE) {
      if (a == AspectPreset.SQUARE_1_1) {
        openai = "1024x1024";
        if (t == ResolutionTier.LARGE) {
          w = h = 1024;
        }
      } else if (a == AspectPreset.PORTRAIT_9_16) {
        openai = "1024x1792";
        if (t == ResolutionTier.LARGE) {
          w = 1024;
          h = 1792;
        }
      } else if (a == AspectPreset.LANDSCAPE_16_9) {
        openai = "1792x1024";
        if (t == ResolutionTier.LARGE) {
          w = 1792;
          h = 1024;
        }
      }
    }

    return new ResolvedSize(a, t, w, h, openai, aspectApi);
  }

  /** JSON-friendly list for REST / admin UI. */
  public static List<Map<String, Object>> optionsForProvider(ProviderType provider) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (AspectPreset preset : allowedPresets(provider)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", preset.name());
      row.put("ratio", preset.ratioLabel);
      row.put("label", preset.displayName);
      Map<String, Object> tiers = new LinkedHashMap<>();
      for (ResolutionTier tier : ResolutionTier.values()) {
        ResolvedSize r = resolve(provider, preset, tier);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("width", r.width);
        t.put("height", r.height);
        t.put("label", tier.name().charAt(0) + tier.name().substring(1).toLowerCase()
            + " (" + r.width + "×" + r.height + ")");
        tiers.put(tier.name(), t);
      }
      row.put("tiers", tiers);
      list.add(row);
    }
    return list;
  }
}
