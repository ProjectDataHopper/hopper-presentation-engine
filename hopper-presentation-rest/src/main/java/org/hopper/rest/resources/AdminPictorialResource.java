package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.HJson;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.GenerationRequest;
import org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.GenerationResult;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent;
import org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode;

/**
 * Admin REST endpoints for pictorial asset management, AI generator UI operations, and server configuration.
 */
@Path("admin/pictorials/")
public class AdminPictorialResource extends BaseResource {

  private static final ObjectMapper MAPPER = HJson.createMapper();

  @GET
  @Path("assets")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listAssets() {
    try {
      String metadataPath = getMetadataDirectory();
      if (StringUtils.isBlank(metadataPath)) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("{\"ok\":false,\"error\":\"Metadata path not configured\"}")
            .build();
      }

      File assetsBase = new File(metadataPath, "assets");
      ArrayNode list = MAPPER.createArrayNode();

      if (assetsBase.exists() && assetsBase.isDirectory()) {
        File[] presDirs = assetsBase.listFiles(File::isDirectory);
        if (presDirs != null) {
          for (File presDir : presDirs) {
            ObjectNode presObj = MAPPER.createObjectNode();
            presObj.put("presentationName", presDir.getName());
            ArrayNode compsNode = MAPPER.createArrayNode();

            File[] compDirs = presDir.listFiles();
            if (compDirs != null) {
              for (File compFile : compDirs) {
                if (compFile.isDirectory()) {
                  ObjectNode compObj = MAPPER.createObjectNode();
                  compObj.put("componentName", compFile.getName());
                  ArrayNode filesNode = MAPPER.createArrayNode();

                  File[] files = compFile.listFiles(f -> !f.isDirectory() && !f.getName().startsWith("."));
                  if (files != null) {
                    for (File f : files) {
                      ObjectNode fileObj = MAPPER.createObjectNode();
                      fileObj.put("fileName", f.getName());
                      fileObj.put("sizeBytes", f.length());
                      fileObj.put("assetUrl", "/hopper/api/assets/" + presDir.getName() + "/" + compFile.getName() + "/" + f.getName());
                      filesNode.add(fileObj);
                    }
                  }
                  compObj.set("files", filesNode);
                  compsNode.add(compObj);
                } else if (!compFile.getName().startsWith(".")) {
                  ObjectNode fileObj = MAPPER.createObjectNode();
                  fileObj.put("fileName", compFile.getName());
                  fileObj.put("sizeBytes", compFile.length());
                  fileObj.put("assetUrl", "/hopper/api/assets/" + presDir.getName() + "/" + compFile.getName());
                  compsNode.add(fileObj);
                }
              }
            }
            presObj.set("components", compsNode);
            list.add(presObj);
          }
        }
      }

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.set("assetLibraries", list);
      return Response.ok(MAPPER.writeValueAsString(res)).type(MediaType.APPLICATION_JSON_TYPE).build();

    } catch (Exception e) {
      return getServerError("Error listing pictorial assets", e);
    }
  }

  @POST
  @Path("generate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response generate(Map<String, Object> body) {
    try {
      if (body == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"Body is required\"}")
            .build();
      }

      String presentationName = stringField(body, "presentationName", "DefaultPres");
      String componentName = stringField(body, "componentName", "PictorialComponent");
      String prompt = stringField(body, "prompt", "A vessel filled to {percentage}%");

      String modeStr = stringField(body, "renderMode", "STEP_IMAGES");
      RenderMode mode = "CLIPPED_LAYERS".equalsIgnoreCase(modeStr) ? RenderMode.CLIPPED_LAYERS : RenderMode.STEP_IMAGES;

      int stepSize = intField(body, "stepSize", 10);
      String style = stringField(body, "generationStyle", "STABLE_INPAINT");
      String aspectPreset = stringField(body, "aspectPreset", "PORTRAIT_3_4");
      String resolutionTier = stringField(body, "resolutionTier", "MEDIUM");

      String metadataPath = getMetadataDirectory();
      File outputDir = new File(metadataPath, "assets" + File.separator + presentationName + File.separator + componentName);

      HPictorialChartComponent component = new HPictorialChartComponent();
      GenerationRequest req = new GenerationRequest();
      req.presentationName = presentationName;
      req.componentName = componentName;
      req.prompt = prompt;
      req.renderMode = mode;
      req.stepSize = stepSize;
      req.generationStyle = style;
      req.aspectPreset = aspectPreset;
      req.resolutionTier = resolutionTier;
      req.providerConfig = getActiveAiProviderConfig();
      org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.applyResolvedSize(
          req);
      req.outputDirectory = outputDir;
      // Store filesystem/VFS paths for server-side SVG render (not browser asset URLs).
      req.relativeAssetUrlPrefix =
          "${HOPPER_METADATA_PATH}/assets/" + presentationName + "/" + componentName;

      HAiImageGeneratorService service = new HAiImageGeneratorService();
      GenerationResult result = service.generateAssets(component, req);

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.put("presentationName", presentationName);
      res.put("componentName", componentName);
      res.put("renderMode", mode.name());
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
      String cleanError = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      if (cleanError == null || cleanError.isBlank()) {
        cleanError = e.toString();
      }
      ObjectNode errNode = MAPPER.createObjectNode();
      errNode.put("ok", false);
      errNode.put("error", cleanError);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(errNode.toString())
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    }
  }

  /**
   * Re-generate a single step image for an existing pictorial series (e.g. fix a bad AI fill
   * level). Body: {@code seriesName}, {@code percentage}, optional prompts / aspect / resolution.
   * Updates series {@code imageMap} and saves metadata when the series exists.
   */
  @POST
  @Path("generate-step")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response generateSingleStep(Map<String, Object> body) {
    try {
      if (body == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"Body is required\"}")
            .build();
      }
      String seriesName = stringField(body, "seriesName", null);
      if (StringUtils.isBlank(seriesName)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"seriesName is required\"}")
            .build();
      }
      if (body.get("percentage") == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"percentage is required\"}")
            .build();
      }
      int percentage = intField(body, "percentage", 0);

      String metadataPath = getMetadataDirectory();
      File outputDir =
          new File(
              metadataPath,
              "assets" + File.separator + "pictorial-series" + File.separator + seriesName);

      org.hopper.presentation.component.types.pictorial.HPictorialSeries series = null;
      org.apache.hop.metadata.api.IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      if (provider != null) {
        org.apache.hop.metadata.api.IHopMetadataSerializer<
                org.hopper.presentation.component.types.pictorial.HPictorialSeries>
            serializer =
                provider.getSerializer(
                    org.hopper.presentation.component.types.pictorial.HPictorialSeries.class);
        if (serializer != null && serializer.exists(seriesName)) {
          series = serializer.load(seriesName);
        }
      }
      if (series == null) {
        series =
            new org.hopper.presentation.component.types.pictorial.HPictorialSeries(
                seriesName, stringField(body, "description", ""), RenderMode.STEP_IMAGES);
      }

      GenerationRequest req = new GenerationRequest();
      req.presentationName = "pictorial-series";
      req.componentName = seriesName;
      req.prompt =
          stringField(
              body,
              "prompt",
              StringUtils.defaultIfBlank(
                  series.getPrompt(),
                  "A clear beer glass filled to {percentage}% with golden beer and foam, side view, plain background"));
      req.negativePrompt =
          stringField(
              body,
              "negativePrompt",
              StringUtils.defaultIfBlank(
                  series.getNegativePrompt(),
                  "A shattered broken beer glass on a wooden bar, spilled beer, shards, failure, side view, plain background"));
      req.overflowPrompt =
          stringField(
              body,
              "overflowPrompt",
              StringUtils.defaultIfBlank(
                  series.getOverflowPrompt(),
                  "A beer glass overflowing with foam and beer, large puddle around the base, overfilled, side view, plain background"));
      req.renderMode = RenderMode.STEP_IMAGES;
      req.aspectPreset = stringField(body, "aspectPreset", "PORTRAIT_3_4");
      req.resolutionTier = stringField(body, "resolutionTier", "MEDIUM");
      req.providerConfig = getActiveAiProviderConfig();
      HAiImageGeneratorService.applyResolvedSize(req);
      req.outputDirectory = outputDir;
      req.relativeAssetUrlPrefix =
          "${HOPPER_METADATA_PATH}/assets/pictorial-series/" + seriesName;

      HAiImageGeneratorService service = new HAiImageGeneratorService();
      String assetPath = service.generateSingleStepImage(req, percentage);

      if (series.getImageMap() == null) {
        series.setImageMap(new java.util.LinkedHashMap<>());
      }
      series.getImageMap().put(String.valueOf(percentage), assetPath);
      if (StringUtils.isNotBlank(req.prompt) && StringUtils.isBlank(series.getPrompt())) {
        series.setPrompt(req.prompt);
      }

      if (provider != null) {
        provider
            .getSerializer(org.hopper.presentation.component.types.pictorial.HPictorialSeries.class)
            .save(series);
      }

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.put("seriesName", seriesName);
      res.put("percentage", percentage);
      res.put("assetPath", assetPath);
      res.put("width", req.width);
      res.put("height", req.height);
      res.set("series", MAPPER.valueToTree(series));
      return Response.ok(res).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      String cleanError = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      if (cleanError == null || cleanError.isBlank()) {
        cleanError = e.toString();
      }
      ObjectNode errNode = MAPPER.createObjectNode();
      errNode.put("ok", false);
      errNode.put("error", cleanError);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(errNode.toString())
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    }
  }

  @POST
  @Path("generate-series")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response generateSeries(Map<String, Object> body) {
    try {
      if (body == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"Body is required\"}")
            .build();
      }

      String seriesName = stringField(body, "seriesName", "default-series");
      String description = stringField(body, "description", "Generated pictorial series");
      String prompt =
          stringField(
              body,
              "prompt",
              "A clear beer glass filled to {percentage}% with golden beer and foam, side view, plain background");
      String negativePrompt =
          stringField(
              body,
              "negativePrompt",
              "A shattered broken beer glass on a wooden bar, spilled beer, shards, failure, side view, plain background");
      String overflowPrompt =
          stringField(
              body,
              "overflowPrompt",
              "A beer glass overflowing with foam and beer, large puddle around the base, overfilled, side view, plain background");

      String modeStr = stringField(body, "renderMode", "STEP_IMAGES");
      RenderMode mode =
          "CLIPPED_LAYERS".equalsIgnoreCase(modeStr)
              ? RenderMode.CLIPPED_LAYERS
              : RenderMode.STEP_IMAGES;

      int stepSize = intField(body, "stepSize", 10);
      String style = stringField(body, "generationStyle", "STABLE_INPAINT");
      String aspectPreset = stringField(body, "aspectPreset", "PORTRAIT_3_4");
      String resolutionTier = stringField(body, "resolutionTier", "MEDIUM");
      boolean includeNegative = boolField(body, "includeNegativeExtreme", true);
      boolean includeOverflow = boolField(body, "includeOverflowExtreme", true);
      int negativeKey = intField(body, "negativeStepKey", -100);
      int overflowKey = intField(body, "overflowStepKey", 200);

      String metadataPath = getMetadataDirectory();
      File outputDir =
          new File(
              metadataPath,
              "assets" + File.separator + "pictorial-series" + File.separator + seriesName);

      org.hopper.presentation.component.types.pictorial.HPictorialSeries series =
          new org.hopper.presentation.component.types.pictorial.HPictorialSeries(
              seriesName, description, mode);

      GenerationRequest req = new GenerationRequest();
      req.presentationName = "pictorial-series";
      req.componentName = seriesName;
      req.prompt = prompt;
      req.negativePrompt = negativePrompt;
      req.overflowPrompt = overflowPrompt;
      req.renderMode = mode;
      req.stepSize = stepSize;
      // Normal ladder is always 0–100; extremes are single optional images
      req.stepMin = 0;
      req.stepMax = 100;
      req.includeNegativeExtreme = includeNegative;
      req.includeOverflowExtreme = includeOverflow;
      req.negativeStepKey = negativeKey;
      req.overflowStepKey = overflowKey;
      req.generationStyle = style;
      req.aspectPreset = aspectPreset;
      req.resolutionTier = resolutionTier;
      req.providerConfig = getActiveAiProviderConfig();
      // width/height resolved from catalog for this provider (ignores free-form tall sizes)
      org.hopper.presentation.component.types.pictorial.HAiImageGeneratorService.applyResolvedSize(
          req);
      req.outputDirectory = outputDir;
      // Store filesystem/VFS paths for server-side SVG render (not browser asset URLs).
      req.relativeAssetUrlPrefix =
          "${HOPPER_METADATA_PATH}/assets/pictorial-series/" + seriesName;

      HAiImageGeneratorService service = new HAiImageGeneratorService();
      GenerationResult result = service.generateSeriesAssets(series, req);

      // Save series metadata to Hop metastore
      org.apache.hop.metadata.api.IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      if (provider != null) {
        org.apache.hop.metadata.api.IHopMetadataSerializer<org.hopper.presentation.component.types.pictorial.HPictorialSeries> serializer =
            provider.getSerializer(org.hopper.presentation.component.types.pictorial.HPictorialSeries.class);
        serializer.save(series);
      }

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.put("seriesName", seriesName);
      res.put("renderMode", mode.name());
      res.set("series", MAPPER.valueToTree(series));

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
      String cleanError = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      if (cleanError == null || cleanError.isBlank()) {
        cleanError = e.toString();
      }
      ObjectNode errNode = MAPPER.createObjectNode();
      errNode.put("ok", false);
      errNode.put("error", cleanError);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(errNode.toString())
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    }
  }

  /**
   * Aspect-ratio / resolution options allowed for a provider (or the currently configured one).
   * Query: {@code ?provider=XAI_GROK} optional.
   */
  @GET
  @Path("size-options")
  @Produces(MediaType.APPLICATION_JSON)
  public Response sizeOptions(@jakarta.ws.rs.QueryParam("provider") String provider) {
    try {
      org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType type;
      if (StringUtils.isNotBlank(provider)) {
        try {
          type =
              org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType
                  .valueOf(provider.trim().toUpperCase());
        } catch (Exception e) {
          type = getActiveAiProviderConfig().getProviderType();
        }
      } else {
        type = getActiveAiProviderConfig().getProviderType();
      }
      java.util.List<java.util.Map<String, Object>> options =
          org.hopper.presentation.component.types.pictorial.HImageSizeCatalog.optionsForProvider(
              type);
      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.put("providerType", type.name());
      res.set("aspectPresets", MAPPER.valueToTree(options));
      res.put(
          "note",
          "External AI models only support fixed aspect ratios. Output is cover-cropped to the "
              + "selected size (no white letterbox bands). Provider: "
              + type.name());
      // Return ObjectNode (not pre-serialized string) so clients get a JSON object after one parse
      return Response.ok(res).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error listing size options", e);
    }
  }

  @GET
  @Path("settings")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSettings() {
    try {
      org.hopper.presentation.component.types.pictorial.HAiProviderConfig config = getActiveAiProviderConfig();

      ObjectNode settings = MAPPER.createObjectNode();
      settings.put("providerType", config.getProviderType().name());
      settings.put("maskedApiKey", config.getMaskedApiKey());
      settings.put("hasApiKey", StringUtils.isNotBlank(config.getEncryptedApiKey()));
      settings.put("apiKeyIsVariable", config.isApiKeyVariableExpression());
      settings.put("modelName", config.getModelName() != null ? config.getModelName() : "");
      settings.put("effectiveModelName", config.getEffectiveModelName());
      settings.put("endpointUrl", config.getEndpointUrl() != null ? config.getEndpointUrl() : "");
      settings.put("effectiveEndpointUrl", config.getEffectiveEndpointUrl());

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.set("settings", settings);
      return Response.ok(MAPPER.writeValueAsString(res)).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error fetching AI settings", e);
    }
  }

  @POST
  @Path("settings")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response saveSettings(Map<String, Object> body) {
    try {
      if (body == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"Body is required\"}")
            .build();
      }

      String providerStr = stringField(body, "providerType", "BUILTIN");
      String rawApiKey = stringField(body, "rawApiKey", null);
      String modelName = stringField(body, "modelName", "");
      String endpointUrl = stringField(body, "endpointUrl", "");

      org.hopper.presentation.component.types.pictorial.HAiProviderConfig config = getActiveAiProviderConfig();
      try {
        config.setProviderType(org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType.valueOf(providerStr));
      } catch (Exception e) {
        config.setProviderType(org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType.BUILTIN);
      }

      if (StringUtils.isNotBlank(rawApiKey) 
          && !rawApiKey.contains("••••") 
          && !rawApiKey.startsWith("Encrypted ")) {
        config.setRawApiKey(rawApiKey);
      }
      config.setModelName(modelName);
      config.setEndpointUrl(endpointUrl);

      saveActiveAiProviderConfig(config);

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", true);
      res.put("message", "AI Server configuration updated successfully.");
      return Response.ok(MAPPER.writeValueAsString(res)).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error saving AI settings", e);
    }
  }

  @POST
  @Path("test-connection")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response testConnection(Map<String, Object> body) {
    try {
      String providerStr = stringField(body, "providerType", null);
      String rawApiKey = stringField(body, "rawApiKey", null);
      String modelName = stringField(body, "modelName", null);
      String endpointUrl = stringField(body, "endpointUrl", null);

      org.hopper.presentation.component.types.pictorial.HAiProviderConfig config =
          getActiveAiProviderConfig();
      if (StringUtils.isNotBlank(providerStr)) {
        try {
          config.setProviderType(
              org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType
                  .valueOf(providerStr));
        } catch (Exception ignored) {
        }
      }
      // Apply form fields when provided (including variable expressions)
      if (StringUtils.isNotBlank(rawApiKey)
          && !rawApiKey.contains("••••")
          && !rawApiKey.startsWith("Encrypted ")) {
        config.setRawApiKey(rawApiKey);
      }
      if (modelName != null) {
        config.setModelName(modelName);
      }
      if (endpointUrl != null) {
        config.setEndpointUrl(endpointUrl);
      }

      org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ConnectionTestResult
          result = config.testLiveConnection();

      ObjectNode res = MAPPER.createObjectNode();
      res.put("ok", result.isOk());
      res.put("providerType", config.getProviderType().name());
      res.put("message", result.getMessage());
      res.put("httpStatus", result.getHttpStatus());
      res.put("keyHint", result.getResolvedKeyHint());
      res.put("modelName", config.getEffectiveModelName());
      // 200 even on failed probe so the UI can show the message body
      return Response.ok(res).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error testing provider connection", e);
    }
  }

  private org.hopper.presentation.component.types.pictorial.HAiProviderConfig getActiveAiProviderConfig() {
    org.hopper.presentation.component.types.pictorial.HAiProviderConfig config =
        new org.hopper.presentation.component.types.pictorial.HAiProviderConfig();

    String metadataPath = getMetadataDirectory();
    if (StringUtils.isNotBlank(metadataPath)) {
      File configFile = new File(metadataPath, "config" + File.separator + "ai-pictorial-settings.json");
      if (configFile.exists()) {
        try {
          ObjectNode json = (ObjectNode) MAPPER.readTree(configFile);
          if (json.has("providerType")) {
            config.setProviderType(org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType.valueOf(json.get("providerType").asText()));
          }
          if (json.has("encryptedApiKey")) {
            config.setEncryptedApiKey(json.get("encryptedApiKey").asText());
          }
          if (json.has("modelName")) {
            config.setModelName(json.get("modelName").asText());
          }
          if (json.has("endpointUrl")) {
            config.setEndpointUrl(json.get("endpointUrl").asText());
          }
          return config;
        } catch (Exception ignored) {
        }
      }
    }

    String providerStr = System.getProperty("hopper.ai.provider", "BUILTIN");
    String encKey = System.getProperty("hopper.ai.apiKey", "");
    String modelName = System.getProperty("hopper.ai.modelName", "");
    String endpointUrl = System.getProperty("hopper.ai.endpointUrl", "");

    try {
      config.setProviderType(org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType.valueOf(providerStr));
    } catch (Exception ignored) {
      config.setProviderType(org.hopper.presentation.component.types.pictorial.HAiProviderConfig.ProviderType.BUILTIN);
    }
    config.setEncryptedApiKey(encKey);
    config.setModelName(modelName);
    config.setEndpointUrl(endpointUrl);
    return config;
  }

  private void saveActiveAiProviderConfig(org.hopper.presentation.component.types.pictorial.HAiProviderConfig config) {
    System.setProperty("hopper.ai.provider", config.getProviderType().name());
    System.setProperty("hopper.ai.apiKey", config.getEncryptedApiKey());
    System.setProperty("hopper.ai.modelName", config.getModelName() != null ? config.getModelName() : "");
    System.setProperty("hopper.ai.endpointUrl", config.getEndpointUrl() != null ? config.getEndpointUrl() : "");

    String metadataPath = getMetadataDirectory();
    if (StringUtils.isNotBlank(metadataPath)) {
      File configDir = new File(metadataPath, "config");
      if (!configDir.exists()) {
        configDir.mkdirs();
      }
      File configFile = new File(configDir, "ai-pictorial-settings.json");
      try {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("providerType", config.getProviderType().name());
        json.put("encryptedApiKey", config.getEncryptedApiKey());
        json.put("modelName", config.getModelName() != null ? config.getModelName() : "");
        json.put("endpointUrl", config.getEndpointUrl() != null ? config.getEndpointUrl() : "");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(configFile, json);
      } catch (Exception ignored) {
      }
    }
  }

  private String getMetadataDirectory() {
    String path = hopperRest.getMetadataPath();
    if (StringUtils.isBlank(path)) {
      path = System.getProperty("HOPPER_METADATA_PATH");
    }
    return path;
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

  private boolean boolField(Map<String, Object> map, String key, boolean defaultValue) {
    Object val = map.get(key);
    if (val instanceof Boolean) {
      return (Boolean) val;
    }
    if (val != null) {
      String s = val.toString().trim();
      if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) {
        return true;
      }
      if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) {
        return false;
      }
    }
    return defaultValue;
  }
}
