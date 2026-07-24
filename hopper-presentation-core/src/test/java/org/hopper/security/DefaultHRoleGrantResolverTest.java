package org.hopper.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultHRoleGrantResolverTest {

  @Test
  void builtInViewerCanRender() {
    DefaultHRoleGrantResolver resolver = new DefaultHRoleGrantResolver();
    assertTrue(resolver.actionsForRoles(List.of("VIEWER")).contains(HAction.PRESENTATION_RENDER));
    assertFalse(resolver.actionsForRoles(List.of("VIEWER")).contains(HAction.PRESENTATION_UPDATE));
  }

  @Test
  void customRoleWithExplicitActions() {
    MemoryRoleSource source = new MemoryRoleSource();
    HSecurityRole hr = new HSecurityRole("HR_VIEWER");
    hr.setActions(List.of("presentation.list", "presentation.read", "presentation.render"));
    source.put(hr);

    DefaultHRoleGrantResolver resolver = new DefaultHRoleGrantResolver(source);
    assertTrue(resolver.actionsForRole("HR_VIEWER").contains(HAction.PRESENTATION_RENDER));
    assertFalse(resolver.actionsForRole("HR_VIEWER").contains(HAction.CONNECTOR_CREATE));
  }

  @Test
  void customRoleInheritsBuiltIn() {
    MemoryRoleSource source = new MemoryRoleSource();
    HSecurityRole finance = new HSecurityRole("FINANCE_AUTHOR");
    finance.setInheritsFrom(List.of("AUTHOR"));
    finance.setActions(List.of("audit.read"));
    source.put(finance);

    DefaultHRoleGrantResolver resolver = new DefaultHRoleGrantResolver(source);
    var actions = resolver.actionsForRole("FINANCE_AUTHOR");
    assertTrue(actions.contains(HAction.PRESENTATION_UPDATE)); // from AUTHOR
    assertTrue(actions.contains(HAction.AUDIT_READ)); // explicit
    assertFalse(actions.contains(HAction.SECURITY_ADMIN));
  }

  @Test
  void familyWildcardExpands() {
    MemoryRoleSource source = new MemoryRoleSource();
    HSecurityRole r = new HSecurityRole("PRES_ALL");
    r.setActions(List.of("presentation.*"));
    source.put(r);

    DefaultHRoleGrantResolver resolver = new DefaultHRoleGrantResolver(source);
    var actions = resolver.actionsForRole("PRES_ALL");
    assertTrue(actions.contains(HAction.PRESENTATION_RENDER));
    assertTrue(actions.contains(HAction.PRESENTATION_DELETE));
    assertFalse(actions.contains(HAction.CONNECTOR_READ));
  }

  @Test
  void invalidateClearsCacheAfterRoleChange() {
    MemoryRoleSource source = new MemoryRoleSource();
    HSecurityRole r = new HSecurityRole("TEMP");
    r.setActions(List.of("theme.read"));
    source.put(r);

    DefaultHRoleGrantResolver resolver = new DefaultHRoleGrantResolver(source);
    assertTrue(resolver.actionsForRole("TEMP").contains(HAction.THEME_READ));
    assertFalse(resolver.actionsForRole("TEMP").contains(HAction.THEME_UPDATE));

    r.setActions(List.of("theme.read", "theme.update"));
    source.put(r);
    // Without invalidate, cache may still hold old set
    resolver.invalidate();
    assertTrue(resolver.actionsForRole("TEMP").contains(HAction.THEME_UPDATE));
  }

  static final class MemoryRoleSource implements HCustomRoleSource {
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
