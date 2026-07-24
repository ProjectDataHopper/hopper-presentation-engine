package org.hopper.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditOutcome;
import org.hopper.rest.HRest;
import org.hopper.security.HAction;
import org.hopper.security.HPrincipal;
import org.hopper.security.HResourceRef;
import org.hopper.security.HSecurityContext;

/**
 * Enforces action-based authorization for mapped REST paths when auth is enabled.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {

  private final PathActionMapper pathActionMapper = new PathActionMapper();

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    HSecuritySettings settings = HRest.getInstance().getSecuritySettings();
    if (settings == null || !settings.isAuthEnabled()) {
      return;
    }

    String path = requestContext.getUriInfo().getPath();
    String method = requestContext.getMethod();

    // Login, callback, static assets, home shell — never demand a principal here
    if (AuthPathExemptions.isPublic(method, path)) {
      return;
    }

    HPrincipal principal = HSecurityContext.getPrincipal();
    if (principal == null
        || principal.isAnonymous()
        || HPrincipal.AUTH_METHOD_DISABLED.equalsIgnoreCase(principal.getAuthMethod())) {
      if (AuthPathExemptions.isBrowserHtmlGet(
          method, path, requestContext.getHeaderString(HttpHeaders.ACCEPT))) {
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        String returnTo = requestUri.getRawPath();
        if (requestUri.getRawQuery() != null) {
          returnTo = returnTo + "?" + requestUri.getRawQuery();
        }
        URI login =
            UriBuilder.fromPath("/hopper/api/auth/login").queryParam("returnTo", returnTo).build();
        requestContext.abortWith(Response.seeOther(login).build());
        return;
      }
      deny(requestContext, principal, null, null, Response.Status.UNAUTHORIZED, "Authentication required");
      return;
    }

    Optional<HAction> action = pathActionMapper.requiredAction(method, path);
    if (action.isEmpty()) {
      // Authenticated but unmapped path: allow (narrow later)
      return;
    }

    Optional<HResourceRef> resource = pathActionMapper.resourceRef(method, path);
    boolean allowed =
        HSecurityContext.getAuthorizationService()
            .can(principal, action.get(), resource.orElse(null));
    if (!allowed) {
      String msg =
          "Access denied for action '"
              + action.get().code()
              + "'"
              + (resource.isPresent() ? " on " + resource.get() : "");
      deny(
          requestContext,
          principal,
          action.get(),
          resource.orElse(null),
          Response.Status.FORBIDDEN,
          msg);
    }
  }

  private void deny(
      ContainerRequestContext requestContext,
      HPrincipal principal,
      HAction action,
      HResourceRef resource,
      Response.Status status,
      String message) {
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.AUTHZ_DENY)
            .actor(principal)
            .actionCode(action != null ? action.code() : null)
            .resource(resource);
    event.setOutcome(HAuditOutcome.DENIED);
    event.setRequestId(HSecurityContext.getRequestId());
    event.setErrorMessage(message);
    HAuditEmitter.getInstance().emitSafely(event);

    requestContext.abortWith(
        Response.status(status).entity(message).type("text/plain; charset=UTF-8").build());
  }
}
