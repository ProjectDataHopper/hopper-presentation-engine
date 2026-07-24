package org.hopper.security;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Built-in role matrix plus optional custom roles from {@link HCustomRoleSource}, with inheritance
 * expansion and a simple invalidateable cache of role → actions.
 */
public class DefaultHRoleGrantResolver implements HRoleGrantResolver {

  private final HCustomRoleSource roleSource;
  private final AtomicReference<Cache> cache = new AtomicReference<>(Cache.empty());

  public DefaultHRoleGrantResolver() {
    this(HCustomRoleSource.NONE);
  }

  public DefaultHRoleGrantResolver(HCustomRoleSource roleSource) {
    this.roleSource = roleSource != null ? roleSource : HCustomRoleSource.NONE;
  }

  public HCustomRoleSource getRoleSource() {
    return roleSource;
  }

  @Override
  public void invalidate() {
    cache.set(Cache.empty());
  }

  @Override
  public Set<HAction> actionsForRoles(Iterable<String> roleNames) {
    EnumSet<HAction> granted = EnumSet.noneOf(HAction.class);
    if (roleNames == null) {
      return granted;
    }
    for (String roleName : roleNames) {
      if (roleName == null || roleName.isBlank()) {
        continue;
      }
      granted.addAll(actionsForRole(roleName.trim()));
    }
    return granted;
  }

  public Set<HAction> actionsForRole(String roleName) {
    if (roleName == null || roleName.isBlank()) {
      return Set.of();
    }
    String key = roleName.trim().toUpperCase(Locale.ROOT);

    // Fast path: built-in without touching cache
    Optional<HRole> builtIn = HRole.fromName(key);
    if (builtIn.isPresent() && roleSource == HCustomRoleSource.NONE) {
      return HBuiltInRoles.actionsFor(builtIn.get());
    }

    Cache c = cache.get();
    if (c.resolved.containsKey(key)) {
      return c.resolved.get(key);
    }
    Set<HAction> resolved = resolveRole(key, new HashSet<>());
    // best-effort cache; concurrent invalidate is fine
    Cache next = c.with(key, Set.copyOf(resolved));
    cache.compareAndSet(c, next);
    return resolved;
  }

  private Set<HAction> resolveRole(String roleNameUpper, Set<String> visiting) {
    if (!visiting.add(roleNameUpper)) {
      return Set.of(); // cycle
    }

    EnumSet<HAction> actions = EnumSet.noneOf(HAction.class);

    Optional<HRole> builtIn = HRole.fromName(roleNameUpper);
    if (builtIn.isPresent()) {
      actions.addAll(HBuiltInRoles.actionsFor(builtIn.get()));
    }

    Optional<HSecurityRole> custom = roleSource.find(roleNameUpper);
    if (custom.isEmpty()) {
      // try original case lookup via list if find is case-sensitive
      custom = roleSource.find(roleNameUpper.toLowerCase(Locale.ROOT));
    }
    if (custom.isPresent()) {
      HSecurityRole role = custom.get();
      if (role.getActions() != null) {
        for (String code : role.getActions()) {
          expandActionToken(code, actions);
        }
      }
      if (role.getInheritsFrom() != null) {
        for (String parent : role.getInheritsFrom()) {
          if (parent == null || parent.isBlank()) {
            continue;
          }
          String parentKey = parent.trim().toUpperCase(Locale.ROOT);
          actions.addAll(resolveRole(parentKey, visiting));
        }
      }
    }

    return actions;
  }

  static void expandActionToken(String token, Set<HAction> into) {
    if (token == null || token.isBlank()) {
      return;
    }
    String t = token.trim();
    if ("*".equals(t)) {
      into.addAll(EnumSet.allOf(HAction.class));
      return;
    }
    if (t.endsWith(".*")) {
      String prefix = t.substring(0, t.length() - 1); // "presentation."
      for (HAction a : HAction.values()) {
        if (a.code().regionMatches(true, 0, prefix, 0, prefix.length())) {
          into.add(a);
        }
      }
      return;
    }
    HAction.fromCode(t).ifPresent(into::add);
  }

  /** Expand inheritance for listing (public helper for admin UI). */
  public Set<HAction> resolveExpandedActions(HSecurityRole role) {
    if (role == null) {
      return Set.of();
    }
    EnumSet<HAction> actions = EnumSet.noneOf(HAction.class);
    Queue<HSecurityRole> q = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    q.add(role);
    while (!q.isEmpty()) {
      HSecurityRole current = q.poll();
      if (current == null || current.getName() == null) {
        continue;
      }
      String key = HSecurityRole.normalizeName(current.getName());
      if (!seen.add(key)) {
        continue;
      }
      if (current.getActions() != null) {
        for (String code : current.getActions()) {
          expandActionToken(code, actions);
        }
      }
      if (current.getInheritsFrom() != null) {
        for (String parent : current.getInheritsFrom()) {
          if (parent == null || parent.isBlank()) {
            continue;
          }
          String parentKey = HSecurityRole.normalizeName(parent);
          HRole.fromName(parentKey)
              .ifPresent(r -> actions.addAll(HBuiltInRoles.actionsFor(r)));
          roleSource.find(parentKey).ifPresent(q::add);
        }
      }
    }
    return actions;
  }

  private record Cache(java.util.Map<String, Set<HAction>> resolved) {
    static Cache empty() {
      return new Cache(java.util.Map.of());
    }

    Cache with(String key, Set<HAction> actions) {
      java.util.LinkedHashMap<String, Set<HAction>> map =
          new java.util.LinkedHashMap<>(resolved);
      map.put(key, actions);
      return new Cache(java.util.Collections.unmodifiableMap(map));
    }
  }
}
