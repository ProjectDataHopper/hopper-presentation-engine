package org.hopper.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditOutcome;
import org.hopper.rest.HRest;
import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.hopper.security.HSecurityContext;

/**
 * Establishes {@link HPrincipal} on {@link HSecurityContext} when auth is enabled.
 *
 * <p>Modes: {@code static-dev} (properties / headers / session cookie), {@code oauth2} (Bearer JWT
 * or browser session cookie after PKCE login).
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

  public static final String HEADER_USER = "X-Hopper-User";
  public static final String HEADER_ROLES = "X-Hopper-Roles";
  public static final String HEADER_REQUEST_ID = "X-Request-Id";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    // Always start clean: ThreadLocal session id must not leak across Tomcat worker threads.
    // A stale HRenderSession made getRendering() fail ownership checks right after HTML open.
    HRenderSession.clear();

    HSecuritySettings settings = HRest.getInstance().getSecuritySettings();
    String requestId = requestContext.getHeaderString(HEADER_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    requestContext.setProperty(HEADER_REQUEST_ID, requestId);
    HSecurityContext.setRequestId(requestId);

    if (settings == null || !settings.isAuthEnabled()) {
      // Open / local demo mode: grant ADMIN so resource-level requireAdmin() checks pass.
      // (AuthorizationFilter already skips action checks when auth is disabled.)
      HSecurityContext.setPrincipal(localOpenAdmin());
      return;
    }

    String path = requestContext.getUriInfo().getPath();
    String method = requestContext.getMethod();
    if (AuthPathExemptions.isPublic(method, path)) {
      // Attach session when present; never block login/callback/static/home shell
      HPrincipal fromSession = principalFromSession(settings, requestContext);
      if (fromSession != null) {
        setEnrichedPrincipal(fromSession);
      } else if (settings.getAuthMode() == HAuthMode.STATIC_DEV
          && AuthPathExemptions.normalize(path).equals("auth/me")) {
        setEnrichedPrincipal(resolveStaticDev(settings, requestContext));
      }
      return;
    }

    switch (settings.getAuthMode()) {
      case STATIC_DEV -> {
        HPrincipal fromSession = principalFromSession(settings, requestContext);
        if (fromSession != null) {
          setEnrichedPrincipal(fromSession);
        } else {
          setEnrichedPrincipal(resolveStaticDev(settings, requestContext));
        }
      }
      case OAUTH2 -> authenticateOAuth2(settings, requestContext);
      case DISABLED -> HSecurityContext.setPrincipal(localOpenAdmin());
    }
  }

  /**
   * Principal used when authentication is off: not anonymous (so authorization {@code can()} does
   * not deny), with ADMIN for admin-panel APIs that call {@code requireAdmin()}.
   */
  private static HPrincipal localOpenAdmin() {
    return HPrincipal.builder()
        .subject("local")
        .username("local")
        .authMethod(HPrincipal.AUTH_METHOD_DISABLED)
        .role(HRole.ADMIN.roleName())
        .role(HRole.AUTHENTICATED.roleName())
        .build();
  }

  private void setEnrichedPrincipal(HPrincipal principal) {
    if (principal == null) {
      return;
    }
    try {
      HSecurityContext.setPrincipal(HRest.getInstance().enrichPrincipal(principal));
    } catch (Exception e) {
      HSecurityContext.setPrincipal(principal);
    }
  }

  private void authenticateOAuth2(
      HSecuritySettings settings, ContainerRequestContext requestContext) {
    // 1) Bearer access token (API clients)
    String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorization != null && !authorization.isBlank()) {
      OAuth2JwtValidator validator = HRest.getInstance().getOAuth2JwtValidator();
      if (validator == null) {
        abort(
            requestContext,
            Response.Status.INTERNAL_SERVER_ERROR,
            "OAuth2 validator is not configured");
        return;
      }
      try {
        HPrincipal principal = validator.authenticateBearer(authorization);
        setEnrichedPrincipal(principal);
        return;
      } catch (OAuth2AuthenticationException e) {
        emitAuthFailure(e.getMessage());
        abortUnauthorized(requestContext, e.getMessage());
        return;
      }
    }

    // 2) Browser session cookie (after OIDC PKCE login)
    HPrincipal fromSession = principalFromSession(settings, requestContext);
    if (fromSession != null) {
      setEnrichedPrincipal(fromSession);
      return;
    }

    // 3) Unauthenticated browser navigation → redirect to login (not a bare 401)
    if (AuthPathExemptions.isBrowserHtmlGet(
        requestContext.getMethod(),
        requestContext.getUriInfo().getPath(),
        requestContext.getHeaderString(HttpHeaders.ACCEPT))) {
      redirectToLogin(requestContext);
      return;
    }

    emitAuthFailure("Authentication required");
    abortUnauthorized(requestContext, "Authentication required");
  }

  private void redirectToLogin(ContainerRequestContext requestContext) {
    URI requestUri = requestContext.getUriInfo().getRequestUri();
    String returnTo = requestUri.getRawPath();
    if (requestUri.getRawQuery() != null) {
      returnTo = returnTo + "?" + requestUri.getRawQuery();
    }
    URI login =
        UriBuilder.fromPath("/hopper/api/auth/login")
            .queryParam("returnTo", returnTo)
            .build();
    requestContext.abortWith(Response.seeOther(login).build());
  }

  private HPrincipal principalFromSession(
      HSecuritySettings settings, ContainerRequestContext requestContext) {
    String cookieName = settings.getSessionCookieName();
    Map<String, Cookie> cookies = requestContext.getCookies();
    if (cookies == null || cookieName == null) {
      return null;
    }
    Cookie cookie = cookies.get(cookieName);
    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
      return null;
    }
    return HSessionStore.getInstance()
        .get(cookie.getValue())
        .map(HBrowserSession::getPrincipal)
        .orElse(null);
  }

  private void emitAuthFailure(String message) {
    HAuditEvent event = HAuditEvent.of(HAuditEventType.AUTH_FAILURE);
    event.setOutcome(HAuditOutcome.FAILURE);
    event.setErrorMessage(message);
    event.setRequestId(HSecurityContext.getRequestId());
    event.setAction("auth.login");
    HAuditEmitter.getInstance().emitSafely(event);
  }

  private HPrincipal resolveStaticDev(
      HSecuritySettings settings, ContainerRequestContext requestContext) {
    String user = settings.getDevUser();
    Set<String> roles = new LinkedHashSet<>(settings.getDevRoles());

    if (settings.isAllowDevHeaderOverride()) {
      String headerUser = requestContext.getHeaderString(HEADER_USER);
      if (headerUser != null && !headerUser.isBlank()) {
        user = headerUser.trim();
      }
      String headerRoles = requestContext.getHeaderString(HEADER_ROLES);
      if (headerRoles != null && !headerRoles.isBlank()) {
        roles =
            Arrays.stream(headerRoles.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
      }
    }

    return HPrincipal.builder()
        .subject(user)
        .username(user)
        .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
        .roles(roles)
        .build();
  }

  private void abortUnauthorized(ContainerRequestContext requestContext, String message) {
    requestContext.abortWith(
        Response.status(Response.Status.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"hopper\", error=\"invalid_token\"")
            .entity(message != null ? message : "Unauthorized")
            .type("text/plain; charset=UTF-8")
            .build());
  }

  private void abort(
      ContainerRequestContext requestContext, Response.Status status, String message) {
    requestContext.abortWith(
        Response.status(status).entity(message).type("text/plain; charset=UTF-8").build());
  }
}
