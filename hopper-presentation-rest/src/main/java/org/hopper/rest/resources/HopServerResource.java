package org.hopper.rest.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browser-facing proxy to a remote Hop Server (Data Hopper sidecar).
 *
 * <p>Uses the same env/system properties as presentation-hop ({@code hopper.hop.mode}, {@code
 * hopper.hop.server.url}, …). Keeps Hop credentials off the browser.
 *
 * <ul>
 *   <li>{@code GET /hopper/api/hop/status}
 *   <li>{@code GET /hopper/api/hop/listProject?path=&type=&depth=}
 *   <li>{@code GET /hopper/api/hop/describePipeline?pipeline=&transform=}
 * </ul>
 */
@Path("hop")
public class HopServerResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @GET
  @Path("status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response status() {
    try {
      String mode = cfg("hopper.hop.mode", "HOPPER_HOP_MODE", "embedded");
      boolean remote = "remote".equalsIgnoreCase(mode.trim());
      String url = cfg("hopper.hop.server.url", "HOPPER_HOP_SERVER_URL", "");
      ObjectNode body = MAPPER.createObjectNode();
      body.put("mode", remote ? "remote" : "embedded");
      body.put("remoteConfigured", remote && url != null && !url.isBlank());
      body.put("serverUrl", url == null ? "" : url);
      if (remote && url != null && !url.isBlank()) {
        try {
          JsonNode health = getHopJson("/hop/hopper/health", Map.of());
          body.set("health", health);
        } catch (Exception e) {
          body.put("healthError", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
      }
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (Exception e) {
      return error(500, e.getMessage());
    }
  }

  @GET
  @Path("listProject")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listProject(
      @QueryParam("path") String path,
      @QueryParam("type") String type,
      @QueryParam("depth") String depth) {
    try {
      requireRemote();
      Map<String, String> q = new LinkedHashMap<>();
      q.put("path", path == null ? "" : path);
      q.put("type", type == null ? "all" : type);
      q.put("depth", depth == null || depth.isBlank() ? "1" : depth);
      JsonNode node = getHopJson("/hop/hopper/listProject", q);
      return Response.ok(MAPPER.writeValueAsString(node)).type(MediaType.APPLICATION_JSON).build();
    } catch (IllegalStateException e) {
      return error(503, e.getMessage());
    } catch (Exception e) {
      return error(502, e.getMessage());
    }
  }

  @GET
  @Path("describePipeline")
  @Produces(MediaType.APPLICATION_JSON)
  public Response describePipeline(
      @QueryParam("pipeline") String pipeline, @QueryParam("transform") String transform) {
    try {
      requireRemote();
      if (pipeline == null || pipeline.isBlank()) {
        return error(400, "Missing pipeline");
      }
      Map<String, String> q = new LinkedHashMap<>();
      q.put("pipeline", pipeline);
      if (transform != null && !transform.isBlank()) {
        q.put("transform", transform);
      }
      JsonNode node = getHopJson("/hop/hopper/describePipeline", q);
      return Response.ok(MAPPER.writeValueAsString(node)).type(MediaType.APPLICATION_JSON).build();
    } catch (IllegalStateException e) {
      return error(503, e.getMessage());
    } catch (Exception e) {
      return error(502, e.getMessage());
    }
  }

  private static void requireRemote() {
    String mode = cfg("hopper.hop.mode", "HOPPER_HOP_MODE", "embedded");
    if (!"remote".equalsIgnoreCase(mode.trim())) {
      throw new IllegalStateException(
          "Hop is in embedded mode. Set hopper.hop.mode=remote and hopper.hop.server.url to browse a Hop project.");
    }
    String url = cfg("hopper.hop.server.url", "HOPPER_HOP_SERVER_URL", "");
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("hopper.hop.server.url / HOPPER_HOP_SERVER_URL is not set");
    }
  }

  private static JsonNode getHopJson(String path, Map<String, String> query) throws Exception {
    String base = cfg("hopper.hop.server.url", "HOPPER_HOP_SERVER_URL", "");
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    StringBuilder sb = new StringBuilder(base).append(path);
    if (query != null && !query.isEmpty()) {
      sb.append('?');
      boolean first = true;
      for (Map.Entry<String, String> e : query.entrySet()) {
        if (!first) {
          sb.append('&');
        }
        first = false;
        sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
        sb.append('=');
        sb.append(
            URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
      }
    }
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(sb.toString()))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json")
            .GET();
    String user = cfg("hopper.hop.server.user", "HOPPER_HOP_SERVER_USER", "cluster");
    String password = cfg("hopper.hop.server.password", "HOPPER_HOP_SERVER_PASSWORD", "cluster");
    if (user != null && !user.isBlank()) {
      String token =
          Base64.getEncoder()
              .encodeToString(
                  (user + ":" + (password == null ? "" : password))
                      .getBytes(StandardCharsets.UTF_8));
      b.header("Authorization", "Basic " + token);
    }
    HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 400) {
      throw new IllegalStateException(
          "Hop Server HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
    }
    return MAPPER.readTree(resp.body());
  }

  private static String cfg(String prop, String env, String def) {
    String v = System.getProperty(prop);
    if (v != null && !v.isBlank()) {
      return v.trim();
    }
    v = System.getenv(env);
    if (v != null && !v.isBlank()) {
      return v.trim();
    }
    return def;
  }

  private static Response error(int status, String message) {
    try {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("status", "ERROR");
      n.put("message", message == null ? "error" : message);
      return Response.status(status)
          .entity(MAPPER.writeValueAsString(n))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (Exception e) {
      return Response.status(status)
          .entity("{\"status\":\"ERROR\",\"message\":\"error\"}")
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 400 ? s.substring(0, 400) + "…" : s;
  }
}
