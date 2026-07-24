package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditOutcome;
import org.hopper.rest.HRest;
import org.hopper.rest.security.HAuthMode;
import org.hopper.rest.security.HBrowserSession;
import org.hopper.rest.security.HSecuritySettings;
import org.hopper.rest.security.HSessionStore;
import org.hopper.rest.security.OAuth2AuthenticationException;
import org.hopper.rest.security.OidcBrowserLoginService;
import org.hopper.security.HPrincipal;
import org.hopper.security.HSecurityContext;

/**
 * Browser authentication endpoints: OIDC PKCE login, session cookie, /me, logout. Also supports a
 * static-dev session bootstrap for local UI testing.
 */
@Path("auth")
public class AuthResource extends BaseResource {

  public static final String COOKIE_PATH = "/hopper";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Path("/config")
  @Produces(MediaType.APPLICATION_JSON)
  public Response config() {
    try {
      HSecuritySettings s = hopperRest.getSecuritySettings();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("authEnabled", s.isAuthEnabled());
      body.put("mode", s.getAuthMode().name());
      body.put(
          "browserLogin",
          s.isAuthEnabled()
              && (s.getAuthMode() == HAuthMode.OAUTH2
                      && hopperRest.getOidcBrowserLoginService() != null
                      && hopperRest.getOidcBrowserLoginService().isConfigured()
                  || s.getAuthMode() == HAuthMode.STATIC_DEV));
      body.put("loginPath", "/hopper/api/auth/login");
      body.put("logoutPath", "/hopper/api/auth/logout");
      body.put("mePath", "/hopper/api/auth/me");
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (Exception e) {
      return getServerError("Error building auth config", e);
    }
  }

  @GET
  @Path("/me")
  @Produces(MediaType.APPLICATION_JSON)
  public Response me() {
    try {
      HPrincipal principal = HSecurityContext.getPrincipal();
      if (principal == null
          || principal.isAnonymous()
          || HPrincipal.AUTH_METHOD_DISABLED.equalsIgnoreCase(principal.getAuthMethod())) {
        if (hopperRest.getSecuritySettings().isAuthEnabled()) {
          return Response.status(Response.Status.UNAUTHORIZED)
              .entity("{\"authenticated\":false}")
              .type(MediaType.APPLICATION_JSON)
              .build();
        }
      }
      Map<String, Object> body = principalMap(principal);
      body.put("authenticated", principal != null && !principal.isAnonymous());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (Exception e) {
      return getServerError("Error reading current user", e);
    }
  }

  @GET
  @Path("/login")
  public Response login(
      @QueryParam("returnTo") String returnTo, @Context UriInfo uriInfo) {
    HSecuritySettings settings = hopperRest.getSecuritySettings();
    try {
      if (!settings.isAuthEnabled()) {
        return Response.seeOther(URI.create(safeReturn(returnTo))).build();
      }
      if (settings.getAuthMode() == HAuthMode.STATIC_DEV) {
        // Create a browser session from static-dev principal
        HPrincipal principal =
            HPrincipal.builder()
                .subject(settings.getDevUser())
                .username(settings.getDevUser())
                .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
                .roles(settings.getDevRoles())
                .build();
        HSessionStore.getInstance()
            .setTtl(Duration.ofMinutes(Math.max(5, settings.getSessionTtlMinutes())));
        HBrowserSession session = HSessionStore.getInstance().create(principal);
        emitLogin(principal);
        return Response.seeOther(URI.create(safeReturn(returnTo)))
            .cookie(sessionCookie(settings, session.getId(), false))
            .build();
      }
      OidcBrowserLoginService oidc = hopperRest.getOidcBrowserLoginService();
      if (oidc == null || !oidc.isConfigured()) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity("OIDC browser login is not configured (auth.oidc.client-id / issuer-uri)")
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      String authorizeUrl = oidc.beginLogin(returnTo);
      return Response.seeOther(URI.create(authorizeUrl)).build();
    } catch (Exception e) {
      return getServerError("Login failed", e);
    }
  }

  @GET
  @Path("/callback")
  public Response callback(
      @QueryParam("code") String code,
      @QueryParam("state") String state,
      @QueryParam("error") String error,
      @QueryParam("error_description") String errorDescription) {
    try {
      if (error != null && !error.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("OIDC error: " + error + " " + (errorDescription != null ? errorDescription : ""))
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      OidcBrowserLoginService oidc = hopperRest.getOidcBrowserLoginService();
      if (oidc == null) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity("OIDC not configured")
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      HSecuritySettings settings = hopperRest.getSecuritySettings();
      HSessionStore.getInstance()
          .setTtl(Duration.ofMinutes(Math.max(5, settings.getSessionTtlMinutes())));
      OidcBrowserLoginService.LoginResult result = oidc.completeLoginWithReturn(code, state);
      emitLogin(result.session().getPrincipal());
      return Response.seeOther(URI.create(safeReturn(result.returnTo())))
          .cookie(sessionCookie(settings, result.session().getId(), false))
          .build();
    } catch (OAuth2AuthenticationException e) {
      HAuditEvent event = HAuditEvent.of(HAuditEventType.AUTH_FAILURE);
      event.setOutcome(HAuditOutcome.FAILURE);
      event.setErrorMessage(e.getMessage());
      HAuditEmitter.getInstance().emitSafely(event);
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(e.getMessage())
          .type(MediaType.TEXT_PLAIN)
          .build();
    } catch (Exception e) {
      return getServerError("OIDC callback failed", e);
    }
  }

  @POST
  @Path("/logout")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logout(@Context HttpHeaders headers) {
    try {
      HSecuritySettings settings = hopperRest.getSecuritySettings();
      String cookieName = settings.getSessionCookieName();
      String sessionId = readCookie(headers, cookieName);
      if (sessionId != null) {
        HSessionStore.getInstance().remove(sessionId);
      }
      Map<String, Object> body = Map.of("ok", true);
      return Response.ok(MAPPER.writeValueAsString(body))
          .cookie(sessionCookie(settings, "", true))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (Exception e) {
      return getServerError("Logout failed", e);
    }
  }

  private void emitLogin(HPrincipal principal) {
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.AUTH_LOGIN).actor(principal).actionCode("auth.login");
    event.setOutcome(HAuditOutcome.SUCCESS);
    event.setRequestId(HSecurityContext.getRequestId());
    HAuditEmitter.getInstance().emitSafely(event);
  }

  private static Map<String, Object> principalMap(HPrincipal principal) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (principal == null) {
      body.put("username", "anonymous");
      body.put("roles", new ArrayList<>());
      return body;
    }
    body.put("subject", principal.getSubject());
    body.put("username", principal.getUsername());
    body.put("email", principal.getEmail());
    body.put("roles", new ArrayList<>(principal.getRoles()));
    body.put("authMethod", principal.getAuthMethod());
    return body;
  }

  private static NewCookie sessionCookie(
      HSecuritySettings settings, String value, boolean clear) {
    int maxAge = clear ? 0 : Math.max(60, settings.getSessionTtlMinutes() * 60);
    return new NewCookie.Builder(settings.getSessionCookieName())
        .value(clear ? "" : value)
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .httpOnly(true)
        .secure(settings.isSessionCookieSecure())
        .sameSite(NewCookie.SameSite.LAX)
        .build();
  }

  static String readCookie(HttpHeaders headers, String name) {
    if (headers == null || name == null) {
      return null;
    }
    Map<String, jakarta.ws.rs.core.Cookie> cookies = headers.getCookies();
    if (cookies == null) {
      return null;
    }
    jakarta.ws.rs.core.Cookie c = cookies.get(name);
    return c != null ? c.getValue() : null;
  }

  private static String safeReturn(String returnTo) {
    return OidcBrowserLoginService.sanitizeReturnTo(returnTo);
  }
}
