package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Loads user assignments from Hop metadata type {@code security-user}. */
public class MetadataHUserAssignmentSource implements HUserAssignmentSource {

  private static final Logger LOG = Logger.getLogger(MetadataHUserAssignmentSource.class.getName());

  private final IHopMetadataProvider metadataProvider;

  public MetadataHUserAssignmentSource(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }

  @Override
  public Optional<HSecurityUser> findByName(String documentName) {
    if (documentName == null || documentName.isBlank() || metadataProvider == null) {
      return Optional.empty();
    }
    try {
      IHopMetadataSerializer<HSecurityUser> serializer =
          metadataProvider.getSerializer(HSecurityUser.class);
      String key = HSecurityUser.normalizeKey(documentName);
      if (serializer.exists(key)) {
        return Optional.ofNullable(serializer.load(key));
      }
      if (serializer.exists(documentName)) {
        return Optional.ofNullable(serializer.load(documentName));
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to load security-user " + documentName + ": " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  @Override
  public Optional<HSecurityUser> findByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    String key = HSecurityUser.normalizeKey(email);
    Optional<HSecurityUser> byName = findByName(key);
    if (byName.isPresent()) {
      return byName;
    }
    // Scan email field
    for (HSecurityUser user : listAll()) {
      if (user.getEmail() != null && key.equals(HSecurityUser.normalizeKey(user.getEmail()))) {
        return Optional.of(user);
      }
      if (user.getName() != null && key.equals(HSecurityUser.normalizeKey(user.getName()))) {
        return Optional.of(user);
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<HSecurityUser> findBySubject(String subject) {
    if (subject == null || subject.isBlank()) {
      return Optional.empty();
    }
    String want = subject.trim();
    Optional<HSecurityUser> byName = findByName(HSecurityUser.normalizeKey(want));
    if (byName.isPresent()) {
      return byName;
    }
    for (HSecurityUser user : listAll()) {
      if (user.getSubject() != null && want.equalsIgnoreCase(user.getSubject().trim())) {
        return Optional.of(user);
      }
    }
    return Optional.empty();
  }

  @Override
  public List<HSecurityUser> listAll() {
    List<HSecurityUser> result = new ArrayList<>();
    if (metadataProvider == null) {
      return result;
    }
    try {
      IHopMetadataSerializer<HSecurityUser> serializer =
          metadataProvider.getSerializer(HSecurityUser.class);
      for (String name : serializer.listObjectNames()) {
        HSecurityUser user = serializer.load(name);
        if (user != null) {
          result.add(user);
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to list security-user documents: " + e.getMessage(), e);
    }
    return result;
  }
}
