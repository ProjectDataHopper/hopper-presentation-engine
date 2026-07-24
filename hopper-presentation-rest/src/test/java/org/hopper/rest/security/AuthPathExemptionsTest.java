package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AuthPathExemptionsTest {

  @Test
  public void authEndpointsArePublic() {
    assertTrue(AuthPathExemptions.isPublic("GET", "auth/callback"));
    assertTrue(AuthPathExemptions.isPublic("GET", "auth/callback/"));
    assertTrue(AuthPathExemptions.isPublic("GET", "/auth/login"));
    assertTrue(AuthPathExemptions.isPublic("GET", "auth/config"));
    assertTrue(AuthPathExemptions.isPublic("GET", "auth/me"));
    assertTrue(AuthPathExemptions.isPublic("POST", "auth/logout"));
  }

  @Test
  public void homeShellIsPublicGet() {
    assertTrue(AuthPathExemptions.isPublic("GET", "render/main/"));
    assertTrue(AuthPathExemptions.isPublic("GET", "render/main"));
    assertFalse(AuthPathExemptions.isPublic("POST", "render/presentation"));
  }

  @Test
  public void staticIsPublic() {
    assertTrue(AuthPathExemptions.isPublic("GET", "static/hopper-auth.js"));
  }

  @Test
  public void healthIsPublic() {
    assertTrue(AuthPathExemptions.isPublic("GET", "health"));
    assertTrue(AuthPathExemptions.isPublic("GET", "system/health"));
    assertTrue(AuthPathExemptions.isPublic("GET", "system/health/"));
  }
}
