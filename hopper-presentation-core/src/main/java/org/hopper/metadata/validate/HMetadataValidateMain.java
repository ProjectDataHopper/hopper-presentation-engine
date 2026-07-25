package org.hopper.metadata.validate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hopper.core.HEnvironment;

/**
 * CLI entry for metadata validation.
 *
 * <pre>
 * java ... org.hopper.metadata.validate.HMetadataValidateMain \
 *   --type presentation|connector --file path.json [--smoke]
 * </pre>
 *
 * Exit code 0 if no ERRORs, 1 otherwise.
 */
public final class HMetadataValidateMain {

  private HMetadataValidateMain() {}

  public static void main(String[] args) throws Exception {
    String type = null;
    String file = null;
    boolean smoke = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--type" -> type = args[++i];
        case "--file" -> file = args[++i];
        case "--smoke" -> smoke = true;
        case "--help", "-h" -> {
          usage();
          return;
        }
        default -> {
          System.err.println("Unknown argument: " + args[i]);
          usage();
          System.exit(2);
        }
      }
    }
    if (type == null || file == null) {
      usage();
      System.exit(2);
    }

    HEnvironment.init();
    String json = Files.readString(Path.of(file), StandardCharsets.UTF_8);
    ValidateOptions options = ValidateOptions.builder().includeSmokeLayout(smoke).build();
    HMetadataValidator validator = new HMetadataValidator();
    ValidationReport report;
    if ("presentation".equalsIgnoreCase(type)) {
      report = validator.validatePresentationJson(json, options);
    } else if ("connector".equalsIgnoreCase(type)) {
      report = validator.validateConnectorJson(json, options);
    } else {
      System.err.println("--type must be presentation or connector");
      System.exit(2);
      return;
    }

    for (ValidationIssue issue : report.getIssues()) {
      System.out.println(
          issue.getSeverity()
              + " ["
              + issue.getCode()
              + "] "
              + issue.getPath()
              + ": "
              + issue.getMessage());
    }
    if (report.isOk()) {
      System.out.println("OK (" + report.getIssues().size() + " warning(s))");
      System.exit(0);
    } else {
      System.err.println("FAILED with " + report.errors().size() + " error(s)");
      System.exit(1);
    }
  }

  private static void usage() {
    System.out.println(
        "Usage: HMetadataValidateMain --type presentation|connector --file path.json [--smoke]");
  }
}
