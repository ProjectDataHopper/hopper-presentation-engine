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
import java.util.List;
import java.util.Map;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityAcl;
import org.hopper.security.HSecurityContext;

/**
 * Admin API for security ACL documents ({@code security-acl} metadata). Requires {@link
 * HAction#SECURITY_ADMIN}.
 */
@Path("security/acls")
public class SecurityAclResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response list() {
    try {
      requireAdmin();
      IHopMetadataSerializer<HSecurityAcl> serializer = serializer();
      List<Map<String, Object>> items = new ArrayList<>();
      for (String name : serializer.listObjectNames()) {
        HSecurityAcl acl = serializer.load(name);
        if (acl == null) {
          continue;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", acl.getName());
        item.put("resourceType", acl.getResourceType());
        item.put("resourceName", acl.getResourceName());
        item.put("entryCount", acl.getEntries() != null ? acl.getEntries().size() : 0);
        items.add(item);
      }
      return Response.ok(MAPPER.writeValueAsString(items))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing security ACLs", e);
    }
  }

  @GET
  @Path("/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(@PathParam("name") String name) {
    try {
      requireAdmin();
      HSecurityAcl acl = serializer().load(name);
      if (acl == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("ACL not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      return Response.ok(MAPPER.writeValueAsString(acl))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading security ACL " + name, e);
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response save(HSecurityAcl body) {
    try {
      requireAdmin();
      if (body == null) {
        return getServerError("ACL body is required", false);
      }
      if (body.getResourceType() == null || body.getResourceType().isBlank()) {
        return getServerError("resourceType is required", false);
      }
      if (body.getResourceName() == null || body.getResourceName().isBlank()) {
        return getServerError("resourceName is required", false);
      }
      if (body.getName() == null || body.getName().isBlank()) {
        body.setName(HSecurityAcl.documentName(body.getResourceType(), body.getResourceName()));
      }
      serializer().save(body);
      emitSecurityChange("save", body.getName());
      hopperRest.getLog().logBasic("Saved security ACL '" + body.getName() + "'");
      return Response.ok().entity(body.getName()).type(MediaType.TEXT_PLAIN).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error saving security ACL", e);
    }
  }

  @DELETE
  @Path("/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response delete(@PathParam("name") String name) {
    try {
      requireAdmin();
      IHopMetadataSerializer<HSecurityAcl> serializer = serializer();
      if (!serializer.exists(name)) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("ACL not found: " + name)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      serializer.delete(name);
      emitSecurityChange("delete", name);
      hopperRest.getLog().logBasic("Deleted security ACL '" + name + "'");
      return Response.ok().entity(name).type(MediaType.TEXT_PLAIN).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error deleting security ACL " + name, e);
    }
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }

  private IHopMetadataSerializer<HSecurityAcl> serializer() throws Exception {
    IHopMetadataProvider provider = hopperRest.getMetadataProvider();
    return provider.getSerializer(HSecurityAcl.class);
  }

  private void emitSecurityChange(String op, String name) {
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.SECURITY_CHANGE)
            .actor(HSecurityContext.getPrincipal())
            .actionCode(HAction.SECURITY_ADMIN.code());
    event.setRequestId(HSecurityContext.getRequestId());
    event.getAttributes().put("operation", op);
    event.getAttributes().put("aclName", name);
    HAuditEmitter.getInstance().emitSafely(event);
  }
}
