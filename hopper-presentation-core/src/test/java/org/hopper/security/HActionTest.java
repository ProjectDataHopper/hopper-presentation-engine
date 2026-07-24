package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HActionTest {

  @Test
  public void fromCodeRoundTrip() {
    for (HAction action : HAction.values()) {
      assertEquals(action, HAction.requireCode(action.code()));
      assertTrue(HAction.fromCode(action.code().toUpperCase()).isPresent());
    }
  }

  @Test
  public void unknownCodeEmpty() {
    assertTrue(HAction.fromCode("no.such.action").isEmpty());
    assertTrue(HAction.fromCode(null).isEmpty());
  }
}
