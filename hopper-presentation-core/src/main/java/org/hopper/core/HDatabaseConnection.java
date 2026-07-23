package org.hopper.core;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.core.exception.HException;
import org.hopper.core.metastore.IHasIdentity;

/** For now we assume sane defaults like JDBC, no generic connections, ... */
@Getter
@Setter
@NoArgsConstructor
@HopMetadata(
    key = "hopper-database-connection",
    name = "Hopper Database Connection",
    description = "A description of a connection to a relational database")
public class HDatabaseConnection extends HopMetadataBase implements IHopMetadata, IHasIdentity {

  /** Hop database type codes (MYSQL, POSTGRESQL, MSSQL, ORACLE, ...) */
  @HopMetadataProperty private String databaseTypeCode;

  @HopMetadataProperty private String hostname;

  @HopMetadataProperty private String port;

  @HopMetadataProperty private String databaseName;

  @HopMetadataProperty private String username;

  @HopMetadataProperty(password = true)
  private String password;

  public HDatabaseConnection(
      String name,
      String databaseTypeCode,
      String hostname,
      String port,
      String databaseName,
      String username,
      String password) {
    this();
    this.name = name;
    this.databaseTypeCode = databaseTypeCode;
    this.hostname = hostname;
    this.port = port;
    this.databaseName = databaseName;
    this.username = username;
    this.password = password;
  }

  public DatabaseMeta createDatabaseMeta() throws HException {
    try {
      return new DatabaseMeta(
          name, databaseTypeCode, "JDBC", hostname, databaseName, port, username, password);
    } catch (Exception e) {
      throw new HException("Unable to create (convert to) Hop database connection object", e);
    }
  }
}
