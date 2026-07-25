package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HJson;
import org.hopper.core.exception.HException;
import org.hopper.metadata.codec.HMetadataCodec;
import org.hopper.metadata.dsl.HAuthoringDsl;
import org.hopper.metadata.schema.HJsonSchemaExporter;
import org.hopper.metadata.validate.HMetadataValidator;
import org.hopper.metadata.validate.ValidateOptions;
import org.hopper.metadata.validate.ValidationIssue;
import org.hopper.metadata.validate.ValidationReport;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.rest.HRest;

/**
 * AI / automated authoring helpers: context bundle, JSON schemas, validate, compile DSL. Does not
 * auto-save metadata.
 */
@Path("ai/")
public class AiAuthoringResource extends BaseResource {

  private static final ObjectMapper MAPPER = HJson.createMapper();
  private final HRest hopperRest = HRest.getInstance();

  @GET
  @Path("context")
  @Produces(MediaType.APPLICATION_JSON)
  public Response context() {
    try {
      HJsonSchemaExporter exporter = new HJsonSchemaExporter();
      return Response.ok(MAPPER.writeValueAsString(exporter.aiContextDocument()))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    } catch (Exception e) {
      return getServerError("Error building AI context", e);
    }
  }

  @GET
  @Path("schemas/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response schema(@PathParam("name") String name) {
    try {
      HJsonSchemaExporter exporter = new HJsonSchemaExporter();
      String json;
      if ("presentation".equalsIgnoreCase(name)) {
        json = exporter.presentationSchemaJson();
      } else if ("connector".equalsIgnoreCase(name)) {
        json = exporter.connectorSchemaJson();
      } else {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("{\"error\":\"Unknown schema: " + name + "\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error exporting schema " + name, e);
    }
  }

  @POST
  @Path("validate/presentation")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response validatePresentation(Map<String, Object> body) {
    try {
      String json = stringField(body, "json");
      if (StringUtils.isBlank(json)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"json is required\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }
      boolean smoke = boolField(body, "smoke", false);
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      ValidateOptions options =
          ValidateOptions.builder()
              .includeSmokeLayout(smoke)
              .metadataProvider(provider)
              .build();
      ValidationReport report =
          new HMetadataValidator().validatePresentationJson(json, options);
      return Response.ok(reportJson(report)).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error validating presentation", e);
    }
  }

  @POST
  @Path("validate/connector")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response validateConnector(Map<String, Object> body) {
    try {
      String json = stringField(body, "json");
      if (StringUtils.isBlank(json)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"json is required\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }
      ValidationReport report =
          new HMetadataValidator()
              .validateConnectorJson(json, ValidateOptions.builder().build());
      return Response.ok(reportJson(report)).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      return getServerError("Error validating connector", e);
    }
  }

  @POST
  @Path("compile/presentation")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response compilePresentation(Map<String, Object> body) {
    try {
      String dsl = stringField(body, "dsl");
      if (StringUtils.isBlank(dsl)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"dsl is required\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }
      HPresentation presentation = HAuthoringDsl.compilePresentation(dsl);
      String hopJson =
          HMetadataCodec.toHopJson(presentation, hopperRest.getMetadataProvider());
      ValidationReport report =
          new HMetadataValidator()
              .validatePresentation(
                  presentation,
                  ValidateOptions.builder()
                      .metadataProvider(hopperRest.getMetadataProvider())
                      .build());
      ObjectNode out = MAPPER.createObjectNode();
      out.put("ok", report.isOk());
      out.put("json", hopJson);
      out.set("validation", MAPPER.readTree(reportJson(report)));
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    } catch (HException e) {
      ObjectNode out = MAPPER.createObjectNode();
      out.put("ok", false);
      out.put("error", e.getMessage());
      try {
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      } catch (Exception e2) {
        return getServerError("Error compiling presentation DSL", e);
      }
    } catch (Exception e) {
      return getServerError("Error compiling presentation DSL", e);
    }
  }

  @POST
  @Path("compile/connector")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response compileConnector(Map<String, Object> body) {
    try {
      String dsl = stringField(body, "dsl");
      if (StringUtils.isBlank(dsl)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":\"dsl is required\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }
      HConnector connector = HAuthoringDsl.compileConnector(dsl);
      String hopJson = HMetadataCodec.toHopJson(connector, hopperRest.getMetadataProvider());
      ValidationReport report =
          new HMetadataValidator()
              .validateConnector(connector, ValidateOptions.builder().build());
      ObjectNode out = MAPPER.createObjectNode();
      out.put("ok", report.isOk());
      out.put("json", hopJson);
      out.set("validation", MAPPER.readTree(reportJson(report)));
      return Response.ok(MAPPER.writeValueAsString(out))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    } catch (HException e) {
      ObjectNode out = MAPPER.createObjectNode();
      out.put("ok", false);
      out.put("error", e.getMessage());
      try {
        return Response.ok(MAPPER.writeValueAsString(out))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      } catch (Exception e2) {
        return getServerError("Error compiling connector DSL", e);
      }
    } catch (Exception e) {
      return getServerError("Error compiling connector DSL", e);
    }
  }

  private static String reportJson(ValidationReport report) throws Exception {
    ObjectNode out = MAPPER.createObjectNode();
    out.put("ok", report.isOk());
    ArrayNode errors = MAPPER.createArrayNode();
    ArrayNode warnings = MAPPER.createArrayNode();
    for (ValidationIssue issue : report.getIssues()) {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("code", issue.getCode());
      n.put("path", issue.getPath());
      n.put("message", issue.getMessage());
      n.put("severity", issue.getSeverity().name());
      if (issue.getSeverity().name().equals("ERROR")) {
        errors.add(n);
      } else {
        warnings.add(n);
      }
    }
    out.set("errors", errors);
    out.set("warnings", warnings);
    return MAPPER.writeValueAsString(out);
  }

  private static String stringField(Map<String, Object> body, String key) {
    if (body == null || !body.containsKey(key) || body.get(key) == null) {
      return null;
    }
    Object v = body.get(key);
    if (v instanceof String s) {
      return s;
    }
    try {
      return MAPPER.writeValueAsString(v);
    } catch (Exception e) {
      return String.valueOf(v);
    }
  }

  private static boolean boolField(Map<String, Object> body, String key, boolean def) {
    if (body == null || !body.containsKey(key) || body.get(key) == null) {
      return def;
    }
    Object v = body.get(key);
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }
}
