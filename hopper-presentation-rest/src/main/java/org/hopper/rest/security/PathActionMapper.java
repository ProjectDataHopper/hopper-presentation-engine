package org.hopper.rest.security;

import java.util.Locale;
import java.util.Optional;
import org.hopper.security.HAction;
import org.hopper.security.HResourceRef;
import org.hopper.security.HResourceType;

/**
 * Maps HTTP method + relative JAX-RS path (under {@code /api}) to a required {@link HAction}.
 *
 * <p>Returns empty when the path is public/exempt or when no mapping is known (caller may allow or
 * deny by policy). Phase 1 covers high-risk surfaces; unmapped paths require authentication only
 * when auth is enabled (no specific action).
 */
public class PathActionMapper {

  /**
   * @param method HTTP method (GET, POST, …)
   * @param path path relative to application root, e.g. {@code render/presentation} or {@code
   *     edit/presentation/foo/}
   */
  public Optional<HAction> requiredAction(String method, String path) {
    if (method == null) {
      return Optional.empty();
    }
    String m = method.toUpperCase(Locale.ROOT);
    String p = normalize(path);

    if ("OPTIONS".equals(m)) {
      return Optional.empty();
    }
    if (p.startsWith("static/")) {
      return Optional.empty();
    }

    // Render
    if (p.startsWith("render/")) {
      if (p.equals("render/main") || p.equals("render/main/")) {
        return Optional.of(HAction.PRESENTATION_LIST);
      }
      if (p.equals("render/presentation") && "POST".equals(m)) {
        return Optional.of(HAction.PRESENTATION_RENDER);
      }
      if (p.startsWith("render/page/") && "GET".equals(m)) {
        return Optional.of(HAction.PRESENTATION_RENDER);
      }
      // Name-based bookmarkable view: render/p/{name}/HTML/{page}/
      if (p.startsWith("render/p/") && "GET".equals(m)) {
        return Optional.of(HAction.PRESENTATION_RENDER);
      }
      if (p.startsWith("render/info/") && "GET".equals(m)) {
        return Optional.of(HAction.PRESENTATION_RENDER);
      }
      if (p.contains("lookupActions") || p.contains("getComponent")) {
        return Optional.of(HAction.PRESENTATION_RENDER);
      }
      if (p.contains("connector") && (p.contains("preview") || p.contains("describe"))) {
        return Optional.of(HAction.CONNECTOR_PREVIEW);
      }
      // other render GETs: treat as render
      if ("GET".equals(m) || "POST".equals(m)) {
        return Optional.of(HAction.PRESENTATION_RENDER);
      }
    }

    // Presentation editor
    if (p.startsWith("edit/presentation")) {
      if ("GET".equals(m)) {
        return Optional.of(HAction.PRESENTATION_READ);
      }
      if ("DELETE".equals(m)) {
        // page delete etc.
        return Optional.of(HAction.PRESENTATION_UPDATE);
      }
      if ("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m)) {
        if (p.endsWith("/create") || p.endsWith("/create/")) {
          return Optional.of(HAction.PRESENTATION_CREATE);
        }
        return Optional.of(HAction.PRESENTATION_UPDATE);
      }
    }

    // Metadata CRUD
    if (p.startsWith("metadata/")) {
      return mapMetadata(m, p);
    }

    // AI authoring: read-only validate/context/compile (no auto-save)
    if (p.startsWith("ai/")) {
      return Optional.of(HAction.PRESENTATION_READ);
    }

    // Plugin forms / edit plugins — treat as read or update based on method
    if (p.startsWith("edit/plugin") || p.startsWith("plugins/")) {
      if ("GET".equals(m)) {
        return Optional.of(HAction.PRESENTATION_READ);
      }
      return Optional.of(HAction.PRESENTATION_UPDATE);
    }

    // Security ACL admin
    if (p.startsWith("security/acls")) {
      return Optional.of(HAction.SECURITY_ADMIN);
    }

    // Live usage admin (AUDITOR+)
    if (p.startsWith("admin/usage")) {
      return Optional.of(HAction.AUDIT_READ);
    }

    // All other admin APIs require security admin
    if (p.startsWith("admin/")) {
      return Optional.of(HAction.SECURITY_ADMIN);
    }

    return Optional.empty();
  }

  private Optional<HAction> mapMetadata(String method, String path) {
    // metadata/types, metadata/list/{key}/, metadata/{key}/{name}, metadata/presentations/
    String resourceFamily = metadataFamily(path);

    if ("GET".equals(method)) {
      return Optional.of(
          switch (resourceFamily) {
            case "connector" -> HAction.CONNECTOR_LIST;
            case "hopper-database-connection", "connection" -> HAction.CONNECTION_LIST;
            case "theme" -> HAction.THEME_LIST;
            case "presentation" -> HAction.PRESENTATION_LIST;
            default -> HAction.METADATA_ADMIN;
          });
    }

    if ("POST".equals(method) || "PUT".equals(method)) {
      // save
      return Optional.of(
          switch (resourceFamily) {
            case "connector" -> HAction.CONNECTOR_CREATE;
            case "hopper-database-connection", "connection" -> HAction.CONNECTION_CREATE;
            case "theme" -> HAction.THEME_CREATE;
            case "presentation" -> HAction.PRESENTATION_CREATE;
            default -> HAction.METADATA_ADMIN;
          });
    }

    if ("DELETE".equals(method)) {
      return Optional.of(
          switch (resourceFamily) {
            case "connector" -> HAction.CONNECTOR_DELETE;
            case "hopper-database-connection", "connection" -> HAction.CONNECTION_DELETE;
            case "theme" -> HAction.THEME_DELETE;
            case "presentation" -> HAction.PRESENTATION_DELETE;
            default -> HAction.METADATA_ADMIN;
          });
    }

    return Optional.of(HAction.METADATA_ADMIN);
  }

  private String metadataFamily(String path) {
    // metadata/list/{key}/ or metadata/{key}/... or metadata/presentations/ or metadata/types
    if (path.contains("presentation")) {
      return "presentation";
    }
    if (path.contains("connector")) {
      return "connector";
    }
    if (path.contains("theme")) {
      return "theme";
    }
    if (path.contains("hopper-database-connection") || path.contains("database")) {
      return "hopper-database-connection";
    }
    // Catalog endpoints used by the home page / editor bootstrap
    if (path.equals("metadata/types") || path.startsWith("metadata/types/")) {
      return "presentation";
    }
    String[] parts = path.split("/");
    // metadata, list, key  OR  metadata, key, name
    if (parts.length >= 3 && "list".equals(parts[1])) {
      return parts[2];
    }
    if (parts.length >= 2) {
      return parts[1];
    }
    return "unknown";
  }

  /**
   * Best-effort resource reference extracted from the path (for ACL evaluation). Empty when the
   * path is a collection/list operation or the resource name is only in the body.
   */
  public Optional<HResourceRef> resourceRef(String method, String path) {
    String p = normalize(path);
    if (p.isEmpty()) {
      return Optional.empty();
    }

    // edit/presentation/{name}/...
    if (p.startsWith("edit/presentation/")) {
      String rest = p.substring("edit/presentation/".length());
      if (rest.isEmpty() || rest.startsWith("create")) {
        return Optional.empty();
      }
      String name = firstSegment(rest);
      if (name != null && !name.isBlank()) {
        return Optional.of(HResourceRef.presentation(urlDecode(name)));
      }
    }

    // metadata/{key}/{name} but not metadata/list/...
    if (p.startsWith("metadata/") && !p.startsWith("metadata/list/") && !p.startsWith("metadata/types")) {
      String[] parts = p.split("/");
      // metadata, key, name
      if (parts.length >= 3 && !"presentations".equals(parts[1])) {
        HResourceType type = mapKeyToResourceType(parts[1]);
        String name = parts[2];
        if (!name.isBlank() && type != null) {
          return Optional.of(HResourceRef.of(type, urlDecode(name)));
        }
      }
    }

    // security/acls/{name}
    if (p.startsWith("security/acls/")) {
      String rest = p.substring("security/acls/".length());
      String name = firstSegment(rest);
      if (name != null && !name.isBlank()) {
        return Optional.of(HResourceRef.of(HResourceType.SECURITY, urlDecode(name)));
      }
    }

    return Optional.empty();
  }

  private static HResourceType mapKeyToResourceType(String key) {
    if (key == null) {
      return null;
    }
    return switch (key) {
      case "presentation" -> HResourceType.PRESENTATION;
      case "connector" -> HResourceType.CONNECTOR;
      case "theme" -> HResourceType.THEME;
      case "hopper-database-connection" -> HResourceType.CONNECTION;
      case "security-acl" -> HResourceType.SECURITY;
      default -> HResourceType.METADATA;
    };
  }

  private static String firstSegment(String path) {
    if (path == null || path.isEmpty()) {
      return null;
    }
    int slash = path.indexOf('/');
    return slash < 0 ? path : path.substring(0, slash);
  }

  private static String urlDecode(String value) {
    try {
      return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      return value;
    }
  }

  private static String normalize(String path) {
    if (path == null) {
      return "";
    }
    String p = path.trim();
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    // strip matrix/query leftovers if any
    int q = p.indexOf('?');
    if (q >= 0) {
      p = p.substring(0, q);
    }
    return p;
  }
}
