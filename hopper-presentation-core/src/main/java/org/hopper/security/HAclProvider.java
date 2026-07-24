package org.hopper.security;

import java.util.List;
import java.util.Optional;

/** Loads ACL documents for authorization decisions. */
public interface HAclProvider {

  /** Empty provider — no ACL documents. */
  static HAclProvider none() {
    return new HAclProvider() {
      @Override
      public Optional<HSecurityAcl> find(HResourceRef resource) {
        return Optional.empty();
      }

      @Override
      public List<HSecurityAcl> listAll() {
        return List.of();
      }
    };
  }

  Optional<HSecurityAcl> find(HResourceRef resource);

  List<HSecurityAcl> listAll();

  default Optional<HSecurityAcl> find(HResourceType type, String name) {
    if (type == null) {
      return Optional.empty();
    }
    return find(HResourceRef.of(type, name));
  }
}
