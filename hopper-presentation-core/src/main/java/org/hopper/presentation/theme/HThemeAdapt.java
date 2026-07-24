package org.hopper.presentation.theme;

import java.util.ArrayList;
import java.util.List;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;

/**
 * Derive a readable dark (or light) companion theme from an authored theme without naive RGB
 * inversion of brand hues.
 */
public final class HThemeAdapt {

  private HThemeAdapt() {}

  /** Build a dark-mode theme from a light-oriented source. */
  public static HTheme forDarkMode(HTheme light) {
    if (light == null) {
      return HTheme.getDefaultDark();
    }
    HTheme dark = new HTheme();
    dark.setName(
        (light.getName() != null ? light.getName() : "Theme") + " (dark, derived)");
    dark.setDescription(
        "Auto-derived dark variant of '"
            + (light.getName() != null ? light.getName() : "?")
            + "'");

    HColorRGB darkBg = new HColorRGB("#0b1220");
    dark.setBackgroundColor(darkBg);
    dark.setDefaultColor(adaptInk(light.getDefaultColor(), darkBg, true));
    dark.setBorderColor(adaptMuted(light.getBorderColor(), darkBg, true));
    dark.setHorizontalDimensionsColor(
        adaptInk(light.getHorizontalDimensionsColor(), darkBg, true));
    dark.setVerticalDimensionsColor(adaptInk(light.getVerticalDimensionsColor(), darkBg, true));
    dark.setFactsColor(adaptInk(light.getFactsColor(), darkBg, true));
    dark.setTitleColor(adaptMuted(light.getTitleColor(), darkBg, true));
    dark.setAxisColor(adaptInk(light.getAxisColor(), darkBg, true));
    dark.setGridColor(adaptMuted(light.getGridColor(), darkBg, true));

    dark.setDefaultFont(copyFont(light.getDefaultFont()));
    dark.setHorizontalDimensionsFont(copyFont(light.getHorizontalDimensionsFont()));
    dark.setVerticalDimensionsFont(copyFont(light.getVerticalDimensionsFont()));
    dark.setFactsFont(copyFont(light.getFactsFont()));
    dark.setTitleFont(copyFont(light.getTitleFont()));

    List<HColorRGB> palette = new ArrayList<>();
    if (light.getColors() != null) {
      for (HColorRGB c : light.getColors()) {
        palette.add(adaptSeries(c, true));
      }
    }
    if (palette.isEmpty()) {
      palette.addAll(HTheme.getDefaultDark().getColors());
    }
    dark.setColors(palette);
    return dark;
  }

  /** Build a light-mode theme from a dark-oriented source (less common). */
  public static HTheme forLightMode(HTheme darkSource) {
    if (darkSource == null) {
      return HTheme.getDefault();
    }
    HTheme light = new HTheme();
    light.setName(
        (darkSource.getName() != null ? darkSource.getName() : "Theme") + " (light, derived)");
    light.setDescription("Auto-derived light variant");

    HColorRGB lightBg = new HColorRGB("#ffffff");
    light.setBackgroundColor(lightBg);
    light.setDefaultColor(adaptInk(darkSource.getDefaultColor(), lightBg, false));
    light.setBorderColor(adaptMuted(darkSource.getBorderColor(), lightBg, false));
    light.setHorizontalDimensionsColor(
        adaptInk(darkSource.getHorizontalDimensionsColor(), lightBg, false));
    light.setVerticalDimensionsColor(
        adaptInk(darkSource.getVerticalDimensionsColor(), lightBg, false));
    light.setFactsColor(adaptInk(darkSource.getFactsColor(), lightBg, false));
    light.setTitleColor(adaptMuted(darkSource.getTitleColor(), lightBg, false));
    light.setAxisColor(adaptInk(darkSource.getAxisColor(), lightBg, false));
    light.setGridColor(adaptMuted(darkSource.getGridColor(), lightBg, false));

    light.setDefaultFont(copyFont(darkSource.getDefaultFont()));
    light.setHorizontalDimensionsFont(copyFont(darkSource.getHorizontalDimensionsFont()));
    light.setVerticalDimensionsFont(copyFont(darkSource.getVerticalDimensionsFont()));
    light.setFactsFont(copyFont(darkSource.getFactsFont()));
    light.setTitleFont(copyFont(darkSource.getTitleFont()));

    List<HColorRGB> palette = new ArrayList<>();
    if (darkSource.getColors() != null) {
      for (HColorRGB c : darkSource.getColors()) {
        palette.add(adaptSeries(c, false));
      }
    }
    if (palette.isEmpty()) {
      palette.addAll(HTheme.getDefault().getColors());
    }
    light.setColors(palette);
    return light;
  }

  /** Relative luminance 0–1 (sRGB). */
  public static double relativeLuminance(HColorRGB c) {
    if (c == null) {
      return 0;
    }
    double r = linearize(c.getR() / 255.0);
    double g = linearize(c.getG() / 255.0);
    double b = linearize(c.getB() / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  public static double contrastRatio(HColorRGB a, HColorRGB b) {
    double l1 = relativeLuminance(a);
    double l2 = relativeLuminance(b);
    double lighter = Math.max(l1, l2);
    double darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  }

  /** Text/ink: ensure strong contrast vs background; keep hue when possible. */
  static HColorRGB adaptInk(HColorRGB source, HColorRGB background, boolean forDarkSurface) {
    HColorRGB fallback = forDarkSurface ? new HColorRGB("#e8eef9") : new HColorRGB("#1a1a1a");
    if (source == null) {
      return fallback;
    }
    if (contrastRatio(source, background) >= 4.5) {
      // Already readable; slight lift away from pure black/white on dark for softer look
      if (forDarkSurface && relativeLuminance(source) < 0.15) {
        return fallback;
      }
      return new HColorRGB(source);
    }
    // Flip toward opposite polarity while preserving hue via HSL
    float[] hsl = rgbToHsl(source.getR(), source.getG(), source.getB());
    if (forDarkSurface) {
      hsl[2] = Math.max(0.72f, Math.min(0.92f, 1.0f - hsl[2] * 0.3f));
    } else {
      hsl[2] = Math.min(0.28f, Math.max(0.08f, 1.0f - hsl[2] * 0.3f));
    }
    int[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
    HColorRGB adapted = new HColorRGB(rgb[0], rgb[1], rgb[2]);
    if (contrastRatio(adapted, background) < 4.5) {
      return fallback;
    }
    return adapted;
  }

  static HColorRGB adaptMuted(HColorRGB source, HColorRGB background, boolean forDarkSurface) {
    if (source == null) {
      return forDarkSurface ? new HColorRGB("#9aa8c0") : new HColorRGB("#c8c8c8");
    }
    float[] hsl = rgbToHsl(source.getR(), source.getG(), source.getB());
    if (forDarkSurface) {
      hsl[2] = clamp(0.40f, 0.58f, hsl[2] < 0.5f ? 0.48f : hsl[2]);
      hsl[1] = Math.min(hsl[1], 0.25f);
    } else {
      hsl[2] = clamp(0.55f, 0.80f, hsl[2] > 0.5f ? 0.70f : hsl[2]);
    }
    int[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
    return new HColorRGB(rgb[0], rgb[1], rgb[2]);
  }

  /** Series/categorical: keep hue, target a readable lightness band. */
  static HColorRGB adaptSeries(HColorRGB source, boolean forDarkSurface) {
    if (source == null) {
      return forDarkSurface ? new HColorRGB("#6ea8fe") : new HColorRGB("#003f5c");
    }
    float[] hsl = rgbToHsl(source.getR(), source.getG(), source.getB());
    // Preserve hue; ensure chroma is not washed out
    if (hsl[1] < 0.15f) {
      hsl[1] = 0.35f;
    }
    if (forDarkSurface) {
      // Lift dark swatches; slightly rein in near-white
      hsl[2] = clamp(0.52f, 0.78f, hsl[2] < 0.45f ? hsl[2] + 0.35f : hsl[2]);
    } else {
      hsl[2] = clamp(0.22f, 0.55f, hsl[2] > 0.6f ? hsl[2] - 0.25f : hsl[2]);
    }
    int[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
    return new HColorRGB(rgb[0], rgb[1], rgb[2]);
  }

  private static double linearize(double c) {
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  private static HFont copyFont(HFont f) {
    return f == null ? null : new HFont(f);
  }

  private static float clamp(float min, float max, float v) {
    return Math.max(min, Math.min(max, v));
  }

  /** H in [0,360), S,L in [0,1]. */
  static float[] rgbToHsl(int r, int g, int b) {
    float rf = r / 255f;
    float gf = g / 255f;
    float bf = b / 255f;
    float max = Math.max(rf, Math.max(gf, bf));
    float min = Math.min(rf, Math.min(gf, bf));
    float h;
    float s;
    float l = (max + min) / 2f;
    if (max == min) {
      h = s = 0f;
    } else {
      float d = max - min;
      s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
      if (max == rf) {
        h = (gf - bf) / d + (gf < bf ? 6f : 0f);
      } else if (max == gf) {
        h = (bf - rf) / d + 2f;
      } else {
        h = (rf - gf) / d + 4f;
      }
      h /= 6f;
    }
    return new float[] {h * 360f, s, l};
  }

  static int[] hslToRgb(float h, float s, float l) {
    h = ((h % 360f) + 360f) % 360f / 360f;
    float r;
    float g;
    float b;
    if (s == 0f) {
      r = g = b = l;
    } else {
      float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
      float p = 2 * l - q;
      r = hue2rgb(p, q, h + 1f / 3f);
      g = hue2rgb(p, q, h);
      b = hue2rgb(p, q, h - 1f / 3f);
    }
    return new int[] {
      Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)
    };
  }

  private static float hue2rgb(float p, float q, float t) {
    if (t < 0f) {
      t += 1f;
    }
    if (t > 1f) {
      t -= 1f;
    }
    if (t < 1f / 6f) {
      return p + (q - p) * 6f * t;
    }
    if (t < 1f / 2f) {
      return q;
    }
    if (t < 2f / 3f) {
      return p + (q - p) * (2f / 3f - t) * 6f;
    }
    return p;
  }
}
