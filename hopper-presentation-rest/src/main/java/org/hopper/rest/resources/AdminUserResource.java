package org.hopper.rest.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.rest.security.HBrowserSession;
import org.hopper.rest.security.HSessionStore;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityContext;
import org.hopper.security.HSecurityUser;
import org.hopper.security.HUserAssignmentSource;

/**
 * Admin CRUD for user role assignments ({@code security-user} metadata). Requires {@link
 * HAction#SECURITY_ADMIN}.
 */
@Path("admin/users")
public class AdminUserResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response list() {
    try {
      requireAdmin();
      HUserAssignmentSource source = hopperRest.getPrincipalEnricher().getUserSource();
      List<Map<String, Object>> items = new ArrayList<>();
      Set<String> seen = new LinkedHashSet<>();
      for (HSecurityUser user : source.listAll()) {
        if (user == null) {
          continue;
        }
        items.add(userMap(user, liveSessionFor(user)));
        if (user.getName() != null) {
          seen.add(HSecurityUser.normalizeKey(user.getName()));
        }
      }
      // Include active session users without an assignment yet (observed)
      for (HBrowserSession session : HSessionStore.getInstance().listActive()) {
        if (session == null || session.getPrincipal() == null) {
          continue;
        }
        String email = session.getPrincipal().getEmail();
        String uname = session.getPrincipal().getUsername();
        String key =
            HSecurityUser.documentNameFor(
                email != null ? email : uname, session.getPrincipal().getSubject());
        if (key.isBlank() || seen.contains(key)) {
          continue;
        }
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("name", key);
        observed.put("email", email != null ? email : "");
        observed.put("subject", session.getPrincipal().getSubject());
        observed.put("displayName", uname);
        observed.put("roles", List.of());
        observed.put("disabled", false);
        observed.put("notes", "");
        observed.put("lastSeenAt", session.getLastAccessAt() != null ? session.getLastAccessAt().toString() : "");
        observed.put("assignment", false);
        observed.put("sessionActive", true);
        items.add(observed);
        seen.add(key);
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("users", items);
      body.put("count", items.size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing users", e);
    }
  }

  @GET
  @Path("/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(@PathParam("name") String name) {
    try {
      requireAdmin();
      HSecurityUser user =
          hopperRest
              .getPrincipalEnricher()
              .getUserSource()
              .findByName(name)
              .or(() -> hopperRest.getPrincipalEnricher().getUserSource().findByEmail(name))
              .orElse(null);
      if (user == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("User assignment not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      return Response.ok(MAPPER.writeValueAsString(userMap(user, liveSessionFor(user))))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading user " + name, e);
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response save(String jsonBody) {
    try {
      requireAdmin();
      HSecurityUser body = parseUser(jsonBody);
      if (body.getName() == null || body.getName().isBlank()) {
        String key = HSecurityUser.documentNameFor(body.getEmail(), body.getSubject());
        if (key.isBlank()) {
          return Response.status(Response.Status.BAD_REQUEST)
              .entity("{\"error\":\"name, email, or subject is required\"}")
              .type(MediaType.APPLICATION_JSON)
              .build();
        }
        body.setName(key);
      } else {
        body.setName(HSecurityUser.normalizeKey(body.getName()));
      }
      if (body.getEmail() == null || body.getEmail().isBlank()) {
        if (body.getName().contains("@")) {
          body.setEmail(body.getName());
        }
      } else {
        body.setEmail(body.getEmail().trim().toLowerCase(Locale.ROOT));
      }
      if (body.getRoles() == null) {
        body.setRoles(new ArrayList<>());
      } else {
        List<String> roles = new ArrayList<>();
        for (String r : body.getRoles()) {
          if (r != null && !r.isBlank()) {
            roles.add(r.trim().toUpperCase(Locale.ROOT));
          }
        }
        body.setRoles(roles);
      }
      serializer().save(body);
      emitSecurityChange("user.save", body.getName());
      hopperRest.getLog().logBasic("Saved security-user '" + body.getName() + "'");
      return Response.ok(MAPPER.writeValueAsString(userMap(body, liveSessionFor(body))))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error saving user assignment", e);
    }
  }

  @POST
  @Path("/{name}/roles")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response patchRoles(@PathParam("name") String name, String jsonBody) {
    try {
      requireAdmin();
      IHopMetadataSerializer<HSecurityUser> serializer = serializer();
      HSecurityUser user =
          hopperRest
              .getPrincipalEnricher()
              .getUserSource()
              .findByName(name)
              .orElse(null);
      if (user == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("User assignment not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      Map<String, Object> root =
          MAPPER.readValue(jsonBody, new TypeReference<Map<String, Object>>() {});
      Object rolesNode = root.get("roles");
      List<String> roles = new ArrayList<>();
      if (rolesNode instanceof List<?> list) {
        for (Object o : list) {
          if (o != null && !o.toString().isBlank()) {
            roles.add(o.toString().trim().toUpperCase(Locale.ROOT));
          }
        }
      }
      user.setRoles(roles);
      serializer.save(user);
      emitSecurityChange("user.roles", user.getName());
      return Response.ok(MAPPER.writeValueAsString(userMap(user, liveSessionFor(user))))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error updating user roles", e);
    }
  }

  @DELETE
  @Path("/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response delete(@PathParam("name") String name) {
    try {
      requireAdmin();
      IHopMetadataSerializer<HSecurityUser> serializer = serializer();
      String key = HSecurityUser.normalizeKey(name);
      String docName = null;
      if (serializer.exists(key)) {
        docName = key;
      } else if (serializer.exists(name)) {
        docName = name;
      } else {
        for (String n : serializer.listObjectNames()) {
          if (key.equals(HSecurityUser.normalizeKey(n))) {
            docName = n;
            break;
          }
        }
      }
      if (docName == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("User assignment not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      serializer.delete(docName);
      emitSecurityChange("user.delete", key);
      hopperRest.getLog().logBasic("Deleted security-user '" + key + "'");
      return Response.ok().entity(key).type(MediaType.TEXT_PLAIN).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error deleting user " + name, e);
    }
  }

  private HSecurityUser parseUser(String jsonBody) throws Exception {
    if (jsonBody == null || jsonBody.isBlank()) {
      return new HSecurityUser();
    }
    return MAPPER.readValue(jsonBody, HSecurityUser.class);
  }

  private Map<String, Object> userMap(HSecurityUser user, boolean sessionActive) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", user.getName());
    m.put("email", user.getEmail() != null ? user.getEmail() : "");
    m.put("subject", user.getSubject() != null ? user.getSubject() : "");
    m.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "");
    m.put("roles", user.getRoles() != null ? user.getRoles() : List.of());
    m.put("disabled", user.isDisabled());
    m.put("notes", user.getNotes() != null ? user.getNotes() : "");
    m.put("lastSeenAt", user.getLastSeenAt() != null ? user.getLastSeenAt() : "");
    m.put("assignment", true);
    m.put("sessionActive", sessionActive);
    return m;
  }

  private boolean liveSessionFor(HSecurityUser user) {
    if (user == null) {
      return false;
    }
    String email = user.getEmail() != null ? user.getEmail() : user.getName();
    String subject = user.getSubject();
    for (HBrowserSession session : HSessionStore.getInstance().listActive()) {
      if (session == null || session.getPrincipal() == null) {
        continue;
      }
      if (email != null
          && !email.isBlank()
          && email.equalsIgnoreCase(
              session.getPrincipal().getEmail() != null
                  ? session.getPrincipal().getEmail()
                  : session.getPrincipal().getUsername())) {
        return true;
      }
      if (subject != null
          && !subject.isBlank()
          && subject.equalsIgnoreCase(session.getPrincipal().getSubject())) {
        return true;
      }
    }
    return false;
  }

  private IHopMetadataSerializer<HSecurityUser> serializer() throws Exception {
    return hopperRest.getMetadataProvider().getSerializer(HSecurityUser.class);
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }

  private void emitSecurityChange(String op, String name) {
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.SECURITY_CHANGE)
            .actor(HSecurityContext.getPrincipal())
            .actionCode(HAction.SECURITY_ADMIN.code());
    event.setRequestId(HSecurityContext.getRequestId());
    event.getAttributes().put("operation", op);
    event.getAttributes().put("userName", name);
    event.getAttributes().put("at", Instant.now().toString());
    HAuditEmitter.getInstance().emitSafely(event);
  }
}
