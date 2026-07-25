package org.hopper.presentation.datacontext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.config.HPresentationDataPaths;
import org.hopper.core.exception.HException;
import org.hopper.core.row.HHopRowsFile;
import org.hopper.presentation.connector.type.IHConnector;

/**
 * Disk-backed connector result cache using Hop binary row files under {@link
 * HPresentationDataPaths#connectorCacheDir(String)}.
 */
public final class HConnectorDiskCache {

  private HConnectorDiskCache() {}

  public static boolean isEnabledFor(IHConnector connector) {
    if (!HPresentationDataPaths.isConnectorDiskCacheEnabled()) {
      return false;
    }
    if (!HPresentationDataPaths.isConfigured()) {
      return false;
    }
    if (connector == null) {
      return false;
    }
    return connector.isCacheOnDisk();
  }

  /**
   * Content fingerprint for cache keying: plugin id + a stable dump of connector fields.
   * Parameter/variable fingerprint should be folded in by the caller when relevant.
   */
  public static String fingerprint(IHConnector connector, String variablesFingerprint) {
    StringBuilder sb = new StringBuilder();
    if (connector != null) {
      sb.append(StringUtils.defaultString(connector.getPluginId())).append('|');
      try {
        // Use toString of common metadata-bearing classes; prefer Jackson if available later
        sb.append(connector.getClass().getName()).append('|');
        sb.append(StringUtils.defaultString(connector.getSourceConnectorName())).append('|');
        // Reflection-light: hash of object identity fields via simple property dump
        sb.append(stableDump(connector));
      } catch (Exception e) {
        sb.append(System.identityHashCode(connector));
      }
    }
    if (StringUtils.isNotBlank(variablesFingerprint)) {
      sb.append("|v=").append(variablesFingerprint);
    }
    return sha1Hex(sb.toString());
  }

  public static HHopRowsFile.Snapshot load(String catalogName, String fingerprint)
      throws HException {
    String path = HPresentationDataPaths.connectorCacheFile(catalogName, fingerprint);
    if (!HHopRowsFile.exists(path)) {
      return null;
    }
    return HHopRowsFile.read(path);
  }

  public static void store(
      String catalogName, String fingerprint, IRowMeta rowMeta, List<Object[]> rows)
      throws HException {
    if (StringUtils.isBlank(catalogName) || StringUtils.isBlank(fingerprint)) {
      return;
    }
    String dir = HPresentationDataPaths.connectorCacheDir(catalogName);
    try {
      java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dir));
    } catch (Exception e) {
      throw new HException("Cannot create connector cache dir: " + dir, e);
    }
    String path = HPresentationDataPaths.connectorCacheFile(catalogName, fingerprint);
    HHopRowsFile.writeAtomic(path, rowMeta, rows);
    // Prune other fingerprints for this connector (keep only latest)
    pruneOthers(dir, fingerprint + ".hoprows");
  }

  private static void pruneOthers(String dir, String keepFileName) {
    try {
      java.nio.file.Path d = java.nio.file.Paths.get(dir);
      if (!java.nio.file.Files.isDirectory(d)) {
        return;
      }
      try (var stream = java.nio.file.Files.list(d)) {
        stream
            .filter(p -> p.getFileName().toString().endsWith(".hoprows"))
            .filter(p -> !p.getFileName().toString().equals(keepFileName))
            .forEach(
                p -> {
                  try {
                    java.nio.file.Files.deleteIfExists(p);
                  } catch (Exception ignored) {
                    // best effort
                  }
                });
      }
    } catch (Exception ignored) {
      // best effort
    }
  }

  private static String stableDump(IHConnector connector) {
    // Use getters via simple string representation of public bean-style properties
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
      // Avoid serializing listeners / runtime state
      return mapper.writeValueAsString(connector);
    } catch (Exception e) {
      return String.valueOf(connector.hashCode());
    }
  }

  private static String sha1Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }
}
