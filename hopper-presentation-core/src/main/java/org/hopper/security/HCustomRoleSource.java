package org.hopper.security;

import java.util.List;
import java.util.Optional;

/** Loads custom {@link HSecurityRole} documents (typically from Hop metadata). */
public interface HCustomRoleSource {

  Optional<HSecurityRole> find(String roleName);

  List<HSecurityRole> listAll();

  HCustomRoleSource NONE =
      new HCustomRoleSource() {
        @Override
        public Optional<HSecurityRole> find(String roleName) {
          return Optional.empty();
        }

        @Override
        public List<HSecurityRole> listAll() {
          return List.of();
        }
      };
}
