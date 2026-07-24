package org.hopper.rest.resources;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HBuiltInRoles;
import org.hopper.security.HRole;
import org.hopper.security.HSecurityContext;
import org.hopper.security.HSecurityRole;

/**
 * Admin CRUD for roles (built-in catalog + custom {@code security-role} metadata). Requires {@link
 * HAction#SECURITY_ADMIN}.
 */
@Path("admin/roles")
public class AdminRoleResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response list() {
    try {
      requireAdmin();
      List<Map<String, Object>> items = new ArrayList<>();
      for (HRole role : HRole.values()) {
        items.add(systemRoleMap(role));
      }
      Set<String> systemNames =
          java.util.Arrays.stream(HRole.values())
              .map(HRole::roleName)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      for (HSecurityRole custom : hopperRest.getRoleGrantResolver().getRoleSource().listAll()) {
        if (custom == null || custom.getName() == null) {
          continue;
        }
        if (systemNames.contains(HSecurityRole.normalizeName(custom.getName()))) {
          // Custom doc shadowing a system name — show as custom override note
          Map<String, Object> m = customRoleMap(custom, true);
          m.put("warning", "Name collides with a system role; grants are merged with built-in.");
          items.add(m);
        } else {
          items.add(customRoleMap(custom, false));
        }
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("roles", items);
      body.put("count", items.size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing roles", e);
    }
  }

  @GET
  @Path("/actions")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listActions() {
    try {
      requireAdmin();
      List<Map<String, Object>> actions = new ArrayList<>();
      for (HAction a : HAction.values()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.code());
        m.put("name", a.name());
        String family = a.code().contains(".") ? a.code().substring(0, a.code().indexOf('.')) : a.code();
        m.put("family", family);
        actions.add(m);
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("actions", actions);
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing actions", e);
    }
  }

  @GET
  @Path("/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(@PathParam("name") String name) {
    try {
      requireAdmin();
      String normalized = HSecurityRole.normalizeName(name);
      var builtIn = HRole.fromName(normalized);
      if (builtIn.isPresent()) {
        return Response.ok(MAPPER.writeValueAsString(systemRoleMap(builtIn.get())))
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
      var custom = hopperRest.getRoleGrantResolver().getRoleSource().find(normalized);
      if (custom.isEmpty()) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("Role not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      return Response.ok(MAPPER.writeValueAsString(customRoleMap(custom.get(), false)))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading role " + name, e);
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response save(HSecurityRole body) {
    try {
      requireAdmin();
      if (body == null || body.getName() == null || body.getName().isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"name is required\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
      body.setName(HSecurityRole.normalizeName(body.getName()));
      if (HRole.fromName(body.getName()).isPresent()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                "{\"error\":\"Cannot create or overwrite system role: "
                    + body.getName()
                    + "\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
      if (body.getActions() == null) {
        body.setActions(new ArrayList<>());
      }
      if (body.getInheritsFrom() == null) {
        body.setInheritsFrom(new ArrayList<>());
      }
      // Normalize inherits
      List<String> inherits = new ArrayList<>();
      for (String i : body.getInheritsFrom()) {
        if (i != null && !i.isBlank()) {
          inherits.add(HSecurityRole.normalizeName(i));
        }
      }
      body.setInheritsFrom(inherits);

      serializer().save(body);
      hopperRest.invalidateRoleGrants();
      emitSecurityChange("role.save", body.getName());
      hopperRest.getLog().logBasic("Saved security role '" + body.getName() + "'");
      return Response.ok(MAPPER.writeValueAsString(customRoleMap(body, false)))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error saving role", e);
    }
  }

  @DELETE
  @Path("/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response delete(@PathParam("name") String name) {
    try {
      requireAdmin();
      String normalized = HSecurityRole.normalizeName(name);
      if (HRole.fromName(normalized).isPresent()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Cannot delete system role: " + normalized)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      IHopMetadataSerializer<HSecurityRole> serializer = serializer();
      String docName = findDocumentName(serializer, normalized);
      if (docName == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("Role not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      serializer.delete(docName);
      hopperRest.invalidateRoleGrants();
      emitSecurityChange("role.delete", normalized);
      hopperRest.getLog().logBasic("Deleted security role '" + normalized + "'");
      return Response.ok().entity(normalized).type(MediaType.TEXT_PLAIN).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error deleting role " + name, e);
    }
  }

  private Map<String, Object> systemRoleMap(HRole role) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", role.roleName());
    m.put("system", true);
    m.put("description", "Built-in Hopper role");
    m.put(
        "actions",
        HBuiltInRoles.actionsFor(role).stream().map(HAction::code).sorted().toList());
    m.put("inheritsFrom", List.of());
    m.put("editable", false);
    return m;
  }

  private Map<String, Object> customRoleMap(HSecurityRole role, boolean systemCollision) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", role.getName());
    m.put("system", false);
    m.put("description", role.getDescription() != null ? role.getDescription() : "");
    m.put("actions", role.getActions() != null ? role.getActions() : List.of());
    m.put("inheritsFrom", role.getInheritsFrom() != null ? role.getInheritsFrom() : List.of());
    m.put(
        "expandedActions",
        hopperRest.getRoleGrantResolver().actionsForRole(role.getName()).stream()
            .map(HAction::code)
            .sorted()
            .toList());
    m.put("editable", !systemCollision);
    return m;
  }

  private String findDocumentName(IHopMetadataSerializer<HSecurityRole> serializer, String normalized)
      throws Exception {
    if (serializer.exists(normalized)) {
      return normalized;
    }
    for (String n : serializer.listObjectNames()) {
      if (normalized.equals(HSecurityRole.normalizeName(n))) {
        return n;
      }
    }
    return null;
  }

  private IHopMetadataSerializer<HSecurityRole> serializer() throws Exception {
    return hopperRest.getMetadataProvider().getSerializer(HSecurityRole.class);
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
    event.getAttributes().put("roleName", name);
    HAuditEmitter.getInstance().emitSafely(event);
  }
}
