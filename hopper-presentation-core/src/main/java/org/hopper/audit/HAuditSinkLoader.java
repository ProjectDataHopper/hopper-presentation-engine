package org.hopper.audit;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.audit.plugin.HAuditPluginType;
import org.hopper.audit.plugin.IAuditSink;
import org.hopper.audit.plugin.JsonlFileAuditSink;
import org.hopper.audit.plugin.LoggingAuditSink;
import org.hopper.core.exception.HException;

/**
 * Loads and registers audit sinks from bootstrap properties and {@link HAuditSinkMeta} metadata.
 */
public final class HAuditSinkLoader {

  private static final Logger LOG = Logger.getLogger(HAuditSinkLoader.class.getName());

  private HAuditSinkLoader() {}

  /**
   * Configure the emitter, clear existing sinks, apply bootstrap sinks from config, then load
   * enabled {@code audit-sink} metadata entries.
   */
  public static void bootstrap(
      HAuditEmitter emitter,
      HAuditConfig config,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HException {
    if (emitter == null) {
      return;
    }
    HAuditConfig cfg = config != null ? config : HAuditConfig.defaults();
    emitter.configure(cfg);
    emitter.clearSinks();

    if (!cfg.isEnabled()) {
      LOG.info("Audit disabled; no sinks registered");
      return;
    }

    if (cfg.isBootstrapLogging()) {
      LoggingAuditSink logging = new LoggingAuditSink();
      logging.init(Map.of(), variables);
      emitter.addSink(logging);
      LOG.info("Registered bootstrap LoggingAuditSink");
    }

    if (StringUtils.isNotBlank(cfg.getBootstrapJsonlPath())) {
      JsonlFileAuditSink jsonl = new JsonlFileAuditSink();
      jsonl.init(Map.of("path", cfg.getBootstrapJsonlPath(), "append", "true"), variables);
      emitter.addSink(jsonl);
      LOG.info("Registered bootstrap JsonlFileAuditSink path=" + cfg.getBootstrapJsonlPath());
    }

    if (metadataProvider != null) {
      loadFromMetadata(emitter, metadataProvider, variables);
    }
  }

  public static void loadFromMetadata(
      HAuditEmitter emitter, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HException {
    if (emitter == null || metadataProvider == null) {
      return;
    }
    try {
      IHopMetadataSerializer<HAuditSinkMeta> serializer =
          metadataProvider.getSerializer(HAuditSinkMeta.class);
      List<String> names = serializer.listObjectNames();
      if (names == null || names.isEmpty()) {
        return;
      }
      for (String name : names) {
        HAuditSinkMeta meta = serializer.load(name);
        if (meta == null || !meta.isEnabled()) {
          continue;
        }
        IAuditSink sink = createSink(meta, variables);
        if (sink != null) {
          emitter.addSink(sink);
          LOG.info(
              "Registered audit sink metadata='"
                  + name
                  + "' pluginId="
                  + meta.getPluginId());
        }
      }
    } catch (Exception e) {
      // Missing metadata folder / null base path must not abort server startup; bootstrap sinks
      // (logging, jsonl) are already registered.
      LOG.log(
          Level.WARNING,
          "Failed to load audit-sink metadata (continuing with bootstrap sinks only): "
              + e.getMessage(),
          e);
    }
  }

  public static IAuditSink createSink(HAuditSinkMeta meta, IVariables variables) throws HException {
    if (meta == null || StringUtils.isBlank(meta.getPluginId())) {
      throw new HException("audit-sink requires pluginId");
    }
    IAuditSink sink = instantiatePlugin(meta.getPluginId());
    Map<String, String> props = meta.propertiesAsMap();
    sink.init(props, variables);

    Set<HAuditEventType> allowed = parseEventTypes(meta.getEventTypes());
    if (allowed != null && !allowed.isEmpty()) {
      return new FilteringAuditSink(sink, allowed);
    }
    return sink;
  }

  private static IAuditSink instantiatePlugin(String pluginId) throws HException {
    try {
      PluginRegistry registry = PluginRegistry.getInstance();
      IPlugin plugin = registry.findPluginWithId(HAuditPluginType.class, pluginId);
      if (plugin == null) {
        // Fallback for built-ins if registry scan missed them (tests without full init)
        if ("LoggingAuditSink".equals(pluginId)) {
          return new LoggingAuditSink();
        }
        if ("JsonlFileAuditSink".equals(pluginId)) {
          return new JsonlFileAuditSink();
        }
        throw new HException("Unknown audit sink plugin id: " + pluginId);
      }
      Object instance = registry.loadClass(plugin);
      if (!(instance instanceof IAuditSink auditSink)) {
        throw new HException("Plugin " + pluginId + " does not implement IAuditSink");
      }
      return auditSink;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Unable to load audit sink plugin " + pluginId, e);
    }
  }

  static Set<HAuditEventType> parseEventTypes(List<String> names) {
    if (names == null || names.isEmpty()) {
      return null;
    }
    EnumSet<HAuditEventType> set = EnumSet.noneOf(HAuditEventType.class);
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      try {
        set.add(HAuditEventType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        LOG.log(Level.WARNING, "Ignoring unknown audit event type filter: " + name);
      }
    }
    return set.isEmpty() ? null : set;
  }

  /** Wrapper that filters by event type list from metadata. */
  static final class FilteringAuditSink implements IAuditSink {
    private final IAuditSink delegate;
    private final Set<HAuditEventType> allowed;

    FilteringAuditSink(IAuditSink delegate, Set<HAuditEventType> allowed) {
      this.delegate = delegate;
      this.allowed = allowed;
    }

    @Override
    public void init(Map<String, String> properties, IVariables variables) throws HException {
      // already initialized
    }

    @Override
    public boolean accepts(HAuditEvent event) {
      if (event == null || event.getEventType() == null) {
        return false;
      }
      return allowed.contains(event.getEventType()) && delegate.accepts(event);
    }

    @Override
    public void emit(HAuditEvent event) throws HException {
      delegate.emit(event);
    }

    @Override
    public void flush() throws HException {
      delegate.flush();
    }

    @Override
    public void close() throws HException {
      delegate.close();
    }

    IAuditSink getDelegate() {
      return delegate;
    }
  }
}
