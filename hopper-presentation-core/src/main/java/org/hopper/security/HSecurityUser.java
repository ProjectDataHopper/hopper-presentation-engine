package org.hopper.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Hopper-side user assignment: extra roles for a known identity (email or IdP subject).
 *
 * <p>Roles are merged additively with IdP claim roles at principal enrichment time. Document {@code
 * name} is typically the lowercased email.
 */
@HopMetadata(
    key = "security-user",
    name = "Security User",
    description = "User role assignment for Hopper Presentation (additive to IdP roles)")
@Getter
@Setter
public class HSecurityUser extends HopMetadataBase implements IHopMetadata {

  /** IdP subject ({@code sub}) when known. */
  @HopMetadataProperty private String subject = "";

  @HopMetadataProperty private String displayName = "";

  @HopMetadataProperty private String email = "";

  /** Hopper role names granted to this user (in addition to IdP claims). */
  @HopMetadataProperty private List<String> roles = new ArrayList<>();

  @HopMetadataProperty private boolean disabled;

  @HopMetadataProperty private String notes = "";

  /** ISO-8601 last activity (optional; updated when observed). */
  @HopMetadataProperty private String lastSeenAt = "";

  public HSecurityUser() {}

  public HSecurityUser(String name) {
    this.name = normalizeKey(name);
    if (this.name.contains("@")) {
      this.email = this.name;
    }
  }

  public static String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    return key.trim().toLowerCase(Locale.ROOT);
  }

  public static String documentNameFor(String email, String subject) {
    if (email != null && !email.isBlank()) {
      return normalizeKey(email);
    }
    if (subject != null && !subject.isBlank()) {
      return normalizeKey(subject);
    }
    return "";
  }
}
