package org.hopper.presentation.swt;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.hop.core.variables.IVariables;

/**
 * String helpers for {@link HPresentationViewer} that must stay free of SWT types so unit tests can
 * run on headless CI agents without GTK.
 */
final class HPresentationViewerSupport {

  private HPresentationViewerSupport() {}

  static int parseRefreshRateMs(String text) {
    if (text == null) {
      return 0;
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return 0;
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.contains("pause")) {
      return 0;
    }
    StringBuilder digits = new StringBuilder();
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c >= '0' && c <= '9') {
        digits.append(c);
      } else if (digits.length() > 0) {
        break;
      }
    }
    if (digits.length() == 0) {
      return 0;
    }
    return Integer.parseInt(digits.toString()) * 1000;
  }

  /**
   * Hop Web {@code Browser.setText} must run only for the first document. RAP never fires {@code
   * ProgressListener.completed} for {@code setText} (only {@code changed}), and a live-refresh
   * fallback to {@code setText} reloads the iframe, re-packs the toolbar, and makes the GUI
   * unusable.
   */
  static boolean rebuildWebDocument(boolean shellReady) {
    return !shellReady;
  }

  static boolean toolbarTextUnchanged(String current, String next) {
    String a = current == null ? "" : current;
    String b = next == null ? "" : next;
    return a.equals(b);
  }

  static String withExtension(String filename, String extension) {
    if (filename == null) {
      return null;
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    String ext = extension.toLowerCase(Locale.ROOT);
    if (!ext.startsWith(".")) {
      ext = "." + ext;
    }
    return lower.endsWith(ext) ? filename : filename + extension;
  }

  /**
   * Expand {@code ${PROJECT_HOME}} and other Hop variables. The projects plugin rewrites VFS dialog
   * paths to portable variables; writing without resolving them on Hop Web becomes {@code
   * file:///usr/local/tomcat/${PROJECT_HOME}/...}.
   */
  static String resolveExportFilename(IVariables variables, String filename) {
    if (filename == null) {
      return null;
    }
    if (variables == null) {
      return filename;
    }
    return variables.resolve(filename);
  }

  static String contentType(boolean pdf) {
    return pdf ? "application/pdf" : "image/svg+xml";
  }

  static String safeDownloadFilename(String name, String extension) {
    String base = name == null ? "" : name.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    base = base.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_").trim();
    if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
      base = "presentation";
    }
    return withExtension(base, extension);
  }

  static String proposedExportPath(IVariables variables, String suggestedName) {
    String folder = null;
    if (variables != null) {
      folder = variables.getVariable("PROJECT_HOME");
      if (folder == null || folder.isBlank()) {
        folder = variables.getVariable("user.home");
      }
    }
    if (folder == null || folder.isBlank()) {
      return suggestedName;
    }
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder + suggestedName;
    }
    return folder + "/" + suggestedName;
  }

  static String contentDisposition(String filename) {
    String safe = filename == null ? "presentation" : filename;
    safe = safe.replaceAll("[\\x00-\\x1f\\x7f\\\\/\"]", "_");
    if (safe.isBlank()) {
      safe = "presentation";
    }
    String ascii = safe.replaceAll("[^\\x20-\\x7E]", "_");
    String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
  }
}
