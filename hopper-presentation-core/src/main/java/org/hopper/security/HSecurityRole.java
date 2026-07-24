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
 * Custom (or exported) role definition with explicit action grants.
 *
 * <p>Built-in roles ({@link HRole}) are not stored here; they come from {@link HBuiltInRoles}.
 * Document {@code name} is the role name (e.g. {@code HR_VIEWER}).
 */
@HopMetadata(
    key = "security-role",
    name = "Security Role",
    description = "Custom Hopper role with action grants for authorization")
@Getter
@Setter
public class HSecurityRole extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description = "";

  /** Action codes (e.g. {@code presentation.render}) or family wildcards ({@code presentation.*}). */
  @HopMetadataProperty private List<String> actions = new ArrayList<>();

  /** Optional role names whose grants are unioned into this role. */
  @HopMetadataProperty private List<String> inheritsFrom = new ArrayList<>();

  public HSecurityRole() {}

  public HSecurityRole(String name) {
    this.name = normalizeName(name);
  }

  public static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      return "";
    }
    return name.trim().toUpperCase(Locale.ROOT);
  }

  public boolean isSystemName() {
    return HRole.fromName(name).isPresent();
  }
}
