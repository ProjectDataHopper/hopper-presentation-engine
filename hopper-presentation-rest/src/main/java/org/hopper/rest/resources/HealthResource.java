package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.rest.HRest;
import org.hopper.rest.render.RenderCache;
import org.hopper.rest.security.HAuthMode;
import org.hopper.rest.security.HSecuritySettings;

/**
 * Public liveness/readiness probe for Docker/K8s. Does not require authentication.
 *
 * <p>Path: {@code GET /api/system/health} (avoids clash with MicroProfile {@code /health} on TomEE).
 */
@Path("system/health")
public class HealthResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    try {
      HRest rest = HRest.getInstance();
      HSecuritySettings s = rest.getSecuritySettings();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("status", "UP");
      body.put("application", "hopper-presentation");
      body.put(
          "auth",
          Map.of(
              "enabled", s != null && s.isAuthEnabled(),
              "mode",
                  s != null && s.getAuthMode() != null
                      ? s.getAuthMode().name()
                      : HAuthMode.DISABLED.name()));
      body.put("renderCacheSize", RenderCache.getInstance().size());
      body.put("metadataPath", rest.getMetadataPath() != null ? rest.getMetadataPath() : "");
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (Exception e) {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "DOWN");
        body.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity(MAPPER.writeValueAsString(body))
            .type(MediaType.APPLICATION_JSON)
            .build();
      } catch (Exception e2) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity("{\"status\":\"DOWN\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
    }
  }
}
