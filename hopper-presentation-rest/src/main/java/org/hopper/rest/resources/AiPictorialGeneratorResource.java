package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.HJson;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.GenerationRequest;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.GenerationResult;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;
import org.hopper.presentation.page.HPage;

/**
 * AI REST service endpoint for design-time generation of pictorial chart image sequences
 * (`STEP_IMAGES`) and dynamic layer fill pairs (`CLIPPED_LAYERS`).
 */
@Path("ai/generate/")
public class AiPictorialGeneratorResource extends BaseResource {

  private static final ObjectMapper MAPPER = HJson.createMapper();

  @POST
  @Path("pictorial")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response generatePictorialAssets(Map<String, Object> body) {
    try {
      if (body == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"Request body is required\"}")
            .build();
      }

      String presentationName = stringField(body, "presentationName");
      String componentName = stringField(body, "componentName");
      String prompt = stringField(body, "prompt");

      if (StringUtils.isBlank(presentationName) || StringUtils.isBlank(prompt)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"presentationName and prompt are required\"}")
            .build();
      }

      String modeStr = stringField(body, "renderMode", "STEP_IMAGES");
      RenderMode mode = "CLIPPED_LAYERS".equalsIgnoreCase(modeStr) ? RenderMode.CLIPPED_LAYERS : RenderMode.STEP_IMAGES;

      int stepSize = intField(body, "stepSize", 10);
      String style = stringField(body, "generationStyle", "STABLE_INPAINT");
      int width = intField(body, "width", 200);
      int height = intField(body, "height", 300);

      String metadataPath = hopperRest.getMetadataPath();
      if (StringUtils.isBlank(metadataPath)) {
        metadataPath = System.getProperty("HOPPER_METADATA_PATH");
      }
      if (StringUtils.isBlank(metadataPath)) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("{\"ok\":false,\"error\":\"Metadata path not configured\"}")
            .build();
      }

      // Output directory: metadata/assets/{presentationName}/{componentName}/
      String subFolder = StringUtils.isNotBlank(componentName) ? componentName : "pictorial";
      File outputDir = new File(metadataPath, "assets" + File.separator + presentationName + File.separator + subFolder);

      HPictorialChartComponent component = new HPictorialChartComponent();
      if (StringUtils.isNotBlank(componentName)) {
        component.setSourceConnectorName("sample");
        component.setValueColumn("value");
      }

      GenerationRequest req = new GenerationRequest();
      req.presentationName = presentationName;
      req.componentName = componentName;
      req.prompt = prompt;
      req.renderMode = mode;
      req.stepSize = stepSize;
      req.generationStyle = style;
      req.width = width;
      req.height = height;
      req.outputDirectory = outputDir;
      req.relativeAssetUrlPrefix = "${HOPPER_METADATA_PATH}/assets/" + presentationName + "/" + subFolder;

      HAiImageGeneratorService service = new HAiImageGeneratorService();
      GenerationResult result = service.generateAssets(component, req);

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.put("presentationName", presentationName);
      res.put("renderMode", mode.name());
      res.put("outputDirectory", outputDir.getAbsolutePath());
      res.set("updatedComponent", MAPPER.valueToTree(result.updatedComponent));

      if (mode == RenderMode.STEP_IMAGES) {
        ObjectNode files = MAPPER.createObjectNode();
        result.generatedFiles.forEach(files::put);
        res.set("generatedFiles", files);
      } else {
        res.put("backgroundImagePath", result.backgroundImagePath);
        res.put("fillImagePath", result.fillImagePath);
      }

      return Response.ok(MAPPER.writeValueAsString(res)).type(MediaType.APPLICATION_JSON_TYPE).build();

    } catch (Exception e) {
      return getServerError("Error generating pictorial assets", e);
    }
  }

  private String stringField(Map<String, Object> map, String key) {
    return stringField(map, key, null);
  }

  private String stringField(Map<String, Object> map, String key, String defaultValue) {
    Object val = map.get(key);
    return val != null ? val.toString() : defaultValue;
  }

  private int intField(Map<String, Object> map, String key, int defaultValue) {
    Object val = map.get(key);
    if (val instanceof Number) {
      return ((Number) val).intValue();
    }
    if (val != null) {
      try {
        return Integer.parseInt(val.toString());
      } catch (NumberFormatException ignored) {
      }
    }
    return defaultValue;
  }
}
