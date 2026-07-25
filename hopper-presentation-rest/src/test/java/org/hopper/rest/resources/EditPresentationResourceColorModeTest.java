package org.hopper.rest.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditPresentationResourceColorModeTest {

  @Test
  void matchesBaseModeIgnoringLayoutSuffixes() {
    assertTrue(EditPresentationResource.layoutColorModeMatches("light", "light"));
    assertTrue(EditPresentationResource.layoutColorModeMatches("light", "light|maxPg=10"));
    assertTrue(
        EditPresentationResource.layoutColorModeMatches(
            "light", "light|noPeerBreak|maxPg=10"));
    assertTrue(EditPresentationResource.layoutColorModeMatches("dark", "dark|maxPg=10"));
    assertTrue(EditPresentationResource.layoutColorModeMatches("DARK", "dark"));
  }

  @Test
  void rejectsDifferentBaseMode() {
    assertFalse(EditPresentationResource.layoutColorModeMatches("dark", "light|maxPg=10"));
    assertFalse(EditPresentationResource.layoutColorModeMatches("light", "dark"));
  }
}
