package org.hopper.presentation.component.types.composite;

import org.hopper.core.HSize;

import java.util.ArrayList;
import java.util.List;

public class CompositeDetails {

  public List<ChildDetails> childDetails;
  public HSize size;

  public CompositeDetails() {
    this.childDetails = new ArrayList<>();
  }
}
