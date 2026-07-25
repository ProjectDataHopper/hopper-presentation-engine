package org.hopper.presentation.component.types.textblock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cached layout state between processSourceData / doLayout / render. */
@Getter
@Setter
@NoArgsConstructor
public class TextBlockDetails {

  /** Variable-resolved source text. */
  private String text;

  /** Full layout for the final wrap width (all lines). */
  private HTextLayout.Result layout;

  /** Content width used for the stored layout (box width including margins). */
  private int wrapWidth;
}
