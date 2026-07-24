package org.hopper.security;

import java.util.List;
import java.util.Optional;

/** Loads {@link HSecurityUser} assignment documents. */
public interface HUserAssignmentSource {

  Optional<HSecurityUser> findByEmail(String email);

  Optional<HSecurityUser> findBySubject(String subject);

  Optional<HSecurityUser> findByName(String documentName);

  List<HSecurityUser> listAll();

  HUserAssignmentSource NONE =
      new HUserAssignmentSource() {
        @Override
        public Optional<HSecurityUser> findByEmail(String email) {
          return Optional.empty();
        }

        @Override
        public Optional<HSecurityUser> findBySubject(String subject) {
          return Optional.empty();
        }

        @Override
        public Optional<HSecurityUser> findByName(String documentName) {
          return Optional.empty();
        }

        @Override
        public List<HSecurityUser> listAll() {
          return List.of();
        }
      };
}
