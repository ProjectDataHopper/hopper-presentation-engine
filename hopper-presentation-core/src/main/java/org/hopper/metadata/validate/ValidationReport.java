package org.hopper.metadata.validate;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** Aggregate validation result. */
@Getter
public class ValidationReport {
  private final List<ValidationIssue> issues = new ArrayList<>();

  public void add(ValidationIssue issue) {
    if (issue != null) {
      issues.add(issue);
    }
  }

  public void error(String code, String path, String message) {
    add(ValidationIssue.error(code, path, message));
  }

  public void warning(String code, String path, String message) {
    add(ValidationIssue.warning(code, path, message));
  }

  public boolean isOk() {
    return issues.stream().noneMatch(i -> i.getSeverity() == ValidationSeverity.ERROR);
  }

  public List<ValidationIssue> errors() {
    return issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.ERROR).toList();
  }

  public List<ValidationIssue> warnings() {
    return issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.WARNING).toList();
  }
}
