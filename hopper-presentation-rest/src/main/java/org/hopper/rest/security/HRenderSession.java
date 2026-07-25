package org.hopper.rest.security;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hopper.rest.HRest;

/**
 * Resolves the session id used to own in-memory presentation renderings.
 *
 * <p>Preference order:
 *
 * <ol>
 *   <li>Authenticated browser session ({@link HBrowserSession} cookie)
 *   <li>Guest render session cookie {@link #COOKIE_NAME} (anonymous / auth-disabled)
 *   <li>Newly generated guest id (caller must attach {@link #newGuestCookieIfCreated()} on the
 *       response)
 * </ol>
 */
public final class HRenderSession {

  /** HttpOnly cookie for browsers without a login session. */
  public static final String COOKIE_NAME = "HOPPER_RENDER_SESSION";

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<String> PENDING_GUEST = new ThreadLocal<>();

  private HRenderSession() {}

  /**
   * Resolve and remember the render session for this request.
   *
   * @param headers request headers (cookies)
   * @return non-blank session id
   */
  public static String resolve(HttpHeaders headers) {
    String existing = CURRENT.get();
    if (existing != null && !existing.isBlank()) {
      return existing;
    }
    String id = resolveFromCookies(headers);
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString();
      PENDING_GUEST.set(id);
    }
    CURRENT.set(id);
    return id;
  }

  /** Session id for the current request, or null if {@link #resolve} was not called. */
  public static String getCurrent() {
    return CURRENT.get();
  }

  /**
   * If a new guest session was created this request, return a Set-Cookie to attach; clears pending.
   */
  public static NewCookie newGuestCookieIfCreated() {
    String id = PENDING_GUEST.get();
    PENDING_GUEST.remove();
    if (id == null || id.isBlank()) {
      return null;
    }
    // 7 days; housecleaning is still via render TTL
    return new NewCookie.Builder(COOKIE_NAME)
        .value(id)
        .path("/hopper")
        .maxAge(7 * 24 * 3600)
        .httpOnly(true)
        .secure(false)
        .sameSite(NewCookie.SameSite.LAX)
        .build();
  }

  /** Clear thread-locals (optional; request end). */
  public static void clear() {
    CURRENT.remove();
    PENDING_GUEST.remove();
  }

  /** Test helper: pin the current session id without cookies. */
  public static void setCurrentForTests(String sessionId) {
    PENDING_GUEST.remove();
    if (sessionId == null || sessionId.isBlank()) {
      CURRENT.remove();
    } else {
      CURRENT.set(sessionId);
    }
  }

  private static String resolveFromCookies(HttpHeaders headers) {
    if (headers == null) {
      return null;
    }
    Map<String, Cookie> cookies = headers.getCookies();
    if (cookies == null) {
      return null;
    }

    // Prefer login browser session when present
    try {
      HSecuritySettings settings = HRest.getInstance().getSecuritySettings();
      if (settings != null
          && settings.isAuthEnabled()
          && settings.getSessionCookieName() != null
          && !settings.getSessionCookieName().isBlank()) {
        Cookie login = cookies.get(settings.getSessionCookieName());
        if (login != null && login.getValue() != null && !login.getValue().isBlank()) {
          Optional<HBrowserSession> session = HSessionStore.getInstance().get(login.getValue());
          if (session.isPresent() && !session.get().isExpired()) {
            session.get().touch();
            return session.get().getId();
          }
        }
      }
    } catch (Exception ignored) {
      // fall through to guest cookie
    }

    Cookie guest = cookies.get(COOKIE_NAME);
    if (guest != null && guest.getValue() != null && !guest.getValue().isBlank()) {
      return guest.getValue().trim();
    }
    return null;
  }
}
