package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Loads custom roles from Hop metadata type {@code security-role}. */
public class MetadataHRoleSource implements HCustomRoleSource {

  private static final Logger LOG = Logger.getLogger(MetadataHRoleSource.class.getName());

  private final IHopMetadataProvider metadataProvider;

  public MetadataHRoleSource(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }

  @Override
  public Optional<HSecurityRole> find(String roleName) {
    if (roleName == null || roleName.isBlank() || metadataProvider == null) {
      return Optional.empty();
    }
    try {
      IHopMetadataSerializer<HSecurityRole> serializer =
          metadataProvider.getSerializer(HSecurityRole.class);
      // Try exact, upper, as stored
      for (String candidate :
          List.of(roleName, HSecurityRole.normalizeName(roleName), roleName.trim())) {
        if (serializer.exists(candidate)) {
          HSecurityRole role = serializer.load(candidate);
          if (role != null) {
            return Optional.of(role);
          }
        }
      }
      // Scan case-insensitive
      String want = HSecurityRole.normalizeName(roleName);
      for (String name : serializer.listObjectNames()) {
        if (want.equals(HSecurityRole.normalizeName(name))) {
          HSecurityRole role = serializer.load(name);
          if (role != null) {
            return Optional.of(role);
          }
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to load security-role " + roleName + ": " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  @Override
  public List<HSecurityRole> listAll() {
    List<HSecurityRole> result = new ArrayList<>();
    if (metadataProvider == null) {
      return result;
    }
    try {
      IHopMetadataSerializer<HSecurityRole> serializer =
          metadataProvider.getSerializer(HSecurityRole.class);
      for (String name : serializer.listObjectNames()) {
        HSecurityRole role = serializer.load(name);
        if (role != null) {
          result.add(role);
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to list security-role documents: " + e.getMessage(), e);
    }
    return result;
  }
}
