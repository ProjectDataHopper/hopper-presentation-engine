package org.hopper.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.Color;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
@EqualsAndHashCode(of = {"r", "g", "b"})
@ToString(includeFieldNames = false)
public class HColorRGB {

  public static final HColorRGB BLACK = new HColorRGB("#000000");
  public static final HColorRGB WHITE = new HColorRGB("#ffffff");

  @HopMetadataProperty private int r;

  @HopMetadataProperty private int g;

  @HopMetadataProperty private int b;

  public HColorRGB() {
    r = 0;
    g = 140;
    b = 194;
  }

  public HColorRGB(int r, int g, int b) {
    this();
    this.r = r;
    this.g = g;
    this.b = b;
  }

  public HColorRGB(HColorRGB c) {
    this(c.r, c.g, c.b);
  }

  /**
   * Decode standard hex values like "#FFCCEE"
   *
   * @param hexValue The hex value to convert to RGB
   */
  public HColorRGB(String hexValue) {
    Color color = Color.decode(hexValue);
    this.r = color.getRed();
    this.g = color.getGreen();
    this.b = color.getBlue();
  }

  @Override
  public String toString() {
    return "Color(" + r + "," + g + "," + b + ")";
  }

  @JsonIgnore
  public String getHexColor() {
    Color color = new Color(getR(), getG(), getB());
    String hex = Integer.toHexString(color.getRGB() & 0xffffff);
    if (hex.length() < 6) {
      hex = "0" + hex;
    }
    return "#" + hex;
  }
}
