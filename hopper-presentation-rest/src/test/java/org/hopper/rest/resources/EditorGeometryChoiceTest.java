package org.hopper.rest.resources;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.hopper.core.HGeometry;
import org.junit.jupiter.api.Test;

/**
 * WYSIWYG editor hit-geometry: always use the layout envelope when present so charts (bars, lines,
 * pies) are selectable over their full attached box — not only the drawn bars/title ink.
 */
class EditorGeometryChoiceTest {

  @Test
  void sparseTitleInkKeepsFullEnvelope() {
    HGeometry envelope = new HGeometry(40, 90, 1000, 230);
    HGeometry titleOnly = new HGeometry(350, 95, 220, 16);

    assertSame(
        envelope,
        RenderResource.chooseEditorGeometry(envelope, titleOnly),
        "title-only ink must not shrink the editor hit box");
  }

  @Test
  void barClusterInkKeepsFullEnvelope() {
    // Company Ships Detail: full-width chart box vs union of a few bars
    HGeometry envelope = new HGeometry(20, 90, 1080, 240);
    HGeometry twoBars = new HGeometry(280, 120, 420, 180);

    assertSame(
        envelope,
        RenderResource.chooseEditorGeometry(envelope, twoBars),
        "bar ink must not replace the full chart layout envelope");
  }

  @Test
  void densePieInkStillUsesEnvelope() {
    HGeometry envelope = new HGeometry(20, 40, 800, 500);
    HGeometry pieInk = new HGeometry(40, 60, 420, 380);

    // Whole-component selection uses the layout box (legend/margins included)
    assertSame(envelope, RenderResource.chooseEditorGeometry(envelope, pieInk));
  }

  @Test
  void missingEnvelopeFallsBackToInk() {
    HGeometry ink = new HGeometry(10, 10, 50, 50);
    assertSame(ink, RenderResource.chooseEditorGeometry(null, ink));
    assertSame(ink, RenderResource.chooseEditorGeometry(new HGeometry(0, 0, 0, 0), ink));
  }

  @Test
  void missingInkUsesEnvelope() {
    HGeometry envelope = new HGeometry(5, 5, 100, 80);
    assertSame(envelope, RenderResource.chooseEditorGeometry(envelope, null));
  }

  @Test
  void bothMissingReturnsNull() {
    assertNull(RenderResource.chooseEditorGeometry(null, null));
  }
}
