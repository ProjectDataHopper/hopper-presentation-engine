package org.hopper.util;

import org.apache.commons.io.FileUtils;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.exception.HException;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class H2DatabaseUtil {

  public static final String CONNECTOR_STEEL_WHEELS_NAME = "SteelWheels";

  /**
   * Create and populate the SteelWheels database
   *
   * @param metadataProvider The metadata provider to save the database connection in
   * @return The created/populated SteelWheels database connection
   */
  public static final HDatabaseConnection createSteelWheelsDatabase(
      IHopMetadataProvider metadataProvider, IVariables variables) throws HException {
    try {

      // Unique file per call. Surefire reuses one JVM (forkCount=1, reuseForks=true), and
      // earlier tests can leave H2 connections open on jdbc:h2:${tmpdir}/SteelWheels. A second
      // CREATE MEMORY TABLE on that same engine then fails with "table already exists".
      String h2DatabaseName =
          System.getProperty("java.io.tmpdir")
              + File.separator
              + CONNECTOR_STEEL_WHEELS_NAME
              + "-"
              + java.util.UUID.randomUUID()
              + ";DB_CLOSE_DELAY=0";

      HDatabaseConnection connection = new HDatabaseConnection();
      connection.setDatabaseTypeCode("H2");
      connection.setName(CONNECTOR_STEEL_WHEELS_NAME);
      connection.setDatabaseName(h2DatabaseName);

      IHopMetadataSerializer<HDatabaseConnection> serializer =
          metadataProvider.getSerializer(HDatabaseConnection.class);
      serializer.save(connection);

      File tmpDir = new File(System.getProperty("java.io.tmpdir"));
      File[] files =
          tmpDir.listFiles(
              new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                  String path = pathname.toString();
                  return path.contains(CONNECTOR_STEEL_WHEELS_NAME)
                      && (path.endsWith(".db") || path.endsWith(".db.old"));
                }
              });
      if (files != null) {
        for (File file : files) {
          FileUtils.deleteQuietly(file);
        }
      }

      List<String> lines =
          Files.readAllLines(
              Paths.get("src/test/resources/steelwheels/steelwheels.script"),
              StandardCharsets.UTF_8);

      DatabaseMeta databaseMeta = connection.createDatabaseMeta();
      databaseMeta.setForcingIdentifiersToUpperCase(true);
      Database database =
          new Database(new LoggingObject(connection.getName()), variables, databaseMeta);
      try {
        database.connect();
        try {
          database.execStatement("DROP ALL OBJECTS");
        } catch (Exception ignored) {
          // empty database
        }
        for (String line : lines) {
          database.execStatement(line);
        }

      } finally {
        database.disconnect();
      }

      return connection;
    } catch (Exception e) {
      throw new HException(
          "Unable to create/populate database " + CONNECTOR_STEEL_WHEELS_NAME, e);
    }
  }
}
