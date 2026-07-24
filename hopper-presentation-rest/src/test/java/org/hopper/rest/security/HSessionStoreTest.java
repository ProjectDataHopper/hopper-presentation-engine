package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class HSessionStoreTest {

  @AfterEach
  void clear() {
    HSessionStore.getInstance().clear();
  }

  @Test
  void createAndGetSession() {
    HSessionStore store = HSessionStore.getInstance();
    store.setTtl(Duration.ofMinutes(30));
    HPrincipal principal =
        HPrincipal.builder()
            .username("alice")
            .role(HRole.AUTHOR.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_OAUTH2)
            .build();
    HBrowserSession session = store.create(principal);
    assertTrue(store.get(session.getId()).isPresent());
    assertEquals("alice", store.get(session.getId()).get().getPrincipal().getUsername());
    store.remove(session.getId());
    assertTrue(store.get(session.getId()).isEmpty());
  }
}
