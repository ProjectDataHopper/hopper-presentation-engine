package org.hopper.presentation.component.types.textblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.BasePresentationUtil;

class HTextBlockComponentTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();
  }

  @Test
  void multiLineHardBreaks_renderIntoSvg() throws Exception {
    HTextBlockComponent block = new HTextBlockComponent("Line one\nLine two\nLine three");
    block.setWrap(false);
    HComponent wrapper = new HComponent("Notes", block);
    wrapper.setLayout(HLayout.topLeftPage());

    String svg = wrapper.getSvgXml(400, 200, new MemoryMetadataProvider());
    assertTrue(svg.contains("Line one"), svg);
    assertTrue(svg.contains("Line two"), svg);
    assertTrue(svg.contains("Line three"), svg);
  }

  @Test
  void softWrap_usesMaxWidth() throws Exception {
    HTextBlockComponent block =
        new HTextBlockComponent(
            "alpha beta gamma delta epsilon zeta eta theta iota kappa");
    block.setWrap(true);
    block.setMaxWidth(80);
    HComponent wrapper = new HComponent("Wrapped", block);
    wrapper.setLayout(HLayout.topLeftPage());

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    HPresentation presentation = new HPresentation();
    presentation.setName("wrap-test");
    HPage page = new HPage(400, 300, 10, 10, 10, 10);
    page.setHeader(false);
    page.setFooter(false);
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, provider);
    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("test"), ctx, provider, Collections.emptyList());
    presentation.render(results, provider, ctx);

    TextBlockDetails details =
        (TextBlockDetails) results.getDataSet(wrapper, HTextBlockComponent.DATA_TEXT_BLOCK);
    assertTrue(details.getLayout().getLines().size() > 1);
    for (HTextLayout.Line line : details.getLayout().getLines()) {
      assertTrue(
          line.getWidth() <= 80,
          "line '" + line.getText() + "' width " + line.getWidth());
    }
  }

  @Test
  void paginate_splitsAcrossRenderPages() throws Exception {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 40; i++) {
      sb.append("Line ").append(i).append('\n');
    }
    HTextBlockComponent block = new HTextBlockComponent(sb.toString());
    block.setWrap(false);
    block.setPaginate(true);

    HComponent wrapper = new HComponent("LongNotes", block);
    // top of page, natural height; short page forces pagination
    wrapper.setLayout(HLayout.topLeftPage());

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    HPresentation presentation = new HPresentation();
    presentation.setName("paginate-test");
    // Short usable height so many lines need multiple pages
    HPage page = new HPage(300, 120, 10, 10, 10, 10);
    page.setHeader(false);
    page.setFooter(false);
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, provider);
    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("test"), ctx, provider, Collections.emptyList());
    presentation.render(results, provider, ctx);

    assertTrue(
        results.getRenderPages().size() > 1,
        "expected multi-page layout, pages=" + results.getRenderPages().size());

    // Drawn items registered with Text category
    boolean foundText =
        results.getRenderPages().stream()
            .flatMap(p -> p.getDrawnItems().stream())
            .anyMatch(d -> DrawnItem.Category.Text.name().equals(d.getCategory()));
    assertTrue(foundText);
  }

  @Test
  void fullPageWidth_wrapsToAttachmentWidth() throws Exception {
    HTextBlockComponent block =
        new HTextBlockComponent(
            "The quick brown fox jumps over the lazy dog repeatedly for wrapping.");
    block.setWrap(true);
    HComponent wrapper = new HComponent("Body", block);
    wrapper.setLayout(HLayout.fullPage());

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    HPresentation presentation = new HPresentation();
    presentation.setName("full-width");
    HPage page = new HPage(200, 400, 10, 10, 10, 10);
    page.setHeader(false);
    page.setFooter(false);
    page.getComponents().add(wrapper);
    presentation.getPages().add(page);

    PresentationRenderContext ctx = new PresentationRenderContext(presentation, provider);
    HLayoutResults results =
        presentation.doLayout(
            new LoggingObject("test"), ctx, provider, Collections.emptyList());

    TextBlockDetails details =
        (TextBlockDetails) results.getDataSet(wrapper, HTextBlockComponent.DATA_TEXT_BLOCK);
    // Page width between margins = 180
    assertTrue(details.getLayout().getLines().size() > 1);
    assertTrue(details.getWrapWidth() <= 180);
  }

  @Test
  void jsonRoundTrip_preservesFields() throws Exception {
    HTextBlockComponent block = new HTextBlockComponent("Hello\nWorld");
    block.setWrap(true);
    block.setMaxWidth(120);
    block.setPaginate(true);
    block.setHorizontalMargin(2);
    block.setVerticalMargin(3);
    block.setLineSpacing("1.5");
    block.setHorizontalAlignment(HHorizontalAlignment.CENTER);

    HPresentation presentation = new HPresentation();
    presentation.setName("text-rt");
    HPage page = HPage.getA4(false);
    page.getComponents().add(new HComponent("T", block));
    presentation.getPages().add(page);

    String json = presentation.toJsonString(true);
    HPresentation loaded = HPresentation.fromJsonString(json);
    HTextBlockComponent copy =
        (HTextBlockComponent) loaded.getPages().get(0).getComponents().get(0).getComponent();
    assertEquals("Hello\nWorld", copy.getText());
    assertTrue(copy.isWrap());
    assertEquals(120, copy.getMaxWidth());
    assertTrue(copy.isPaginate());
    assertEquals(2, copy.getHorizontalMargin());
    assertEquals(3, copy.getVerticalMargin());
    assertEquals("1.5", copy.getLineSpacing());
    assertEquals(HHorizontalAlignment.CENTER, copy.getHorizontalAlignment());
  }

  @Test
  void clone_copiesMetadata() {
    HTextBlockComponent a = new HTextBlockComponent("x");
    a.setMaxWidth(50);
    a.setPaginate(true);
    HTextBlockComponent b = a.clone();
    assertEquals("x", b.getText());
    assertEquals(50, b.getMaxWidth());
    assertTrue(b.isPaginate());
    assertFalse(a == b);
  }

  @Test
  void formSchema_includesMultiLineText() throws Exception {
    org.hopper.core.gui.form.GuiFormSchema schema =
        new org.hopper.core.gui.form.GuiFormSchemaBuilder()
            .buildComponentSchema("HTextBlockComponent");
    assertEquals("HTextBlockComponent", schema.getPluginId());
    boolean multi =
        schema.getSections().stream()
            .flatMap(s -> s.getFields().stream())
            .anyMatch(
                f ->
                    "text".equals(f.getFieldName())
                        && org.hopper.core.gui.form.GuiFormFieldType.MULTI_LINE_TEXT
                            == f.getType());
    assertTrue(multi, "expected MULTI_LINE_TEXT field for text");
  }
}
