package org.hopper.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.hopper.security.HSecurityContext;

/** Clears thread-local security state and echoes {@code X-Request-Id}. */
@Provider
@Priority(Priorities.USER)
public class SecurityContextCleanupFilter implements ContainerResponseFilter {

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    Object requestId = requestContext.getProperty(AuthenticationFilter.HEADER_REQUEST_ID);
    if (requestId == null) {
      requestId = HSecurityContext.getRequestId();
    }
    if (requestId != null) {
      responseContext.getHeaders().putSingle(AuthenticationFilter.HEADER_REQUEST_ID, requestId.toString());
    }
    HSecurityContext.clear();
  }
}
