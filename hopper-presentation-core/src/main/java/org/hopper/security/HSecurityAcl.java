package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * ACL document for a single named resource (presentation, connector, connection, theme, …).
 *
 * <p>Recommended {@code name}: {@code {resourceType}:{resourceName}} e.g. {@code
 * PRESENTATION:HR Salary}.
 */
@HopMetadata(
    key = "security-acl",
    name = "Security ACL",
    description = "Access control list for a Hopper presentation/connector/connection resource")
@Getter
@Setter
public class HSecurityAcl extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String resourceType = HResourceType.PRESENTATION.name();

  @HopMetadataProperty private String resourceName;

  @HopMetadataProperty private List<HAclEntry> entries = new ArrayList<>();

  public HSecurityAcl() {}

  public HSecurityAcl(String resourceType, String resourceName) {
    this.resourceType = resourceType;
    this.resourceName = resourceName;
    this.name = documentName(resourceType, resourceName);
  }

  public static String documentName(HResourceType type, String resourceName) {
    return documentName(type != null ? type.name() : "RESOURCE", resourceName);
  }

  public static String documentName(String resourceType, String resourceName) {
    String t = resourceType != null ? resourceType.trim().toUpperCase() : "RESOURCE";
    String n = resourceName != null ? resourceName.trim() : "";
    return t + ":" + n;
  }

  public HResourceType resourceTypeEnum() {
    return HResourceType.fromString(resourceType).orElse(HResourceType.PRESENTATION);
  }

  public HResourceRef toResourceRef() {
    return HResourceRef.of(resourceTypeEnum(), resourceName);
  }
}
