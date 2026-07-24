package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Loads {@link HSecurityAcl} documents from Hop metadata. */
public class MetadataHAclProvider implements HAclProvider {

  private static final Logger LOG = Logger.getLogger(MetadataHAclProvider.class.getName());

  private final IHopMetadataProvider metadataProvider;

  public MetadataHAclProvider(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }

  @Override
  public Optional<HSecurityAcl> find(HResourceRef resource) {
    if (resource == null || metadataProvider == null) {
      return Optional.empty();
    }
    try {
      IHopMetadataSerializer<HSecurityAcl> serializer =
          metadataProvider.getSerializer(HSecurityAcl.class);
      // Prefer conventional document name
      String docName = HSecurityAcl.documentName(resource.getType(), resource.getName());
      HSecurityAcl acl = serializer.load(docName);
      if (acl != null) {
        return Optional.of(acl);
      }
      // Scan if saved under a custom name
      for (String name : serializer.listObjectNames()) {
        HSecurityAcl candidate = serializer.load(name);
        if (candidate != null && matches(candidate, resource)) {
          return Optional.of(candidate);
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to load ACL for " + resource + ": " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  @Override
  public List<HSecurityAcl> listAll() {
    List<HSecurityAcl> result = new ArrayList<>();
    if (metadataProvider == null) {
      return result;
    }
    try {
      IHopMetadataSerializer<HSecurityAcl> serializer =
          metadataProvider.getSerializer(HSecurityAcl.class);
      for (String name : serializer.listObjectNames()) {
        HSecurityAcl acl = serializer.load(name);
        if (acl != null) {
          result.add(acl);
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to list security ACLs: " + e.getMessage(), e);
    }
    return result;
  }

  private static boolean matches(HSecurityAcl acl, HResourceRef resource) {
    if (acl.getResourceName() == null || resource.getName() == null) {
      return false;
    }
    if (!acl.getResourceName().equalsIgnoreCase(resource.getName())) {
      return false;
    }
    HResourceType type = acl.resourceTypeEnum();
    return type == resource.getType();
  }
}
