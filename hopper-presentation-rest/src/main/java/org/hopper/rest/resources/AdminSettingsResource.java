package org.hopper.rest.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.config.HEffectiveSetting;
import org.hopper.config.HSettingCategory;
import org.hopper.rest.admin.AdminSettingsService;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityContext;

/**
 * Admin API for layered runtime settings (schema, effective values, apply patch).
 *
 * <p>Requires {@link HAction#SECURITY_ADMIN}.
 */
@Path("admin/settings")
public class AdminSettingsResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEffective(@QueryParam("redact") String redactParam) {
    try {
      requireAdmin();
      boolean redact = redactParam == null || !"false".equalsIgnoreCase(redactParam.trim());
      AdminSettingsService service = hopperRest.getAdminSettingsService();
      List<Map<String, Object>> settings = new ArrayList<>();
      for (HEffectiveSetting s : service.listEffective(redact)) {
        settings.add(s.toMap());
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("settings", settings);
      body.put("overrideCount", service.getOverrides().size());
      body.put("categories", categoryNames());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading admin settings", e);
    }
  }

  @GET
  @Path("/schema")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSchema() {
    try {
      requireAdmin();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("settings", hopperRest.getAdminSettingsService().schema());
      body.put("categories", categoryNames());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading settings schema", e);
    }
  }

  /**
   * Apply a partial patch of setting keys. Body: {@code {"settings": {"auth.mode": "oauth2",
   * ...}}} or a flat map of key→value.
   */
  @POST
  @Path("/apply")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response apply(String jsonBody) {
    try {
      requireAdmin();
      Map<String, String> patch = parsePatch(jsonBody);
      if (patch.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"success\":false,\"errors\":[\"No settings in patch\"]}")
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
      AdminSettingsService.ApplyResult result = hopperRest.applySettingsPatch(patch);
      int status =
          result.success()
              ? Response.Status.OK.getStatusCode()
              : Response.Status.BAD_REQUEST.getStatusCode();
      return Response.status(status)
          .entity(MAPPER.writeValueAsString(result.toMap()))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error applying admin settings", e);
    }
  }

  private Map<String, String> parsePatch(String jsonBody) throws Exception {
    Map<String, String> patch = new LinkedHashMap<>();
    if (jsonBody == null || jsonBody.isBlank()) {
      return patch;
    }
    Map<String, Object> root =
        MAPPER.readValue(jsonBody, new TypeReference<Map<String, Object>>() {});
    Object settingsNode = root.get("settings");
    if (settingsNode instanceof Map<?, ?> nested) {
      for (Map.Entry<?, ?> e : nested.entrySet()) {
        if (e.getKey() != null) {
          patch.put(
              e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : null);
        }
      }
      return patch;
    }
    // Flat map of key → value (skip meta keys if any)
    for (Map.Entry<String, Object> e : root.entrySet()) {
      if ("settings".equals(e.getKey()) || "success".equals(e.getKey())) {
        continue;
      }
      patch.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
    }
    return patch;
  }

  private static List<String> categoryNames() {
    List<String> names = new ArrayList<>();
    for (HSettingCategory c : HSettingCategory.values()) {
      names.add(c.name());
    }
    return names;
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }
}
