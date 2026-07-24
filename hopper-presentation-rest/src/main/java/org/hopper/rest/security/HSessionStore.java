package org.hopper.rest.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory browser session store (single-node). */
public class HSessionStore {

  private static final HSessionStore INSTANCE = new HSessionStore();

  private final Map<String, HBrowserSession> sessions = new ConcurrentHashMap<>();
  private volatile Duration ttl = Duration.ofHours(8);

  public static HSessionStore getInstance() {
    return INSTANCE;
  }

  public void setTtl(Duration ttl) {
    if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
      this.ttl = ttl;
    }
  }

  public Duration getTtl() {
    return ttl;
  }

  public HBrowserSession create(org.hopper.security.HPrincipal principal) {
    Instant expires = Instant.now().plus(ttl);
    HBrowserSession session = new HBrowserSession(principal, expires);
    sessions.put(session.getId(), session);
    return session;
  }

  public Optional<HBrowserSession> get(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    HBrowserSession session = sessions.get(id);
    if (session == null) {
      return Optional.empty();
    }
    if (session.isExpired()) {
      sessions.remove(id);
      return Optional.empty();
    }
    session.touch();
    return Optional.of(session);
  }

  public void remove(String id) {
    if (id != null) {
      sessions.remove(id);
    }
  }

  public void clear() {
    sessions.clear();
  }

  public int size() {
    purgeExpired();
    return sessions.size();
  }

  public List<HBrowserSession> listActive() {
    purgeExpired();
    return new ArrayList<>(sessions.values());
  }

  private void purgeExpired() {
    sessions.entrySet().removeIf(e -> e.getValue().isExpired());
  }
}
