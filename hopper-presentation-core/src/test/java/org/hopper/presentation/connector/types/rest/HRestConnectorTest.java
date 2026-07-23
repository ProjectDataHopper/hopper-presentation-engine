package org.hopper.presentation.connector.types.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import org.apache.hop.core.RowMetaAndData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.rest.HRestConnector.JsonField;
import org.hopper.presentation.datacontext.PresentationDataContext;

class HRestConnectorTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
    server.createContext(
        "/data",
        exchange -> {
          String body =
              """
              {"rows":[
                {"id":1,"name":"Ada"},
                {"id":2,"name":"Grace"}
              ]}
              """;
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void streamsRowsFromJsonArrayElement() throws Exception {
    HRestConnector rest = new HRestConnector();
    rest.setUrl("http://127.0.0.1:" + port);
    rest.setPath("/data");
    rest.setRowsElement("rows");
    rest.setFields(
        Arrays.asList(
            field("id", "Integer"),
            field("name", "String")));

    HConnector connector = ConnectorTestSupport.wrap("rest", rest);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(connector);

    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(connector, ctx);
    assertEquals(2, rows.size());
    assertEquals(1L, rows.get(0).getInteger("id", 0));
    assertEquals("Ada", rows.get(0).getString("name", null));
    assertEquals(2L, rows.get(1).getInteger("id", 0));
    assertEquals("Grace", rows.get(1).getString("name", null));
  }

  @Test
  void postsBodyWhenConfigured() throws Exception {
    server.createContext(
        "/echo",
        exchange -> {
          String method = exchange.getRequestMethod();
          String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          String response =
              method.equals("POST") && requestBody.contains("hello")
                  ? "{\"rows\":[{\"ok\":\"yes\"}]}"
                  : "{\"rows\":[]}";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });

    HRestConnector rest = new HRestConnector();
    rest.setUrl("http://127.0.0.1:" + port);
    rest.setPath("/echo");
    rest.setBody("{\"msg\":\"hello\"}");
    rest.setRowsElement("rows");
    rest.setFields(List.of(field("ok", "String")));

    HConnector connector = ConnectorTestSupport.wrap("rest", rest);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(connector);
    List<RowMetaAndData> rows = ConnectorTestSupport.retrieve(connector, ctx);
    assertEquals(1, rows.size());
    assertEquals("yes", rows.get(0).getString("ok", null));
  }

  private static JsonField field(String name, String type) {
    JsonField f = new JsonField(name, type);
    f.setTag(name);
    return f;
  }
}
