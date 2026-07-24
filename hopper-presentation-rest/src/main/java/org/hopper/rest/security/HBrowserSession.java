package org.hopper.rest.security;

import java.time.Instant;
import java.util.UUID;
import org.hopper.security.HPrincipal;

/** Server-side browser session created after OIDC login (or static-dev session login). */
public class HBrowserSession {
  private final String id;
  private final HPrincipal principal;
  private final Instant createdAt;
  private volatile Instant lastAccessAt;
  private final Instant expiresAt;

  public HBrowserSession(HPrincipal principal, Instant expiresAt) {
    this.id = UUID.randomUUID().toString();
    this.principal = principal;
    this.createdAt = Instant.now();
    this.lastAccessAt = this.createdAt;
    this.expiresAt = expiresAt;
  }

  public String getId() {
    return id;
  }

  public HPrincipal getPrincipal() {
    return principal;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastAccessAt() {
    return lastAccessAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public void touch() {
    this.lastAccessAt = Instant.now();
  }
}
