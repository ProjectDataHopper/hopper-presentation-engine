package org.hopper.presentation.swt;

import java.util.Locale;

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
}
