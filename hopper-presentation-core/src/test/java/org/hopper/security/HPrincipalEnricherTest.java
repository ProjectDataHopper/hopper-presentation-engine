package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class HPrincipalEnricherTest {

  @Test
  void mergesAssignmentRolesAdditively() {
    MemoryUsers users = new MemoryUsers();
    HSecurityUser u = new HSecurityUser("alice@example.com");
    u.setEmail("alice@example.com");
    u.setRoles(List.of("AUTHOR", "AUDITOR"));
    users.put(u);

    HPrincipalEnricher enricher = new HPrincipalEnricher(users);
    HPrincipal base =
        HPrincipal.builder()
            .subject("sub-1")
            .username("alice@example.com")
            .email("alice@example.com")
            .role("VIEWER")
            .authMethod(HPrincipal.AUTH_METHOD_OAUTH2)
            .build();

    HPrincipal enriched = enricher.enrich(base);
    assertTrue(enriched.hasRole("VIEWER"));
    assertTrue(enriched.hasRole("AUTHOR"));
    assertTrue(enriched.hasRole("AUDITOR"));
    assertTrue(enriched.hasRole("AUTHENTICATED"));
  }

  @Test
  void disabledUserLosesRoles() {
    MemoryUsers users = new MemoryUsers();
    HSecurityUser u = new HSecurityUser("bob@example.com");
    u.setEmail("bob@example.com");
    u.setRoles(List.of("ADMIN"));
    u.setDisabled(true);
    users.put(u);

    HPrincipalEnricher enricher = new HPrincipalEnricher(users);
    HPrincipal base =
        HPrincipal.builder()
            .subject("sub-2")
            .email("bob@example.com")
            .role("VIEWER")
            .authMethod(HPrincipal.AUTH_METHOD_OAUTH2)
            .build();

    HPrincipal enriched = enricher.enrich(base);
    assertFalse(enriched.hasRole("VIEWER"));
    assertFalse(enriched.hasRole("ADMIN"));
    assertEquals("true", enriched.getAttributes().get("disabled"));
  }

  @Test
  void customRoleHonoredByAuthorization() {
    MemoryUsers users = new MemoryUsers();
    HSecurityUser u = new HSecurityUser("carol@example.com");
    u.setEmail("carol@example.com");
    u.setRoles(List.of("HR_VIEWER"));
    users.put(u);

    MemoryRoles roles = new MemoryRoles();
    HSecurityRole hr = new HSecurityRole("HR_VIEWER");
    hr.setActions(List.of("presentation.render", "presentation.read", "presentation.list"));
    roles.put(hr);

    HPrincipalEnricher enricher = new HPrincipalEnricher(users);
    HPrincipal principal =
        enricher.enrich(
            HPrincipal.builder()
                .subject("sub-3")
                .email("carol@example.com")
                .authMethod(HPrincipal.AUTH_METHOD_OAUTH2)
                .build());

    DefaultHAuthorizationService authz =
        new DefaultHAuthorizationService(
            HAclProvider.none(), false, new DefaultHRoleGrantResolver(roles));

    assertTrue(authz.can(principal, HAction.PRESENTATION_RENDER));
    assertFalse(authz.can(principal, HAction.PRESENTATION_UPDATE));
  }

  static final class MemoryUsers implements HUserAssignmentSource {
    private final Map<String, HSecurityUser> byKey = new ConcurrentHashMap<>();

    void put(HSecurityUser user) {
      byKey.put(HSecurityUser.normalizeKey(user.getName()), user);
    }

    @Override
    public Optional<HSecurityUser> findByEmail(String email) {
      return Optional.ofNullable(byKey.get(HSecurityUser.normalizeKey(email)));
    }

    @Override
    public Optional<HSecurityUser> findBySubject(String subject) {
      return byKey.values().stream()
          .filter(u -> subject != null && subject.equalsIgnoreCase(u.getSubject()))
          .findFirst();
    }

    @Override
    public Optional<HSecurityUser> findByName(String documentName) {
      return Optional.ofNullable(byKey.get(HSecurityUser.normalizeKey(documentName)));
    }

    @Override
    public List<HSecurityUser> listAll() {
      return List.copyOf(byKey.values());
    }
  }

  static final class MemoryRoles implements HCustomRoleSource {
    private final Map<String, HSecurityRole> byName = new ConcurrentHashMap<>();

    void put(HSecurityRole role) {
      byName.put(HSecurityRole.normalizeName(role.getName()), role);
    }

    @Override
    public Optional<HSecurityRole> find(String roleName) {
      return Optional.ofNullable(byName.get(HSecurityRole.normalizeName(roleName)));
    }

    @Override
    public List<HSecurityRole> listAll() {
      return List.copyOf(byName.values());
    }
  }
}
