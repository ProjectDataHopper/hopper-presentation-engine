package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.rest.admin.HServerHousekeeping;
import org.hopper.rest.render.RenderCache;
import org.hopper.rest.security.HSessionStore;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityContext;

/**
 * Admin APIs for server ops: render cache stats/eviction, housekeeping, session counts. Requires
 * {@link HAction#SECURITY_ADMIN}.
 */
@Path("admin/server")
public class AdminServerResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response status() {
    try {
      requireAdmin();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("renderCache", RenderCache.getInstance().stats());
      body.put("renderEntries", RenderCache.getInstance().listEntries());
      body.put("sessions", Map.of("count", HSessionStore.getInstance().size()));
      body.put("housekeeping", HServerHousekeeping.getInstance().stats());
      body.put(
          "settings",
          Map.of(
              "server.render.ttl-minutes",
              RenderCache.getInstance().getTtlMinutes(),
              "server.render.max-entries",
              RenderCache.getInstance().getMaxEntries(),
              "server.session.sweep-interval-seconds",
              HServerHousekeeping.getInstance().getSweepIntervalSeconds()));
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading server status", e);
    }
  }

  @POST
  @Path("/housekeeping/run")
  @Produces(MediaType.APPLICATION_JSON)
  public Response runHousekeeping() {
    try {
      requireAdmin();
      HServerHousekeeping.getInstance().runOnce();
      emit("server.housekeeping.run", null);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("success", true);
      body.put("housekeeping", HServerHousekeeping.getInstance().stats());
      body.put("renderCache", RenderCache.getInstance().stats());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error running housekeeping", e);
    }
  }

  @DELETE
  @Path("/renders/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response evictOne(@PathParam("id") String id) {
    try {
      requireAdmin();
      var removed = hopperRest.removeRenderingById(id);
      emit("server.render.evict", id);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("success", removed != null);
      body.put("id", id);
      body.put("renderCache", RenderCache.getInstance().stats());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error evicting render " + id, e);
    }
  }

  @DELETE
  @Path("/renders")
  @Produces(MediaType.APPLICATION_JSON)
  public Response evictAll() {
    try {
      requireAdmin();
      int before = RenderCache.getInstance().size();
      hopperRest.clearRenderings();
      emit("server.render.evict_all", "count=" + before);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("success", true);
      body.put("evicted", before);
      body.put("renderCache", RenderCache.getInstance().stats());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error clearing render cache", e);
    }
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }

  private void emit(String op, String detail) {
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.SECURITY_CHANGE)
            .actor(HSecurityContext.getPrincipal())
            .actionCode(HAction.SECURITY_ADMIN.code());
    event.setRequestId(HSecurityContext.getRequestId());
    event.getAttributes().put("operation", op);
    if (detail != null) {
      event.getAttributes().put("detail", detail);
    }
    HAuditEmitter.getInstance().emitSafely(event);
  }
}
