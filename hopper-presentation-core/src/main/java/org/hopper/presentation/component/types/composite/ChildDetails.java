package org.hopper.presentation.component.types.composite;

import org.hopper.core.HSize;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayoutResults;

public class ChildDetails {
  public HSize childExpectedSize;
  public HComponent childComponent;

  public ChildDetails() {}

  public ChildDetails(
      HLayoutResults childLayoutResults,
      HSize childExpectedSize,
      HComponent childComponent) {
    this.childExpectedSize = childExpectedSize;
    this.childComponent = childComponent;
  }
}
