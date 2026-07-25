package org.hopper.metadata.validate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One validation finding with a stable machine-readable {@link #code}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {
  private ValidationSeverity severity;
  /** Stable code for agent repair loops, e.g. {@code ATTACHMENT_MISSING}. */
  private String code;
  /** JSON-ish path, e.g. {@code pages[0].components[1].layout.top}. */
  private String path;
  private String message;

  public static ValidationIssue error(String code, String path, String message) {
    return new ValidationIssue(ValidationSeverity.ERROR, code, path, message);
  }

  public static ValidationIssue warning(String code, String path, String message) {
    return new ValidationIssue(ValidationSeverity.WARNING, code, path, message);
  }
}
