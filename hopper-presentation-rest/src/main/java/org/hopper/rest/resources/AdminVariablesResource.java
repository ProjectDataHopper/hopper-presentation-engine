package org.hopper.rest.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.config.HVariableEntry;
import org.hopper.rest.admin.AdminVariablesService;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityContext;

/**
 * Admin API for server-wide system variables (inherited by presentations and connectors).
 *
 * <p>Requires {@link HAction#SECURITY_ADMIN}.
 */
@Path("admin/variables")
public class AdminVariablesResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response list(@QueryParam("redact") String redactParam) {
    try {
      requireAdmin();
      boolean redact = "true".equalsIgnoreCase(redactParam != null ? redactParam.trim() : "");
      AdminVariablesService service = hopperRest.getAdminVariablesService();
      // Create empty system-variables/runtime document if the metadata file is missing
      service.ensureLoaded();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("variables", service.listEntries(redact));
      body.put("count", service.getVariables().size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing system variables", e);
    }
  }

  /**
   * Replace the full system variable set. Body: {@code {"variables":[{"name":"X","value":"y"},…]}}
   * or a bare array of entries.
   */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response save(String jsonBody) {
    try {
      requireAdmin();
      List<HVariableEntry> entries = parseEntries(jsonBody);
      int count =
          hopperRest
              .getAdminVariablesService()
              .saveAndApply(entries, hopperRest.getVariables());
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("ok", true);
      body.put("count", count);
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return getServerError(e.getMessage(), false);
    } catch (Exception e) {
      return getServerError("Error saving system variables", e);
    }
  }

  /**
   * Encrypt a value with {@code Encr.encryptPasswordIfNotUsingVariables}. Body: {@code
   * {"value":"…"}}.
   */
  @POST
  @Path("/encrypt")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response encrypt(String jsonBody) {
    try {
      requireAdmin();
      Map<String, Object> in =
          MAPPER.readValue(
              jsonBody != null ? jsonBody : "{}", new TypeReference<Map<String, Object>>() {});
      Object raw = in.get("value");
      String value = raw != null ? raw.toString() : "";
      String encrypted = AdminVariablesService.encryptIfNotUsingVariables(value);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("value", encrypted);
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error encrypting value", e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<HVariableEntry> parseEntries(String jsonBody) throws Exception {
    if (jsonBody == null || jsonBody.isBlank()) {
      throw new IllegalArgumentException("Request body is required");
    }
    Object root = MAPPER.readValue(jsonBody, Object.class);
    List<Map<String, Object>> rows;
    if (root instanceof List) {
      rows = (List<Map<String, Object>>) root;
    } else if (root instanceof Map) {
      Object vars = ((Map<String, Object>) root).get("variables");
      if (!(vars instanceof List)) {
        throw new IllegalArgumentException("Body must include a variables array");
      }
      rows = (List<Map<String, Object>>) vars;
    } else {
      throw new IllegalArgumentException("Body must be a variables array or {variables:[…]}");
    }
    List<HVariableEntry> entries = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      Object name = row.get("name");
      Object value = row.get("value");
      entries.add(
          new HVariableEntry(
              name != null ? name.toString() : "", value != null ? value.toString() : ""));
    }
    return entries;
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }
}
