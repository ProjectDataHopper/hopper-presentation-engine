package org.hopper.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class HSize {

  @HopMetadataProperty private int width;
  @HopMetadataProperty private int height;

  public HSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public HSize(HSize size) {
    this(size.width, size.height);
  }

  @Override
  public String toString() {
    return "HSize(" + width + "x" + height + ")";
  }

  @JsonIgnore
  public boolean isDefined() {
    return width > 0 && height > 0;
  }
}
