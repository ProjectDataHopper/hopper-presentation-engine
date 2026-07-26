package org.hopper.render.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.GenerationRequest;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.ClipDirection;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;

class HPictorialChartRenderTest extends HPresentationTestBase {

  @TempDir File tempAssetsDir;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  void testPictorialChartStepImagesRender() throws Exception {
    HPictorialChartComponent pictorial = new HPictorialChartComponent();
    pictorial.setShowValueLabel(true);

    GenerationRequest req = new GenerationRequest();
    req.presentationName = "TestPres";
    req.componentName = "PictorialStep";
    req.prompt = "Test fill level";
    req.renderMode = RenderMode.STEP_IMAGES;
    req.stepSize = 25;
    req.outputDirectory = tempAssetsDir;
    req.relativeAssetUrlPrefix = tempAssetsDir.getAbsolutePath();

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    service.generateAssets(pictorial, req);

    HPresentation presentation = new HPresentation();
    presentation.setName("PictorialStepPresentation");
    HPage page = new HPage();

    HComponent comp = new HComponent("PictorialStep", pictorial);
    comp.setLayout(new HLayoutBuilder().left().top().build());
    page.getComponents().add(comp);
    presentation.getPages().add(page);

    HLayoutResults layoutResults =
        presentation.doLayout(
            parent, createRenderContext(presentation), metadataProvider, List.of());
    presentation.render(layoutResults, metadataProvider);

    List<HRenderPage> renderPages = layoutResults.getRenderPages();
    assertFalse(renderPages.isEmpty());
    String svgXml = renderPages.get(0).getSvgXml();
    assertNotNull(svgXml);
    assertTrue(svgXml.contains("<svg"));
  }

  @Test
  void testPictorialChartClippedLayersRender() throws Exception {
    HPictorialChartComponent pictorial = new HPictorialChartComponent();
    pictorial.setShowValueLabel(true);
    pictorial.setClipDirection(ClipDirection.BOTTOM_TO_TOP);

    GenerationRequest req = new GenerationRequest();
    req.presentationName = "TestPres";
    req.componentName = "PictorialClip";
    req.prompt = "Battery meter";
    req.renderMode = RenderMode.CLIPPED_LAYERS;
    req.outputDirectory = tempAssetsDir;
    req.relativeAssetUrlPrefix = tempAssetsDir.getAbsolutePath();

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    service.generateAssets(pictorial, req);

    HPresentation presentation = new HPresentation();
    presentation.setName("PictorialClipPresentation");
    HPage page = new HPage();

    HComponent comp = new HComponent("PictorialClip", pictorial);
    comp.setLayout(new HLayoutBuilder().left().top().build());
    page.getComponents().add(comp);
    presentation.getPages().add(page);

    HLayoutResults layoutResults =
        presentation.doLayout(
            parent, createRenderContext(presentation), metadataProvider, List.of());
    presentation.render(layoutResults, metadataProvider);

    List<HRenderPage> renderPages = layoutResults.getRenderPages();
    assertFalse(renderPages.isEmpty());
    String svgXml = renderPages.get(0).getSvgXml();
    assertNotNull(svgXml);
    assertTrue(svgXml.contains("<svg"));
  }

  @Test
  void testPictorialSeriesMetadataRender() throws Exception {
    org.hopper.presentation.component.types.pictorial.HPictorialSeries series =
        new org.hopper.presentation.component.types.pictorial.HPictorialSeries(
            "beer-series", "Beer Series", RenderMode.STEP_IMAGES);

    GenerationRequest req = new GenerationRequest();
    req.presentationName = "pictorial-series";
    req.componentName = "beer-series";
    req.prompt = "Beer glass fill";
    req.renderMode = RenderMode.STEP_IMAGES;
    req.stepSize = 25;
    req.outputDirectory = tempAssetsDir;
    req.relativeAssetUrlPrefix = tempAssetsDir.getAbsolutePath();

    HAiImageGeneratorService service = new HAiImageGeneratorService();
    service.generateSeriesAssets(series, req);

    metadataProvider
        .getSerializer(org.hopper.presentation.component.types.pictorial.HPictorialSeries.class)
        .save(series);

    HPictorialChartComponent pictorial = new HPictorialChartComponent();
    pictorial.setSeriesName("beer-series");
    pictorial.setShowValueLabel(true);

    HPresentation presentation = new HPresentation();
    presentation.setName("PictorialSeriesPresentation");
    HPage page = new HPage();

    HComponent comp = new HComponent("PictorialComp", pictorial);
    comp.setLayout(new HLayoutBuilder().left().top().build());
    page.getComponents().add(comp);
    presentation.getPages().add(page);

    HLayoutResults layoutResults =
        presentation.doLayout(
            parent, createRenderContext(presentation), metadataProvider, List.of());
    presentation.render(layoutResults, metadataProvider);

    List<HRenderPage> renderPages = layoutResults.getRenderPages();
    assertFalse(renderPages.isEmpty());
    String svgXml = renderPages.get(0).getSvgXml();
    assertNotNull(svgXml);
    assertTrue(svgXml.contains("<svg"));
  }

  @Test
  void testMultiCategoryPictorialRendersLabels() throws Exception {
    HPictorialChartComponent pictorial = new HPictorialChartComponent();
    pictorial.setCategoryColumn("region");
    pictorial.setValueColumn("pct");
    pictorial.setDomainMin("0");
    pictorial.setDomainMax("100");
    pictorial.setShowValueLabel(true);
    pictorial.setShowCategoryLabel(true);
    pictorial.setLabelFormat("%.0f%%");
    pictorial.setSourceConnectorName("regions");

    GenerationRequest req = new GenerationRequest();
    req.renderMode = RenderMode.STEP_IMAGES;
    req.stepSize = 25;
    req.outputDirectory = tempAssetsDir;
    req.relativeAssetUrlPrefix = tempAssetsDir.getAbsolutePath();
    new HAiImageGeneratorService().generateAssets(pictorial, req);

    File csv = new File(tempAssetsDir, "regions.csv");
    java.nio.file.Files.writeString(
        csv.toPath(),
        "region,pct\nBenelux,95\nGermany,82\nFrance,72\n");

    org.hopper.presentation.connector.types.csv.HCsvConnector csvConn =
        new org.hopper.presentation.connector.types.csv.HCsvConnector();
    csvConn.setFilename(csv.getAbsolutePath());
    csvConn.setHeaderPresent(true);
    csvConn.setSeparator(",");
    csvConn.setFields(
        List.of(
            new org.hopper.presentation.connector.types.csv.HCsvConnector.CsvField(
                "region", "String"),
            new org.hopper.presentation.connector.types.csv.HCsvConnector.CsvField(
                "pct", "Number")));

    HConnector connector = new HConnector();
    connector.setName("regions");
    connector.setConnector(csvConn);
    metadataProvider.getSerializer(HConnector.class).save(connector);

    HPresentation presentation = new HPresentation();
    presentation.setName("MultiPictorial");
    HPage page = new HPage();
    // left+top only so expected size (multi-item preferred width) drives geometry
    HComponent comp = new HComponent("pict", pictorial);
    comp.setLayout(new HLayoutBuilder().left().top().build());
    page.getComponents().add(comp);
    presentation.getPages().add(page);

    HLayoutResults layoutResults =
        presentation.doLayout(
            parent, createRenderContext(presentation), metadataProvider, List.of());

    HPictorialChartComponent.PictorialDetails details =
        (HPictorialChartComponent.PictorialDetails)
            layoutResults.getDataSet(comp, HPictorialChartComponent.DATA_PICTORIAL_DETAILS);
    assertNotNull(details);
    assertNotNull(details.items);
    assertEquals(3, details.items.size(), "one pictorial cell per category row");
    assertEquals("Benelux", details.items.get(0).category);
    assertEquals(95.0, details.items.get(0).rawValue, 0.01);
    assertEquals("France", details.items.get(2).category);
    for (HPictorialChartComponent.PictorialItem item : details.items) {
      assertNotNull(item.primaryImage, "step image for " + item.category);
      assertTrue(item.percentage >= 0 && item.percentage <= 100);
    }
    assertTrue(details.imageSize.getWidth() > details.naturalImageW);

    presentation.render(layoutResults, metadataProvider);

    String svgXml = layoutResults.getRenderPages().get(0).getSvgXml();
    assertNotNull(svgXml);
    assertTrue(svgXml.contains("<svg"));
    // Batik may embed rasters as <image> or data URI paths; SVG must be non-trivial
    assertTrue(svgXml.length() > 500, "expected non-empty SVG content");
  }

  private org.hopper.render.context.PresentationRenderContext createRenderContext(
      HPresentation presentation) {
    return new org.hopper.render.context.PresentationRenderContext(presentation, metadataProvider);
  }
}
