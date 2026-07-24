package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.rest.security.HActiveUsageRegistry;
import org.hopper.rest.security.HBrowserSession;
import org.hopper.rest.security.HSessionStore;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.hopper.security.HSecurityContext;

/** Live usage / session admin APIs for “who is doing what”. */
@Path("admin/usage")
public class AdminUsageResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Path("/active")
  @Produces(MediaType.APPLICATION_JSON)
  public Response activeRenders() {
    try {
      requireUsageViewer();
      List<Map<String, Object>> items = new ArrayList<>();
      for (HActiveUsageRegistry.ActiveUsage u : HActiveUsageRegistry.getInstance().listActive()) {
        items.add(u.toMap());
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("activeRenders", items);
      body.put("count", items.size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing active usage", e);
    }
  }

  @GET
  @Path("/sessions")
  @Produces(MediaType.APPLICATION_JSON)
  public Response activeSessions() {
    try {
      requireUsageViewer();
      List<Map<String, Object>> items = new ArrayList<>();
      for (HBrowserSession session : HSessionStore.getInstance().listActive()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", session.getId());
        m.put("username", session.getPrincipal().getUsername());
        m.put("roles", session.getPrincipal().getRoles());
        m.put("authMethod", session.getPrincipal().getAuthMethod());
        m.put("createdAt", session.getCreatedAt().toString());
        m.put("lastAccessAt", session.getLastAccessAt().toString());
        m.put("expiresAt", session.getExpiresAt().toString());
        items.add(m);
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("sessions", items);
      body.put("count", items.size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing sessions", e);
    }
  }

  private void requireUsageViewer() throws HAccessDeniedException {
    HPrincipal principal = HSecurityContext.getPrincipal();
    // ADMIN has security.admin; AUDITOR has audit.read
    if (principal != null
        && (principal.hasRole(HRole.ADMIN)
            || principal.hasRole(HRole.AUDITOR)
            || principal.isSystem())) {
      return;
    }
    HSecurityContext.getAuthorizationService().check(principal, HAction.AUDIT_READ);
  }
}
