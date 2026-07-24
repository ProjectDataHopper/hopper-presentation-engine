package org.hopper.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.security.HPrincipal;
import org.hopper.security.HRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class HActiveUsageRegistryTest {

  @AfterEach
  void clear() {
    HActiveUsageRegistry.getInstance().clear();
  }

  @Test
  void tracksActiveRenders() {
    HPrincipal principal =
        HPrincipal.builder()
            .username("bob")
            .role(HRole.VIEWER.roleName())
            .authMethod(HPrincipal.AUTH_METHOD_STATIC_DEV)
            .build();
    HActiveUsageRegistry reg = HActiveUsageRegistry.getInstance();
    reg.start("rid-1", "Sales", principal, "req-1");
    assertEquals(1, reg.size());
    assertEquals("Sales", reg.listActive().get(0).presentationName());
    assertEquals("bob", reg.listActive().get(0).username());
    reg.end("rid-1");
    assertTrue(reg.listActive().isEmpty());
  }
}
