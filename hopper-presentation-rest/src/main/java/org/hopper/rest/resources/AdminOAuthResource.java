package org.hopper.rest.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.rest.admin.oauth.OAuthAdminService;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityContext;

/**
 * Admin OAuth / OIDC provider wizard API. Requires {@link HAction#SECURITY_ADMIN}.
 *
 * <ul>
 *   <li>GET {@code /admin/oauth/presets} — provider templates + field schema
 *   <li>GET {@code /admin/oauth/status} — current auth/OIDC status (secrets masked)
 *   <li>POST {@code /admin/oauth/preview} — expand wizard inputs → settings patch
 *   <li>POST {@code /admin/oauth/test} — OIDC discovery + JWKS connectivity
 *   <li>POST {@code /admin/oauth/apply} — persist L1 settings + hot-reload
 * </ul>
 */
@Path("admin/oauth")
public class AdminOAuthResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Path("/presets")
  @Produces(MediaType.APPLICATION_JSON)
  public Response presets() {
    try {
      requireAdmin();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("presets", oauth().listPresets());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing OAuth presets", e);
    }
  }

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response status() {
    try {
      requireAdmin();
      return Response.ok(MAPPER.writeValueAsString(oauth().status()))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading OAuth status", e);
    }
  }

  @POST
  @Path("/preview")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response preview(String jsonBody) {
    try {
      requireAdmin();
      WizardRequest req = parse(jsonBody);
      Map<String, Object> body = oauth().preview(req.provider(), req.inputs());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return getServerError("Error previewing OAuth settings", e);
    }
  }

  @POST
  @Path("/test")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response test(String jsonBody) {
    try {
      requireAdmin();
      WizardRequest req = parse(jsonBody);
      Map<String, Object> body = oauth().test(req.provider(), req.inputs());
      // Always 200 with success flag so the admin UI can show discovery errors cleanly
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return getServerError("Error testing OAuth connectivity", e);
    }
  }

  /**
   * Apply provider configuration.
   *
   * <p>Body: {@code {"provider":"google","inputs":{...},"requireTest":true}}
   */
  @POST
  @Path("/apply")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response apply(String jsonBody) {
    try {
      requireAdmin();
      WizardRequest req = parse(jsonBody);
      boolean requireTest = req.requireTest();
      Map<String, Object> body = oauth().apply(req.provider(), req.inputs(), requireTest);
      int code =
          Boolean.TRUE.equals(body.get("success"))
              ? Response.Status.OK.getStatusCode()
              : Response.Status.BAD_REQUEST.getStatusCode();
      return Response.status(code)
          .entity(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return getServerError("Error applying OAuth configuration", e);
    }
  }

  private OAuthAdminService oauth() {
    return hopperRest.getOAuthAdminService();
  }

  private WizardRequest parse(String jsonBody) throws Exception {
    if (jsonBody == null || jsonBody.isBlank()) {
      return new WizardRequest(null, Map.of(), true);
    }
    Map<String, Object> root =
        MAPPER.readValue(jsonBody, new TypeReference<Map<String, Object>>() {});
    String provider = root.get("provider") != null ? root.get("provider").toString() : null;
    if (provider == null && root.get("providerId") != null) {
      provider = root.get("providerId").toString();
    }
    Map<String, String> inputs = new LinkedHashMap<>();
    Object inputsNode = root.get("inputs");
    if (inputsNode instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (e.getKey() != null) {
          inputs.put(
              e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : "");
        }
      }
    } else {
      // Flat body fields (except meta keys)
      for (Map.Entry<String, Object> e : root.entrySet()) {
        String k = e.getKey();
        if ("provider".equals(k)
            || "providerId".equals(k)
            || "requireTest".equals(k)
            || "inputs".equals(k)) {
          continue;
        }
        inputs.put(k, e.getValue() != null ? e.getValue().toString() : "");
      }
    }
    boolean requireTest = true;
    if (root.containsKey("requireTest")) {
      Object rt = root.get("requireTest");
      requireTest =
          rt instanceof Boolean b
              ? b
              : !"false".equalsIgnoreCase(String.valueOf(rt));
    }
    return new WizardRequest(provider, inputs, requireTest);
  }

  private Response badRequest(String message) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("success", false);
      body.put("error", message != null ? message : "Bad request");
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(message)
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }

  private record WizardRequest(String provider, Map<String, String> inputs, boolean requireTest) {}
}
