package org.hopper.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@JsonDeserialize(as = HFont.class)
public class HFont {

  @HopMetadataProperty @JsonProperty private String fontName;

  @HopMetadataProperty @JsonProperty private String fontSize;

  @HopMetadataProperty @JsonProperty private boolean bold;

  @HopMetadataProperty @JsonProperty private boolean italic;

  public HFont(String fontName, String fontSize, boolean bold, boolean italic) {
    this.fontName = fontName;
    this.fontSize = fontSize;
    this.bold = bold;
    this.italic = italic;
  }

  public HFont(HFont f) {
    this(f.fontName, f.fontSize, f.bold, f.italic);
  }
}
