package org.hopper.render.svg;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.svg.HSvgComponent;
import org.hopper.presentation.component.types.svg.ScaleType;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.context.PresentationRenderContext;
import org.junit.jupiter.api.Test;

/**
 * Regression: SVG embed must compose with the GC margin translate used for header/footer.
 * HopSvgGraphics2D.embedSvg() replaces the element transform and previously dropped margins
 * when no style group had absorbed them — icons appeared shifted up/left by the page margin.
 */
public class InventoryPitHeaderSvgTest extends HPresentationTestBase {

  @Test
  public void headerSvgEmbedIncludesPageMarginTranslate() throws Exception {
    metadataProvider.getSerializer(HTheme.class).save(HTheme.getDefault());

    HPresentation presentation = new HPresentation();
    presentation.setName("InventoryPitHeader");
    presentation.setDefaultThemeName(HTheme.getDefault().getName());

    // Match Inventory PIT header: top/bottom/right only, height 50, logo scaled with MIN
    HPage header = new HPage(1073, 50, 0, 0, 0, 0);
    header.setHeader(true);
    HSvgComponent svg = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
    svg.setBorder(true);
    HComponent c = new HComponent("header-logo", svg);
    c.setLayout(new HLayoutBuilder().top().bottom().right().build());
    header.getComponents().add(c);
    presentation.setHeader(header);

    HPage page = HPage.getA4(false); // left/top margin 25
    presentation.getPages().add(page);

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadataProvider);
    HLayoutResults results =
        presentation.doLayout(parent, renderContext, metadataProvider, java.util.Collections.emptyList());
    presentation.render(results, metadataProvider, renderContext);

    String xml = results.getRenderPages().get(0).getSvgXml();

    // Border is drawn at local (1023,0) with GC translate(25,25) → page (1048,25)
    // Embed must land at the same origin (margin included), not (1023,0).
    Pattern embed =
        Pattern.compile(
            "<g[^>]*filename=\"[^\"]*hopper-presentation-logo\\.svg\"[^>]*transform=\"([^\"]+)\"");
    Matcher m = embed.matcher(xml);
    assertTrue(m.find(), "Expected embedded logo group with transform");
    String transform = m.group(1);
    assertTrue(
        transform.contains("1048") && transform.contains("25"),
        "Embed transform should include page margin so logo aligns with border; was: " + transform);
  }
}
