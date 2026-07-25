package org.hopper.presentation.theme;

import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.core.Constants;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.core.exception.HException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@HopMetadata(
    key = "theme",
    name = "Hopper Theme",
    description = "A theme with colors and fonts to use as default in the components")
@Getter
@Setter
public class HTheme extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty protected String description;

  @HopMetadataProperty protected List<HColorRGB> colors;

  @HopMetadataProperty protected HColorRGB backgroundColor;

  @HopMetadataProperty protected HColorRGB defaultColor;

  @HopMetadataProperty protected HFont defaultFont;

  @HopMetadataProperty protected HColorRGB borderColor;

  @HopMetadataProperty protected HFont horizontalDimensionsFont;

  @HopMetadataProperty protected HColorRGB horizontalDimensionsColor;

  @HopMetadataProperty protected HFont verticalDimensionsFont;

  @HopMetadataProperty protected HColorRGB verticalDimensionsColor;

  @HopMetadataProperty protected HFont factsFont;

  @HopMetadataProperty protected HColorRGB factsColor;

  @HopMetadataProperty protected HFont titleFont;

  @HopMetadataProperty protected HColorRGB titleColor;

  @HopMetadataProperty protected HColorRGB axisColor;

  @HopMetadataProperty protected HColorRGB gridColor;

  /**
   * Default font for table (and similar) column headers when the component does not set its own
   * {@code headerFont}. Falls back to {@link #defaultFont} via {@link #lookupHeaderFont()}.
   */
  @HopMetadataProperty protected HFont headerFont;

  /**
   * Ink/text color for table (and similar) column headers. Should contrast with {@link
   * #headerBackGroundColor}. Falls back to {@link #defaultColor} via {@link #lookupHeaderColor()}.
   */
  @HopMetadataProperty protected HColorRGB headerColor;

  /**
   * Default background fill for table (and similar) header cells when the component does not set
   * its own {@code headerBackGroundColor}. May be null (no header fill).
   */
  @HopMetadataProperty protected HColorRGB headerBackGroundColor;

  public HTheme() {
    colors = new ArrayList<>();
  }

  public HTheme(String name, String description, List<HColorRGB> colors) {
    this.name = name;
    this.description = description;
    this.colors = colors;
    this.backgroundColor = null;
    this.defaultColor = null;
    this.defaultFont = null;
    this.borderColor = null;
  }

  public HTheme(HTheme s) {
    this();
    this.name = s.name;
    this.description = s.description;
    for (HColorRGB color : s.getColors()) {
      colors.add(new HColorRGB(color));
    }
    this.backgroundColor = s.backgroundColor == null ? null : new HColorRGB(s.backgroundColor);
    this.defaultColor = s.defaultColor == null ? null : new HColorRGB(s.defaultColor);
    this.defaultFont = s.defaultFont == null ? null : new HFont(s.defaultFont);
    this.borderColor = s.borderColor == null ? null : new HColorRGB(s.borderColor);
    this.horizontalDimensionsFont =
        s.horizontalDimensionsFont == null ? null : new HFont(s.horizontalDimensionsFont);
    this.horizontalDimensionsColor =
        s.horizontalDimensionsColor == null ? null : new HColorRGB(s.horizontalDimensionsColor);
    this.verticalDimensionsFont =
        s.verticalDimensionsFont == null ? null : new HFont(s.verticalDimensionsFont);
    this.verticalDimensionsColor =
        s.verticalDimensionsColor == null ? null : new HColorRGB(s.verticalDimensionsColor);
    this.factsFont = s.factsFont == null ? null : new HFont(s.factsFont);
    this.factsColor = s.factsColor == null ? null : new HColorRGB(s.factsColor);
    this.titleFont = s.titleFont == null ? null : new HFont(s.titleFont);
    this.titleColor = s.titleColor == null ? null : new HColorRGB(s.titleColor);
    this.axisColor = s.axisColor == null ? null : new HColorRGB(s.axisColor);
    this.gridColor = s.gridColor == null ? null : new HColorRGB(s.gridColor);
    this.headerFont = s.headerFont == null ? null : new HFont(s.headerFont);
    this.headerColor = s.headerColor == null ? null : new HColorRGB(s.headerColor);
    this.headerBackGroundColor =
        s.headerBackGroundColor == null ? null : new HColorRGB(s.headerBackGroundColor);
  }

  public static final HTheme getDefault() {
    HTheme theme = new HTheme();

    theme.setName(Constants.DEFAULT_THEME_NAME);
    theme.setDescription(Constants.DEFAULT_THEME_DESCRIPTION);

    theme.getColors().clear();
    theme
        .getColors()
        .addAll(
            Arrays.asList(
                new HColorRGB("#003f5c"),
                new HColorRGB("#2f4b7c"),
                new HColorRGB("#665191"),
                new HColorRGB("#a05195"),
                new HColorRGB("#d45087"),
                new HColorRGB("#f95d6a"),
                new HColorRGB("#ff7c43"),
                new HColorRGB("#ffa600")));

    theme.setBackgroundColor(new HColorRGB("#ffffff")); // Simply white
    theme.setDefaultColor(new HColorRGB("#000000")); // Simply black
    theme.setDefaultFont(new HFont("Arial", "12", false, false));
    theme.setBorderColor(new HColorRGB("#f0f0f0")); // very light gray

    theme.setHorizontalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setHorizontalDimensionsColor(new HColorRGB("#000000"));
    theme.setVerticalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setVerticalDimensionsColor(new HColorRGB("#000000"));
    theme.setFactsFont(new HFont("Hack", "12", false, false));
    theme.setFactsColor(new HColorRGB("#000000"));
    theme.setTitleFont(new HFont("Arial", "10", true, true));
    theme.setTitleColor(new HColorRGB("#c8c8c8"));
    theme.setAxisColor(new HColorRGB("#000000"));
    theme.setGridColor(new HColorRGB("#c8c8c8"));
    theme.setHeaderFont(new HFont("Arial", "12", true, false));
    theme.setHeaderColor(new HColorRGB("#000000"));
    theme.setHeaderBackGroundColor(new HColorRGB("#e8e8e8"));

    return theme;
  }

  /**
   * Built-in dark default: deep slate background, light ink, series palette lifted for dark
   * surfaces.
   */
  /**
   * Built-in dark default (palette inspired by pdi-codebase-assessment.html): deep navy
   * surfaces, soft light ink, blue/cyan/violet series accents.
   */
  public static final HTheme getDefaultDark() {
    HTheme theme = new HTheme();
    theme.setName("Default Dark");
    theme.setDescription(
        "Built-in dark theme (PDI assessment palette): #0b1220 surfaces, blue/cyan accents");

    theme.getColors().clear();
    theme
        .getColors()
        .addAll(
            Arrays.asList(
                new HColorRGB("#3b82f6"), // accent blue
                new HColorRGB("#22d3ee"), // cyan
                new HColorRGB("#a78bfa"), // violet
                new HColorRGB("#34d399"), // green
                new HColorRGB("#fbbf24"), // amber
                new HColorRGB("#f87171"), // red
                new HColorRGB("#93c5fd"), // soft blue
                new HColorRGB("#67e8f9"))); // soft cyan

    theme.setBackgroundColor(new HColorRGB("#0b1220"));
    theme.setDefaultColor(new HColorRGB("#e8eef9"));
    theme.setDefaultFont(new HFont("Arial", "12", false, false));
    theme.setBorderColor(new HColorRGB("#1b2740"));

    theme.setHorizontalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setHorizontalDimensionsColor(new HColorRGB("#e8eef9"));
    theme.setVerticalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setVerticalDimensionsColor(new HColorRGB("#e8eef9"));
    theme.setFactsFont(new HFont("Hack", "12", false, false));
    theme.setFactsColor(new HColorRGB("#e8eef9"));
    theme.setTitleFont(new HFont("Arial", "10", true, true));
    theme.setTitleColor(new HColorRGB("#9aa8c0"));
    theme.setAxisColor(new HColorRGB("#9aa8c0"));
    theme.setGridColor(new HColorRGB("#1b2740"));
    theme.setHeaderFont(new HFont("Arial", "12", true, false));
    theme.setHeaderColor(new HColorRGB("#e8eef9"));
    theme.setHeaderBackGroundColor(new HColorRGB("#1b2740"));

    return theme;
  }

  /**
   * Whether this theme has enough ink/grid identity to paint components safely. Empty or
   * half-deserialized catalog objects (no default color and no grid color) are not usable.
   */
  public boolean isRenderable() {
    return defaultColor != null || gridColor != null;
  }

  public HColorRGB lookupDefaultColor() throws HException {
    if (defaultColor == null) {
      throw new HException("No default color defined in theme '" + name + "'");
    }
    return defaultColor;
  }

  public HFont lookupDefaultFont() throws HException {
    if (defaultFont == null) {
      throw new HException("No default font defined in theme '" + name + "'");
    }
    return defaultFont;
  }

  public HColorRGB lookupBackgroundColor() throws HException {
    if (backgroundColor == null && defaultColor == null) {
      throw new HException(
          "No background color nor default color defined in theme '" + name + "'");
    }
    if (backgroundColor != null) {
      return backgroundColor;
    }
    return HColorRGB.WHITE;
  }

  public HColorRGB lookupBorderColor() throws HException {
    if (borderColor == null && defaultColor == null) {
      throw new HException("No border color nor default color defined in theme '" + name + "'");
    }
    if (borderColor != null) {
      return borderColor;
    }
    return defaultColor;
  }

  public HColorRGB lookupHorizontalDimensionsColor() throws HException {
    if (horizontalDimensionsColor == null && defaultColor == null) {
      throw new HException(
          "No horizontal dimensions color nor default color defined in theme '" + name + "'");
    }
    if (horizontalDimensionsColor != null) {
      return horizontalDimensionsColor;
    }
    return defaultColor;
  }

  public HColorRGB lookupVerticalDimensionsColor() throws HException {
    if (verticalDimensionsColor == null && defaultColor == null) {
      throw new HException(
          "No vertical dimensions color nor default color defined in theme '" + name + "'");
    }
    if (verticalDimensionsColor != null) {
      return verticalDimensionsColor;
    }
    return defaultColor;
  }

  public HColorRGB lookupFactsColor() throws HException {
    if (factsColor == null && defaultColor == null) {
      throw new HException("No facts color nor default color defined in theme '" + name + "'");
    }
    if (factsColor != null) {
      return factsColor;
    }
    return defaultColor;
  }

  public HColorRGB lookupTitleColor() throws HException {
    if (titleColor == null && defaultColor == null) {
      throw new HException("No title color nor default color defined in theme '" + name + "'");
    }
    if (titleColor != null) {
      return titleColor;
    }
    return defaultColor;
  }

  public HColorRGB lookupAxisColor() throws HException {
    if (axisColor == null && defaultColor == null) {
      throw new HException("No axis color nor default color defined in theme '" + name + "'");
    }
    if (axisColor != null) {
      return axisColor;
    }
    return defaultColor;
  }

  public HColorRGB lookupGridColor() throws HException {
    if (gridColor == null && defaultColor == null) {
      throw new HException("No grid color nor default color defined in theme '" + name + "'");
    }
    if (gridColor != null) {
      return gridColor;
    }
    return defaultColor;
  }

  public HFont lookupHorizontalDimensionsFont() throws HException {
    if (horizontalDimensionsFont == null && defaultFont == null) {
      throw new HException(
          "No horizontal dimensions font nor default font defined in theme '" + name + "'");
    }
    if (horizontalDimensionsFont != null) {
      return horizontalDimensionsFont;
    }
    return defaultFont;
  }

  public HFont lookupVerticalDimensionsFont() throws HException {
    if (verticalDimensionsFont == null && defaultFont == null) {
      throw new HException(
          "No vertical dimensions font nor default font defined in theme '" + name + "'");
    }
    if (verticalDimensionsFont != null) {
      return verticalDimensionsFont;
    }
    return defaultFont;
  }

  public HFont lookupFactsFont() throws HException {
    if (factsFont == null && defaultFont == null) {
      throw new HException("No facts font nor default font defined in theme '" + name + "'");
    }
    if (factsFont != null) {
      return factsFont;
    }
    return defaultFont;
  }

  public HFont lookupTitleFont() throws HException {
    if (titleFont == null && defaultFont == null) {
      throw new HException("No title font nor default font defined in theme '" + name + "'");
    }
    if (titleFont != null) {
      return titleFont;
    }
    return defaultFont;
  }

  /**
   * Table / grid header font. Falls back to {@link #defaultFont} when unset so older theme JSON
   * without this property still measures and paints headers.
   */
  public HFont lookupHeaderFont() throws HException {
    if (headerFont == null && defaultFont == null) {
      throw new HException("No header font nor default font defined in theme '" + name + "'");
    }
    if (headerFont != null) {
      return headerFont;
    }
    return defaultFont;
  }

  /**
   * Table / grid header text (ink) color. Falls back to {@link #defaultColor} when unset so older
   * themes without this property remain readable on light headers.
   */
  public HColorRGB lookupHeaderColor() throws HException {
    if (headerColor == null && defaultColor == null) {
      throw new HException("No header color nor default color defined in theme '" + name + "'");
    }
    if (headerColor != null) {
      return headerColor;
    }
    return defaultColor;
  }

  /**
   * Table / grid header cell background. {@code null} means no header fill (component may still
   * leave the cell transparent). Does not fall back to {@link #backgroundColor} so body and header
   * stay distinct when only a page background is set.
   */
  public HColorRGB lookupHeaderBackGroundColor() {
    return headerBackGroundColor;
  }
}
