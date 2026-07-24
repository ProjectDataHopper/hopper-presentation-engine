package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory ACL store for tests and simple deployments. */
public class MemoryHAclProvider implements HAclProvider {

  private final Map<String, HSecurityAcl> byKey = new ConcurrentHashMap<>();

  private static String key(HResourceRef resource) {
    if (resource == null || resource.getType() == null) {
      return "";
    }
    String name = resource.getName() != null ? resource.getName() : "";
    return resource.getType().name() + "\0" + name.toLowerCase();
  }

  public void put(HSecurityAcl acl) {
    if (acl == null) {
      return;
    }
    byKey.put(key(acl.toResourceRef()), acl);
  }

  public void remove(HResourceRef resource) {
    byKey.remove(key(resource));
  }

  public void clear() {
    byKey.clear();
  }

  @Override
  public Optional<HSecurityAcl> find(HResourceRef resource) {
    if (resource == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(byKey.get(key(resource)));
  }

  @Override
  public List<HSecurityAcl> listAll() {
    return new ArrayList<>(byKey.values());
  }
}
