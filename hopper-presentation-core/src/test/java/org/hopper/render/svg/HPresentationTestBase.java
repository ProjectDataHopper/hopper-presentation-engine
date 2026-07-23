package org.hopper.render.svg;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.hopper.core.HEnvironment;
import org.hopper.core.log.DurationRequest;
import org.hopper.core.log.HMetricsUtil;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.render.IRenderContext;
import org.hopper.render.context.PresentationRenderContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class HPresentationTestBase {

  protected IHopMetadataProvider metadataProvider;
  protected IVariables variables;
  protected ILoggingObject parent;
  protected String folderName;

  @BeforeEach
  public void setUp() throws Exception {

    // Create a metastore
    //
    metadataProvider = new MemoryMetadataProvider();
    variables = Variables.getADefaultVariableSpace();

    HEnvironment.init();
    org.hopper.util.BasePresentationUtil.registerTestPlugins();

    parent = new LoggingObject("Presentation unit test");

    // Create SVG output folder if it doesn't exist
    //
    folderName = System.getProperty("java.io.tmpdir") + "/Hopper/";
    File folder = new File(folderName);
    if (!folder.exists()) {
      folder.mkdirs();
    }
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Disabled
  public List<DurationRequest> getStandardDurationRequests() {
    List<DurationRequest> requests = new ArrayList<>();

    requests.add(
        new DurationRequest(
            HMetricsUtil.PRESENTATION_START_LAYOUT,
            HMetricsUtil.PRESENTATION_FINISH_LAYOUT,
            "Layout time was"));
    requests.add(
        new DurationRequest(
            HMetricsUtil.PRESENTATION_START_RENDER,
            HMetricsUtil.PRESENTATION_FINISH_RENDER,
            "Rendering time was"));
    requests.add(
        new DurationRequest(
            HMetricsUtil.PRESENTATION_START_LAYOUT,
            HMetricsUtil.PRESENTATION_FINISH_RENDER,
            "Total time was"));

    return requests;
  }

  @Disabled
  protected void testRendering(HPresentation presentation, String filename) throws Exception {
    testRendering(presentation, filename, getStandardDurationRequests());
  }

  @Disabled
  protected void testRendering(
      HPresentation presentation, String filename, List<DurationRequest> durationRequests)
      throws Exception {

    IRenderContext renderContext = new PresentationRenderContext(presentation, metadataProvider);

    HLayoutResults results =
        presentation.doLayout(parent, renderContext, metadataProvider, Collections.emptyList());
    presentation.render(results, metadataProvider);

    ILogChannel log = results.getLog();

    for (DurationRequest durationRequest : durationRequests) {
      long duration =
          HMetricsUtil.getLastDuration(
              log, durationRequest.getStartId(), durationRequest.getFinishId());
      log.logBasic(durationRequest.getMessage() + " " + duration + " ms");
    }

    results.saveSvgPages(folderName, filename, true, true, true);

    // Also save the JSON of the presentation
    //
    File jsonFolder = new File(folderName + File.separator + "json");
    if (!jsonFolder.exists()) {
      jsonFolder.mkdirs();
    }
    String jsonFilename =
        folderName + File.separator + "json" + File.separator + filename + ".json";
    String json = presentation.toJsonString(true);
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(jsonFilename);
      fos.write(json.getBytes(StandardCharsets.UTF_8));
    } finally {
      if (fos != null) {
        fos.close();
      }
    }

    assertFalse(results.getRenderPages().isEmpty(), "Expected at least one render page");
    HRenderPage hopperRenderPage = results.getRenderPages().get(0);
    String xml = hopperRenderPage.getSvgXml();
    assertNotNull(xml, "SVG XML should be produced");
    assertTrue(xml.contains("<svg") || xml.contains("<svg:"), "SVG should contain an svg root element");
    assertTrue(xml.length() > 100, "SVG output looks too small to be a full page render");
  }
}
