package org.hopper.audit.plugin;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventJson;
import org.hopper.core.exception.HException;

/**
 * Appends one JSON object per line to a file (or VFS URI) using {@link HopVfs}.
 *
 * <p>Properties:
 *
 * <ul>
 *   <li>{@code path} (required) — file path or VFS URI
 *   <li>{@code append} — default {@code true}
 * </ul>
 */
@HAuditPlugin(
    id = "JsonlFileAuditSink",
    name = "JSONL file audit sink",
    description = "Appends audit events as JSON Lines to a VFS file")
public class JsonlFileAuditSink extends HBaseAuditSink implements IAuditSink {

  private String path;
  private boolean append = true;
  private final Object writeLock = new Object();

  @Override
  public void init(Map<String, String> properties, IVariables variables) throws HException {
    super.init(properties, variables);
    String rawPath = property("path", property("filename", ""));
    if (variables != null && StringUtils.isNotBlank(rawPath)) {
      rawPath = variables.resolve(rawPath);
    }
    if (StringUtils.isBlank(rawPath)) {
      throw new HException("JsonlFileAuditSink requires property 'path'");
    }
    this.path = rawPath.trim();
    String appendProp = property("append", "true");
    this.append = !"false".equalsIgnoreCase(appendProp) && !"no".equalsIgnoreCase(appendProp);
  }

  @Override
  public void emit(HAuditEvent event) throws HException {
    if (event == null) {
      return;
    }
    String line = HAuditEventJson.toJsonQuietly(event);
    synchronized (writeLock) {
      try (OutputStream out = HopVfs.getOutputStream(path, append);
          Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
        writer.write(line);
        writer.write('\n');
        writer.flush();
      } catch (Exception e) {
        throw new HException("Failed to write audit event to JSONL file: " + path, e);
      }
    }
  }

  public String getPath() {
    return path;
  }
}
